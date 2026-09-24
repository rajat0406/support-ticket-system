# Support Ticket Management System — UI Flows

**Status:** frontend interaction specification; not implementation  
**Sources:** `spec/requirements.md`, `spec/state-machine.md`  
**Not present:** `spec/api-contract.md` — flows refer to **conceptual API operations** from `spec/architecture.md` and error codes from `spec/state-machine.md`. Exact paths/query names remain unconfirmed until an API contract exists.  
**Constraint:** no React, Next.js, CSS, or component code in this document.

The UI is a client of the backend. It may **suggest** likely-valid actions. It must **never** treat a local rule as proof that a status change succeeded.

---

## 1. Screens and navigation

| Screen | Purpose | Typical entry |
| --- | --- | --- |
| Ticket list | Browse, search, filter | App home / after save |
| Ticket create | New ticket | List “Create ticket” |
| Ticket details | View fields, comments, status actions | Click a list row |
| Ticket edit | Change title, description, priority, assignee | Details “Edit” |

Status change and add-comment happen **on details**, not as a combined “save everything including status” form.

Conceptual routes (names only): list `/tickets`, create `/tickets/new`, details `/tickets/{id}`, edit `/tickets/{id}/edit`.

---

## 2. Global UI rules

1. Every mutating action waits for the HTTP result before treating data as saved.  
2. Success: update on-screen data from the **response body** (or a follow-up GET), not from the form guess.  
3. Failure: keep the previous server-known state on screen; show a meaningful error (`message` from JSON when present).  
4. Client-side checks are optional convenience. Backend validation is mandatory (`spec/requirements.md`).  
5. Status is **not** an editable field on create or edit. Create never sends `status`. Edit never sends `status` (`STATUS_NOT_UPDATABLE`).  
6. Loading, empty, validation, and backend-error states are specified per flow below.

---

## 3. Ticket creation

### 3.1 Flow

1. User opens create from the list.  
2. Form shows title, description, priority, assignee as product fields allow. **No status control.** Copy may state that new tickets start as `OPEN`.  
3. User submits. UI enters **submitting** (disable submit, show loading on the button).  
4. UI calls **create ticket** (no `status` in body).  
5. **2xx:** navigate to details of the created ticket (or list with the new row). Show `status = OPEN` from the server.  
6. **4xx/5xx:** stay on create; show errors (§13–14); form values preserved.

### 3.2 Validation (client, optional)

Until field rules are confirmed in the data model, the UI must not invent strict lengths as product law. Reasonable **draft** checks (blank title) may run, but submit still hits the backend if the user can proceed.

### 3.3 Empty / loading

| State | Behavior |
| --- | --- |
| Initial | Empty fields (or confirmed defaults only) |
| Submitting | Controls disabled; no double submit |
| After success | Leave create screen |

---

## 4. Ticket list

### 4.1 Flow

1. On enter, UI calls **list tickets** (current search keyword and status filter, if any).  
2. **Loading:** list region shows a loading indicator; do not show a fake empty “no tickets” message until the response arrives.  
3. **Success with items:** each row shows enough to identify the ticket (title, status, other confirmed fields). Row click → details.  
4. **Success with zero items:** empty state (§15), distinct from error.  
5. **Failure:** list error state (§14); offer retry.

### 4.2 Invariants

- List is read-only for status. No status dropdown on the row that PATCHes the ticket.  
- After create/edit/transition/comment, returning to list should reflect server data (refetch or merge from last known GET).

---

## 5. Ticket search

### 5.1 Flow

1. List screen has a keyword field.  
2. User submits search (explicit Search control and/or enter). Debounce is a UX choice, not a product rule.  
3. UI calls list/search with the keyword. **Loading** on the result region.  
4. Results replace the previous list. Status filter, if set, remains applied unless the user clears it (combination **unconfirmed** in API contract; UI must send both parameters when both are set, and display whatever the backend returns).

### 5.2 Empty / errors

| Outcome | UI |
| --- | --- |
| No matches | Empty search state: keyword was applied, zero tickets — not “the system has no tickets” |
| Backend error | Search error; previous results may be cleared or retained with an error banner; do not silently show an unfiltered list as if search succeeded |

Search semantics (case, partial vs exact) are backend-defined. The UI does not implement a second search over a stale full list and call it success.

---

## 6. Status filtering

### 6.1 Flow

