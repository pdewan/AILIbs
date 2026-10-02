package eduAI.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A deterministic summary of complete text, with ten Unicode characters at each edge. */
public record TraceTextSummary(int length, String prefix, String suffix, String sha256) {
	private static final int EDGE_CHARACTERS = 10;
	private static final java.util.regex.Pattern SERIALIZED = java.util.regex.Pattern.compile(
			"textSummary\\(length=(\\d+),prefix=\"((?:\\\\.|[^\"\\\\])*)\",suffix=\"((?:\\\\.|[^\"\\\\])*)\",sha256=([0-9a-f]{64})\\)");

	/** Parse the entire summary; malformed or partial evidence is not accepted. */
	public static TraceTextSummary parse(String value) {
		if (value == null) return null;
		var m = SERIALIZED.matcher(value);
		if (!m.matches()) return null;
		try {
			return new TraceTextSummary(Integer.parseInt(m.group(1)), unescape(m.group(2)), unescape(m.group(3)), m.group(4));
		} catch (NumberFormatException e) { return null; }
	}

	public static String unescape(String value) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length()) {
				char next = value.charAt(++i);
				if (next == 'u' && i + 4 < value.length()) {
					result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16)); i += 4; continue;
				}
				result.append(switch (next) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'b' -> '\b'; case 'f' -> '\f'; default -> next; });
			} else result.append(c);
		}
		return result.toString();
	}

	public static TraceTextSummary from(String text) {
		java.util.Objects.requireNonNull(text, "text");
		int length = text.codePointCount(0, text.length());
		int edge = Math.min(EDGE_CHARACTERS, length);
		try {
			return new TraceTextSummary(length,
					text.substring(0, text.offsetByCodePoints(0, edge)),
					text.substring(text.offsetByCodePoints(text.length(), -edge)),
					HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
							.digest(text.getBytes(StandardCharsets.UTF_8))));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
		}
	}

	/** System prompt evidence deliberately omits a digest, including its computation. */
	public static TraceTextSummary fromEdges(String text) {
		java.util.Objects.requireNonNull(text, "text");
		int length = text.codePointCount(0, text.length());
		int edge = Math.min(EDGE_CHARACTERS, length);
		return new TraceTextSummary(length,
				text.substring(0, text.offsetByCodePoints(0, edge)),
				text.substring(text.offsetByCodePoints(text.length(), -edge)), "");
	}

	public String formatted() {
		return "textSummary(length=" + length + ",prefix=\"" + escape(prefix)
				+ "\",suffix=\"" + escape(suffix) + "\""
				+ (sha256.isEmpty() ? "" : ",sha256=" + sha256) + ")";
	}

	/** ASCII-only token: safe inside quoted native dumps and reflective string fields. */
	public String token() {
		return "@text:" + length + ":" + encode(prefix) + ":" + encode(suffix) + ":" + sha256;
	}
	public TraceTextSummary edgesOnly() { return new TraceTextSummary(length, prefix, suffix, ""); }
	public static TraceTextSummary fromToken(String token) {
		if (token == null || !token.startsWith("@text:")) return null;
		String[] fields = token.split(":", -1);
		if (fields.length != 5 || !fields[4].matches("(?:[0-9a-f]{64})?")) return null;
		try {
			int length = Integer.parseInt(fields[1]);
			String prefix = decode(fields[2]), suffix = decode(fields[3]);
			if (length < 0 || prefix.codePointCount(0, prefix.length()) != Math.min(10, length)
					|| suffix.codePointCount(0, suffix.length()) != Math.min(10, length)) return null;
			return new TraceTextSummary(length, prefix, suffix, fields[4]);
		} catch (IllegalArgumentException e) { return null; }
	}
	public static String compact(String text, boolean systemPrompt) {
		if (text.codePointCount(0, text.length()) <= 20 && !text.startsWith("@text:")) return text;
		return (systemPrompt ? fromEdges(text) : from(text)).token();
	}
	public static boolean matches(String left, String right) {
		TraceTextSummary a = fromToken(left), b = fromToken(right);
		if (a == null) a = from(left);
		if (b == null) b = from(right);
		return a.length == b.length && a.prefix.equals(b.prefix) && a.suffix.equals(b.suffix)
				&& (a.sha256.isEmpty() || b.sha256.isEmpty() || a.sha256.equals(b.sha256));
	}
	/** Used only for system instructions, where the requested contract omits hashes. */
	public static String concatenateEdges(java.util.List<String> pieces) {
		int length = 0;
		String prefix = "", suffix = "";
		for (String piece : pieces) {
			TraceTextSummary s = fromToken(piece);
			if (s == null) s = fromEdges(piece);
			length = Math.addExact(length, s.length);
			prefix += s.prefix;
			if (prefix.codePointCount(0, prefix.length()) > 10) prefix = prefix.substring(0, prefix.offsetByCodePoints(0, 10));
			suffix += s.suffix;
			if (suffix.codePointCount(0, suffix.length()) > 10) suffix = suffix.substring(suffix.offsetByCodePoints(suffix.length(), -10));
		}
		return new TraceTextSummary(length, prefix, suffix, "").token();
	}
	private static String encode(String text) {
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}
	private static String decode(String text) {
		return new String(java.util.Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
	}
}
