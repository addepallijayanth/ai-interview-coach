import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, asApiError } from '../api/client';
import type { ResumeResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { FileDropzone } from '../components/FileDropzone';
import { describeRedactions, formatNumber } from '../lib/format';
import { useMeta } from '../lib/useMeta';

const ANALYSIS_STEPS = [
  'Extracting your skills and experience',
  'Reading the job requirements',
  'Comparing you with the role',
  'Writing resume suggestions',
];
const MIN_JD_CHARS = 50;

export function UploadPage() {
  const meta = useMeta();
  const navigate = useNavigate();
  const [resume, setResume] = useState<ResumeResponse>();
  const [uploading, setUploading] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [jobText, setJobText] = useState('');
  const [analyzing, setAnalyzing] = useState(false);
  const [step, setStep] = useState(0);
  const [error, setError] = useState<ApiError>();

  // Analysis takes 10-30 seconds; move through the stages so the wait feels informative.
  useEffect(() => {
    if (!analyzing) return;
    setStep(0);
    const timer = setInterval(() => setStep((current) => Math.min(current + 1, ANALYSIS_STEPS.length - 1)), 5000);
    return () => clearInterval(timer);
  }, [analyzing]);

  async function upload(file: File) {
    setError(undefined);
    if (file.size > (meta?.maxUploadMb ?? 2) * 1024 * 1024) {
      setError(new ApiError(413, 'Resumes must be 2 MB or smaller.'));
      return;
    }
    setUploading(true);
    try {
      setResume(await api.uploadResume(file));
      setShowPreview(false);
    } catch (e) {
      setError(asApiError(e));
    } finally {
      setUploading(false);
    }
  }

  async function analyze() {
    if (!resume) return;
    setError(undefined);
    setAnalyzing(true);
    try {
      const job = await api.createJobDescription({
        title: title.trim() || undefined,
        company: company.trim() || undefined,
        text: jobText,
      });
      const analysis = await api.analyze(resume.id, job.id);
      navigate(`/analyses/${analysis.id}`);
    } catch (e) {
      setError(asApiError(e));
      setAnalyzing(false);
    }
  }

  const jobLength = jobText.trim().length;
  const canAnalyze = !!resume && jobLength >= MIN_JD_CHARS && !uploading && !analyzing;
  const redactions = resume ? describeRedactions(resume.redactions) : '';

  if (analyzing) {
    return (
      <div className="card stack" style={{ maxWidth: 560, margin: '6vh auto 0' }} aria-live="polite">
        <h2>Analysing your fit for the role…</h2>
        <ol className="progress-steps">
          {ANALYSIS_STEPS.map((label, index) => (
            <li key={label} className={index < step ? 'done' : index === step ? 'current' : ''}>
              {index < step ? '✓' : index === step ? <span className="spinner" /> : '○'} {label}
            </li>
          ))}
        </ol>
        <p className="small muted" style={{ margin: 0 }}>This usually takes 10 to 30 seconds.</p>
      </div>
    );
  }

  return (
    <div className="stack">
      <div className="page-header">
        <h1>New analysis</h1>
        <p>Compare your resume with a job description, then practise with a mock interview.</p>
      </div>

      {meta && !meta.aiConfigured && (
        <div className="banner warning">
          AI is not configured on this server (ANTHROPIC_API_KEY is missing), so analyses will fail. Uploads still work.
        </div>
      )}
      {error && <ErrorBanner error={error} />}

      <div className="grid-2">
        <section className="card stack">
          <h2><span className="card-step">1</span>Your resume</h2>
          {resume ? (
            <>
              <div className="file-pill">
                <span aria-hidden>📄</span>
                <div className="grow">
                  <div className="name">{resume.fileName}</div>
                  <div className="small">
                    {formatNumber(resume.characters)} characters read
                    {redactions ? ` · masked ${redactions}` : ''}
                  </div>
                </div>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setResume(undefined)}>Change</button>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" style={{ alignSelf: 'flex-start' }}
                onClick={() => setShowPreview((shown) => !shown)}>
                {showPreview ? 'Hide' : 'Preview'} what the AI will see
              </button>
              {showPreview && <pre className="redacted-preview">{resume.redactedText}</pre>}
            </>
          ) : uploading ? (
            <div className="dropzone"><span className="spinner" /> Reading your resume…</div>
          ) : (
            <FileDropzone onFile={upload} />
          )}
          <p className="small muted" style={{ margin: 0 }}>
            Email addresses, phone numbers, profile links and street addresses are masked before anything is stored or
            sent to the AI. The original file is not kept.
          </p>
        </section>

        <section className="card stack">
          <h2><span className="card-step">2</span>The job</h2>
          <div className="grid-2" style={{ gap: 12 }}>
            <label className="field">Job title <span className="field-hint">optional</span>
              <input className="input" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)}
                placeholder="Backend Engineer" />
            </label>
            <label className="field">Company <span className="field-hint">optional</span>
              <input className="input" value={company} maxLength={200} onChange={(e) => setCompany(e.target.value)}
                placeholder="Globex" />
            </label>
          </div>
          <label className="field">
            Job description
            <textarea className="textarea" rows={9} value={jobText} maxLength={20000}
              onChange={(e) => setJobText(e.target.value)}
              placeholder="Paste the full job description: responsibilities, requirements, nice-to-haves…" />
            <span className="field-hint">
              {jobLength < MIN_JD_CHARS ? `At least ${MIN_JD_CHARS} characters (${jobLength} so far)` : `${formatNumber(jobLength)} characters`}
            </span>
          </label>
        </section>
      </div>

      <div className="spread">
        <span className="small muted">
          {meta ? `Extraction runs on ${meta.fastModel}; scoring and rewrites on ${meta.smartModel}.` : ''}
        </span>
        <button type="button" className="btn btn-primary btn-lg" disabled={!canAnalyze} onClick={analyze}>
          Analyse my match
        </button>
      </div>
    </div>
  );
}
