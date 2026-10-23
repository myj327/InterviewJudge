import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
import test from 'node:test';
test('local assessment dates are sent in UTC independently of browser timezone',async()=>{
    let body;
    class ShanghaiDate extends Date {
        constructor(value){super(typeof value==='string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value)?value+'+08:00':value);}
    }
    const scope={Date:ShanghaiDate,request:{post:async(path,data)=>{body=data;return {data:{code:200,data:10}};}}};
    vm.createContext(scope);
    const script=readFileSync('upstream/D-OnlineJudge/DOJ-FE/src/api/assessment/index.ts','utf8').replace(/^import .*;$/gm,'');
    vm.runInContext(stripTypeScriptTypes(script).replace(/^export\s+/gm,'')+'\nglobalThis.create=createAssessment;',scope);
    await scope.create({title:'Synthetic',problemId:20,startsAt:'2026-10-10T10:00:00',endsAt:'2026-10-10T11:00:00'});
    assert.equal(body.startsAt,'2026-10-10T02:00:00.000');assert.equal(body.endsAt,'2026-10-10T03:00:00.000');
});