1. List screen offers filter values: all tickets, plus each of `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`.  
2. Changing the filter calls the backend with the selected status (or omits status for “all”).  
3. **Loading** on the result region.  
4. Display only the server result set.

### 6.2 Rules

- Filter is a **query**, not a transition. It must not call the transition API.  
- “All” is not a ticket status.  
- Empty filter result uses a filter-empty message (e.g. no `CLOSED` tickets), not the global empty catalog message.

---

## 7. Ticket details

### 7.1 Flow

1. Load **get ticket** (fields + comments).  
2. **Loading:** details skeleton/placeholder; no actions that mutate until data is shown (or disable them).  
3. **404 `TICKET_NOT_FOUND`:** not-found empty/error; link back to list.  
4. **Other errors:** details error + retry.  
5. **Success:** show title, description, priority, assignee, **current status from server**, timestamps if present, comments in chronological order.  
6. Actions: Edit (fields), Add comment, **status actions** (§11). Not a single form that saves status with fields.

### 7.2 Empty comments

If the ticket exists but has no comments, show a comments empty state (“No comments yet”) and still show the add-comment control.

---

## 8. Ticket editing

### 8.1 Flow

1. From details, open edit with fields populated from the last successful GET.  
2. Editable: title, description, priority, assignee. **Status is displayed read-only or omitted from the form.**  
3. Submit → **field-update** API without `status`. **Submitting** state.  
4. **2xx:** use response (or GET) as new details; navigate to details.  
5. **4xx/5xx:** remain on edit; show field and/or banner errors; do not change details cache to unsaved values.

### 8.2 Must not

- Include `status` in the update body.  
- Use edit-save to move `OPEN` → `CLOSED` or any other lifecycle change.

---

## 9. Assignee change

Assignee change is the same **field-update** operation as title/description/priority, either:

- as part of the edit screen, or  
- as a details control that submits only assignee (still field-update, still no `status`).

Flow: user changes assignee → submitting → 2xx refresh details / 4xx show `message` and keep previous assignee.

Unassigned / “user must exist” remain product-unresolved; the UI follows backend errors rather than inventing a user directory unless one is specified.

---

## 10. Adding comments

### 10.1 Flow

1. On details, comment box + submit.  
2. Submitting: disable send; do not clear the box until **2xx**.  
3. **2xx:** append the returned comment (or refetch comments); clear the box. Ticket **status must not change** because a comment was added.  
4. **4xx/5xx:** keep text; show error.  
5. **404:** ticket gone; not-found behavior as details.

### 10.2 Empty / loading

| State | Behavior |
| --- | --- |
| No comments | Empty list + composer visible |
| Posting | Composer loading |
| Ticket loading | Composer disabled until ticket is loaded |

---

## 11. Changing status (authoritative backend)

### 11.1 Principle

The UI **must not assume every transition is valid.**

| Allowed | Not allowed |
| --- | --- |
| Offer actions that **correspond to legal edges from the last known server status** (usability) | A single dropdown of all five statuses as if any target were legal |
| Call the **dedicated transition** operation with one requested status | Sending `status` on create or field-update |
| Treat **2xx + body.status** as the new current status | Treating a click as success before the response |
| Show **409** `ILLEGAL_STATUS_TRANSITION` `message` and keep old status | Hiding the error because “the button should not have been possible” |
| After 409, refetch or keep `currentStatus` from the error body | Retrying in a loop; or setting UI status to the requested value anyway |

The backend remains the **only** authority (`spec/state-machine.md` §4). Suggested buttons are a hint, not a lock.

### 11.2 Suggested actions by last known status

Use this table for **which controls to show**. Do not show “set status to X” for every X.

**Sync rule:** this table must mirror `spec/state-machine.md` §5 exactly. Any change to the state machine requires updating this table in the same change. If the API later exposes the allowed transitions for the current ticket in the ticket response (**requires confirmation**), the UI should render actions from that server-provided list instead of this hardcoded table.

| Last known `status` | Show these transition actions | Do not show (examples) |
| --- | --- | --- |
| `OPEN` | Start progress → `IN_PROGRESS`; Cancel → `CANCELLED` | Resolve, Close, reopen, skip to `CLOSED` |
| `IN_PROGRESS` | Resolve → `RESOLVED`; Cancel → `CANCELLED` | Open, Close (skip), same-state |
| `RESOLVED` | Close → `CLOSED` | Cancel, Open, In progress |
| `CLOSED` | **None** (terminal). Show status as closed | Reopen, any other target |
| `CANCELLED` | **None** (terminal) | Reopen, Close, etc. |

