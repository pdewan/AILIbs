package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts stored leaf values from a TraceObjectPrinter dump. */
public class ReflectiveValueCollector {
	public enum ValueKind {
		STRING,
		CHARACTER,
		NUMBER,
		BOOLEAN,
		ENUM_OR_SYMBOL
	}

	public record ValueOccurrence(
			ValueKind kind,
			String value,
			int offset,
			String path,
			List<String> typesOnPath) {
		public ValueOccurrence(
				ValueKind aKind,
				String aValue,
				int anOffset) {
			this(aKind, aValue, anOffset, "", List.of());
		}

		public ValueOccurrence {
			path = path == null ? "" : path;
			typesOnPath = typesOnPath == null
					? List.of()
					: List.copyOf(typesOnPath);
		}
	}

	private static final String VALUE_MARKER = " value=";
	private static final Pattern LABELED_TYPE = Pattern.compile(
			"(?:^|[,{\\[]\\s*)([^,:={}\\[\\]]+|\\[\\d+\\]|key|value)"
					+ ":\\s+([A-Za-z_$][A-Za-z0-9_$]*"
					+ "(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
					+ "(?:\\$[A-Za-z0-9_$]+)*)");
	private static final List<String> BODY_MARKERS =
			List.of(" fields={", " elements=[", " entries={");

	public int matchingValueOffset(
			String aDump,
			String anExpectedValue,
			Set<Integer> someExcludedOffsets) {
		if (aDump == null || anExpectedValue == null) {
			return -1;
		}
		Set<Integer> excluded = someExcludedOffsets == null
				? new HashSet<>()
				: someExcludedOffsets;
		int searchFrom = 0;
		while (true) {
			int marker = aDump.indexOf(VALUE_MARKER, searchFrom);
			if (marker < 0) {
				return -1;
			}
			int valueStart = marker + VALUE_MARKER.length();
			if (!excluded.contains(valueStart)
					&& storedValueEquals(aDump, valueStart, anExpectedValue)) {
				return valueStart;
			}
			searchFrom = valueStart;
		}
	}

	public int matchingSerializedValueOffset(
			String aDump,
			String anExpectedValue) {
		int reflectedOffset = matchingValueOffset(
				aDump, anExpectedValue, Set.of());
		if (reflectedOffset >= 0 || aDump == null
				|| anExpectedValue == null || anExpectedValue.isEmpty()) {
			return reflectedOffset;
		}
		int from = 0;
		while (from < aDump.length()) {
			int offset = aDump.indexOf(anExpectedValue, from);
			if (offset < 0) {
				return -1;
			}
			int end = offset + anExpectedValue.length();
			if (isSerializedBoundary(aDump, offset - 1)
					&& isSerializedBoundary(aDump, end)) {
				return offset;
			}
			from = offset + 1;
		}
		return -1;
	}

	private boolean isSerializedBoundary(String aText, int anOffset) {
		if (anOffset < 0 || anOffset >= aText.length()) {
			return true;
		}
		char character = aText.charAt(anOffset);
		return !Character.isLetterOrDigit(character)
				&& character != '_';
	}

	public List<ValueOccurrence> collect(String aDump) {
		if (aDump == null || aDump.isEmpty()) {
			return List.of();
		}
		ArrayList<ValueOccurrence> result = new ArrayList<>();
		List<StructuralFrame> frames = structuralFrames(aDump);
		int searchFrom = 0;
		while (true) {
			int marker = aDump.indexOf(VALUE_MARKER, searchFrom);
			if (marker < 0) {
				break;
			}
			int valueStart = marker + VALUE_MARKER.length();
			ParsedValue parsed = parseValue(aDump, valueStart);
			if (parsed != null && !isPrinterSentinel(parsed.value())) {
				result.add(occurrence(
						aDump,
						frames,
						parsed.kind(),
						parsed.value(),
						valueStart));
			}
			searchFrom = parsed == null
					? valueStart + 1
					: Math.max(valueStart + 1, parsed.endOffset());
		}
		return List.copyOf(result);
	}

	private ValueOccurrence occurrence(
			String aDump,
			List<StructuralFrame> someFrames,
			ValueKind aKind,
			String aValue,
			int anOffset) {
		ArrayList<StructuralFrame> pathFrames = new ArrayList<>();
		for (StructuralFrame frame : someFrames) {
			if (frame.bodyStart() < anOffset && anOffset < frame.bodyEnd()) {
				pathFrames.add(frame);
			}
		}
		pathFrames.sort(Comparator.comparingInt(StructuralFrame::bodyStart));
		LabeledType leaf = labeledTypeBefore(aDump, anOffset);
		ArrayList<String> labels = new ArrayList<>();
		LinkedHashSet<String> types = new LinkedHashSet<>();
		for (StructuralFrame frame : pathFrames) {
			if (!frame.label().isBlank()) {
				labels.add(frame.label());
			}
			if (!frame.typeName().isBlank()) {
				types.add(frame.typeName());
			}
		}
		if (leaf != null) {
			if (!leaf.label().isBlank()
					&& (labels.isEmpty()
							|| !leaf.label().equals(labels.get(labels.size() - 1)))) {
				labels.add(leaf.label());
			}
			if (!leaf.typeName().isBlank()) {
				types.add(leaf.typeName());
			}
		}
		return new ValueOccurrence(
				aKind,
				aValue,
				anOffset,
				String.join(".", labels),
				new ArrayList<>(types));
	}

