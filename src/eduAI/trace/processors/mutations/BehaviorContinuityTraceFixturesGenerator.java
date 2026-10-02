package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;

public class BehaviorContinuityTraceFixturesGenerator {
	private static final String GEMINI_NON_STREAMING_FILE =
			"TraceGeminiBridgeNonStreamingDemo.txt";
	private static final String OLLAMA_NON_STREAMING_FILE =
			"TraceOllamaNonStreamingBridgeDemo.txt";

	public void generate(Path aSource, Path aTarget) throws IOException {
		generateFixture(
				aSource,
				aTarget.resolve("missing_previous_system_prompt"),
				GEMINI_NON_STREAMING_FILE,
				2,
				line -> replaceTextEvidence(line,
						"You are movie expert",
						"Incorrect retained system prompt"));
		generateFixture(
				aSource,
				aTarget.resolve("missing_previous_user_prompt"),
				GEMINI_NON_STREAMING_FILE,
				3,
				line -> replaceTextEvidence(line,
						"Galahad?",
						"Incorrect retained user prompt"));
		generateFixture(
				aSource,
				aTarget.resolve("missing_previous_completed_response"),
				GEMINI_NON_STREAMING_FILE,
				3,
				this::changeAssistantRolesToUser);
		generateFixture(
				aSource,
				aTarget.resolve("ollama_missing_previous_system_prompt"),
				OLLAMA_NON_STREAMING_FILE,
				2,
				line -> replaceTextEvidence(line,
						"You are movie expert",
						"Incorrect retained system prompt"));
		generateFixture(
				aSource,
				aTarget.resolve("ollama_missing_previous_user_prompt"),
				OLLAMA_NON_STREAMING_FILE,
				3,
				line -> replaceTextEvidence(line,
						"Galahad?",
						"Incorrect retained user prompt"));
		generateFixture(
				aSource,
				aTarget.resolve("ollama_missing_previous_completed_response"),
				OLLAMA_NON_STREAMING_FILE,
				3,
				this::changeAssistantRolesToUser);
		generateMissingCompactionFixture(
				aSource,
				aTarget.resolve("missing_expected_compaction"));
		generateAllRequestTextFixture(
				aSource,
				aTarget.resolve("missing_expected_prompt"),
				"Do you understand the system instruction?",
				"Unexpected initial prompt");
		generateAllRequestTextFixture(
				aSource,
				aTarget.resolve("missing_expected_image"),
				"4884",
				"0");
	}

