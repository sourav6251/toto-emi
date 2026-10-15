package com.emi.config;

import com.emi.annotation.resolver.AccessTokenResolver;
import com.emi.annotation.resolver.CurrentUserIdResolver;
import com.emi.annotation.resolver.TokenSubjectResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AccessTokenResolver accessTokenResolver;
    private final CurrentUserIdResolver currentUserIdResolver;
    private final TokenSubjectResolver tokenSubjectResolver;

    public WebConfig(AccessTokenResolver accessTokenResolver,
                     CurrentUserIdResolver currentUserIdResolver,
                     TokenSubjectResolver tokenSubjectResolver) {
        this.accessTokenResolver = accessTokenResolver;
        this.currentUserIdResolver = currentUserIdResolver;
        this.tokenSubjectResolver = tokenSubjectResolver;
        log.info(">>> WebConfig initialized and registered resolvers <<<");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(accessTokenResolver);
        resolvers.add(currentUserIdResolver);
        resolvers.add(tokenSubjectResolver);
        log.info(">>> Added AccessTokenResolver, CurrentUserIdResolver, and TokenSubjectResolver to Spring MVC Argument Resolvers <<<");
    }
}

