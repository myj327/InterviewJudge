<template>
    <div class="problem-detail-page">
        <section class="hero card-glass">
            <div class="hero-main">
                <div class="title-row">
                    <span class="problem-id">#{{ problem?.id }}</span>
                    <h1 class="problem-title">{{ problem?.name }}</h1>
                </div>
            </div>
            <div class="hero-actions">
                <button class="btn-primary" @click="scrollToEditor">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="10">
                        <polyline points="16 18 22 12 16 6"/>
                        <polyline points="8 6 2 12 8 18"/>
                    </svg>
                    提交代码
                </button>
                <button class="btn-secondary" @click="personSubmission">我的提交</button>
            </div>
        </section>

        <section class="card-glass assessment-entry" aria-label="考核入口">
            <div v-if="route.query.attemptId">
                <p>考核模式 · Attempt #{{ route.query.attemptId }} · 仅支持 Java · {{ attemptStatus === 'STARTED' ? '进行中' : attemptStatus === 'SUBMITTED' ? '已交卷' : attemptStatus === 'EXPIRED' ? '已过期' : '加载中' }}</p>
                <p>编译 → 基础用例 → 完整用例 → AI 复盘；未通过前置阶段会跳过后续阶段。</p>
                <el-button v-if="attemptStatus === 'STARTED'" :disabled="finishing || submissionBlocked" :loading="finishing" @click="finishAssessment">交卷并结束考核</el-button>
                <p v-if="finishing" role="status">正在处理交卷，提交和重试暂时暂停。</p>
                <p v-else-if="submissionBlocked" role="status">{{ attemptStatus === 'STARTED' ? '请先确认当前请求的提交编号，再交卷。' : '考核已结束，请使用原请求恢复提交编号。' }}</p>
                <el-button @click="personSubmission">查看我的提交与复盘</el-button>
            </div>
            <form v-else @submit.prevent="startAssessment">
                <label for="assessment-id">考核编号</label>
                <el-input id="assessment-id" v-model="assessmentId" :disabled="joining" placeholder="输入招聘方提供的考核编号" />
                <el-button native-type="submit" :loading="joining" :disabled="joining">进入考核</el-button>
                <span>当前为练习模式；进入考核后启用重复请求保护。</span>
            </form>
            <p v-if="entryError" role="alert">{{ entryError }}</p>
        </section>

        <div class="problem-layout">
            <main class="problem-main">
                <div class="problem-content-wrapper">
                    <section class="content-card card-glass">
                        <div class="content-section">
                            <h2 class="section-title">题目描述</h2>
                            <div class="markdown-body" v-html="renderMarkdown(problem?.description)"></div>
                        </div>

                        <div class="content-section">
                            <h2 class="section-title">输入格式</h2>
                            <div class="markdown-body" v-html="renderMarkdown(problem?.inputStyle)"></div>
                        </div>

                        <div class="content-section">
                            <h2 class="section-title">输出格式</h2>
                            <div class="markdown-body" v-html="renderMarkdown(problem?.outputStyle)"></div>
                        </div>

                        <div class="content-section">
                            <h2 class="section-title">样例</h2>
                            <div v-if="problem?.inputSample?.length" class="samples">
                                <div v-for="(input, idx) in problem?.inputSample" :key="idx" class="sample-pair">
                                    <div class="sample-card">
                                        <div class="sample-header">
                                            <span>输入样例 #{{ idx + 1 }}</span>
                                            <button class="copy-tiny" @click="copyText(input)">复制</button>
                                        </div>
                                        <pre class="sample-code">{{ input }}</pre>
                                    </div>
                                    <div class="sample-card">
                                        <div class="sample-header">
                                            <span>输出样例 #{{ idx + 1 }}</span>
                                            <button class="copy-tiny" @click="copyText(problem?.outputSample?.[idx])">复制</button>
                                        </div>
                                        <pre class="sample-code">{{ problem?.outputSample?.[idx] }}</pre>
                                    </div>
                                </div>
                            </div>
                            <div v-else class="empty-block">暂无样例</div>
                        </div>

                        <div v-if="problem?.hint" class="content-section">
                            <h2 class="section-title">提示</h2>
                            <div class="markdown-body" v-html="renderMarkdown(problem?.hint)"></div>
                        </div>
                    </section>
                </div>
            </main>

            <aside class="problem-sidebar">
                <div class="sidebar-card card-glass">
                    <h3 class="sidebar-title">问题信息</h3>
                    <p>Java 判题支持题目限额 1–10000 ms、128–1024 MB。独立运行默认 2 秒、128 MB。</p>
                    <div class="meta-list">
                        <div class="meta-item-inline">
                            <span class="meta-label">难度</span>
                            <span class="difficulty-tag" :class="difficultyClass">{{ problem?.difficulty }}</span>
                        </div>
                        <div class="meta-item-inline">
                            <span class="meta-label">时间限制</span>
                            <span class="meta-value">{{ problem?.timeLimit }} ms</span>
                        </div>
                        <div class="meta-item-inline">
                            <span class="meta-label">内存限制</span>
                            <span class="meta-value">{{ problem?.memoryLimit }} MB</span>
                        </div>
                    </div>
                </div>

                <div class="sidebar-card card-glass">
                    <h3 class="sidebar-title">标签</h3>
                    <div class="tag-row">
                        <span v-for="tag in problem?.tags" :key="tag" class="tag-chip" @click="searchByTag(tag)">{{ tag }}</span>
                        <span v-if="!problem?.tags?.length" class="tag-empty">暂无标签</span>
                    </div>
                </div>

                <div class="sidebar-card card-glass">
                    <h3 class="sidebar-title">提交统计</h3>
                    <div class="stats-grid">
                        <div class="stat-item">
                            <span class="stat-label">提交次数</span>
                            <span class="stat-value">{{ problem?.totalAttempt }}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">通过次数</span>
                            <span class="stat-value">{{ problem?.totalPass }}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">通过率</span>
                            <span class="stat-value">{{ passRate }}%</span>
                        </div>
                    </div>
                </div>
            </aside>
        </div>

        <div class="editor-container" ref="editorRef">
            <div class="editor-card card-glass">
                <div class="editor-header">
                    <h2>代码编辑器</h2>
                </div>
                <Editor v-if="problem && !entryError && (!route.query.attemptId || attemptStatus === 'STARTED' || submissionBlocked)" ref="editorInstance" :key="route.fullPath + ':' + userStore.userInfo?.userId" :config="config" />
                <p v-else-if="attemptStatus === 'SUBMITTED' || attemptStatus === 'EXPIRED'">本次考核已结束，可查看提交与复盘。</p>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { reqProblemDetail } from '@/api/problem';
