import type { ReactNode } from 'react';

interface Props {
  role: 'interviewer' | 'candidate';
  label: string;
  /** Small pill next to the label, e.g. the question category. */
  tag?: string;
  draft?: boolean;
  children: ReactNode;
}

export function ChatMessage({ role, label, tag, draft, children }: Props) {
  return (
    <div className={`message ${role}${draft ? ' draft' : ''}`}>
      <div className="avatar" aria-hidden>{role === 'interviewer' ? 'AI' : 'You'}</div>
      <div>
        <div className="meta">{label}{tag && <span className="category-tag">{tag}</span>}</div>
        <div className="bubble">{children}</div>
      </div>
    </div>
  );
}
