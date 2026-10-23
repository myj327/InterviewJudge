<template>
    <section v-if="userStore.userInfo?.role === false" class="assessment-manager">
        <h2>考核管理</h2>
        <p>创建草稿，确认信息后发布。当前考核为单题 Java：编译 → 基础用例 → 完整用例 → AI 复盘。</p>
        <el-form label-position="top" @submit.prevent="create" @keydown="preventRepeatedEnter">
            <el-form-item label="考核标题"><el-input v-model="draft.title" maxlength="128" :disabled="busy" /></el-form-item>
            <el-form-item label="题目编号"><el-input-number v-model="draft.problemId" :min="1" :precision="0" :disabled="busy" /></el-form-item>
            <el-form-item label="开始时间（本地时区）"><el-date-picker v-model="draft.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" :disabled="busy" /></el-form-item>
            <el-form-item label="截止时间"><el-date-picker v-model="draft.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" :disabled="busy" /></el-form-item>
            <p v-if="error" role="alert">{{ error }}</p>
            <el-button type="primary" native-type="submit" :loading="busy" :disabled="busy" @click="preventMulticlick">创建草稿</el-button>
            <el-button :disabled="busy" @click="load">刷新我的考核</el-button>
        </el-form>
        <p v-if="notice" role="status">{{ notice }}</p>
        <el-table :data="assessments" empty-text="暂无考核" aria-label="我的考核（最近 100 条）">
            <el-table-column prop="id" label="编号" width="85" /><el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column prop="problemId" label="题号" width="85" /><el-table-column label="截止时间（本地）" min-width="170"><template #default="{row}">{{ localAssessmentTime(row.endsAt) }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{row}">{{ row.status === 'DRAFT' ? '草稿' : '已发布' }}</template></el-table-column>
            <el-table-column label="操作" min-width="300"><template #default="{row}">
                <el-button v-if="row.status === 'DRAFT'" :disabled="busy" @click="publish(row, $event)">发布</el-button>
                <el-button v-else :disabled="busy" @click="router.push({path: `/problem/${row.problemId}`, query: {assessmentId: String(row.id)}})">候选人入口</el-button>
                <el-button :disabled="busy" @click="showAttempts(row.id)">参加记录</el-button>
            </template></el-table-column>
        </el-table>
        <el-dialog v-model="attemptsOpen" title="参加记录（最近 100 条）" width="min(900px, calc(100vw - 32px))" append-to-body align-center class="ij-assessment-dialog">
            <p v-if="attemptsLoading" role="status">加载中…</p>
            <p v-if="attemptsError" role="alert">{{ attemptsError }}</p>
            <el-table :data="attempts" empty-text="暂无参加记录">
                <el-table-column prop="id" label="Attempt" /><el-table-column prop="candidateId" label="候选人编号" />
                <el-table-column prop="status" label="状态" /><el-table-column label="开始时间（本地）"><template #default="{row}">{{ localAssessmentTime(row.startedAt) }}</template></el-table-column>
                <el-table-column label="报告"><template #default="{row}"><el-button @click="selectedAttempt = row.id">提交与复盘</el-button></template></el-table-column>
            </el-table>
            <AttemptReviews v-if="selectedAttempt" :key="selectedAttempt" :attempt-id="selectedAttempt" />
            <template #footer><el-button @click="attemptsOpen = false">关闭</el-button></template>
        </el-dialog>
    </section>
    <p v-else role="alert">请使用管理员账号进入考核管理。</p>
</template>
<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/userStore';
import { createAssessment, ownAssessments, publishAssessment, assessmentAttempts, localAssessmentTime } from '@/api/assessment';
import type { Assessment, Attempt } from '@/api/assessment';
import AttemptReviews from '@/components/AttemptReviews.vue';
const userStore = useUserStore(); const router = useRouter();
const draft = reactive({title: '', problemId: 1, startsAt: '', endsAt: ''});
const busy = ref(false); const error = ref(''); const notice = ref('');
const assessments = ref<Assessment[]>([]); const attempts = ref<Attempt[]>([]);
const attemptsOpen = ref(false); const attemptsLoading = ref(false); const attemptsError = ref('');
const selectedAttempt = ref<number>();
const message = (err: unknown) => err instanceof Error ? err.message : '操作失败，请刷新后重试';
const preventRepeatedEnter = (event: KeyboardEvent) => { if (event.repeat && event.key === 'Enter') event.preventDefault(); };
const preventMulticlick = (event: MouseEvent) => { if (event.detail > 1) event.preventDefault(); };
async function load() {
    if (busy.value || userStore.userInfo?.role !== false) return;
    busy.value = true; error.value = '';
    try { assessments.value = await ownAssessments(); } catch (err) { error.value = message(err); }
    finally { busy.value = false; }
}
async function create(event?: Event) {
    if (busy.value || userStore.userInfo?.role !== false || (event instanceof MouseEvent && event.detail > 1)) return;
    error.value = ''; notice.value = '';
    if (!draft.title.trim() || !Number.isInteger(draft.problemId) || draft.problemId < 1
        || !draft.startsAt || !draft.endsAt || !(new Date(draft.startsAt) < new Date(draft.endsAt))) {
        error.value = '请填写标题、有效题号和起止时间，截止时间须晚于开始时间'; return;
    }
    busy.value = true;
    try {
        const id = await createAssessment({...draft, title: draft.title.trim()});
        notice.value = `草稿 #${id} 已创建，请确认后发布`;
        assessments.value = await ownAssessments();
    } catch (err) { error.value = message(err); }
    finally { busy.value = false; }
}
async function publish(row: Assessment, event?: MouseEvent) {
    if (busy.value || event?.detail && event.detail > 1) return;
    busy.value = true; error.value = ''; notice.value = '';
    try { await publishAssessment(row.id); row.status = 'PUBLISHED'; notice.value = `考核 #${row.id} 已发布`; }
    catch (err) { error.value = message(err); } finally { busy.value = false; }
}
async function showAttempts(id: number) {
    if (attemptsLoading.value) return;
    attempts.value = []; attemptsError.value = ''; attemptsOpen.value = true; attemptsLoading.value = true;
    selectedAttempt.value = undefined;
    try { attempts.value = await assessmentAttempts(id); } catch (err) { attemptsError.value = message(err); }
    finally { attemptsLoading.value = false; }
}
onMounted(load);
</script>
<style scoped lang="scss">
.assessment-manager { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: $space-md; color: var(--text-primary); }
.el-form { max-width: 680px; }
:deep(.el-date-editor.el-input) { max-width: 100%; }
:deep(.el-button) { min-height: 44px; margin-bottom: $space-xs; }
:global(.ij-assessment-dialog) { display: flex; flex-direction: column; max-height: calc(100dvh - 32px); }
:global(.ij-assessment-dialog .el-dialog__body) { min-height: 0; overflow: auto; overflow-wrap: anywhere; }
:global(.ij-assessment-dialog .el-dialog__header),
:global(.ij-assessment-dialog .el-dialog__footer) { flex-shrink: 0; }
:global(.ij-assessment-dialog .el-dialog__header) { padding-right: 52px; }
:global(.ij-assessment-dialog .el-dialog__headerbtn) { width: 44px; height: 44px; }
</style>
