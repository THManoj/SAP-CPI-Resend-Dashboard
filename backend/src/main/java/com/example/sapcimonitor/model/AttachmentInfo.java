package com.example.sapcimonitor.model;

import java.time.OffsetDateTime;

public class AttachmentInfo {
    private String id;
    private String name;
    private String contentType;
    private Long payloadSize;
    private OffsetDateTime timestamp;

    public AttachmentInfo() {}

    public AttachmentInfo(String id, String name, String contentType, Long payloadSize, OffsetDateTime timestamp) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.payloadSize = payloadSize;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getPayloadSize() { return payloadSize; }
    public void setPayloadSize(Long payloadSize) { this.payloadSize = payloadSize; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
}
