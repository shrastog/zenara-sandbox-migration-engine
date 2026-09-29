package com.zenarahealth.sbt.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.zenarahealth.sbt.model.Journey;
import com.zenarahealth.sbt.model.MigrationResponse;
import com.zenarahealth.sbt.service.MigrationEngine;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {
    private static final Pattern FIXTURE_NAME = Pattern.compile("[A-Za-z0-9_-]+\\.json");

    private final MigrationEngine migrationEngine;

    public MigrationController(MigrationEngine migrationEngine) {
        this.migrationEngine = migrationEngine;
    }

    @PostMapping("/execute")
    public ResponseEntity<MigrationResponse> executeMigration(@RequestBody Journey journey) {
        return ResponseEntity.ok(migrationEngine.executeMigration(journey));
    }

    @GetMapping(value = "/fixtures/{fixtureName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFixture(@PathVariable String fixtureName) throws IOException {
        if (!FIXTURE_NAME.matcher(fixtureName).matches()) {
            return ResponseEntity.notFound().build();
        }
        ClassPathResource fixture = new ClassPathResource("fixtures/" + fixtureName);
        if (!fixture.exists()) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream input = fixture.getInputStream()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
