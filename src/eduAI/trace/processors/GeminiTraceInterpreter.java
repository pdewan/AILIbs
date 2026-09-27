package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GeminiTraceInterpreter
		extends AbstractProviderTraceInterpreter {
	private final NativeMetadataRegistry metadata = new NativeMetadataRegistry();

	public GeminiTraceInterpreter() {
		metadata.register("inputTokens", Pattern.compile(
				"usageMetadata=Optional\\[GenerateContentResponseUsageMetadata\\{.*?\\bpromptTokenCount=Optional\\[([0-9]+)\\]", Pattern.DOTALL));
		metadata.register("outputTokens", Pattern.compile(
				"usageMetadata=Optional\\[GenerateContentResponseUsageMetadata\\{.*?\\bcandidatesTokenCount=Optional\\[([0-9]+)\\]", Pattern.DOTALL));
		metadata.register("resolvedModel", Pattern.compile("\\bmodelVersion=Optional\\[([^\\]]+)\\]"));
		metadata.register("terminationReason", Pattern.compile("\\bfinishReason=Optional\\[([^\\]]+)\\]"));
	}

	@Override
	public NativeMetadataRegistry nativeMetadataRegistry(String source) {
		return source != null && source.contains("GenerateContentResponse") ? metadata : null;
	}
	private static final Pattern CONTENT_PATTERN =
			Pattern.compile(
					"com\\.google\\.genai\\.types\\.Content\\(role=\\\\?\"?([^\",)]*)\\\\?\"?, parts=\\[((?:\"(?:\\\\.|[^\"\\\\])*\"|[^\"\\]])*)\\]\\)",
					Pattern.DOTALL);
	private static final Pattern TEXT_PATTERN =
			Pattern.compile(
					"text\\(\\\\?\"((?:\\\\.|[^\"\\\\])*)\\\\?\"",
					Pattern.DOTALL);
	private static final Pattern IMAGE_PATTERN =
			Pattern.compile(
					"inlineData\\(mimeType=\\\\?\"?([^\",)]*)\\\\?\"?, "
							+ "imageBytes\\(count=(\\d+),prefix=([0-9a-f]*),"
							+ "suffix=([0-9a-f]*)\\)\\)");
	private static final Pattern SYSTEM_INSTRUCTION_PATTERN =
			Pattern.compile(
					"systemInstruction=Optional\\[Content\\{.*?text=Optional\\[(.*?)\\](?=\\s*(?:,\\s*thought=|\\}|$))",
					Pattern.DOTALL);
	private static final Pattern RESPONSE_TEXT_PATTERN = Pattern.compile(
			"text=Optional\\[(.*?)\\](?=\\s*(?:,\\s*thought=|\\}|$))",
			Pattern.DOTALL);

	@Override
	public boolean canInterpretProviderContextWindow(
			String aContextWindowDump) {
		return aContextWindowDump != null
				&& aContextWindowDump.contains(
						"com.google.genai.types.Content(");
	}

	@Override
	public List<GenericTracesFileProcessor.TraceMessage>
			providerContextWindowMessages(String aContextWindowDump) {
		ArrayList<GenericTracesFileProcessor.TraceMessage> result =
				new ArrayList<>();
		java.util.regex.Matcher matcher =
				CONTENT_PATTERN.matcher(aContextWindowDump);
		while (matcher.find()) {
			String role = providerRole(matcher.group(1));
			List<GenericTracesFileProcessor.TracePart> parts =
					geminiParts(matcher.group(2));
			if (!role.isBlank()) {
				result.add(message(role, parts));
			}
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
				RESPONSE_TEXT_PATTERN.matcher(aResponseDump);
		String responseText = null;
		while (matcher.find()) {
			responseText = unescapeTraceString(matcher.group(1));
		}
		return responseText == null
				? List.of()
				: List.of(message(
						"ASSISTANT",
						List.of(textPart(responseText))));
	}

	private List<GenericTracesFileProcessor.TracePart> geminiParts(
			String aPartsText) {
		ArrayList<GenericTracesFileProcessor.TracePart> result =
				new ArrayList<>();
		java.util.regex.Matcher textMatcher =
				TEXT_PATTERN.matcher(aPartsText);
		while (textMatcher.find()) {
			result.add(
					textPart(
							unescapeTraceString(textMatcher.group(1))));
		}
		java.util.regex.Matcher imageMatcher =
				IMAGE_PATTERN.matcher(aPartsText);
		while (imageMatcher.find()) {
			result.add(imageBytesPart(
					imageMatcher.group(2),
					imageMatcher.group(3),
					imageMatcher.group(4)));
		}
		return result;
	}

	@Override
	public boolean canInterpretProviderConfiguration(
			String aConfigurationDump) {
		return aConfigurationDump != null
				&& aConfigurationDump.contains("GenerateContentConfig");
	}

	@Override
	public List<GenericTracesFileProcessor.TraceMessage>
			providerConfigurationMessages(String aConfigurationDump) {
		if (aConfigurationDump == null) {
			return List.of();
		}
		ArrayList<GenericTracesFileProcessor.TraceMessage> result =
				new ArrayList<>();
		java.util.regex.Matcher matcher =
				SYSTEM_INSTRUCTION_PATTERN.matcher(aConfigurationDump);
		while (matcher.find()) {
			result.add(
					message(
							"SYSTEM",
							List.of(
									textPart(
											unescapeTraceString(
													matcher.group(1))))));
		}
		return result;
	}
}
