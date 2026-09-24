Analyze the following Support Ticket Management System requirements.



Requirements:

Create a ticket.

List tickets.

View ticket details.

Update title, description, priority and assignee.

Add comments.

Search tickets by keyword.

Filter tickets by status.

Persist data in a database.

Validate input at the backend.

Display meaningful errors in the UI.

The backend must enforce this state machine:

OPEN → IN_PROGRESS → RESOLVED → CLOSED

OPEN → CANCELLED
IN_PROGRESS → CANCELLED

Invalid transitions must be rejected.

Examples:

CLOSED → OPEN = invalid
RESOLVED → OPEN = invalid
CANCELLED → OPEN = invalid

Acceptance criteria:

Ticket can be created from UI.

Tickets can be listed.

Ticket details can be viewed.

Ticket fields can be updated.

Assignee can be changed.

Comments can be added.

Search works.

Status filter works.

Valid status transitions work.

Invalid status transitions are rejected by backend.

Data survives application restart.

Backend validation works.

UI shows meaningful errors.

State-machine integration tests pass.

No secrets are committed.

Produce a requirements analysis only.

Identify:

Functional requirements

Non-functional requirements

Business rules

State-machine rules

Validation requirements

Persistence requirements

API requirements

UI requirements

Testing requirements

Security/configuration requirements

Ambiguities that require an explicit decision
