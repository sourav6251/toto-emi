package com.emi.annotation.resolver;

import com.emi.annotation.AuthToken;
import com.emi.config.JWTService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Slf4j
@Component
public class AccessTokenResolver implements HandlerMethodArgumentResolver {

    private final JWTService jwtService;

    public AccessTokenResolver(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean supports = parameter.hasParameterAnnotation(AuthToken.class)
                && parameter.getParameterType().equals(String.class);
        return supports;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        AuthToken annotation = parameter.getParameterAnnotation(AuthToken.class);
        if (annotation == null) {
            return null;
        }

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }

        // Determine token name from value() or name() or default to "AccessToken"
        String tokenName = !annotation.value().isEmpty() ? annotation.value() :
                           !annotation.name().isEmpty() ? annotation.name() :
                           !annotation.cookieName().isEmpty() ? annotation.cookieName() : "AccessToken";

        String headerName = !annotation.headerName().isEmpty() ? annotation.headerName() : tokenName;
        String cookieName = !annotation.cookieName().isEmpty() ? annotation.cookieName() : tokenName;

        String token = null;

        // 1. Check Header by name (e.g. "otp_token" or "Authorization")
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.trim().isEmpty()) {
            if ("Authorization".equalsIgnoreCase(headerName) && headerValue.toLowerCase().startsWith("bearer ")) {
                token = headerValue.substring(7).trim();
            } else {
                token = headerValue.trim();
            }
        }

        // 2. Check Standard Authorization Header if not found
        if (token == null || token.isEmpty()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
                token = authHeader.substring(7).trim();
            }
        }

        // 3. Check Cookie (e.g. "otp_token")
        if (token == null || token.isEmpty()) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookieName.equalsIgnoreCase(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
                        token = cookie.getValue().trim();
                        break;
                    }
                }
            } else {
                log.warn("AccessTokenResolver: No cookies present in incoming request for '{}'", request.getRequestURI());
            }
        }

        // 4. Check Query Parameter (e.g. ?token=... or ?otp_token=...) as fallback
        if (token == null || token.isEmpty()) {
            String paramValue = request.getParameter(cookieName);
            if (paramValue == null || paramValue.trim().isEmpty()) {
                paramValue = request.getParameter("token");
            }
            if (paramValue != null && !paramValue.trim().isEmpty()) {
                token = paramValue.trim();
            }
        }

        log.info("AccessTokenResolver: resolved token for '{}' = {}", tokenName, (token != null ? "[PRESENT]" : "[NOT FOUND]"));

        // 5. Handle required constraint with dynamic/custom error message
        if ((token == null || token.isEmpty()) && annotation.required()) {
            String errorMsg = (annotation.message() != null && !annotation.message().trim().isEmpty())
                    ? annotation.message()
                    : "Missing '" + tokenName + "' token in request cookies or headers";
            log.warn("AccessTokenResolver: rejecting request - {}", errorMsg);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, errorMsg);
        }

        return token;
    }
}
