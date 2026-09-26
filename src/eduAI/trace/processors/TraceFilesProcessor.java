package eduAI.trace.processors;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import eduAI.trace.lib.StreamingMode;

public interface TraceFilesProcessor {
	void registerProviderTraceInterpreter(
			ProviderTraceInterpreter anInterpreter);

	void registerProviderParameterChecker(
			ProviderParameterChecker aChecker);

	void registerProviderSpecificTypes(
			String aProvider,
			Collection<String> someClassNames,
			Collection<String> somePackagePrefixes);

	void registerProviderIndependentIgnoredValue(
			CanonicalValueKind aKind,
			String aValue);

	void setProviderIndependentValueComparator(
			ProviderIndependentValueComparator aComparator);

	void setProviderIndependentKeyValueComparator(
			ProviderIndependentKeyValueComparator aComparator);

	boolean isProviderIndependentValueIgnored(CanonicalValue aValue);

	List<CanonicalValue> providerIndependentIgnoredValues();

	void initializeTraceProcessing();

	void processTraceFile(
			String aProvider,
			StreamingMode aMode,
			Path aTraceFile);

	void processTraceFiles(
			String aProvider,
			Path aStreamingTraceFile,
			Path aNonStreamingTraceFile);

	void processTraceFiles(
			Map<String, GenericTracesFileProcessor.TraceFiles> aProviderTraceFiles);

	void processTraceFiles();

	TraceCheckResult checkTraceFilesExist();

	TraceCheckResult checkProviderNamesConsistent();

	TraceCheckResult checkRequestsTraced();

	TraceCheckResult checkTraceEventExists(
			String anEventName,
			Map<String, List<StreamingMode>> expectedProviderModes);

	void buildClassRegistry(Path aClassRegistryFile);

	void writeClassRegistry(Path aClassRegistryFile);

	List<TraceFilesProcessorError> generateClassRegistry(
			Path aClassRegistryFile);

	List<String> classesForTag(String aTag);

	List<String> tagsForClass(String aClassName);

	List<String> classTagDerivations(String aClassName);

	TraceCheckResult checkClassExistsForTag(String aTag);

	TraceCheckResult checkClassExistsForTag(
			String aTag,
			List<String> anExpectedTraceEvents);

	TraceCheckResult checkClassHasMultipleTags(
			String aClassName);

	void writeLineBasedTraceReport(Path aReportFile);

	void writeDeduplicatedTraceReport(Path aReportFile);

	void writeCheckBasedTraceReport(Path aReportFile);

	TraceCheckResult checkContextWindowContinuity();

	TraceCheckResult checkPreviousSystemPromptsRetained();
	TraceCheckResult checkPreviousSystemPromptsRetained(String aProvider);

	TraceCheckResult checkPreviousUserPromptsRetained();
	TraceCheckResult checkPreviousUserPromptsRetained(String aProvider);

	TraceCheckResult checkPreviousCompletedResponsesRetained();
	TraceCheckResult checkPreviousCompletedResponsesRetained(String aProvider);
	TraceCheckResult checkPreviousImagesRetained();
	TraceCheckResult checkPreviousImagesRetained(String aProvider);

	TraceCheckResult checkExpectedContextWindowCompactions();
	TraceCheckResult checkExpectedContextWindowCompactions(String aProvider);

	TraceCheckResult checkContextWindowContinuity(
			String aProvider,
			StreamingMode aMode);

	TraceCheckResult checkProviderGenericTextualContextWindowMatches();
	TraceCheckResult checkProviderGenericTextualContextWindowMatches(String aProvider);

	TraceCheckResult checkProviderGenericTextualContextWindowMatches(
			String aProvider,
			StreamingMode aMode);
	TraceCheckResult checkProviderIndependentContextWindowTypes(String aProvider);
	TraceCheckResult checkProviderIndependentParameterStoreTypes(String aProvider);
	TraceCheckResult checkProviderIndependentResponseTypes(String aProvider);

	TraceCheckResult checkProviderGenericParameterMatches();
	TraceCheckResult checkProviderGenericParameterMatches(String aProvider);

	TraceCheckResult checkProviderGenericParameterMatches(
			String aProvider,
			StreamingMode aMode);

