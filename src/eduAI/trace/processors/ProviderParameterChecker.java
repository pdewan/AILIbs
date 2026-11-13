package eduAI.trace.processors;

public interface ProviderParameterChecker {
	String providerName();

	/** Empty string means translated numeric values have native evidence and occur in the generic store. */
	String difference(TraceRecord request);

	record ParameterEvidence(
			String propertyValue,
			String providerDependentTarget,
			String providerDependentTargetState) {
	}
}
