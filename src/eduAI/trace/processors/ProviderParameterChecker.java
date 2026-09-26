package eduAI.trace.processors;

public interface ProviderParameterChecker {
	String providerName();

	String parameterName();

	boolean isExpectedTarget(ParameterEvidence anEvidence);

	boolean isAssigned(ParameterEvidence anEvidence);

	record ParameterEvidence(
			String propertyName,
			String propertyValue,
			String parameterAdapterClass,
			String providerDependentTarget,
			String providerDependentTargetState) {
	}
}
