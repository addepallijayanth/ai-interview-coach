import type {
  AnalysisResponse,
  AnalysisSummary,
  AuthResponse,
  Country,
  InterviewLevel,
  InterviewResponse,
  InterviewSummary,
  JobDescriptionResponse,
  MessageResponse,
  MetaResponse,
  Problem,
  RegisterRequest,
  RegistrationResponse,
  ReportResponse,
  ResumeResponse,
  Turn,
  UsageResponse,
  User,
} from './types';

/** Empty in development (Vite proxies /api); the backend URL when the frontend is hosted separately. */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

const TOKEN_KEY = 'coach.token';

export const tokenStore = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code?: string,
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function asApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  return new ApiError(0, error instanceof Error ? error.message : 'Something went wrong.');
}

let unauthorizedHandler: (() => void) | undefined;

/** Called when a signed-in request comes back 401 (expired or invalid token). */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler;
}

export async function toApiError(response: Response): Promise<ApiError> {
  let problem: Partial<Problem> = {};
  try {
    problem = await response.json();
  } catch {
    // not a JSON body (e.g. a proxy error page)
  }
  if (response.status === 401 && tokenStore.get()) {
    unauthorizedHandler?.();
  }
  return new ApiError(response.status, problem.detail ?? fallbackMessage(response.status), problem.code, problem.errors);
}

function fallbackMessage(status: number): string {
  if (status === 0 || status >= 500) return 'The server is having trouble right now. Please try again.';
  if (status === 429) return 'Too many requests. Please slow down and try again shortly.';
  return `Request failed (${status}).`;
}

export function authHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra);
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return headers;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = authHeaders(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  let response: Response;
  try {
    response = await fetch(API_BASE + path, { ...init, headers });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }
  if (!response.ok) throw await toApiError(response);
  return (response.status === 204 ? undefined : await response.json()) as T;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

export const api = {
  register: (body: RegisterRequest) => post<RegistrationResponse>('/api/auth/register', body),
  login: (email: string, password: string) => post<AuthResponse>('/api/auth/login', { email, password }),
  verifyEmail: (token: string) => post<AuthResponse>('/api/auth/verify-email', { token }),
  resendVerification: (email: string) => post<MessageResponse>('/api/auth/resend-verification', { email }),
  forgotPassword: (email: string) => post<MessageResponse>('/api/auth/forgot-password', { email }),
  resetPassword: (token: string, password: string) =>
    post<AuthResponse>('/api/auth/reset-password', { token, password }),
  me: () => request<User>('/api/auth/me'),
  meta: () => request<MetaResponse>('/api/meta'),
  countries: () => request<Country[]>('/api/meta/countries'),
  usage: () => request<UsageResponse>('/api/usage/me'),

  uploadResume: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<ResumeResponse>('/api/resumes', { method: 'POST', body: form });
  },
  createJobDescription: (body: { title?: string; company?: string; text: string }) =>
    post<JobDescriptionResponse>('/api/job-descriptions', body),

  analyze: (resumeId: number, jdId: number) => post<AnalysisResponse>('/api/analyses', { resumeId, jdId }),
  analysis: (id: number) => request<AnalysisResponse>(`/api/analyses/${id}`),
  analyses: () => request<AnalysisSummary[]>('/api/analyses'),

  startInterview: (analysisId: number, level: InterviewLevel) =>
    post<InterviewResponse>('/api/interviews', { analysisId, level }),
  interview: (id: number) => request<InterviewResponse>(`/api/interviews/${id}`),
  interviews: () => request<InterviewSummary[]>('/api/interviews'),
  answer: (id: number, answer: string) => post<Turn>(`/api/interviews/${id}/answers`, { answer }),
  endInterview: (id: number) => post<InterviewResponse>(`/api/interviews/${id}/end`),

  report: (sessionId: number) => request<ReportResponse>(`/api/reports/${sessionId}`),

  /** Fetches the PDF with the auth header, then hands it to the browser as a download. */
  downloadReport: async (sessionId: number) => {
    const response = await fetch(`${API_BASE}/api/reports/${sessionId}.pdf`, { headers: authHeaders() });
    if (!response.ok) throw await toApiError(response);
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = `interview-report-${sessionId}.pdf`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  },
};
