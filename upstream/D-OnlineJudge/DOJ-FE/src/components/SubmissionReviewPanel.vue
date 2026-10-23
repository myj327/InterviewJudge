<template>
    <section aria-label="提交复盘" class="review-panel">
        <p role="status">提交 #{{ submissionId }} · {{ loading ? '加载中' : report ? stateLabel(report.status) : '尚未加载' }}</p>
        <el-button :disabled="loading" @click="loadReport">刷新复盘状态</el-button>
        <p v-if="error" role="alert">{{ error }}</p>
        <template v-if="report">
            <p v-if="report.model === 'mock'">当前为 mock 示例报告，未分析源码。真实模型效果待验收。</p>
            <p class="summary">{{ report.summary }}</p>
            <template v-if="report.status === 'SUCCEEDED'">
                <div v-for="group in [{title:'优点',items:report.strengths},{title:'风险',items:report.risks},{title:'改进建议',items:report.suggestions}]" :key="group.title">
                    <h3>{{ group.title }}</h3><ul><li v-for="(item,index) in group.items" :key="index">{{ item }}</li></ul>
                </div>
            </template>
        </template>
        <p>确定性判题决定结果，AI 复盘仅供参考。</p>
    </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useUserStore } from '@/stores/userStore';
import { submissionReview } from '@/api/assessment';
import type { ReviewReport } from '@/api/assessment';
const props = defineProps<{submissionId: number}>();
const userStore = useUserStore();
const report = ref<ReviewReport>(); const loading = ref(false); const error = ref(''); let generation = 0;
const stateLabel = (state: string) => (({PENDING:'等待生成',RUNNING:'生成中',FAILED:'生成失败，等待恢复',SKIPPED:'已跳过',SUCCEEDED:'已生成'} as Record<string,string>)[state] || state);
async function loadReport() {
    const ticket = ++generation; loading.value = true; error.value = ''; report.value = undefined;
    try { const result = await submissionReview(props.submissionId); if (ticket === generation) report.value = result; }
    catch (err) { if (ticket === generation) error.value = err instanceof Error ? err.message : '复盘加载失败'; }
    finally { if (ticket === generation) loading.value = false; }
}
watch(() => [props.submissionId, userStore.userInfo?.userId], loadReport, {immediate:true});
</script>
<style scoped lang="scss">
.review-panel { display: grid; gap: $space-sm; color: var(--text-primary); }
.summary { white-space: pre-wrap; overflow-wrap: anywhere; }
li { overflow-wrap: anywhere; }
:deep(.el-button) { min-height: 44px; justify-self: start; }
</style>
