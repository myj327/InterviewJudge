package com.decade.doj.submission.websocket;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.config.custom.JwtTool;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.service.ISubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionWSServer {

    private static final Map<Long, Session> ONLINE_SESSIONS = new ConcurrentHashMap<>();
    private final JwtTool jwtTool;
    private final ISubmissionService submissionService;

    @OnOpen
    public void onOpen(Session session) {
        log.info("WebSocket 连接已建立: {}", session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            Map<String, Object> data = JSON.parseObject(message);
            Long userId = jwtTool.parseToken((String) data.get("authorization"));
            if (userId == null || userId <= 0) {
                throw new UnauthorizedException("Subscription denied");
            }
            Long submissionId = Long.valueOf(data.get("submissionId").toString());
            Submission submission = submissionService.getById(submissionId);
            if (submission == null || !userId.equals(submission.getUserId())) {
                throw new UnauthorizedException("Subscription denied");
            }
            ONLINE_SESSIONS.put(submissionId, session);
            if (java.util.Set.of("COMPILE_FAILED", "SMOKE_FAILED", "VERDICT_READY", "INFRA_FAILED")
                    .contains(submission.getJudgeStage() == null ? "" : submission.getJudgeStage()))
                sendMessage(submissionId, JSON.toJSONString(submission));
            log.info("submissionId: {} 已订阅 WebSocket 通知", submissionId);
        } catch (Exception e) {
            ONLINE_SESSIONS.values().removeIf(current -> current == session);
            log.warn("拒绝 WebSocket 订阅: {}", session.getId());
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Subscription denied"));
            } catch (IOException closeError) {
                log.warn("关闭 WebSocket 连接失败: {}", session.getId());
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        // 清理无效的 session
        ONLINE_SESSIONS.values().removeIf(s -> s == session || !s.isOpen());
        log.info("WebSocket 连接已关闭: {}", session.getId());
    }

    public static void sendMessage(Long submissionId, String message) {
        Session session = ONLINE_SESSIONS.get(submissionId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                log.info("成功向 submissionId: {} 推送消息", submissionId);
                // 推送成功后可以移除，避免重复推送
                ONLINE_SESSIONS.remove(submissionId, session);
            } catch (IOException e) {
                log.error("向 submissionId: {} 推送消息失败", submissionId, e);
            }
        }
    }
}
