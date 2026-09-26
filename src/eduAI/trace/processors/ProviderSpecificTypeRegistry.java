package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProviderSpecificTypeRegistry {
	private static final Pattern QUALIFIED_TYPE = Pattern.compile(
			"(?<![A-Za-z0-9_$])"
					+ "[A-Za-z_$][A-Za-z0-9_$]*"
					+ "(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+"
					+ "(?:\\$[A-Za-z0-9_$]+)*");

	private final Map<String, ProviderSpecificTypes> typesByProvider =
			new LinkedHashMap<>();

	public record ProviderSpecificTypes(
			Set<String> classNames,
			Set<String> packagePrefixes) {
		public ProviderSpecificTypes {
			classNames = immutableNonBlankSet(classNames);
			packagePrefixes = immutableNonBlankSet(packagePrefixes);
		}
	}

	public void register(
			String aProvider,
			Collection<String> someClassNames,
			Collection<String> somePackagePrefixes) {
		if (aProvider == null || aProvider.isBlank()) {
			throw new IllegalArgumentException("Provider name is required");
		}
		typesByProvider.put(
				aProvider,
				new ProviderSpecificTypes(
						new LinkedHashSet<>(someClassNames),
						new LinkedHashSet<>(somePackagePrefixes)));
	}

	public Map<String, ProviderSpecificTypes> registrations() {
		return Map.copyOf(typesByProvider);
	}

	public boolean isProviderSpecificType(String aClassName) {
		return aClassName != null
				&& (isRegisteredClass(aClassName)
						|| hasRegisteredPackagePrefix(aClassName));
	}

	public List<String> providerSpecificTypesIn(String aReflectiveDump) {
		if (aReflectiveDump == null || aReflectiveDump.isBlank()) {
			return List.of();
		}
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (ProviderSpecificTypes types : typesByProvider.values()) {
			for (String className : types.classNames()) {
				if (containsTypeToken(aReflectiveDump, className)) {
					result.add(className);
				}
			}
		}
		Matcher matcher = QUALIFIED_TYPE.matcher(aReflectiveDump);
		while (matcher.find()) {
			String candidate = matcher.group();
			if (isRegisteredClass(candidate)
					|| hasRegisteredPackagePrefix(candidate)) {
				result.add(candidate);
			}
		}
		return new ArrayList<>(result);
	}

	public List<String> providerSpecificTypesIn(
			Collection<ReflectiveValueCollector.ValueOccurrence> someValues) {
		if (someValues == null || someValues.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (ReflectiveValueCollector.ValueOccurrence value : someValues) {
			for (String typeName : value.typesOnPath()) {
				if (isRegisteredClass(typeName)
						|| hasRegisteredPackagePrefix(typeName)) {
					result.add(typeName + " at " + value.path());
				}
			}
		}
		return new ArrayList<>(result);
	}

	private boolean containsTypeToken(
			String aDump,
			String aTypeName) {
		return Pattern.compile(
				"(?<![A-Za-z0-9_$])"
						+ Pattern.quote(aTypeName)
						+ "(?![A-Za-z0-9_$])")
				.matcher(aDump)
				.find();
	}

	private boolean isRegisteredClass(String aClassName) {
		for (ProviderSpecificTypes types : typesByProvider.values()) {
			if (types.classNames().contains(aClassName)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasRegisteredPackagePrefix(String aClassName) {
		for (ProviderSpecificTypes types : typesByProvider.values()) {
			for (String prefix : types.packagePrefixes()) {
				if (aClassName.startsWith(prefix)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Set<String> immutableNonBlankSet(
			Collection<String> someValues) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		if (someValues != null) {
			for (String value : someValues) {
				if (value != null && !value.isBlank()) {
					result.add(value);
				}
			}
		}
		return Set.copyOf(result);
	}
}
