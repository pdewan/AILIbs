package eduAI.trace.processors;

import java.util.List;

public class TextProviderParameterChecker
		implements ProviderParameterChecker {
	private final String providerName;
	private final String parameterName;
	private final List<String> targetMarkers;
	private final List<String> assignmentMarkers;

	public TextProviderParameterChecker(
			String aProviderName,
			String aParameterName,
			String aTargetMarker,
			String... someAssignmentMarkers) {
		providerName = aProviderName;
		parameterName = aParameterName;
		targetMarkers = List.of(aTargetMarker);
		assignmentMarkers = List.of(someAssignmentMarkers);
	}

	@Override
	public String providerName() {
		return providerName;
	}

	@Override
	public String parameterName() {
		return parameterName;
	}

	@Override
	public boolean isExpectedTarget(ParameterEvidence anEvidence) {
		String searchable = String.valueOf(
				anEvidence.providerDependentTarget());
		if (isOllamaOptionsMap(searchable)) return true;
		for (String marker : targetMarkers) {
			if (searchable.contains(marker)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isAssigned(ParameterEvidence anEvidence) {
		String searchable = String.valueOf(
				anEvidence.providerDependentTargetState());
		if (isOllamaOptionsMap(anEvidence.providerDependentTarget())) {
			return new FlattenedKeyValueComparator().compare(searchable,
					java.util.Map.of(parameterName, anEvidence.propertyValue())).matches();
		}
		for (String marker : assignmentMarkers) {
			if (searchable.contains(marker)) {
				return true;
			}
		}
		return false;
	}
	private boolean isOllamaOptionsMap(String dump) {
		if (!"Ollama".equalsIgnoreCase(providerName) || !"temperature".equalsIgnoreCase(parameterName) || dump == null) return false;
		int entries = dump.indexOf(" entries={"), fields = dump.indexOf(" fields={");
		return entries >= 0 && (fields < 0 || entries < fields);
	}
}
