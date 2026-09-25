import type { ApiError } from "@/lib/types";

export function ApiErrorMessage({ error }: { error: ApiError | null }) {
  if (!error) {
    return null;
  }
  return (
    <p className="error-banner" role="alert">
      {error.message}
    </p>
  );
}
