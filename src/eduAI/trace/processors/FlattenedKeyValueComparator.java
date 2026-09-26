package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Finds every expected key and value among flattened stored leaf values. */
public class FlattenedKeyValueComparator
		implements ProviderIndependentKeyValueComparator {
	private final ReflectiveValueCollector valueCollector;

	public FlattenedKeyValueComparator() {
		this(new ReflectiveValueCollector());
	}

	public FlattenedKeyValueComparator(
			ReflectiveValueCollector aValueCollector) {
		valueCollector = aValueCollector == null
				? new ReflectiveValueCollector()
				: aValueCollector;
	}

	@Override
	public KeyValueComparison compare(
			String aProviderIndependentStoreDump,
			Map<String, String> someExpectedProperties) {
		ArrayList<String> missingKeys = new ArrayList<>();
		ArrayList<String> missingValues = new ArrayList<>();
		Set<Integer> usedOffsets = new HashSet<>();
		for (Map.Entry<String, String> property : safe(someExpectedProperties)
				.entrySet()) {
			int keyOffset = valueCollector.matchingValueOffset(
					aProviderIndependentStoreDump,
					property.getKey(),
					usedOffsets);
			if (keyOffset < 0) {
				missingKeys.add(property.getKey());
			} else {
				usedOffsets.add(keyOffset);
			}
			int valueOffset = valueCollector.matchingValueOffset(
					aProviderIndependentStoreDump,
					property.getValue(),
					usedOffsets);
			if (valueOffset < 0) {
				missingValues.add(
						property.getKey() + "=" + property.getValue());
			} else {
				usedOffsets.add(valueOffset);
			}
		}
		boolean matches = missingKeys.isEmpty() && missingValues.isEmpty();
		return new KeyValueComparison(
				matches,
				missingKeys,
				missingValues,
				matches
						? "all expected keys and values occur in the generic store"
						: "missingKeys=" + missingKeys
								+ " missingValues=" + missingValues);
	}

	private Map<String, String> safe(Map<String, String> someProperties) {
		return someProperties == null ? Map.of() : someProperties;
	}
}
