package com.zenarahealth.sbt.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.zenarahealth.sbt.model.Component;
import com.zenarahealth.sbt.model.Journey;

public class TargetSandboxService {
    private final Map<String, Component> targetState = new HashMap<>();
    private final Map<String, Component> readOnlyTargetState = Collections.unmodifiableMap(targetState);

    public synchronized void resetState() {
        targetState.clear();
    }

    public synchronized Map<String, Component> getTargetState() {
        return readOnlyTargetState;
    }

    public synchronized boolean writeComponent(Component component) {
        Component existing = targetState.get(component.getId());
        if (existing != null) {
            return Objects.equals(existing.getPayloadContentHash(), component.getPayloadContentHash());
        }
        if (component.getId().contains("FAIL")
                || (component.getName() != null && component.getName().contains("FAIL"))) {
            return false;
        }
        targetState.put(component.getId(), component);
        return true;
    }

    public synchronized void deleteComponent(String id) {
        targetState.remove(id);
    }

    public synchronized boolean verifyIntegrity(Journey journey) {
        for (Component component : targetState.values()) {
            if (component.getDependencies() == null) {
                continue;
            }
            for (String dependencyId : component.getDependencies()) {
                if (!targetState.containsKey(dependencyId)) {
                    return false;
                }
            }
        }
        return true;
    }
}
