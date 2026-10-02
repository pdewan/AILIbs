package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import eduAI.trace.lib.StreamingMode;

public class ProviderMetadataKeyValueComparatorTest {
	public static void main(String[] args) throws IOException {
		assertCase("1185", true);
		assertCase("999", false);
		assertMappings(new GeminiTraceInterpreter(),
				"GenerateContentResponse{modelVersion=Optional[model], finishReason=Optional[STOP], "
				+ "usageMetadata=Optional[GenerateContentResponseUsageMetadata{promptTokenCount=Optional[1185], candidatesTokenCount=Optional[12]}]}",
				"STOP");
		String ollama = "OllamaChatResponseModel(model=model, doneReason=stop, promptEvalCount=1185, evalCount=12, totalDuration=1234567890)";
		assertMappings(new OllamaTraceInterpreter(), ollama, "stop");
		NativeMetadataRegistry registry = new OllamaTraceInterpreter().nativeMetadataRegistry(ollama);
		assertDifference(registry, ollama, "totalTimeMs", "1234", null);
		assertDifference(registry, ollama, "duration in native units", "1234567890", null);
		assertDifference(registry, ollama, "arbitrary duration name", "1234", null);
		assertDifference(registry, ollama, "arbitrary duration name", "999999", "absent from native metadata");
		System.out.println("Provider metadata key-value checks passed");
	}

	private static void assertMappings(ProviderTraceInterpreter interpreter, String source, String reason) {
		NativeMetadataRegistry registry = interpreter.nativeMetadataRegistry(source);
		assertDifference(registry, source, "inputTokens", "1185", null);
		assertDifference(registry, source, "outputTokens", "12", null);
		assertDifference(registry, source, "resolvedModel", "model", null);
		assertDifference(registry, source, "terminationReason", reason, null);
		assertDifference(registry, source, "inputTokens", "12", null);
		assertDifference(registry, source, "student chosen label", "1185", null);
		assertDifference(registry, source, "student chosen label", "99999", "absent from native metadata");
		assertDifference(registry, "", "inputTokens", "1185", "absent or unreadable");
	}

	private static void assertDifference(NativeMetadataRegistry registry, String source,
			String key, String value, String expected) {
		String actual = registry.difference(source, key, value);
		if (expected == null ? actual != null : actual == null || !actual.contains(expected)) {
			throw new AssertionError("key=" + key + " expected=" + expected + " actual=" + actual);
		}
	}

	private static void assertCase(
			String aStoredValue,
			boolean anExpectedMatch) throws IOException {
		Path trace = Files.createTempFile("metadata-check", ".txt");
		Files.writeString(
				trace,
				"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: sample.Factory) <provider=\"Provider\" serverHandleFactoryClass=\"sample.Factory\" serverHandleFactoryRegistryClass=\"sample.Registry\" serverHandleFactoryIdentity=\"sample.Factory@1\"> ##\n"
						+ "## lib {metadata_translated} [none: main] "
						+ "(single_model_request_processing: sample.MetadataAdapter) "
						+ "<metadataHandlerClass=\"sample.MetadataAdapter\" "
						+ "propertyName=\"student_defined_counter\" "
						+ "propertyValue=\"1185\" "
						+ "providerDependentSource=\"GenerateContentResponse{usageMetadata=Optional[GenerateContentResponseUsageMetadata{promptTokenCount=Optional["
						+ aStoredValue + "]}]}\"> ##\n",
				StandardCharsets.UTF_8);
		GenericTracesFileProcessor processor =
				new GenericTracesFileProcessor();
		processor.registerProviderTraceInterpreter(new GeminiTraceInterpreter());
		processor.processTraceFile(
				"Provider", StreamingMode.NON_STREAMING, trace);
		TraceCheckResult result = processor.checkProviderGenericMetadataMatches(
				"Provider", StreamingMode.NON_STREAMING);
		if (result.passed() != anExpectedMatch) {
			throw new AssertionError(
					"Expected metadata match=" + anExpectedMatch
							+ " but got messages=" + processor.getCheckerMessages());
		}
		Files.deleteIfExists(trace);
	}
}
