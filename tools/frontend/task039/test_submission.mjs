// Three focused contracts against the actual TS session, SFC handlers and API adapters.
// Node 24 strips types; Vue rendering and real infrastructure are reserved for integration.
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const fe = path.join(root, 'upstream/D-OnlineJudge/DOJ-FE/src');
const source = relative => readFileSync(path.join(fe, relative), 'utf8');
const strip = code => stripTypeScriptTypes(code).replace(/^export\s+/gm, '');
const editorSource = source('components/CodeEditor/problemEditor.vue');
const handler = editorSource.slice(editorSource.indexOf('const handleSubmit1 ='), editorSource.indexOf('const handleSubmit ='));
const extensions = editorSource.slice(editorSource.indexOf('const getLanguageExtension ='), editorSource.indexOf('// 监听 props 变化'));
const adapters = source('api/submit/index.ts').split('\n').filter(line =>
    /^export (type IdResponse|const req(?:ProblemValidate|AttemptValidate|StartAttempt|AttemptProblem)\b)/.test(line)).join('\n');
const assessmentAdapters = source('api/assessment/index.ts').replace(/^import[^\n]*\n/gm, '');
const pageSource = source('views/Problem/detail/index.vue');
const setup = pageSource.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1].replace(/^import[^\n]*\n/gm, '');
const copy = value => JSON.parse(JSON.stringify(value));
const ok = data => ({ data: { code: 200, msg: 'OK', data } });

function environment(attemptId = '200') {
    const calls = [], errors = [], notifications = [], subscriptions = [], navigation = [];
    let transport = async call => ok(call.url.startsWith('/assessments/attempts/')
        ? { attemptId: 200, problemId: 10, status: 'STARTED' } : 42);
    let watcher, cleanup;
    const route = { params: { id: '10' }, query: attemptId === undefined ? {} : { attemptId }, fullPath: '/problem/10' };
    const scope = { Blob, FormData, crypto: webcrypto, API: { PROBLEMS_VALIDATE_URL: '/sandbox/validate' },
        code: { value: 'synthetic V1' }, props: { languageName: 'java', config: { disabled: false, submissionLocked: false } }, route,
        ElMessage: { error: message => errors.push(message), info: message => notifications.push(message) },
        useWebSocket: () => ({ subscribeToSubmission: id => subscriptions.push(id) }),
        request: Object.fromEntries(['post', 'get'].map(method => [method, (url, data) => {
            const call = { method, url, data }; calls.push(call); return transport(call);
        }])),
        ref: value => ({ value }), computed: read => ({ get value() { return read(); } }),
        useRoute: () => route, useRouter: () => ({
            push: async value => navigation.push(copy(value)), replace: async value => navigation.push(copy(value)) }),
        useUserStore: () => ({ userInfo: { userId: 3 } }), renderMarkdown: text => text,
        reqProblemDetail: async () => ok({ id: 10, name: 'synthetic problem' }),
        watch: (_, callback) => { watcher = callback; }
    };
    vm.createContext(scope);
    vm.runInContext(strip(source('utils/submissionSession.ts')) + '\nglobalThis.Session = SubmissionSession;', scope);
    scope.props.submission = new scope.Session();
    vm.runInContext(strip(adapters) + '\n' + strip(assessmentAdapters) + '\n' + strip(extensions) + '\n' + strip(handler) + '\nglobalThis.submit = handleSubmit1;', scope);
    vm.runInContext(strip(setup) + '\nglobalThis.page = { startAssessment, assessmentId, joining, problem, entryError, config };', scope);
    return { scope, calls, errors, notifications, subscriptions, navigation,
        setTransport: callback => { transport = callback; },
        reload: async () => { cleanup?.(); await watcher([], [], callback => { cleanup = callback; }); } };
}

async function formPayload(call) {
    return { url: call.url, language: call.data.get('language'), key: call.data.get('idempotencyKey'),
        filename: call.data.get('file').name, code: await call.data.get('file').text(), pid: call.data.get('pid') };
}