	TraceCheckResult checkProviderGenericResponseMatches();
	TraceCheckResult checkProviderGenericResponseMatches(String aProvider);
	TraceCheckResult checkProviderGenericResponseMatches(
			String aProvider,
			StreamingMode aMode);

	TraceCheckResult checkProviderGenericMetadataMatches();
	TraceCheckResult checkProviderGenericMetadataMatches(String aProvider);
	TraceCheckResult checkProviderGenericMetadataMatches(
			String aProvider,
			StreamingMode aMode);

	TraceCheckResult checkExpectedInputs();

	TraceCheckResult checkExpectedPrompts();
	TraceCheckResult checkExpectedPrompts(String aProvider);

	TraceCheckResult checkProviderGenericImagesMatch();
	TraceCheckResult checkProviderGenericImagesMatch(String aProvider);
	TraceCheckResult checkExpectedGenericImagesMatch();
	TraceCheckResult checkExpectedGenericImagesMatch(String aProvider);

	TraceCheckResult checkExpectedInputs(
			String aProvider,
			StreamingMode aMode);

	TraceCheckResult checkStreamingChunksReflectedInNextContextWindow();

	TraceCheckResult checkStreamingChunksReflectedInNextContextWindow(
			String aProvider);
	TraceCheckResult checkStreamingChunksAccumulatedCorrectly();
	TraceCheckResult checkStreamingChunksAccumulatedCorrectly(String aProvider);

	TraceCheckResult checkStreamingCallbacksInvoked();

	TraceCheckResult checkPartialResponseCallbacksInvoked();
	TraceCheckResult checkPartialResponseCallbacksInvoked(String aProvider);

	TraceCheckResult checkCompleteResponseCallbacksInvoked();
	TraceCheckResult checkCompleteResponseCallbacksInvoked(String aProvider);

	TraceCheckResult checkStreamingCallbacksInvoked(
			String aProvider);

	TraceCheckResult checkProviderIndependentNonStreamingDataMatchesAcrossProviders();

	TraceCheckResult checkProviderIndependentNonStreamingRequestsMatchAcrossProviders();

	TraceCheckResult checkProviderIndependentNonStreamingResponsesMatchAcrossProviders();

	TraceCheckResult checkProviderIndependentNonStreamingDataMatchesAcrossProviders(
			String anEventName);

	Map<TraceRecordId, TraceRecord> getTraceRecords();

	List<Boolean> getContextWindowContinuityResults();

	List<Boolean> getRequestContextWindowMatchResults();
	List<Boolean> getRequestParameterMatchResults();
	List<Boolean> getResponseMatchResults();
	List<Boolean> getMetadataMatchResults();

	List<Boolean> getExpectedInputMatchResults();

	List<Boolean> getStreamingChunkContextWindowMatchResults();
	List<Boolean> getStreamingChunkAccumulationResults();

	List<Boolean> getStreamingCallbackInvocationResults();

	List<Boolean> getProviderIndependentNonStreamingMatchResults();

	List<TraceFilesProcessorError> getErrors();

	List<String> getCheckerMessages();

	List<String> getGroupedCheckerMessages();

	Map<String, String> getCheckerExplanations();

	boolean isFalseCheckMessagesAreWarnings();

	void setFalseCheckMessagesAreWarnings(
			boolean areFalseCheckMessagesWarnings);

	boolean isFalseCheckMessagesAreErrors();

	void setFalseCheckMessagesAreErrors(
			boolean areFalseCheckMessagesErrors);

	boolean isUncheckedCheckMessagesAreWarnings();

	void setUncheckedCheckMessagesAreWarnings(
			boolean areUncheckedCheckMessagesWarnings);

	boolean isUncheckedCheckMessagesAreErrors();

	void setUncheckedCheckMessagesAreErrors(
			boolean areUncheckedCheckMessagesErrors);

	boolean isTrueCheckMessagesAreWarnings();

	void setTrueCheckMessagesAreWarnings(
			boolean areTrueCheckMessagesWarnings);

	boolean isTrueCheckMessagesAreErrors();

	void setTrueCheckMessagesAreErrors(
			boolean areTrueCheckMessagesErrors);
}
