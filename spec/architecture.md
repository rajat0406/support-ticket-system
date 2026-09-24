# Support Ticket Management System — Architecture

**Status:** proposed, not implemented  
**Sources:** `spec/requirements.md`, workspace project steering rules  
**Constraint:** this document defines structure and responsibilities only. It does not contain application, SQL, or UI implementation code.

Product rules that are still ambiguous (ticket ID format, field optionality, lengths, priority values, assignee model, search/filter/pagination, error payload shape, timestamps, comment mutability, and others) are **not decided here**. Where the design must name a seam, it is marked **Requires confirmation**.

---

## 1. System overview

Two independently deployable applications, communicating over HTTP JSON REST.

| Part | Role |
| --- | --- |
| Spring Boot backend | Source of truth for tickets, comments, validation, and the status state machine |
| PostgreSQL | Production (and intended local-dev) persistence |
| H2 | Isolated test database only |
| React / Next.js frontend | User interface; never the authority for business rules |

```
Browser
  → Next.js UI
    → HTTP JSON REST
      → Spring Boot API
        → Domain rules (including state machine)
          → Repositories
            → PostgreSQL (runtime) / H2 (automated tests)
```

The UI may hide invalid actions for usability. **Only the backend may accept or reject a status change.** A crafted HTTP request that skips the UI must still be rejected when the transition is illegal.

---

## 2. Backend layers

Recommended package intent (names are indicative):

- **API layer** — REST controllers, request/response records, HTTP mapping  
- **Application/service layer** — use-case orchestration, transactions  
- **Domain layer** — ticket aggregate behavior, status transition policy, domain exceptions  
- **Persistence layer** — Spring Data repositories, JPA mappings, schema ownership  
- **Shared kernel** — error payload mapping, clock, identifiers (no business workflow)

Rules that apply to every layer:

- Controllers do not call repositories.  
- Repositories do not contain status-transition logic.  
- Services do not expose a “save whatever status the client sent” path.  
- Secrets, connection strings, and CORS origins come from environment/profile configuration, never from committed credentials.

---

## 3. Controller responsibilities

Controllers are HTTP adapters.

They:

- Bind and deserialize JSON to request objects  
- Trigger **bean-validation** of request shape (required JSON fields, types, enum spelling at the wire level)  
- Invoke one service method per use case  
- Map successful results to HTTP status codes and response bodies  
- Do not open transactions  
- Do not compute the next ticket status  
- Do not persist entities  

Proposed use-case mapping (paths are illustrative):

| Use case | Controller responsibility |
| --- | --- |
| Create ticket | Accept creation payload; return created ticket |
| List / search / filter | Accept query parameters; return a list or page |
| View details | Return ticket plus comments |
| Update title, description, priority, assignee | Dedicated field-update contract that **does not accept status** |
| Change status | Dedicated transition contract (target status only) |
| Add comment | Nested resource on the ticket |

**Requires confirmation:** exact URL design, whether list and search share one endpoint, and pagination query parameter names. Those are API contract choices, not layering choices.

---

## 4. Service responsibilities

Services are the application use-case boundary and the **only** transactional writers.

They:

- Load the ticket aggregate (or fail with not-found)  
- Apply the requested use case  
- Start and commit the transaction (`@Transactional` on service methods that mutate data)  
- Translate domain outcomes into application-level results  
- Coordinate ticket persistence and comment persistence  

They do **not**:

- Interpret HTTP  
- Bypass the domain transition method by setting status on an entity and saving  
- Perform ad-hoc SQL that updates `status`  

Concurrency: mutating use cases should assume lost-update risk. **Requires confirmation:** optimistic locking (`version` column) versus last-write-wins. Architecture recommendation: optimistic locking on the ticket aggregate so two concurrent transitions cannot both succeed.

---

## 5. Domain / business-rule responsibilities

The domain layer is the **single source of truth** for ticket lifecycle.

### 5.1 Ticket aggregate

The ticket owns:

- Identity  
- Mutable fields allowed by the product (title, description, priority, assignee — subject to still-open product decisions)  
- Current status  
- Comments as a collection related to that ticket  

Status is not a freely assignable field. The aggregate exposes a **transition operation** (conceptual: `transitionTo(targetStatus)`), not a public status setter used by controllers or services.

### 5.2 Status transition policy

A dedicated, testable policy (table or explicit allowed-pair set) encodes only:

```
OPEN → IN_PROGRESS
IN_PROGRESS → RESOLVED
RESOLVED → CLOSED
OPEN → CANCELLED
IN_PROGRESS → CANCELLED
```

All other pairs, including the stated examples (`CLOSED → OPEN`, `RESOLVED → OPEN`, `CANCELLED → OPEN`), are illegal.

The policy is a pure function of `(current, target)`. It does not read HTTP, the database catalog, or UI state.

**Requires confirmation (product, not architecture):** whether `CLOSED` and `CANCELLED` are terminal with no outbound edges; the written machine implies that, but it is not stated as a general rule. Architecture treats unspecified edges as **forbidden** until a new allowed edge is added to this policy.

### 5.3 Other domain rules

