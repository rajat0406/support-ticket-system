# Support Ticket Management System — Ticket State Machine

**Status:** specification for implementation and tests; not implementation  
**Sources:** approved lifecycle in `spec/requirements.md`; enforcement placement in `spec/architecture.md`; stored status in `spec/data-model.md`  
**Constraint:** no application code in this document.

This specification is the test oracle for unit tests (domain policy) and integration tests (HTTP + persistence). If a case is not listed as **Valid**, it is **Invalid**.

---

## 1. States

Exactly five statuses exist. Labels are case-sensitive and must match these strings:

| Status | Kind | Meaning for lifecycle |
| --- | --- | --- |
| `OPEN` | initial | Ticket has been created; work has not started |
| `IN_PROGRESS` | intermediate | Work has started |
| `RESOLVED` | intermediate | Work is done; awaiting close |
| `CLOSED` | terminal | Lifecycle complete |
| `CANCELLED` | terminal | Lifecycle abandoned |

No other status value is part of the machine. A requested value outside this set is **not** an “invalid transition”; it is an **invalid status value** (see §7.3).

---

## 2. Initial state

| Rule | Expected behavior |
| --- | --- |
| On create | Persisted `status` is always `OPEN` |
| Client-supplied status on create | The create contract has **no** `status` field. If `status` is present in a create request, the API rejects the whole request (HTTP **400**, `code = STATUS_NOT_UPDATABLE`) and persists no ticket. Create must never persist `IN_PROGRESS`, `RESOLVED`, `CLOSED`, or `CANCELLED`. |
| After create | Ticket is subject to the transition table in §5 |

**Create is not a transition.** Tests for create must not use the transition API.

---

## 3. Allowed transitions (complete legal set)

These five directed edges are the **only** legal status changes:

```
OPEN         → IN_PROGRESS
IN_PROGRESS  → RESOLVED
RESOLVED     → CLOSED
OPEN         → CANCELLED
IN_PROGRESS  → CANCELLED
```

Linear happy path:

```
OPEN → IN_PROGRESS → RESOLVED → CLOSED
```

Cancellation shortcuts (only from non-terminal in-progress work, not from `RESOLVED`):

```
OPEN        → CANCELLED
IN_PROGRESS → CANCELLED
```

There is no legal edge out of `CLOSED` or `CANCELLED`.  
There is no legal edge from `RESOLVED` to `CANCELLED`.  
There is no legal skip (`OPEN → RESOLVED`, `OPEN → CLOSED`, `IN_PROGRESS → CLOSED`).  
There is no legal backward move (for example `IN_PROGRESS → OPEN`, `RESOLVED → IN_PROGRESS`).  
There is no legal no-op (`OPEN → OPEN`, and likewise for every state). Same-state requests are **invalid**.

### 3.1 Interpretations of the written machine (flagged for product confirmation)

The written requirements list the allowed edges and three invalid examples but do not state every rule explicitly. The following are **interpretations** that follow from “only listed edges are valid.” They are part of this contract unless the product owner changes them; changing any of them is a requirements change, not an implementation detail.

| # | Interpretation | Basis |
| --- | --- | --- |
| I-1 | Same-state requests (`X → X`) are invalid, not idempotent success | No same-state edge is listed |
| I-2 | `RESOLVED → CANCELLED` is invalid | Only `OPEN → CANCELLED` and `IN_PROGRESS → CANCELLED` are listed |
| I-3 | `CLOSED` and `CANCELLED` are terminal (no outbound edges) | No outbound edges are listed for them |
| I-4 | `OPEN` is the only initial status | Requirements define creation but no initial-status choice |
| I-5 | Illegal transitions return **409** (not 422) | Standardized in §7.2 so tests have one expected status |

---

## 4. Business rule — status is not a field update

**Rule:** A status change must be validated by backend business logic. It must not be implemented as an unrestricted field update.

Consequences that tests must lock in:

