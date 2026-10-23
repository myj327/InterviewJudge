export type SubmissionDraft = { code: string; language: string; pid: string; attemptId?: string };
export type SubmissionSnapshot = Readonly<SubmissionDraft & { idempotencyKey?: string }>;
type Receipt = { code: number; msg?: string; data: number };

// Owned by the editor wrapper so switching languages keeps the pending request.
export class SubmissionSession {
    busy = false;
    pending: SubmissionSnapshot | null = null;
    submissionId: number | null = null;

    async send(draft: SubmissionDraft, retry: boolean,
               transport: (snapshot: SubmissionSnapshot) => Promise<Receipt>) {
        if (this.busy) return;
        const reuse = retry || Boolean(this.pending?.attemptId);
        if (reuse && (!this.pending?.attemptId || this.pending.attemptId !== draft.attemptId
            || this.pending.pid !== draft.pid)) throw new Error('没有可重试的考核提交');
        if (!reuse && !draft.code.trim()) throw new Error('代码不能为空');
        if (!reuse && draft.attemptId && draft.language !== 'java') throw new Error('考核仅支持 Java');
        const snapshot = reuse ? this.pending! : Object.freeze({ ...draft,
            idempotencyKey: draft.attemptId ? Array.from(crypto.getRandomValues(new Uint8Array(16)),
                byte => byte.toString(16).padStart(2, '0')).join('') : undefined });
        if (snapshot.attemptId && snapshot.language !== 'java') throw new Error('考核仅支持 Java');
        this.busy = true;
        this.pending = snapshot;
        this.submissionId = null;
        try {
            const response = await transport(snapshot);
            if (response.code !== 200) {
                throw new Error(response.msg || '提交未成功受理');
            }
            if (!Number.isSafeInteger(response.data) || response.data <= 0) throw new Error('提交受理结果未确认');
            this.pending = null;
            this.submissionId = response.data;
            return response.data;
        } finally {
            this.busy = false;
        }
    }
}