Field-level product rules (lengths, priority vocabulary, unassigned tickets, comment author) belong here **after** they are decided. Until then, the domain must not invent them silently.

---

## 6. Repository responsibilities

Repositories:

- Load and save aggregates / entities  
- Execute queries for list, keyword search, and status filter  
- Remain ignorant of whether a status value is a legal *transition*  

They may constrain **stored** status to the known enum set (schema/enum). That is value integrity, not transition integrity.

They must not provide methods such as “update status by id” that skip the aggregate.

---

## 7. Persistence strategy

| Environment | Database | Purpose |
| --- | --- | --- |
| Production | PostgreSQL | Durable source of truth; data survives process restart |
| Local development | PostgreSQL (preferred, same engine as production) | Avoid “works on H2 only” surprises |
| Automated tests | H2 | Fast, isolated, no external Postgres required for CI unit/slice/integration tests |

Schema:

- Versioned migrations owned by the backend (tool **requires confirmation**: Flyway vs Liquibase).  
- Production schema is applied by migrations, not by Hibernate `ddl-auto=update` / `create`.  
- Test profile may use a compatible H2 schema derived from the same migrations, or a test-only `ddl-auto` **only if** dialect differences are explicitly accepted. **Requires confirmation:** H2 in PostgreSQL compatibility mode versus Testcontainers PostgreSQL for integration tests. Steering preference: keep production and tests honest; H2 is mandated for tests, so use PostgreSQL-compatible H2 mode and keep dialect-specific SQL out of repositories.

Data:

- Tickets and comments persist in relational tables with a foreign key from comment to ticket.  
- No secrets in schema files.  

**Requires confirmation:** identifier type (UUID vs sequence vs display-code), timestamp storage (UTC `timestamptz` recommended), and whether comments are cascade-persisted with the ticket or saved as their own aggregate.

---

## 8. Validation strategy

Validation is layered. UI validation is optional convenience; **backend validation is mandatory**.

1. **Wire validation (API)** — malformed JSON, missing required request properties, wrong types, unknown enum literals for fields that *are* client-supplied.  
2. **Use-case validation (service/domain)** — ticket exists, field-update vs transition split, comment attached to an existing ticket.  
3. **Lifecycle validation (domain policy)** — status change allowed only via the transition operation and the allowed-pair table.  
4. **Persistence constraints** — non-null keys, foreign keys, enum/check for known statuses. These are a backstop, not the workflow engine.

The general ticket-update request **must not include status**. If a client sends `status` on the field-update contract, the API rejects it (400, `STATUS_NOT_UPDATABLE`), so the update path cannot double as a transition bypass.

The create request likewise **must not include status**. If a client sends `status` on create, the API rejects the whole request (400, `STATUS_NOT_UPDATABLE`) and persists no ticket, so create cannot seed a non-`OPEN` initial status (see `spec/state-machine.md` §2).

---

## 9. Exception handling and API errors

A single API exception handler (conceptual `@ControllerAdvice`) maps:

| Outcome | HTTP meaning (illustrative) |
| --- | --- |
| Invalid request shape / bean-validation | 400 |
| Illegal status transition | **409** (defined in `spec/state-machine.md` §7.2) |
| Ticket not found | 404 |
| Optimistic lock failure | 409 |
| Unexpected server failure | 500 with a stable, non-secret message |

Steering: no stack traces, connection strings, or internal SQL in responses. UI displays the API’s **meaningful** error fields.

**Error schema ownership:** the lifecycle error codes and the transition error body are defined in `spec/state-machine.md` §7 and are the source of truth until `spec/api-contract.md` (not yet created) formalizes the full error schema. Architecture requires **one** documented shape used by every endpoint so the frontend can render errors uniformly.

---

## 10. State-machine enforcement (bypass prevention)

### 10.1 Where it is enforced

**Primary enforcement:** domain layer, inside the ticket aggregate’s transition operation, which consults the status transition policy.

**Invocation path:** REST transition endpoint → service (transaction) → `ticket.transitionTo(target)` → persist.

**Not enforced in:** React components, Next.js route handlers (if any), controllers, or repositories.

### 10.2 How callers are prevented from bypassing it

| Bypass attempt | Why it fails |
| --- | --- |
| UI hidden button / client-only disable | Irrelevant; server still validates |
| `PUT/PATCH` ticket with `"status": "CLOSED"` | Field-update API does not accept status; request rejected |
| Direct repository `save` after mutating status in a controller | Controllers cannot reach repositories; entities do not expose a public status setter for API/service use |
| Raw SQL / undocumented admin script | Out of app scope; optional DB trigger is a later hardening choice (**requires confirmation**) |
| Concurrent two transitions | Optimistic lock (**if confirmed**) ensures one commit wins; the loser retries against the new current status and is re-validated |
| Test or future feature calling “setStatus” | No such supported API; new code must call `transitionTo` |
| Future code adds a controller→repository path or a public status setter | Caught by an automated architecture-guard test that fails the build (see §13 and `spec/test-strategy.md` §7.5) |

Defense in depth (optional, confirmation required): a database trigger that rejects `UPDATE` of `status` when `(OLD.status, NEW.status)` is not in the allowed pair set. This is **not** a substitute for domain enforcement. It helps only against out-of-band writes.