1. **Dedicated operation.** Status changes only through a transition use case (conceptual: “transition ticket `{id}` to `{requestedStatus}`”). The domain evaluates `(currentStatus, requestedStatus)` against §5 **before** any persist of a new status.
2. **Field update excludes status.** The use case that updates title, description, priority, and assignee must not accept `status` in its request body. If `status` is present, the backend rejects the request as malformed/unsupported input (HTTP **400**), and **must not** change status (and should not apply a partial field update in the same request).
3. **UI is not enforcement.** Hiding buttons in the frontend is allowed for usability. Integration tests must call the backend **without** the UI and still observe rejection of illegal edges.
4. **Persistence is not enforcement.** A check/enum that limits stored labels to the five values is allowed. It does **not** satisfy this rule. Repositories must not expose “set status = X” that skips the policy.
5. **On rejection, no mutation.** Invalid transition: HTTP error, same `status` as before the request, same other ticket fields, no new comment implied, `updated_at` unchanged (aside from clock precision; see §7.1).

---

## 5. Transition table

Legend:

- **Valid** — backend accepts, persists `status = Requested State`, returns success with the updated ticket.
- **Invalid** — backend rejects, persists nothing for this request, returns the error in §7.

Self-transitions and all unspecified pairs are invalid.

| Current State | Requested State | Valid/Invalid | Reason |
| --- | --- | --- | --- |
| `OPEN` | `OPEN` | Invalid | Same-state change is not an allowed edge; no-op is not defined as success |
| `OPEN` | `IN_PROGRESS` | Valid | Allowed: work started |
| `OPEN` | `RESOLVED` | Invalid | Skip of `IN_PROGRESS` is not allowed |
| `OPEN` | `CLOSED` | Invalid | Skip of `IN_PROGRESS` and `RESOLVED` is not allowed |
| `OPEN` | `CANCELLED` | Valid | Allowed cancellation from `OPEN` |
| `IN_PROGRESS` | `OPEN` | Invalid | Backward transition is not allowed |
| `IN_PROGRESS` | `IN_PROGRESS` | Invalid | Same-state change is not an allowed edge |
| `IN_PROGRESS` | `RESOLVED` | Valid | Allowed: work completed |
| `IN_PROGRESS` | `CLOSED` | Invalid | Skip of `RESOLVED` is not allowed |
| `IN_PROGRESS` | `CANCELLED` | Valid | Allowed cancellation from `IN_PROGRESS` |
| `RESOLVED` | `OPEN` | Invalid | Backward / reopen is not allowed (requirements example) |
| `RESOLVED` | `IN_PROGRESS` | Invalid | Backward transition is not allowed |
| `RESOLVED` | `RESOLVED` | Invalid | Same-state change is not an allowed edge |
| `RESOLVED` | `CLOSED` | Valid | Allowed: lifecycle complete |
| `RESOLVED` | `CANCELLED` | Invalid | Cancellation is not allowed after `RESOLVED` |
| `CLOSED` | `OPEN` | Invalid | Terminal state; reopen is not allowed (requirements example) |
| `CLOSED` | `IN_PROGRESS` | Invalid | Terminal state; no outbound edges |
| `CLOSED` | `RESOLVED` | Invalid | Terminal state; no outbound edges |
| `CLOSED` | `CLOSED` | Invalid | Terminal state; same-state change is not allowed |
| `CLOSED` | `CANCELLED` | Invalid | Terminal state; cannot cancel after close |
| `CANCELLED` | `OPEN` | Invalid | Terminal state; reopen is not allowed (requirements example) |
| `CANCELLED` | `IN_PROGRESS` | Invalid | Terminal state; no outbound edges |
| `CANCELLED` | `RESOLVED` | Invalid | Terminal state; no outbound edges |
| `CANCELLED` | `CLOSED` | Invalid | Terminal state; cannot close a cancelled ticket |
| `CANCELLED` | `CANCELLED` | Invalid | Terminal state; same-state change is not allowed |

Count check for tests: **5 × 5 = 25** pairs. **5 valid**, **20 invalid**. Unit tests of the policy should cover all 25. Integration tests must cover at least every **valid** edge and every **requirements example** invalid edge (`CLOSED → OPEN`, `RESOLVED → OPEN`, `CANCELLED → OPEN`), plus at least one skip and one terminal outbound besides reopen.

---

## 6. Reachability (for fixture setup)

How a test may legally obtain each current state **through the API** (starting from create → `OPEN`):

