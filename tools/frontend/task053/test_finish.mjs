import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
import test from 'node:test';
const source=readFileSync('upstream/D-OnlineJudge/DOJ-FE/src/views/Problem/detail/index.vue','utf8');
function setup(){
    let confirm=async()=>{},submit=async()=>{};const calls=[],errors=[];
    const scope={Error,config:{value:{disabled:false,submissionLocked:false}},route:{query:{attemptId:'100'}},finishing:{value:false},submissionBlocked:{value:false},attemptStatus:{value:'STARTED'},
        ElMessage:{error:msg=>errors.push(msg),success:()=>{}},ElMessageBox:{confirm:()=>confirm()},
        finishAttempt:id=>{calls.push(id);return submit();},attemptView:async()=>({status:'SUBMITTED'})};
    vm.createContext(scope);vm.runInContext(stripTypeScriptTypes(source.slice(source.indexOf('async function finishAssessment'),source.indexOf('const startAssessment')))+'\nglobalThis.finish=finishAssessment;',scope);
    return {scope,calls,errors,setConfirm:callback=>confirm=callback,setSubmit:callback=>submit=callback};
}
test('confirmed finish closes attempt while cancellation sends nothing',async()=>{
    const env=setup();env.setConfirm(async()=>{throw 'cancel';});await env.scope.finish();assert.equal(env.calls.length,0);assert.equal(env.errors.length,0);
    assert.equal(env.scope.config.value.submissionLocked,false);
    env.setConfirm(async()=>{});await env.scope.finish();assert.deepEqual(env.calls,['100']);assert.equal(env.scope.attemptStatus.value,'SUBMITTED');
});
test('in flight or unconfirmed submission cannot be finished',async()=>{
    const env=setup();env.scope.submissionBlocked.value=true;await env.scope.finish();assert.equal(env.calls.length,0);assert.equal(env.errors.length,1);
    assert.match(source,/submission\.busy \|\| editorInstance\.value\?\.submission\.pending/);
    env.scope.submissionBlocked.value=false;env.scope.attemptStatus.value='EXPIRED';await env.scope.finish();assert.equal(env.calls.length,0);
});
test('repeat finish is suppressed and transport failure releases state',async()=>{
    const env=setup();let complete;env.setSubmit(()=>new Promise(resolve=>complete=resolve));const first=env.scope.finish();
    await Promise.resolve();await env.scope.finish();assert.equal(env.calls.length,1);complete();await first;
    env.scope.attemptStatus.value='STARTED';env.setSubmit(async()=>{throw new Error('synthetic offline');});await env.scope.finish();
    assert.equal(env.scope.finishing.value,false);assert.match(env.errors[0],/offline/);await env.scope.finish({detail:2});assert.equal(env.calls.length,2);
    assert.equal(env.scope.config.value.submissionLocked,false);
});
