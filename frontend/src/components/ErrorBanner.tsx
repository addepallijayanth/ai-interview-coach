import type { ApiError } from '../api/client';

export function ErrorBanner({ error, onRetry }: { error: ApiError | Error; onRetry?: () => void }) {
  return (
    <div className="banner error" role="alert">
      <span>{error.message}</span>
      {onRetry && (
        <button type="button" className="btn btn-secondary btn-sm" onClick={onRetry}>Try again</button>
      )}
    </div>
  );
}

export function LoadingCard({ text }: { text: string }) {
  return (
    <div className="card row" aria-live="polite">
      <span className="spinner" />
      <span>{text}</span>
    </div>
  );
}