| Desired current status | Legal path |
| --- | --- |
| `OPEN` | Create only |
| `IN_PROGRESS` | `OPEN → IN_PROGRESS` |
| `RESOLVED` | `OPEN → IN_PROGRESS → RESOLVED` |
| `CLOSED` | `OPEN → IN_PROGRESS → RESOLVED → CLOSED` |
| `CANCELLED` | `OPEN → CANCELLED` **or** `OPEN → IN_PROGRESS → CANCELLED` |

Tests must not insert illegal statuses via SQL to prove the HTTP contract, except for isolated persistence/check tests. Domain unit tests may construct an aggregate already in a given status.

---

## 7. Invalid transition — expected behavior and API error semantics

### 7.1 Persistence and domain behavior

When `(current, requested)` is **Invalid** in §5:

| Observable | Expected |
| --- | --- |
| Decision location | Backend domain/business logic (not UI, not “save DTO fields”) |
| HTTP outcome | Error response in §7.2 — **not** 2xx |
| Ticket `status` | Unchanged |
| Title, description, priority, assignee | Unchanged |
| Comments | Unchanged |
| Row existence | Ticket still exists |
| `updated_at` | Unchanged |
| Optimistic lock `version` (if present) | Unchanged |

Repeat request: still invalid; still unchanged; not a retry that later succeeds without a **valid** edge from the **then-current** status.

### 7.2 HTTP semantics for an illegal transition

Applies when:

- Ticket exists  
- Requested status is one of the five labels  
- Pair is **Invalid** in §5  
- Caller used the **transition** operation  

| Item | Contract (test against this) |
| --- | --- |
| HTTP status | **409 Conflict** |
| Meaning | The request is understood, but it conflicts with the ticket’s current lifecycle state |
| Response `Content-Type` | `application/json` |
| Body fields (required) | `code`, `message`, `currentStatus`, `requestedStatus` |
| `code` | `ILLEGAL_STATUS_TRANSITION` (exact string) |
| `message` | Non-empty, human-readable, safe to show in the UI. Must mention that the transition is not allowed. Must **not** include stack traces, SQL, or secrets |
| `currentStatus` | The persisted status **before** the request (one of the five labels) |
| `requestedStatus` | The status the client asked for |
| Success body | Must not be returned |
| Location / other headers | Not required |

Example shape (illustrative, not implementation):

```json
{
  "code": "ILLEGAL_STATUS_TRANSITION",
  "message": "Transition from CLOSED to OPEN is not allowed.",
  "currentStatus": "CLOSED",
  "requestedStatus": "OPEN"
}
```

The UI must display `message` (and may use `code`) so “meaningful errors in the UI” is testable once the frontend is built.

**409 vs 422:** this specification **standardizes on 409** so tests have a single expected status. 409 denotes conflict with current resource state. Unknown labels and missing fields stay **400**; missing ticket stays **404**.

### 7.3 Related errors (not illegal-transition 409)

| Situation | HTTP | `code` (exact) | Persist status? |
| --- | --- | --- | --- |
| Ticket id does not exist | **404** | `TICKET_NOT_FOUND` | no row to change |
| Requested status missing / null / blank | **400** | `INVALID_REQUEST` | no |
| Requested status not one of the five labels (including wrong case, e.g. `open`, `Closed`, `DONE`) | **400** | `INVALID_STATUS_VALUE` | no |
| `status` sent on the **field-update** API | **400** | `STATUS_NOT_UPDATABLE` | no (status and, for this request, other fields) |
| `status` sent on the **create** API | **400** | `STATUS_NOT_UPDATABLE` | no ticket is created |
| Malformed JSON | **400** | `INVALID_REQUEST` | no |
| Valid transition | **200** or **200/204 with body** — see §8 | n/a | yes, new status |

Wrong-case labels are **invalid values**, not table rows in §5.

### 7.4 Valid transition HTTP semantics

| Item | Contract |
| --- | --- |
| HTTP status | **200 OK** |
| Body | Ticket representation including `status` equal to the requested state |
| Persist | `status` is the requested state after commit |
| Repeat of the same request | Second call is **Invalid** (same-state), therefore **409** with `ILLEGAL_STATUS_TRANSITION` |

