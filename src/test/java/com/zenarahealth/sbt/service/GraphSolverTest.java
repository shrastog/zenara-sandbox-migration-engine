package com.zenarahealth.sbt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.zenarahealth.sbt.exception.DependencyCycleException;
import com.zenarahealth.sbt.exception.MissingDependencyException;
import com.zenarahealth.sbt.model.Component;
import com.zenarahealth.sbt.model.ComponentType;
import com.zenarahealth.sbt.model.Journey;
import org.junit.jupiter.api.Test;

class GraphSolverTest {
    private final GraphSolver graphSolver = new GraphSolver();

    @Test
    void resolvesCleanChainInExactOrder() throws IOException {
        Journey journey = loadJourney("clean_chain.json");

        List<Component> executionOrder = graphSolver.resolveExecutionOrder(journey);

        assertEquals(
                List.of(ComponentType.AUDIENCE, ComponentType.INLINE_CAMPAIGN,
                        ComponentType.EVENT, ComponentType.CUSTOM_ACTION),
                executionOrder.stream().map(Component::getType).toList());
    }

    @Test
    void resolvesDiamondWithRootFirstAndEveryComponent() throws IOException {
        Journey journey = loadJourney("diamond_parallel.json");

        List<Component> executionOrder = graphSolver.resolveExecutionOrder(journey);

        assertEquals("audience-new-users", executionOrder.getFirst().getId());
        assertEquals(journey.getComponents().size(), executionOrder.size());
    }

    @Test
    void refusesCycleOrMissingDependency() throws IOException {
        Journey journey = loadJourney("cycle_or_missing.json");

        RuntimeException exception = assertThrows(
                RuntimeException.class, () -> graphSolver.resolveExecutionOrder(journey));
        assertTrue(exception instanceof DependencyCycleException
                || exception instanceof MissingDependencyException);
    }

    private Journey loadJourney(String fixtureName) throws IOException {
        String resourceName = "/fixtures/" + fixtureName;
        try (InputStream input = getClass().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Fixture not found: " + resourceName);
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            DocumentContext fixture = JsonPath.parse(json);
            Map<String, Object> journeyJson = fixture.read("$.journey");
            Object rawComponents = journeyJson.get("components");
            if (!(rawComponents instanceof List<?> componentJson)) {
                throw new IOException("Fixture journey must contain a components array: " + resourceName);
            }
            List<Component> components = new ArrayList<>(componentJson.size());
            for (Object rawComponent : componentJson) {
                if (!(rawComponent instanceof Map<?, ?> component)) {
                    throw new IOException("Fixture component must be a JSON object: " + resourceName);
                }
                Object rawDependencies = component.get("dependencies");
                if (!(rawDependencies instanceof List<?> dependencyJson)) {
                    throw new IOException("Fixture component must contain a dependencies array: " + resourceName);
                }
                List<String> dependencies = new ArrayList<>(dependencyJson.size());
                for (Object dependency : dependencyJson) {
                    if (!(dependency instanceof String dependencyId)) {
                        throw new IOException("Fixture dependencies must be strings: " + resourceName);
                    }
                    dependencies.add(dependencyId);
                }
                Object rawType = component.get("type");
                if (!(rawType instanceof String type)) {
                    throw new IOException("Fixture component must contain a type: " + resourceName);
                }
                components.add(Component.builder()
                        .id((String) component.get("id"))
                        .name((String) component.get("name"))
                        .type(ComponentType.valueOf(type))
                        .payloadContentHash((String) component.get("payloadContentHash"))
                        .dependencies(dependencies)
                        .build());
            }
            return Journey.builder()
                    .journeyId((String) journeyJson.get("journeyId"))
                    .name((String) journeyJson.get("name"))
                    .components(components)
                    .build();
        }
    }
}
