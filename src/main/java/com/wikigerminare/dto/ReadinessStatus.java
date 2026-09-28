package com.wikigerminare.dto;

/**
 * Public readiness answer. It deliberately carries nothing but the status (FR-004).
 */
public record ReadinessStatus(String status) {

	private static final ReadinessStatus READY = new ReadinessStatus("ready");

	private static final ReadinessStatus NOT_READY = new ReadinessStatus("not_ready");

	public static ReadinessStatus ready() {
		return READY;
	}

	public static ReadinessStatus notReady() {
		return NOT_READY;
	}

}
