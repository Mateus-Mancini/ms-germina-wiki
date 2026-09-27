package com.wikigerminare.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.wikigerminare.WikigerminareApplication;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/**
 * Priming runs inside the SnapStart checkpoint, so it must exercise the request path without ever
 * opening a database connection (a connection must not be captured in the snapshot).
 */
class SnapStartPrimingTest {

	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("postgres:17-alpine"));

	private static SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;

	@BeforeAll
	static void startApplicationLikeLambdaInit() throws Exception {
		postgres.start();
		System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
		System.setProperty("spring.datasource.username", postgres.getUsername());
		System.setProperty("spring.datasource.password", postgres.getPassword());
		handler = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(WikigerminareApplication.class);
	}

	@AfterAll
	static void cleanUp() {
		System.clearProperty("spring.datasource.url");
		System.clearProperty("spring.datasource.username");
		System.clearProperty("spring.datasource.password");
		postgres.stop();
	}

	@Test
	void primesTheRequestPathThroughTheErrorHandler() {
		int status = new SnapStartPriming(handler).prime();

		assertThat(status).isEqualTo(404);
	}

	@Test
	void primingOpensNoDatabaseConnection() {
		new SnapStartPriming(handler).prime();

		HikariDataSource dataSource = WebApplicationContextUtils
			.getRequiredWebApplicationContext(handler.getServletContext())
			.getBean(HikariDataSource.class);
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		assertThat(pool == null ? 0 : pool.getTotalConnections()).isZero();
	}

}
