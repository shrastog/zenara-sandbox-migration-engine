package com.zenarahealth.sbt.service;

import java.util.ArrayList;
import java.util.List;

import com.zenarahealth.sbt.model.AuditLogEntry;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final List<AuditLogEntry> trail = new ArrayList<>();

    public synchronized void logStep(
            int stepIndex,
            String componentId,
            String action,
            String details) {
        trail.add(AuditLogEntry.builder()
                .stepIndex(stepIndex)
                .componentId(componentId)
                .action(action)
                .timestampMs(System.currentTimeMillis())
                .details(details)
                .build());
    }

    public synchronized List<AuditLogEntry> getTrail() {
        return List.copyOf(trail);
    }

    public synchronized void clear() {
        trail.clear();
    }
}
