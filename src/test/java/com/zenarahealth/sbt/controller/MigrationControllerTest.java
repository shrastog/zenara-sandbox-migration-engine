package com.zenarahealth.sbt.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MigrationControllerTest {
    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void executesCleanChainAndReturnsAuditTrail() throws Exception {
        HttpResponse<String> response = execute(loadJourney("clean_chain.json"));

        assertEquals(200, response.statusCode());
        assertEquals("SUCCESS", JsonPath.read(response.body(), "$.status"));
        assertTrue(JsonPath.<Integer>read(response.body(), "$.auditTrail.length()") > 0);
        assertEquals("APPLIED", JsonPath.read(response.body(), "$.auditTrail[0].action"));
    }

    @Test
    void executesMidwayFailureAndReturnsLifoDeletionAudit() throws Exception {
        HttpResponse<String> response = execute(loadJourney("midway_failure_rollback.json"));

        assertEquals(200, response.statusCode());
        assertEquals("ROLLED_BACK", JsonPath.read(response.body(), "$.status"));
        assertEquals("campaign-welcome", JsonPath.read(response.body(), "$.auditTrail[3].componentId"));
        assertEquals("DELETED", JsonPath.read(response.body(), "$.auditTrail[3].action"));
        assertEquals("audience-new-users", JsonPath.read(response.body(), "$.auditTrail[4].componentId"));
        assertEquals("DELETED", JsonPath.read(response.body(), "$.auditTrail[4].action"));
    }

    @Test
    void servesFixtureJsonForInspection() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/migration/fixtures/clean_chain.json"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("clean_chain", JsonPath.read(response.body(), "$.scenario"));
    }

    private HttpResponse<String> execute(String journeyJson) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/migration/execute"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(journeyJson))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String loadJourney(String fixtureName) throws IOException {
        String resourceName = "/fixtures/" + fixtureName;
        try (InputStream input = getClass().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Fixture not found: " + resourceName);
            }
            String fixtureJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Object journey = JsonPath.parse(fixtureJson).read("$.journey");
            return JsonPath.parse(journey).jsonString();
        }
    }
}
