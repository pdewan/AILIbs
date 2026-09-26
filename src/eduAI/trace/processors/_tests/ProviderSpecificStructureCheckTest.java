package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProviderSpecificStructureCheckTest {
	public static void main(String[] args) throws IOException {
		ProviderSpecificStructureCheckTest test =
				new ProviderSpecificStructureCheckTest();
		test.testRegistryUsesClassesAndPrefixes();
		test.testFlattenedValuesRetainForbiddenTypesOnTheirPaths();
		test.testStructureWithoutLeafValuesRemainsDetectable();
		test.testCorrectAndMutatedTraceFiles();
		System.out.println("Provider-specific structure checks passed");
	}

	public void testFlattenedValuesRetainForbiddenTypesOnTheirPaths() {
		String dump = "root: generic.Root fields={nested: native.package.Value "
				+ "fields={text: java.lang.String value=\"hello\"}}";
		ProviderSpecificTypeRegistry registry =
				new ProviderSpecificTypeRegistry();
		registry.register("Provider", List.of(), List.of("native.package."));
		List<String> found = registry.providerSpecificTypesIn(
				new ReflectiveValueCollector().collect(dump));
		if (found.stream().noneMatch(value -> value.contains(
				"native.package.Value at root.nested.text"))) {
			throw new AssertionError(
					"Forbidden path type was not associated with its leaf: "
							+ found);
		}
	}

	public void testStructureWithoutLeafValuesRemainsDetectable() {
		ProviderSpecificTypeRegistry registry =
				new ProviderSpecificTypeRegistry();
		registry.register("Provider", List.of(), List.of("native.package."));
		String dump = "root: generic.Root fields={nested: "
				+ "native.package.Empty fields={}}";
		if (!new ReflectiveValueCollector().collect(dump).isEmpty()) {
			throw new AssertionError("Empty structure unexpectedly had leaf values");
		}
		List<String> found = registry.providerSpecificTypesIn(dump);
		if (!found.contains("native.package.Empty")) {
			throw new AssertionError(
					"Forbidden empty structural type was not detected: " + found);
		}
	}

	public void testRegistryUsesClassesAndPrefixes() {
		ProviderSpecificTypeRegistry registry =
				new ProviderSpecificTypeRegistry();
		registry.register(
				"Provider",
				List.of("native.exact.NativeRequest"),
				List.of("native.package."));
		List<String> found = registry.providerSpecificTypesIn(
				"root: native.exact.NativeRequest fields={nested: "
						+ "native.package.InternalValue}");
		if (!found.contains("native.exact.NativeRequest")
				|| !found.contains("native.package.InternalValue")) {
			throw new AssertionError(
					"Exact classes and package prefixes were not both detected: "
							+ found);
		}
	}

	public void testCorrectAndMutatedTraceFiles() throws IOException {
		BridgeDemoTraceFilesProcessor correct = processor(Path.of("correct_filtered"));
		TraceCheckResult correctRequests =
				correct.checkProviderGenericTextualContextWindowMatches();
		TraceCheckResult correctParameters =
				correct.checkProviderGenericParameterMatches();
		TraceCheckResult correctResponses =
				correct.checkProviderGenericResponseMatches();
		TraceCheckResult correctMetadata =
				correct.checkProviderGenericMetadataMatches();
		TraceCheckResult correctContinuity =
				correct.checkContextWindowContinuity();
		TraceCheckResult correctStreaming =
				correct.checkStreamingChunksReflectedInNextContextWindow();
		for (String provider : List.of(
				TraceProviderNames.GEMINI, TraceProviderNames.OLLAMA)) {
			if (!correct.checkProviderIndependentContextWindowTypes(provider).passed()
					|| !correct.checkProviderIndependentParameterStoreTypes(provider).passed()
					|| !correct.checkProviderIndependentResponseTypes(provider).passed()) {
				throw new AssertionError(
						"Correct traces contain provider-specific types for " + provider);
			}
		}
		assertCanonicalStructuresPopulated(correct);
		if (!correctRequests.passed()
				|| !correctParameters.passed()
				|| !correctResponses.passed()
				|| !correctMetadata.passed()
				|| !correctContinuity.passed()
				|| !correctStreaming.passed()) {
			throw new AssertionError(
					"Correct traces failed canonical checks: requests="
							+ correctRequests.level3ErrorMessages()
							+ " parameters="
							+ correctParameters.level3ErrorMessages()
							+ " responses="
							+ correctResponses.level3ErrorMessages()
							+ " metadata="
							+ correctMetadata.level3ErrorMessages()
							+ " continuity="
							+ correctContinuity.level3ErrorMessages()
							+ " streaming="
							+ correctStreaming.level3ErrorMessages());
		}

		try (var directories = Files.list(Path.of("provider_structure_broken"))) {
			for (Path directory : directories.filter(Files::isDirectory).toList()) {
				BridgeDemoTraceFilesProcessor mutated = processor(directory);
				String mutationName = directory.getFileName().toString();
				boolean requestMutation = mutationName.contains("request_sent");
				boolean parameterMutation = mutationName.contains(
						"providerindependentparameterstore");
				boolean responseMutation = mutationName.contains(
						"response_translated")
						&& mutationName.contains("providerindependentresponse");
				if (!requestMutation && !responseMutation) {
					continue;
				}
				String provider = mutationName.contains("provider_gemini")
						? TraceProviderNames.GEMINI : TraceProviderNames.OLLAMA;
				TraceCheckResult result = requestMutation
						? parameterMutation
								? mutated.checkProviderIndependentParameterStoreTypes(provider)
								: mutated.checkProviderIndependentContextWindowTypes(provider)
						: mutated.checkProviderIndependentResponseTypes(provider);
				boolean expectedError = result.errors().contains(
						TraceFilesProcessorError
								.PROVIDER_SPECIFIC_TYPE_IN_PROVIDER_INDEPENDENT_STRUCTURE)
						|| result.level3ErrorMessages().stream().anyMatch(
								message -> message.contains(
										"provider-specific types"));
				if (result.passed() || !expectedError) {
					throw new AssertionError(
							"Provider-specific structure mutation passed: "
									+ directory
									+ " messages="
									+ result.level3Messages());
				}
			}
		}
	}

	private void assertCanonicalStructuresPopulated(
			BridgeDemoTraceFilesProcessor aProcessor) {
		for (TraceRecord record :
				aProcessor.getProcessor().getTraceRecords().values()) {
			String event = record.getTraceLine().getEventName();
			if ("request_sent".equals(event)) {
				CanonicalTraceRequest independent =
						(CanonicalTraceRequest) record.getExtractedData()
								.get("providerIndependentCanonicalRequest");
				CanonicalTraceRequest dependent =
						(CanonicalTraceRequest) record.getExtractedData()
								.get("providerDependentCanonicalRequest");
				if (independent == null
						|| dependent == null
						|| independent.parameters().isEmpty()
						|| dependent.parameters().isEmpty()
						|| independent.contextWindow().isEmpty()
						|| dependent.contextWindow().isEmpty()) {
					throw new AssertionError(
							"Canonical request was not populated at "
									+ record.getTraceFileName()
									+ ":"
									+ record.getTraceFileLineNumber());
				}
			}
			if ("response_translated".equals(event)) {
				CanonicalTraceResponse independent =
						(CanonicalTraceResponse) record.getExtractedData()
								.get("providerIndependentCanonicalResponse");
				CanonicalTraceResponse dependent =
						(CanonicalTraceResponse) record.getExtractedData()
								.get("providerDependentCanonicalResponse");
				if (independent == null
						|| dependent == null
						|| independent.metadata().isEmpty()
						|| dependent.metadata().isEmpty()
						|| independent.messages().isEmpty()
						|| dependent.messages().isEmpty()) {
					throw new AssertionError(
							"Canonical response was not populated at "
									+ record.getTraceFileName()
									+ ":"
									+ record.getTraceFileLineNumber());
				}
			}
		}
	}

	private BridgeDemoTraceFilesProcessor processor(Path aDirectory) {
		BridgeDemoTraceFilesProcessor result =
				new BridgeDemoTraceFilesProcessor();
		result.setTraceDirectory(aDirectory.toString());
		result.processTraceFiles();
		return result;
	}
}
