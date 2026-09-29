# AI ENGINEERING GUIDELINES — Zenara Case Assessment

## 1. Scope & Execution Boundaries
- **Time Target:** ~3.5 Hours (Hard Cap: 4 Hours).
- **Core Stack:** Java 21 + Spring Boot + Lombok + Jackson + JUnit 5.
- **Orchestration:** Pure In-Memory Parallel DAG Scheduler (`CompletableFuture` + `ExecutorService`). NO Temporal, Kafka, or heavy external frameworks.
- **Persistence:** In-Memory / Local JSON Mock Repositories (`MockSourceSandbox`, `MockTargetSandbox`).

## 2. Core Invariants & System Rules
1. **Transactional Invariant:** After any migration attempt, the Target Sandbox must be either **100% Verified Runnable** or **Identical to its Pre-Attempt State (Clean Rollback)**.
2. **No Success by Default:** If the engine cannot establish either outcome, it MUST REFUSE and report why. "Completed" is never reported for an unverified journey.
3. **Target-Based Verification:** Verification MUST inspect the Target Sandbox storage directly—never trust in-memory migrator logs.

## 3. Domain Component Types
Every Journey is composed of 4 concrete component types referencing one another:
1. `AUDIENCE`: Base profile/segment entry (0 dependencies).
2. `INLINE_CAMPAIGN`: Message wrapper for Email/SMS/Reminder channels (Depends on `AUDIENCE`).
3. `EVENT`: Listens for user interactions or reaction timeouts (Depends on `INLINE_CAMPAIGN`).
4. `CUSTOM_ACTION`: External webhook or execution trigger (Depends on `EVENT`).

## 4. Key Engine Mechanics
- **Graph Solver:** Derived dynamically via Kahn's Topological Sort Algorithm.
- **Cycles & Missing Dependencies:** Refuse migration before writing if a cycle or dangling reference exists.
- **Target Content Conflicts:** If a component exists in the target sandbox with a different `payloadContentHash`, REJECT migration to prevent overwriting target states.
- **Rollback Engine:** If component $k$ fails during migration, components $1..k-1$ are removed from the target sandbox. Handle failure during rollback explicitly (`ROLLBACK_FAILED`).
- **Parallel Scheduler:** Independent branches migrate concurrently. Protect against shared mutable state corruption with deterministic thread locking.

## 5. Review UI (Spring Boot Web)
- Single-page application serving `/journey/{id}?view=plan|result|diff&sort=name`.
- Deep-links preserve state; back/forward navigation updates without full page reloads (`pushState` / `popstate`).

## 6. Required Fixtures (JSON)
1. `clean_chain.json`: Linear success path.
2. `diamond_parallel.json`: Parallel branch graph.
3. `cycle_or_missing.json`: Invalid graph refused pre-write.
4. `midway_failure_rollback.json`: Fails at step $k$, triggers clean rollback.
5. `silent_partial_failure.json`: Writes succeed, but verifier catches broken target references.
6. `existing_target_conflict.json`: Target component exists with conflicting payload hash.