	/** Actual runtime object types, including opaque SDK objects without leaf fields. */
	public List<ValueOccurrence> collectRuntimeTypes(String dump) {
		if (dump == null) return List.of();
		List<StructuralFrame> frames = structuralFrames(dump);
		List<ValueOccurrence> result = new ArrayList<>();
		Matcher types = LABELED_TYPE.matcher(maskQuotedText(dump));
		while (types.find()) result.add(occurrence(dump, frames, ValueKind.ENUM_OR_SYMBOL,
				types.group(2), types.end()));
		return List.copyOf(result);
	}

	private List<StructuralFrame> structuralFrames(String aDump) {
		ArrayList<StructuralFrame> result = new ArrayList<>();
		String unquoted = maskQuotedText(aDump);
		for (String marker : BODY_MARKERS) {
			int from = 0;
			while (from < aDump.length()) {
				int markerOffset = unquoted.indexOf(marker, from);
				if (markerOffset < 0) {
					break;
				}
				int bodyStart = markerOffset + marker.length() - 1;
				int bodyEnd = matchingDelimiter(aDump, bodyStart);
				LabeledType owner = labeledTypeBefore(aDump, markerOffset);
				if (bodyEnd > bodyStart && owner != null) {
					result.add(new StructuralFrame(
							bodyStart,
							bodyEnd,
							owner.label(),
							owner.typeName()));
				}
				from = bodyStart + 1;
			}
		}
		return result;
	}

