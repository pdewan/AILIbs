package eduAI.trace.processors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts native metadata values without prescribing generic property names. */
public class NativeMetadataRegistry {
	private final Map<String, Function<String, String>> extractors = new LinkedHashMap<>();

	public void register(String key, Function<String, String> extractor) {
		extractors.put(java.util.Objects.requireNonNull(key),
				java.util.Objects.requireNonNull(extractor));
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
		return values.contains(actual) ? null
				: "translated metadata value absent from native metadata; actual=" + actual;
	}

	/** Compatibility overload; key is diagnostic information, never a matching rule. */
	public String difference(String source, String key, String actual) {
		return difference(source, actual);
	}
}
