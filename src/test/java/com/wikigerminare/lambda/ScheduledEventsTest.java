package com.wikigerminare.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ScheduledEventsTest {

	@Test
	void recognisesTheImageCleanupEvent() {
		assertThat(ScheduledEvents.isImageCleanup(bytes("{\"source\":\"germinawiki.image-cleanup\"}"))).isTrue();
	}

	@Test
	void httpEventsAreNeverTreatedAsCleanupEvenIfTheirBodySaysSo() {
		String functionUrlEvent = """
				{"version":"2.0","rawPath":"/x","requestContext":{"http":{"method":"POST"}},
				 "body":"{\\"source\\":\\"germinawiki.image-cleanup\\"}"}""";
		assertThat(ScheduledEvents.isImageCleanup(bytes(functionUrlEvent))).isFalse();
		assertThat(ScheduledEvents.isImageCleanup(
				bytes("{\"source\":\"germinawiki.image-cleanup\",\"requestContext\":{}}"))).isFalse();
	}

	@Test
	void otherOrMalformedEventsAreNotCleanup() {
		assertThat(ScheduledEvents.isImageCleanup(bytes("{\"source\":\"aws.events\"}"))).isFalse();
		assertThat(ScheduledEvents.isImageCleanup(bytes("not json"))).isFalse();
		assertThat(ScheduledEvents.isImageCleanup(new byte[0])).isFalse();
	}

	private static byte[] bytes(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}

}
