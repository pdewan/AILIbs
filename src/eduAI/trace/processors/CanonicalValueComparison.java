package eduAI.trace.processors;

import java.util.List;

public record CanonicalValueComparison(
		boolean matches,
		List<CanonicalValueOccurrence> missingValues,
		String detail) {
	public CanonicalValueComparison {
		missingValues = missingValues == null
				? List.of()
				: List.copyOf(missingValues);
		detail = detail == null ? "" : detail;
	}
}
