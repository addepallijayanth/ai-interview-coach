import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import type { ReportResponse } from '../api/types';
import { ErrorBanner, LoadingCard } from '../components/ErrorBanner';
import { FeedbackCard } from '../components/FeedbackCard';
import { SkillChips } from '../components/SkillChips';
import { formatDate, formatUsd, roleLabel, scoreTone } from '../lib/format';

export function ReportPage() {
  const { id } = useParams();
  const sessionId = Number(id);
  const [report, setReport] = useState<ReportResponse>();
  const [error, setError] = useState<ApiError>();
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    api.report(sessionId).then(setReport).catch((e) => setError(asApiError(e)));
  }, [sessionId]);

  async function download() {
    setDownloading(true);
    try {
      await api.downloadReport(sessionId);
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setDownloading(false);
    }
  }

  if (error?.status === 409) {
    return (
      <div className="banner info">
        This interview is still in progress. <Link to={`/interviews/${sessionId}`}>Go back to the interview</Link>
      </div>
    );
  }
  if (error && !report) return <ErrorBanner error={error} />;
  if (!report) return <LoadingCard text="Building your report and study plan…" />;

  const interviewTone = report.overallScore === undefined ? 'neutral' : scoreTone(report.overallScore, 10);
  return (
    <div className="stack">
      <div className="page-header spread">
        <div>
          <h1>Interview report</h1>
          <p>
            {roleLabel(report.jobTitle, report.company)} · {report.levelLabel} interview
            {report.endedAt ? ` · ${formatDate(report.endedAt)}` : ''}
          </p>
        </div>
        <button type="button" className="btn btn-primary" disabled={downloading} onClick={download}>
          {downloading && <span className="spinner" />}Download PDF
        </button>
      </div>
      {error && <ErrorBanner error={error} />}

      <div className="grid-3">
        <div className="card stat">
          <div className={`number badge ${scoreTone(report.matchScore)}`} style={{ fontSize: '1.6rem' }}>{report.matchScore}/100</div>
          <div className="label">Resume match</div>
        </div>
        <div className="card stat">
          <div className={`number badge ${interviewTone}`} style={{ fontSize: '1.6rem' }}>
            {report.overallScore === undefined ? '–' : `${report.overallScore}/10`}
          </div>
          <div className="label">Interview score</div>
        </div>
        <div className="card stat">
          <div className="number">{report.questionsAnswered}<span className="muted" style={{ fontSize: '1rem' }}> / {report.questionTarget}</span></div>
          <div className="label">Questions answered</div>
        </div>
      </div>

      {report.summary && (
        <section className="card">
          <h2>Interviewer summary</h2>
          <p style={{ margin: 0 }}>{report.summary}</p>
        </section>
      )}

      {report.categoryScores.length > 0 && (
        <section className="card">
          <h2>Score by category</h2>
          <div className="category-scores">
            {report.categoryScores.map((category) => {
              const tone = category.averageScore === undefined ? 'neutral' : scoreTone(category.averageScore, 10);
              return (
                <div key={category.category} className="category-score">
                  <span className="name">{category.label}</span>
                  <div className="bar" aria-hidden>
                    <div className={tone} style={{ width: `${(category.averageScore ?? 0) * 10}%` }} />
                  </div>
                  <span className={`badge ${tone}`}>
                    {category.averageScore === undefined ? '–' : `${category.averageScore}/10`}
                  </span>
                  <span className="small muted count">{category.scored} of {category.asked} scored</span>
                </div>
              );
            })}
          </div>
        </section>
      )}

      <section className="card">
        <h2>Skills to develop</h2>
        <SkillChips skills={report.missingSkills} tone="weak" empty="Your resume covered every skill the job asked for." />
      </section>

      <section className="card stack">
        <h2>What to study next</h2>
        {report.studyPlan ? (
          <>
            <p style={{ margin: 0 }}>{report.studyPlan.summary}</p>
            {report.studyPlan.focusAreas.map((area) => (
              <div key={area.topic} className="focus-area">
                <h3>
                  {area.topic}
                  <span className={`badge ${area.priority === 'high' ? 'weak' : area.priority === 'medium' ? 'ok' : 'neutral'}`}>
                    {area.priority} priority
                  </span>
                </h3>
                <p className="muted" style={{ margin: 0 }}>{area.why}</p>
                <ul>{area.actions.map((action) => <li key={action}>{action}</li>)}</ul>
                {area.resources.length > 0 && <p className="small muted" style={{ margin: 0 }}>Resources: {area.resources.join(' · ')}</p>}
              </div>
            ))}
          </>
        ) : (
          <p className="muted">The study plan could not be generated right now. Reload the page to try again.</p>
        )}
      </section>

      <section className="card">
        <h2>Question by question</h2>
        {report.turns.length === 0 && <p className="muted">No answers were given.</p>}
        {report.turns.map((turn) => (
          <div key={turn.questionNo} className="turn-row stack" style={{ gap: 8 }}>
            {turn.categoryLabel && <span className="category-tag" style={{ marginLeft: 0 }}>{turn.categoryLabel}</span>}
            <strong>Q{turn.questionNo}. {turn.question}</strong>
            {turn.answer && <p className="muted" style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{turn.answer}</p>}
            {turn.score !== undefined && <FeedbackCard score={turn.score} feedback={turn.feedback} />}
          </div>
        ))}
      </section>

      <p className="small muted">Total AI cost for this analysis and interview: {formatUsd(report.aiCostUsd)}</p>
    </div>
  );
}
