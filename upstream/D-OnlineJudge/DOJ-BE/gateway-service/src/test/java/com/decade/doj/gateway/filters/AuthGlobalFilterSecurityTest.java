package com.decade.doj.gateway.filters;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.decade.doj.common.config.custom.JwtTool;
import com.decade.doj.common.config.properties.JwtProperties;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.gateway.config.properties.AuthProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthGlobalFilterSecurityTest {
    @Test
    void authenticatesWithoutLoggingCredentialsOnSuccessOrFailure() {
        String token = "synthetic-login-token";
        String cookie = "synthetic-cookie-secret";
        String identityHeader = "synthetic-internal-identity-header";
        JwtTool jwt = mock(JwtTool.class);
        when(jwt.parseToken(token)).thenReturn(7L);
        when(jwt.parseToken("synthetic-invalid-token")).thenThrow(new UnauthorizedException("invalid"));
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey(identityHeader);
        properties.setAuthorization("Authorization");
        AuthProperties auth = new AuthProperties();
        auth.setExcludePaths(List.of("/user/login"));
        AuthGlobalFilter filter = new AuthGlobalFilter(jwt, auth, properties);
        Logger logger = (Logger) LoggerFactory.getLogger(AuthGlobalFilter.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            MockServerWebExchange valid = MockServerWebExchange.from(MockServerHttpRequest.get("/submission/page")
                    .header("Authorization", token).header("Cookie", cookie).header(identityHeader, "999"));
            GatewayFilterChain chain = exchange -> {
                assertEquals("7", exchange.getRequest().getHeaders().getFirst(identityHeader));
                return Mono.empty();
            };
            filter.filter(valid, chain).block();

            MockServerWebExchange invalid = MockServerWebExchange.from(MockServerHttpRequest.get("/submission/page")
                    .header("Authorization", "synthetic-invalid-token").header("Cookie", cookie));
            GatewayFilterChain rejectedChain = mock(GatewayFilterChain.class);
            filter.filter(invalid, rejectedChain).block();
            assertEquals(HttpStatus.UNAUTHORIZED, invalid.getResponse().getStatusCode());
            verifyNoInteractions(rejectedChain);
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertAll(() -> assertFalse(logs.contains(token)), () -> assertFalse(logs.contains(cookie)),
                    () -> assertFalse(logs.contains("synthetic-invalid-token")),
                    () -> assertFalse(logs.contains(identityHeader)));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }
}
