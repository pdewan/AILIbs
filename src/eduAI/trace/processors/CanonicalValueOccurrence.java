package eduAI.trace.processors;

public record CanonicalValueOccurrence(
		CanonicalValue canonicalValue,
		String location) {
	public CanonicalValueOccurrence {
		if (canonicalValue == null) {
			throw new IllegalArgumentException("Canonical value is required");
		}
		location = location == null ? "" : location;
	}
}
