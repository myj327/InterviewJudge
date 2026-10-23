<template>
    <section aria-label="考核提交与复盘">
        <p>Attempt #{{ attemptId }} · 最近 100 条提交</p>
        <el-button :disabled="loading" @click="load">刷新提交</el-button>
        <p v-if="error" role="alert">{{ error }}</p><p v-if="loading" role="status">加载中…</p>
        <el-table :data="submissions" empty-text="暂无提交">
            <el-table-column prop="submissionId" label="提交编号" /><el-table-column prop="judgeStage" label="判题阶段" />
            <el-table-column prop="status" label="判题结果" />
            <el-table-column label="复盘"><template #default="{row}"><el-button @click="selected = row.submissionId">查看复盘</el-button></template></el-table-column>
        </el-table>
        <SubmissionReviewPanel v-if="selected" :key="selected" :submission-id="selected" />
    </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { attemptReviews } from '@/api/assessment';
import type { SubmissionSummary } from '@/api/assessment';
import SubmissionReviewPanel from './SubmissionReviewPanel.vue';
import { useUserStore } from '@/stores/userStore';
const props = defineProps<{attemptId:number}>(); const userStore = useUserStore();
const submissions = ref<SubmissionSummary[]>([]); const selected = ref<number>();
const error = ref(''); const loading = ref(false); let generation = 0;
async function load() {
    const ticket = ++generation; loading.value = true; error.value = ''; submissions.value = []; selected.value = undefined;
    try { const result = await attemptReviews(props.attemptId); if (ticket === generation) submissions.value = result; }
    catch (err) { if (ticket === generation) error.value = err instanceof Error ? err.message : '提交加载失败'; }
    finally { if (ticket === generation) loading.value = false; }
}
watch(() => [props.attemptId, userStore.userInfo?.userId], load, {immediate:true});
</script>
