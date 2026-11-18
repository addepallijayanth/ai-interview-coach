// Mirrors the backend DTOs. The server omits null fields, so nullable values are optional here.

export interface User {
  id: number;
  email: string;
  /** Profile details are collected at sign-up (older accounts may not have them). */
  firstName?: string;
  lastName?: string;
  /** ISO 3166-1 alpha-2 country of the mobile number, e.g. "IN". */
  countryCode?: string;
  /** E.164, e.g. "+919876543210". */
  phoneNumber?: string;
  emailVerified: boolean;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  mobileNumber: string;
  password: string;
}

export interface RegistrationResponse {
  email: string;
  message: string;
}

export interface MessageResponse {
  message: string;
}

export interface Country {
  code: string;
  name: string;
  dialCode: string;
}

export type InterviewLevel = 'EASY' | 'STANDARD' | 'PROFESSIONAL';

export interface CategoryCount {
  category: string;
  label: string;
  count: number;
}

export interface LevelInfo {
  id: InterviewLevel;
  label: string;
  description: string;
  questions: number;
  breakdown: CategoryCount[];
}

export interface CategoryInfo {
  id: string;
  label: string;
  description: string;
}

export interface MetaResponse {
  aiConfigured: boolean;
  fastModel: string;
  smartModel: string;
  /** False when the server has no SMTP settings and only logs email links. */
  emailEnabled: boolean;
  interviewLevels: LevelInfo[];
  questionCategories: CategoryInfo[];
  maxUploadMb: number;
  maxAnswerChars: number;
}

export interface UsageResponse {
  calls: number;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  costUsd: number;
  aiRequestsRemainingToday: number;
  aiRequestsPerDay: number;
}

export interface ResumeResponse {
  id: number;
  fileName: string;
  characters: number;
  redactions: Record<string, number>;
  redactedText: string;
  createdAt: string;
}

export interface JobDescriptionResponse {
  id: number;
  title?: string;
  company?: string;
  characters: number;
  createdAt: string;
}

export interface Suggestion {
  original: string;
  improved: string;
  reason: string;
}

export interface MatchResult {
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  strengths: string[];
  suggestions: Suggestion[];
  summary: string;
}

export interface ResumeProfile {
  skills: string[];
  yearsExperience: number;
  roles: string[];
  seniority: string;
  education: string;
  highlights: string[];
}

export interface JobRequirements {
  title: string;
  company: string;
  seniority: string;
  yearsRequired: number;
  requiredSkills: string[];
  niceToHaveSkills: string[];
  responsibilities: string[];
}

export interface AnalysisResponse {
  id: number;
  resumeId: number;
  jdId: number;
  jobTitle: string;
  company?: string;
  matchScore: number;
  match: MatchResult;
  candidate: ResumeProfile;
  job: JobRequirements;
  modelUsed: string;
  tokensIn: number;
  tokensOut: number;
  costUsd: number;
  createdAt: string;
}

export interface AnalysisSummary {
  id: number;
  jobTitle: string;
  company?: string;
  matchScore: number;
  createdAt: string;
}

export interface AnswerFeedback {
  strengths: string[];
  weaknesses: string[];
  modelAnswer: string;
}

export interface Turn {
  questionNo: number;
  /** e.g. "SYSTEM_DESIGN"; absent for interviews created before question categories existed. */
  category?: string;
  categoryLabel?: string;
  question: string;
  answer?: string;
  score?: number;
  feedback?: AnswerFeedback;
}

export type InterviewStatus = 'ACTIVE' | 'ENDED';

export interface InterviewResponse {
  id: number;
  analysisId: number;
  status: InterviewStatus;
  level: InterviewLevel;
  levelLabel: string;
  questionTarget: number;
  questionsAnswered: number;
  overallScore?: number;
  summary?: string;
  jobTitle: string;
  company?: string;
  awaitingFeedback: boolean;
  turns: Turn[];
  createdAt: string;
  endedAt?: string;
}

export interface InterviewSummary {
  id: number;
  analysisId: number;
  status: InterviewStatus;
  level: InterviewLevel;
  levelLabel: string;
  jobTitle: string;
  questionTarget: number;
  questionsAnswered: number;
  overallScore?: number;
  createdAt: string;
}

export interface FocusArea {
  topic: string;
  priority: 'high' | 'medium' | 'low';
  why: string;
  actions: string[];
  resources: string[];
}

export interface StudyPlan {
  summary: string;
  focusAreas: FocusArea[];
}

export interface CategoryScore {
  category: string;
  label: string;
  asked: number;
  scored: number;
  averageScore?: number;
}

export interface ReportResponse {
  sessionId: number;
  jobTitle: string;
  company?: string;
  level: InterviewLevel;
  levelLabel: string;
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  overallScore?: number;
  questionsAnswered: number;
  questionTarget: number;
  summary?: string;
  categoryScores: CategoryScore[];
  turns: Turn[];
  studyPlan?: StudyPlan;
  aiCostUsd: number;
  endedAt?: string;
}

/** RFC 7807 problem details returned for every error. */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: Record<string, string>;
}

/** Events on GET /api/interviews/{id}/stream. */
export type InterviewEvent =
  | { type: 'status'; message: string }
  | { type: 'token'; text: string }
  | { type: 'draft_reset' }
  | { type: 'feedback'; questionNo: number; score: number; strengths: string[]; weaknesses: string[]; modelAnswer: string }
  | { type: 'question'; questionNo: number; question: string; questionTarget: number; category: string; categoryLabel: string }
  | { type: 'ended'; overallScore?: number; summary?: string; questionsAnswered: number }
  | { type: 'error'; code: string; message: string; retryable: boolean }
  | { type: 'done' };
