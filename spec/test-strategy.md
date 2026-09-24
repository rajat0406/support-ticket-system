# Support Ticket Management System — Test Strategy

**Status:** test specification; no test code  
**Sources:** `spec/requirements.md`, `spec/architecture.md`, `spec/data-model.md`, `spec/state-machine.md`, `spec/ui-flow.md`  
**Constraint:** this document names levels, scope, and cases. It does not contain JUnit, Spring Test, React Testing Library, Playwright, or other implementation.

**Environments:** automated backend tests use **H2** (`spec/architecture.md` §7). Production/local persistence is PostgreSQL. Frontend tests mock HTTP. End-to-end uses a real backend + database (PostgreSQL preferred).

**Oracle for status:** `spec/state-machine.md` is the only authority for legal transitions and error codes for transitions.

---

## 1. Test levels

| Level | Scope | Database | HTTP | UI |
| --- | --- | --- | --- | --- |
| **Unit** | Pure functions, domain policy, small classes | none | none | none |
| **Service / business-rule** | Use-case orchestration, transactions, domain + service | none or in-memory fakes | none | none |
| **Repository / data** | JPA mappings, queries, constraints, H2 behavior | **H2** | none | none |
| **REST controller / API** | Request/response, validation mapping, error JSON | mocked service or slice | MockMvc / WebTestClient | none |
| **Integration** | HTTP → service → repository → commit | **H2** | real or MockMvc | none |
| **State-machine integration** | Transitions through the real backend path | **H2** | yes | none |
| **Architecture-guard** | Layering rules hold; no status-bypass path exists | none | none | none |
| **Frontend** | Rendering, forms, calls, error display per `spec/ui-flow.md` | none | mocked | yes (jsdom) |
| **End-to-end** | Browser against full stack | PostgreSQL (or documented local) | real | real browser |

Rules:

- **State-machine integration tests** must exercise the production enforcement path (controller → service → domain → repository). A test double that re-implements “allowed pairs” is not acceptable as the only integration proof.
- Backend validation tests must not rely on the frontend.
- UI tests must not replace backend transition tests (`spec/ui-flow.md` §11.4).

---

## 2. Unit tests

**Purpose:** fast, no Spring context unless trivial.

| Area | Cases |
| --- | --- |
| Status transition policy | All **25** `(current, requested)` pairs from `spec/state-machine.md` §5: 5 valid, 20 invalid. Unknown label → invalid value path, not a table row. |
| Ticket aggregate (domain) | Create yields `OPEN`. `transitionTo` updates only on valid edges. Invalid edge leaves status unchanged. Field mutators do not expose status. |
| Validation helpers (if any) | Blank vs non-blank title/comment body once rules are confirmed; trim behavior. |
| Error mapping helpers | `ILLEGAL_STATUS_TRANSITION` carries `currentStatus` / `requestedStatus` for the API layer. |

---

## 3. Service / business-rule tests

**Purpose:** use cases without HTTP. Mock repositories or use a minimal in-memory adapter.

| Use case | Cases |
| --- | --- |
| Create ticket | Result status `OPEN`. Client-supplied `status` on create is rejected or ignored per contract (must not persist non-`OPEN`). |
| Get ticket | Returns aggregate; missing id → not-found error for API to map to 404. |
| List / search / filter | Delegates to repository with correct parameters; combines search + status if contract supports both. |
| Field update | Updates title/description/priority/assignee only. Rejects or ignores `status` in payload. |
| Transition | Calls domain `transitionTo`; persists only on success; on invalid edge, no save of new status. |
| Add comment | Requires existing ticket; comment linked to ticket; ticket status unchanged. |
| Transactions | Mutating use cases run in a transaction; failure rolls back (no partial comment + status). |
| Concurrency (if optimistic locking confirmed) | Two conflicting transitions: one succeeds, loser gets conflict; status consistent. |

---

## 4. Repository / data tests (H2)

**Purpose:** mappings and queries against the real test database.

