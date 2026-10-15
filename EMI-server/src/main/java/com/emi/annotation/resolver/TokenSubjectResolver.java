package com.emi.annotation.resolver;

import com.emi.annotation.TokenSubject;
import com.emi.config.JWTService;
import com.emi.exception.DomainException;
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

@Slf4j
@Component
public class TokenSubjectResolver implements HandlerMethodArgumentResolver {

    private final JWTService jwtService;

    public TokenSubjectResolver(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(TokenSubject.class)
                && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        TokenSubject annotation = parameter.getParameterAnnotation(TokenSubject.class);
        if (annotation == null) {
            return null;
        }

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }

        // Determine token name
        String tokenName = !annotation.value().isEmpty() ? annotation.value() :
                           !annotation.name().isEmpty() ? annotation.name() :
                           !annotation.cookieName().isEmpty() ? annotation.cookieName() :
                           !annotation.headerName().isEmpty() ? annotation.headerName() : "accessToken";

        String headerName = !annotation.headerName().isEmpty() ? annotation.headerName() : tokenName;
        String cookieName = !annotation.cookieName().isEmpty() ? annotation.cookieName() : tokenName;

        String token = null;

        // 1. Try to retrieve token from HTTP header first
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.trim().isEmpty()) {
            token = cleanTokenPrefix(headerValue, tokenName);
        }

        // 2. If missing in header, try to retrieve from cookies
        if (token == null || token.isEmpty()) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookieName.equalsIgnoreCase(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
                        token = cleanTokenPrefix(cookie.getValue(), tokenName);
                        break;
                    }
                }
            }
        }

        // 3. Handle missing token based on required flag
        if (token == null || token.isEmpty()) {
            if (annotation.required()) {
                String errorMsg = !annotation.message().trim().isEmpty()
                        ? annotation.message()
                        : "Missing '" + tokenName + "' token in request headers or cookies";
                log.warn("TokenSubjectResolver: {}", errorMsg);
                throw new DomainException(errorMsg, HttpStatus.UNAUTHORIZED);
            }
            return null;
        }

        // 4. Extract subject from the token
        try {
            String subject = jwtService.getUserID(token);
            if ((subject == null || subject.trim().isEmpty()) && annotation.required()) {
                throw new DomainException("Subject not found in token", HttpStatus.UNAUTHORIZED);
            }
            return subject;
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TokenSubjectResolver: Failed to parse token - {}", e.getMessage());
            if (annotation.required()) {
                throw new DomainException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }
            return null;
        }
    }

    private String cleanTokenPrefix(String rawValue, String tokenName) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        String customPrefix = tokenName + " ";
        if (trimmed.regionMatches(true, 0, customPrefix, 0, customPrefix.length())) {
            return trimmed.substring(customPrefix.length()).trim();
        }
        return trimmed;
    }
}

