package com.decade.doj.submission.review;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.dto.ReviewReport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompatibleReviewProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOnlyEvidenceWhitelistAndAppliesReportMetadata() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, response("summary", "strength", "risk", "suggestion"));
        });
        OpenAiCompatibleReviewProvider provider = provider();

        ReviewReport report = provider.review(evidence());

        JSONObject request = JSON.parseObject(requestBody.get());
        JSONObject outboundEvidence = JSON.parseObject(request.getJSONArray("messages").getJSONObject(1).getString("content"));
        assertEquals(Set.of("schemaVersion", "submissionId", "language", "sourceCode", "verdict", "judgeStage",
            "exitValue", "timeSeconds", "memoryKb", "problemDescription", "inputStyle", "outputStyle", "hint"),
            outboundEvidence.keySet());
        assertFalse(outboundEvidence.containsKey("userId"));
        assertFalse(outboundEvidence.containsKey("testData"));
        assertEquals("deepseek-flash", request.getString("model"));
        assertEquals("disabled", request.getJSONObject("thinking").getString("type"));
        assertFalse(request.getBooleanValue("stream"));
        assertEquals(400, request.getIntValue("max_tokens"));
        assertEquals("json_object", request.getJSONObject("response_format").getString("type"));
        assertEquals("1", report.getSchemaVersion());
        assertEquals("r1-v1", report.getPromptVersion());
        assertEquals("deepseek-flash", report.getModel());
        assertEquals(91L, report.getSubmissionId());
        assertEquals("SUCCEEDED", report.getStatus());
    }

    @Test
    void rejectsInvalidProviderJson() throws Exception {
        startServer(exchange -> respond(exchange, "not-json"));

        assertThrows(RuntimeException.class, () -> provider().review(evidence()));
    }

    @Test
    void usesRealReadTimeoutAndActivatesOnlyOneProvider() throws Exception {
        startServer(exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        OpenAiCompatibleReviewProvider limited = new OpenAiCompatibleReviewProvider(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", 40, 120, "synthetic-key");
        assertThrows(org.springframework.web.client.ResourceAccessException.class, () -> limited.review(evidence()));
        org.springframework.boot.test.context.runner.ApplicationContextRunner context =
                new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                        .withUserConfiguration(MockReviewProvider.class, OpenAiCompatibleReviewProvider.class);
        context.run(app -> assertEquals(1, app.getBeansOfType(ReviewProvider.class).size()));
        context.withPropertyValues("doj.review.provider=openai-compatible",
                "doj.review.openai-compatible.base-url=http://127.0.0.1:9",
                "doj.review.openai-compatible.model=test-model",
                "doj.review.openai-compatible.timeout-ms=40",
                "doj.review.openai-compatible.max-output-tokens=120",
                "doj.review.openai-compatible.api-key=synthetic-key")
                .run(app -> {
                    assertEquals(1, app.getBeansOfType(ReviewProvider.class).size());
                    assertEquals(OpenAiCompatibleReviewProvider.class, app.getBean(ReviewProvider.class).getClass());
                });
    }

    private OpenAiCompatibleReviewProvider provider() {
        return new OpenAiCompatibleReviewProvider(new RestTemplate(), "http://127.0.0.1:" + server.getAddress().getPort(),
            "deepseek-flash", 400, "test-key");
    }

    private ReviewEvidence evidence() {
        return new ReviewEvidence()
            .setSchemaVersion("1")
            .setSubmissionId(91L)
            .setLanguage("java")
            .setSourceCode("synthetic")
            .setVerdict("Accepted")
            .setJudgeStage("VERDICT_READY")
            .setExitValue(10)
            .setTimeSeconds(0.1)
            .setMemoryKb(128L)
            .setProblemDescription("synthetic")
            .setInputStyle("input")
            .setOutputStyle("output")
            .setHint("hint");
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String response(String summary, String strength, String risk, String suggestion) {
        String content = JSON.toJSONString(Map.of("summary", summary, "strengths", List.of(strength), "risks", List.of(risk),
            "suggestions", List.of(suggestion)));
        return JSON.toJSONString(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
    }
}
