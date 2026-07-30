package com.femstep.store.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final boolean rateLimitEnabled;

    public WebMvcConfig(
            RateLimitInterceptor rateLimitInterceptor,
            @Value("${security.rate-limit.enabled:true}") boolean rateLimitEnabled
    ) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.rateLimitEnabled = rateLimitEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitEnabled) {
            registry.addInterceptor(rateLimitInterceptor);
        }
    }
}
