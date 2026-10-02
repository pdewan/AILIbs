package eduAI.trace;

/** Reads the generic text-part representation already used by the local checks. */
public final class TraceMessageText {
	private static final java.util.regex.Pattern TEXT = java.util.regex.Pattern.compile(
			"fields=\\{text:\\s+java\\.lang\\.String\\s+value=\"((?:\\\\.|[^\"\\\\])*+)\"", java.util.regex.Pattern.DOTALL);
	private static final java.util.regex.Pattern EMPTY_PARTS = java.util.regex.Pattern.compile(
			"parts:\\s+[^\\r\\n]*? elements=\\[(?:\\[\\d+\\]: null(?:,\\s*)?)*\\]");
	private TraceMessageText() { }
	public static String fromFullDump(String dump) {
		if (dump == null) return null;
		var matcher = TEXT.matcher(dump);
		StringBuilder result = new StringBuilder();
		boolean found = false;
		while (matcher.find()) { found = true; result.append(TraceTextSummary.unescape(matcher.group(1))); }
		// A terminal provider chunk can contain no text part. Require an explicit
		// empty/null-only parts collection; arbitrary unreadable objects stay invalid.
		return found ? result.toString() : EMPTY_PARTS.matcher(dump).find() ? "" : null;
	}
}