import type { ProblemType } from '@/api/problem/type';
import 'katex/dist/katex.min.css';
import { configType } from '@/components/CodeEditor/index.vue';
import Editor from '@/components/CodeEditor/index.vue';
import { useUserStore } from '@/stores/userStore';
import { renderMarkdown } from '@/utils/markdown';
import { reqStartAttempt, reqAttemptProblem } from '@/api/submit';
import { attemptView, finishAttempt } from '@/api/assessment';
import { ElMessage, ElMessageBox } from 'element-plus';

const config = ref<configType>({
    tabSize: 4,
    disabled: false,
    submissionLocked: false,
    height: '60vh',
    width: '100%',
    language: 'java',
    theme: 'default',
    fontSize: 16,
    editorType: 'problem'
});

const route = useRoute();
const router = useRouter();
const problem = ref<ProblemType>();
const userStore = useUserStore();
const editorRef = ref<HTMLElement | null>(null);
const assessmentId = ref(typeof route.query.assessmentId === 'string' ? route.query.assessmentId : '');
const joining = ref(false);
const entryError = ref('');
const attemptStatus = ref('');
const finishing = ref(false);
const editorInstance = ref<InstanceType<typeof Editor> | null>(null);
const submissionBlocked = computed(() => Boolean(editorInstance.value?.submission.busy || editorInstance.value?.submission.pending));

async function finishAssessment(event?: MouseEvent) {
    if (finishing.value || (event?.detail && event.detail > 1)) return;
    if (submissionBlocked.value) { ElMessage.error('请先恢复并确认当前提交'); return; }
    const id = route.query.attemptId;
    if (typeof id !== 'string' || !/^[1-9]\d*$/.test(id) || attemptStatus.value !== 'STARTED') return;
    finishing.value = true;
    config.value.submissionLocked = true;
    try {
        await ElMessageBox.confirm('交卷后不能再新提交。确认结束本次考核？', '确认交卷', {type: 'warning'});
        if (submissionBlocked.value) { ElMessage.error('请先确认当前提交'); return; }
        await finishAttempt(id);
        attemptStatus.value = (await attemptView(id)).status;
        config.value.disabled = attemptStatus.value !== 'STARTED';
        ElMessage.success('本次考核已结束');
    } catch (err) {
        if (err !== 'cancel' && err !== 'close') ElMessage.error(err instanceof Error ? err.message : '交卷失败，请重试');
    } finally { config.value.submissionLocked = false; finishing.value = false; }
}

