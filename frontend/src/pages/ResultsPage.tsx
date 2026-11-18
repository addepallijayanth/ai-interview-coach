import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import type { AnalysisResponse, InterviewLevel, Suggestion } from '../api/types';
import { ErrorBanner, LoadingCard } from '../components/ErrorBanner';
import { ScoreGauge } from '../components/ScoreGauge';
import { SkillChips } from '../components/SkillChips';
import { formatDate, formatNumber, formatUsd, roleLabel } from '../lib/format';
import { useMeta } from '../lib/useMeta';

function headline(score: number): string {
  if (score >= 85) return 'Strong match';
  if (score >= 65) return 'Good match, with a few gaps';
  if (score >= 40) return 'Partial match';
  return 'Not a close match yet';
}

export function ResultsPage() {
  const { id } = useParams();
  const meta = useMeta();
  const navigate = useNavigate();
  const [analysis, setAnalysis] = useState<AnalysisResponse>();
  const [error, setError] = useState<ApiError>();
  const [level, setLevel] = useState<InterviewLevel>('STANDARD');
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    api.analysis(Number(id)).then(setAnalysis).catch((e) => setError(asApiError(e)));
  }, [id]);

  if (error && !analysis) return <ErrorBanner error={error} />;
  if (!analysis) return <LoadingCard text="Loading your analysis…" />;

  const { match, candidate } = analysis;

  async function startInterview() {
    setStarting(true);
    setError(undefined);
    try {
      const session = await api.startInterview(analysis!.id, level);
      navigate(`/interviews/${session.id}`);
    } catch (e) {
      setError(asApiError(e));
      setStarting(false);
    }
  }

  return (
    <div className="stack">
      <div className="page-header">
        <h1>{roleLabel(analysis.jobTitle, analysis.company)}</h1>
        <p>Analysed {formatDate(analysis.createdAt)} · {candidate.yearsExperience} years of experience · {candidate.seniority} level</p>
      </div>

      <section className="card score-hero">
        <ScoreGauge score={analysis.matchScore} />
        <div>
          <h2>{headline(analysis.matchScore)}</h2>
          <p>{match.summary}</p>
          {match.strengths.length > 0 && (
            <ul>{match.strengths.map((strength) => <li key={strength}>{strength}</li>)}</ul>
          )}
        </div>
      </section>

      <div className="grid-2">
        <section className="card">
          <h2>Skills you match</h2>
          <SkillChips skills={match.matchedSkills} tone="good" empty="No direct skill matches were found." />
        </section>
        <section className="card">
          <h2>Skills to build</h2>
          <SkillChips skills={match.missingSkills} tone="weak" empty="No gaps found. Nice." />
        </section>
      </div>

      <section className="card stack">
        <div>
          <h2>Resume improvements</h2>
          <p className="muted" style={{ margin: 0 }}>Rewrites of your own bullets, aimed at this job. Fill in any [X] placeholders with real numbers.</p>
        </div>
        {match.suggestions.length === 0
          ? <p className="muted">No rewrites suggested.</p>
          : match.suggestions.map((suggestion) => <SuggestionCard key={suggestion.original} suggestion={suggestion} />)}
      </section>

      <section className="card stack">
        <div>
          <h2>Practise with a mock interview</h2>
          <p className="muted" style={{ margin: 0 }}>
            A realistic interview for this role, like a real hiring loop: it opens with an introduction, digs into
            your projects, mixes technical, problem-solving, system design, behavioural, situational and HR questions,
            focuses on your skill gaps and scores every answer.
          </p>
        </div>
        {error && <ErrorBanner error={error} />}
        <div className="level-grid" role="radiogroup" aria-label="Interview level">
          {(meta?.interviewLevels ?? []).map((option) => (
            <button key={option.id} type="button" role="radio" aria-checked={option.id === level}
              className={`level-card${option.id === level ? ' selected' : ''}`} onClick={() => setLevel(option.id)}>
              <span className="spread">
                <strong>{option.label}</strong>
                <span className="badge neutral">{option.questions} questions</span>
              </span>
              <span className="small muted">{option.description}</span>
              <span className="small muted">About {Math.round(option.questions * 2.5)} minutes</span>
              <span className="mix">
                {option.breakdown.map((part) => (
                  <span key={part.category} className="mix-item">{part.label} <b>{part.count}</b></span>
                ))}
              </span>
            </button>
          ))}
          {!meta && <LoadingCard text="Loading interview levels…" />}
        </div>
        <div className="row">
          <button type="button" className="btn btn-primary btn-lg" disabled={starting || !meta} onClick={startInterview}>
            {starting && <span className="spinner" />}Start {meta?.interviewLevels.find((option) => option.id === level)?.label ?? ''} interview
          </button>
          <span className="small muted">You can end early at any time and still get a report.</span>
        </div>
      </section>

      <p className="small muted">
        {analysis.modelUsed} · {formatNumber(analysis.tokensIn + analysis.tokensOut)} tokens · {formatUsd(analysis.costUsd)}
      </p>
    </div>
  );
}

function SuggestionCard({ suggestion }: { suggestion: Suggestion }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    await navigator.clipboard.writeText(suggestion.improved);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }
  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="suggestion">
        <div className="before"><span className="tag">Current</span>{suggestion.original}</div>
        <div className="after"><span className="tag">Suggested</span>{suggestion.improved}</div>
      </div>
      <div className="spread">
        <span className="small muted">{suggestion.reason}</span>
        <button type="button" className="btn btn-ghost btn-sm" onClick={copy}>{copied ? 'Copied' : 'Copy suggestion'}</button>
      </div>
    </div>
  );
}
