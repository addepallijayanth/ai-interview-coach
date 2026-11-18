interface Props {
  skills: string[];
  tone?: 'good' | 'weak';
  empty: string;
}

export function SkillChips({ skills, tone, empty }: Props) {
  if (skills.length === 0) return <p className="muted small">{empty}</p>;
  return (
    <div className="chips">
      {skills.map((skill) => (
        <span key={skill} className={`chip ${tone ?? ''}`}>
          {tone === 'good' && <span aria-hidden>✓</span>}
          {tone === 'weak' && <span aria-hidden>+</span>}
          {skill}
        </span>
      ))}
    </div>
  );
}