	private int matchingDelimiter(String aText, int anOpeningOffset) {
		char opening = aText.charAt(anOpeningOffset);
		char closing = opening == '{' ? '}' : ']';
		int depth = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int index = anOpeningOffset; index < aText.length(); index++) {
			char character = aText.charAt(index);
			if (escaped) {
				escaped = false;
				continue;
			}
			if (character == '\\') {
				escaped = true;
				continue;
			}
			if (character == '"') {
				quoted = !quoted;
				continue;
			}
			if (quoted) {
				continue;
			}
			if (character == opening) {
				depth++;
			} else if (character == closing && --depth == 0) {
				return index;
			}
		}
		return -1;
	}

	private LabeledType labeledTypeBefore(String aDump, int anOffset) {
		int start = 0;
		String prefix = maskQuotedText(aDump.substring(start, anOffset));
		Matcher matcher = LABELED_TYPE.matcher(prefix);
		LabeledType result = null;
		while (matcher.find()) {
			result = new LabeledType(
					matcher.group(1).trim(), matcher.group(2));
		}
		return result;
	}

	private String maskQuotedText(String aText) {
		StringBuilder result = new StringBuilder(aText);
		boolean quoted = false;
		boolean escaped = false;
		for (int index = 0; index < result.length(); index++) {
			char character = result.charAt(index);
			if (escaped) {
				if (quoted) {
					result.setCharAt(index, ' ');
				}
				escaped = false;
				continue;
			}
			if (character == '\\') {
				escaped = true;
				if (quoted) {
					result.setCharAt(index, ' ');
				}
				continue;
			}
			if (character == '"') {
				quoted = !quoted;
				result.setCharAt(index, ' ');
			} else if (quoted) {
				result.setCharAt(index, ' ');
			}
		}
		return result.toString();
	}

	private ParsedValue parseValue(String aDump, int aStart) {
		if (aStart >= aDump.length()) {
			return null;
		}
		if (startsEscapedQuote(aDump, aStart)) {
			return parseQuoted(aDump, aStart + 2, true, ValueKind.STRING);
		}
		char first = aDump.charAt(aStart);
		if (first == '"') {
			return parseQuoted(aDump, aStart + 1, false, ValueKind.STRING);
		}
		if (first == '\'') {
			return parseCharacter(aDump, aStart + 1);
		}
		int end = aStart;
		while (end < aDump.length()
				&& !isBareValueDelimiter(aDump.charAt(end))) {
			end++;
		}
		String value = aDump.substring(aStart, end).trim();
		if (value.isEmpty()) {
			return null;
		}
		return new ParsedValue(bareKind(value), value, end);
	}

	private boolean storedValueEquals(
			String aDump,
			int aStart,
			String anExpectedValue) {
		if (aStart >= aDump.length()) {
			return false;
		}
		ParsedValue stored = parseValue(aDump, aStart);
		if (stored != null && (eduAI.trace.TraceTextSummary.fromToken(stored.value()) != null
				|| eduAI.trace.TraceTextSummary.fromToken(anExpectedValue) != null)) {
			return eduAI.trace.TraceTextSummary.matches(stored.value(), anExpectedValue);
		}
		boolean quoted = aDump.charAt(aStart) == '"';
		int sourceIndex = quoted ? aStart + 1 : aStart;
		int expectedIndex = 0;
		while (expectedIndex < anExpectedValue.length()) {
			DecodedCharacter decoded = decodedCharacter(
					aDump,
					sourceIndex,
					anExpectedValue.charAt(expectedIndex));
			if (decoded == null
					|| decoded.value() != anExpectedValue.charAt(expectedIndex)) {
				return false;
			}
			sourceIndex = decoded.nextOffset();
			expectedIndex++;
		}
		if (sourceIndex >= aDump.length()) {
			return true;
		}
		char next = aDump.charAt(sourceIndex);
		return quoted
				? next == '"'
				: isBareValueDelimiter(next);
	}

	private DecodedCharacter decodedCharacter(
			String aText,
			int anOffset,
			char anExpectedCharacter) {
		if (anOffset >= aText.length()) {
			return null;
		}
		char value = aText.charAt(anOffset);
		if (value == '\\' && anOffset + 1 < aText.length()) {
			char escaped = aText.charAt(anOffset + 1);
			char decoded = switch (escaped) {
			case 'n' -> '\n';
			case 'r' -> '\r';
			case 't' -> '\t';
			default -> '\0';
			};
			if (decoded == anExpectedCharacter) {
				return new DecodedCharacter(decoded, anOffset + 2);
			}
		}
		if (value == '\\' && anOffset + 1 < aText.length()
				&& (aText.charAt(anOffset + 1) == '\\'
						|| aText.charAt(anOffset + 1) == '"')) {
			return new DecodedCharacter(
					aText.charAt(anOffset + 1), anOffset + 2);
		}
		return new DecodedCharacter(value, anOffset + 1);
	}

	private ParsedValue parseQuoted(
			String aDump,
			int aContentStart,
			boolean isEscapedDelimiter,
			ValueKind aKind) {
		StringBuilder value = new StringBuilder();
		int index = aContentStart;
		while (index < aDump.length()) {
			if (isEscapedDelimiter && startsEscapedQuote(aDump, index)) {
				return new ParsedValue(aKind, value.toString(), index + 2);
			}
			char character = aDump.charAt(index);
			if (!isEscapedDelimiter && character == '"') {
				return new ParsedValue(aKind, value.toString(), index + 1);
			}
			if (character == '\\' && index + 1 < aDump.length()) {
				char escaped = aDump.charAt(index + 1);
				if (isEscapedDelimiter && escaped == '"') {
					return new ParsedValue(aKind, value.toString(), index + 2);
				}
				value.append(unescaped(escaped));
				index += 2;
				continue;
			}
			value.append(character);
			index++;
		}
		return null;
	}

	private ParsedValue parseCharacter(String aDump, int aContentStart) {
		if (aContentStart >= aDump.length()) {
			return null;
		}
		int index = aContentStart;
		char value = aDump.charAt(index);
		if (value == '\\' && index + 1 < aDump.length()) {
			value = unescaped(aDump.charAt(++index));
		}
		index++;
		if (index >= aDump.length() || aDump.charAt(index) != '\'') {
			return null;
		}
		return new ParsedValue(
				ValueKind.CHARACTER, String.valueOf(value), index + 1);
	}

	private ValueKind bareKind(String aValue) {
		if ("true".equals(aValue) || "false".equals(aValue)) {
			return ValueKind.BOOLEAN;
		}
		if (aValue.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")) {
			return ValueKind.NUMBER;
		}
		return ValueKind.ENUM_OR_SYMBOL;
	}

	private boolean startsEscapedQuote(String aText, int anIndex) {
		return anIndex + 1 < aText.length()
				&& aText.charAt(anIndex) == '\\'
				&& aText.charAt(anIndex + 1) == '"';
	}

	private boolean isBareValueDelimiter(char aCharacter) {
		return Character.isWhitespace(aCharacter)
				|| aCharacter == ','
				|| aCharacter == '}'
				|| aCharacter == ']';
	}

	private boolean isPrinterSentinel(String aValue) {
		return aValue.startsWith("<") && aValue.endsWith(">");
	}

	private char unescaped(char aCharacter) {
		return switch (aCharacter) {
		case 'n' -> '\n';
		case 'r' -> '\r';
		case 't' -> '\t';
		default -> aCharacter;
		};
	}

	private record ParsedValue(
			ValueKind kind,
			String value,
			int endOffset) {
	}

	private record DecodedCharacter(char value, int nextOffset) {
	}

	private record LabeledType(String label, String typeName) {
	}

	private record StructuralFrame(
			int bodyStart,
			int bodyEnd,
			String label,
			String typeName) {
	}
}
