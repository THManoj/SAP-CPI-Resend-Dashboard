package com.example.sapcimonitor.model;

import java.time.OffsetDateTime;

public class MessageSummary {
    private String iFlowName;
    private String messageId;
    private String status;
    private String error;
    private OffsetDateTime timestamp;
    private String failedStep;
    private boolean resendAvailable;
    private String alternateWebLink;
    private String correlationId;
    private String tracePayload;
    private String packageName;
    private boolean wasResent = false;
    private String resendComment;

    public MessageSummary() {}

    public MessageSummary(String iFlowName, String messageId, String status, String error, OffsetDateTime timestamp, String failedStep, boolean resendAvailable) {
        this(iFlowName, messageId, status, error, timestamp, failedStep, resendAvailable, null, null, null);
    }

    public MessageSummary(String iFlowName, String messageId, String status, String error, OffsetDateTime timestamp, String failedStep, boolean resendAvailable, String alternateWebLink, String correlationId) {
        this(iFlowName, messageId, status, error, timestamp, failedStep, resendAvailable, alternateWebLink, correlationId, null);
    }

    public MessageSummary(String iFlowName, String messageId, String status, String error, OffsetDateTime timestamp, String failedStep, boolean resendAvailable, String alternateWebLink, String correlationId, String tracePayload) {
        this(iFlowName, messageId, status, error, timestamp, failedStep, resendAvailable, alternateWebLink, correlationId, tracePayload, null);
    }

    public MessageSummary(String iFlowName, String messageId, String status, String error, OffsetDateTime timestamp, String failedStep, boolean resendAvailable, String alternateWebLink, String correlationId, String tracePayload, String packageName) {
        this.iFlowName = iFlowName;
        this.messageId = messageId;
        this.status = status;
        this.error = error;
        this.timestamp = timestamp;
        this.failedStep = failedStep;
        this.resendAvailable = resendAvailable;
        this.alternateWebLink = alternateWebLink;
        this.correlationId = correlationId;
        this.tracePayload = tracePayload;
        this.packageName = packageName;
    }

    public String getIFlowName() { return iFlowName; }
    public String getMessageId() { return messageId; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    public String getFailedStep() { return failedStep; }
    public boolean isResendAvailable() { return resendAvailable; }
    public String getAlternateWebLink() { return alternateWebLink; }
    public String getCorrelationId() { return correlationId; }
    public String getTracePayload() { return tracePayload; }
    public String getPackageName() { return packageName; }
    public boolean isWasResent() { return wasResent; }
    public String getResendComment() { return resendComment; }

    public void setIFlowName(String iFlowName) { this.iFlowName = iFlowName; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public void setStatus(String status) { this.status = status; }
    public void setError(String error) { this.error = error; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
    public void setFailedStep(String failedStep) { this.failedStep = failedStep; }
    public void setResendAvailable(boolean resendAvailable) { this.resendAvailable = resendAvailable; }
    public void setAlternateWebLink(String alternateWebLink) { this.alternateWebLink = alternateWebLink; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setTracePayload(String tracePayload) { this.tracePayload = tracePayload; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public void setWasResent(boolean wasResent) { this.wasResent = wasResent; }
    public void setResendComment(String resendComment) { this.resendComment = resendComment; }
}
