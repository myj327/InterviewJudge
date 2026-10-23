import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
import test from 'node:test';
const source=file=>readFileSync('upstream/D-OnlineJudge/DOJ-FE/src/'+file,'utf8');
function panel(){
    let response=async()=>({status:'PENDING',summary:'waiting'});
    const scope={Error,props:{submissionId:91},ref:value=>({value}),watch:()=>{},useUserStore:()=>({}),submissionReview:id=>response(id)};
    vm.createContext(scope);
    const script=source('components/SubmissionReviewPanel.vue').split('<script setup lang="ts">')[1].split('</script>')[0]
        .replace(/^import .*;$/gm,'').replace(/^const props = .*;$/m,'');
    vm.runInContext(stripTypeScriptTypes(script)+'\nglobalThis.state={loadReport,report,loading,error,stateLabel};',scope);
    return {scope,state:scope.state,setResponse:callback=>response=callback};
}
test('all report states are readable and network errors clear loading',async()=>{
    const env=panel();for(const state of ['PENDING','RUNNING','FAILED','SKIPPED','SUCCEEDED']){
        env.setResponse(async()=>({status:state}));await env.state.loadReport();assert.equal(env.state.report.value.status,state);assert.notEqual(env.state.stateLabel(state),state);
    }
    env.setResponse(async()=>{throw new Error('synthetic unavailable');});await env.state.loadReport();assert.equal(env.state.loading.value,false);assert.match(env.state.error.value,/unavailable/);
    assert.match(source('components/SubmissionReviewPanel.vue'),/mock 示例报告，未分析源码/);assert.doesNotMatch(source('components/SubmissionReviewPanel.vue'),/v-html/);
});
test('late response from another submission cannot replace current report',async()=>{
    const env=panel();let finish;
    env.setResponse(id=>id===91?new Promise(resolve=>finish=resolve):Promise.resolve({submissionId:id,status:'SUCCEEDED'}));
    const first=env.state.loadReport();env.scope.props.submissionId=92;await env.state.loadReport();finish({submissionId:91,status:'SUCCEEDED'});await first;
    assert.equal(env.state.report.value.submissionId,92);
});
test('metadata outage still displays accepted record with unknown memory',async()=>{
    const scope={Promise,Date,pageQueryForm:{value:{}},searchProblemName:{},searchUserId:{},searchLanguage:{},searchStatus:{},loading:{value:false},
        total:{value:0},pages:{value:0},tableData:{value:[]},
        reqSubmissionPageList:async()=>({data:{data:{total:1,pages:1,list:[{id:91,attemptId:10,problemId:20,userId:7,status:'Accepted',time:0.12,memory:-1,submitTime:'2026-10-10T10:00:00'}]}}}),
        reqProblemDetail:async()=>{throw new Error('offline');},reqUserInfo:async()=>{throw new Error('offline');}};
    vm.createContext(scope);const page=source('views/Status/index.vue');
    vm.runInContext(stripTypeScriptTypes(page.slice(page.indexOf('const getStatusList ='),page.indexOf('onMounted(()')))+'\nglobalThis.load=getStatusList;',scope);
    await scope.load();assert.equal(scope.tableData.value[0].id,91);assert.equal(scope.tableData.value[0].problemName,'20');assert.equal(scope.tableData.value[0].attemptId,10);
    assert.match(page,/memory < 0 \? '未测量'/);assert.match(page,/time \* 1000/);
});
