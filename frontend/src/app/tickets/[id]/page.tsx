"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { ApiErrorMessage } from "@/components/ApiErrorMessage";
import {
  ApiRequestError,
  addComment,
  changeStatus,
  fieldErrorsFromMessage,
  getTicket,
  updateTicket,
} from "@/lib/api";
import {
  ApiError,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  TicketDetail,
  TicketPriority,
  TicketStatus,
} from "@/lib/types";

function formatWhen(value: string): string {
  return new Date(value).toLocaleString();
}

export default function TicketDetailPage() {
  const params = useParams<{ id: string }>();
  const ticketId = Number(params.id);

  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);

  const [statusError, setStatusError] = useState<ApiError | null>(null);
  const [selectedStatus, setSelectedStatus] = useState<TicketStatus>("OPEN");
  const [statusBusy, setStatusBusy] = useState(false);

  const [editTitle, setEditTitle] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editPriority, setEditPriority] = useState<TicketPriority>("MEDIUM");
  const [editAssignee, setEditAssignee] = useState("");
  const [editError, setEditError] = useState<ApiError | null>(null);
  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>({});
  const [editBusy, setEditBusy] = useState(false);

  const [authorName, setAuthorName] = useState("");
  const [commentText, setCommentText] = useState("");
  const [commentError, setCommentError] = useState<ApiError | null>(null);
  const [commentFieldErrors, setCommentFieldErrors] = useState<Record<string, string>>({});
  const [commentBusy, setCommentBusy] = useState(false);

  const finished =
    ticket?.status === "CLOSED" || ticket?.status === "CANCELLED";

  const applyTicket = useCallback((next: TicketDetail) => {
    setTicket(next);
    setSelectedStatus(next.status);
    setEditTitle(next.title);
    setEditDescription(next.description);
    setEditPriority(next.priority);
    setEditAssignee(next.assignee ?? "");
  }, []);

  const load = useCallback(async () => {
    if (!Number.isFinite(ticketId)) {
      setLoadError({
        timestamp: new Date().toISOString(),
        status: 400,
        error: "Bad Request",
        message: "Ticket reference is invalid.",
        path: "/api/v1/tickets",
      });
      setLoading(false);
      return;
    }
    setLoading(true);
    setLoadError(null);
    try {
      applyTicket(await getTicket(ticketId));
    } catch (caught) {
      if (caught instanceof ApiRequestError) {
        setLoadError(caught.apiError);
        setTicket(null);
      } else {
        setLoadError({
          timestamp: new Date().toISOString(),
          status: 500,
          error: "Error",
          message: "Could not load the ticket.",
          path: `/api/v1/tickets/${ticketId}`,
        });
      }
    } finally {
      setLoading(false);
    }
  }, [applyTicket, ticketId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onChangeStatus(event: FormEvent) {
    event.preventDefault();
    if (!ticket) {
      return;
    }
    setStatusBusy(true);
    setStatusError(null);
    try {
      applyTicket(await changeStatus(ticket.id, { status: selectedStatus }));
    } catch (caught) {
      if (caught instanceof ApiRequestError) {
        setStatusError(caught.apiError);
      } else {
        setStatusError({
          timestamp: new Date().toISOString(),
          status: 500,
          error: "Error",
          message: "Could not change status.",
          path: `/api/v1/tickets/${ticket.id}/status`,
        });
      }
      try {
        applyTicket(await getTicket(ticket.id));
      } catch {
        // Keep the last known ticket if reload fails.
      }
    } finally {
      setStatusBusy(false);
    }
  }

  async function onEdit(event: FormEvent) {
    event.preventDefault();
    if (!ticket || finished) {
      return;
    }
    setEditBusy(true);
    setEditError(null);
    setEditFieldErrors({});
    try {
      applyTicket(
        await updateTicket(ticket.id, {
          title: editTitle,
          description: editDescription,
          priority: editPriority,
          assignee: editAssignee.trim() ? editAssignee : null,
        })
      );
    } catch (caught) {
      if (caught instanceof ApiRequestError) {
        setEditError(caught.apiError);
        setEditFieldErrors(fieldErrorsFromMessage(caught.apiError.message));
      } else {
        setEditError({
          timestamp: new Date().toISOString(),
          status: 500,
          error: "Error",
          message: "Could not update the ticket.",
          path: `/api/v1/tickets/${ticket.id}`,
        });
      }
    } finally {
      setEditBusy(false);
    }
  }

  async function onComment(event: FormEvent) {
    event.preventDefault();
    if (!ticket || finished) {
      return;
    }
    setCommentBusy(true);
    setCommentError(null);
    setCommentFieldErrors({});
    try {
      await addComment(ticket.id, { authorName, text: commentText });
      setAuthorName("");
      setCommentText("");
      applyTicket(await getTicket(ticket.id));
    } catch (caught) {
      if (caught instanceof ApiRequestError) {
        setCommentError(caught.apiError);
        setCommentFieldErrors(fieldErrorsFromMessage(caught.apiError.message));
      } else {
        setCommentError({
          timestamp: new Date().toISOString(),
          status: 500,
          error: "Error",
          message: "Could not add the comment.",
          path: `/api/v1/tickets/${ticket.id}/comments`,
        });
      }
    } finally {
      setCommentBusy(false);
    }
  }

  return (
    <main>
      <header className="site-header">
        <div>
          <h1>{ticket ? `Ticket #${ticket.id}` : "Ticket detail"}</h1>
          <p>Review details, move status, edit fields, and add comments.</p>
        </div>
        <Link className="button secondary" href="/">
          Back to list
        </Link>
      </header>

      {loading ? <p className="muted">Loading ticket…</p> : null}
      <ApiErrorMessage error={loadError} />
      {loadError?.status === 404 ? (
        <p>
          <Link href="/">Return to the ticket list</Link>
        </p>
      ) : null}

      {ticket ? (
        <div className="stack">
          <section className="panel">
            <h2>{ticket.title}</h2>
            <dl className="meta-grid">
              <div>
                <dt>Reference</dt>
                <dd className="mono">#{ticket.id}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{STATUS_LABELS[ticket.status]}</dd>
              </div>
              <div>
                <dt>Priority</dt>
                <dd>{PRIORITY_LABELS[ticket.priority]}</dd>
              </div>
              <div>
                <dt>Assignee</dt>
                <dd>{ticket.assignee ?? "Unassigned"}</dd>
              </div>
              <div>
                <dt>Created</dt>
                <dd>{formatWhen(ticket.createdAt)}</dd>
              </div>
              <div>
                <dt>Updated</dt>
                <dd>{formatWhen(ticket.updatedAt)}</dd>
              </div>
            </dl>
            <p style={{ whiteSpace: "pre-wrap" }}>{ticket.description}</p>
          </section>

          <section className="panel">
            <h2>Change status</h2>
            <ApiErrorMessage error={statusError} />
            <form onSubmit={onChangeStatus}>
              <div className="field">
                <label htmlFor="status">New status</label>
                <select
                  id="status"
                  value={selectedStatus}
                  onChange={(event) =>
                    setSelectedStatus(event.target.value as TicketStatus)
                  }
                >
                  {TICKET_STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {STATUS_LABELS[value]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="button-row">
                <button type="submit" disabled={statusBusy}>
                  {statusBusy ? "Updating…" : "Update status"}
                </button>
              </div>
            </form>
          </section>

          <section className="panel">
            <h2>Edit details</h2>
            {finished ? (
              <p className="muted">
                Closed and Cancelled tickets cannot be edited.
              </p>
            ) : null}
            <ApiErrorMessage error={editError} />
            <form onSubmit={onEdit}>
              <div className="field">
                <label htmlFor="edit-title">Title</label>
                <input
                  id="edit-title"
                  value={editTitle}
                  onChange={(event) => setEditTitle(event.target.value)}
                  maxLength={120}
                  disabled={finished || editBusy}
                  required
                />
                {editFieldErrors.title ? (
                  <span className="field-error">{editFieldErrors.title}</span>
                ) : null}
              </div>
              <div className="field">
                <label htmlFor="edit-description">Description</label>
                <textarea
                  id="edit-description"
                  value={editDescription}
                  onChange={(event) => setEditDescription(event.target.value)}
                  maxLength={4000}
                  disabled={finished || editBusy}
                  required
                />
                {editFieldErrors.description ? (
                  <span className="field-error">{editFieldErrors.description}</span>
                ) : null}
              </div>
              <div className="field">
                <label htmlFor="edit-priority">Priority</label>
                <select
                  id="edit-priority"
                  value={editPriority}
                  onChange={(event) =>
                    setEditPriority(event.target.value as TicketPriority)
                  }
                  disabled={finished || editBusy}
                >
                  {TICKET_PRIORITIES.map((value) => (
                    <option key={value} value={value}>
                      {PRIORITY_LABELS[value]}
                    </option>
                  ))}
                </select>
                {editFieldErrors.priority ? (
                  <span className="field-error">{editFieldErrors.priority}</span>
                ) : null}
              </div>
              <div className="field">
                <label htmlFor="edit-assignee">Assignee</label>
                <input
                  id="edit-assignee"
                  value={editAssignee}
                  onChange={(event) => setEditAssignee(event.target.value)}
                  maxLength={80}
                  disabled={finished || editBusy}
                  placeholder="Leave blank for Unassigned"
                />
                {editFieldErrors.assignee ? (
                  <span className="field-error">{editFieldErrors.assignee}</span>
                ) : null}
              </div>
              <div className="button-row">
                <button type="submit" disabled={finished || editBusy}>
                  {editBusy ? "Saving…" : "Save details"}
                </button>
              </div>
            </form>
          </section>

          <section className="panel">
            <h2>Comments</h2>
            {ticket.comments.length === 0 ? (
              <p className="muted">No comments yet.</p>
            ) : (
              <ul className="comment-list">
                {ticket.comments.map((comment) => (
                  <li key={comment.id}>
                    <strong>{comment.authorName}</strong>
                    <span className="muted"> · {formatWhen(comment.createdAt)}</span>
                    <p style={{ whiteSpace: "pre-wrap", margin: "0.35rem 0 0" }}>
                      {comment.text}
                    </p>
                  </li>
                ))}
              </ul>
            )}

            {finished ? (
              <p className="muted">
                Closed and Cancelled tickets cannot receive comments.
              </p>
            ) : null}
            <ApiErrorMessage error={commentError} />
            <form onSubmit={onComment}>
              <div className="field">
                <label htmlFor="authorName">Your name</label>
                <input
                  id="authorName"
                  value={authorName}
                  onChange={(event) => setAuthorName(event.target.value)}
                  maxLength={80}
                  disabled={finished || commentBusy}
                  required
                />
                {commentFieldErrors.authorName ? (
                  <span className="field-error">{commentFieldErrors.authorName}</span>
                ) : null}
              </div>
              <div className="field">
                <label htmlFor="commentText">Comment</label>
                <textarea
                  id="commentText"
                  value={commentText}
                  onChange={(event) => setCommentText(event.target.value)}
                  maxLength={2000}
                  disabled={finished || commentBusy}
                  required
                />
                {commentFieldErrors.text ? (
                  <span className="field-error">{commentFieldErrors.text}</span>
                ) : null}
              </div>
              <div className="button-row">
                <button type="submit" disabled={finished || commentBusy}>
                  {commentBusy ? "Posting…" : "Add comment"}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </main>
  );
}
