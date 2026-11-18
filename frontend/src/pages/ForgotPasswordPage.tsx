import { useState, type FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import { AuthCard } from '../components/AuthShell';
import { EmailDeliveryHint } from '../components/EmailDeliveryHint';

export function ForgotPasswordPage() {
  const location = useLocation();
  const [email, setEmail] = useState((location.state as { email?: string } | null)?.email ?? '');
  const [sentTo, setSentTo] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      await api.forgotPassword(email.trim());
      setSentTo(email.trim());
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setBusy(false);
    }
  }

  if (sentTo) {
    return (
      <AuthCard title="Check your inbox">
        <div className="mail-illustration" aria-hidden>✉</div>
        <p style={{ margin: 0 }}>
          If <strong>{sentTo}</strong> belongs to an account, we've sent it a link to choose a new password. The link
          expires in 1 hour.
        </p>
        <EmailDeliveryHint />
        <p className="small muted" style={{ margin: 0 }}>
          Remembered it? <Link to="/login" state={{ email: sentTo }}>Back to sign in</Link>
        </p>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Reset your password">
      <form className="stack" onSubmit={submit}>
        <p className="muted" style={{ margin: 0 }}>Enter the email you signed up with and we'll send you a reset link.</p>
        {error && <div className="banner error" role="alert">{error.message}</div>}
        <label className="field">
          Email
          <input className="input" type="email" autoComplete="email" required value={email}
            onChange={(event) => setEmail(event.target.value)} />
          {error?.fieldErrors?.email && <span className="field-error">{error.fieldErrors.email}</span>}
        </label>
        <button className="btn btn-primary btn-lg" disabled={busy || !email.trim()}>
          {busy && <span className="spinner" />}Send reset link
        </button>
        <p className="small muted center" style={{ margin: 0 }}><Link to="/login">Back to sign in</Link></p>
      </form>
    </AuthCard>
  );
}
