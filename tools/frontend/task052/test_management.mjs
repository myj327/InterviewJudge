import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
import test from 'node:test';
const file='upstream/D-OnlineJudge/DOJ-FE/src/views/Admin/Assessment/index.vue';
const source=readFileSync(file,'utf8');
function setup(){
    const calls=[];let create=async()=>30;
    const scope={Error,ref:value=>({value}),reactive:value=>value,onMounted:()=>{},MouseEvent:class{},
        useUserStore:()=>({userInfo:{role:false}}),useRouter:()=>({push:()=>{}}),
        createAssessment:body=>{calls.push(body);return create();},ownAssessments:async()=>[],
        publishAssessment:async id=>calls.push(id),assessmentAttempts:async()=>[]};
    vm.createContext(scope);
    const script=source.split('<script setup lang="ts">')[1].split('</script>')[0].replace(/^import .*;$/gm,'');
    vm.runInContext(stripTypeScriptTypes(script)+'\nglobalThis.actions={create,publish,load,showAttempts,draft,busy,error,notice,userStore};',scope);
    return {scope:scope.actions,calls,setCreate:callback=>create=callback};
}
test('invalid form cannot create and valid form reports created draft',async()=>{
    const env=setup();await env.scope.create();assert.equal(env.calls.length,0);assert.ok(env.scope.error.value);
    Object.assign(env.scope.draft,{title:' Synthetic ',problemId:20,startsAt:'2026-10-11T10:00:00',endsAt:'2026-10-11T11:00:00'});
    await env.scope.create();assert.equal(env.calls.length,1);assert.equal(env.calls[0].title,'Synthetic');assert.match(env.scope.notice.value,/#30/);
});
test('in flight repeat and non-admin do not create; failures release busy',async()=>{
    const env=setup();Object.assign(env.scope.draft,{title:'Synthetic',problemId:20,startsAt:'2026-10-11T10:00:00',endsAt:'2026-10-11T11:00:00'});
    let finish;env.setCreate(()=>new Promise(resolve=>finish=resolve));const sending=env.scope.create();await env.scope.create();assert.equal(env.calls.length,1);
    finish(30);await sending;env.scope.userStore.userInfo.role=true;await env.scope.create();assert.equal(env.calls.length,1);
    env.scope.userStore.userInfo.role=false;env.setCreate(async()=>{throw new Error('synthetic offline');});await env.scope.create();
    assert.equal(env.scope.busy.value,false);assert.match(env.scope.error.value,/offline/);
});
test('publish only updates after success and browser double click is ignored',async()=>{
    const env=setup();const row={id:30,status:'DRAFT'};await env.scope.publish(row,{detail:2});assert.equal(env.calls.length,0);
    await env.scope.publish(row,{detail:1});assert.equal(row.status,'PUBLISHED');assert.deepEqual(env.calls,[30]);
});
