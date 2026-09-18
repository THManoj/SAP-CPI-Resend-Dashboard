package com.example.sapcimonitor.service;

import com.example.sapcimonitor.model.MessageSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SapCiClientTest {

    @Autowired
    private SapCiClient sapCiClient;

    @Test
    public void testFetchRecentMessages() {
        List<MessageSummary> messages = sapCiClient.fetchRecentMessages();
        assertNotNull(messages);
        assertFalse(messages.isEmpty(), "Should fetch messages from SAP CPI");
        System.out.println("Fetched " + messages.size() + " messages from SAP CPI:");
        for (MessageSummary m : messages) {
            System.out.println(" - [" + m.getStatus() + "] iFlow=" + m.getIFlowName() + ", ID=" + m.getMessageId() + ", Time=" + m.getTimestamp());
        }
    }

    @Test
    public void testFetchMessageDetailsWithFailedMessage() {
        List<MessageSummary> messages = sapCiClient.fetchRecentMessages();
        assertNotNull(messages);
        
        // Find a failed message if present
        MessageSummary failedMsg = messages.stream()
                .filter(m -> "FAILED".equalsIgnoreCase(m.getStatus()))
                .findFirst()
                .orElse(null);

        if (failedMsg != null) {
            System.out.println("Testing details for failed message: " + failedMsg.getMessageId());
            MessageSummary details = sapCiClient.fetchMessageDetails(failedMsg.getMessageId());
            assertNotNull(details);
            System.out.println("Detailed Error: " + details.getError());
            assertNotNull(details.getError());
            assertFalse(details.getError().isBlank());
        }
    }

    @Test
    public void testDiscoverEndpoints() {
        String token = sapCiClient.fetchAccessToken();
        assertNotNull(token);
        org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(org.springframework.http.MediaType.APPLICATION_JSON));
        
        String rtToken = sapCiClient.fetchRuntimeAccessToken();
        org.springframework.http.HttpHeaders rtHeaders = new org.springframework.http.HttpHeaders();
        rtHeaders.setBearerAuth(rtToken != null ? rtToken : token);
        rtHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        String[] testUrls = {
            "https://b65f2da8trial.it-cpitrial03-rt.cfapps.ap21.hana.ondemand.com/http/testresend",
            "https://b65f2da8trial.it-cpitrial03-rt.cfapps.ap21.hana.ondemand.com/http/test/resend",
            "https://b65f2da8trial.it-cpitrial03-rt.cfapps.ap21.hana.ondemand.com/http/Testing"
        };
        for (String url : testUrls) {
            try {
                org.springframework.http.ResponseEntity<String> resp = rest.postForEntity(
                    java.net.URI.create(url),
                    new org.springframework.http.HttpEntity<>("{\"test\":true,\"orderId\":\"ORD-777\"}", rtHeaders),
                    String.class
                );
                System.out.println("POST SUCCESS for " + url + " -> Status: " + resp.getStatusCode() + ", Body: " + resp.getBody());
            } catch (Exception e) {
                System.out.println("POST Failed for " + url + " -> " + e.getMessage());
            }
        }
    }
}