Same-state is never offered.

### 11.3 Flow (each action)

1. User chooses one suggested target (e.g. “Start progress”).  
2. Optional confirm for `CANCELLED` (UX only).  
3. **Transitioning:** disable all status actions; show loading on the chosen action.  
4. `POST` (conceptual) transition with `requestedStatus`.  
5. **200:** set displayed status to `body.status`; replace available actions using §11.2 for the **new** status. List badge updates if the user returns to list.  
6. **409 `ILLEGAL_STATUS_TRANSITION`:** display `message`; set displayed status to `currentStatus` from the body if present, else refetch; rebuild actions from server status. This covers races (another client already transitioned) and any mistaken extra control.  
7. **400 `INVALID_STATUS_VALUE` / `INVALID_REQUEST`:** show `message`; do not change status.  
8. **404:** not-found flow.  
9. **5xx / network:** show backend/network error; status unchanged; actions re-enabled.

### 11.4 Why a 409 can still appear

- Stale UI (ticket moved on the server).  
- A future screen that offers a wider control.  
- Bugs.

The details page must still render 409 meaningfully. Frontend tests of “button hidden” do **not** replace backend state-machine tests.

---

## 12. Validation errors (client + 400)

| Source | UI |
| --- | --- |
| Blank required field before submit (if required is confirmed) | Inline error on the field; no request, or request still allowed if rules unconfirmed |
| HTTP **400** with field errors | Map to fields when the payload includes them; otherwise banner with `message` |
| `INVALID_REQUEST` | Banner; form stays |
| `INVALID_STATUS_VALUE` | Banner on details; should be rare if UI only sends the five labels |
| `STATUS_NOT_UPDATABLE` | Must not occur if the UI omits `status` on create and edit; if it does, show `message` and fix the client |

Client-side checks are convenience only and must never be stricter than the backend: if the UI is unsure whether a value is valid, it must submit and display the backend error rather than block the user.

Do not show stack traces.

---

## 13. Backend errors (non-validation)

| HTTP / code | UI |
| --- | --- |
| **409** `ILLEGAL_STATUS_TRANSITION` | Banner using `message`; status from server; §11.3 |
| **404** `TICKET_NOT_FOUND` | Not-found; back to list |
| **409** optimistic lock (if later specified) | Banner; refetch; ask user to retry |
| **500** | Generic safe message plus `message` if it is user-safe; retry |
| Network failure | Offline/retry message; no optimistic status change |

All of these count as “UI shows meaningful errors.”

---

## 14. Loading states (summary)

| Surface | Loading means |
| --- | --- |
| List / search / filter | Result region waiting; avoid false empty |
| Details GET | Page waiting; mutations disabled |
| Create / edit / comment / transition submit | That action waiting; prevent double submit |

Do not use a spinner as a success signal.

---

## 15. Empty states (summary)

| Situation | Message intent |
| --- | --- |
| List, no tickets in system (no search/filter) | No tickets yet + affordance to create |
| Search, zero hits | No tickets match the keyword |
| Filter, zero hits | No tickets in that status |
| Details, no comments | No comments yet |
| Details 404 | Ticket not found |

Empty ≠ error. Error ≠ empty.

---

## 16. Flow map

```
List ──create──► Create ──2xx──► Details
 ▲                  │
 │                  └──4xx── stay + errors
 │
 ├──search/filter── refetch list (empty or rows)
 │
 └──row──► Details ──edit──► Edit ──2xx──► Details
              │                 └──4xx── stay + errors
              ├── comment ──2xx── same page, comments updated
              └── transition ──200── new status + new actions
                                ──409── message + old/server status
```

---

## 17. Unresolved (UI must not freeze as product)

These remain open in requirements/data-model/API contract. Flows above stay valid:

- Exact REST paths and query parameter names (`spec/api-contract.md` missing)  
- Pagination and sort controls  
- Required fields and max lengths  
- Priority value set and assignee widget (text vs user picker)  
- Whether search+filter are one request  

When `spec/api-contract.md` exists, this document should be updated to name the real operations; the status-change rules in §11 do not change.
