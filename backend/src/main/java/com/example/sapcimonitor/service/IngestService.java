package com.example.sapcimonitor.service;

import com.example.sapcimonitor.model.MessageSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngestService {
    // In-memory demo store for metadata only. Not persistent.
    private final Map<String, MessageSummary> store = new ConcurrentHashMap<>();

    public void add(MessageSummary m) {
        if (m == null || m.getMessageId() == null) return;
        store.put(m.getMessageId(), m);
    }

    public MessageSummary get(String messageId) {
        return store.get(messageId);
    }

    public Collection<MessageSummary> list() {
        return new ArrayList<>(store.values());
    }

    public void clear() { store.clear(); }
}
