package com.zenarahealth.sbt.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationResponse {
    private MigrationStatus status;
    private String journeyId;
    private long executionTimeMs;
    private List<AuditLogEntry> auditTrail;
    private String failureReason;
}