| Area | Cases |
| --- | --- |
| Ticket CRUD | Save and load by id; generated `id`, `created_at`, `updated_at` present. |
| Comment | Save comment with `ticket_id`; load comments for ticket ordered by `created_at`. |
| Constraints | `NOT NULL` on required columns; FK comment → ticket; status/priority check constraints reject illegal labels (once priority set is confirmed). |
| Status value integrity | Database rejects stored status outside the five labels (check/enum). This is **not** a transition test. |
| Search query | Keyword search returns expected rows for the chosen semantics (case/partial per product decision); empty keyword behavior per contract. |
| Status filter | Filter by each of the five statuses returns only matching tickets. |
| Pagination (if implemented) | Page size and page index return correct slices; stable sort. |
| Restart-equivalent | Commit, new persistence context/session, read back (proves durability within the test DB). |

---

## 5. REST controller / API tests

**Purpose:** HTTP contract without full integration (mock or slice services).

| Endpoint type | Cases |
| --- | --- |
| Create | 2xx with body; 400 for malformed JSON; 400 for missing/invalid fields per contract; 400 `STATUS_NOT_UPDATABLE` if `status` is sent, and no ticket persisted. |
| List | 2xx; query params passed to service; empty list 2xx with empty array/page. |
| Details | 2xx with ticket + comments; 404 `TICKET_NOT_FOUND` when service reports missing. |
| Field update | 2xx; 400 when `status` present (`STATUS_NOT_UPDATABLE`); 404 unknown id. |
| Transition | 2xx valid; 400 `INVALID_STATUS_VALUE` for bad label; 400 `INVALID_REQUEST` for missing body; 409 `ILLEGAL_STATUS_TRANSITION` from service/domain; 404 unknown id. |
| Add comment | 2xx; 400 blank/invalid body; 404 unknown ticket. |
| Error format | Single documented JSON shape; no stack traces; `code` and `message` present. |

---

## 6. Integration tests (backend, H2)

**Purpose:** full backend slice through HTTP and persistence.

| Requirement | Cases |
| --- | --- |
| Create | Create via API; GET returns same data; status `OPEN`. |
| List | Create several; list contains them. |
| Details | GET by id includes comments. |
| Field update | Update fields; GET reflects changes; status unchanged. |
| Assignee | Update assignee; GET reflects change. |
| Comments | Add comment; GET details includes it; status unchanged. |
| Search | Keyword returns matching tickets only. |
| Filter | Status filter returns only that status. |
| Persistence | Data readable after application context restart (or equivalent new session against same H2 file/DB as configured). |
| Validation | Invalid create/update/comment bodies → 400; nothing persisted. |
| Missing ticket | GET/update/transition/comment on unknown id → 404. |

---

## 7. State-machine integration tests (explicit)

**Setup:** create tickets and drive them to the needed current status using **only legal API transitions** (`spec/state-machine.md` §6), or domain fixtures for unit-level policy tests.

### 7.1 Valid transitions (must succeed)

| # | From | To | Expected |
| --- | --- | --- | --- |
| SM-1 | `OPEN` | `IN_PROGRESS` | 200; persisted `IN_PROGRESS` |
| SM-2 | `IN_PROGRESS` | `RESOLVED` | 200; persisted `RESOLVED` |
| SM-3 | `RESOLVED` | `CLOSED` | 200; persisted `CLOSED` |
| SM-4 | `OPEN` | `CANCELLED` | 200; persisted `CANCELLED` |
| SM-5 | `IN_PROGRESS` | `CANCELLED` | 200; persisted `CANCELLED` |
| SM-6 | `OPEN` | `CLOSED` via full path | `OPEN → IN_PROGRESS → RESOLVED → CLOSED` each step 200 |
| SM-7 | — | — | After restart/new session, last status still persisted |

### 7.2 Invalid transitions — required examples (must reject)

| # | From | To | Expected |
| --- | --- | --- | --- |
| SM-8 | `CLOSED` | `OPEN` | 409; `ILLEGAL_STATUS_TRANSITION`; status still `CLOSED` |
| SM-9 | `RESOLVED` | `OPEN` | 409; status still `RESOLVED` |
| SM-10 | `CANCELLED` | `OPEN` | 409; status still `CANCELLED` |

