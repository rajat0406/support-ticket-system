# Support Ticket Management System — REST API Contract

**Status:** approved HTTP contract for implementation and API tests  
**Sources:** `spec/requirements.md`, `spec/state-machine.md`, `spec/architecture.md`, `spec/data-model.md`, `spec/test-strategy.md`  
**Constraint:** this document defines the wire contract only. It does not contain controller or persistence code.

Endpoints are limited to the written product capabilities: create, list, get details, update fields, change status, add comment, search by keyword, filter by status.

---

## 1. Conventions

| Item | Contract |
| --- | --- |
| Base path | `/api` |
| Media type | `application/json; charset=UTF-8` |
| Identifiers | Path `{ticketId}` is a UUID string (API identifier; storage type remains a data-model seam) |
| Timestamps | ISO-8601 instants in UTC, e.g. `2026-09-25T01:00:00Z` |
| Status labels | Exact strings: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` (case-sensitive) |
| List + search + filter | One `GET` collection resource; optional query parameters combine with **AND** |
| Pagination | Not in requirements; this contract returns the full matching set |
| Persistence entities | Not returned. Responses are DTOs defined here |

Controllers map HTTP only. Status transitions are evaluated in domain/service logic (`spec/state-machine.md`). Controllers must not compute allowed next statuses.

---

## 2. Shared representations

### 2.1 Ticket DTO (detail)

Returned by create, get, field update, and status transition.

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "title": "Login page crashes",
  "description": "Crash on submit",
  "priority": null,
  "assignee": null,
  "status": "OPEN",
  "createdAt": "2026-09-25T01:00:00Z",
  "updatedAt": "2026-09-25T01:00:00Z",
  "comments": []
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string (UUID) | Server-generated |
| `title` | string | Non-blank |
| `description` | string or `null` | Optional until product tightens the rule |
| `priority` | string or `null` | Free-text seam; no approved enum in requirements |
| `assignee` | string or `null` | Free-text seam |
| `status` | string | Server-owned; never client-writable on create/update |
| `createdAt` | string | Set on create |
| `updatedAt` | string | Set on successful ticket-row mutation (not defined as changing on comment-add) |
| `comments` | array of comment DTOs | Empty array if none |

### 2.2 Ticket DTO (list item)

Same as detail **except** `comments` is omitted on list/search/filter responses.

### 2.3 Comment DTO

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "body": "Looking into it",
  "author": null,
  "createdAt": "2026-09-25T01:05:00Z"
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string (UUID) | Server-generated |
| `body` | string | Non-blank |
| `author` | string or `null` | Representation unresolved; client may omit |
| `createdAt` | string | Set on create |

Comments are add-only. No edit/delete endpoints.

### 2.4 Error DTO (all error responses)

```json
{
  "code": "ILLEGAL_STATUS_TRANSITION",
  "message": "Transition from CLOSED to OPEN is not allowed.",
  "currentStatus": "CLOSED",
  "requestedStatus": "OPEN"
}
```

| Field | Required | When present |
| --- | --- | --- |
| `code` | always | Exact machine code from the table below |
| `message` | always | Human-readable, UI-safe; no stack traces, SQL, or secrets |
| `currentStatus` | 409 illegal transition only | Persisted status before the request |
| `requestedStatus` | 409 illegal transition only | Status the client asked for |

`currentStatus` and `requestedStatus` **must** appear on `ILLEGAL_STATUS_TRANSITION`. They **must not** appear on other errors.

### 2.5 Error codes

| `code` | HTTP | Meaning |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | Malformed JSON, missing required body/fields, blank required text, missing/blank `requestedStatus` |
| `INVALID_STATUS_VALUE` | 400 | Status label not one of the five exact strings (including wrong case) |
| `STATUS_NOT_UPDATABLE` | 400 | `status` present on create or field-update |
| `TICKET_NOT_FOUND` | 404 | No ticket for `{ticketId}` |
| `ILLEGAL_STATUS_TRANSITION` | 409 | Ticket exists; requested label is valid; pair is not an allowed edge |

Unexpected server failures: **500** with `code` omitted or a stable non-secret `message` only. Do not leak internals.

Malformed JSON always **400** `INVALID_REQUEST`.

---

## 3. State-machine bypass prevention (normative)

1. **Create** must not include `status`. Presence → 400 `STATUS_NOT_UPDATABLE`; no ticket persisted. Created tickets always have `status: "OPEN"`.
2. **Field update** must not include `status`. Presence → 400 `STATUS_NOT_UPDATABLE`; no field or status change.
3. **Status change** is only `POST /api/tickets/{ticketId}/transitions` with `requestedStatus`. The handler delegates to domain `transitionTo`; it must not assign status from the DTO onto an entity and save.
4. There is no `PUT`/`PATCH` that accepts a full ticket including `status`.
5. List/search/filter are read-only and must not call the transition use case.

Allowed edges (only):

```
OPEN → IN_PROGRESS
IN_PROGRESS → RESOLVED
RESOLVED → CLOSED
OPEN → CANCELLED
IN_PROGRESS → CANCELLED
```

All other pairs, including same-state, are **409** `ILLEGAL_STATUS_TRANSITION` when both labels are valid. See `spec/state-machine.md` §5.

---

## 4. Endpoints

### 4.1 Create ticket

| Item | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/tickets` |
| Query | none |

