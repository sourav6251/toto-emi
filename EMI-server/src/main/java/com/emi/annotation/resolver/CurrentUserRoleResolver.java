package com.emi.annotation.resolver;

import com.emi.annotation.CurrentUserRole;
import com.emi.exception.DomainException;
import com.emi.utils.enums.Role;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserRoleResolver implements HandlerMethodArgumentResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserRole.class)
                && parameter.getParameterType().equals(Role.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                   ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest,
                                   WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new DomainException("User not found", HttpStatus.UNAUTHORIZED);
        }

        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .flatMap(name -> {
                    try {
                        return java.util.stream.Stream.of(Role.valueOf(name));
                    } catch (IllegalArgumentException e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .findFirst()
                .orElseThrow(() -> new DomainException("User not found", HttpStatus.UNAUTHORIZED));
    }
}
