import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import type { Country } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AuthShell } from '../components/AuthShell';
import { PasswordInput, PasswordRules } from '../components/PasswordInput';
import { guessCountry, validateRegistration, type RegistrationForm } from '../lib/account';

const EMPTY: RegistrationForm = {
  firstName: '', lastName: '', email: '', countryCode: '', mobileNumber: '', password: '', confirmPassword: '',
};

export function RegisterPage() {
  const { user, signUp } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState<RegistrationForm>(EMPTY);
  const [countries, setCountries] = useState<Country[]>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<ApiError>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.countries()
      .then((list) => {
        setCountries(list);
        const guess = guessCountry(Intl.DateTimeFormat().resolvedOptions().timeZone, navigator.languages ?? [],
          new Set(list.map((country) => country.code)));
        setForm((current) => (current.countryCode ? current : { ...current, countryCode: guess }));
      })
      .catch((e) => setError(asApiError(e)));
  }, []);

  const country = useMemo(() => countries?.find((c) => c.code === form.countryCode), [countries, form.countryCode]);

  if (user) return <Navigate to="/" replace />;

  function update<K extends keyof RegistrationForm>(field: K, value: RegistrationForm[K]) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field]) return current;
      const { [field]: _removed, ...rest } = current;
      return rest;
    });
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(undefined);
    const problems = validateRegistration(form);
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      document.getElementById(`register-${Object.keys(problems)[0]}`)?.focus();
      return;
    }
    setBusy(true);
    try {
      const { confirmPassword: _confirm, ...request } = form;
      const registered = await signUp({
        ...request,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        mobileNumber: form.mobileNumber.trim(),
      });
      navigate('/check-email', { state: { email: registered.email } });
    } catch (e) {
      const apiError = asApiError(e);
      setError(apiError);
      if (apiError.fieldErrors) setErrors(apiError.fieldErrors);
    } finally {
      setBusy(false);
    }
  }

  const fieldError = (name: string) => errors[name];

  return (
    <AuthShell>
      <form className="card stack" onSubmit={submit} noValidate>
        <div>
          <h2 style={{ marginBottom: 4 }}>Create your account</h2>
          <p className="small muted" style={{ margin: 0 }}>We'll email you a link to confirm your address.</p>
        </div>
        {error && (
          <div className="banner error" role="alert">
            <span>
              {error.message}{' '}
              {error.status === 409 && error.code === 'email_not_verified' && (
                <Link to="/check-email" state={{ email: form.email.trim() }}>Send a new link</Link>
              )}
              {error.status === 409 && error.code !== 'email_not_verified' && (
                <Link to="/login" state={{ email: form.email.trim() }}>Sign in</Link>
              )}
            </span>
          </div>
        )}

        <div className="grid-2 tight">
          <Field id="register-firstName" label="First name" error={fieldError('firstName')}>
            <input id="register-firstName" className="input" autoComplete="given-name" value={form.firstName}
              maxLength={60} aria-invalid={!!fieldError('firstName') || undefined}
              onChange={(event) => update('firstName', event.target.value)} />
          </Field>
          <Field id="register-lastName" label="Last name" error={fieldError('lastName')}>
            <input id="register-lastName" className="input" autoComplete="family-name" value={form.lastName}
              maxLength={60} aria-invalid={!!fieldError('lastName') || undefined}
              onChange={(event) => update('lastName', event.target.value)} />
          </Field>
        </div>

        <Field id="register-email" label="Email address" error={fieldError('email')}>
          <input id="register-email" className="input" type="email" autoComplete="email" value={form.email}
            maxLength={255} aria-invalid={!!fieldError('email') || undefined}
            onChange={(event) => update('email', event.target.value)} />
        </Field>

        <Field id="register-countryCode" label="Country" error={fieldError('countryCode')}>
          <select id="register-countryCode" className="select" autoComplete="country" value={form.countryCode}
            disabled={!countries} aria-invalid={!!fieldError('countryCode') || undefined}
            onChange={(event) => update('countryCode', event.target.value)}>
            {!countries && <option value="">Loading countries…</option>}
            {countries && !form.countryCode && <option value="">Choose your country</option>}
            {countries?.map((c) => <option key={c.code} value={c.code}>{c.name} ({c.dialCode})</option>)}
          </select>
        </Field>

        <Field id="register-mobileNumber" label="Mobile number" error={fieldError('mobileNumber')}
          hint="Used for your account only. We never share it.">
          <div className="input-group">
            <span className="input-prefix" aria-hidden>{country?.dialCode ?? '+'}</span>
            <input id="register-mobileNumber" className="input" type="tel" inputMode="tel" autoComplete="tel-national"
              value={form.mobileNumber} maxLength={25} placeholder="Mobile number"
              aria-invalid={!!fieldError('mobileNumber') || undefined}
              aria-label={`Mobile number${country ? `, country code ${country.dialCode}` : ''}`}
              onChange={(event) => update('mobileNumber', event.target.value)} />
          </div>
        </Field>

        <Field id="register-password" label="Password" error={fieldError('password')}>
          <PasswordInput id="register-password" value={form.password} autoComplete="new-password"
            invalid={!!fieldError('password')} describedBy="register-password-rules"
            onChange={(value) => update('password', value)} />
          <PasswordRules id="register-password-rules" password={form.password} />
        </Field>

        <Field id="register-confirmPassword" label="Confirm password" error={fieldError('confirmPassword')}>
          <PasswordInput id="register-confirmPassword" value={form.confirmPassword} autoComplete="new-password"
            invalid={!!fieldError('confirmPassword')} onChange={(value) => update('confirmPassword', value)} />
        </Field>

        <button className="btn btn-primary btn-lg" disabled={busy}>
          {busy && <span className="spinner" />}Create account
        </button>
        <p className="small muted center" style={{ margin: 0 }}>
          Already have an account? <Link to="/login" state={location.state}>Sign in</Link>
        </p>
      </form>
    </AuthShell>
  );
}

function Field({ id, label, error, hint, children }: {
  id: string; label: string; error?: string; hint?: string; children: ReactNode;
}) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? <span className="field-error" role="alert">{error}</span> : hint && <span className="field-hint">{hint}</span>}
    </div>
  );
}
