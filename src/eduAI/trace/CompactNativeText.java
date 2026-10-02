package eduAI.trace;

import java.util.regex.Pattern;

/** Compact text leaves in the existing native dump grammar, retaining its structure. */
public final class CompactNativeText {
	private static final Pattern GEMINI = Pattern.compile("text=Optional\\[(.*?)\\](?=\\s*(?:,\\s*thought=|\\}|$))", Pattern.DOTALL);
	private static final Pattern JSON_CONTENT = Pattern.compile("(\"(?:content|thinking)\"\\s*:\\s*\")((?:\\\\.|[^\"\\\\])*+)(\")", Pattern.DOTALL);
	private CompactNativeText() { }
	public static String format(String raw, boolean configuration) {
		if (raw == null) return null;
		var m = GEMINI.matcher(raw);
		StringBuilder result = new StringBuilder();
		java.util.List<String> systems = new java.util.ArrayList<>();
		while (m.find()) {
			String original = m.group(1);
			String compact = TraceTextSummary.fromToken(original) != null ? original : TraceTextSummary.compact(original, configuration);
			if (configuration) systems.add(compact);
			m.appendReplacement(result, java.util.regex.Matcher.quoteReplacement("text=Optional[" + compact + "]"));
		}
		m.appendTail(result);
		m = JSON_CONTENT.matcher(result.toString());
		result = new StringBuilder();
		while (m.find()) {
			String text = TraceTextSummary.unescape(m.group(2));
			String compact = TraceTextSummary.fromToken(text) != null ? text : TraceTextSummary.compact(text, false);
			m.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(m.group(1)
					+ (compact.equals(text) ? m.group(2) : compact) + m.group(3)));
		}
		m.appendTail(result);
		// Separate system parts remain individually observable in the new format.
		if (configuration && !systems.isEmpty()) result.append(" systemTextSummaries=")
				.append(String.join(";", systems.stream().map(s -> {
					TraceTextSummary existing = TraceTextSummary.fromToken(s);
					return (existing == null ? TraceTextSummary.fromEdges(s) : existing.edgesOnly()).token();
				}).toList()));
		return result.toString();
	}
}
