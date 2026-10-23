import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';
import test from 'node:test';

const source=readFileSync('upstream/D-OnlineJudge/DOJ-FE/src/stores/userStore/index.ts','utf8');
function environment(role=false, fail=false) {
    const profiles=[];
    const scope={Error,ref:value=>({value}),defineStore:(_name,factory)=>factory(),
        reqLogin:async()=>({data:{code:200,data:{userId:207,username:'synthetic',accessToken:'test-token',refreshToken:'test-refresh'}}}),
        reqUserInfo:async id=>{
            profiles.push({id,token:scope.store.userInfo.value?.accessToken});
            if(fail) throw new Error('synthetic profile unavailable');
            return {data:{code:200,data:{role,password:'PRIVATE_PROFILE_FIELD',email:'synthetic@example.invalid'}}};
        }};
    vm.createContext(scope);
    vm.runInContext(stripTypeScriptTypes(source.replace(/^import .*;\r?$/gm,'').replace(/^export /gm,''))
        +'\nglobalThis.store=useUserStore;',scope);
    return {store:scope.store,profiles};
}
test('actual login response without role hydrates admin or candidate from own profile and stores only role',async()=>{
    for(const role of [false,true]) {
        const env=environment(role);
        await env.store.getUserInfo({username:'synthetic',password:'synthetic'});
        assert.equal(env.store.userInfo.value.role,role);
        assert.deepEqual(env.profiles,[{id:207,token:'test-token'}]);
        assert.ok(!JSON.stringify(env.store.userInfo.value).includes('PRIVATE_PROFILE_FIELD'));
        assert.equal(env.store.userInfo.value.email,undefined);
    }
});
test('profile failure rejects login completion and clears provisional auth state',async()=>{
    const env=environment(false,true);
    await assert.rejects(()=>env.store.getUserInfo({username:'synthetic',password:'synthetic'}),/profile unavailable/);
    assert.equal(env.store.userInfo.value,undefined);
});
