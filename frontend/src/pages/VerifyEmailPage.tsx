import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import type { User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AuthCard } from '../components/AuthShell';

/** The page behind the link in the confirmation email: confirms the address and signs the user in. */
export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const { acceptSession } = useAuth();
  const navigate = useNavigate();
  const [confirmed, setConfirmed] = useState<User>();
  const [error, setError] = useState<ApiError>();
  const started = useRef(false); // links are single-use: never send the token twice (React StrictMode re-runs effects)

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (!token) {
      setError(asApiError(new Error('This link is incomplete. Open the link from the email again.')));
      return;
    }
    api.verifyEmail(token)
      .then((response) => {
        acceptSession(response);
        setConfirmed(response.user);
      })
      .catch((e) => setError(asApiError(e)));
  }, [token, acceptSession]);

  if (confirmed) {
    return (
      <AuthCard title="Email confirmed">
        <div className="mail-illustration good" aria-hidden>✓</div>
        <p style={{ margin: 0 }}>
          Thanks{confirmed.firstName ? `, ${confirmed.firstName}` : ''}! Your account is active and you're signed in.
        </p>
        <button type="button" className="btn btn-primary btn-lg" onClick={() => navigate('/', { replace: true })}>
          Start your first analysis
        </button>
      </AuthCard>
    );
  }

  if (error) {
    const used = error.code === 'link_used';
    return (
      <AuthCard title={used ? 'Already confirmed' : "This link doesn't work"}>
        <div className={`banner ${used ? 'info' : 'error'}`} role="alert">{error.message}</div>
        {used
          ? <Link className="btn btn-primary" to="/login">Sign in</Link>
          : <Link className="btn btn-primary" to="/check-email">Send a new confirmation link</Link>}
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Confirming your email…">
      <div className="row"><span className="spinner" /><span>Just a moment.</span></div>
    </AuthCard>
  );
}
