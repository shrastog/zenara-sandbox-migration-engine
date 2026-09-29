# Zenara Sandbox Migration Engine — Audit Log

## Project structure

- `model/` contains journey and component domain types, migration statuses, audit entries, and migration responses.
- `exception/` contains explicit graph validation exceptions.
- `service/GraphSolver` validates dependency references and resolves execution order.
- `service/MigrationEngine` coordinates validation, writes, target verification, audit events, and rollback.
- `service/TargetSandboxService` provides synchronized in-memory target storage and target-based integrity verification.
- `src/main/resources/fixtures/` contains six synthetic success and failure scenarios.
- `src/test/java/` covers graph solving and migration behavior, including rollback and edge cases.

## Dependency resolution

`GraphSolver.resolveExecutionOrder` uses Kahn's algorithm. It indexes components, validates every dependency ID, builds dependency-to-dependent adjacency lists, and computes in-degrees. Components with zero in-degree enter a queue first. Processing each component decrements its dependents' in-degrees; unresolved components indicate a cycle. Missing references and cycles are reported using dedicated exceptions before target writes begin.

## Transaction and rollback architecture

Migration is sequential and has two rollback trigger phases:

1. A component write failure triggers reverse-order deletion of all components newly written during this attempt.
2. Failed target-based post-write integrity verification triggers the same reverse-order cleanup.

The engine preserves pre-existing target entries and compares the final target state to its pre-attempt snapshot. It reports `ROLLED_BACK` only when restoration is confirmed; otherwise it reports `ROLLBACK_FAILED`. A successful response is emitted only after the target sandbox verifies.

## Audit trail and response

Each migration returns a `MigrationResponse` with the journey ID, terminal status, elapsed milliseconds, failure reason when applicable, and an immutable snapshot of audit entries. Entries record write attempts (`APPLIED`), verifier results (`VERIFIED`), and rollback deletions (`DELETED`) with step indices and timestamps. `AuditLogService` synchronizes updates and snapshots for thread-safe access.

## Defensive validation

Graph resolution rejects null journeys/component collections, null components or IDs, duplicate IDs, missing dependencies, and cycles. Fixture readers validate JSON object/array/string shapes. Target access is synchronized, its exposed map is unmodifiable, and rollback checks that the target matches its original state.

## AI tool usage strategy

Copilot Agent Mode was used as an implementation assistant to work from the engineering guidelines, make focused changes across models/services/tests, and iterate based on Maven test feedback. Generated changes were reviewed against the project invariants and exercised through the existing test suite; tool output was not treated as a substitute for code review.

## Test verification

The project test suite is run with `mvn test`. It covers all six JSON fixtures, graph ordering and rejection, target conflicts, idempotent writes, empty journeys, independent roots, silent partial writes, audit/response behavior, and LIFO rollback.
