# Support Ticket Management System — Implementation Plan

**Status:** plan only; no application code in this document  
**Sources:** `spec/requirements.md`, `spec/architecture.md`, `spec/data-model.md`, `spec/state-machine.md`, `spec/ui-flow.md`, `spec/test-strategy.md`  
**Not present:** `spec/api-contract.md` (still blocking for exact paths and full error schema beyond lifecycle codes)

This plan splits work into small, independently reviewable tasks. **Do not treat this plan as a product-requirements document.** Unresolved product decisions remain unresolved (`spec/data-model.md` §10, `spec/architecture.md` §16). Tasks that touch those seams must implement the **seam**, not invent the missing rule.

Suggested layout (`spec/architecture.md` §14):

```
backend/     Spring Boot
frontend/    Next.js (React)
spec/        specifications and this plan
```

Illustrative REST mapping is from `spec/architecture.md` §3 until an API contract exists. Implementers must not invent a second status-write path.

---

## Blocking before code (not implementation tasks)

These are **not** silently decided here. Starting BE-08 / BE-18 / FE-02 without them will force unapproved guesses.

| ID | Decision still required | Blocks |
| --- | --- | --- |
| D-1 | `spec/api-contract.md` (paths, verbs, query names, resource JSON, pagination) | BE-18+, FE-02+ |
| D-2 | Ticket `id` type (UUID vs identity vs display code) | BE-08, BE-09 |
| D-3 | Title/description/priority/assignee required vs optional, lengths, priority value set | BE-12, BE-18, FE-07 |
| D-4 | Search semantics (fields, case, partial vs exact) | BE-10, BE-14, FE-05 |
| D-5 | Authn/authz or explicit “no auth in v1” | BE-02, production CORS |
| D-6 | Optimistic locking vs last-write-wins | BE-09, SM-34 |
| D-7 | Flyway vs Liquibase; H2 PostgreSQL-compat vs later Testcontainers | BE-08 |
| D-8 | Browser CORS vs Next.js proxy | BE-02, FE-01 |

**Allowed without those decisions:** BE-01–BE-07 (domain + policy with the **approved** five statuses and 25-pair table). Status labels and transitions are specified.

Lifecycle error codes already specified (`spec/state-machine.md` §7): `ILLEGAL_STATUS_TRANSITION`, `TICKET_NOT_FOUND`, `INVALID_REQUEST`, `INVALID_STATUS_VALUE`, `STATUS_NOT_UPDATABLE`.

---

## Recommended order

```
D-* (as needed)
→ BE-01 → BE-02
→ BE-03 → BE-04 → BE-05 → BE-06 → BE-07
→ BE-08 → BE-09 → BE-10
→ BE-11 → BE-12 → BE-13
→ BE-14 → BE-15 → BE-16 → BE-17
→ BE-18 → BE-19 → BE-20 → BE-21
→ BE-22 → BE-23 → BE-24
→ FE-01 → FE-02 → FE-03
→ FE-04 → FE-05 → FE-06
→ FE-07 → FE-08 → FE-09 → FE-10 → FE-11
→ TE-01 … TE-05 (may overlap the later BE/FE tasks as listed)
```

Frontend must not start calling real endpoints until BE-18+ exist **or** FE-02 is built against mocks that match the (future) contract.

---

## Backend

### BE-01 — Project setup

| | |
| --- | --- |
| **Task ID** | BE-01 |
| **Spec refs** | `architecture.md` §12, §14, §15; `requirements.md` (no secrets) |
| **Files likely to change** | `backend/pom.xml` or `build.gradle`; `backend/src/main/java/**/Application.java`; `.gitignore`; `README` only if already used for layout (do not invent product docs) |
| **Objective** | Create a Spring Boot application skeleton with Java, test runner, and empty package layout (api, application, domain, persistence). No business logic. |
| **Dependencies** | None |
| **Tests required** | Context-load smoke test (optional) |
| **Completion** | Project builds; no credentials in repo; packages exist |

### BE-02 — Configuration

