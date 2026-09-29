package com.zenarahealth.sbt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {
    private int stepIndex;
    private String componentId;
    private String action;
    private long timestampMs;
    private String details;
}
