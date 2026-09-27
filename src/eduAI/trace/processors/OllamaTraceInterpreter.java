package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class OllamaTraceInterpreter
		extends AbstractProviderTraceInterpreter {
	private static final Pattern MESSAGE_PATTERN =
			Pattern.compile(
					"OllamaChatMessage\\(role=([^,)]*), thinking=\\\\?\"(?:\\\\.|[^\"\\\\])*\\\\?\", response=\\\\?\"((?:\\\\.|[^\"\\\\])*)\\\\?\", images=(\\d+), imageBytes=(\\d+), imageSummary=imageBytes\\(count=(\\d+),prefix=([0-9a-f]*),suffix=([0-9a-f]*)\\)",
					Pattern.DOTALL);
	private static final Pattern RESPONSE_MESSAGE_PATTERN = Pattern.compile(
			"\\\\?\"role\\\\?\"\\s*:\\s*\\\\?\"assistant\\\\?\".*?"
					+ "\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*[,}]",
			Pattern.DOTALL);
	private static final Pattern TOTAL_DURATION_PATTERN = Pattern.compile(
			"totalDuration=(\\d+)");
	private static final Pattern PROMPT_EVAL_COUNT_PATTERN = Pattern.compile(
			"promptEvalCount=(\\d+)");
	private static final Pattern EVAL_COUNT_PATTERN = Pattern.compile(
			"evalCount=(\\d+)");
	private static final Pattern MODEL_PATTERN = Pattern.compile(
			"(?:^|\\()model=([^,)]*)");
	private static final Pattern DONE_REASON_PATTERN = Pattern.compile(
			"doneReason=([^,)]*)");

	@Override
	public boolean canInterpretProviderContextWindow(
			String aContextWindowDump) {
		return aContextWindowDump != null
				&& aContextWindowDump.contains("OllamaChatMessage(");
	}

	@Override
	public List<GenericTracesFileProcessor.TraceMessage>
			providerContextWindowMessages(String aContextWindowDump) {
		ArrayList<GenericTracesFileProcessor.TraceMessage> result =
				new ArrayList<>();
		java.util.regex.Matcher matcher =
				MESSAGE_PATTERN.matcher(aContextWindowDump);
		while (matcher.find()) {
			ArrayList<GenericTracesFileProcessor.TracePart> parts =
					new ArrayList<>();
			String text = unescapeTraceString(matcher.group(2));
			if (!text.isEmpty()) {
				parts.add(textPart(text));
			}
			if (integerValue(matcher.group(3)) > 0) {
				parts.add(imageBytesPart(
						matcher.group(5),
						matcher.group(6),
						matcher.group(7)));
			}
			result.add(message(matcher.group(1), parts));
		}
		return result;
	}

	@Override
	public List<GenericTracesFileProcessor.TraceMessage>
			providerResponseMessages(String aResponseDump) {
		if (aResponseDump == null) {
			return List.of();
		}
		java.util.regex.Matcher matcher =
				RESPONSE_MESSAGE_PATTERN.matcher(aResponseDump);
		String responseText = null;
		while (matcher.find()) {
			responseText = decodeJsonString(matcher.group(1));
		}
		return responseText == null
				? List.of()
				: List.of(message(
						"ASSISTANT",
						List.of(textPart(responseText))));
	}

	private String decodeJsonString(String aText) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < aText.length(); i++) {
			char character = aText.charAt(i);
			if (character != '\\') {
				result.append(character);
				continue;
			}
			if (++i >= aText.length()) {
				throw new IllegalArgumentException("Incomplete JSON escape");
			}
			char escaped = aText.charAt(i);
			switch (escaped) {
			case '"', '\\', '/' -> result.append(escaped);
			case 'n' -> result.append('\n');
			case 'r' -> result.append('\r');
			case 't' -> result.append('\t');
			case 'b' -> result.append('\b');
			case 'f' -> result.append('\f');
			case 'u' -> {
				if (i + 4 >= aText.length()) {
					throw new IllegalArgumentException("Incomplete JSON Unicode escape");
				}
				result.append((char) Integer.parseInt(aText.substring(i + 1, i + 5), 16));
				i += 4;
			}
			default -> throw new IllegalArgumentException("Invalid JSON escape: " + escaped);
			}
		}
		return result.toString();
	}

	private final NativeMetadataRegistry metadata = new NativeMetadataRegistry();

	public OllamaTraceInterpreter() {
		metadata.register("inputTokens", PROMPT_EVAL_COUNT_PATTERN);
		metadata.register("outputTokens", EVAL_COUNT_PATTERN);
		metadata.register("resolvedModel", MODEL_PATTERN);
		metadata.register("terminationReason", DONE_REASON_PATTERN);
		metadata.register("totalTimeMs", source -> {
			java.util.regex.Matcher matcher = TOTAL_DURATION_PATTERN.matcher(source);
			if (!matcher.find()) {
				return null;
			}
			try {
				return Long.toString(Long.parseLong(matcher.group(1)) / 1_000_000L);
			} catch (NumberFormatException e) {
				return null;
			}
		});
	}

	@Override
	public NativeMetadataRegistry nativeMetadataRegistry(String source) {
		return source != null && source.contains("OllamaChatResponseModel") ? metadata : null;
	}
}
