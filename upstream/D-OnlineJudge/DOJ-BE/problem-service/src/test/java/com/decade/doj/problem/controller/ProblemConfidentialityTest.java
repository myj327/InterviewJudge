package com.decade.doj.problem.controller;

import com.decade.doj.common.domain.PageDTO;
import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.config.custom.DefaultFeignConfig;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.problem.domain.dto.ProblemPageQueryDTO;
import com.decade.doj.problem.domain.po.Problem;
import com.decade.doj.problem.service.IProblemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Logger;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClientSpecification;
import org.springframework.cloud.openfeign.FeignContext;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProblemConfidentialityTest {
    @Test
    void publicDetailListAndPageDoNotExposeOrMutateCachedHiddenData() throws Exception {
        Problem cached = new Problem().setId(20L).setName("synthetic problem")
                .setInputSample(List.of("public input")).setOutputSample(List.of("public answer"))
                .setTestData("synthetic-hidden-input").setTestAns("synthetic-hidden-answer");
        IProblemService service = mock(IProblemService.class);
        when(service.getById(20L)).thenReturn(cached);
        when(service.list()).thenReturn(List.of(cached));
        PageDTO<Problem> cachedPage = PageDTO.fullPage(1L, 1L, List.of(cached));
        when(service.pageQuery(any(ProblemPageQueryDTO.class))).thenReturn(cachedPage);
        ProblemController controller = new ProblemController(service);
        ObjectMapper mapper = new ObjectMapper();

        for (Object response : List.of(controller.getProblemById(20L), controller.list(),
                controller.page(new ProblemPageQueryDTO()))) {
            String json = mapper.writeValueAsString(response);
            assertAll(() -> assertFalse(json.contains("synthetic-hidden-input")),
                    () -> assertFalse(json.contains("synthetic-hidden-answer")),
                    () -> assertTrue(json.contains("public input")),
                    () -> assertTrue(json.contains("public answer")));
        }
        assertEquals("synthetic-hidden-input", cached.getTestData());
        assertEquals("synthetic-hidden-answer", cached.getTestAns());
        assertSame(cached, cachedPage.getList().get(0));
    }

    @Test
    void internalHiddenDataRequiresAConfiguredMatchingCredential() {
        IProblemService service = mock(IProblemService.class);
        Problem problem = new Problem().setTestData("synthetic-hidden-input").setTestAns("synthetic-hidden-answer");
        when(service.getById(20L)).thenReturn(problem);
        ProblemController controller = new ProblemController(service);
        ReflectionTestUtils.setField(controller, "internalToken", "synthetic-service-token");
        for (String supplied : new String[]{null, "", "wrong-token"}) {
            assertThrows(ForbiddenException.class, () -> controller.getJudgingProblemById(20L, supplied));
        }
        verifyNoInteractions(service);
        assertSame(problem, controller.getJudgingProblemById(20L, "synthetic-service-token").getData());
        verify(service).getById(20L);
        clearInvocations(service);
        ReflectionTestUtils.setField(controller, "internalToken", "");
        assertThrows(ForbiddenException.class,
                () -> controller.getJudgingProblemById(20L, "synthetic-service-token"));
        verifyNoInteractions(service);

        try (AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext()) {
            parent.refresh();
            FeignContext context = new FeignContext();
            context.setApplicationContext(parent);
            context.setConfigurations(List.of(
                    new FeignClientSpecification("default.security-test", new Class<?>[]{DefaultFeignConfig.class}),
                    new FeignClientSpecification("problem-service", new Class<?>[]{ProblemClient.PrivateDataClientConfiguration.class})));
            try {
                List<String> logs = new ArrayList<>();
                ProblemClient client = Feign.builder().contract(new SpringMvcContract())
                        .logLevel(context.getInstance("problem-service", Logger.Level.class))
                        .logger(new Logger() {
                            @Override
                            protected void log(String configKey, String format, Object... args) {
                                logs.add(String.format(format, args));
                            }
                        })
                        .client((request, options) -> {
                            assertTrue(request.url().endsWith("/problem/internal/20"));
                            assertEquals(List.of("synthetic-service-token"), new ArrayList<>(
                                    request.headers().get("X-InterviewJudge-Internal-Token")));
                            return Response.builder().request(request).status(200).reason("OK")
                                    .body("{}", StandardCharsets.UTF_8).build();
                        })
                        .decoder((response, type) -> com.decade.doj.common.domain.R.ok(
                                new com.decade.doj.common.domain.po.Problem().setTestData("synthetic-hidden-input")))
                        .target(ProblemClient.class, "http://127.0.0.1:1");
                assertEquals("synthetic-hidden-input",
                        client.getJudgingProblemById(20L, "synthetic-service-token").getData().getTestData());
                assertTrue(logs.isEmpty(), "internal request and response must not be logged");
            } finally {
                context.destroy();
            }
        }
    }
}
