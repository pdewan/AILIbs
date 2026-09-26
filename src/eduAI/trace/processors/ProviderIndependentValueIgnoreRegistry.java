package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProviderIndependentValueIgnoreRegistry {
	private final Map<CanonicalValueKind, Set<String>> ignoredValues =
			new LinkedHashMap<>();

	public void register(CanonicalValueKind aKind, String aValue) {
		if (aKind == null || aValue == null) {
			throw new IllegalArgumentException(
					"Ignored canonical value kind and value are required");
		}
		ignoredValues.computeIfAbsent(
				aKind, ignoredKind -> new LinkedHashSet<>()).add(aValue);
	}

	public void register(
			CanonicalValueKind aKind,
			Collection<String> someValues) {
		if (someValues == null) {
			throw new IllegalArgumentException(
					"Ignored canonical values are required");
		}
		for (String value : someValues) {
			register(aKind, value);
		}
	}

	public boolean isIgnored(CanonicalValue aValue) {
		if (aValue == null) {
			return false;
		}
		return ignoredValues
				.getOrDefault(aValue.kind(), Set.of())
				.contains(aValue.value());
	}

	public List<CanonicalValue> ignoredValues() {
		ArrayList<CanonicalValue> result = new ArrayList<>();
		for (Map.Entry<CanonicalValueKind, Set<String>> entry :
				ignoredValues.entrySet()) {
			for (String value : entry.getValue()) {
				result.add(new CanonicalValue(entry.getKey(), value));
			}
		}
		return List.copyOf(result);
	}

	public void clear() {
		ignoredValues.clear();
	}
}
