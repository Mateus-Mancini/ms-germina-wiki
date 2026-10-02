package com.wikigerminare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for local runs only. In production CORS is configured on the Lambda Function URL
 * (template.yaml), so it must never be enabled here as well.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalCorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public LocalCorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(allowedOrigins)
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
			.allowedHeaders("Authorization", "Content-Type", "If-Match")
			// Same as the Function URL in template.yaml: the editor reads the page ETag to save with If-Match.
			.exposedHeaders("ETag")
			.maxAge(86400);
	}

}
