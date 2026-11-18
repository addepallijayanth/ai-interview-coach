import type { ReactNode } from 'react';

/** Sign-in and sign-up layout: what the app does on the left, the form on the right. */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <section className="auth-pitch">
        <h1>Practise for the job you actually want.</h1>
        <p className="muted">
          Upload your resume, paste a job description, and get an honest match score, sharper resume bullets and a
          realistic mock interview that adapts to your answers.
        </p>
        <ul>
          <li><span>1</span><div><strong>Match score and skill gaps</strong><br />See how you stack up against the role, out of 100.</div></li>
          <li><span>2</span><div><strong>Resume rewrites</strong><br />Bullet-by-bullet suggestions tailored to the job.</div></li>
          <li><span>3</span><div><strong>Mock interviews at three levels</strong><br />Easy, Standard or Professional: 10, 20 or 30 technical, design, project, behavioural and HR questions, each scored with feedback.</div></li>
        </ul>
      </section>
      {children}
    </div>
  );
}

/** A narrow centred card for the email-link pages (check your inbox, confirm, reset). */
export function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="auth-single">
      <section className="card stack">
        <h1 style={{ fontSize: '1.5rem', margin: 0 }}>{title}</h1>
        {children}
      </section>
    </div>
  );
}