	private void generateFixture(
			Path aSource,
			Path aDestination,
			String aTraceFileName,
			int aRequestOccurrence,
			UnaryOperator<String> aMutation) throws IOException {
		Files.createDirectories(aDestination);
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			Path sourceFile = aSource.resolve(fileName);
			List<String> lines = Files.readAllLines(
					sourceFile,
					StandardCharsets.UTF_8);
			if (aTraceFileName.equals(fileName)) {
				lines = mutateRequestOccurrence(
						lines,
						aRequestOccurrence,
						aMutation);
			}
			Files.write(
					aDestination.resolve(fileName),
					lines,
					StandardCharsets.UTF_8);
		}
	}

	private List<String> mutateRequestOccurrence(
			List<String> someLines,
			int aRequestOccurrence,
			UnaryOperator<String> aMutation) {
		int occurrence = 0;
		for (int index = 0; index < someLines.size(); index++) {
			TraceLine traceLine = TraceLineParser.parse(someLines.get(index));
			if (traceLine == null
					|| !"request_sent".equals(traceLine.getEventName())) {
				continue;
			}
			occurrence++;
			if (occurrence == aRequestOccurrence) {
				String original = someLines.get(index);
				String changed = aMutation.apply(original);
				if (original.equals(changed)) {
					throw new IllegalStateException(
							"Continuity mutation changed no text at occurrence "
									+ aRequestOccurrence);
				}
				someLines.set(index, changed);
				return someLines;
			}
		}
		throw new IllegalStateException(
				"Missing request occurrence " + aRequestOccurrence);
	}

	private String changeAssistantRolesToUser(String aLine) {
		return aLine.replace("role=ASSISTANT", "role=USER")
				.replace("value=ASSISTANT", "value=USER")
				.replace("role=\\\"model\\\"", "role=\\\"user\\\"")
				.replace("role=assistant", "role=user")
				.replace("role=\\\"assistant\\\"", "role=\\\"user\\\"");
	}

	private void generateMissingCompactionFixture(
			Path aSource,
			Path aDestination) throws IOException {
		Files.createDirectories(aDestination);
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			List<String> lines = Files.readAllLines(
					aSource.resolve(fileName),
					StandardCharsets.UTF_8);
			if (fileName.contains("NonStreaming")) {
				int previous = requestLineIndex(lines, 3);
				int current = requestLineIndex(lines, 4);
				String changed = appendContextWindow(
						lines.get(current),
						lines.get(previous),
						"providerIndependentContextWindow");
				changed = appendContextWindow(
						changed,
						lines.get(previous),
						"providerDependentContextWindow");
				lines.set(current, changed);
			}
			Files.write(
					aDestination.resolve(fileName),
					lines,
					StandardCharsets.UTF_8);
		}
	}

	private void generateAllRequestTextFixture(
			Path aSource,
			Path aDestination,
			String aTargetText,
			String aReplacementText) throws IOException {
		Files.createDirectories(aDestination);
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			List<String> lines = Files.readAllLines(
					aSource.resolve(fileName),
					StandardCharsets.UTF_8);
			boolean changed = false;
			for (int index = 0; index < lines.size(); index++) {
				TraceLine traceLine = TraceLineParser.parse(lines.get(index));
				if (traceLine == null
						|| !"request_sent".equals(traceLine.getEventName())) {
					continue;
				}
				String original = lines.get(index);
				String replacement = replaceTextEvidence(original,
						aTargetText,
						aReplacementText);
				if (!original.equals(replacement)) {
					lines.set(index, replacement);
					changed = true;
				}
			}
			if (!changed) {
				throw new IllegalStateException(
						"Mutation changed no request text in " + fileName);
			}
			Files.write(
					aDestination.resolve(fileName),
					lines,
					StandardCharsets.UTF_8);
		}
	}

	/** Corrupt the same prompt whether its evidence is full text or a compact token. */
	private static String replaceTextEvidence(String line, String target, String replacement) {
		String changed = line.replace(target, replacement);
		var matcher = java.util.regex.Pattern.compile("@text:[0-9]+:[A-Za-z0-9_-]*:[A-Za-z0-9_-]*:[0-9a-f]*").matcher(changed);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			var summary = eduAI.trace.TraceTextSummary.fromToken(matcher.group());
			if (summary == null) continue;
			String prefix = summary.prefix().stripLeading();
			boolean matches = summary.prefix().contains(target) || summary.suffix().contains(target)
					|| (!prefix.isEmpty() && target.startsWith(prefix));
			if (matches) {
				var corrupted = new eduAI.trace.TraceTextSummary(summary.length() + 1,
						"Incorrect!", summary.suffix(), summary.sha256());
				matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(corrupted.token()));
			}
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private int requestLineIndex(List<String> someLines, int anOccurrence) {
		int occurrence = 0;
		for (int index = 0; index < someLines.size(); index++) {
			TraceLine traceLine = TraceLineParser.parse(someLines.get(index));
			if (traceLine != null
					&& "request_sent".equals(traceLine.getEventName())
					&& ++occurrence == anOccurrence) {
				return index;
			}
		}
		throw new IllegalStateException(
				"Missing request occurrence " + anOccurrence);
	}

	private String appendContextWindow(
			String aCurrentLine,
			String aPreviousLine,
			String aFieldName) {
		String currentValue = topLevelFieldValue(aCurrentLine, aFieldName);
		String previousValue = topLevelFieldValue(aPreviousLine, aFieldName);
		if (currentValue == null || previousValue == null) {
			throw new IllegalStateException(
					"Missing context-window field " + aFieldName);
		}
		String combined = quotedContents(currentValue)
				+ "\\r\\n"
				+ quotedContents(previousValue);
		return replaceTopLevelFieldValue(
				aCurrentLine,
				aFieldName,
				'"' + combined + '"');
	}

	private String quotedContents(String aValue) {
		return aValue.length() >= 2
				&& aValue.charAt(0) == '"'
				&& aValue.charAt(aValue.length() - 1) == '"'
						? aValue.substring(1, aValue.length() - 1)
						: aValue;
	}

	private String topLevelFieldValue(String aLine, String aFieldName) {
		for (String field : topLevelFields(aLine)) {
			int equals = field.indexOf('=');
			if (equals > 0
					&& aFieldName.equals(field.substring(0, equals).trim())) {
				return field.substring(equals + 1);
			}
		}
		return null;
	}

	private String replaceTopLevelFieldValue(
			String aLine,
			String aFieldName,
			String aValue) {
		int dataStart = aLine.indexOf(" <");
		int dataEnd = aLine.lastIndexOf("> ##");
		List<String> fields = topLevelFields(aLine);
		for (int index = 0; index < fields.size(); index++) {
			String field = fields.get(index);
			int equals = field.indexOf('=');
			if (equals > 0
					&& aFieldName.equals(field.substring(0, equals).trim())) {
				fields.set(index, field.substring(0, equals + 1) + aValue);
				return aLine.substring(0, dataStart + 2)
						+ String.join(" ", fields)
						+ aLine.substring(dataEnd);
			}
		}
		throw new IllegalStateException("Missing field " + aFieldName);
	}

	private List<String> topLevelFields(String aLine) {
		int dataStart = aLine.indexOf(" <");
		int dataEnd = aLine.lastIndexOf("> ##");
		String data = aLine.substring(dataStart + 2, dataEnd);
		java.util.ArrayList<String> fields = new java.util.ArrayList<>();
		int start = 0;
		boolean escaped = false;
		boolean quoted = false;
		for (int index = 0; index < data.length(); index++) {
			char character = data.charAt(index);
			if (escaped) {
				escaped = false;
			} else if (character == '\\') {
				escaped = true;
			} else if (character == '"') {
				quoted = !quoted;
			} else if (character == ' ' && !quoted) {
				fields.add(data.substring(start, index));
				start = index + 1;
			}
		}
		fields.add(data.substring(start));
		return fields;
	}

	public static void main(String[] args) throws IOException {
		Path source = args.length > 0
				? Path.of(args[0])
				: Path.of("correct_filtered");
		Path target = args.length > 1
				? Path.of(args[1])
				: Path.of("behavior_broken");
		new BehaviorContinuityTraceFixturesGenerator()
				.generate(source, target);
	}
}
