package com.decade.doj.sandbox.controller;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.config.global.CommonExceptionConfig;
import com.decade.doj.common.config.properties.ResourceProperties;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.sandbox.service.ISandboxService;
import feign.Feign;
import feign.Response;
import feign.Retryer;
import feign.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SandboxUpstreamStatusTest {
    private static final String PRIVATE_BODY = "synthetic-downstream-source-and-token";
    private final AtomicInteger receiptStatus = new AtomicInteger(409);
    private final AtomicInteger problemStatus = new AtomicInteger(400);
    private final AtomicInteger problemCalls = new AtomicInteger();
    private String receiptData = "91";
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        SubmissionClient submissions = client(SubmissionClient.class, false);
        ProblemClient problems = client(ProblemClient.class, true);
        SandboxController controller = new SandboxController(new ResourceProperties(),
                mock(ISandboxService.class), submissions, problems, mock(StringRedisTemplate.class));
        ReflectionTestUtils.setField(controller, "internalToken", "synthetic-service-token");
        UserContext.setCurrentUser(7L);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CommonExceptionConfig()).build();
    }

    @AfterEach
    void clear() { UserContext.clear(); }

    private <T> T client(Class<T> type, boolean problem) {
        return Feign.builder().contract(new SpringMvcContract()).retryer(Retryer.NEVER_RETRY)
                .encoder((value, ignored, template) -> template.body(JSON.toJSONString(value)))
                .decoder((response, resultType) -> JSON.parseObject(
                        Util.toString(response.body().asReader(StandardCharsets.UTF_8)), resultType))
                .client((request, options) -> {
                    if (problem) problemCalls.incrementAndGet();
                    int status = problem ? problemStatus.get() : receiptStatus.get();
                    String body = status == 200 ? "{\"code\":200,\"data\":" + receiptData + "}"
                            : "{\"code\":" + status + ",\"msg\":\"" + PRIVATE_BODY + "\"}";
                    return Response.builder().request(request).status(status).reason("synthetic")
                            .headers(Map.of("Content-Type", List.of("application/json")))
                            .body(body, StandardCharsets.UTF_8).build();
                }).target(type, "http://synthetic.invalid");
    }

    private org.springframework.test.web.servlet.ResultActions submit() throws Exception {
        return mvc.perform(multipart("/sandbox/attempts/10/validate")
                .file(new MockMultipartFile("file", "Main.java", "text/plain", "class Main {}".getBytes(StandardCharsets.UTF_8)))
                .param("language", "java").param("idempotencyKey", "synthetic-same-key"));
    }

    @Test
    void realFeignConflictRetains409AndDoesNotExposeTheDownstreamBody() throws Exception {
        String body = submit().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(PRIVATE_BODY));
        assertEquals(0, problemCalls.get());
    }

    @Test
    void successfulReceiptStillReturnsOriginalIdWithoutResolvingTheAttempt() throws Exception {
        receiptStatus.set(200);
        submit().andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(91));
        assertEquals(0, problemCalls.get());
    }

    @Test
    void expiredFinishedForeignAndUnavailableAttemptsKeepSafeStatuses() throws Exception {
        receiptStatus.set(200);
        receiptData = "null";
        for (int upstream : new int[]{400, 400, 403, 503}) {
            problemStatus.set(upstream);
            String body = submit().andExpect(status().is(upstream)).andExpect(jsonPath("$.code").value(upstream))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(PRIVATE_BODY));
        }
        assertEquals(4, problemCalls.get());
    }
}
