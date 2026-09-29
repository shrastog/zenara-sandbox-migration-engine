package com.zenarahealth.sbt.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.zenarahealth.sbt.exception.DependencyCycleException;
import com.zenarahealth.sbt.exception.MissingDependencyException;
import com.zenarahealth.sbt.model.Component;
import com.zenarahealth.sbt.model.Journey;

public class GraphSolver {
    public List<Component> resolveExecutionOrder(Journey journey) {
        if (journey == null || journey.getComponents() == null) {
            throw new IllegalArgumentException("Journey and its components must not be null");
        }

        List<Component> components = journey.getComponents();
        Map<String, Component> componentsById = new HashMap<>();
        Map<String, Integer> inDegrees = new HashMap<>();
        Map<String, List<String>> dependentsById = new HashMap<>();

        for (Component component : components) {
            if (component == null || component.getId() == null) {
                throw new IllegalArgumentException("Journey components and component IDs must not be null");
            }
            if (componentsById.putIfAbsent(component.getId(), component) != null) {
                throw new IllegalArgumentException("Duplicate component ID: " + component.getId());
            }
            inDegrees.put(component.getId(), 0);
            dependentsById.put(component.getId(), new ArrayList<>());
        }

        for (Component component : components) {
            List<String> dependencies = component.getDependencies();
            if (dependencies == null) {
                continue;
            }
            for (String dependencyId : dependencies) {
                if (!componentsById.containsKey(dependencyId)) {
                    throw new MissingDependencyException(
                            "Component " + component.getId() + " depends on missing component " + dependencyId);
                }
                inDegrees.compute(component.getId(), (id, degree) -> degree + 1);
                dependentsById.get(dependencyId).add(component.getId());
            }
        }

        Queue<String> ready = new ArrayDeque<>();
        for (Component component : components) {
            if (inDegrees.get(component.getId()) == 0) {
                ready.add(component.getId());
            }
        }

        List<Component> result = new ArrayList<>(components.size());
        while (!ready.isEmpty()) {
            String componentId = ready.remove();
            result.add(componentsById.get(componentId));
            for (String dependentId : dependentsById.get(componentId)) {
                int remainingDependencies = inDegrees.compute(dependentId, (id, degree) -> degree - 1);
                if (remainingDependencies == 0) {
                    ready.add(dependentId);
                }
            }
        }

        if (result.size() != components.size()) {
            throw new DependencyCycleException("Journey contains a dependency cycle");
        }
        return result;
    }
}
