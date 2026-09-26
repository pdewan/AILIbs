package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Proves that expected-content presence does not substitute for ordered
 * cross-provider request agreement.
 */
public class CrossProviderMessageOrderMutationTest {
	private static final Path CORRECT = Path.of("correct_filtered");
	private static final Path MUTATIONS = Path.of(
			"build", "cross-provider-message-order-mutation");

	public static void main(String[] args) throws IOException {
		new CrossProviderMessageOrderMutationTest()
				.testContentPassesWhileCrossProviderOrderFails();
		System.out.println(
				"Cross-provider message-order mutation test passed");
	}

	public void testContentPassesWhileCrossProviderOrderFails()
			throws IOException {
		Path directory = new CrossProviderBehaviorBrokenTraceFixturesGenerator()
				.generate(CORRECT, MUTATIONS)
				.stream()
				.filter(path -> path.getFileName().toString()
						.equals("ollama_reordered_request_messages"))
				.findFirst()
				.orElseThrow();

		BridgeDemoTraceFilesProcessor processor =
				new BridgeDemoTraceFilesProcessor();
		processor.setTraceDirectory(directory.toString());
		processor.processTraceFiles();

		assertPassed(
				processor.checkExpectedPrompts("Ollama"),
				"Expected Ollama prompts");
		assertPassed(
				processor.checkProviderGenericImagesMatch("Ollama"),
				"Ollama image comparison");
		assertPassed(
				processor.checkProviderGenericTextualContextWindowMatches(
						"Ollama"),
				"Ollama generic/native context-window agreement");
		assertFailed(
				processor
						.checkProviderIndependentNonStreamingRequestsMatchAcrossProviders(),
				"Gemini/Ollama canonical request order");
	}

	private void assertPassed(
			TraceCheckResult aResult,
			String aDescription) {
		if (!aResult.passed()) {
			throw new AssertionError(
					aDescription + " unexpectedly failed: "
							+ aResult.level3ErrorMessages());
		}
	}

	private void assertFailed(
			TraceCheckResult aResult,
			String aDescription) {
		if (aResult.passed()) {
			throw new AssertionError(
					aDescription + " unexpectedly passed");
		}
	}
}