### 7.3 Invalid transitions — additional (must reject)

| # | From | To | Reason |
| --- | --- | --- | --- |
| SM-11 | `OPEN` | `RESOLVED` | skip |
| SM-12 | `OPEN` | `CLOSED` | skip |
| SM-13 | `IN_PROGRESS` | `CLOSED` | skip |
| SM-14 | `IN_PROGRESS` | `OPEN` | backward |
| SM-15 | `RESOLVED` | `IN_PROGRESS` | backward |
| SM-16 | `RESOLVED` | `CANCELLED` | not allowed |
| SM-17 | `CLOSED` | `IN_PROGRESS` | terminal |
| SM-18 | `CLOSED` | `RESOLVED` | terminal |
| SM-19 | `CLOSED` | `CANCELLED` | terminal |
| SM-20 | `CLOSED` | `CLOSED` | same-state |
| SM-21 | `CANCELLED` | `IN_PROGRESS` | terminal |
| SM-22 | `CANCELLED` | `RESOLVED` | terminal |
| SM-23 | `CANCELLED` | `CLOSED` | terminal |
| SM-24 | `CANCELLED` | `CANCELLED` | same-state |
| SM-25 | `OPEN` | `OPEN` | same-state |
| SM-26 | `IN_PROGRESS` | `IN_PROGRESS` | same-state |
| SM-27 | `RESOLVED` | `RESOLVED` | same-state |

### 7.4 Bypass and contract

| # | Case | Expected |
| --- | --- | --- |
| SM-28 | Field update body includes `status` | 400 `STATUS_NOT_UPDATABLE`; status unchanged |
| SM-29 | Transition to `open` / `DONE` / empty | 400 `INVALID_STATUS_VALUE` or `INVALID_REQUEST` |
| SM-30 | Transition unknown ticket id | 404 `TICKET_NOT_FOUND` |
| SM-31 | After any 409, GET ticket | Previous status |
| SM-32 | Repeat same valid transition twice | Second is same-state → 409 |
| SM-33 | Create request includes `status` | 400 `STATUS_NOT_UPDATABLE`; no ticket persisted |
| SM-34 | Two concurrent transitions on the same ticket (required if optimistic locking is confirmed) | Exactly one succeeds; the loser receives 409; final persisted status is consistent. If locking is not confirmed, `spec/architecture.md` must document the accepted last-write-wins risk instead |

Every SM case asserts: HTTP status, JSON `code`, `currentStatus` / `requestedStatus` on 409, and persisted status via GET.

### 7.5 Architecture-guard tests

Automated tests (e.g. ArchUnit or equivalent) that fail the build if the layering and bypass rules in `spec/architecture.md` §2 and §10 are violated:

| # | Rule under test |
| --- | --- |
| AG-1 | Controllers do not reference repositories |
| AG-2 | Repositories do not contain transition logic |
| AG-3 | Ticket status is mutated only through the domain transition operation (no public status setter usable by API/service code) |
| AG-4 | Services do not perform ad-hoc status updates |

These tests enforce rules that already exist in the specifications; they add no new product behavior.

---

## 8. Frontend tests

**Purpose:** UI behavior per `spec/ui-flow.md` with mocked HTTP.

| Flow | Cases |
| --- | --- |
| Create | Submit calls API without `status`; success navigates; 400 shows errors; loading disables submit. |
| List | Loading then rows; empty state vs error state; row click to details. |
| Search | Keyword sent; results rendered; zero hits shows search-empty; error shows search error. |
| Filter | Status query sent; only returned rows shown; filter-empty message. |
| Details | Renders fields and comments; 404 shows not-found; comments empty state. |
| Edit | No `status` in payload; success updates; 400 keeps form and shows errors. |
| Assignee | Update sends assignee only; error keeps previous value. |
| Comment | Submit; success appends and clears; error keeps text; status unchanged. |
| Status actions | For each current status, only suggested actions appear (`spec/ui-flow.md` §11.2); terminal states show none. |
| Transition success | 200 updates displayed status and available actions. |
| Transition 409 | Shows `message`; keeps or restores server status; does not show requested status as current. |
| Meaningful errors | 400/404/409/500/network each render a user-safe message; no stack trace. |

