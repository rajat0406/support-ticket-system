"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { ApiErrorMessage } from "@/components/ApiErrorMessage";
import { ApiRequestError, createTicket, fieldErrorsFromMessage } from "@/lib/api";
import {
  ApiError,
  PRIORITY_LABELS,
  TICKET_PRIORITIES,
  TicketPriority,
} from "@/lib/types";

export default function CreateTicketPage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TicketPriority>("MEDIUM");
  const [assignee, setAssignee] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      const created = await createTicket({
        title,
        description,
        priority,
        assignee: assignee.trim() ? assignee : null,
      });
      router.push(`/tickets/${created.id}`);
    } catch (caught) {
      if (caught instanceof ApiRequestError) {
        setError(caught.apiError);
        setFieldErrors(fieldErrorsFromMessage(caught.apiError.message));
      } else {
        setError({
          timestamp: new Date().toISOString(),
          status: 500,
          error: "Error",
          message: "Could not create the ticket.",
          path: "/api/v1/tickets",
        });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <header className="site-header">
        <div>
          <h1>Create ticket</h1>
          <p>New tickets start as Open. Status is assigned by the server.</p>
        </div>
        <Link className="button secondary" href="/">
          Back to list
        </Link>
      </header>

      <section className="panel">
        <ApiErrorMessage error={error} />
        <form onSubmit={onSubmit}>
          <div className="field">
            <label htmlFor="title">Title</label>
            <input
              id="title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              maxLength={120}
              required
            />
            {fieldErrors.title ? <span className="field-error">{fieldErrors.title}</span> : null}
          </div>
          <div className="field">
            <label htmlFor="description">Description</label>
            <textarea
              id="description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={4000}
              required
            />
            {fieldErrors.description ? (
              <span className="field-error">{fieldErrors.description}</span>
            ) : null}
          </div>
          <div className="field">
            <label htmlFor="priority">Priority</label>
            <select
              id="priority"
              value={priority}
              onChange={(event) => setPriority(event.target.value as TicketPriority)}
            >
              {TICKET_PRIORITIES.map((value) => (
                <option key={value} value={value}>
                  {PRIORITY_LABELS[value]}
                </option>
              ))}
            </select>
            {fieldErrors.priority ? (
              <span className="field-error">{fieldErrors.priority}</span>
            ) : null}
          </div>
          <div className="field">
            <label htmlFor="assignee">Assignee (optional)</label>
            <input
              id="assignee"
              value={assignee}
              onChange={(event) => setAssignee(event.target.value)}
              maxLength={80}
            />
            {fieldErrors.assignee ? (
              <span className="field-error">{fieldErrors.assignee}</span>
            ) : null}
          </div>
          <div className="button-row">
            <button type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Create ticket"}
            </button>
          </div>
        </form>
      </section>
    </main>
  );
}
