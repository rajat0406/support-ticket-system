"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { ApiErrorMessage } from "@/components/ApiErrorMessage";
import { ApiRequestError, listTickets } from "@/lib/api";
import {
  ApiError,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TICKET_STATUSES,
  TicketSummary,
} from "@/lib/types";

function formatWhen(value: string): string {
  return new Date(value).toLocaleString();
}

export default function TicketListPage() {
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [appliedStatus, setAppliedStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(async (nextKeyword: string, nextStatus: string) => {
    setLoading(true);
    setError(null);
    try {
      const rows = await listTickets(nextKeyword || undefined, nextStatus || undefined);
      setTickets(rows);
      setAppliedKeyword(nextKeyword);
      setAppliedStatus(nextStatus);
    } catch (caught) {
      if (caught instanceof ApiRequestError) {
        setError(caught.apiError);
        setTickets([]);
      } else {
        setError({
          timestamp: new Date().toISOString(),
          status: 500,
          error: "Error",
          message: "Could not load tickets.",
          path: "/api/v1/tickets",
        });
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load("", "");
  }, [load]);

  function onFilter(event: FormEvent) {
    event.preventDefault();
    void load(keyword, status);
  }

  function onClear() {
    setKeyword("");
    setStatus("");
    void load("", "");
  }

  const filtersActive = Boolean(appliedKeyword || appliedStatus);
  const showEmptyMatch = !loading && !error && tickets.length === 0;

  return (
    <main>
      <header className="site-header">
        <div>
          <h1>Support tickets</h1>
          <p>Find open work, filter by status, and open a ticket for details.</p>
        </div>
        <Link className="button" href="/tickets/new">
          Create ticket
        </Link>
      </header>

      <section className="panel">
        <form className="toolbar" onSubmit={onFilter}>
          <div className="field">
            <label htmlFor="keyword">Keyword</label>
            <input
              id="keyword"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="Title, description, or comment"
              maxLength={200}
            />
          </div>
          <div className="field">
            <label htmlFor="status">Status</label>
            <select
              id="status"
              value={status}
              onChange={(event) => setStatus(event.target.value)}
            >
              <option value="">All statuses</option>
              {TICKET_STATUSES.map((value) => (
                <option key={value} value={value}>
                  {STATUS_LABELS[value]}
                </option>
              ))}
            </select>
          </div>
          <div className="button-row">
            <button type="submit">Apply</button>
            <button type="button" className="secondary" onClick={onClear}>
              Clear
            </button>
          </div>
        </form>

        <ApiErrorMessage error={error} />

        {loading ? <p className="muted">Loading tickets…</p> : null}

        {showEmptyMatch ? (
          <p className="empty-state">
            {filtersActive
              ? "No tickets match the current keyword or status filter."
              : "No tickets yet. Create the first one to get started."}
          </p>
        ) : null}

        {!loading && tickets.length > 0 ? (
          <table className="ticket-table">
            <thead>
              <tr>
                <th>Reference</th>
                <th>Title</th>
                <th>Status</th>
                <th>Priority</th>
                <th>Assignee</th>
                <th>Last updated</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((ticket) => (
                <tr key={ticket.id}>
                  <td className="mono">
                    <Link href={`/tickets/${ticket.id}`}>#{ticket.id}</Link>
                  </td>
                  <td>
                    <Link href={`/tickets/${ticket.id}`}>{ticket.title}</Link>
                  </td>
                  <td>{STATUS_LABELS[ticket.status]}</td>
                  <td>{PRIORITY_LABELS[ticket.priority]}</td>
                  <td>{ticket.assignee ?? "Unassigned"}</td>
                  <td>{formatWhen(ticket.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </section>
    </main>
  );
}
