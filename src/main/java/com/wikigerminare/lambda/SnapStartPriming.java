package com.wikigerminare.lambda;

import java.util.Map;

import org.crac.Context;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2HttpContext;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequestContext;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.wikigerminare.dto.ReadinessStatus;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;

/**
 * Warms the request path right before the SnapStart snapshot, so class loading and JIT happen when a
 * version is published instead of on a student's first request (research R3).
 * <p>
 * It must never touch the database: a connection captured in the snapshot would be dead after restore.
 * That is why it hits an unmapped path (DispatcherServlet + Spring's JSON error handling) and serialises
 * the readiness DTO in memory, rather than calling {@code /health}.
 */
public class SnapStartPriming implements Resource {

	private static final Logger log = LoggerFactory.getLogger(SnapStartPriming.class);

	private static final String PRIMING_PATH = "/__snapstart-priming";

	private final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;

	public SnapStartPriming(SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler) {
		this.handler = handler;
	}

	@Override
	public void beforeCheckpoint(Context<? extends Resource> context) {
		try {
			int status = prime();
			log.info("SnapStart priming finished with status {}", status);
		}
		catch (RuntimeException ex) {
			// Priming is an optimisation: a failure must never block publishing a version.
			log.warn("SnapStart priming failed", ex);
		}
	}

	@Override
	public void afterRestore(Context<? extends Resource> context) {
	}

	int prime() {
		LambdaContainerHandler.getObjectMapper().writeValueAsString(ReadinessStatus.ready());
		return handler.proxy(primingRequest(), new PrimingContext()).getStatusCode();
	}

	private static HttpApiV2ProxyRequest primingRequest() {
		HttpApiV2HttpContext http = new HttpApiV2HttpContext();
		http.setMethod("GET");
		http.setPath(PRIMING_PATH);
		http.setProtocol("HTTP/1.1");
		http.setSourceIp("127.0.0.1");
		http.setUserAgent("snapstart-priming");

		HttpApiV2ProxyRequestContext requestContext = new HttpApiV2ProxyRequestContext();
		requestContext.setHttp(http);
		requestContext.setRequestId("snapstart-priming");

		HttpApiV2ProxyRequest request = new HttpApiV2ProxyRequest();
		request.setVersion("2.0");
		request.setRawPath(PRIMING_PATH);
		request.setHeaders(Map.of("accept", "application/json"));
		request.setRequestContext(requestContext);
		return request;
	}

	/**
	 * Minimal Lambda context for the synthetic priming invocation.
	 */
	private static final class PrimingContext implements com.amazonaws.services.lambda.runtime.Context {

		@Override
		public String getAwsRequestId() {
			return "snapstart-priming";
		}

		@Override
		public String getLogGroupName() {
			return "";
		}

		@Override
		public String getLogStreamName() {
			return "";
		}

		@Override
		public String getFunctionName() {
			return "";
		}

		@Override
		public String getFunctionVersion() {
			return "";
		}

		@Override
		public String getInvokedFunctionArn() {
			return "";
		}

		@Override
		public CognitoIdentity getIdentity() {
			return null;
		}

		@Override
		public ClientContext getClientContext() {
			return null;
		}

		@Override
		public int getRemainingTimeInMillis() {
			return 10_000;
		}

		@Override
		public int getMemoryLimitInMB() {
			return 0;
		}

		@Override
		public LambdaLogger getLogger() {
			return new LambdaLogger() {

				@Override
				public void log(String message) {
					log.debug(message);
				}

				@Override
				public void log(byte[] message) {
				}

			};
		}

	}

}
