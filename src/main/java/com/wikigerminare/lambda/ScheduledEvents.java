package com.wikigerminare.lambda;

import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Recognises non-HTTP events sent to the API function by EventBridge Scheduler (which invokes the alias
 * directly, never through the public Function URL).
 */
final class ScheduledEvents {

	static final String IMAGE_CLEANUP_SOURCE = "germinawiki.image-cleanup";

	private ScheduledEvents() {
	}

	/**
	 * True only for {"source":"germinawiki.image-cleanup"} without an HTTP request context. Function URL events
	 * always carry requestContext, and callers can't set top-level fields (their body is a string field).
	 */
	static boolean isImageCleanup(byte[] event) {
		if (event.length == 0 || event[0] != '{') {
			return false;
		}
		try {
			JsonNode root = LambdaContainerHandler.getObjectMapper().readTree(event);
			return !root.has("requestContext") && IMAGE_CLEANUP_SOURCE.equals(root.path("source").asString(null));
		}
		catch (JacksonException ex) {
			return false;
		}
	}

}
