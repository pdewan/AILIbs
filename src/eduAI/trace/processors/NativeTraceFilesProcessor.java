package eduAI.trace.processors;

import java.util.List;
import java.util.Map;

public class NativeTraceFilesProcessor extends GenericTracesFileProcessor {
	public NativeTraceFilesProcessor(java.nio.file.Path sessionTraceFile) {
		super(sessionTraceFile, null);
		registerNativeProviderTraceInterpreters();
	}

	public NativeTraceFilesProcessor() {
		super();
		registerNativeProviderTraceInterpreters();
	}

	public NativeTraceFilesProcessor(
			Map<String, TraceFiles> aProviderTraceFiles) {
		super(aProviderTraceFiles);
		registerNativeProviderTraceInterpreters();
	}

	public NativeTraceFilesProcessor(
			Map<String, TraceFiles> aProviderTraceFiles,
			ExpectedInputs anExpectedInputs) {
		super(aProviderTraceFiles, anExpectedInputs);
		registerNativeProviderTraceInterpreters();
	}

	private void registerNativeProviderTraceInterpreters() {
		registerProviderTraceInterpreter(TraceProviderNames.GEMINI, new GeminiTraceInterpreter());
		registerProviderTraceInterpreter(TraceProviderNames.OLLAMA, new OllamaTraceInterpreter());
		registerNativeProviderParameterCheckers();
		registerProviderSpecificTypes(
				"Gemini",
				List.of(
						"com.google.genai.Client",
						"com.google.genai.types.AutoValue_Content",
						"com.google.genai.types.AutoValue_GenerateContentConfig",
						"com.google.genai.types.AutoValue_GenerateContentResponse"),
				List.of("com.google.genai."));
		registerProviderSpecificTypes(
				"Ollama",
				List.of(
						"io.github.ollama4j.Ollama",
						"io.github.ollama4j.models.chat.OllamaChatMessage",
						"io.github.ollama4j.models.chat.OllamaChatResponseModel",
						"io.github.ollama4j.models.chat.OllamaChatResult",
						OllamaTraceInterpreter.CONFIGURATION_TYPE),
				List.of("io.github.ollama4j."));
	}

	private void registerNativeProviderParameterCheckers() {
		for (String timeoutName : List.of(eduAI.properties.AIPropertyNames.TIMEOUT, "timeout_milliseconds")) {
			registerProviderParameterChecker(
					new TextProviderParameterChecker(
							"Gemini", timeoutName, "HttpOptions$Builder", "timeout=Optional["));
			registerProviderParameterChecker(
					new TextProviderParameterChecker(
							"Ollama", timeoutName, "io.github.ollama4j.Ollama", "requestTimeoutSeconds"));
		}
		registerProviderParameterChecker(
				new TextProviderParameterChecker(
						"Gemini",
						eduAI.properties.AIPropertyNames.TEMPERATURE,
						"GenerateContentConfig$Builder",
						"temperature=Optional["));
		registerProviderParameterChecker(
				new TextProviderParameterChecker(
						"Ollama",
						eduAI.properties.AIPropertyNames.TEMPERATURE,
						"Options",
						"temperature="));
	}
}
