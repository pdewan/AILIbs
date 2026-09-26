package eduAI.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TraceLineParser {
	private static final String TRACE_START = "##";
	private static final String TRACE_END = "##";
	private static final Pattern TRACE_PATTERN =
			Pattern.compile(
					"^##\\s+([^\\s{}\\[\\]()<>:]+)\\s+\\{([^}]+)\\}\\s*(?:\\[([^:\\]]+):\\s*([^\\]]*)\\])?\\s*(?:\\(([^:)]+):\\s*([^)]*)\\))?\\s*(?:<([^>]*)>)?\\s*(?:##)?\\s*$");

	private TraceLineParser() {
	}

	public static TraceLine parse(String aLine) {
		String line = firstTraceRecord(aLine);
		Matcher matcher = TRACE_PATTERN.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		Map<String, String> data =
				parseAuxiliaryData(matcher.group(7));
		return new TraceLine(
				matcher.group(1).trim(),
				matcher.group(2).trim(),
				value(matcher.group(3), "none"),
				value(matcher.group(4), ""),
				value(matcher.group(5), "none"),
				value(matcher.group(6), ""),
				data,
				line);
	}

	public static String firstTraceRecord(String aText) {
		String text = aText == null ? "" : aText;
		int start = text.indexOf(TRACE_START);
		while (start >= 0
				&& start + TRACE_START.length() < text.length()
				&& !Character.isWhitespace(
						text.charAt(start + TRACE_START.length()))) {
			start = text.indexOf(TRACE_START, start + TRACE_START.length());
		}
		if (start < 0) {
			return "";
		}
		int end = explicitEndIndex(text, start + TRACE_START.length());
		if (end >= 0) {
			return text
					.substring(start, end + TRACE_END.length())
					.trim();
		}
		int lineEnd = lineEndIndex(text, start);
		return text.substring(start, lineEnd).trim();
	}

	private static int explicitEndIndex(String aText, int aStart) {
		int index = aText.indexOf(TRACE_END, aStart);
		while (index >= 0) {
			if (isEscapedTraceEnd(aText, index)) {
				index = aText.indexOf(
						TRACE_END,
						index + TRACE_END.length());
				continue;
			}
			return index;
		}
		return -1;
	}

	private static boolean isEscapedTraceEnd(
			String aText,
			int anIndex) {
		return anIndex > 0 && aText.charAt(anIndex - 1) == '\\';
	}

	private static int lineEndIndex(String aText, int aStart) {
		int index = aStart;
		while (index < aText.length()) {
			char ch = aText.charAt(index);
			if (ch == '\r' || ch == '\n') {
				return index;
			}
			index++;
		}
		return aText.length();
	}

	private static String value(String aValue, String aDefaultValue) {
		return aValue == null ? aDefaultValue : aValue.trim();
	}

	static Map<String, String> parseAuxiliaryData(String aText) {
		Map<String, String> result = new LinkedHashMap<>();
		String text = aText == null ? "" : aText.trim();
		int index = 0;
		while (index < text.length()) {
			while (index < text.length()
					&& Character.isWhitespace(text.charAt(index))) {
				index++;
			}
			if (index >= text.length()) {
				break;
			}
			int keyStart = index;
			while (index < text.length()
					&& text.charAt(index) != '='
					&& !Character.isWhitespace(text.charAt(index))) {
				index++;
			}
			if (index >= text.length() || text.charAt(index) != '=') {
				break;
			}
			String key = text.substring(keyStart, index).trim();
			index++;
			String value;
			if (index < text.length() && text.charAt(index) == '"') {
				ParseResult parsed = quotedValue(text, index + 1);
				value = parsed.value();
				index = parsed.nextIndex();
			} else {
				int valueStart = index;
				while (index < text.length()
						&& !Character.isWhitespace(text.charAt(index))) {
					index++;
				}
				value = text.substring(valueStart, index);
			}
			if (!key.isEmpty()) {
				result.put(key, value);
			}
		}
		return result;
	}

	private static ParseResult quotedValue(String aText, int aStart) {
		StringBuilder builder = new StringBuilder();
		int index = aStart;
		while (index < aText.length()) {
			char ch = aText.charAt(index++);
			if (ch == '"') {
				return new ParseResult(builder.toString(), index);
			}
			if (ch == '\\' && index < aText.length()) {
				char escaped = aText.charAt(index++);
				if (escaped == 'n') {
					builder.append('\n');
				} else if (escaped == 'r') {
					builder.append('\r');
				} else if (escaped == 'x'
						&& index + 1 < aText.length()) {
					String hex = aText.substring(index, index + 2);
					if ("3c".equalsIgnoreCase(hex)) {
						builder.append('<');
						index += 2;
					} else if ("3e".equalsIgnoreCase(hex)) {
						builder.append('>');
						index += 2;
					} else if ("23".equalsIgnoreCase(hex)) {
						builder.append('#');
						index += 2;
					} else {
						builder.append(escaped);
					}
				} else {
					builder.append(escaped);
				}
			} else {
				builder.append(ch);
			}
		}
		return new ParseResult(builder.toString(), index);
	}

	private record ParseResult(String value, int nextIndex) {
	}
}
