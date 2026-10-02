package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eduAI.trace.processors.ReflectiveValueCollector.ValueKind;
import eduAI.trace.processors.ReflectiveValueCollector.ValueOccurrence;
import java.util.function.Predicate;

/** Requires every non-ignored native canonical value in the generic dump. */
public class CanonicalValueContainmentComparator
		implements ProviderIndependentValueComparator {
	private final ReflectiveValueCollector valueCollector;

	public CanonicalValueContainmentComparator() {
		this(new ReflectiveValueCollector());
	}

	public CanonicalValueContainmentComparator(
			ReflectiveValueCollector aValueCollector) {
		valueCollector = aValueCollector == null
				? new ReflectiveValueCollector()
				: aValueCollector;
	}

	@Override
	public CanonicalValueComparison compare(
			String aProviderIndependentObjectDump,
			List<CanonicalValueOccurrence> someProviderCanonicalValues,
			Predicate<CanonicalValue> anIgnorePredicate) {
		Set<Integer> usedValueOffsets = new HashSet<>();
		List<ValueOccurrence> collectedValues =
				valueCollector.collect(aProviderIndependentObjectDump);
		ArrayList<CanonicalValueOccurrence> missing = new ArrayList<>();
		for (CanonicalValueOccurrence expected : safe(someProviderCanonicalValues)) {
			if (anIgnorePredicate != null
					&& anIgnorePredicate.test(expected.canonicalValue())) {
				continue;
			}
			int matchingOffset = valueCollector.matchingValueOffset(
					aProviderIndependentObjectDump,
					expected.canonicalValue().value(),
					usedValueOffsets);
			if (matchingOffset < 0) {
				List<Integer> componentOffsets = matchingStringComponentOffsets(
						collectedValues,
						usedValueOffsets,
						expected.canonicalValue());
				if (componentOffsets.isEmpty()) {
					missing.add(expected);
				} else {
					usedValueOffsets.addAll(componentOffsets);
				}
			} else {
				usedValueOffsets.add(matchingOffset);
			}
		}
		return new CanonicalValueComparison(
				missing.isEmpty(),
				missing,
				missing.isEmpty()
						? "all provider response values occur in the generic response"
						: missingDetail(missing));
	}

	private List<Integer> matchingStringComponentOffsets(
			List<ValueOccurrence> someAvailableValues,
			Set<Integer> someUsedOffsets,
			CanonicalValue anExpectedValue) {
		if (anExpectedValue.kind() != CanonicalValueKind.DATA
				|| anExpectedValue.value().isEmpty()) {
			return List.of();
		}
		ArrayList<Integer> matchedOffsets = new ArrayList<>();
		var compactExpected = eduAI.trace.TraceTextSummary.fromToken(anExpectedValue.value());
		if (compactExpected != null && compactExpected.sha256().isEmpty()) {
			// Native system instructions may combine multiple generic system parts.
			// Their edge/length summaries compose; hashes of ordinary text do not.
			List<String> pieces = new ArrayList<>();
			for (ValueOccurrence available : someAvailableValues) {
				var summary = eduAI.trace.TraceTextSummary.fromToken(available.value());
				if (available.kind() != ValueKind.STRING || someUsedOffsets.contains(available.offset())
						|| summary == null || !summary.sha256().isEmpty()) continue;
				pieces.add(available.value());
				matchedOffsets.add(available.offset());
				String combined = eduAI.trace.TraceTextSummary.concatenateEdges(pieces);
				if (eduAI.trace.TraceTextSummary.matches(combined, anExpectedValue.value())) return List.copyOf(matchedOffsets);
			}
			return List.of();
		}
		int expectedOffset = 0;
		for (ValueOccurrence available : someAvailableValues) {
			if (available.kind() != ValueKind.STRING
					|| available.value().isEmpty()
					|| someUsedOffsets.contains(available.offset())) {
				continue;
			}
			if (anExpectedValue.value().startsWith(
					available.value(), expectedOffset)) {
				matchedOffsets.add(available.offset());
				expectedOffset += available.value().length();
				if (expectedOffset == anExpectedValue.value().length()) {
					return matchedOffsets.size() > 1
							? List.copyOf(matchedOffsets)
							: List.of();
				}
			}
		}
		return List.of();
	}

	private List<CanonicalValueOccurrence> safe(
			List<CanonicalValueOccurrence> someValues) {
		return someValues == null ? List.of() : someValues;
	}

	private String missingDetail(
			List<CanonicalValueOccurrence> someMissingValues) {
		ArrayList<String> descriptions = new ArrayList<>();
		for (CanonicalValueOccurrence missing : someMissingValues) {
			descriptions.add(
					missing.location()
							+ " " + missing.canonicalValue().kind()
							+ "=\"" + escaped(missing.canonicalValue().value())
							+ "\"");
		}
		return "generic response is missing provider values " + descriptions;
	}

	private String escaped(String aValue) {
		return aValue.replace("\\", "\\\\").replace("\"", "'");
	}
}
