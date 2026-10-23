import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import vm from 'node:vm';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const source = readFileSync(path.join(root, 'upstream/D-OnlineJudge/DOJ-FE/src/utils/websocket.ts'), 'utf8');
const body = stripTypeScriptTypes(source).replace(/^import .*;?\r?\n/gm, '').replace(/^export /gm, '');
const finished = { id: 42, userId: 7, status: 'Accepted', exitValue: 10, judgeStage: 'VERDICT_READY' };

function environment() {
    const timers = new Map(), alerts = [], calls = [];
    const store = { userInfo: { userId: 7, accessToken: 'synthetic-only' } };
    let next = 0, query = async () => ({ data: { code: 200, data: { list: [finished] } } });
    class Socket {
        static OPEN = 1; static CONNECTING = 0;
        readyState = 1;
        constructor() { Socket.last = this; }
        send() {} close() { this.readyState = 3; this.onclose?.({}); }
    }
    const scope = { WebSocket: Socket, useUserStore: () => store, h: () => ({}), router: { push() {} },
        console: { log() {}, error() {} }, ElNotification: value => alerts.push(value),
        reqSubmissionPageList: async args => { calls.push(args); return query(); },
        setTimeout: callback => { const id = ++next; timers.set(id, callback); return id; },
        clearTimeout: id => timers.delete(id) };
    vm.createContext(scope);
    vm.runInContext(body + '\nglobalThis.subscribe = useWebSocket().subscribeToSubmission;', scope);
    const flush = () => new Promise(resolve => setImmediate(resolve));
    return { scope, store, alerts, calls, timers, Socket, flush,
        query(value) { query = value; },
        async tick() { const first = timers.entries().next().value; if (first) { timers.delete(first[0]); first[1](); } await flush(); } };
}

test('missed push and disconnected socket recover once from the owned submission query', async () => {
    const env = environment();
    env.query(async () => ({ data: { code: 200, data: { list: [{ id: 42, status: 'QUEUED', judgeStage: 'QUEUED' }] } } }));
    env.scope.subscribe(42); env.scope.subscribe(42); await env.flush();
    assert.equal(env.calls.length, 1, 'one in-flight query per submission');
    env.Socket.last.close();
    env.query(async () => ({ data: { code: 200, data: { list: [finished] } } }));
    await env.tick();
    assert.equal(env.alerts.length, 1);
    assert.equal(env.calls[0].submissionId, 42);
    env.Socket.last.onmessage?.({ data: JSON.stringify(finished) });
    assert.equal(env.alerts.length, 1, 'late push must not duplicate notification');
    assert.equal(env.timers.size, 0);
});

test('transient reads recover; switched or unauthorized accounts never display stale results', async () => {
    const env = environment();
    env.query(async () => { throw new Error('synthetic instance unavailable'); });
    env.scope.subscribe(42); await env.flush();
    assert.equal(env.alerts.length, 0);
    env.query(async () => ({ data: { code: 200, data: { list: [finished] } } }));
    await env.tick(); assert.equal(env.alerts.length, 1);

    let release;
    env.query(() => new Promise(resolve => { release = resolve; }));
    env.scope.subscribe(43); await env.flush();
    env.store.userInfo = { userId: 8, accessToken: 'synthetic-other' };
    release({ data: { code: 200, data: { list: [{ ...finished, id: 43 }] } } });
    await env.flush();
    assert.equal(env.alerts.length, 1); assert.equal(env.timers.size, 0);
    env.scope.subscribe(43);
    env.query(async () => ({ data: { code: 403 } }));
    release({ data: { code: 403 } }); await env.flush();
    assert.equal(env.alerts.length, 1); assert.equal(env.timers.size, 0);

    const replies = [];
    env.query(() => new Promise(resolve => replies.push(resolve)));
    env.scope.subscribe(44);
    env.store.userInfo = { userId: 9, accessToken: 'synthetic-new-account' };
    env.scope.subscribe(44);
    replies[0]({ data: { code: 403 } }); await env.flush();
    replies[1]({ data: { code: 200, data: { list: [{ ...finished, id: 44, userId: 9 }] } } });
    await env.flush();
    assert.equal(env.alerts.length, 2, 'old completion must not cancel the new account tracker');
    assert.equal(env.timers.size, 0);
});