Whether the success status is 200 vs 204 is fixed here as **200 OK with ticket body** so clients and tests can read `status` without a follow-up GET. A subsequent GET must return the same `status`.

---

## 8. Test catalog (write tests from this list)

### 8.1 Domain unit tests (no HTTP, no UI)

For each of the 25 rows in §5:

- Arrange a ticket whose `status` is **Current State**  
- Act: `transitionTo(Requested State)` (conceptual)  
- If **Valid:** result success; current status equals requested  
- If **Invalid:** result failure/exception mapped later to §7.2; status still **Current State**

Additional unit tests:

- Policy contains exactly the five allowed edges  
- Unknown requested label is rejected before/without treating it as a table miss of type “illegal transition” if the implementation distinguishes `INVALID_STATUS_VALUE` (preferred)

### 8.2 Backend integration tests (HTTP + database)

Setup: persist via API or test fixture as in §6.

**Valid (must pass):**

1. `OPEN → IN_PROGRESS`  
2. `IN_PROGRESS → RESOLVED`  
3. `RESOLVED → CLOSED`  
4. `OPEN → CANCELLED`  
5. `IN_PROGRESS → CANCELLED`  
6. Full path `OPEN → IN_PROGRESS → RESOLVED → CLOSED`  
7. After valid transition, process restart (or new HTTP client) still reads the new status (persistence acceptance)

**Invalid — requirements examples (must reject with 409 / `ILLEGAL_STATUS_TRANSITION`):**

8. `CLOSED → OPEN`  
9. `RESOLVED → OPEN`  
10. `CANCELLED → OPEN`  

**Invalid — additional (must reject the same way):**

11. `OPEN → RESOLVED` (skip)  
12. `OPEN → CLOSED` (skip)  
13. `IN_PROGRESS → CLOSED` (skip)  
14. `IN_PROGRESS → OPEN` (backward)  
15. `RESOLVED → IN_PROGRESS` (backward)  
16. `RESOLVED → CANCELLED`  
17. `CLOSED → IN_PROGRESS`, `CLOSED → RESOLVED`, `CLOSED → CANCELLED`, `CLOSED → CLOSED`  
18. `CANCELLED → IN_PROGRESS`, `CANCELLED → RESOLVED`, `CANCELLED → CLOSED`, `CANCELLED → CANCELLED`  
19. Same-state from `OPEN` and from `IN_PROGRESS`  

**Bypass / contract:**

20. Field-update request that includes `status` → **400** `STATUS_NOT_UPDATABLE`; GET shows original status  
21. Create request that includes `status` → **400** `STATUS_NOT_UPDATABLE`; no ticket is persisted  
22. Transition on unknown ticket id → **404** `TICKET_NOT_FOUND`  
23. Transition to `done` / `open` / empty → **400** `INVALID_STATUS_VALUE` or `INVALID_REQUEST`  
24. After invalid transition, GET returns previous status  

**Not sufficient for acceptance:** frontend-only tests that never send the illegal HTTP request.

### 8.3 Assertions for every integration test

- Status code matches §7  
- JSON `code` matches exactly  
- For 409: `currentStatus` and `requestedStatus` match the attempt  
- Database (or GET) confirms persist/no-persist  
- No secret or stack trace in body  

---

## 9. Out of scope for this machine

Unchanged by a transition unless a later product rule says otherwise:

- Title, description, priority, assignee  
- Comments (adding a comment is not a status change)  
- Search and status **filter** (filter reads current status; it does not transition)

Authn/authz who may transition is **not** specified in the approved requirements; this machine applies to any caller of the backend transition API until a permission model is added.

---

## 10. Summary for implementers

| Question | Answer |
| --- | --- |
| How many states? | Five: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` |
| How many legal changes? | Five edges in §3 |
| How many illegal directed pairs among the five? | Twenty (including same-state) |
| Where validated? | Backend business/domain logic |
| Field update of `status`? | Forbidden |
| Illegal edge HTTP? | 409, `code=ILLEGAL_STATUS_TRANSITION`, status unchanged |
| Terminal states? | `CLOSED`, `CANCELLED` |
