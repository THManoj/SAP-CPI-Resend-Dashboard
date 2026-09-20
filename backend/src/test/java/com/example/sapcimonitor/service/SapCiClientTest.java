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
        
        // Test querying /ServiceEndpoints?$expand=EntryPoints&$format=json from SAP CPI
        try {
            String seUrl = "https://b65f2da8trial.it-cpitrial03.cfapps.ap21.hana.ondemand.com/api/v1/ServiceEndpoints?$expand=EntryPoints&$format=json";
            org.springframework.http.ResponseEntity<String> seResp = rest.exchange(
                java.net.URI.create(seUrl),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
            );
            System.out.println("ServiceEndpoints with EntryPoints Response -> Status: " + seResp.getStatusCode() + ", Body: " + seResp.getBody());
        } catch (Exception e) {
            System.out.println("ServiceEndpoints with EntryPoints Failed -> " + e.getMessage());
        }

        // Test calling resolveIflowEndpoint directly on SapCiClient
        String testingUrl = sapCiClient.resolveIflowEndpoint("Testing", token);
        System.out.println("Resolved endpoint for 'Testing': " + testingUrl);
        assertNotNull(testingUrl, "Should resolve endpoint for 'Testing'");
        assertTrue(testingUrl.contains("/http/test/resend"), "Should match deployed sender address /http/test/resend");

        String resendTestingUrl = sapCiClient.resolveIflowEndpoint("resend-testing", token);
        System.out.println("Resolved endpoint for 'resend-testing': " + resendTestingUrl);
        assertNotNull(resendTestingUrl, "Should resolve endpoint for 'resend-testing'");
        assertTrue(resendTestingUrl.contains("/http/test/resending"), "Should match deployed sender address /http/test/resending");

        String cpiDashUrl = sapCiClient.resolveIflowEndpoint("CPI_Dashboard_testing", token);
        System.out.println("Resolved endpoint for 'CPI_Dashboard_testing': " + cpiDashUrl);
        assertNotNull(cpiDashUrl, "Should resolve endpoint for 'CPI_Dashboard_testing'");
        assertTrue(cpiDashUrl.contains("/http/resending/testing"), "Should match deployed sender address /http/resending/testing");

        String nonExistentUrl = sapCiClient.resolveIflowEndpoint("NonExistent_iFlow_12345", token);
        System.out.println("Resolved endpoint for 'NonExistent_iFlow_12345': " + nonExistentUrl);
        assertNull(nonExistentUrl, "Should return null for non-existent or undeployed flow");
    }
}
