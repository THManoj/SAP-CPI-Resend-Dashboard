package com.example.sapcimonitor.controller;

import com.example.sapcimonitor.model.MessageSummary;
import com.example.sapcimonitor.service.IngestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestService ingestService;
    private final ObjectMapper mapper = new ObjectMapper();

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> ingestJson(@RequestBody JsonNode body) {
        // Extract minimal metadata only — do not store business payloads
        String messageId = tryField(body, "messageId", "id", "msgId");
        if (messageId == null) return ResponseEntity.badRequest().body("messageId required");

        String iFlow = tryField(body, "iFlowName", "iflow", "integrationFlow", "flowName");
        String status = tryField(body, "status", "processingStatus", "state");
        String error = tryField(body, "error", "fault", "errorMessage");
        String failedStep = tryField(body, "failedStep", "activity", "processingStep");
        OffsetDateTime ts = OffsetDateTime.now();
        if (body.has("timestamp")) {
            try { ts = OffsetDateTime.parse(body.get("timestamp").asText()); } catch (Exception ignored) {}
        }

        MessageSummary m = new MessageSummary(iFlow == null ? "" : iFlow, messageId, status == null ? "" : status, error == null ? "" : error, ts, failedStep == null ? "" : failedStep, false);
        ingestService.add(m);
        return ResponseEntity.status(201).body("ok");
    }

    @GetMapping("/messages")
    public ResponseEntity<List<MessageSummary>> listIngested() {
        return ResponseEntity.ok(ingestService.list().stream().collect(Collectors.toList()));
    }

    private String tryField(JsonNode n, String... names) {
        for (String s : names) if (n.has(s)) return n.get(s).asText();
        return null;
    }
}
