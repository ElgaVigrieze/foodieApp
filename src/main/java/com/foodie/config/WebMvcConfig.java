package com.foodie.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final HouseholdRequiredInterceptor householdRequiredInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(householdRequiredInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        HouseholdRequiredInterceptor.SETUP_PAGE, HouseholdRequiredInterceptor.SETUP_PAGE + "/**",
                        "/login", "/logout", "/error", "/health",
                        "/css/**", "/js/**", "/favicon.ico", "/h2-console/**",
                        "/api/brain/ingest", "/api/brain/ping", "/api/brain/heartbeat");
    }
}
