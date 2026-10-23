package com.decade.doj.submission.websocket;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson.JSON;
import com.decade.doj.common.config.custom.JwtTool;
import com.decade.doj.submission.config.WebSocketConfig;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.service.ISubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.websocket.CloseReason;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SubmissionWebSocketSecurityTest {
    private final ISubmissionService submissions = mock(ISubmissionService.class);
    private final GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext());
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(SubmissionWSServer.class);
    private SubmissionWSServer endpoint;
    private JwtTool tokens;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        tokens = new JwtTool(generator.generateKeyPair());
        context.getBeanFactory().registerSingleton("jwtTool", tokens);
        context.getBeanFactory().registerSingleton("submissionService", submissions);
        new AnnotatedBeanDefinitionReader(context).register(SubmissionWSServer.class);
        context.refresh();
        endpoint = context.getBean(SubmissionWSServer.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void cleanUp() {
        ((Map<?, ?>) ReflectionTestUtils.getField(SubmissionWSServer.class, "ONLINE_SESSIONS")).clear();
        logger.detachAppender(logs);
        logs.stop();
        context.close();
    }

    @Test
    void aFreshInstanceRecoversAnAlreadyCompletedOwnedResultFromTheDatabase() throws Exception {
        Submission own = new Submission();
        own.setId(401L); own.setUserId(7L); own.setStatus("Accepted"); own.setJudgeStage("VERDICT_READY");
        when(submissions.getById(401L)).thenReturn(own);
        Session session = session();
        endpoint.onMessage(frame(401L, tokens.createToken(7L, Duration.ofMinutes(5))), session);
        verify(session.getBasicRemote()).sendText(argThat(text ->
                JSON.parseObject(text).getLongValue("id") == 401L
                        && "Accepted".equals(JSON.parseObject(text).getString("status"))));
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(SubmissionWSServer.class, "ONLINE_SESSIONS")).isEmpty());
    }

    @Test
    void ownerReceivesOneResultAndClosedConnectionsAreRemoved() throws Exception {
        WebSocketConfig configuration = new WebSocketConfig();
        ServerEndpointConfig definition = configuration.submissionEndpoint(endpoint);
        assertEquals("/ws/submission", definition.getPath());
        assertSame(endpoint, definition.getConfigurator().getEndpointInstance(SubmissionWSServer.class));
        context.registerBean("submissionEndpoint", ServerEndpointConfig.class, () -> definition);
        ServerContainer container = mock(ServerContainer.class);
        context.getServletContext().setAttribute("javax.websocket.server.ServerContainer", container);
        ServerEndpointExporter exporter = configuration.serverEndpointExporter();
        exporter.setServerContainer(container);
        exporter.setApplicationContext(context);
        exporter.afterPropertiesSet();
        exporter.afterSingletonsInstantiated();
        verify(container).addEndpoint(definition);

        Submission own = new Submission();
        own.setId(100L);
        own.setUserId(7L);
        when(submissions.getById(100L)).thenReturn(own);
        Session session = session();
        String token = tokens.createToken(7L, Duration.ofMinutes(5));

        endpoint.onOpen(session);
        endpoint.onMessage(frame(100L, token), session);
        String result = "{\"id\":100,\"status\":\"Accepted\",\"code\":\"synthetic owner source\"}";
        SubmissionWSServer.sendMessage(100L, result);
        SubmissionWSServer.sendMessage(100L, result);
        verify(session.getBasicRemote(), times(1)).sendText(result);
        verify(session, never()).close(any(CloseReason.class));

        endpoint.onMessage(frame(100L, token), session);
        session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "test close"));
        endpoint.onClose(session);
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(SubmissionWSServer.class, "ONLINE_SESSIONS")).isEmpty());
        assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains(token)));
    }

    @Test
    void anonymousInvalidExpiredAndOtherOwnerSubscriptionsCannotReceiveOrLogData() throws Exception {
        Submission other = new Submission();
        other.setId(200L);
        other.setUserId(8L);
        Submission unowned = new Submission();
        unowned.setId(201L);
        when(submissions.getById(200L)).thenReturn(other);
        when(submissions.getById(201L)).thenReturn(unowned);
        String token = tokens.createToken(7L, Duration.ofMinutes(5));
        String expired = tokens.createToken(7L, Duration.ofMinutes(-10));
        String invalid = "synthetic-invalid-token";
        String malformed = "synthetic-sensitive-frame{";
        var checks = new ArrayList<org.junit.jupiter.api.function.Executable>();
        for (String message : new String[]{
                "{\"submissionId\":200,\"userId\":8}", frame(200L, invalid), frame(200L, expired),
                frame(200L, token), frame(201L, token), frame(202L, token), malformed,
                frame(200L, tokens.createToken(0L, Duration.ofMinutes(5)))}) {
            checks.add(() -> {
                Session session = session();
                endpoint.onMessage(message, session);
                SubmissionWSServer.sendMessage(200L, "synthetic other owner's source and result");
                SubmissionWSServer.sendMessage(201L, "synthetic unowned result");
                SubmissionWSServer.sendMessage(202L, "synthetic unknown result");
                verify(session.getBasicRemote(), never()).sendText(anyString());
                verify(session).close(argThat(reason -> reason.getCloseCode() == CloseReason.CloseCodes.VIOLATED_POLICY));
            });
        }
        checks.add(() -> {
            Session session = session();
            Submission own = new Submission();
            own.setId(300L);
            own.setUserId(7L);
            when(submissions.getById(300L)).thenReturn(own);
            endpoint.onMessage(frame(300L, token), session);
            endpoint.onMessage(frame(200L, token), session);
            SubmissionWSServer.sendMessage(300L, "synthetic formerly authorized result");
            verify(session.getBasicRemote(), never()).sendText(anyString());
        });
        checks.add(() -> {
            for (String secret : new String[]{token, expired, invalid, malformed}) {
                assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains(secret)));
            }
        });
        assertAll("denied subscriptions and credential confidentiality", checks);
    }

    private String frame(Long submissionId, String token) {
        return JSON.toJSONString(Map.of("submissionId", submissionId, "authorization", token));
    }

    private Session session() throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        AtomicBoolean open = new AtomicBoolean(true);
        when(session.getBasicRemote()).thenReturn(remote);
        when(session.isOpen()).thenAnswer(invocation -> open.get());
        when(session.getId()).thenReturn("synthetic-session");
        doAnswer(invocation -> { open.set(false); return null; }).when(session).close(any(CloseReason.class));
        return session;
    }
}
