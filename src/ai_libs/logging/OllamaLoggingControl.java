package ai_libs.logging;

import ai_libs.logging.LogProcessor;

public final class OllamaLoggingControl {
	public static final String DEFAULT_LOGGING_LEVEL = "warn";
	public static final String LOGGING_LEVEL_ENVIRONMENT_VARIABLE =
			"EDUCOPILOT_DEMO_LOGGING_LEVEL";
	private static final String OLLAMA_REQUEST_LOGGER =
			"io.github.ollama4j.models.request.OllamaChatEndpointCaller";

	private OllamaLoggingControl() {
	}

	public static void configureProviderLogging() {
		configureProviderLogging(loggingLevel());
	}

	public static void configureProviderLogging(String aLoggingLevel) {
		/*
		 * Keep demos standalone. EduCopilot's registry initializer uses this
		 * same Tinylog API from the logging_level property, but exported demo
		 * mains may run without EduCopilot startup or tinylog.properties on the
		 * runtime classpath.
		 *
		 * Call this before creating provider clients or touching loggers.
		 * Tinylog freezes configuration after first use; if it is already
		 * frozen, the host application's existing logging configuration wins.
		 */
		configureTinylog("level", nonBlankLoggingLevel(aLoggingLevel));
	}

	public static boolean isOllamaRequestDebugEnabled() {
		return org.slf4j.LoggerFactory
				.getLogger(OLLAMA_REQUEST_LOGGER)
				.isDebugEnabled();
	}

	private static void configureTinylog(
			String aPropertyName,
			String aPropertyValue) {
		try {
			org.tinylog.configuration.Configuration.set(
					aPropertyName,
					aPropertyValue);
		} catch (UnsupportedOperationException e) {
			/*
			 * Tinylog was already initialized. In a standalone demo this usually
			 * means some class touched logging before main configured it.
			 */
		}
	}

	private static String loggingLevel() {
		String value = System.getenv(LOGGING_LEVEL_ENVIRONMENT_VARIABLE);
		return value == null || value.trim().isEmpty()
				? LogProcessor.getOllamaLoggingLevel()
				: nonBlankLoggingLevel(value);
	}

	private static String nonBlankLoggingLevel(String aLoggingLevel) {
		return aLoggingLevel == null || aLoggingLevel.trim().isEmpty()
				? DEFAULT_LOGGING_LEVEL
				: aLoggingLevel.trim();
	}
}
