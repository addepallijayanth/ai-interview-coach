import { useMeta } from '../lib/useMeta';

/** For local development: the server has no SMTP settings, so the link is in the backend log, not an inbox. */
export function EmailDeliveryHint() {
  const meta = useMeta();
  if (!meta || meta.emailEnabled) return null;
  return (
    <div className="banner warning" role="note">
      <span>
        <strong>Developer note:</strong> this server has no email settings (MAIL_HOST), so the email was not sent.
        Its link is printed in the backend console instead. With <code>docker compose</code>, open Mailpit at
        {' '}<a href="http://localhost:8025" target="_blank" rel="noreferrer">localhost:8025</a>.
      </span>
    </div>
  );
}