**Request body**

```json
{
  "title": "Login page crashes",
  "description": "Crash on submit",
  "priority": null,
  "assignee": null
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `title` | yes | Non-null, non-blank after trim |
| `description` | no | May be omitted or `null` |
| `priority` | no | May be omitted or `null` |
| `assignee` | no | May be omitted or `null` |
| `status` | **forbidden** | If present (including `null`) → 400 `STATUS_NOT_UPDATABLE` |

**Success**

| HTTP | Body |
| --- | --- |
| **201 Created** | Ticket DTO (detail); `status` is `OPEN`; `comments` is `[]` |

Optional `Location: /api/tickets/{id}`.

**Errors**

| Condition | HTTP | `code` |
| --- | --- | --- |
| Malformed JSON | 400 | `INVALID_REQUEST` |
| Missing/blank `title` | 400 | `INVALID_REQUEST` |
| `status` property present | 400 | `STATUS_NOT_UPDATABLE` |
| **Not-found** | n/a | Create does not look up a ticket |

**Business-rule errors:** none beyond create-always-`OPEN` (enforced by omitting client status).

---

### 4.2 List tickets (includes search and status filter)

| Item | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/tickets` |
| Request body | none |

**Query parameters**

| Name | Required | Rules |
| --- | --- | --- |
| `q` | no | Keyword search. Omitted or blank = no keyword constraint. Matching fields/case/partial vs exact remain a product seam; until decided, the backend applies one documented implementation and tests lock it. |
| `status` | no | If present, must be one of the five labels. Omitted = all statuses. There is no `status=ALL` value. |

If both `q` and `status` are present, results must match **both**.

**Success**

| HTTP | Body |
| --- | --- |
| **200 OK** | `{ "tickets": [ /* list-item DTOs */ ] }` |

Empty match: **200** with `"tickets": []` (not 404).

**Errors**

| Condition | HTTP | `code` |
| --- | --- | --- |
| `status` present but not a valid label (e.g. `open`) | 400 | `INVALID_STATUS_VALUE` |
| **Not-found** | n/a | Collection GET never 404 for “no rows” |

**Business-rule errors:** none (read-only).

---

### 4.3 Get ticket

