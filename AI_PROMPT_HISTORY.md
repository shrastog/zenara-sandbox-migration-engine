# Zenara Health Sandbox Migration Engine - AI Prompt History & Collaboration Log

This document records the prompt orchestration and AI collaboration strategy used during the development of the Sandbox Migration Engine. GitHub Copilot (Agent Mode) was leveraged as an engineering pair-programmer following strict test-driven and bottom-up architectural principles.

---

## Phase 1: Core Domain & Kahn's Topological Solver
* **Objective:** Establish dependency graph resolution using Kahn's algorithm and core domain models.
* **Prompt Strategy:** Instructed the AI to implement strict acyclic dependency validation, handling component ordering cleanly.
* **Key Validation:** Ensured circular dependency detection throws appropriate runtime exceptions.

## Phase 2: Target Sandbox Service & Mock Integration
* **Objective:** Implement stubbed target sandbox provisioning and write operations.
* **Prompt Strategy:** Focused on defining clear service interfaces (`TargetSandboxService`) to simulate real-world API latency and failure points.

## Phase 3: Migration Engine & 2-Phase LIFO Rollback
* **Objective:** Build the core orchestration state machine handling transactional execution and automated cleanups.
* **Prompt Strategy:** Required a 2-Phase LIFO rollback strategy where any midway failure triggers sequential reverse-order deletions of successfully applied components.

## Phase 4.1: Audit Logging & Metrics
* **Objective:** Introduce step-by-step audit logging and execution duration tracking.
* **Prompt Strategy:** Staged audit integration *after* core engine stability to prevent domain model churn. Requested thread-safe logging (`AuditLogService`) and structured response payloads (`MigrationResponse`).

## Phase 4.2: REST Controller & Integration Tests
* **Objective:** Expose clean HTTP endpoints and verify end-to-end functionality via Spring MockMvc.
* **Prompt Strategy:** Requested `@RestController` mapping (`/api/migration/execute` and `/api/migration/fixtures/{name}`) alongside integration test coverage verifying both `SUCCESS` and `ROLLED_BACK` states.