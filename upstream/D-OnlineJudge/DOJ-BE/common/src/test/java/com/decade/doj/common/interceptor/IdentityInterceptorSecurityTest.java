package com.decade.doj.common.interceptor;

import com.decade.doj.common.annotation.AdminRequired;
import com.decade.doj.common.config.properties.JwtProperties;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class IdentityInterceptorSecurityTest {
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanUp() {
        UserContext.clear();
    }

    @Test
    void validIdentityIsClearedAndMissingAnonymousOrInvalidIdentityCannotImpersonateAUser() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey("X-Synthetic-User");
        IdentityInterceptor interceptor = new IdentityInterceptor(properties);
        HandlerMethod admin = new HandlerMethod(new AdminAction(), AdminAction.class.getMethod("run"));
        var checks = new ArrayList<org.junit.jupiter.api.function.Executable>();
        checks.add(() -> {
            assertTrue(interceptor.preHandle(request("7"), response, new Object()));
            assertEquals(7L, UserContext.getCurrentUser());
            interceptor.afterCompletion(request("7"), response, new Object(), null);
            assertNull(UserContext.getCurrentUser());
        });
        for (String header : new String[]{null, "", "   ", "0"}) {
            checks.add(() -> {
                UserContext.setCurrentUser(99L);
                assertTrue(interceptor.preHandle(request(header), response, new Object()));
                assertNull(UserContext.getCurrentUser(), "anonymous request must not become user 3 or retain user 99");
                assertThrows(UnauthorizedException.class,
                        () -> new AdminCheckInterceptor().preHandle(request(header), response, admin));
            });
        }
        for (String header : new String[]{"not-a-user", "-1", "9223372036854775808"}) {
            checks.add(() -> {
                UserContext.setCurrentUser(99L);
                assertThrows(UnauthorizedException.class,
                        () -> interceptor.preHandle(request(header), response, new Object()));
                assertNull(UserContext.getCurrentUser());
            });
        }
        assertAll("identity propagation, anonymous public access and rejection", checks);
    }

    private MockHttpServletRequest request(String identity) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/synthetic");
        if (identity != null) {
            request.addHeader("X-Synthetic-User", identity);
        }
        return request;
    }

    static class AdminAction {
        @AdminRequired
        public void run() { }
    }
}
