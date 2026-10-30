package eduAI.trace.processors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts native metadata values without prescribing generic property names. */
public class NativeMetadataRegistry {
	private static final Pattern DURATION_DUMP = Pattern.compile(
			"^[^:]+: java\\.time\\.Duration interfaces=\\[[^]]*\\] toString=\"([^\"]+)\"$");
	private final Map<String, Function<String, String>> extractors = new LinkedHashMap<>();
	private final Map<String, String> meanings = new LinkedHashMap<>();

	public void register(String key, Function<String, String> extractor) {
		register(key, key, extractor);
	}

	/** Alternative representations (for example nanos and Duration) retain one meaning. */
	public void register(String key, String meaning, Function<String, String> extractor) {
		extractors.put(java.util.Objects.requireNonNull(key),
				java.util.Objects.requireNonNull(extractor));
		meanings.put(key, java.util.Objects.requireNonNull(meaning));
	}

	/** All possible native meanings; equal values deliberately remain ambiguous. */
	public java.util.Set<String> matchingFields(String source, String actual) {
		var matches = new java.util.LinkedHashSet<String>();
		if (source != null) extractors.forEach((key, extractor) -> {
			String value = extractor.apply(source);
			if (value != null && value.equals(durationValue(actual))) matches.add(meanings.get(key));
		});
		return matches;
	}

	public void register(String key, Pattern pattern) {
		register(key, source -> {
			Matcher matcher = pattern.matcher(source);
			return matcher.find() ? matcher.group(1) : null;
		});
	}

	/** The generic property name is deliberately not an input to correctness. */
	public String difference(String source, String actual) {
		java.util.Set<String> values = new java.util.LinkedHashSet<>();
		if (source != null) for (Function<String, String> extractor : extractors.values()) {
			String value = extractor.apply(source);
			if (value != null) values.add(value);
		}
		if (values.isEmpty()) return "native metadata values absent or unreadable";
		return values.contains(durationValue(actual)) ? null
				: "translated metadata value absent from native metadata; actual=" + actual;
	}

	private static String durationValue(String actual) {
		if (actual == null) return null;
		Matcher dump = DURATION_DUMP.matcher(actual);
		String value = dump.matches() ? dump.group(1) : actual;
		try {
			return java.time.Duration.parse(value).toString();
		} catch (java.time.format.DateTimeParseException e) {
			return actual;
		}
	}

	/** Compatibility overload; key is diagnostic information, never a matching rule. */
	public String difference(String source, String key, String actual) {
		return difference(source, actual);
	}
}
