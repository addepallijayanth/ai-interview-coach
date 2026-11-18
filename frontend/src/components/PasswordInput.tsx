import { useState } from 'react';
import { passwordChecks } from '../lib/account';

interface Props {
  id: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: 'current-password' | 'new-password';
  invalid?: boolean;
  describedBy?: string;
}

/** A password field with a show/hide toggle. */
export function PasswordInput({ id, value, onChange, autoComplete, invalid, describedBy }: Props) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="input-group">
      <input id={id} className="input" type={visible ? 'text' : 'password'} value={value} maxLength={72}
        autoComplete={autoComplete} aria-invalid={invalid || undefined} aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.value)} />
      <button type="button" className="input-addon btn-ghost" aria-pressed={visible}
        aria-label={visible ? 'Hide password' : 'Show password'} onClick={() => setVisible((shown) => !shown)}>
        {visible ? 'Hide' : 'Show'}
      </button>
    </div>
  );
}

/** Live checklist under a new password. */
export function PasswordRules({ id, password }: { id: string; password: string }) {
  const checks = passwordChecks(password);
  const rules: [boolean, string][] = [
    [checks.length, '8 or more characters'],
    [checks.letter, 'a letter'],
    [checks.number, 'a number'],
  ];
  return (
    <ul id={id} className="rules" aria-label="Password requirements">
      {rules.map(([met, text]) => <li key={text} className={met ? 'met' : ''}>{text}</li>)}
    </ul>
  );
}
