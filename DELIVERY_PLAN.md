# Zenara Health Sandbox Migration Engine - Delivery Plan & Factual Snapshot (Part B)

## B1 — Factual Snapshot
* **Team Structure & Authority:** A team of 5 engineers (1 tech lead/architect, 3 backend engineers, 1 QA engineer) over ~14 months. Decision authority: Architectural choices and schema boundaries required tech lead sign-off; implementation details and component-level refactors were autonomous.
* **Redesign to First Customer Migration:** Calendar duration of 5.5 months from redesign kickoff to live migration.
* **Migration Success Metrics:** ~10% to ~95% metric: Success was defined strictly as *journey verified runnable in the target* (not just migration completion). Across ~1,200 migrations over a 6-month window, the remaining ~5% failures were primarily due to stale downstream reference schemas and transient timeout conditions during peak parallel execution.
* **The Production Failure (Shared Context Object):**
    * *Origin:* Introduced by a backend engineer during a microservice optimization refactor to reduce memory allocation overhead in the event/custom action handlers.
    * *Detection:* First reported 3 weeks post-deployment when enterprise tenants experienced intermittent 500 errors and silent data corruption during high-concurrency batch migrations. Root cause identified 6 hours post-report.
    * *Local Reproducibility Failure:* Did not reproduce locally because single-user test runs never saturated the shared event-loop pool or triggered race conditions on mutable thread-local/singleton caches.
    * *Personal Accountability:* Approved the PR review without enforcing thread-safety immutability guarantees on the shared context beans.
    * *Prevention Control:* Enforced static analysis rules (`SpotBugs`) blocking mutable static/singleton fields across service beans, supplemented by rigorous integration tests running deterministic parallel executors.
* **Refused Request:** Refused an unbuffered batch-bypass request from Product Management to skip post-migration verification for speed during maintenance windows. Cost: Delayed release by 2 days while reinforcing the non-negotiable verification invariant.

---

## B2 — The Constrained Release (Day 1–6 Plan)

### Day 1–6 Sequence
* **Day 1:**
    * *Owner:* Human Backend Engineer + AI Coding Agent.
    * *Input/Output:* Requirements spec $\rightarrow$ Core audit model extensions for anonymized transaction logging.
    * *Verification:* Unit tests verifying zero PII in audit payloads. Merge order: PR #1 (Audit Schema).
* **Day 2:**
    * *Owner:* Human Backend Engineer.
    * *Input/Output:* AuditLogService integration $\rightarrow$ Thread-safe logging stream.
    * *Verification:* Concurrent execution unit tests. Merge order: PR #2 (Audit Service).
* **Day 3 (The Constraint Point):**
    * *Owner:* Tech Lead (You) + Compliance Reviewer Agent.
    * *Input/Output:* Shared runtime API blocker notice $\rightarrow$ Execution cut list & mitigation routing.
    * *Verification:* Compliance sign-off on anonymized log format. Merge order: PR #3 (Isolation wrapper).
* **Day 4:**
    * *Owner:* QA Engineer (50% capacity) + AI Agent.
    * *Input/Output:* Isolated target-write adapter $\rightarrow$ Local fallback write channel.
    * *Verification:* Integration tests with mock API failure. Merge order: PR #4 (Fallback Adapter).
* **Day 5:**
    * *Owner:* QA Engineer + Human Engineer.
    * *Input/Output:* End-to-end fixtures $\rightarrow$ 6 fixture verification suites.
    * *Verification:* Automated test run exiting non-zero on failure. Merge order: PR #5 (Test Suite).
* **Day 6:**
    * *Owner:* Tech Lead + Compliance Sign-off.
    * *Input/Output:* Final audit bundle $\rightarrow$ Tenant sandbox enablement.
    * *Verification:* 1-hour rollback dry run. Merge order: PR #6 (Production Gate).

### Must / Should / Will-Not-Ship Cut List
* **WILL NOT SHIP (Cut):** Automated real-time streaming analytics dashboard for migration telemetry.
    * *Numeric Consequence:* Forces internal operators to rely on JSON audit log dumps for 2 weeks post-launch, adding ~1.5 hours of manual log parsing per week.
* **MUST SHIP:** 2-Phase LIFO rollback and post-migration verification guard.
* **SHOULD SHIP:** Parallel worker thread pool optimization.

### Day 3 Control Artifact (Status Table)
| Blocker / Item | Status | Owner | Evidence Type | Next Gate | ETA | Blocker Open Duration |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Compliance Audit Log | Active | QA Engineer | Anonymized JSON Schema | Reviewer Sign-off | End of Day 4 | 6 hours |
| Shared Write API Delay | Blocked | External Team | Written Notice | Fallback Adapter Merge | End of Day 4 | 12 hours |

### Release Rule
* **Condition:** Path is enabled for tenant *only when* code is merged, the journey has passed automated target verification, and compliance sign-off is logged.
* **Ownership:** Tech Lead owns the release gate; QA Engineer can halt for compliance; Security can halt for audit gaps.

### Top 2 Risks & Early Signals
1. *Risk:* Target write API deadlock during parallel health data migration.
    * *Signal:* Elevated thread wait times in `MigrationEngine` execution logs.
    * *Halt Authority:* Tech Lead.
2. *Risk:* Compliance reviewer rejects anonymized log payload format.
    * *Signal:* QA compliance check failure on sample payload.
    * *Halt Authority:* QA Engineer.

### Founder Update
Regulated health tenant migration path remains on track for Day 6 despite shared service API delays. We have cut the real-time analytics dashboard to reallocate engineering bandwidth toward an isolated fallback write adapter. Compliance review for anonymized audit logging is active and on schedule. The core invariants—zero unverified migrations and 1-hour rollback reversibility—are fully preserved. No executive decision required at this stage; requesting awareness only.