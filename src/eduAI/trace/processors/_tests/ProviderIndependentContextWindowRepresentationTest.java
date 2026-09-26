package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies that context matching depends on values, not generic field layout. */
public class ProviderIndependentContextWindowRepresentationTest {
	private static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	private static final Path DEFAULT_REPRESENTATION_TARGET =
			Path.of("build", "provider_independent_context_representation");
	private static final Path DEFAULT_WRONG_VALUE_TARGET =
			Path.of("build", "provider_independent_context_wrong_value");
	private static final String CONTEXT_START =
			"providerIndependentContextWindow=\"";
	private static final String CONTEXT_END =
			"\" providerIndependentParameterStore=\"";

	public static void main(String[] args) throws IOException {
		new ProviderIndependentContextWindowRepresentationTest().test(
				DEFAULT_SOURCE,
				DEFAULT_REPRESENTATION_TARGET,
				DEFAULT_WRONG_VALUE_TARGET);
	}

	public void test(
			Path aSourceDirectory,
			Path aRepresentationTarget,
			Path aWrongValueTarget) throws IOException {
		assertPassed(aSourceDirectory, "Correct context traces failed");
		Files.createDirectories(aRepresentationTarget);
		Files.createDirectories(aWrongValueTarget);
		int renamedCount = 0;
		int wrongValueCount = 0;
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			String original = Files.readString(
					aSourceDirectory.resolve(fileName), StandardCharsets.UTF_8);
			Mutation renamed = mutateContextArguments(original, false);
			renamedCount += renamed.count();
			Files.writeString(
					aRepresentationTarget.resolve(fileName),
					renamed.text(),
					StandardCharsets.UTF_8);
			Mutation wrongValue = mutateContextArguments(renamed.text(), true);
			wrongValueCount += wrongValue.count();
			Files.writeString(
					aWrongValueTarget.resolve(fileName),
					wrongValue.text(),
					StandardCharsets.UTF_8);
		}
		if (renamedCount == 0 || wrongValueCount == 0) {
			throw new AssertionError("Context-window fixtures were not mutated");
		}
		assertPassed(
				aRepresentationTarget,
				"Renaming and reordering generic fields changed the verdict");
		TraceCheckResult wrongValueResult = result(aWrongValueTarget);
		if (wrongValueResult.passed()) {
			throw new AssertionError("A changed generic prompt value was accepted");
		}
		System.out.println(
				"Provider-independent context matching is value-based");
	}

	private Mutation mutateContextArguments(
			String aText,
			boolean shouldChangeValue) {
		StringBuilder result = new StringBuilder(aText.length());
		int cursor = 0;
		int count = 0;
		while (true) {
			int start = aText.indexOf(CONTEXT_START, cursor);
			if (start < 0) {
				result.append(aText, cursor, aText.length());
				break;
			}
			int valueStart = start + CONTEXT_START.length();
			int end = aText.indexOf(CONTEXT_END, valueStart);
			if (end < 0) {
				throw new IllegalArgumentException(
						"Unterminated providerIndependentContextWindow argument");
			}
			result.append(aText, cursor, valueStart);
			String context = aText.substring(valueStart, end);
			String mutated;
			if (shouldChangeValue) {
				mutated = context.replace(
						"You are movie expert", "You are music expert");
			} else {
				mutated = context
						.replace("role:", "speakerCode:")
						.replace("fields={text:", "fields={payload:")
						.replace(
								"fields={thinking: java.lang.String value=\\\"\\\", "
										+ "speakerCode:",
								"fields={speakerCode:");
			}
			if (!mutated.equals(context)) {
				count++;
			}
			result.append(mutated);
			cursor = end;
		}
		return new Mutation(result.toString(), count);
	}

	private TraceCheckResult result(Path aDirectory) {
		BridgeDemoTraceFilesProcessor processor =
				new BridgeDemoTraceFilesProcessor();
		processor.setTraceDirectory(aDirectory.toString());
		processor.processTraceFiles();
		return processor.checkProviderGenericTextualContextWindowMatches();
	}

	private void assertPassed(Path aDirectory, String aMessage) {
		TraceCheckResult result = result(aDirectory);
		if (!result.passed()) {
			throw new AssertionError(
					aMessage + ": " + result.level3ErrorMessages());
		}
	}

	private record Mutation(String text, int count) {
	}
}
