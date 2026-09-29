package com.zenarahealth.sbt.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zenarahealth.sbt.exception.DependencyCycleException;
import com.zenarahealth.sbt.exception.MissingDependencyException;
import com.zenarahealth.sbt.model.Component;
import com.zenarahealth.sbt.model.Journey;
import com.zenarahealth.sbt.model.MigrationResponse;
import com.zenarahealth.sbt.model.MigrationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MigrationEngine {
    private final GraphSolver graphSolver;
    private final TargetSandboxService targetSandboxService;
    private final AuditLogService auditLogService;

    public MigrationEngine(GraphSolver graphSolver, TargetSandboxService targetSandboxService) {
        this(graphSolver, targetSandboxService, new AuditLogService());
    }

    @Autowired
    public MigrationEngine(
            GraphSolver graphSolver,
            TargetSandboxService targetSandboxService,
            AuditLogService auditLogService) {
        this.graphSolver = graphSolver;
        this.targetSandboxService = targetSandboxService;
        this.auditLogService = auditLogService;
    }

    public MigrationResponse executeMigration(Journey journey) {
        long startTimeNs = System.nanoTime();
        auditLogService.clear();
        MigrationStatus status;
        String failureReason = null;
        int stepIndex = 0;

        List<Component> executionOrder;
        try {
            executionOrder = graphSolver.resolveExecutionOrder(journey);
        } catch (DependencyCycleException | MissingDependencyException exception) {
            status = MigrationStatus.REFUSED;
            failureReason = exception.getMessage();
            return response(journey, status, startTimeNs, failureReason);
        }

        Map<String, Component> preAttemptState = new HashMap<>(targetSandboxService.getTargetState());
        Set<String> preAttemptComponentIds = new HashSet<>(preAttemptState.keySet());
        List<Component> appliedComponents = new ArrayList<>();
        for (Component component : executionOrder) {
            stepIndex++;
            boolean written = targetSandboxService.writeComponent(component);
            auditLogService.logStep(stepIndex, component.getId(), "APPLIED",
                    written ? "Component written or already present with matching content hash"
                            : "Component write refused; payload conflict or simulated write failure");
            if (!written) {
                failureReason = "Failed to write component " + component.getId();
                boolean rollbackSucceeded = rollback(
                        appliedComponents, preAttemptComponentIds, preAttemptState, stepIndex);
                status = rollbackSucceeded ? MigrationStatus.ROLLED_BACK : MigrationStatus.ROLLBACK_FAILED;
                if (!rollbackSucceeded) {
                    failureReason += "; rollback did not restore the pre-attempt target state";
                }
                return response(journey, status, startTimeNs, failureReason);
            }
            appliedComponents.add(component);
        }

        boolean verified = targetSandboxService.verifyIntegrity(journey);
        stepIndex++;
        auditLogService.logStep(stepIndex, null, "VERIFIED",
                verified ? "Target sandbox integrity verified" : "Target sandbox integrity verification failed");
        if (!verified) {
            failureReason = "Target sandbox integrity verification failed";
            boolean rollbackSucceeded = rollback(
                    appliedComponents, preAttemptComponentIds, preAttemptState, stepIndex);
            status = rollbackSucceeded ? MigrationStatus.ROLLED_BACK : MigrationStatus.ROLLBACK_FAILED;
            if (!rollbackSucceeded) {
                failureReason += "; rollback did not restore the pre-attempt target state";
            }
            return response(journey, status, startTimeNs, failureReason);
        }
        return response(journey, MigrationStatus.SUCCESS, startTimeNs, null);
    }

    private boolean rollback(
            List<Component> appliedComponents,
            Set<String> preAttemptComponentIds,
            Map<String, Component> preAttemptState,
            int stepIndex) {
        boolean rollbackSucceeded = true;
        for (int index = appliedComponents.size() - 1; index >= 0; index--) {
            String componentId = appliedComponents.get(index).getId();
            if (!preAttemptComponentIds.contains(componentId)) {
                try {
                    targetSandboxService.deleteComponent(componentId);
                    auditLogService.logStep(++stepIndex, componentId, "DELETED",
                            "Component removed during rollback");
                } catch (RuntimeException exception) {
                    rollbackSucceeded = false;
                    auditLogService.logStep(++stepIndex, componentId, "DELETED",
                            "Rollback deletion failed: " + exception.getMessage());
                }
            }
        }
        return rollbackSucceeded && targetSandboxService.getTargetState().equals(preAttemptState);
    }

    private MigrationResponse response(
            Journey journey,
            MigrationStatus status,
            long startTimeNs,
            String failureReason) {
        return MigrationResponse.builder()
                .status(status)
                .journeyId(journey == null ? null : journey.getJourneyId())
                .executionTimeMs((System.nanoTime() - startTimeNs) / 1_000_000)
                .auditTrail(auditLogService.getTrail())
                .failureReason(failureReason)
                .build();
    }
}
