package eduAI.trace.processors;

import java.util.List;
import java.util.Map;

public interface ProviderTraceInterpreter {
	boolean canInterpretProviderContextWindow(String aContextWindowDump);

	List<GenericTracesFileProcessor.TraceMessage> providerContextWindowMessages(
			String aContextWindowDump);

	boolean canInterpretProviderConfiguration(String aConfigurationDump);

	List<GenericTracesFileProcessor.TraceMessage> providerConfigurationMessages(
			String aConfigurationDump);

	default List<GenericTracesFileProcessor.TraceMessage>
			providerResponseMessages(String aResponseDump) {
		return List.of();
	}

	default Map<String, String> providerConfigurationValues(
			String aConfigurationDump) {
		return Map.of();
	}

	default NativeMetadataRegistry nativeMetadataRegistry(String aSourceDump) {
		return null;
	}
}
