package com.example.sapcimonitor.controller;

import com.example.sapcimonitor.model.MessageSummary;
import com.example.sapcimonitor.model.AttachmentInfo;
import com.example.sapcimonitor.service.SapCiClient;
import com.example.sapcimonitor.service.IngestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {

    private final SapCiClient sapCiClient;
    private final IngestService ingestService;

    public MessageController(SapCiClient sapCiClient, IngestService ingestService) {
        this.sapCiClient = sapCiClient;
        this.ingestService = ingestService;
    }

    @GetMapping
    public ResponseEntity<List<MessageSummary>> listMessages() {
        if (!sapCiClient.isNotConfigured()) {
            List<MessageSummary> list = sapCiClient.fetchRecentMessages();
            if (!list.isEmpty()) {
                return ResponseEntity.ok(list);
            }
        }
        var ingested = ingestService.list();
        if (!ingested.isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>(ingested));
        }
        return ResponseEntity.ok(sapCiClient.fetchRecentMessages());
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<MessageSummary> getMessage(@PathVariable String messageId) {
        MessageSummary details = sapCiClient.fetchMessageDetails(messageId);
        if (details == null) {
            details = ingestService.get(messageId);
        }
        if (details == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(details);
    }

    @GetMapping("/{messageId}/attachments")
    public ResponseEntity<List<AttachmentInfo>> getMessageAttachments(@PathVariable String messageId) {
        List<AttachmentInfo> attachments = sapCiClient.fetchMessageAttachments(messageId);
        return ResponseEntity.ok(attachments != null ? attachments : List.of());
    }

    @GetMapping("/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<Map<String, Object>> getAttachmentContent(
            @PathVariable String messageId,
            @PathVariable String attachmentId,
            @RequestHeader(value = "X-BTP-Username", required = false) String headerUser,
            @RequestHeader(value = "X-BTP-Password", required = false) String headerPass,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        String effectiveUser = (headerUser != null && !headerUser.isBlank()) ? headerUser : username;
        String effectivePass = (headerPass != null && !headerPass.isBlank()) ? headerPass : password;
        String content = sapCiClient.fetchAttachmentContent(attachmentId, effectiveUser, effectivePass);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("attachmentId", attachmentId);
        resp.put("messageId", messageId);
        resp.put("content", content);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{messageId}/resend")
    public ResponseEntity<Map<String, Object>> resend(
            @PathVariable String messageId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = sapCiClient.initiateResend(messageId, body != null ? body : Map.of());
        return ResponseEntity.ok().body(response);
    }
}
