// 管理用户数据相关

import { defineStore } from 'pinia';
import { ref } from 'vue';
import { reqLogin, reqUserInfo } from '@/api/user';
import { loginForm, loginResponseData } from '@/api/user/type';

// 定义用户信息的接口类型
interface UserInfo {
    userId: number;
    username: string;
    accessToken: string;
    refreshToken: string;
    avatar?: string;
    role: boolean; // false = admin, true = user (based on backend logic)
}

export const useUserStore = defineStore('user', () => {
    const userInfo = ref<UserInfo | undefined>();

    const getUserInfo = async (form: loginForm) => {
        const res = await reqLogin(form);
        if (res.data.code !== 200) throw new Error('登录失败');
        userInfo.value = { ...res.data.data, role: true };
        try {
            const profile = await reqUserInfo(userInfo.value.userId);
            const role: unknown = profile.data.data.role;
            if (profile.data.code !== 200 || typeof role !== 'boolean')
                throw new Error('无法确认用户角色，请重新登录');
            userInfo.value.role = role;
        } catch (error) {
            userInfo.value = undefined;
            throw error;
        }
    };

    const clearUserInfo = () => {
        userInfo.value = undefined;
    };

    // 新增一个 action，只用于更新 accessToken
    const setAccessToken = (token: string) => {
        if (userInfo.value) {
            userInfo.value.accessToken = token;
        }
    };

    // 以对象的格式把state和action return
    return {
        userInfo,
        getUserInfo,
        clearUserInfo,
        setAccessToken, // 暴露新的 action
    };
}, {
    persist: true,
});
