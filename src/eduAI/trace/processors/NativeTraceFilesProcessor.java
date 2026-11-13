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
		registerProviderParameterChecker(new NativeParameterValues(TraceProviderNames.GEMINI));
		registerProviderParameterChecker(new NativeParameterValues(TraceProviderNames.OLLAMA));
	}
}