Integration tests required by the product:

- Every allowed edge succeeds  
- Documented illegal edges are rejected by the **backend**, including when the HTTP client is not the UI  

---

## 11. Frontend / backend communication

- Browser talks to the Next.js origin; the browser calls the Spring REST API (directly or via a thin Next.js BFF).  
- **Requires confirmation:** browser → Spring directly (CORS on Spring) versus Next.js rewrite/proxy (same-origin for the browser).  
- JSON only for these use cases. No frontend-owned database.  
- Frontend modules: ticket list, ticket detail, create/edit form, comment form, status-transition control that only *requests* a transition.  
- On error, the UI renders the backend error payload; it does not invent a success state if HTTP failed.  
- Next.js must not implement a second state machine that the server trusts.

Steering: CORS is explicit allow-list of the UI origin(s), not `*`, and not used as an auth substitute. **Requires confirmation:** authentication model. The written product requirements do not specify users, sessions, or roles. Architecture must not add a fake login or disable security controls as a shortcut. Until auth is specified, local/dev may run without user identity **only as an explicit temporary decision**, and production exposure must be re-reviewed.

---

## 12. Configuration management

Spring profiles:

| Profile | Database | Notes |
| --- | --- | --- |
| `prod` | PostgreSQL | Credentials and URLs from environment variables |
| `local` | PostgreSQL | Same. Local `.env` / uncommitted `application-local.yml` |
| `test` | H2 | Used by the test runtime only |

Rules (steering):

- No secrets in git  
- TLS not disabled in production configuration  
- API base URL for the frontend is an environment variable (`NEXT_PUBLIC_…` or server-only proxy target)  
- Default ports, CORS origins, and datasource URLs are configuration, not code  

---

## 13. Testing architecture

| Layer | What it proves | Typical store |
| --- | --- | --- |
| Domain unit tests | Allowed and forbidden transitions; no Spring context | None |
| Service tests | Use-case orchestration, transaction boundaries mocked or sliced | Optional |
| API slice / MockMvc | HTTP contracts, validation errors, transition vs update split | Mocked services or slice |
| Backend integration | Persistence + state machine + restart-equivalent commit | **H2** |
| Frontend tests | Rendering, form errors, calling the correct API | Mocked HTTP |
| Architecture-guard tests | Layering rules hold; no status-bypass path exists | None |
| Manual / e2e (later) | UI create/list/detail/update/comment/search/filter | Local PostgreSQL |

State-machine integration tests must hit the real enforcement path (HTTP or service + repository + H2), not only a copied if/else in a test double that the production code does not use.

---

## 14. Local development architecture

Suggested layout:

```
backend/     Spring Boot application
frontend/    Next.js (React) application
spec/        requirements and architecture only
```

Local run:

1. PostgreSQL available locally or via a documented compose file (compose is ops, not app code).  
2. Backend `local` profile → PostgreSQL.  
3. Frontend dev server → configured API base URL.  
4. Tests run against H2 without that Postgres instance.

Data created through the API remains after backend restart because it is in PostgreSQL, satisfying the persistence acceptance criterion.

---

## 15. Security and dependency posture

Aligned with project steering:

- No hardcoded credentials  
- No permissive production CORS  
- No `eval` / unsafe deserialization of untrusted payloads  
- Backend validation cannot be skipped because the UI already validated  
- New dependencies only when necessary; pin versions when implementation starts  
- Authn/authz is **unspecified in requirements** and is listed below as confirmation, not as a silent “open API in production”

---

## 16. Architectural decisions that require confirmation

These are design choices. They are **not** silent product requirements.

1. **Monorepo vs two repositories** for `backend/` and `frontend/`.  
2. **Migration tool:** Flyway vs Liquibase.  
3. **H2 strategy:** PostgreSQL-compatibility mode vs later Testcontainers PostgreSQL while still keeping H2 for a subset of tests.  
4. **Optimistic locking** on tickets vs last-write-wins.  
5. ~~Illegal transition HTTP status~~ — **decided: 409** (`spec/state-machine.md` §7.2).  
6. **Full error JSON schema** beyond lifecycle codes — to be formalized in `spec/api-contract.md` (not yet created); lifecycle codes in `spec/state-machine.md` §7 are authoritative until then.  
7. **Browser CORS direct to Spring** vs **Next.js proxy/BFF**.  
8. **Next.js App Router vs Pages Router**, and whether any ticket data is server-rendered vs client-fetched.  
9. **Optional DB trigger** duplicating the allowed transition pairs.  
10. **Authentication/authorization** model (or an explicit “no auth in v1” decision).  
11. **Package style:** classic Spring layers vs hexagonal ports/adapters (this document assumes classic layers plus a distinct domain policy).  
12. **Comment persistence:** collection on the ticket aggregate vs separate comment service/repository called from the same transaction.

Product ambiguities (IDs, required fields, lengths, priority, assignee existence, unassigned tickets, comment author, search semantics, pagination, sorting, date format, whether comments are editable) remain **open**. Architecture reserves seams for them and must not fill them in during implementation without a requirements decision.
