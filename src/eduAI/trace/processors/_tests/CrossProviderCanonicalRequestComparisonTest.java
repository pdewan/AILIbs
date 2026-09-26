package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CrossProviderCanonicalRequestComparisonTest {
	private static final Path CORRECT = Path.of("correct_filtered");
	private static final Path MUTATIONS = Path.of(
			"build", "cross-provider-canonical-input-mutations");

	public static void main(String[] args) throws IOException {
		CrossProviderCanonicalRequestComparisonTest test =
				new CrossProviderCanonicalRequestComparisonTest();
		test.testCorrectRequestsMatch();
		test.testChangedNativePromptFails();
		test.testChangedNativeImageFails();
		System.out.println(
				"Cross-provider canonical request comparisons passed");
	}

	public void testCorrectRequestsMatch() {
		assertPassed(
				processor(CORRECT)
						.checkProviderIndependentNonStreamingRequestsMatchAcrossProviders(),
				"correct requests");
	}

	public void testChangedNativePromptFails() throws IOException {
		Path directory = createWrongPromptDataset();
		assertFailed(
				processor(directory)
						.checkProviderIndependentNonStreamingRequestsMatchAcrossProviders(),
				"different native prompt");
	}

	public void testChangedNativeImageFails() throws IOException {
		List<Path> directories =
				new ImageComparisonBrokenTraceFixturesGenerator()
						.generate(CORRECT, MUTATIONS.resolve("images"));
		Path directory = directories.stream()
				.filter(path -> path.getFileName().toString()
						.equals("ollama_wrong_image_prefix"))
				.findFirst()
				.orElseThrow();
		assertFailed(
				processor(directory)
						.checkProviderIndependentNonStreamingRequestsMatchAcrossProviders(),
				"different native image");
	}

	private Path createWrongPromptDataset() throws IOException {
		Path directory = MUTATIONS.resolve("ollama_wrong_native_prompt");
		Files.createDirectories(directory);
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			List<String> lines = Files.readAllLines(
					CORRECT.resolve(fileName),
					StandardCharsets.UTF_8);
			if (fileName.equals("TraceOllamaNonStreamingBridgeDemo.txt")) {
				for (int i = 0; i < lines.size(); i++) {
					lines.set(i, replaceNativeContextValue(
							lines.get(i), "Galahad?", "Different prompt?"));
				}
			}
			Files.write(
					directory.resolve(fileName),
					lines,
					StandardCharsets.UTF_8);
		}
		return directory;
	}

	private String replaceNativeContextValue(
			String aLine,
			String anOldValue,
			String aNewValue) {
		int start = aLine.indexOf("providerDependentContextWindow=");
		int end = aLine.indexOf(" providerDependentConfiguration=", start);
		if (start < 0 || end < 0) {
			return aLine;
		}
		String field = aLine.substring(start, end)
				.replace(anOldValue, aNewValue);
		return aLine.substring(0, start) + field + aLine.substring(end);
	}

	private BridgeDemoTraceFilesProcessor processor(Path aDirectory) {
		BridgeDemoTraceFilesProcessor result =
				new BridgeDemoTraceFilesProcessor();
		result.setTraceDirectory(aDirectory.toString());
		result.processTraceFiles();
		return result;
	}

	private void assertPassed(TraceCheckResult aResult, String aDescription) {
		if (!aResult.passed()) {
			throw new AssertionError(
					aDescription + " unexpectedly failed: "
							+ aResult.level3ErrorMessages());
		}
	}

	private void assertFailed(TraceCheckResult aResult, String aDescription) {
		if (aResult.passed()) {
			throw new AssertionError(
					aDescription + " was not detected");
		}
	}
}
