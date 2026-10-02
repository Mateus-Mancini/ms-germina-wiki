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

	@Test
	@org.junit.jupiter.api.Timeout(15)
	void commentsCompleteThroughFunctionUrlAdapterWithRealIdentityAndPostgres() {
		var application = org.springframework.web.context.support.WebApplicationContextUtils
				.getRequiredWebApplicationContext(handler.getServletContext());
		var jdbc = application.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
		UUID author = UUID.randomUUID();
		UUID admin = UUID.randomUUID();
		UUID page = UUID.randomUUID();
		UUID block = UUID.randomUUID();
		jdbc.update("INSERT INTO users (id,name,email,password_hash,role) VALUES (?,?,?,?,?::user_role)",
				author, "Author", author + "@example.invalid", "unused", "member");
		jdbc.update("INSERT INTO users (id,name,email,password_hash,role) VALUES (?,?,?,?,?::user_role)",
				admin, "Admin", admin + "@example.invalid", "unused", "admin");
		jdbc.update("INSERT INTO pages (id,title,slug,content,created_by) VALUES (?,?,?,?,?)",
				page, "Lambda comments", page.toString(), "<!--b:" + block + "-->paragraph", author);
		String authorToken = signedToken(author, "member");
		String adminToken = signedToken(admin, "admin");
		String body = "{\"pageId\":\"" + page + "\",\"anchor\":{\"blockId\":\"" + block
				+ "\"},\"text\":\"  question  \"}";
		AwsProxyResponse created = responseOf("POST", "/api/comments", authorToken, body);
		assertThat(created.getStatusCode()).isEqualTo(201);
		String id = com.jayway.jsonpath.JsonPath.read(created.getBody(), "$.id");
		assertThat(com.jayway.jsonpath.JsonPath.<String>read(created.getBody(), "$.userId"))
				.isEqualTo(author.toString());
		assertThat(responseOf("GET", "/api/comments/" + id, authorToken, null).getStatusCode()).isEqualTo(200);
		assertThat(responseOf("PATCH", "/api/comments/" + id, authorToken,
				"{\"text\":\"edited\"}").getStatusCode()).isEqualTo(200);
		assertThat(responseOf("POST", "/api/comments/" + id + "/admin-replies", authorToken,
				"{\"text\":\"answer\"}").getStatusCode()).isEqualTo(403);
		AwsProxyResponse reply = responseOf("POST", "/api/comments/" + id + "/admin-replies", adminToken,
				"{\"text\":\"answer\"}");
		assertThat(reply.getStatusCode()).isEqualTo(201);
		String replyId = com.jayway.jsonpath.JsonPath.read(reply.getBody(), "$.id");
		AwsProxyResponse loaded = responseOf("GET", "/api/comments/" + id, authorToken, null);
		assertThat(com.jayway.jsonpath.JsonPath.<String>read(loaded.getBody(), "$.adminReplies[0].text"))
				.isEqualTo("answer");
		assertThat(responseOf("PATCH", "/api/comments/" + replyId, adminToken,
				"{\"text\":\"changed\"}").getStatusCode()).isEqualTo(403);
		assertThat(responseOf("DELETE", "/api/comments/" + id, authorToken, null).getStatusCode()).isEqualTo(204);
		assertThat(responseOf("GET", "/api/comments/" + replyId, authorToken, null).getStatusCode()).isEqualTo(404);
	}

	private static AwsProxyResponse responseOf(String method, String path, String token, String body) {
		HttpApiV2HttpContext http = new HttpApiV2HttpContext();
		http.setMethod(method);
		http.setPath(path);
		http.setProtocol("HTTP/1.1");
		http.setSourceIp("127.0.0.1");
		HttpApiV2ProxyRequestContext context = new HttpApiV2ProxyRequestContext();
		context.setHttp(http);
		context.setRequestId("comments-lambda-test");
		HttpApiV2ProxyRequest request = new HttpApiV2ProxyRequest();
		request.setVersion("2.0");
		request.setRawPath(path);
		request.setHeaders(Map.of("accept", "application/json", "content-type", "application/json",
				"authorization", "Bearer " + token));
		request.setBody(body);
		request.setRequestContext(context);
		return handler.proxy(request, lambdaContext());
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
		return signedToken(UUID.randomUUID(), "member");
	}

	private static String signedToken(UUID userId, String role) {
		AuthJwtProperties properties = new AuthJwtProperties(
				"VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9yS2V5RGVyaXZhdGlvbg==", 900);
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(
				new ImmutableSecret<SecurityContext>(properties.secretKey().getEncoded()));
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(AuthJwtProperties.ISSUER)
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("role", role)
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
