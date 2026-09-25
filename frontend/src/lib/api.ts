import type {
  ApiError,
  Comment,
  CreateCommentRequest,
  CreateTicketRequest,
  TicketDetail,
  TicketSummary,
  UpdateStatusRequest,
  UpdateTicketRequest,
} from "./types";

export class ApiRequestError extends Error {
  readonly apiError: ApiError;

  constructor(apiError: ApiError) {
    super(apiError.message);
    this.name = "ApiRequestError";
    this.apiError = apiError;
  }
}

function isApiError(value: unknown): value is ApiError {
  if (!value || typeof value !== "object") {
    return false;
  }
  const body = value as Record<string, unknown>;
  return (
    typeof body.timestamp === "string" &&
    typeof body.status === "number" &&
    typeof body.error === "string" &&
    typeof body.message === "string" &&
    typeof body.path === "string"
  );
}

async function parseError(response: Response): Promise<ApiRequestError> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (isApiError(body)) {
    return new ApiRequestError(body);
  }
  return new ApiRequestError({
    timestamp: new Date().toISOString(),
    status: response.status,
    error: response.statusText || "Error",
    message: "Request failed.",
    path: response.url,
  });
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
    cache: "no-store",
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function listTickets(keyword?: string, status?: string): Promise<TicketSummary[]> {
  const params = new URLSearchParams();
  if (keyword && keyword.trim()) {
    params.set("keyword", keyword.trim());
  }
  if (status && status.trim()) {
    params.set("status", status.trim());
  }
  const query = params.toString();
  return request<TicketSummary[]>(`/api/v1/tickets${query ? `?${query}` : ""}`);
}

export function getTicket(id: number): Promise<TicketDetail> {
  return request<TicketDetail>(`/api/v1/tickets/${id}`);
}

export function createTicket(body: CreateTicketRequest): Promise<TicketDetail> {
  return request<TicketDetail>("/api/v1/tickets", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateTicket(id: number, body: UpdateTicketRequest): Promise<TicketDetail> {
  return request<TicketDetail>(`/api/v1/tickets/${id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export function changeStatus(id: number, body: UpdateStatusRequest): Promise<TicketDetail> {
  return request<TicketDetail>(`/api/v1/tickets/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export function addComment(id: number, body: CreateCommentRequest) {
  return request<Comment>(`/api/v1/tickets/${id}/comments`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** Split `field: reason; field: reason` into a map for form field display. */
export function fieldErrorsFromMessage(message: string): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const segment of message.split(";")) {
    const trimmed = segment.trim();
    const colon = trimmed.indexOf(":");
    if (colon <= 0) {
      continue;
    }
    const field = trimmed.slice(0, colon).trim();
    const reason = trimmed.slice(colon + 1).trim();
    if (field && reason) {
      errors[field] = reason;
    }
  }
  return errors;
}
