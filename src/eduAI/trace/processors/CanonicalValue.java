package eduAI.trace.processors;

public record CanonicalValue(
		CanonicalValueKind kind,
		String value) {
	public CanonicalValue {
		if (kind == null) {
			throw new IllegalArgumentException("Canonical value kind is required");
		}
		if (value == null) {
			throw new IllegalArgumentException("Canonical value is required");
		}
	}
}
