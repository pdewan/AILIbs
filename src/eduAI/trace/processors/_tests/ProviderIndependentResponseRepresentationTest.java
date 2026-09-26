package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demonstrates that generic-response matching must not depend on field names
 * or field order.
 */
public class ProviderIndependentResponseRepresentationTest {
	private static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	private static final Path DEFAULT_TARGET =
			Path.of("build", "provider_independent_response_representation");
	private static final String RESPONSE_START =
			"providerIndependentResponse=\"";
	private static final String RESPONSE_END =
			"\" providerIndependentMetadata=\"";

	public static void main(String[] args) throws IOException {
		Path source = args.length > 0 ? Path.of(args[0]) : DEFAULT_SOURCE;
		Path target = args.length > 1 ? Path.of(args[1]) : DEFAULT_TARGET;
		if (args.length > 2) {
			throw new IllegalArgumentException(
					"Expected optional arguments: <sourceDirectory> <targetDirectory>");
		}
		new ProviderIndependentResponseRepresentationTest()
				.testRenamedGenericResponseFields(source, target);
	}

	public void testRenamedGenericResponseFields(
			Path aSourceDirectory,
			Path aTargetDirectory) throws IOException {
		assertResponseMatches(
				aSourceDirectory,
				"The unchanged correct dataset must pass before mutation");
		Files.createDirectories(aTargetDirectory);
		int changedResponses = 0;
		int reorderedResponses = 0;
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			Path source = aSourceDirectory.resolve(fileName);
			Path target = aTargetDirectory.resolve(fileName);
			String original = Files.readString(source, StandardCharsets.UTF_8);
			MutationResult mutation = renameResponseFields(original);
			changedResponses += mutation.changedResponses();
			reorderedResponses += mutation.reorderedResponses();
			Files.writeString(target, mutation.text(), StandardCharsets.UTF_8);
		}
		if (changedResponses == 0) {
			throw new AssertionError(
					"No provider-independent response fields were renamed");
		}
		if (reorderedResponses == 0) {
			throw new AssertionError(
					"No provider-independent response fields were reordered");
		}

		TraceCheckResult result = responseMatchResult(aTargetDirectory);
		if (!result.passed()) {
			throw new AssertionError(
					"A provider-independent response with unchanged values "
							+ "failed after only its field names and order changed. This "
							+ "demonstrates that the current checker assumes the "
							+ "generic response representation. errors="
							+ result.errors()
							+ " messages="
							+ result.level3ErrorMessages());
		}
		System.out.println(
				"Provider-independent response matching ignored generic field names");
	}

	private void assertResponseMatches(
			Path aTraceDirectory,
			String aFailureMessage) {
		TraceCheckResult result = responseMatchResult(aTraceDirectory);
		if (!result.passed()) {
			throw new AssertionError(
					aFailureMessage + ": " + result.level3ErrorMessages());
		}
	}

	private TraceCheckResult responseMatchResult(Path aTraceDirectory) {
		BridgeDemoTraceFilesProcessor processor =
				new BridgeDemoTraceFilesProcessor();
		processor.setTraceDirectory(aTraceDirectory.toString());
		processor.processTraceFiles();
		return processor.checkProviderGenericResponseMatches();
	}

	private MutationResult renameResponseFields(String aText) {
		StringBuilder result = new StringBuilder(aText.length());
		int cursor = 0;
		int changed = 0;
		int reorderedCount = 0;
		while (true) {
			int start = aText.indexOf(RESPONSE_START, cursor);
			if (start < 0) {
				result.append(aText, cursor, aText.length());
				break;
			}
			int valueStart = start + RESPONSE_START.length();
			int end = aText.indexOf(RESPONSE_END, valueStart);
			if (end < 0) {
				throw new IllegalArgumentException(
						"Unterminated providerIndependentResponse argument");
			}
			result.append(aText, cursor, valueStart);
			String response = aText.substring(valueStart, end);
			String renamed = response
					.replace("role:", "speakerCode:")
					.replace("fields={text:", "fields={payload:")
					.replace(
							"fields={thinking: java.lang.String value=\\\"\\\", "
									+ "speakerCode: eduAI.lib.message.AIRole "
									+ "value=ASSISTANT, originalPrompt: null, parts:",
							"fields={originalPrompt: null, speakerCode: "
									+ "eduAI.lib.message.AIRole value=ASSISTANT, "
									+ "thinking: java.lang.String value=\\\"\\\", parts:");
			if (!renamed.equals(response)) {
				changed++;
			}
			boolean reordered = renamed.contains(
					"fields={originalPrompt: null, speakerCode:");
			result.append(renamed);
			cursor = end;
			if (reordered) {
				reorderedCount++;
			}
		}
		return new MutationResult(
				result.toString(), changed, reorderedCount);
	}

	private record MutationResult(
			String text,
			int changedResponses,
			int reorderedResponses) {
	}
}
