import { ElNotification } from 'element-plus';
import router from '@/router';
import { h } from 'vue';
import { useUserStore } from '@/stores/userStore';
import { reqSubmissionPageList } from '@/api/submission';

let socket: WebSocket | null = null;
type Tracking = { owner: string; timer?: ReturnType<typeof setTimeout> };
const pending = new Map<number, Tracking>();
const account = () => String(useUserStore().userInfo?.userId ?? '');

const forget = (id: number) => {
    clearTimeout(pending.get(id)?.timer);
    pending.delete(id);
};

const receiveResult = (result: any) => {
    const tracked = pending.get(result.id);
    if (!tracked) return;
    if (account() !== tracked.owner) { forget(result.id); return; }
    if (!['COMPILE_FAILED', 'SMOKE_FAILED', 'VERDICT_READY', 'INFRA_FAILED'].includes(result.judgeStage)) return;
    if (String(result.userId) !== tracked.owner) { forget(result.id); return; }
    forget(result.id);
    ElNotification({
        title: `提交 #${result.id} 已完成`, message: renderResultMessage(result),
        type: result.status === 'Accepted' ? 'success' : 'error', duration: 15000,
        onClick: () => router.push(`/status?submissionId=${result.id}`), position: 'bottom-right',
    });
};

const poll = async (id: number, tracked: Tracking) => {
    if (pending.get(id) !== tracked) return;
    if (account() !== tracked.owner) { forget(id); return; }
    try {
        const response = (await reqSubmissionPageList({ submissionId: id,
            pageNo: 1, pageSize: 1, isAsc: false, sortBy: 'submit_time' })).data;
        if (pending.get(id) !== tracked) return;
        if (account() !== tracked.owner) { forget(id); return; }
        if (response.code === 200) {
            const result = response.data.list.find(row => row.id === id);
            if (result) receiveResult(result); else forget(id);
        } else if (response.code >= 400 && response.code < 500 && response.code !== 429) forget(id);
    } catch (error: any) {
        const status = error.response?.status;
        if (pending.get(id) === tracked && status >= 400 && status < 500 && status !== 429) forget(id);
    } finally {
        if (pending.get(id) === tracked && account() !== tracked.owner) forget(id);
        if (pending.get(id) === tracked) tracked.timer = setTimeout(() => { void poll(id, tracked); }, 2000);
    }
};

const sendSubscription = (id: number) => {
    const accessToken = useUserStore().userInfo?.accessToken;
    if (accessToken && pending.get(id)?.owner === account() && socket?.readyState === WebSocket.OPEN)
        socket.send(JSON.stringify({ submissionId: id, authorization: accessToken }));
};

// 辅助函数：创建丰富的通知内容
const renderResultMessage = (result: any) => {
    const time = result.time ? `${(result.time * 1000).toFixed(2)} ms` : 'N/A';
    const memory = result.memory ? `${result.memory.toFixed(2)} KB` : 'N/A';

    return h('div', null, [
        h('p', { style: { margin: '0 0 5px 0' } }, [
            h('strong', null, '状态: '),
            h('span', { style: { fontWeight: 'bold', color: result.status === 'Accepted' ? '#67c23a' : '#f56c6c' } }, ` ${result.status}`)
        ]),
        h('p', { style: { margin: '0 0 5px 0' } }, [
            h('strong', null, '耗时: '),
            h('span', null, time)
        ]),
        h('p', { style: { margin: '0 0 5px 0' } }, [
            h('strong', null, '内存: '),
            h('span', null, memory)
        ]),
        result.message && result.status !== 'Accepted' ? h('div', null, [
            h('strong', null, '信息: '),
            h('pre', { style: { marginTop: '5px', padding: '5px', background: '#f5f5f5', border: '1px solid #e3e3e3', borderRadius: '4px', whiteSpace: 'pre-wrap', wordBreak: 'break-all' } }, result.message)
        ]) : null
    ]);
};

const connectWebSocket = () => {
    if (socket && socket.readyState === WebSocket.OPEN) {
        return;
    }
    if (socket && socket.readyState === WebSocket.CONNECTING) {
        return;
    }

    const wsUrl = 'ws://127.0.0.1:8084/ws/submission'; // 直接连接后端 WebSocket 地址

    socket = new WebSocket(wsUrl);

    socket.onopen = () => {
        pending.forEach((_, id) => sendSubscription(id));
    };

    socket.onmessage = (event) => {
        try {
            receiveResult(JSON.parse(event.data));
        } catch (e) {
            console.error('处理 WebSocket 消息失败:', e);
        }
    };

    socket.onclose = () => {
        socket = null;
    };

    socket.onerror = (error) => {
        console.error('WebSocket 发生错误:', error);
        // 可以在这里添加一些错误提示
    };
};

const subscribeToSubmission = (submissionId: number) => {
    if (!account() || !useUserStore().userInfo?.accessToken) return;
    if (pending.get(submissionId)?.owner === account()) return;
    forget(submissionId);
    const tracked = { owner: account() };
    pending.set(submissionId, tracked);
    void poll(submissionId, tracked);
    try { connectWebSocket(); sendSubscription(submissionId); }
    catch { /* The owned HTTP query continues when WebSocket is unavailable. */ }
};

export const useWebSocket = () => {
    // 确保只有一个 WebSocket 连接实例
    if (!socket) {
        connectWebSocket();
    }

    return {
        subscribeToSubmission
    };
};
