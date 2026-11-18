import { Fragment, useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, asApiError, type ApiError } from '../api/client';
import { streamInterview } from '../api/sse';
import type { InterviewEvent, InterviewResponse, Turn } from '../api/types';
import { ChatMessage } from '../components/ChatMessage';
import { ErrorBanner, LoadingCard } from '../components/ErrorBanner';
import { FeedbackCard } from '../components/FeedbackCard';
import { roleLabel, scoreTone } from '../lib/format';
import { useMeta } from '../lib/useMeta';

interface Ended {
  overallScore?: number;
  summary?: string;
  questionsAnswered: number;
}

interface StreamError {
  message: string;
  retryable: boolean;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export function InterviewPage() {
  const { id } = useParams();
  const sessionId = Number(id);
  const meta = useMeta();
  const [session, setSession] = useState<InterviewResponse>();
  const [loadError, setLoadError] = useState<ApiError>();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [draft, setDraft] = useState('');
  const [status, setStatus] = useState<string>();
  const [streaming, setStreaming] = useState(false);
  const [ended, setEnded] = useState<Ended>();
  const [answer, setAnswer] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<StreamError>();
  const abortRef = useRef<AbortController | null>(null);
  const pendingAnswerRef = useRef(''); // the submitted answer until its feedback arrives
  const gotDoneRef = useRef(false);

  const handleEvent = useCallback((event: InterviewEvent) => {
    switch (event.type) {
      case 'status':
        setStatus(event.message);
        break;
      case 'token':
        setStatus(undefined);
        setDraft((current) => current + event.text);
        break;
      case 'draft_reset':
        setDraft('');
        break;
      case 'feedback':
        pendingAnswerRef.current = '';
        setTurns((current) => current.map((turn) => turn.questionNo === event.questionNo
          ? { ...turn, score: event.score, feedback: { strengths: event.strengths, weaknesses: event.weaknesses, modelAnswer: event.modelAnswer } }
          : turn));
        break;
      case 'question':
        setDraft('');
        setStatus(undefined);
        setTurns((current) => current.some((turn) => turn.questionNo === event.questionNo)
          ? current
          : [...current, {
            questionNo: event.questionNo, question: event.question, category: event.category,
            categoryLabel: event.categoryLabel,
          }]);
        break;
      case 'ended':
        setDraft('');
        setStatus(undefined);
        setEnded({ overallScore: event.overallScore, summary: event.summary, questionsAnswered: event.questionsAnswered });
        break;
      case 'error':
        setDraft('');
        setStatus(undefined);
        if (event.code === 'ai_refused' && pendingAnswerRef.current) {
          // The server re-opened the question: give the answer back so it can be rephrased.
          const text = pendingAnswerRef.current;
          pendingAnswerRef.current = '';
          setTurns((current) => current.map((turn, index) =>
            index === current.length - 1 && turn.score === undefined ? { ...turn, answer: undefined } : turn));
          setAnswer(text);
        }
        setError({ message: event.message, retryable: event.retryable });
        break;
      case 'done':
        gotDoneRef.current = true;
        break;
    }
  }, []);

  /** Replaces local turns with the server's copy: the database is the source of truth. */
  const resync = useCallback(async () => {
    const loaded = await api.interview(sessionId);
    setSession(loaded);
    setTurns(loaded.turns);
    if (loaded.status === 'ENDED') {
      setEnded({ overallScore: loaded.overallScore, summary: loaded.summary, questionsAnswered: loaded.questionsAnswered });
    }
  }, [sessionId]);

  const openStream = useCallback(async () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    gotDoneRef.current = false;
    setStreaming(true);
    setError(undefined);
    setStatus('The interviewer is thinking…');
    try {
      for (let attempt = 0; ; attempt++) {
        try {
          await streamInterview(sessionId, handleEvent, controller.signal);
          if (controller.signal.aborted) return;
          if (!gotDoneRef.current) {
            setError({ message: 'The connection dropped before the interviewer finished.', retryable: true });
          }
          // Events sent while this page was disconnected (e.g. feedback during a reload) are not replayed,
          // so pick them up from the stored interview.
          await resync().catch(() => undefined);
          return;
        } catch (e) {
          if (controller.signal.aborted) return;
          const apiError = asApiError(e);
          // 409: an earlier run (another tab, or a reload mid-answer) is still finishing. Wait, then re-attach.
          if (apiError.status === 409 && attempt < 10) {
            await sleep(1500);
            continue;
          }
          setError({ message: apiError.message, retryable: apiError.status !== 404 });
          return;
        }
      }
    } finally {
      if (abortRef.current === controller) {
        setStreaming(false);
        setStatus(undefined);
      }
    }
  }, [sessionId, handleEvent, resync]);

  useEffect(() => {
    let cancelled = false;
    api.interview(sessionId)
      .then((loaded) => {
        if (cancelled) return;
        setSession(loaded);
        setTurns(loaded.turns);
        if (loaded.status === 'ENDED') {
          setEnded({ overallScore: loaded.overallScore, summary: loaded.summary, questionsAnswered: loaded.questionsAnswered });
        } else {
          void openStream(); // asks the next question, or replays the open one
        }
      })
      .catch((e) => !cancelled && setLoadError(asApiError(e)));
    return () => {
      cancelled = true;
      abortRef.current?.abort();
    };
  }, [sessionId, openStream]);

