import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthShell } from '../components/AuthShell';
import { PasswordInput } from '../components/PasswordInput';

export function LoginPage() {
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as { from?: string; email?: string; notice?: string } | null;
  const [email, setEmail] = useState(state?.email ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<ApiError>();
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const from = state?.from ?? '/';

  if (user) return <Navigate to={from} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      await signIn(email.trim(), password);
      navigate(from, { replace: true });
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setResending(true);
    try {
      await api.resendVerification(email.trim());
      navigate('/check-email', { state: { email: email.trim(), resent: true } });
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setResending(false);
    }
  }

  const unverified = error?.code === 'email_not_verified';
  return (
    <AuthShell>
      <form className="card stack" onSubmit={submit}>
        <h2>Sign in</h2>
        {state?.notice && !error && <div className="banner info" role="status">{state.notice}</div>}
        {error && (
          <div className={`banner ${unverified ? 'warning' : 'error'}`} role="alert">
            <span>{error.message}</span>
            {unverified && (
              <button type="button" className="btn btn-secondary btn-sm" disabled={resending} onClick={resend}>
                {resending && <span className="spinner" />}Resend link
              </button>
            )}
          </div>
        )}
        <label className="field">
          Email
          <input className="input" type="email" autoComplete="email" required value={email}
            onChange={(event) => setEmail(event.target.value)} />
        </label>
        <div className="field">
          <div className="spread">
            <label htmlFor="login-password">Password</label>
            <Link className="small" to="/forgot-password" state={{ email: email.trim() }}>Forgot password?</Link>
          </div>
          <PasswordInput id="login-password" value={password} onChange={setPassword} autoComplete="current-password" />
        </div>
        <button className="btn btn-primary btn-lg" disabled={busy || !email || !password}>
          {busy && <span className="spinner" />}Sign in
        </button>
        <p className="small muted center" style={{ margin: 0 }}>
          New here? <Link to="/register" state={location.state}>Create an account</Link>
        </p>
      </form>
    </AuthShell>
  );
}
