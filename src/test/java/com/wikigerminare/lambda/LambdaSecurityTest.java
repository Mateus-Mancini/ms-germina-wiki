package com.wikigerminare.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2HttpContext;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequestContext;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.wikigerminare.WikigerminareApplication;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.wikigerminare.auth.security.AuthJwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Spring Security through the real Lambda adapter with Function URL (HTTP API v2) events. MockMvc can't
 * catch adapter-specific failures: the adapter has no HTTP session for v2 events, so anything that touches
 * the session (e.g. Spring Security saving the request for a later login) crashes and Lambda answers 502.
 */
class LambdaSecurityTest {

	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("postgres:18-alpine"));

	private static SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;

	@BeforeAll
	static void startLikeLambda() throws Exception {
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
	void signedOutRequestsToProtectedEndpointsGet401LikeBrowsersAndCurlSendThem() {
		assertThat(statusOf("GET", "/api/folders", "*/*")).isEqualTo(401);
		assertThat(statusOf("POST", "/api/pages/00000000-0000-0000-0000-000000000001/images/uploads", "*/*"))
			.isEqualTo(401);
	}

	@Test
	void publicEndpointsStayReachable() {
		assertThat(statusOf("GET", "/api/images/00000000-0000-0000-0000-000000000000", "*/*")).isEqualTo(404);
	}

	@Test
	void signedBearerTokenReachesProtectedRouteThroughLambdaAdapter() {
		assertThat(statusOf("GET", "/api/folders", "*/*", signedToken())).isEqualTo(200);
	}

	private static int statusOf(String method, String path, String accept) {
		return statusOf(method, path, accept, null);
	}

	private static int statusOf(String method, String path, String accept, String token) {
		HttpApiV2HttpContext http = new HttpApiV2HttpContext();
		http.setMethod(method);
		http.setPath(path);
		http.setProtocol("HTTP/1.1");
		http.setSourceIp("127.0.0.1");
		http.setUserAgent("curl/8");
		HttpApiV2ProxyRequestContext context = new HttpApiV2ProxyRequestContext();
		context.setHttp(http);
		context.setRequestId("security-test");
		HttpApiV2ProxyRequest request = new HttpApiV2ProxyRequest();
		request.setVersion("2.0");
		request.setRawPath(path);
		request.setHeaders(token == null
				? Map.of("accept", accept)
				: Map.of("accept", accept, "authorization", "Bearer " + token));
		request.setRequestContext(context);
		return handler.proxy(request, lambdaContext()).getStatusCode();
	}

	private static String signedToken() {
		AuthJwtProperties properties = new AuthJwtProperties(
				"VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9yS2V5RGVyaXZhdGlvbg==", 900);
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(
				new ImmutableSecret<SecurityContext>(properties.secretKey().getEncoded()));
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(AuthJwtProperties.ISSUER)
				.subject(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("role", "member")
				.build();
		return encoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private static Context lambdaContext() {
		return (Context) Proxy.newProxyInstance(LambdaSecurityTest.class.getClassLoader(), new Class<?>[] { Context.class },
				(proxy, method, args) -> switch (method.getName()) {
					case "getLogger" -> new LambdaLogger() {
						public void log(String message) {
						}

						public void log(byte[] message) {
						}
					};
					case "getRemainingTimeInMillis" -> 10_000;
					case "getMemoryLimitInMB" -> 2048;
					default -> method.getReturnType() == String.class ? "test" : null;
				});
	}

}
