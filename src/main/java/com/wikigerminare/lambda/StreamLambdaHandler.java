package com.wikigerminare.lambda;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.crac.Core;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.wikigerminare.WikigerminareApplication;
import com.wikigerminare.service.ImageCleanupService;

/**
 * AWS Lambda entry point. Lambda Function URLs deliver HTTP API v2 events, which the adapter
 * translates into servlet requests for the regular Spring MVC application. The daily image cleanup
 * (EventBridge Scheduler, invoking the alias directly) is the only non-HTTP event.
 */
public class StreamLambdaHandler implements RequestStreamHandler {

	// Initialised once per execution environment, during Lambda init (captured in the SnapStart snapshot).
	private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> HANDLER;

	// Strong reference required: org.crac only keeps weak references to registered resources.
	private static final SnapStartPriming PRIMING;

	static {
		try {
			HANDLER = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(WikigerminareApplication.class);
		}
		catch (ContainerInitializationException ex) {
			throw new IllegalStateException("Could not initialize Spring Boot application", ex);
		}
		// Registered after Spring's own CRaC resource, so it runs first on checkpoint (reverse order),
		// while the application context is still started.
		PRIMING = new SnapStartPriming(HANDLER);
		Core.getGlobalContext().register(PRIMING);
	}

	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		byte[] event = input.readAllBytes();
		if (ScheduledEvents.isImageCleanup(event)) {
			int deleted = applicationContext().getBean(ImageCleanupService.class).run();
			output.write(("{\"deleted\":" + deleted + "}").getBytes(StandardCharsets.UTF_8));
			return;
		}
		HANDLER.proxyStream(new ByteArrayInputStream(event), output, context);
	}

	private static WebApplicationContext applicationContext() {
		return WebApplicationContextUtils.getRequiredWebApplicationContext(HANDLER.getServletContext());
	}

}
