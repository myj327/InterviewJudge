import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const source = file => readFileSync(path.join(root, 'upstream/D-OnlineJudge/DOJ-FE/src', file), 'utf8');
const strip = text => stripTypeScriptTypes(text).replace(/^export\s+/gm, '');
const editor = source('components/CodeEditor/problemEditor.vue');
const handler = editor.slice(editor.indexOf('const handleSubmit1 ='), editor.indexOf('const handleSubmit ='));
const extensions = editor.slice(editor.indexOf('const getLanguageExtension ='), editor.indexOf('// 监听 props 变化'));

function environment() {
    const calls = [], errors = [];
    let transport = async () => ({ data: { code: 200, data: 42 } });
    const scope = { Blob, FormData, crypto: webcrypto, code: { value: 'synthetic V1' },
        props: { languageName: 'java', config: { disabled: false, submissionLocked: false } }, route: { params: { id: '10' }, query: { attemptId: '200' } },
        ElMessage: { error: text => errors.push(text), info: () => {} },
        useWebSocket: () => ({ subscribeToSubmission: () => {} }),
        reqAttemptValidate: (id, data) => { calls.push({ id, data }); return transport(); },
        reqProblemValidate: () => { throw new Error('Unexpected practice fallback'); } };
    vm.createContext(scope);
    vm.runInContext(strip(source('utils/submissionSession.ts')) + '\nglobalThis.Session = SubmissionSession;', scope);
    scope.props.submission = new scope.Session();
    vm.runInContext(strip(extensions) + '\n' + strip(handler) + '\nglobalThis.submit = handleSubmit1; globalThis.repeat = blockRepeatedKey;', scope);
    return { scope, calls, errors, setTransport: callback => { transport = callback; } };
}
async function payload(call) {
    return { attempt: call.id, code: await call.data.get('file').text(), lang: call.data.get('language'), key: call.data.get('idempotencyKey') };
}

test('unconfirmed main-button retries preserve snapshot and only acceptance enables a new key', async () => {
    const env = environment();
    env.setTransport(async () => { throw new Error('response lost'); });
    await env.scope.submit(false);
    const first = await payload(env.calls[0]);
    env.scope.code.value = 'synthetic V2'; env.scope.props.languageName = 'cpp';
    env.setTransport(async () => ({ data: { code: 200, data: null } }));
    await env.scope.submit(false);
    assert.deepEqual(await payload(env.calls[1]), first);
    env.setTransport(async () => ({ data: { code: 403, msg: 'synthetic rejection', data: null } }));
    await env.scope.submit(false);
    assert.deepEqual(await payload(env.calls[2]), first);
    assert.ok(env.scope.props.submission.pending);
    env.setTransport(async () => ({ data: { code: 200, data: 42 } }));
    await env.scope.submit(false);
    assert.deepEqual(await payload(env.calls[3]), first);
    assert.equal(env.scope.props.submission.pending, null);
    env.scope.props.languageName = 'java';
    await env.scope.submit(false);
    const next = await payload(env.calls[4]);
    assert.equal(next.code, 'synthetic V2'); assert.notEqual(next.key, first.key);
});

test('in-flight clicks and browser multiclicks after a fast receipt create only one request', async () => {
    const env = environment(); let done;
    env.setTransport(() => new Promise(resolve => { done = resolve; }));
    const inflight = [env.scope.submit(false, { detail: 1 }), env.scope.submit(false, { detail: 1 })];
    assert.equal(env.calls.length, 1);
    done({ data: { code: 200, data: 42 } }); await Promise.all(inflight);
    env.setTransport(async () => ({ data: { code: 200, data: 43 } }));
    await env.scope.submit(false, { detail: 2 }); await env.scope.submit(false, { detail: 3 });
    assert.equal(env.calls.length, 1);
    assert.match(editor, /@keydown="blockRepeatedKey"/);
    let prevented = 0;
    env.scope.repeat({repeat:true,key:'Enter',preventDefault:()=>prevented++});
    env.scope.repeat({repeat:false,key:'Enter',preventDefault:()=>prevented++});
    assert.equal(prevented,1);
    await env.scope.submit(false, { detail: 1 }); assert.equal(env.calls.length, 2);
});

test('empty, non-Java and changed attempt cannot create or replace an unresolved request', async () => {
    const env = environment(); env.scope.code.value = '  ';
    await env.scope.submit(false); assert.equal(env.calls.length, 0);
    env.scope.code.value = 'synthetic V1'; env.scope.props.languageName = 'cpp';
    await env.scope.submit(false); assert.equal(env.calls.length, 0);
    env.scope.props.languageName = 'java';
    env.setTransport(async () => { throw new Error('response lost'); }); await env.scope.submit(false);
    const original = env.scope.props.submission.pending;
    env.scope.route.query.attemptId = '201';
    await env.scope.submit(false); assert.equal(env.calls.length, 1);
    assert.equal(env.scope.props.submission.pending, original);
});
