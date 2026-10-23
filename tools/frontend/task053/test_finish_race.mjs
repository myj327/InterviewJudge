import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
import test from 'node:test';

const source=file=>readFileSync('upstream/D-OnlineJudge/DOJ-FE/src/'+file,'utf8');
const strip=text=>stripTypeScriptTypes(text).replace(/^export\s+/gm,'');
const pageSource=source('views/Problem/detail/index.vue'),editorSource=source('components/CodeEditor/problemEditor.vue');
function environment() {
    const calls=[];let resolveFinish,transport=async()=>({data:{code:200,data:42}});
    const route={params:{id:'10'},query:{attemptId:'200'},fullPath:'/problem/10?attemptId=200'};
    const scope={Error,Blob,FormData,crypto:webcrypto,route,ref:value=>({value}),computed:read=>({get value(){return read();}}),
        watch:()=>{},useRoute:()=>route,useRouter:()=>({}),useUserStore:()=>({userInfo:{userId:73}}),
        ElMessage:{error:()=>{},success:()=>{},info:()=>{}},ElMessageBox:{confirm:async()=>{}},
        finishAttempt:()=>{calls.push({type:'finish'});return new Promise(resolve=>resolveFinish=resolve);},
        attemptView:async()=>({status:'SUBMITTED'}),
        reqAttemptValidate:(id,form)=>{calls.push({type:'submit',id,form});return transport();},
        reqProblemValidate:()=>{throw new Error('Unexpected practice fallback');},useWebSocket:()=>({subscribeToSubmission:()=>{}})};
    vm.createContext(scope);
    const script=pageSource.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*;\r?$/gm,'');
    vm.runInContext(strip(script)+'\nglobalThis.page={config,problem,entryError,attemptStatus,finishing,editorInstance,submissionBlocked,finishAssessment};',scope);
    vm.runInContext(strip(source('utils/submissionSession.ts'))+'\nglobalThis.Session=SubmissionSession;',scope);
    scope.props={config:scope.page.config.value,languageName:'java',submission:new scope.Session()};
    scope.code={value:'synthetic V1'};scope.page.editorInstance.value={submission:scope.props.submission};
    scope.page.problem.value={id:10};scope.page.attemptStatus.value='STARTED';
    const extensions=editorSource.slice(editorSource.indexOf('const getLanguageExtension ='),editorSource.indexOf('// 监听 props 变化'));
    const handler=editorSource.slice(editorSource.indexOf('const handleSubmit1 ='),editorSource.indexOf('const handleSubmit ='));
    vm.runInContext(strip(extensions)+'\n'+strip(handler)+'\nglobalThis.submit=handleSubmit1;',scope);
    const condition=pageSource.match(/<Editor v-if="([^"]+)"/)[1];
    vm.runInContext('globalThis.mounted=()=>('+condition.replace(/\b(problem|entryError|attemptStatus|submissionBlocked)\b/g,'$1.value')+');',scope);
    return {scope,calls,setTransport:callback=>transport=callback,resolveFinish:()=>resolveFinish()};
}

test('finish locks both submit actions and an ended attempt retains its original recovery snapshot',async()=>{
    const env=environment();const closing=env.scope.page.finishAssessment();
    for(let turn=0;turn<20 && env.calls.length===0;turn++) await Promise.resolve();
    assert.equal(env.calls[0].type,'finish');assert.equal(env.scope.page.finishing.value,true);
    await env.scope.submit(false,{detail:1});await env.scope.submit(true,{detail:1});
    assert.equal(env.calls.length,1,'No submission or retry may start while finish is in flight');
    env.resolveFinish();await closing;assert.equal(env.scope.page.config.value.submissionLocked,false);
    assert.equal(env.scope.props.submission.pending,null);assert.equal(env.scope.mounted(),false);

    const recovery=environment();recovery.setTransport(async()=>{throw new Error('Synthetic accepted response lost');});
    await recovery.scope.submit(false);const pending=recovery.scope.props.submission.pending;
    assert.ok(pending);recovery.scope.code.value='synthetic V2';
    // Model the authoritative ended status while an already-admitted request is unconfirmed.
    recovery.scope.page.attemptStatus.value='SUBMITTED';recovery.scope.page.config.value.disabled=true;
    assert.equal(recovery.scope.mounted(),true,'The pending editor and its retry button must remain mounted');
    recovery.setTransport(async()=>({data:{code:200,data:42}}));await recovery.scope.submit(true);
    const form=recovery.calls[1].form;
    assert.equal(form.get('idempotencyKey'),pending.idempotencyKey);assert.equal(await form.get('file').text(),pending.code);
    assert.equal(recovery.scope.props.submission.submissionId,42);assert.equal(recovery.scope.mounted(),false);
    await recovery.scope.submit(false);assert.equal(recovery.calls.length,2,'An ended attempt cannot start a new submission');
});
