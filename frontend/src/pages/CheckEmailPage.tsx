import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import { AuthCard } from '../components/AuthShell';
import { EmailDeliveryHint } from '../components/EmailDeliveryHint';

const COOLDOWN_SECONDS = 60;

/** After sign-up: "check your inbox", with a rate-limited "resend" button. */
export function CheckEmailPage() {
  const location = useLocation();
  const state = location.state as { email?: string; resent?: boolean } | null;
  const [email, setEmail] = useState(state?.email ?? '');
  const [cooldown, setCooldown] = useState(state?.resent || state?.email ? COOLDOWN_SECONDS : 0);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(!!state?.resent);
  const [error, setError] = useState<ApiError>();

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((seconds) => seconds - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  async function resend() {
    setSending(true);
    setError(undefined);
    try {
      await api.resendVerification(email.trim());
      setSent(true);
      setCooldown(COOLDOWN_SECONDS);
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setSending(false);
    }
  }

  return (
    <AuthCard title="Check your inbox">
      <div className="mail-illustration" aria-hidden>✉</div>
      {state?.email ? (
        <p style={{ margin: 0 }}>
          We sent a confirmation link to <strong>{state.email}</strong>. Click it to activate your account. The link
          expires in 24 hours.
        </p>
      ) : (
        <p style={{ margin: 0 }}>Enter the email you signed up with and we'll send you a new confirmation link.</p>
      )}
      <EmailDeliveryHint />
      {sent && <div className="banner info" role="status">If that address still needs confirming, a new link is on its way.</div>}
      {error && <div className="banner error" role="alert">{error.message}</div>}
      {!state?.email && (
        <label className="field">
          Email
          <input className="input" type="email" autoComplete="email" value={email}
            onChange={(event) => setEmail(event.target.value)} />
        </label>
      )}
      <div className="spread">
        <span className="small muted">Didn't get it? Check spam, or</span>
        <button type="button" className="btn btn-secondary" disabled={sending || cooldown > 0 || !email.trim()}
          onClick={resend}>
          {sending && <span className="spinner" />}
          {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend link'}
        </button>
      </div>
      <p className="small muted" style={{ margin: 0 }}>
        Already confirmed? <Link to="/login" state={{ email: email.trim() }}>Sign in</Link>
      </p>
    </AuthCard>
  );
}
