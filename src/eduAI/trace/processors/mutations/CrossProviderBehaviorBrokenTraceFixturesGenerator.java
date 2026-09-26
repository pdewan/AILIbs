package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

public class CrossProviderBehaviorBrokenTraceFixturesGenerator {
	public static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	public static final Path DEFAULT_TARGET =
			Path.of("cross_provider_behavior_broken");

	public List<Path> generate(Path aSource, Path aTarget)
			throws IOException {
		TraceFixtureMutator mutator = new TraceFixtureMutator();
		String ollamaTrace = "TraceOllamaNonStreamingBridgeDemo.txt";
		Path request = mutator.createBrokenDataset(
				aSource,
				aTarget,
				List.of(
						Mutation.wrongFieldValue(
								"request_sent",
								"providerIndependentContextWindow")
								.inFile(ollamaTrace)
								.atOccurrence(1),
						Mutation.wrongFieldValue(
								"request_sent",
								"providerDependentContextWindow")
								.inFile(ollamaTrace)
								.atOccurrence(1)))
				.directory();
		Path response = mutator.createBrokenDataset(
				aSource,
				aTarget,
				List.of(
						Mutation.wrongFieldValue(
								"response_translated",
								"providerIndependentResponse")
								.inFile(ollamaTrace)
								.atOccurrence(1),
						Mutation.wrongFieldValue(
								"response_translated",
								"providerDependentResponse")
								.inFile(ollamaTrace)
								.atOccurrence(1)))
				.directory();
		Path reorderedRequests = createReorderedRequestDataset(
				aSource,
				aTarget.resolve("ollama_reordered_request_messages"));
		return List.of(request, response, reorderedRequests);
	}

	private Path createReorderedRequestDataset(
			Path aSource,
			Path aTarget) throws IOException {
		Files.createDirectories(aTarget);
		String ollamaTrace = "TraceOllamaNonStreamingBridgeDemo.txt";
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			List<String> lines = new ArrayList<>(Files.readAllLines(
					aSource.resolve(fileName),
					StandardCharsets.UTF_8));
			if (ollamaTrace.equals(fileName)) {
				swapRequestContextWindows(lines, 2, 3);
			}
			Files.write(
					aTarget.resolve(fileName),
					lines,
					StandardCharsets.UTF_8);
		}
		return aTarget;
	}

	private void swapRequestContextWindows(
			List<String> someLines,
			int aFirstOccurrence,
			int aSecondOccurrence) {
		List<Integer> requestLines = new ArrayList<>();
		for (int i = 0; i < someLines.size(); i++) {
			if (someLines.get(i).contains("{request_sent}")) {
				requestLines.add(i);
			}
		}
		if (requestLines.size() < aSecondOccurrence) {
			throw new IllegalStateException(
					"Expected at least " + aSecondOccurrence
							+ " Ollama request_sent records");
		}
		int firstIndex = requestLines.get(aFirstOccurrence - 1);
		int secondIndex = requestLines.get(aSecondOccurrence - 1);
		String first = someLines.get(firstIndex);
		String second = someLines.get(secondIndex);
		for (String field : List.of(
				"providerIndependentContextWindow",
				"providerDependentContextWindow")) {
			String firstValue = quotedFieldValue(first, field);
			String secondValue = quotedFieldValue(second, field);
			first = replaceQuotedFieldValue(first, field, secondValue);
			second = replaceQuotedFieldValue(second, field, firstValue);
		}
		someLines.set(firstIndex, first);
		someLines.set(secondIndex, second);
	}

	private String quotedFieldValue(String aLine, String aField) {
		int start = quotedFieldValueStart(aLine, aField);
		int end = quotedFieldValueEnd(aLine, start);
		return aLine.substring(start, end);
	}

	private String replaceQuotedFieldValue(
			String aLine,
			String aField,
			String aValue) {
		int start = quotedFieldValueStart(aLine, aField);
		int end = quotedFieldValueEnd(aLine, start);
		return aLine.substring(0, start) + aValue + aLine.substring(end);
	}

	private int quotedFieldValueStart(String aLine, String aField) {
		String prefix = aField + "=\"";
		int prefixStart = aLine.indexOf(prefix);
		if (prefixStart < 0) {
			throw new IllegalStateException("Missing field " + aField);
		}
		return prefixStart + prefix.length();
	}

	private int quotedFieldValueEnd(String aLine, int aStart) {
		boolean escaped = false;
		for (int i = aStart; i < aLine.length(); i++) {
			char current = aLine.charAt(i);
			if (current == '\"' && !escaped) {
				return i;
			}
			if (current == '\\') {
				escaped = !escaped;
			} else {
				escaped = false;
			}
		}
		throw new IllegalStateException("Unterminated quoted trace field");
	}

	public static void main(String[] args) throws IOException {
		Path source = args.length > 0 ? Path.of(args[0]) : DEFAULT_SOURCE;
		Path target = args.length > 1 ? Path.of(args[1]) : DEFAULT_TARGET;
		if (args.length > 2) {
			throw new IllegalArgumentException(
					"Expected optional arguments: <sourceDirectory> <targetRoot>");
		}
		List<Path> generated =
				new CrossProviderBehaviorBrokenTraceFixturesGenerator()
						.generate(source, target);
		System.out.println(
				"Generated "
						+ generated.size()
						+ " cross-provider datasets under "
						+ target);
	}
}