const startAssessment = async () => {
    if (joining.value) return;
    if (!/^[1-9]\d*$/.test(assessmentId.value.trim())) return ElMessage.error('请输入有效的考核编号');
    joining.value = true;
    try {
        const attempt = (await reqStartAttempt(assessmentId.value.trim())).data;
        if (attempt.code !== 200) throw new Error(attempt.msg);
        const target = await attemptView(String(attempt.data));
        await router.push({ path: `/problem/${target.problemId}`, query: { attemptId: String(attempt.data) } });
    } catch (err) {
        ElMessage.error(err instanceof Error ? err.message : '无法进入考核');
    } finally { joining.value = false; }
};

const difficultyClass = computed(() => {
    if (!problem.value) return '';
    const diff = problem.value.difficulty;
    return {
        easy: diff === '简单',
        medium: diff === '中等',
        hard: diff === '困难'
    };
});

const passRate = computed(() => {
    if (!problem.value || problem.value.totalAttempt === 0) return '0.0';
    return (problem.value.totalPass / problem.value.totalAttempt * 100).toFixed(1);
});

const scrollToEditor = () => {
    editorRef.value?.scrollIntoView({ behavior: 'smooth' });
};

const copyText = (text: string | undefined) => {
    if (text) {
        navigator.clipboard.writeText(text).catch(err => {
            console.error('复制失败：', err);
        });
    }
};

const personSubmission = () => {
    router.push({
        path: '/status',
        query: {
            problemId: String(problem.value?.id || '')
        }
    });
};

const searchByTag = (tag: string) => {
    router.push({
        path: '/problem',
        query: { tag }
    });
};

watch(() => [route.fullPath, userStore.userInfo?.userId], async (_, __, onCleanup) => {
    let active = true;
    onCleanup(() => { active = false; });
    problem.value = undefined;
    entryError.value = '';
    attemptStatus.value = '';
    config.value.disabled = false;
    try {
        const attemptId = route.query.attemptId;
        if (attemptId !== undefined) {
            if (typeof attemptId !== 'string' || !/^[1-9]\d*$/.test(attemptId)) throw new Error('考核链接无效');
            const target = await attemptView(attemptId);
            if (!active) return;
            attemptStatus.value = target.status;
            config.value.disabled = target.status !== 'STARTED';
            if (String(target.problemId) !== route.params.id) {
                await router.replace({ path: `/problem/${target.problemId}`, query: { attemptId } });
                return;
            }
        }
        config.value.language = 'java';
        const response = (await reqProblemDetail(route.params.id as string)).data;
        if (response.code !== 200) throw new Error('题目加载失败');
        if (active) problem.value = response.data;
    } catch (err) {
        if (active) entryError.value = err instanceof Error ? err.message : '无法加载考核题目';
    }
}, { immediate: true });
</script>

<style scoped lang="scss">
.assessment-entry {
    padding: $space-md;
    form { display: flex; flex-wrap: wrap; gap: $space-sm; align-items: center; }
    :deep(.el-input) { width: min(100%, 260px); }
}
.problem-detail-page {
    padding: $space-lg;
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: $space-xl;
}

.hero {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: $space-lg;
    align-items: center;
    background: var(--bg-elevated);
    border-radius: $radius-lg;

    @include tablet {
        grid-template-columns: 1fr;
    }
}

.hero-main {
    display: flex;
    flex-direction: column;
    gap: $space-md;
}

.title-row {
    display: flex;
    align-items: baseline;
    gap: $space-sm;
}

.problem-id {
    padding: 2px 10px;
    border-radius: $radius-full;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    color: var(--text-muted);
    font-size: $font-size-xs;
}

.problem-title {
    margin: 0;
    font-size: $font-size-2xl;
    color: var(--text-primary);
}

.hero-meta {
    display: flex;
    flex-wrap: wrap;
    gap: $space-sm;
}

.meta-chip {
    padding: 6px 12px;
    border-radius: $radius-full;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    color: var(--text-secondary);
    font-size: $font-size-xs;
}

.tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: $space-xs;
}

.tag-chip {
    padding: 4px 12px;
    border-radius: $radius-full;
    background: $info-light;
    border: 1px solid rgba($info, 0.2);
    color: var(--text-secondary);
    font-size: $font-size-xs;
    transition: all $transition-fast;
    cursor: pointer;

    &:hover {
        background: rgba($info, 0.25);
        border-color: rgba($info, 0.4);
    }
}

