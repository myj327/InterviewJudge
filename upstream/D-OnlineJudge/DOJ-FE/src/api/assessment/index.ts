import request from '@/utils/request';
export interface Assessment {
    id: number; title: string; problemId: number; startsAt: string; endsAt: string; status: string;
}
export interface Attempt { id: number; candidateId: number; status: string; startedAt: string; submittedAt?: string; }
export interface AttemptView { attemptId: number; problemId: number; status: string; }
export interface SubmissionSummary { submissionId: number; status: string; judgeStage: string; submitTime: string; }
export interface ReviewReport {
    submissionId: number; status: string; summary: string; model?: string;
    strengths: string[]; risks: string[]; suggestions: string[];
}
async function data<T>(response: Promise<{data: {code: number; msg: string; data: T}}>): Promise<T> {
    const result = (await response).data;
    if (result.code !== 200) throw new Error(result.msg || '操作失败');
    return result.data;
}
export const createAssessment = (draft: Omit<Assessment, 'id' | 'status'>) => data<number>(request.post('/assessments', {
    ...draft, startsAt: new Date(draft.startsAt).toISOString().slice(0,-1), endsAt: new Date(draft.endsAt).toISOString().slice(0,-1)
}));
export const localAssessmentTime = (value?: string) => value ? new Date(value + 'Z').toLocaleString() : '—';
export const ownAssessments = () => data<Assessment[]>(request.get('/assessments'));
export const publishAssessment = (id: number) => data<void>(request.post(`/assessments/${id}/publish`));
export const assessmentAttempts = (id: number) => data<Attempt[]>(request.get(`/assessments/${id}/attempts`));
export const attemptView = (id: string) => data<AttemptView>(request.get(`/assessments/attempts/${id}`));
export const finishAttempt = (id: string) => data<void>(request.post(`/assessments/attempts/${id}/finish`));
export const attemptReviews = (id: number) => data<SubmissionSummary[]>(request.get(`/submission/attempts/${id}/reviews`));
export const submissionReview = (id: number) => data<ReviewReport>(request.get(`/submission/${id}/review`));
