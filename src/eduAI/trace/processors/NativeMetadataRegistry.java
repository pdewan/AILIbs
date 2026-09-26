package eduAI.trace.processors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps generic metadata keys to values extracted from a native response. */
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

	/** Null means success; otherwise returns the precise mismatch. */
	public String difference(String source, String key, String actual) {
		Function<String, String> extractor = extractors.get(key);
		if (extractor == null) {
			return "unsupported metadata key " + key + "; supported keys=" + extractors.keySet();
		}
		String expected = source == null ? null : extractor.apply(source);
		if (expected == null) {
			return "native metadata field absent or unreadable for key " + key;
		}
		return expected.equals(actual) ? null
				: "metadata key " + key + " expected=" + expected + " actual=" + actual;
	}
}