.tag-empty {
    color: var(--text-muted);
    font-size: $font-size-xs;
}

.hero-actions {
    display: flex;
    gap: $space-md;
    align-items: center;
    flex-shrink: 0;
    flex-wrap: wrap;
    justify-content: flex-end;

    button {
        padding: 0 $space-lg;
        height: 40px;
        display: flex;
        align-items: center;
        gap: $space-sm;
        white-space: nowrap;
        min-width: 120px;
    }
}

.problem-layout {
    display: grid;
    grid-template-columns: 1fr 320px;
    gap: $space-xl;

    @include tablet {
        grid-template-columns: 1fr;
    }
}

.problem-main {
    display: flex;
    flex-direction: column;
    gap: $space-lg;
}

.content-card {
    padding: $space-lg;
    background: var(--bg-elevated);
    border-radius: $radius-lg;
}

.content-section + .content-section {
    margin-top: $space-lg;
    padding-top: $space-lg;
    border-top: 1px solid var(--border-color);
}

.section-title {
    margin: 0 0 $space-md;
    font-size: $font-size-lg;
    color: var(--primary-start);
    display: flex;
    align-items: center;
    gap: $space-sm;
    border-bottom: 1px solid var(--border-color);
    padding-bottom: $space-xs;
}

.samples {
    display: flex;
    flex-direction: column;
    gap: $space-lg;
}

.sample-pair {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: $space-lg;

    @include tablet {
        grid-template-columns: 1fr;
    }
}

.sample-card {
    background: var(--bg-primary);
    border-radius: $radius-md;
    padding: $space-md;
}

.sample-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: $font-size-xs;
    color: var(--text-secondary);
    margin-bottom: $space-xs;
}

.sample-code {
    margin: 0;
    white-space: pre-wrap;
    font-family: $font-mono;
    font-size: $font-size-sm;
    color: var(--text-primary);
}

.copy-tiny {
    background: transparent;
    border: 1px solid var(--border-color);
    border-radius: $radius-sm;
    padding: 2px 8px;
    font-size: 10px;
    cursor: pointer;

    &:hover {
        background: var(--glass-bg);
    }
}

.empty-block {
    color: var(--text-muted);
    font-size: $font-size-sm;
}

.problem-sidebar {
    display: flex;
    flex-direction: column;
    gap: $space-lg;
}

.sidebar-card {
    padding: $space-lg;
    background: var(--bg-elevated);
    border-radius: $radius-lg;
}

.sidebar-title {
    font-size: $font-size-base;
    font-weight: 600;
    margin-bottom: $space-md;
}

.stats-grid {
    display: grid;
    gap: $space-sm;
}

.stat-item {
    display: flex;
    justify-content: space-between;
    font-size: $font-size-sm;
    color: var(--text-secondary);
}

.stat-value {
    color: var(--text-primary);
    font-weight: 500;
}

.meta-item-inline {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: $font-size-sm;
    color: var(--text-secondary);
    padding: $space-xs 0;

    &:not(:last-child) {
        border-bottom: 1px solid var(--border-color);
    }
}

.difficulty-tag {
    padding: 2px 10px;
    border-radius: $radius-full;
    font-size: 12px;
    &.easy { background: rgba($success, 0.12); color: $success; }
    &.medium { background: rgba($warning, 0.12); color: $warning; }
    &.hard { background: rgba($danger, 0.12); color: $danger; }
}

.markdown-body {
    line-height: 1.7;
    color: var(--text-secondary);

    :deep(h1),
    :deep(h2),
    :deep(h3) {
        color: var(--text-primary);
        margin: $space-md 0 $space-sm;
        font-weight: 600;
    }

    :deep(p) {
        margin: 0 0 $space-sm;
    }

    :deep(ul),
    :deep(ol) {
        padding-left: 1.5em;
        margin: 0 0 $space-sm;
    }

    :deep(code) {
        background: var(--bg-primary);
        padding: 2px 6px;
        border-radius: 6px;
        font-family: $font-mono;
        font-size: $font-size-sm;
    }

    :deep(pre) {
        background: var(--bg-primary);
        padding: $space-md;
        border-radius: $radius-md;
        overflow: auto;
    }

    :deep(blockquote) {
        margin: $space-md 0;
        padding: $space-sm $space-md;
        border-left: 3px solid var(--primary-start);
        background: var(--glass-bg);
    }
}

.editor-container {
    .editor-card {
        padding: 0;
        overflow: hidden;
    }

    .editor-header {
        padding: $space-md $space-lg;
        border-bottom: 1px solid var(--border-color);
    }
}
</style>