  // Scroll the page (not just the chat) to the bottom, so the sticky answer box never covers the newest message.
  useEffect(() => {
    window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'smooth' });
  }, [turns, draft, status, ended, error]);

  const lastTurn = turns.at(-1);
  const openQuestion = lastTurn && lastTurn.answer === undefined && !ended ? lastTurn : undefined;
  const answered = turns.filter((turn) => turn.score !== undefined).length;
  const target = session?.questionTarget ?? 0;
  const maxChars = meta?.maxAnswerChars ?? 4000;
  const canSend = !!openQuestion && !streaming && !submitting && answer.trim().length > 0;

  async function submit() {
    if (!openQuestion || !canSend) return;
    const text = answer.trim();
    setSubmitting(true);
    setError(undefined);
    try {
      await api.answer(sessionId, text);
      pendingAnswerRef.current = text;
      setTurns((current) => current.map((turn) => turn.questionNo === openQuestion.questionNo ? { ...turn, answer: text } : turn));
      setAnswer('');
      void openStream();
    } catch (e) {
      setError({ message: asApiError(e).message, retryable: false });
    } finally {
      setSubmitting(false);
    }
  }

  async function endInterview() {
    if (!window.confirm('End the interview now? You will get a summary of the answers you have given.')) return;
    setError(undefined);
    try {
      const updated = await api.endInterview(sessionId);
      if (updated.status === 'ENDED') {
        setEnded({ overallScore: updated.overallScore, summary: updated.summary, questionsAnswered: updated.questionsAnswered });
      } else {
        void openStream(); // the interviewer writes a closing summary
      }
    } catch (e) {
      setError({ message: asApiError(e).message, retryable: false });
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      void submit();
    }
  }

  if (loadError) return <ErrorBanner error={loadError} />;
  if (!session) return <LoadingCard text="Loading your interview…" />;

  return (
    <div>
      <div className="interview-header">
        <div className="spread">
          <div>
            <h1 style={{ marginBottom: 2 }}>{session.levelLabel} mock interview</h1>
            <div className="muted">{roleLabel(session.jobTitle, session.company)} · {target} questions</div>
          </div>
          {!ended && (
            <button type="button" className="btn btn-danger" disabled={streaming || submitting} onClick={endInterview}>
              End interview
            </button>
          )}
        </div>
        <div className="small muted" style={{ marginTop: 10 }}>{answered} of {target} questions answered</div>
        <div className="progress-bar" aria-hidden>
          <div style={{ width: `${target ? (answered / target) * 100 : 0}%` }} />
        </div>
      </div>

      <div className="chat" aria-live="polite">
        {turns.map((turn) => (
          <Fragment key={turn.questionNo}>
            <ChatMessage role="interviewer" label={`Question ${turn.questionNo} of ${target}`} tag={turn.categoryLabel}>
              {turn.question}
            </ChatMessage>
            {turn.answer !== undefined && <ChatMessage role="candidate" label="Your answer">{turn.answer}</ChatMessage>}
            {turn.score !== undefined && <FeedbackCard score={turn.score} feedback={turn.feedback} />}
          </Fragment>
        ))}
        {draft && <ChatMessage role="interviewer" label="Coach is typing…" draft>{draft}</ChatMessage>}
        {status && !draft && (
          <div className="row muted small" style={{ marginLeft: 42 }}>
            <span className="typing" aria-hidden><span /><span /><span /></span>
            {status}
          </div>
        )}
        {error && (
          <div style={{ marginLeft: 42 }}>
            <ErrorBanner error={new Error(error.message)} onRetry={error.retryable ? () => void openStream() : undefined} />
          </div>
        )}
        {ended && <EndedCard sessionId={sessionId} ended={ended} target={target} />}
      </div>

      {!ended && (
        <div className="composer">
          <div className="card">
            <textarea
              className="textarea"
              value={answer}
              maxLength={maxChars}
              aria-label="Your answer"
              placeholder={openQuestion ? 'Type your answer. Press Ctrl+Enter to send.' : 'Waiting for the next question…'}
              onChange={(event) => setAnswer(event.target.value)}
              onKeyDown={onKeyDown}
            />
            <div className="spread">
              <span className="small muted">{answer.length}/{maxChars}</span>
              <button type="button" className="btn btn-primary" disabled={!canSend} onClick={() => void submit()}>
                {submitting && <span className="spinner" />}Send answer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function EndedCard({ sessionId, ended, target }: { sessionId: number; ended: Ended; target: number }) {
  const tone = ended.overallScore === undefined ? 'neutral' : scoreTone(ended.overallScore, 10);
  return (
    <div className="card stack" style={{ marginTop: 8 }}>
      <div className="row">
        <h2 style={{ margin: 0 }}>Interview complete</h2>
        {ended.overallScore !== undefined && <span className={`badge ${tone}`}>{ended.overallScore}/10 overall</span>}
      </div>
      {ended.summary && <p style={{ margin: 0 }}>{ended.summary}</p>}
      <p className="small muted" style={{ margin: 0 }}>{ended.questionsAnswered} of {target} questions answered.</p>
      {ended.questionsAnswered > 0
        ? <Link className="btn btn-primary" style={{ alignSelf: 'flex-start' }} to={`/reports/${sessionId}`}>See your report and study plan</Link>
        : <Link to="/">Start a new analysis</Link>}
    </div>
  );
}
