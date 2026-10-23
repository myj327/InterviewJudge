package com.decade.doj.common.interceptor;
import cn.hutool.core.util.StrUtil;
import com.decade.doj.common.config.properties.JwtProperties;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class IdentityInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserContext.clear();
        log.debug("path: {}", request.getRequestURI());
        String userId = request.getHeader(jwtProperties.getSecretKey());
        if (StrUtil.isBlank(userId)) {
            return true;
        }
        long parsedId;
        try {
            parsedId = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("无效的用户身份");
        }
        if (parsedId < 0) {
            throw new UnauthorizedException("无效的用户身份");
        }
        if (parsedId > 0) {
            UserContext.setCurrentUser(parsedId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }

}
