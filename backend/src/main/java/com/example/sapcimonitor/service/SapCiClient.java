package com.example.sapcimonitor.service;

import com.example.sapcimonitor.model.MessageSummary;
import com.example.sapcimonitor.model.AttachmentInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SapCiClient {

    private static final Logger log = LoggerFactory.getLogger(SapCiClient.class);

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${sapci.tenantUrl:}")
    private String tenantUrl;

    @Value("${sapci.tokenUrl:}")
    private String tokenUrl;

    @Value("${sapci.clientId:}")
    private String clientId;

    @Value("${sapci.clientSecret:}")
    private String clientSecret;

    @Value("${sapci.messageProcessingApi:}")
    private String messageProcessingApi;

    @Value("${sapci.runtimeUrl:}")
    private String runtimeUrl;

    @Value("${sapci.runtimeTokenUrl:}")
    private String runtimeTokenUrl;

    @Value("${sapci.runtimeClientId:}")
    private String runtimeClientId;

    @Value("${sapci.runtimeClientSecret:}")
    private String runtimeClientSecret;

    @Value("${sapci.targetReceiverUrl:https://resend-testing.free.beeceptor.com}")
    private String targetReceiverUrl;

    private String cachedToken = null;
    private Instant tokenExpiry = Instant.MIN;

    private String cachedRuntimeToken = null;
    private Instant runtimeTokenExpiry = Instant.MIN;

    private final Map<String, String> flowToPackageCache = new ConcurrentHashMap<>();
    private Instant packageCacheExpiry = Instant.MIN;

    private final Map<String, String> resentMessageComments = new ConcurrentHashMap<>();

    public List<MessageSummary> fetchRecentMessages() {
        if (isNotConfigured()) {
            log.info("SAP CI not configured — returning mock messages");
            return mockMessages();
        }

        try {
            String token = fetchAccessToken();
            if (token == null) {
                log.warn("Could not obtain access token — returning empty list");
                return List.of();
            }

            String base = messageProcessingApi.endsWith("/")
                    ? messageProcessingApi.substring(0, messageProcessingApi.length() - 1)
                    : messageProcessingApi;
            String apiUrl = base + "?$top=50&$orderby=LogStart%20desc&$format=json";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<String> response = rest.exchange(URI.create(apiUrl), HttpMethod.GET, req, String.class);
            String raw = response.getBody();
            if (raw == null || raw.isBlank()) return List.of();

            JsonNode root = mapper.readTree(raw);
            return mapMessagesFromJson(root);
        } catch (Exception e) {
            log.error("Error fetching messages from SAP CI", e);
            return List.of();
        }
    }

    public MessageSummary fetchMessageDetails(String messageId) {
        if (messageId == null || messageId.isBlank()) return null;

        if (isNotConfigured()) {
            return fetchRecentMessages().stream()
                    .filter(m -> messageId.equals(m.getMessageId()))
                    .findFirst()
                    .orElse(null);
        }

        try {
            String token = fetchAccessToken();
            if (token == null) return null;

            String base = messageProcessingApi.endsWith("/")
                    ? messageProcessingApi.substring(0, messageProcessingApi.length() - 1)
                    : messageProcessingApi;
            String detailUrl = base + "('" + messageId + "')?$format=json";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = rest.exchange(URI.create(detailUrl), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String raw = response.getBody();
            if (raw == null || raw.isBlank()) return null;

            JsonNode node = mapper.readTree(raw);
            JsonNode payload = node.has("d") && node.get("d").isObject() ? node.get("d") : node;

            String iFlow = tryField(payload, "IntegrationFlowName", "iFlowName", "iflow", "integrationFlow");
            if (iFlow == null && payload.has("IntegrationArtifact") && payload.get("IntegrationArtifact").has("Name")) {
                iFlow = payload.get("IntegrationArtifact").get("Name").asText();
            }
            String status = tryField(payload, "Status", "status", "processingStatus", "state");
            OffsetDateTime ts = parseTimestamp(payload, "LogStart", "LogEnd", "timestamp", "logStart", "logEnd");
            String failedStep = tryField(payload, "LocalComponentName", "Receiver", "failedStep");
            String alternateWebLink = tryField(payload, "AlternateWebLink", "alternateWebLink");
            String correlationId = tryField(payload, "CorrelationId", "correlationId");

            // Detailed error messages from ErrorInformation/$value
            String errorDetail = fetchErrorInformation(messageId, token);
            if (errorDetail == null || errorDetail.isBlank()) {
                errorDetail = tryField(payload, "ErrorMessage", "error", "fault");
            }
            if (errorDetail == null && "FAILED".equalsIgnoreCase(status)) {
                errorDetail = "Processing failed in SAP Cloud Integration.";
            }

            // Scrape trace payload if present
            String tracePayload = scrapeTraceAttachment(messageId, token);
            String pkgName = resolvePackageForFlow(iFlow);

            MessageSummary details = new MessageSummary(
                    iFlow == null ? "" : iFlow,
                    messageId,
                    status == null ? "" : status,
                    errorDetail == null ? "" : errorDetail,
                    ts,
                    failedStep == null ? "" : failedStep,
                    true,
                    alternateWebLink,
                    correlationId,
                    tracePayload,
                    pkgName
            );
            if (resentMessageComments.containsKey(messageId)) {
                details.setWasResent(true);
                details.setResendComment(resentMessageComments.get(messageId));
            }
            return details;
        } catch (Exception e) {
            log.error("Error fetching message details for {}", messageId, e);
            return null;
        }
    }

    public String fetchErrorInformation(String messageId, String token) {
        try {
            String base = messageProcessingApi.endsWith("/")
                    ? messageProcessingApi.substring(0, messageProcessingApi.length() - 1)
                    : messageProcessingApi;
            String errorUrl = base + "('" + messageId + "')/ErrorInformation/$value";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.TEXT_PLAIN, MediaType.ALL));

            ResponseEntity<String> response = rest.exchange(URI.create(errorUrl), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().trim();
            }
        } catch (Exception e) {
            log.debug("No ErrorInformation/$value found for {}: {}", messageId, e.getMessage());
        }
        return null;
    }

    public List<AttachmentInfo> fetchMessageAttachments(String messageId) {
        List<AttachmentInfo> attachments = new ArrayList<>();
        if (isNotConfigured()) return attachments;

        String token = fetchAccessToken();
        if (token == null) return attachments;

        try {
            String base = messageProcessingApi.endsWith("/")
                    ? messageProcessingApi.substring(0, messageProcessingApi.length() - 1)
                    : messageProcessingApi;
            String manifestUrl = base + "('" + messageId + "')/Attachments?$format=json";

            HttpHeaders traceHeaders = new HttpHeaders();
            traceHeaders.setBearerAuth(token);
            traceHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> attResp = rest.exchange(URI.create(manifestUrl), HttpMethod.GET, new HttpEntity<>(traceHeaders), String.class);
            if (attResp.getStatusCode().is2xxSuccessful() && attResp.getBody() != null) {
                JsonNode attRoot = mapper.readTree(attResp.getBody());
                JsonNode attList = attRoot.has("d") && attRoot.get("d").has("results") ? attRoot.get("d").get("results") : attRoot;
                
                if (attList.isArray()) {
                    for (JsonNode a : attList) {
                        String id = a.has("Id") ? a.get("Id").asText() : "";
                        String name = a.has("Name") ? a.get("Name").asText() : "Attachment";
                        String contentType = a.has("ContentType") ? a.get("ContentType").asText() : "application/octet-stream";
                        Long size = a.has("PayloadSize") && !a.get("PayloadSize").isNull() ? a.get("PayloadSize").asLong() : 0L;
                        OffsetDateTime ts = parseTimestamp(a, "TimeStamp", "timestamp");
                        if (!id.isBlank()) {
                            attachments.add(new AttachmentInfo(id, name, contentType, size, ts));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Error fetching attachments for {}: {}", messageId, ex.getMessage());
        }
        return attachments;
    }

    public String fetchAttachmentContent(String attachmentId) {
        return fetchAttachmentContent(attachmentId, null, null);
    }

    public String fetchAttachmentContent(String attachmentId, String username, String password) {
        if (isNotConfigured() || attachmentId == null || attachmentId.isBlank()) return null;
        
        String token = null;
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            token = fetchUserAccessToken(username, password);
            if (token != null) {
                log.info("Successfully acquired User OAuth token for user {}", username);
            } else {
                log.warn("Could not acquire User OAuth token for user {}", username);
                return "[User Authentication Failed]: BTP user authentication failed for '" + username + "'. Please check your password or confirm if MFA / custom IdP is enabled on this account.";
            }
        }

        if (token == null) {
            token = fetchAccessToken();
        }
        if (token == null) return null;

        try {
            String base = messageProcessingApi.endsWith("/")
                    ? messageProcessingApi.substring(0, messageProcessingApi.length() - 1)
                    : messageProcessingApi;
            String valueUrl = base.substring(0, base.lastIndexOf("/")) + "/MessageProcessingLogAttachments('" + attachmentId + "')/$value";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.ALL));

            ResponseEntity<String> valResp = rest.exchange(URI.create(valueUrl), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (valResp.getStatusCode().is2xxSuccessful() && valResp.getBody() != null) {
                return valResp.getBody();
            }
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 403) {
                log.warn("SAP BTP returned 403 Forbidden on attachment $value: Role 'MonitoringPayloads.Read' / 'AuthGroup_Administrator' required per SAP note 2824338.");
                return "[SAP BTP Security Policy]: Raw payload reading ($value) is restricted on this BTP service key. (Role 'MonitoringPayloads.Read' / 'AuthGroup_Administrator' required per SAP note 2824338 & plan.pdf).";
            }
            log.error("Error fetching attachment content for {}: {}", attachmentId, ex.getMessage());
        } catch (Exception ex) {
            log.error("Error fetching attachment content for {}: {}", attachmentId, ex.getMessage());
        }
        return null;
    }

    public String fetchUserAccessToken(String username, String password) {
        if (tokenUrl == null || tokenUrl.isBlank() || clientId == null || clientSecret == null) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String authHeader = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", authHeader);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "password");
            form.add("username", username);
            form.add("password", password);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
            String resp = rest.postForObject(URI.create(tokenUrl), request, String.class);
            if (resp == null) return null;

            JsonNode n = mapper.readTree(resp);
            if (n.has("access_token")) {
                return n.get("access_token").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching user access token for username {}: {}", username, e.getMessage());
            return null;
        }
    }

    public String scrapeTraceAttachment(String messageId, String token) {
        List<AttachmentInfo> list = fetchMessageAttachments(messageId);
        if (list != null && !list.isEmpty()) {
            for (AttachmentInfo att : list) {
                String n = att.getName().toLowerCase();
                if (n.contains("body") || n.contains("payload") || n.contains("request") || n.contains("input")) {
                    String content = fetchAttachmentContent(att.getId());
                    if (content != null && !content.isBlank()) return content;
                }
            }
            // If no explicit body/payload match, return the first available attachment content
            return fetchAttachmentContent(list.get(0).getId());
        }
        return null;
    }

    public synchronized String fetchRuntimeAccessToken() {
        if (cachedRuntimeToken != null && Instant.now().isBefore(runtimeTokenExpiry)) {
            return cachedRuntimeToken;
        }

        try {
            String rClientId = (runtimeClientId != null && !runtimeClientId.isBlank()) ? runtimeClientId : clientId;
            String rClientSecret = (runtimeClientSecret != null && !runtimeClientSecret.isBlank()) ? runtimeClientSecret : clientSecret;
            String rTokenUrl = (runtimeTokenUrl != null && !runtimeTokenUrl.isBlank()) ? runtimeTokenUrl : tokenUrl;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String authHeader = "Basic " + Base64.getEncoder().encodeToString((rClientId + ":" + rClientSecret).getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", authHeader);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
            String resp = rest.postForObject(URI.create(rTokenUrl), request, String.class);
            if (resp == null) return null;

            JsonNode n = mapper.readTree(resp);
            if (n.has("access_token")) {
                cachedRuntimeToken = n.get("access_token").asText();
                long expiresInSeconds = n.has("expires_in") ? n.get("expires_in").asLong() : 3600;
                runtimeTokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresInSeconds - 60));
                return cachedRuntimeToken;
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching runtime access token", e);
            return null;
        }
    }

    public String resolveIflowEndpoint(String iflowName, String token) {
        String rtBase = (runtimeUrl != null && !runtimeUrl.isBlank()) ? runtimeUrl : tenantUrl;
        if (rtBase.endsWith("/")) rtBase = rtBase.substring(0, rtBase.length() - 1);

        if (iflowName == null || iflowName.isBlank()) {
            return rtBase + "/http/test/resending";
        }

        String normalized = iflowName.trim().toLowerCase();
        if (normalized.equals("testing")) {
            return rtBase + "/http/test/resend";
        }
        if (normalized.equals("resend-testing")) {
            return rtBase + "/http/test/resending";
        }

        // Dynamic lookup via ServiceEndpoints API
        if (token != null && !isNotConfigured()) {
            try {
                String base = messageProcessingApi.substring(0, messageProcessingApi.lastIndexOf("/"));
                String seUrl = base + "/ServiceEndpoints?$format=json";

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));

                ResponseEntity<String> resp = rest.exchange(URI.create(seUrl), HttpMethod.GET, new HttpEntity<>(headers), String.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    JsonNode root = mapper.readTree(resp.getBody());
                    JsonNode list = root.has("d") && root.get("d").has("results") ? root.get("d").get("results") : root;
                    if (list.isArray()) {
                        for (JsonNode ep : list) {
                            String name = ep.has("Name") ? ep.get("Name").asText() : "";
                            if (name.equalsIgnoreCase(iflowName)) {
                                String id = ep.has("Id") ? ep.get("Id").asText() : "";
                                int idx = id.indexOf("endpointAddress=");
                                if (idx > 0) {
                                    String addr = id.substring(idx + "endpointAddress=".length());
                                    addr = addr.replaceAll("^/+", "");
                                    if (!addr.startsWith("http/")) {
                                        addr = "http/" + addr;
                                    }
                                    return rtBase + "/" + addr;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("ServiceEndpoints resolution error for {}: {}", iflowName, e.getMessage());
            }
        }

        return rtBase + "/http/" + iflowName;
    }

    public Map<String, Object> initiateResend(String messageId, Map<String, Object> options) {
        Map<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);

        if (isNotConfigured()) {
            result.put("success", false);
            result.put("message", "SAP CI credentials not configured.");
            return result;
        }

        String token = fetchAccessToken();
        if (token == null) {
            result.put("success", false);
            result.put("message", "Could not obtain OAuth access token from SAP BTP.");
            return result;
        }

        MessageSummary summary = fetchMessageDetails(messageId);
        String webLink = summary != null ? summary.getAlternateWebLink() : null;
        String iflowName = (summary != null && summary.getIFlowName() != null && !summary.getIFlowName().isBlank())
                ? summary.getIFlowName()
                : "Testing";
        result.put("alternateWebLink", webLink);
        result.put("iFlowName", iflowName);

        try {
            // Determine payload dynamically
            String payloadToSend = null;
            if (options != null && options.containsKey("payload") && !String.valueOf(options.get("payload")).isBlank()) {
                payloadToSend = String.valueOf(options.get("payload"));
            } else if (options != null && options.containsKey("attachmentId") && !String.valueOf(options.get("attachmentId")).isBlank()) {
                payloadToSend = fetchAttachmentContent(String.valueOf(options.get("attachmentId")));
            } else {
                payloadToSend = scrapeTraceAttachment(messageId, token);
            }

            if (payloadToSend == null || payloadToSend.isBlank() || payloadToSend.startsWith("[SAP BTP Security Policy]") || payloadToSend.startsWith("[User Authentication Failed]")) {
                result.put("success", false);
                result.put("message", "Resend blocked: No payload attachment available for message " + messageId + ". This message cannot be resent even after pressing the resend button.");
                return result;
            }

            String resendMode = (options != null && options.containsKey("mode")) ? String.valueOf(options.get("mode")) : "IFLOW_ENDPOINT";
            String destinationUrl;
            HttpHeaders outHeaders = new HttpHeaders();

            if (payloadToSend.trim().startsWith("<")) {
                outHeaders.setContentType(MediaType.APPLICATION_XML);
            } else {
                outHeaders.setContentType(MediaType.APPLICATION_JSON);
            }

            // Propagate SAP PO standard correlation and parent lineage headers
            String correlationId = summary != null ? summary.getCorrelationId() : null;
            if (correlationId != null && !correlationId.isBlank()) {
                outHeaders.set("SAP_MplCorrelationId", correlationId);
                outHeaders.set("X-Correlation-ID", correlationId);
            } else {
                outHeaders.set("SAP_MplCorrelationId", messageId);
                outHeaders.set("X-Correlation-ID", messageId);
            }
            outHeaders.set("SAP_OriginalMessageId", messageId);
            outHeaders.set("SAP_ParentMessageId", messageId);
            outHeaders.set("SAP_ResendAttempt", "1");
            outHeaders.set("SAP_SourceSystem", "SAP_PO_MONITORING_REPLAY");
            outHeaders.set("X-Original-Message-Id", messageId);
            outHeaders.set("X-Resent-By", "SAP-CI-Monitor-Stateless-Engine");

            // Dynamically re-inject any custom or original headers passed in options
            if (options != null && options.containsKey("headers")) {
                Object hdrsObj = options.get("headers");
                if (hdrsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> customHeaders = (Map<String, Object>) hdrsObj;
                    for (Map.Entry<String, Object> entry : customHeaders.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null && !entry.getKey().isBlank()) {
                            String k = entry.getKey().trim();
                            if (!k.equalsIgnoreCase("Content-Length") && !k.equalsIgnoreCase("Host")) {
                                outHeaders.set(k, String.valueOf(entry.getValue()));
                            }
                        }
                    }
                }
            }

            if ("DIRECT_RECEIVER".equalsIgnoreCase(resendMode)) {
                destinationUrl = (options != null && options.containsKey("targetUrl") && !String.valueOf(options.get("targetUrl")).isBlank())
                        ? String.valueOf(options.get("targetUrl"))
                        : targetReceiverUrl;
            } else {
                // Re-trigger the EXACT SAP CPI iFlow Endpoint corresponding to this message!
                if (options != null && options.containsKey("iflowUrl") && !String.valueOf(options.get("iflowUrl")).isBlank()) {
                    destinationUrl = String.valueOf(options.get("iflowUrl"));
                } else if (options != null && options.containsKey("targetEndpoint") && !String.valueOf(options.get("targetEndpoint")).isBlank()) {
                    destinationUrl = String.valueOf(options.get("targetEndpoint"));
                } else {
                    destinationUrl = resolveIflowEndpoint(iflowName, token);
                }

                String rtToken = fetchRuntimeAccessToken();
                if (rtToken != null) {
                    outHeaders.setBearerAuth(rtToken);
                } else {
                    outHeaders.setBearerAuth(token);
                }
            }

            HttpEntity<String> outReq = new HttpEntity<>(payloadToSend, outHeaders);
            ResponseEntity<String> outResp = rest.postForEntity(URI.create(destinationUrl), outReq, String.class);

            // De-allocate memory reference immediately (plan.pdf compliant)
            payloadToSend = null;

            if (outResp.getStatusCode().is2xxSuccessful()) {
                resentMessageComments.put(messageId, "This message was resent");
                result.put("success", true);
                result.put("wasResent", true);
                result.put("comment", "This message was resent");
                result.put("statusCode", outResp.getStatusCode().value());
                result.put("destination", destinationUrl);
                if ("IFLOW_ENDPOINT".equalsIgnoreCase(resendMode)) {
                    result.put("message", "Message resend triggered successfully on iFlow '" + iflowName + "'! Reprocessed via " + destinationUrl + ". A brand new execution log has been created under '" + iflowName + "' in SAP CI Monitoring.");
                } else {
                    result.put("message", "Message " + messageId + " payload delivered directly to " + destinationUrl + " (HTTP " + outResp.getStatusCode().value() + ").");
                }
                return result;
            } else {
                result.put("success", false);
                result.put("message", "Destination returned HTTP " + outResp.getStatusCode().value());
                return result;
            }
        } catch (Exception e) {
            log.error("Resend execution error for {}", messageId, e);
            result.put("success", false);
            result.put("message", "Resend execution error: " + e.getMessage());
            return result;
        }
    }

    public boolean isNotConfigured() {
        return (tenantUrl == null || tenantUrl.isBlank())
                || (clientId == null || clientId.isBlank())
                || (clientSecret == null || clientSecret.isBlank())
                || (tokenUrl == null || tokenUrl.isBlank())
                || (messageProcessingApi == null || messageProcessingApi.isBlank());
    }

    public synchronized String fetchAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String authHeader = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", authHeader);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
            String resp = rest.postForObject(URI.create(tokenUrl), request, String.class);
            if (resp == null) return null;

            JsonNode n = mapper.readTree(resp);
            if (n.has("access_token")) {
                cachedToken = n.get("access_token").asText();
                long expiresInSeconds = n.has("expires_in") ? n.get("expires_in").asLong() : 3600;
                tokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresInSeconds - 60));
                return cachedToken;
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching access token from {}", tokenUrl, e);
            return null;
        }
    }

    private List<MessageSummary> mapMessagesFromJson(JsonNode root) {
        List<MessageSummary> result = new ArrayList<>();

        if (root.has("d") && root.get("d").isObject()) {
            JsonNode d = root.get("d");
            if (d.has("results")) {
                root = d.get("results");
            } else {
                root = d;
            }
        }

        if (root.has("value") && root.get("value").isArray()) {
            root = root.get("value");
        }

        Iterator<JsonNode> nodes;
        if (root.isArray()) nodes = root.elements();
        else {
            if (root.isObject() && root.has("messageId")) {
                nodes = List.of(root).iterator();
            } else {
                JsonNode arr = null;
                for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
                    String f = it.next();
                    if (root.get(f).isArray()) { arr = root.get(f); break; }
                }
                if (arr == null) return result;
                nodes = arr.elements();
            }
        }

        while (nodes.hasNext()) {
            JsonNode n = nodes.next();
            String messageId = tryField(n,
                    "MessageGuid", "messageGuid", "messageId", "id", "msgId", "messageIdLocal",
                    "MessageId", "ApplicationMessageId");
            String iFlow = tryField(n,
                    "IntegrationFlowName", "iFlowName", "iflow", "integrationFlow", "integrationFlowName", "flowName");
            if (iFlow == null && n.has("IntegrationArtifact") && n.get("IntegrationArtifact").has("Name")) {
                iFlow = n.get("IntegrationArtifact").get("Name").asText();
            }
            String status = tryField(n, "Status", "status", "processingStatus", "state");
            String error = tryField(n, "error", "fault", "errorMessage", "rootCause", "Error", "ErrorMessage");
            String failedStep = tryField(n,
                    "failedStep", "activity", "processingStep",
                    "LocalComponentName", "Receiver", "ReceiverInterface");
            String alternateWebLink = tryField(n, "AlternateWebLink", "alternateWebLink");
            String correlationId = tryField(n, "CorrelationId", "correlationId");
            OffsetDateTime ts = parseTimestamp(n,
                    "LogStart", "LogEnd", "timestamp", "logStart", "logEnd", "createdAt",
                    "StartTime", "EndTime");
            boolean resendAvailable = true;

            if (messageId == null) continue;
            String pkgName = resolvePackageForFlow(iFlow);
            MessageSummary summary = new MessageSummary(
                    iFlow == null ? "" : iFlow,
                    messageId,
                    status == null ? "" : status,
                    error == null ? "" : error,
                    ts,
                    failedStep == null ? "" : failedStep,
                    resendAvailable,
                    alternateWebLink,
                    correlationId,
                    null,
                    pkgName
            );
            if (resentMessageComments.containsKey(messageId)) {
                summary.setWasResent(true);
                summary.setResendComment(resentMessageComments.get(messageId));
            }
            result.add(summary);
        }

        return result;
    }

    public String resolvePackageForFlow(String flowName) {
        if (flowName == null || flowName.isBlank()) return "Standard Package";
        refreshPackageCacheIfNeeded();
        return flowToPackageCache.getOrDefault(flowName, "CPI-Trail to resend messages");
    }

    private synchronized void refreshPackageCacheIfNeeded() {
        if (Instant.now().isBefore(packageCacheExpiry) && !flowToPackageCache.isEmpty()) {
            return;
        }
        if (isNotConfigured()) return;
        String token = fetchAccessToken();
        if (token == null) return;

        try {
            String base = messageProcessingApi.endsWith("/")
                    ? messageProcessingApi.substring(0, messageProcessingApi.length() - 1)
                    : messageProcessingApi;
            String rootApi = base.substring(0, base.lastIndexOf("/"));
            String packagesUrl = rootApi + "/IntegrationPackages?$format=json";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> resp = rest.exchange(URI.create(packagesUrl), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = mapper.readTree(resp.getBody());
                JsonNode results = root.has("d") && root.get("d").has("results") ? root.get("d").get("results") : null;
                if (results != null && results.isArray()) {
                    for (JsonNode pkgNode : results) {
                        String pkgId = pkgNode.has("Id") ? pkgNode.get("Id").asText() : null;
                        String pkgName = pkgNode.has("Name") ? pkgNode.get("Name").asText() : pkgId;
                        if (pkgId == null) continue;

                        String artsUrl = rootApi + "/IntegrationPackages('" + pkgId + "')/IntegrationDesigntimeArtifacts?$format=json";
                        try {
                            ResponseEntity<String> aResp = rest.exchange(URI.create(artsUrl), HttpMethod.GET, new HttpEntity<>(headers), String.class);
                            if (aResp.getStatusCode().is2xxSuccessful() && aResp.getBody() != null) {
                                JsonNode aRoot = mapper.readTree(aResp.getBody());
                                JsonNode aResults = aRoot.has("d") && aRoot.get("d").has("results") ? aRoot.get("d").get("results") : null;
                                if (aResults != null && aResults.isArray()) {
                                    for (JsonNode artNode : aResults) {
                                        String artId = artNode.has("Id") ? artNode.get("Id").asText() : null;
                                        String artName = artNode.has("Name") ? artNode.get("Name").asText() : null;
                                        if (artId != null) flowToPackageCache.put(artId, pkgName);
                                        if (artName != null) flowToPackageCache.put(artName, pkgName);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            log.debug("Could not fetch artifacts for package {}: {}", pkgId, ex.getMessage());
                        }
                    }
                }
                packageCacheExpiry = Instant.now().plus(Duration.ofMinutes(10));
                log.info("Successfully dynamically mapped CPI packages: {}", flowToPackageCache);
            }
        } catch (Exception e) {
            log.warn("Could not dynamically refresh CPI package cache: {}", e.getMessage());
        }
    }

    private String tryField(JsonNode n, String... names) {
        for (String s : names) {
            if (n.has(s) && !n.get(s).isNull()) {
                String val = n.get(s).asText();
                if (!val.isBlank()) return val;
            }
        }
        return null;
    }

    private OffsetDateTime parseTimestamp(JsonNode n, String... names) {
        for (String s : names) {
            if (!n.has(s) || n.get(s).isNull()) continue;
            String raw = n.get(s).asText();
            if (raw == null || raw.isBlank()) continue;

            try {
                return OffsetDateTime.parse(raw);
            } catch (Exception ignored) {
            }

            try {
                int open = raw.indexOf('(');
                int close = raw.indexOf(')');
                if (open >= 0 && close > open) {
                    String millisPart = raw.substring(open + 1, close);
                    int plus = millisPart.indexOf('+');
                    int minus = millisPart.indexOf('-', 1);
                    int cut = plus > 0 ? plus : (minus > 0 ? minus : -1);
                    if (cut > 0) millisPart = millisPart.substring(0, cut);
                    long epochMillis = Long.parseLong(millisPart.trim());
                    return Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC);
                }
            } catch (Exception ignored) {
            }
        }
        return OffsetDateTime.now();
    }

    private List<MessageSummary> mockMessages() {
        List<MessageSummary> list = new ArrayList<>();
        list.add(new MessageSummary("CustomerSync", "ABC123", "FAILED", "HTTP 503 - Service Unavailable", OffsetDateTime.now(), "Request Reply", true));
        list.add(new MessageSummary("InvoiceSync", "ABC125", "FAILED", "HTTP 400 - Bad Request", OffsetDateTime.now().minusMinutes(10), "Mapping", false));
        return list;
    }
}
