import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthCard } from '../components/AuthShell';
import { PasswordInput, PasswordRules } from '../components/PasswordInput';
import { passwordProblem } from '../lib/account';

/** The page behind the link in the password-reset email. */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const { acceptSession } = useAuth();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [problem, setProblem] = useState<string>();
  const [error, setError] = useState<ApiError>();
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(undefined);
    const invalid = passwordProblem(password) ?? (confirm !== password ? 'The passwords do not match.' : undefined);
    setProblem(invalid);
    if (invalid) return;
    setBusy(true);
    try {
      acceptSession(await api.resetPassword(token, password));
      navigate('/', { replace: true });
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setBusy(false);
    }
  }

  const linkProblem = !token || ['invalid_link', 'link_used', 'link_expired'].includes(error?.code ?? '');
  if (linkProblem) {
    return (
      <AuthCard title="This link doesn't work">
        <div className="banner error" role="alert">
          {error?.message ?? 'This link is incomplete. Open the link from the email again.'}
        </div>
        <Link className="btn btn-primary" to="/forgot-password">Request a new reset link</Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Choose a new password">
      <form className="stack" onSubmit={submit} noValidate>
        {error && <div className="banner error" role="alert">{error.message}</div>}
        <div className="field">
          <label htmlFor="reset-password">New password</label>
          <PasswordInput id="reset-password" value={password} onChange={setPassword} autoComplete="new-password"
            describedBy="reset-password-rules" invalid={!!problem} />
          <PasswordRules id="reset-password-rules" password={password} />
        </div>
        <div className="field">
          <label htmlFor="reset-confirm">Confirm new password</label>
          <PasswordInput id="reset-confirm" value={confirm} onChange={setConfirm} autoComplete="new-password"
            invalid={!!problem} />
          {(problem ?? error?.fieldErrors?.password) && (
            <span className="field-error" role="alert">{problem ?? error?.fieldErrors?.password}</span>
          )}
        </div>
        <button className="btn btn-primary btn-lg" disabled={busy || !password}>
          {busy && <span className="spinner" />}Save password and sign in
        </button>
      </form>
    </AuthCard>
  );
}
