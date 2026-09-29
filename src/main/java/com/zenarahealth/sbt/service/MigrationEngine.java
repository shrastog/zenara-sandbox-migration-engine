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
import com.zenarahealth.sbt.model.MigrationStatus;

public class MigrationEngine {
    private final GraphSolver graphSolver;
    private final TargetSandboxService targetSandboxService;

    public MigrationEngine(GraphSolver graphSolver, TargetSandboxService targetSandboxService) {
        this.graphSolver = graphSolver;
        this.targetSandboxService = targetSandboxService;
    }

    public MigrationStatus executeMigration(Journey journey) {
        List<Component> executionOrder;
        try {
            executionOrder = graphSolver.resolveExecutionOrder(journey);
        } catch (DependencyCycleException | MissingDependencyException exception) {
            return MigrationStatus.REFUSED;
        }

        Map<String, Component> preAttemptState = new HashMap<>(targetSandboxService.getTargetState());
        Set<String> preAttemptComponentIds = new HashSet<>(preAttemptState.keySet());
        List<Component> appliedComponents = new ArrayList<>();
        for (Component component : executionOrder) {
            if (!targetSandboxService.writeComponent(component)) {
                return rollback(appliedComponents, preAttemptComponentIds, preAttemptState)
                        ? MigrationStatus.ROLLED_BACK
                        : MigrationStatus.ROLLBACK_FAILED;
            }
            appliedComponents.add(component);
        }

        if (!targetSandboxService.verifyIntegrity(journey)) {
            return rollback(appliedComponents, preAttemptComponentIds, preAttemptState)
                    ? MigrationStatus.ROLLED_BACK
                    : MigrationStatus.ROLLBACK_FAILED;
        }
        return MigrationStatus.SUCCESS;
    }

    private boolean rollback(
            List<Component> appliedComponents,
            Set<String> preAttemptComponentIds,
            Map<String, Component> preAttemptState) {
        boolean rollbackSucceeded = true;
        for (int index = appliedComponents.size() - 1; index >= 0; index--) {
            String componentId = appliedComponents.get(index).getId();
            if (!preAttemptComponentIds.contains(componentId)) {
                try {
                    targetSandboxService.deleteComponent(componentId);
                } catch (RuntimeException exception) {
                    rollbackSucceeded = false;
                }
            }
        }
        return rollbackSucceeded && targetSandboxService.getTargetState().equals(preAttemptState);
    }
}
