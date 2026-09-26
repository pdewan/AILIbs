package eduAI.trace.processors;

import java.util.List;

public record KeyValueComparison(
		boolean matches,
		List<String> missingKeys,
		List<String> missingValues,
		String detail) {
	public KeyValueComparison {
		missingKeys = missingKeys == null ? List.of() : List.copyOf(missingKeys);
		missingValues = missingValues == null
				? List.of()
				: List.copyOf(missingValues);
		detail = detail == null ? "" : detail;
	}
}
