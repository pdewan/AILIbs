package eduAI.trace.processors;

import java.util.List;
import java.util.Map;

public class NativeTraceFilesProcessor extends GenericTracesFileProcessor {
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
		registerProviderTraceInterpreter(new GeminiTraceInterpreter());
		registerProviderTraceInterpreter(new OllamaTraceInterpreter());
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
						"io.github.ollama4j.utils.Options"),
				List.of("io.github.ollama4j."));
	}

	private void registerNativeProviderParameterCheckers() {
		registerProviderParameterChecker(
				new TextProviderParameterChecker(
						"Gemini",
						"timeout_milliseconds",
						"HttpOptions$Builder",
						"timeout=Optional["));
		registerProviderParameterChecker(
				new TextProviderParameterChecker(
						"Gemini",
						"temperature",
						"GenerateContentConfig$Builder",
						"temperature=Optional["));
		registerProviderParameterChecker(
				new TextProviderParameterChecker(
						"Ollama",
						"timeout_milliseconds",
						"io.github.ollama4j.Ollama",
						"requestTimeoutSeconds"));
		registerProviderParameterChecker(
				new TextProviderParameterChecker(
						"Ollama",
						"temperature",
						"Options",
						"temperature="));
	}
}