test('enter assessment; actual handler suppresses clicks and creates fresh keys after acceptance', async () => {
    const env = environment(undefined);
    env.scope.route.query = {};
    env.scope.page.assessmentId.value = '77';
    let open;
    env.setTransport(call => call.url === '/assessments/77/attempts'
        ? new Promise(resolve => { open = resolve; }) : Promise.resolve(ok({ attemptId: 200, problemId: 10, status: 'STARTED' })));
    const starts = [env.scope.page.startAssessment(), env.scope.page.startAssessment()];
    assert.equal(env.calls.length, 1);
    assert.equal(env.scope.page.joining.value, true);
    open(ok(200));
    await Promise.all(starts);
    assert.deepEqual(env.navigation, [{ path: '/problem/10', query: { attemptId: '200' } }]);
    assert.equal(env.calls[1].url, '/assessments/attempts/200');
    env.scope.route.query = { attemptId: '200' };
    await env.reload();
    assert.equal(env.scope.page.problem.value.id, 10);
    assert.equal(env.scope.page.config.value.language, 'java');
    env.calls.length = 0;
    let accept;
    env.setTransport(() => new Promise(resolve => { accept = resolve; }));
    const requests = [env.scope.submit(false), env.scope.submit(false), env.scope.submit(false)];
    assert.equal(env.calls.length, 1);
    assert.equal(env.scope.props.submission.busy, true);
    const first = await formPayload(env.calls[0]);
    assert.equal(first.url, '/sandbox/attempts/200/validate');
    assert.match(first.key, /^[0-9a-f]{32}$/);
    assert.equal(first.pid, null);
    assert.equal(first.filename, 'Main.java');
    accept(ok(42));
    await Promise.all(requests);
    assert.equal(env.scope.props.submission.busy, false);
    assert.equal(env.scope.props.submission.pending, null);
    assert.deepEqual(env.subscriptions, [42]);
    env.setTransport(async () => ok(43));
    await env.scope.submit(false); // Same code is still an intentional new submission.
    env.scope.code.value = 'synthetic V2';
    await env.scope.submit(false);
    const same = await formPayload(env.calls[1]), changed = await formPayload(env.calls[2]);
    assert.equal(same.code, first.code);
    assert.equal(changed.code, 'synthetic V2');
    assert.equal(new Set([first.key, same.key, changed.key]).size, 3);
    // Practice keeps its endpoint and gains only request-in-flight suppression.
    env.calls.length = 0;
    env.scope.route.query = {};
    env.setTransport(() => new Promise(resolve => { accept = resolve; }));
    const practice = [env.scope.submit(false), env.scope.submit(false)];
    assert.equal(env.calls.length, 1);
    assert.equal(env.calls[0].url, '/sandbox/validate');
    assert.equal(env.calls[0].data.get('idempotencyKey'), null);
    accept(ok(44));
    await Promise.all(practice);
});

test('lost response; retry preserves code, language, attempt and key through editor changes', async () => {
    const env = environment();
    const accepted = new Map();
    let loseResponse = true;
    env.setTransport(async call => {
        const key = call.data.get('idempotencyKey');
        if (!accepted.has(key)) accepted.set(key, accepted.size + 42);
        if (loseResponse) { loseResponse = false; throw new Error('synthetic lost response'); }
        return ok(accepted.get(key));
    });
    await env.scope.submit(false);
    const original = await formPayload(env.calls[0]);
    assert.equal(env.scope.props.submission.busy, false);
    assert.ok(env.scope.props.submission.pending);
    assert.equal(Object.isFrozen(env.scope.props.submission.pending), true);
    env.scope.code.value = 'synthetic V2';
    env.scope.props.languageName = 'cpp';
    // The actual wrapper owns one session rather than recreating it in the language component.
    assert.match(source('components/CodeEditor/index.vue'), /const submission = reactive\(new SubmissionSession\(\)\)/);
    assert.match(source('components/CodeEditor/index.vue'), /v-bind="config\.editorType === 'problem' \? \{ submission \} : \{\}"/);
    vm.runInContext('{' + strip(handler) + '\nglobalThis.submit = handleSubmit1;}', env.scope);
    await env.scope.submit(true);
    assert.deepEqual(await formPayload(env.calls[1]), original);
    assert.equal(accepted.size, 1);
    assert.equal(env.scope.props.submission.submissionId, 42);
    assert.equal(env.scope.props.submission.pending, null);
    env.scope.props.languageName = 'java';
    await env.scope.submit(false);
    const next = await formPayload(env.calls[2]);
    assert.equal(next.code, 'synthetic V2');
    assert.notEqual(next.key, original.key);
    assert.equal(accepted.size, 2);
});

test('invalid or forbidden attempt and non-Java submissions cannot fall back to practice', async () => {
    const env = environment('invalid');
    await env.reload();
    assert.equal(env.scope.page.problem.value, undefined);
    assert.equal(env.scope.page.entryError.value, '考核链接无效');
    await env.scope.submit(false);
    assert.equal(env.calls.length, 0);
    env.scope.route.query.attemptId = ['200'];
    await env.scope.submit(false);
    assert.equal(env.calls.length, 0);
    env.scope.route.query.attemptId = '200';
    env.setTransport(async () => ({ data: { code: 403, msg: 'synthetic forbidden attempt', data: null } }));
    await env.reload();
    assert.equal(env.scope.page.problem.value, undefined);
    assert.equal(env.scope.page.entryError.value, 'synthetic forbidden attempt');
    assert.equal(env.calls[0].url, '/assessments/attempts/200');
    assert.match(pageSource, /<Editor v-if="problem && !entryError &&/);
    assert.match(editorSource, /:loading="submission\.busy"/);
    assert.match(editorSource, /:disabled="submission\.busy \|\| config\.submissionLocked/);
    env.calls.length = 0;
    env.scope.props.languageName = 'cpp';
    await env.scope.submit(false);
    assert.equal(env.calls.length, 0);
    assert.equal(env.errors.at(-1), '考核仅支持 Java');
    env.scope.props.languageName = 'java';
    env.setTransport(async () => ({ data: { code: 400, msg: 'synthetic expired attempt', data: null } }));
    await env.scope.submit(false);
    assert.equal(env.calls.length, 1);
    assert.equal(env.calls[0].url, '/sandbox/attempts/200/validate');
    assert.equal(env.scope.props.submission.busy, false);
    assert.ok(env.scope.props.submission.pending);
    assert.equal(env.errors.at(-1), 'synthetic expired attempt');
    assert.equal(env.subscriptions.length, 0);
});