| Item | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/tickets/{ticketId}` |
| Query | none |
| Request body | none |

**Success**

| HTTP | Body |
| --- | --- |
| **200 OK** | Ticket DTO (detail), comments in `createdAt` ascending order |

**Errors**

| Condition | HTTP | `code` |
| --- | --- | --- |
| `{ticketId}` not a UUID | 400 | `INVALID_REQUEST` |
| No ticket with that id | **404** | `TICKET_NOT_FOUND` |

**Business-rule errors:** none.

---

### 4.4 Update ticket fields

| Item | Value |
| --- | --- |
| Method | `PATCH` |
| Path | `/api/tickets/{ticketId}` |
| Query | none |

Updates **only** title, description, priority, assignee. Does not change `status` or comments.

**Request body**

```json
{
  "title": "Login page crashes",
  "description": "Crash on submit",
  "priority": null,
  "assignee": "alex"
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `title` | yes | Non-blank after trim |
| `description` | no | Omitted or `null` allowed |
| `priority` | no | Omitted or `null` allowed |
| `assignee` | no | Omitted or `null` allowed |
| `status` | **forbidden** | If present → 400 `STATUS_NOT_UPDATABLE`; **no** partial field update |

Omitted optional fields are treated as “set to null / unchanged per implementation of PATCH”: this contract requires the client to send the four field keys shown when performing a full edit from the UI. **Minimum:** `title` must be present. If `description`, `priority`, or `assignee` are omitted, the server leaves that stored value unchanged. If they are present as `null`, the server stores `null`.

**Success**

| HTTP | Body |
| --- | --- |
| **200 OK** | Ticket DTO (detail); `status` equal to pre-update status |

**Errors**

| Condition | HTTP | `code` |
| --- | --- | --- |
| Malformed JSON | 400 | `INVALID_REQUEST` |
| Missing/blank `title` | 400 | `INVALID_REQUEST` |
| `status` present | 400 | `STATUS_NOT_UPDATABLE` |
| `{ticketId}` not a UUID | 400 | `INVALID_REQUEST` |
| Ticket missing | **404** | `TICKET_NOT_FOUND` |

**Business-rule errors:** `STATUS_NOT_UPDATABLE` as above. No lifecycle transition via this endpoint.

---

### 4.5 Change ticket status

| Item | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/tickets/{ticketId}/transitions` |
| Query | none |

**Request body**

```json
{
  "requestedStatus": "IN_PROGRESS"
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `requestedStatus` | yes | Non-blank; must be one of the five labels |
| `status` | must not be used | Clients send `requestedStatus` only |

Missing body, missing `requestedStatus`, or blank → 400 `INVALID_REQUEST`.  
Valid-looking but unknown label (`open`, `DONE`) → 400 `INVALID_STATUS_VALUE`.  
Legal label but illegal edge → 409 (not 400).

**Success**

| HTTP | Body |
| --- | --- |
| **200 OK** | Ticket DTO (detail) with `status` equal to `requestedStatus` |

A following `GET` must return the same `status`. Repeating the same `requestedStatus` is same-state → **409**.

**Errors**

| Condition | HTTP | `code` |
| --- | --- | --- |
| Malformed JSON / missing body / blank `requestedStatus` | 400 | `INVALID_REQUEST` |
| Label not in the five | 400 | `INVALID_STATUS_VALUE` |
| `{ticketId}` not a UUID | 400 | `INVALID_REQUEST` |
| Ticket missing | **404** | `TICKET_NOT_FOUND` |
| Illegal pair (see §3 and `spec/state-machine.md` §5) | **409** | `ILLEGAL_STATUS_TRANSITION` |

**Not-found:** 404 `TICKET_NOT_FOUND` before any transition evaluation.

**Business-rule errors:** 409 as specified; ticket fields and comments unchanged.

---

### 4.6 Add comment

| Item | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/tickets/{ticketId}/comments` |
| Query | none |

**Request body**

```json
{
  "body": "Looking into it",
  "author": null
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `body` | yes | Non-blank after trim |
| `author` | no | Omitted or `null` allowed |

Adding a comment **must not** change ticket `status`.

**Success**

| HTTP | Body |
| --- | --- |
| **201 Created** | Comment DTO |

**Errors**

| Condition | HTTP | `code` |
| --- | --- | --- |
| Malformed JSON | 400 | `INVALID_REQUEST` |
| Missing/blank `body` | 400 | `INVALID_REQUEST` |
| `{ticketId}` not a UUID | 400 | `INVALID_REQUEST` |
| Ticket missing | **404** | `TICKET_NOT_FOUND` |

**Business-rule errors:** none beyond “ticket must exist.”

---

## 5. Endpoint summary

| Use case | Method | Path |
| --- | --- | --- |
| Create ticket | `POST` | `/api/tickets` |
| List / search / filter | `GET` | `/api/tickets` |
| Get ticket | `GET` | `/api/tickets/{ticketId}` |
| Update fields | `PATCH` | `/api/tickets/{ticketId}` |
| Change status | `POST` | `/api/tickets/{ticketId}/transitions` |
| Add comment | `POST` | `/api/tickets/{ticketId}/comments` |

No other resources (users, attachments, delete, comment edit).

---

## 6. Out of contract (unresolved product, not invented here)

- Maximum lengths for title, description, comment body  
- Closed set of priority values  
- Whether assignee must exist in a user directory  
- Search case-sensitivity and partial vs exact match (must be documented in implementation notes when chosen)  
- Pagination and sort query parameters  
- Authentication