---

## 9. End-to-end tests

**When appropriate:** after API contract and UI exist; run against real backend + PostgreSQL (or documented local stack).

| Flow | Cases |
| --- | --- |
| Happy path | Create → list → details → edit → comment → `OPEN → IN_PROGRESS → RESOLVED → CLOSED` |
| Cancel path | Create → cancel from `OPEN`; separate ticket → `IN_PROGRESS` → cancel |
| Search + filter | Create known titles; search; filter by status |
| Persistence | Create, restart backend, data still present |
| Invalid transition via UI | Attempt only if UI exposes a control; otherwise verify 409 handling with mocked or forced stale state |
| Errors | Backend down or 500 shows meaningful UI error |

E2E does not replace §7 state-machine integration tests.

---

## 10. Requirement → test traceability

| Requirement / acceptance | Test case(s) |
| --- | --- |
| Create ticket from UI | FE create; E2E happy path; API create; integration create |
| Tickets listed | FE list; API list; integration list |
| Ticket details viewed | FE details; API details; integration details |
| Fields updated | FE edit; API field update; integration update |
| Assignee changed | FE assignee; API/integration assignee |
| Comments added | FE comment; API comment; integration comment |
| Search works | FE search; repository search; integration search |
| Status filter works | FE filter; repository filter; integration filter |
| Valid transitions work | SM-1 … SM-7 |
| Invalid transitions rejected by backend | SM-8 … SM-27, SM-31, SM-32 |
| Data survives restart | Repository restart-equivalent; integration persistence; E2E persistence |
| Backend validation works | API 400 cases; integration validation |
| UI shows meaningful errors | FE error cases; E2E errors |
| State-machine integration tests pass | §7 suite |
| No secrets committed | CI secret-scanning step (e.g. gitleaks or equivalent) must pass on every commit; no test may require real secrets |

---

## 11. Validation failures, missing tickets, duplicate/invalid data

| Concern | Where tested | Examples |
| --- | --- | --- |
| Validation failures | API, integration | Blank title (once required), bad enum, malformed JSON, `status` on update |
| Missing tickets | API, integration, SM-30 | 404 on GET, update, transition, comment |
| Duplicate data | Repository/service | Unique constraints if any are added (e.g. user email); currently no unique business key specified for tickets — do not invent one |
| Invalid data | Repository | Check constraints for status/priority labels; FK violation for orphan comment |

---

## 12. Search and filtering test data

Reference seed set (test fixture, not product data):

| # | Title | Description | Status |
| --- | --- | --- | --- |
| T1 | `Login page crashes` | `Crash on submit` | `OPEN` |
| T2 | `Password reset broken` | `Email never arrives` | `IN_PROGRESS` |
| T3 | `Login logo missing` | `Asset 404` | `RESOLVED` |
| T4 | `Old SSO issue` | `Superseded` | `CLOSED` |
| T5 | `Duplicate signup` | `Created twice` | `CANCELLED` |

- Search: positive match (`Login` → T1, T3), negative match (`billing` → none). Case-sensitivity and partial-vs-exact assertions must be written **only after** the product decision on search semantics; until then those assertions are placeholders, not passing criteria.  
- Filter: each status returns exactly its one ticket; “all” returns five.  
- Combined search + filter: only if the API contract defines it.

---

## 13. Persistence tests

- H2: commit and read in new transaction/session.  
- Integration: restart Spring context or use a file-based H2 URL for a restart test if in-memory is reset.  
- E2E: restart backend process against PostgreSQL.

---

## 14. Meaningful UI errors

Frontend tests must assert:

- `message` from API JSON is displayed when present  
- `code` used for logic where needed (e.g. 409 transition)  
- No raw stack trace or SQL shown  
- Empty states are not shown as errors and vice versa

---

## 15. What is not tested here

- Authn/authz (not in requirements)  
- Comment edit/delete (not in requirements)  
- Pagination details (unconfirmed)  
- Exact field lengths (unconfirmed)

When those are decided, add cases to the matching level above.
