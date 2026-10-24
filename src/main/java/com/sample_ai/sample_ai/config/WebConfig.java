package com.sample_ai.sample_ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration.
 *
 * Instead of hardcoding "http://localhost:4200" on the controller, the allowed
 * origins come from the `app.cors.allowed-origins` property, which can be
 * overridden with the CORS_ALLOWED_ORIGINS environment variable.
 *
 * Locally:  http://localhost:4200
 * On AWS :  https://your-cloudfront-domain.cloudfront.net
 * Multiple origins can be comma-separated.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:4200}") String origins) {
        this.allowedOrigins = origins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