| | |
| --- | --- |
| **Task ID** | BE-02 |
| **Spec refs** | `architecture.md` §11–12, §15; `test-strategy.md` §1 (H2 for tests) |
| **Files likely to change** | `application.yml`; `application-local.yml.example` (no secrets); `application-prod.yml` placeholders; `application-test.yml`; CORS config class |
| **Objective** | Profiles `local`/`prod` → PostgreSQL via **environment variables**; `test` → H2. CORS allow-list from config, not `*`. TLS not disabled in prod profile. |
| **Dependencies** | BE-01 |
| **Tests required** | Test profile starts with H2; fail-fast if required env vars missing in prod (if implemented) |
| **Completion** | No hardcoded passwords; test profile does not need Postgres |

### BE-03 — Domain model (types)

| | |
| --- | --- |
| **Task ID** | BE-03 |
| **Spec refs** | `state-machine.md` §1; `data-model.md` §5 |
| **Files likely to change** | `backend/.../domain/TicketStatus.java` (or equivalent) |
| **Objective** | Encode the five **approved** status labels: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`. Case-sensitive. Do **not** add unapproved priority enums as product law. |
| **Dependencies** | BE-01 |
| **Tests required** | Enum/values equal the five strings |
| **Completion** | No sixth status; no `open` lowercase alias |

### BE-04 — State-machine logic (policy)

| | |
| --- | --- |
| **Task ID** | BE-04 |
| **Spec refs** | `state-machine.md` §3, §3.1, §5 |
| **Files likely to change** | `backend/.../domain/TicketStatusPolicy.java` (name indicative) |
| **Objective** | Pure function: `(current, requested) → allowed?` for all 25 pairs. Unknown labels are **not** policy rows (`INVALID_STATUS_VALUE` later). |
| **Dependencies** | BE-03 |
| **Tests required** | Unit: all 25 rows (`test-strategy.md` §2 / `state-machine.md` §8.1) |
| **Completion** | Exactly five true edges; 20 false including same-state and `RESOLVED → CANCELLED` |

### BE-05 — Domain model (ticket aggregate)

| | |
| --- | --- |
| **Task ID** | BE-05 |
| **Spec refs** | `architecture.md` §5.1; `state-machine.md` §2, §4; `data-model.md` §2 |
| **Files likely to change** | `backend/.../domain/Ticket.java` |
| **Objective** | Ticket created in `OPEN`. Status changes **only** via `transitionTo`. No public status setter for services/controllers. Field updates do not set status. |
| **Dependencies** | BE-04 |
| **Tests required** | Unit: create → `OPEN`; valid transition mutates; invalid leaves status unchanged |
| **Completion** | Aggregate cannot be status-patched without the policy |

### BE-06 — Domain model (comment)

| | |
| --- | --- |
| **Task ID** | BE-06 |
| **Spec refs** | `data-model.md` §3; `architecture.md` §5.1 |
| **Files likely to change** | `backend/.../domain/Comment.java` (or ticket collection) |
| **Objective** | Comment belongs to one ticket; adding a comment is not a status change. Do not add edit/delete APIs. |
| **Dependencies** | BE-05 |
| **Tests required** | Unit: comment attached; ticket status unchanged |
| **Completion** | No comment lifecycle beyond add |

### BE-07 — Domain exceptions

| | |
| --- | --- |
| **Task ID** | BE-07 |
| **Spec refs** | `state-machine.md` §7 |
| **Files likely to change** | `backend/.../domain/*Exception.java` |
| **Objective** | Distinct domain failures: illegal transition (carry current + requested), ticket not found, invalid status value. No HTTP types in domain. |
| **Dependencies** | BE-03 |
| **Tests required** | Unit: exception payload fields present |
| **Completion** | Mapping to HTTP happens only in BE-13 |

### BE-08 — Persistence (schema)

| | |
| --- | --- |
| **Task ID** | BE-08 |
| **Spec refs** | `data-model.md` §2–3, §7; `architecture.md` §7 |
| **Files likely to change** | Migration files under `backend/src/main/resources/db/migration/` (tool per D-7) |
| **Objective** | Tables `ticket` and `comment`; PK; FK `comment.ticket_id`; `status` NOT NULL + CHECK/enum of the **five** labels; timestamps. **Do not** invent approved priority CHECK list or User FK. Assignee/priority columns only as **seams** in `data-model.md`. |
| **Dependencies** | BE-02, D-2, D-7 |
| **Tests required** | Migration applies on H2 (and documented Postgres) |
| **Completion** | No `ddl-auto=update` for prod; no secrets in migrations |

### BE-09 — Persistence (mappings)

| | |
| --- | --- |
| **Task ID** | BE-09 |
| **Spec refs** | `data-model.md` §2–3; `architecture.md` §6 |
| **Files likely to change** | JPA entities (may wrap domain or live in persistence package per chosen style) |
| **Objective** | Map ticket/comment; status stored as approved labels; optional `version` **only if D-6 confirmed**. |
| **Dependencies** | BE-05, BE-06, BE-08 |
| **Tests required** | Round-trip save/load on H2 (`test-strategy.md` §4) |
| **Completion** | Mapping does not implement transitions |

### BE-10 — Repositories

| | |
| --- | --- |
| **Task ID** | BE-10 |
| **Spec refs** | `architecture.md` §6; `data-model.md` §2.4, §3.4 |
| **Files likely to change** | `TicketRepository`, `CommentRepository` (indicative) |
| **Objective** | Load/save; list; **filter by status**; keyword search **once D-4 is known** (until then, implement a documented placeholder query that will be replaced—do not claim search “done”). No `updateStatusById`. |
| **Dependencies** | BE-09; D-4 for completing search |
| **Tests required** | Repository tests: save, find, status filter; search cases from `test-strategy.md` §12 **after D-4** |
| **Completion** | No transition logic in repository |

### BE-11 — Repository / data tests (standalone review)

| | |
| --- | --- |
| **Task ID** | BE-11 |
| **Spec refs** | `test-strategy.md` §4, §12, §13 |
| **Files likely to change** | `backend/src/test/java/**/repository/*` |
| **Objective** | Dedicated H2 data tests: constraints, FK, status CHECK, session reload. |
| **Dependencies** | BE-10 |
| **Tests required** | This task **is** the tests |
| **Completion** | Illegal status label cannot persist; comments require a ticket |

### BE-12 — Validation (API + application)

| | |
| --- | --- |
| **Task ID** | BE-12 |
| **Spec refs** | `architecture.md` §8; `state-machine.md` §2, §4, §7.3; `data-model.md` §7.2 |
| **Files likely to change** | Request records/DTOs; bean-validation annotations; service guards |
| **Objective** | Create/update request types **omit `status`**. Presence of `status` → 400 `STATUS_NOT_UPDATABLE` (mapped in BE-13). Transition body: requested status required. Unknown status label → not a 409. Field rules only as far as **approved** (blank title if still unconfirmed: do not invent max length). |
| **Dependencies** | BE-07 |
| **Tests required** | API/service tests for missing body, blank requested status, `status` on create/update |
| **Completion** | Create/update cannot write status |

### BE-13 — Error handling

| | |
| --- | --- |
| **Task ID** | BE-13 |
| **Spec refs** | `state-machine.md` §7.2–7.3; `architecture.md` §9 |
| **Files likely to change** | `@ControllerAdvice` (indicative); error JSON records |
| **Objective** | Map domain/validation exceptions: 400 / 404 / 409 as specified; JSON includes `code`, `message`; 409 includes `currentStatus`, `requestedStatus`. No stack traces, SQL, or secrets. |
| **Dependencies** | BE-07, BE-12 |
| **Tests required** | Controller/advice tests for each `code` string (exact) |
| **Completion** | One shape for these codes; UI-safe messages |

### BE-14 — Services (create, get, list, search, filter)

| | |
| --- | --- |
| **Task ID** | BE-14 |
| **Spec refs** | `architecture.md` §4; `requirements.md` create/list/search/filter |
| **Files likely to change** | `TicketService` (indicative) |
| **Objective** | Transactional create (always `OPEN`); get-or-not-found; list; status filter; search per D-4. Controllers not called. |
| **Dependencies** | BE-05, BE-10, BE-12 |
| **Tests required** | Service tests with fake/real repo (`test-strategy.md` §3) |
| **Completion** | Create never persists non-`OPEN` |

### BE-15 — Services (field update + assignee)

| | |
| --- | --- |
| **Task ID** | BE-15 |
| **Spec refs** | `architecture.md` §3–4; `state-machine.md` §4; `ui-flow.md` §8–9 |
| **Files likely to change** | `TicketService` update method |
| **Objective** | Update title, description, priority, assignee only. Reject `status` in this use case. Status unchanged after success. |
| **Dependencies** | BE-14 |
| **Tests required** | Service: fields change; status same; status-in-payload rejected |
| **Completion** | No combined “save including status” |

### BE-16 — Services (comments)

| | |
| --- | --- |
| **Task ID** | BE-16 |
| **Spec refs** | `data-model.md` §3; `ui-flow.md` §10 |
| **Files likely to change** | `TicketService` or `CommentService` in same transaction |
| **Objective** | Add comment to existing ticket;  not-found if ticket missing; status unchanged. |
| **Dependencies** | BE-06, BE-14 |
| **Tests required** | Service: comment persisted; missing ticket fails |
| **Completion** | No comment edit/delete |

### BE-17 — Services (state-machine use case)

| | |
| --- | --- |
| **Task ID** | BE-17 |
| **Spec refs** | `architecture.md` §10; `state-machine.md` §4, §7; `test-strategy.md` §3 |
| **Files likely to change** | `TicketService.transition` (indicative) |
| **Objective** | Load ticket; `transitionTo`; save only on success. Invalid → domain exception, no persist of new status, `updated_at` unchanged. |
| **Dependencies** | BE-05, BE-14, BE-07 |
| **Tests required** | Service: five valid edges; representative invalid edges; no repository `setStatus` |
| **Completion** | Single write path for status |

### BE-18 — REST controllers (create, list, get)

| | |
| --- | --- |
| **Task ID** | BE-18 |
| **Spec refs** | `architecture.md` §3, §11; `ui-flow.md` §3–4, §7; **paths: D-1** |
| **Files likely to change** | `TicketController`; request/response JSON types |
| **Objective** | HTTP adapters only. Create omits status. List/search/filter query params per contract. Get returns ticket + comments. |
| **Dependencies** | BE-13, BE-14, D-1 |
| **Tests required** | MockMvc/API tests: 2xx create/list/get; 404 get; 400 create with `status` (SM-33) |
| **Completion** | Controller does not call repositories or `transitionTo` |

### BE-19 — REST controllers (update)

| | |
| --- | --- |
| **Task ID** | BE-19 |
| **Spec refs** | `state-machine.md` §4, §7.3; `ui-flow.md` §8 |
| **Files likely to change** | `TicketController` PATCH/PUT (per contract) |
| **Objective** | Field-update endpoint; `status` property → 400 `STATUS_NOT_UPDATABLE`. |
| **Dependencies** | BE-15, BE-18 |
| **Tests required** | API: success; SM-28 |
| **Completion** | Cannot close a ticket via update |

### BE-20 — REST controllers (comments)

| | |
| --- | --- |
| **Task ID** | BE-20 |
| **Spec refs** | `architecture.md` §3; `ui-flow.md` §10 |
| **Files likely to change** | Nested comment endpoint |
| **Objective** | POST comment on ticket; 404 if missing; 400 invalid body. |
| **Dependencies** | BE-16, BE-18 |
| **Tests required** | API create comment; 404; 400 |
| **Completion** | Comment does not change status (assert via GET) |

### BE-21 — REST controllers (transitions)

| | |
| --- | --- |
| **Task ID** | BE-21 |
| **Spec refs** | `state-machine.md` §7.2–7.4; `architecture.md` §10 |
| **Files likely to change** | Dedicated transition endpoint |
| **Objective** | Accept requested status only; 200 + ticket body on success; 409 body on illegal edge. |
| **Dependencies** | BE-17, BE-13, BE-18 |
| **Tests required** | API slice for 200/400/404/409 mapping (full table in BE-24) |
| **Completion** | No other endpoint writes status |

### BE-22 — Architecture-guard tests

| | |
| --- | --- |
| **Task ID** | BE-22 |
| **Spec refs** | `test-strategy.md` §7.5; `architecture.md` §2, §10, §13 |
| **Files likely to change** | `backend/src/test/java/**/architecture/*` |
| **Objective** | AG-1–AG-4: controllers ↛ repositories; no public status setter used as API; no ad-hoc status SQL/update. |
| **Dependencies** | BE-18, BE-21 (packages stable) |
| **Tests required** | This task **is** the tests |
| **Completion** | Build fails if bypass layers are introduced |

### BE-23 — Integration tests (CRUD, search, filter, persistence)

| | |
| --- | --- |
| **Task ID** | BE-23 |
| **Spec refs** | `test-strategy.md` §6, §11–13; `requirements.md` acceptance |
| **Files likely to change** | `backend/src/test/java/**/integration/*` |
| **Objective** | HTTP + H2: create, list, details, update, assignee, comments, filter; validation 400; missing 404; restart/session durability. Search assertions complete only after D-4. |
| **Dependencies** | BE-18–BE-20, BE-10 |
| **Tests required** | This task **is** the tests |
| **Completion** | Data readable after new persistence context |

### BE-24 — State-machine integration tests

| | |
| --- | --- |
| **Task ID** | BE-24 |
| **Spec refs** | `state-machine.md` §8.2; `test-strategy.md` §7 (SM-1–SM-33; SM-34 if D-6) |
| **Files likely to change** | `backend/src/test/java/**/statemachine/*` |
| **Objective** | Real HTTP + H2 + domain: five valid edges, full path, restart, required invalid examples, skips/backward/terminal/same-state, bypass create/update. |
| **Dependencies** | BE-21, BE-23 |
| **Tests required** | This task **is** the tests; **not** a copied policy in a fake |
| **Completion** | `test-strategy.md` §7 suite green except SM-34 if locking not confirmed |

---

## Frontend

### FE-01 — Project setup

| | |
| --- | --- |
| **Task ID** | FE-01 |
| **Spec refs** | `architecture.md` §11, §14; `ui-flow.md` §1 |
| **Files likely to change** | `frontend/` Next.js app; env example for API base URL (no secrets) |
| **Objective** | App Router or Pages **per D-8 / architecture confirmation**; routes stubs: list, new, details, edit. No ticket logic. |
| **Dependencies** | D-8 (or documented temporary same-origin proxy) |
| **Tests required** | App builds; smoke render of shell |
| **Completion** | API URL from env; no hardcoded prod hosts with credentials |

### FE-02 — API client

| | |
| --- | --- |
| **Task ID** | FE-02 |
| **Spec refs** | `architecture.md` §11; `state-machine.md` §7; **D-1** |
| **Files likely to change** | `frontend/lib/api.ts` (indicative) |
| **Objective** | Typed functions for create, list/search/filter, get, update (no status), comment, **transition**. Parse error JSON (`code`, `message`, 409 fields). |
| **Dependencies** | FE-01, D-1 (or freeze illustrative paths matching BE-18–21) |
| **Tests required** | Unit: maps 409 body; never puts `status` on create/update payloads |
| **Completion** | Single module used by all screens |

### FE-03 — Error handling (UI)

| | |
| --- | --- |
| **Task ID** | FE-03 |
| **Spec refs** | `ui-flow.md` §12–15; `requirements.md` (meaningful errors) |
| **Files likely to change** | Shared error banner / field-error helper |
| **Objective** | Display API `message`; distinguish empty vs error vs loading. No stack traces. |
| **Dependencies** | FE-02 |
| **Tests required** | Frontend: 400/404/409/500/network fixtures (`test-strategy.md` §8, §14) |
| **Completion** | Reusable; used by later screens |

### FE-04 — Ticket list

| | |
| --- | --- |
| **Task ID** | FE-04 |
| **Spec refs** | `ui-flow.md` §4, §14–15 |
| **Files likely to change** | List page/component |
| **Objective** | Load list; loading then rows or empty (not false empty); row → details. No status dropdown on rows. |
| **Dependencies** | FE-02, FE-03 |
| **Tests required** | Frontend: loading, rows, empty, error+retry |
| **Completion** | Matches empty ≠ error |

### FE-05 — Search

| | |
| --- | --- |
| **Task ID** | FE-05 |
| **Spec refs** | `ui-flow.md` §5; `test-strategy.md` §12 |
| **Files likely to change** | List page search control |
| **Objective** | Send keyword to backend; replace results; search-empty copy. Do not client-filter a stale full list as success. |
| **Dependencies** | FE-04, D-4 for asserting match rules |
| **Tests required** | Frontend: keyword sent; zero hits; error |
| **Completion** | Backend remains search authority |

### FE-06 — Status filter

| | |
| --- | --- |
| **Task ID** | FE-06 |
| **Spec refs** | `ui-flow.md` §6; `state-machine.md` §1 |
| **Files likely to change** | List filter control |
| **Objective** | Filter `all` + five statuses; calls list API not transition API; filter-empty copy. |
| **Dependencies** | FE-04 |
| **Tests required** | Frontend: query param sent; no transition call |
| **Completion** | “All” is not a ticket status |

### FE-07 — Ticket creation

| | |
| --- | --- |
| **Task ID** | FE-07 |
| **Spec refs** | `ui-flow.md` §3; `state-machine.md` §2 |
| **Files likely to change** | Create page/form |
| **Objective** | Form without status control; submit without `status`; 2xx → details; 4xx stay + errors; disable double submit. |
| **Dependencies** | FE-02, FE-03, D-3 for required fields |
| **Tests required** | Frontend: payload has no `status`; error preserve fields |
| **Completion** | Shown status after create comes from server (`OPEN`) |

### FE-08 — Ticket details

| | |
| --- | --- |
| **Task ID** | FE-08 |
| **Spec refs** | `ui-flow.md` §7 |
| **Files likely to change** | Details page |
| **Objective** | GET ticket + comments; 404 not-found; comments empty state; read-only status from server; links to edit. |
| **Dependencies** | FE-02, FE-03 |
| **Tests required** | Frontend: render fields; 404; comments empty |
| **Completion** | Mutations disabled while loading |

### FE-09 — Editing (including assignee)

| | |
| --- | --- |
| **Task ID** | FE-09 |
| **Spec refs** | `ui-flow.md` §8–9 |
| **Files likely to change** | Edit page |
| **Objective** | Edit title/description/priority/assignee; status read-only or omitted; update API without `status`. |
| **Dependencies** | FE-08 |
| **Tests required** | Frontend: no status in body; 400 keeps form |
| **Completion** | Edit cannot transition |

### FE-10 — Comments

| | |
| --- | --- |
| **Task ID** | FE-10 |
| **Spec refs** | `ui-flow.md` §10 |
| **Files likely to change** | Comment composer on details |
| **Objective** | Post comment; clear on 2xx only; keep text on error; status unchanged. |
| **Dependencies** | FE-08 |
| **Tests required** | Frontend: success append; error keep text |
| **Completion** | No comment edit/delete UI |

### FE-11 — Status transition UI

| | |
| --- | --- |
| **Task ID** | FE-11 |
| **Spec refs** | `ui-flow.md` §11; `state-machine.md` §5 |
| **Files likely to change** | Details status actions |
| **Objective** | Show **only** suggested actions from `ui-flow.md` §11.2 (must match policy). Call **transition** API. 200 → new status + new actions. 409 → show `message`, restore `currentStatus`. Terminal: no actions. Never a five-status dropdown. |
| **Dependencies** | FE-08, FE-02, FE-03 |
| **Tests required** | Frontend: actions per status; 200 update; 409 handling; no optimistic status |
| **Completion** | UI is not the enforcement authority |

---

## Testing (cross-cutting review units)

Implementation tasks already include tests. These tasks are **reviewable test deliveries** so coverage is not lost in feature PRs.

### TE-01 — Unit tests (domain)

| | |
| --- | --- |
| **Task ID** | TE-01 |
| **Spec refs** | `test-strategy.md` §2; `state-machine.md` §8.1 |
| **Files likely to change** | `backend/src/test/java/**/domain/*` |
| **Objective** | Complete 25-pair policy + aggregate tests if not already merged with BE-04/BE-05. |
| **Dependencies** | BE-04, BE-05 |
| **Tests required** | This task **is** the tests |
| **Completion** | 5 valid / 20 invalid asserted |

### TE-02 — API tests (controllers)

| | |
| --- | --- |
| **Task ID** | TE-02 |
| **Spec refs** | `test-strategy.md` §5 |
| **Files likely to change** | `backend/src/test/java/**/api/*` |
| **Objective** | Contract tests for all endpoints and error codes, including create/update `status` rejection. |
| **Dependencies** | BE-18–BE-21, BE-13 |
| **Tests required** | This task **is** the tests |
| **Completion** | Exact `code` strings |

### TE-03 — Integration tests

| | |
| --- | --- |
| **Task ID** | TE-03 |
| **Spec refs** | `test-strategy.md` §6 |
| **Files likely to change** | Same area as BE-23 or a checklist PR if BE-23 already landed |
| **Objective** | Ensure BE-23 covers create/list/details/update/assignee/comment/filter/validation/404/persistence. Search after D-4. |
| **Dependencies** | BE-23 |
| **Tests required** | Gap-fill only |
| **Completion** | Traceability table `test-strategy.md` §10 backend rows satisfied |

### TE-04 — State-machine tests

| | |
| --- | --- |
| **Task ID** | TE-04 |
| **Spec refs** | `test-strategy.md` §7; `state-machine.md` §8.2 |
| **Files likely to change** | Same as BE-24 |
| **Objective** | Confirm SM-1–SM-33 (and SM-34 iff D-6). Architecture guards AG-1–AG-4 present (BE-22). |
| **Dependencies** | BE-22, BE-24 |
| **Tests required** | This task **is** verification |
| **Completion** | Illegal examples `CLOSED→OPEN`, `RESOLVED→OPEN`, `CANCELLED→OPEN` fail closed with 409 |

### TE-05 — Frontend tests

| | |
| --- | --- |
| **Task ID** | TE-05 |
| **Spec refs** | `test-strategy.md` §8, §14; `ui-flow.md` |
| **Files likely to change** | `frontend/**/*.test.*` |
| **Objective** | Consolidate screen tests: list/search/filter/create/details/edit/comment/transitions/errors. |
| **Dependencies** | FE-04–FE-11 |
| **Tests required** | This task **is** the tests |
| **Completion** | 409 shown; suggested actions only; no status on create/update |

### TE-06 — End-to-end (optional, later)

| | |
| --- | --- |
| **Task ID** | TE-06 |
| **Spec refs** | `test-strategy.md` §9 |
| **Files likely to change** | `e2e/` or Playwright specs |
| **Objective** | Browser + real backend + PostgreSQL: happy path, cancel path, search/filter, restart persistence, meaningful error if backend down. Does **not** replace TE-04. |
| **Dependencies** | BE-24, FE-11, local Postgres |
| **Tests required** | This task **is** the tests |
| **Completion** | Documented how to run; secrets not in repo |

---

## Task index (dependency-respecting)

| Order | ID | Area |
| --- | --- | --- |
| 1 | BE-01 | Backend setup |
| 2 | BE-02 | Configuration |
| 3 | BE-03 | Domain types |
| 4 | BE-04 | State-machine policy |
| 5 | BE-05 | Ticket aggregate |
| 6 | BE-06 | Comment |
| 7 | BE-07 | Domain exceptions |
| 8 | BE-08 | Schema |
| 9 | BE-09 | Mappings |
| 10 | BE-10 | Repositories |
| 11 | BE-11 | Data tests |
| 12 | BE-12 | Validation |
| 13 | BE-13 | Error handling |
| 14 | BE-14 | Service read/create/list |
| 15 | BE-15 | Service update |
| 16 | BE-16 | Service comments |
| 17 | BE-17 | Service transition |
| 18 | BE-18 | REST create/list/get |
| 19 | BE-19 | REST update |
| 20 | BE-20 | REST comments |
| 21 | BE-21 | REST transition |
| 22 | BE-22 | Architecture guards |
| 23 | BE-23 | Integration tests |
| 24 | BE-24 | SM integration tests |
| 25 | FE-01 | Frontend setup |
| 26 | FE-02 | API client |
| 27 | FE-03 | UI errors |
| 28 | FE-04 | List |
| 29 | FE-05 | Search |
| 30 | FE-06 | Status filter |
| 31 | FE-07 | Create |
| 32 | FE-08 | Details |
| 33 | FE-09 | Edit |
| 34 | FE-10 | Comments |
| 35 | FE-11 | Transition UI |
| 36 | TE-01 | Unit tests |
| 37 | TE-02 | API tests |
| 38 | TE-03 | Integration tests |
| 39 | TE-04 | State-machine tests |
| 40 | TE-05 | Frontend tests |
| 41 | TE-06 | E2E (later) |

TE-01 may land immediately after BE-05 (parallel to persistence). TE-02 after BE-21. FE-01 may start after BE-01 in parallel but must not assume unapproved API paths.

---

## Explicitly out of this plan (unapproved)

Do not implement as if specified: authentication product, comment edit/delete, ticket delete, user directory, pagination/sort UI, DB transition triggers, `allowedTransitions` on GET (optional confirmation in `ui-flow.md` §11.2), priority value CHECK list, display ticket numbers.
