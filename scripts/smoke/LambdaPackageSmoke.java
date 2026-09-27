import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

/**
 * Invokes the packaged Lambda handler exactly as Lambda would (Function URL event, GET /health).
 * Run by scripts/smoke-lambda-package.sh with the unpacked deployment zip as the classpath.
 */
public class LambdaPackageSmoke {

	private static final String EVENT = """
			{"version":"2.0","routeKey":"$default","rawPath":"/health","rawQueryString":"",
			 "headers":{"host":"smoke.lambda-url.sa-east-1.on.aws","accept":"application/json"},
			 "requestContext":{"accountId":"anonymous","apiId":"smoke","domainName":"smoke.lambda-url.sa-east-1.on.aws",
			   "domainPrefix":"smoke","http":{"method":"GET","path":"/health","protocol":"HTTP/1.1",
			   "sourceIp":"127.0.0.1","userAgent":"smoke"},"requestId":"smoke","routeKey":"$default",
			   "stage":"$default","time":"01/Jan/2026:00:00:00 +0000","timeEpoch":1767225600000},
			 "isBase64Encoded":false}""";

	public static void main(String[] args) throws Exception {
		Context context = (Context) Proxy.newProxyInstance(LambdaPackageSmoke.class.getClassLoader(),
				new Class<?>[] { Context.class }, (proxy, method, methodArgs) -> switch (method.getName()) {
					case "getLogger" -> new LambdaLogger() {
						public void log(String message) {
							System.out.print(message);
						}

						public void log(byte[] message) {
						}
					};
					case "getRemainingTimeInMillis" -> 20_000;
					case "getMemoryLimitInMB" -> 2048;
					default -> method.getReturnType() == String.class ? "smoke" : null;
				});

		var handler = (RequestStreamHandler) Class.forName("com.wikigerminare.lambda.StreamLambdaHandler")
			.getDeclaredConstructor()
			.newInstance();
		var output = new ByteArrayOutputStream();
		handler.handleRequest(new ByteArrayInputStream(EVENT.getBytes(StandardCharsets.UTF_8)), output, context);

		String response = output.toString(StandardCharsets.UTF_8);
		System.out.println("\nResponse: " + response);
		boolean ok = response.contains("\"statusCode\":200") && response.contains("\\\"status\\\":\\\"ready\\\"");
		System.out.println(ok ? "SMOKE OK" : "SMOKE FAILED");
		System.exit(ok ? 0 : 1);
	}

}
