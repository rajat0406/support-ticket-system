export type TicketStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "RESOLVED"
  | "CLOSED"
  | "CANCELLED";

export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface TicketSummary {
  id: number;
  title: string;
  status: TicketStatus;
  priority: TicketPriority;
  assignee: string | null;
  updatedAt: string;
}

export interface Comment {
  id: number;
  authorName: string;
  text: string;
  createdAt: string;
}

export interface TicketDetail {
  id: number;
  title: string;
  description: string;
  priority: TicketPriority;
  assignee: string | null;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  comments: Comment[];
}

export interface CreateTicketRequest {
  title: string;
  description: string;
  priority: TicketPriority;
  assignee?: string | null;
}

export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  priority?: TicketPriority;
  assignee?: string | null;
}

export interface UpdateStatusRequest {
  status: TicketStatus;
}

export interface CreateCommentRequest {
  authorName: string;
  text: string;
}

export const TICKET_STATUSES: TicketStatus[] = [
  "OPEN",
  "IN_PROGRESS",
  "RESOLVED",
  "CLOSED",
  "CANCELLED",
];

export const TICKET_PRIORITIES: TicketPriority[] = [
  "LOW",
  "MEDIUM",
  "HIGH",
  "CRITICAL",
];

export const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In Progress",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
  CANCELLED: "Cancelled",
};

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  CRITICAL: "Critical",
};
