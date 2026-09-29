package com.zenarahealth.sbt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.zenarahealth.sbt.model.Component;
import com.zenarahealth.sbt.model.ComponentType;
import com.zenarahealth.sbt.model.Journey;
import com.zenarahealth.sbt.model.MigrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MigrationEngineTest {
    private TargetSandboxService targetSandboxService;
    private MigrationEngine migrationEngine;

    @BeforeEach
    void setUp() {
        targetSandboxService = new TargetSandboxService();
        migrationEngine = new MigrationEngine(new GraphSolver(), targetSandboxService);
    }

    @Test
    void migratesCleanChain() throws IOException {
        Fixture fixture = loadFixture("clean_chain.json");

        MigrationStatus status = migrationEngine.executeMigration(fixture.journey());

        assertEquals(MigrationStatus.SUCCESS, status);
        assertEquals(fixture.journey().getComponents().size(), targetSandboxService.getTargetState().size());
    }

    @Test
    void migratesDiamondGraph() throws IOException {
        Fixture fixture = loadFixture("diamond_parallel.json");

        MigrationStatus status = migrationEngine.executeMigration(fixture.journey());

        assertEquals(MigrationStatus.SUCCESS, status);
        assertEquals(fixture.journey().getComponents().size(), targetSandboxService.getTargetState().size());
    }

    @Test
    void refusesInvalidDependencyGraphWithoutWriting() throws IOException {
        Fixture fixture = loadFixture("cycle_or_missing.json");

        MigrationStatus status = migrationEngine.executeMigration(fixture.journey());

        assertEquals(MigrationStatus.REFUSED, status);
        assertTrue(targetSandboxService.getTargetState().isEmpty());
    }

    @Test
    void rollsBackWritesAfterMidwayFailure() throws IOException {
        Fixture fixture = loadFixture("midway_failure_rollback.json");

        MigrationStatus status = migrationEngine.executeMigration(fixture.journey());

        assertEquals(MigrationStatus.ROLLED_BACK, status);
        assertTrue(targetSandboxService.getTargetState().isEmpty());
    }

    @Test
    void detectsSilentPartialWriteDuringTargetVerification() throws IOException {
        Fixture fixture = loadFixture("silent_partial_failure.json");
        TargetSandboxService silentFailureTarget = new TargetSandboxService() {
            @Override
            public synchronized boolean writeComponent(Component component) {
                if (component.getId().equals(fixture.silentlyOmitComponentId())) {
                    return true;
                }
                return super.writeComponent(component);
            }
        };
        migrationEngine = new MigrationEngine(new GraphSolver(), silentFailureTarget);

        MigrationStatus status = migrationEngine.executeMigration(fixture.journey());

        assertEquals(MigrationStatus.ROLLED_BACK, status);
        assertTrue(silentFailureTarget.getTargetState().isEmpty());
    }

    @Test
    void preservesPreExistingTargetStateWhenPayloadConflicts() throws IOException {
        Fixture fixture = loadFixture("existing_target_conflict.json");
        for (Component component : fixture.targetComponents()) {
            assertTrue(targetSandboxService.writeComponent(component));
        }
        Map<String, Component> preAttemptState = new HashMap<>(targetSandboxService.getTargetState());

        MigrationStatus status = migrationEngine.executeMigration(fixture.journey());

        assertTrue(status == MigrationStatus.REFUSED || status == MigrationStatus.ROLLED_BACK);
        assertEquals(preAttemptState, targetSandboxService.getTargetState());
    }

    @Test
    void testIdempotentMigration_SameContentHash_ShouldSucceed() {
        Component existingComponent = component("AUD-100", ComponentType.AUDIENCE, "HASH_ABC", List.of());
        assertTrue(targetSandboxService.writeComponent(existingComponent));
        Journey journey = journeyWith(existingComponent);

        MigrationStatus status = migrationEngine.executeMigration(journey);

        assertEquals(MigrationStatus.SUCCESS, status);
        assertEquals(existingComponent, targetSandboxService.getTargetState().get("AUD-100"));
    }

    @Test
    void testEmptyJourney_ShouldReturnSuccess() {
        Journey journey = Journey.builder()
                .journeyId("empty-journey")
                .name("Empty journey")
                .components(Collections.emptyList())
                .build();

        MigrationStatus status = migrationEngine.executeMigration(journey);

        assertEquals(MigrationStatus.SUCCESS, status);
        assertTrue(targetSandboxService.getTargetState().isEmpty());
    }

    @Test
    void testMultipleIndependentRoots_ShouldSucceed() {
        Component audienceOne = component("AUD-1", ComponentType.AUDIENCE, "HASH_AUD_1", List.of());
        Component eventOne = component("EVT-1", ComponentType.EVENT, "HASH_EVT_1", List.of("AUD-1"));
        Component audienceTwo = component("AUD-2", ComponentType.AUDIENCE, "HASH_AUD_2", List.of());
        Component eventTwo = component("EVT-2", ComponentType.EVENT, "HASH_EVT_2", List.of("AUD-2"));
        Journey journey = journeyWith(audienceOne, eventOne, audienceTwo, eventTwo);

        MigrationStatus status = migrationEngine.executeMigration(journey);

        assertEquals(MigrationStatus.SUCCESS, status);
        assertEquals(4, targetSandboxService.getTargetState().size());
        assertTrue(targetSandboxService.getTargetState().keySet()
                .containsAll(List.of("AUD-1", "EVT-1", "AUD-2", "EVT-2")));
    }

    @Test
    void testRollbackLIFOHierarchyOrder_OnFailure() {
        List<String> deletedIds = new ArrayList<>();
        TargetSandboxService trackingTarget = new TargetSandboxService() {
            @Override
            public synchronized void deleteComponent(String id) {
                deletedIds.add(id);
                super.deleteComponent(id);
            }
        };
        migrationEngine = new MigrationEngine(new GraphSolver(), trackingTarget);

        Component audience = component("AUD-100", ComponentType.AUDIENCE, "HASH_AUD", List.of());
        Component campaign = component("CAM-100", ComponentType.INLINE_CAMPAIGN, "HASH_CAM", List.of("AUD-100"));
        Component failingAction = component("FAIL-100", ComponentType.CUSTOM_ACTION, "HASH_FAIL", List.of("CAM-100"));
        Journey journey = journeyWith(audience, campaign, failingAction);

        MigrationStatus status = migrationEngine.executeMigration(journey);

        assertEquals(MigrationStatus.ROLLED_BACK, status);
        assertEquals(List.of("CAM-100", "AUD-100"), deletedIds);
        assertTrue(trackingTarget.getTargetState().isEmpty());
    }

    private Component component(String id, ComponentType type, String payloadHash, List<String> dependencies) {
        return Component.builder()
                .id(id)
                .name(id)
                .type(type)
                .payloadContentHash(payloadHash)
                .dependencies(dependencies)
                .build();
    }

    private Journey journeyWith(Component... components) {
        return Journey.builder()
                .journeyId("test-journey")
                .name("Test journey")
                .components(List.of(components))
                .build();
    }

    private Fixture loadFixture(String fixtureName) throws IOException {
        String resourceName = "/fixtures/" + fixtureName;
        try (InputStream input = getClass().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Fixture not found: " + resourceName);
            }
            DocumentContext fixture = JsonPath.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, Object> root = fixture.read("$");
            Map<String, Object> journeyJson = requireMap(root.get("journey"), "journey", resourceName);
            Journey journey = Journey.builder()
                    .journeyId(requireString(journeyJson.get("journeyId"), "journeyId", resourceName))
                    .name(requireString(journeyJson.get("name"), "journey name", resourceName))
                    .components(parseComponents(journeyJson.get("components"), resourceName))
                    .build();
            List<Component> targetComponents = root.containsKey("targetComponents")
                    ? parseComponents(root.get("targetComponents"), resourceName)
                    : List.of();
            Object omittedId = root.get("silentlyOmitComponentId");
            String silentlyOmitComponentId = omittedId == null
                    ? null
                    : requireString(omittedId, "silentlyOmitComponentId", resourceName);
            return new Fixture(journey, targetComponents, silentlyOmitComponentId);
        }
    }

    private List<Component> parseComponents(Object rawComponents, String resourceName) throws IOException {
        if (!(rawComponents instanceof List<?> componentJson)) {
            throw new IOException("Fixture must contain a components array: " + resourceName);
        }
        List<Component> components = new ArrayList<>(componentJson.size());
        for (Object rawComponent : componentJson) {
            Map<String, Object> component = requireMap(rawComponent, "component", resourceName);
            Object rawDependencies = component.get("dependencies");
            if (!(rawDependencies instanceof List<?> dependencyJson)) {
                throw new IOException("Fixture component must contain dependencies: " + resourceName);
            }
            List<String> dependencies = new ArrayList<>(dependencyJson.size());
            for (Object dependency : dependencyJson) {
                dependencies.add(requireString(dependency, "dependency ID", resourceName));
            }
            String type = requireString(component.get("type"), "component type", resourceName);
            components.add(Component.builder()
                    .id(requireString(component.get("id"), "component ID", resourceName))
                    .name(requireString(component.get("name"), "component name", resourceName))
                    .type(ComponentType.valueOf(type))
                    .payloadContentHash(requireString(
                            component.get("payloadContentHash"), "payloadContentHash", resourceName))
                    .dependencies(dependencies)
                    .build());
        }
        return components;
    }

    private Map<String, Object> requireMap(Object value, String field, String resourceName) throws IOException {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IOException("Fixture " + field + " must be an object: " + resourceName);
        }
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IOException("Fixture object keys must be strings: " + resourceName);
            }
            map.put(key, entry.getValue());
        }
        return map;
    }

    private String requireString(Object value, String field, String resourceName) throws IOException {
        if (!(value instanceof String string)) {
            throw new IOException("Fixture " + field + " must be a string: " + resourceName);
        }
        return string;
    }

    private record Fixture(
            Journey journey,
            List<Component> targetComponents,
            String silentlyOmitComponentId) {
    }
}
