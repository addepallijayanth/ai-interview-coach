import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import type { AnalysisSummary, InterviewSummary } from '../api/types';
import { ErrorBanner, LoadingCard } from '../components/ErrorBanner';
import { formatDate, roleLabel, scoreTone } from '../lib/format';

export function HistoryPage() {
  const [analyses, setAnalyses] = useState<AnalysisSummary[]>();
  const [interviews, setInterviews] = useState<InterviewSummary[]>();
  const [error, setError] = useState<ApiError>();

  useEffect(() => {
    Promise.all([api.analyses(), api.interviews()])
      .then(([loadedAnalyses, loadedInterviews]) => {
        setAnalyses(loadedAnalyses);
        setInterviews(loadedInterviews);
      })
      .catch((e) => setError(asApiError(e)));
  }, []);

  if (error) return <ErrorBanner error={error} />;
  if (!analyses || !interviews) return <LoadingCard text="Loading your history…" />;

  return (
    <div className="stack">
      <div className="page-header">
        <h1>History</h1>
        <p>Your recent analyses and mock interviews.</p>
      </div>
      <div className="grid-2">
        <section className="card">
          <h2>Analyses</h2>
          {analyses.length === 0 ? (
            <p className="empty">No analyses yet. <Link to="/">Start one</Link></p>
          ) : (
            <ul className="list">
              {analyses.map((analysis) => (
                <li key={analysis.id}>
                  <span className={`badge ${scoreTone(analysis.matchScore)}`}>{analysis.matchScore}</span>
                  <div className="grow">
                    <Link to={`/analyses/${analysis.id}`}>{roleLabel(analysis.jobTitle, analysis.company)}</Link>
                    <div className="small muted">{formatDate(analysis.createdAt)}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="card">
          <h2>Interviews</h2>
          {interviews.length === 0 ? (
            <p className="empty">No interviews yet. Start one from an analysis.</p>
          ) : (
            <ul className="list">
              {interviews.map((interview) => (
                <li key={interview.id}>
                  <span className={`badge ${interview.overallScore === undefined ? 'neutral' : scoreTone(interview.overallScore, 10)}`}>
                    {interview.overallScore === undefined ? '–' : interview.overallScore}
                  </span>
                  <div className="grow">
                    <Link to={interview.status === 'ENDED' && interview.questionsAnswered > 0 ? `/reports/${interview.id}` : `/interviews/${interview.id}`}>
                      {interview.jobTitle}
                    </Link>
                    <div className="small muted">
                      {interview.levelLabel} · {interview.status === 'ACTIVE' ? 'In progress' : 'Finished'} · {interview.questionsAnswered} of {interview.questionTarget} answered · {formatDate(interview.createdAt)}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
