package eduAI.trace.processors;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import eduAI.trace.lib.StreamingMode;

public class BridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public BridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate();
	}

	public BridgeDemoTraceFilesProcessor(
			Path aTraceDirectory,
			GenericTracesFileProcessor.ExpectedInputs anExpectedInputs) {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(
				aTraceDirectory,
				anExpectedInputs);
	}

	public void setTraceDirectory(String aTraceDirectory) {
		delegate.setTraceDirectory(aTraceDirectory);
	}

	public List<TraceFilesProcessorError> generateClassRegistry() {
		return delegate.generateClassRegistry();
	}

	public void processTraceFiles() {
		delegate.processTraceFiles();
	}

	public TraceCheckResult checkTraceFilesExist() {
		return delegate.checkTraceFilesExist();
	}

	public TraceCheckResult checkProviderNamesConsistent() {
		return delegate.checkProviderNamesConsistent();
	}

	public TraceCheckResult checkRequestsTraced() {
		return delegate.checkRequestsTraced();
	}

	public TraceCheckResult checkTraceEventExists(
			String anEventName,
			Map<String, List<StreamingMode>> expectedProviderModes) {
		return delegate.checkTraceEventExists(
				anEventName,
				expectedProviderModes);
	}

	public void buildClassRegistry() {
		delegate.buildClassRegistry();
	}

	public List<String> classesForTag(String aTag) {
		return delegate.classesForTag(aTag);
	}

	public List<String> tagsForClass(String aClassName) {
		return delegate.tagsForClass(aClassName);
	}

	public List<String> classTagDerivations(String aClassName) {
		return delegate.classTagDerivations(aClassName);
	}

	public TraceCheckResult checkClassExistsForTag(
			String aTag) {
		return delegate.checkClassExistsForTag(aTag);
	}

	public TraceCheckResult checkClassHasMultipleTags(
			String aClassName) {
		return delegate.checkClassHasMultipleTags(aClassName);
	}

	public Path classRegistryFile() {
		return delegate.classRegistryFile();
	}

	public Path lineBasedTraceReportFile() {
		return delegate.lineBasedTraceReportFile();
	}

	public Path deduplicatedTraceReportFile() {
		return delegate.deduplicatedTraceReportFile();
	}

	public Path checkBasedTraceReportFile() {
		return delegate.checkBasedTraceReportFile();
	}

	public List<TraceFilesProcessorError> runChecks() {
		return delegate.runChecks();
	}

	public void writeTraceReports() {
		delegate.writeTraceReports();
	}

	public TraceCheckResult checkContextWindowContinuity() {
		return delegate.checkContextWindowContinuity();
	}

	public TraceCheckResult checkPreviousSystemPromptsRetained() {
		return delegate.checkPreviousSystemPromptsRetained();
	}
	public TraceCheckResult checkPreviousSystemPromptsRetained(String p) { return delegate.checkPreviousSystemPromptsRetained(p); }

	public TraceCheckResult checkPreviousUserPromptsRetained() {
		return delegate.checkPreviousUserPromptsRetained();
	}
	public TraceCheckResult checkPreviousUserPromptsRetained(String p) { return delegate.checkPreviousUserPromptsRetained(p); }

	public TraceCheckResult checkPreviousCompletedResponsesRetained() {
		return delegate.checkPreviousCompletedResponsesRetained();
	}
	public TraceCheckResult checkPreviousCompletedResponsesRetained(String p) { return delegate.checkPreviousCompletedResponsesRetained(p); }
	public TraceCheckResult checkPreviousImagesRetained() {
		return delegate.checkPreviousImagesRetained();
	}
	public TraceCheckResult checkPreviousImagesRetained(String p) {
		return delegate.checkPreviousImagesRetained(p);
	}

	public TraceCheckResult checkExpectedContextWindowCompactions() {
		return delegate.checkExpectedContextWindowCompactions();
	}
	public TraceCheckResult checkExpectedContextWindowCompactions(String p) { return delegate.checkExpectedContextWindowCompactions(p); }

	public TraceCheckResult checkProviderGenericTextualContextWindowMatches() {
		return delegate.checkProviderGenericTextualContextWindowMatches();
	}
	public TraceCheckResult checkProviderGenericTextualContextWindowMatches(String p) { return delegate.checkProviderGenericTextualContextWindowMatches(p); }
	public TraceCheckResult checkProviderIndependentContextWindowTypes(String p) { return delegate.checkProviderIndependentContextWindowTypes(p); }
	public TraceCheckResult checkProviderIndependentParameterStoreTypes(String p) { return delegate.checkProviderIndependentParameterStoreTypes(p); }
	public TraceCheckResult checkProviderIndependentResponseTypes(String p) { return delegate.checkProviderIndependentResponseTypes(p); }

	public TraceCheckResult checkProviderGenericParameterMatches() {
		return delegate.checkProviderGenericParameterMatches();
	}
	public TraceCheckResult checkProviderGenericParameterMatches(String p) { return delegate.checkProviderGenericParameterMatches(p); }

	public TraceCheckResult checkProviderGenericResponseMatches() {
		return delegate.checkProviderGenericResponseMatches();
	}
	public TraceCheckResult checkProviderGenericResponseMatches(String p) { return delegate.checkProviderGenericResponseMatches(p); }

	public TraceCheckResult checkProviderGenericMetadataMatches() {
		return delegate.checkProviderGenericMetadataMatches();
	}
	public TraceCheckResult checkProviderGenericMetadataMatches(String p) { return delegate.checkProviderGenericMetadataMatches(p); }

	public TraceCheckResult checkStreamingChunksReflectedInNextContextWindow() {
		return delegate.checkStreamingChunksReflectedInNextContextWindow();
	}
	public TraceCheckResult checkStreamingChunksReflectedInNextContextWindow(String p) { return delegate.checkStreamingChunksReflectedInNextContextWindow(p); }
	public TraceCheckResult checkStreamingChunksAccumulatedCorrectly() {
		return delegate.checkStreamingChunksAccumulatedCorrectly();
	}
	public TraceCheckResult checkStreamingChunksAccumulatedCorrectly(String p) {
		return delegate.checkStreamingChunksAccumulatedCorrectly(p);
	}

	public TraceCheckResult checkStreamingCallbacksInvoked() {
		return delegate.checkStreamingCallbacksInvoked();
	}

	public TraceCheckResult checkPartialResponseCallbacksInvoked() {
		return delegate.checkPartialResponseCallbacksInvoked();
	}
	public TraceCheckResult checkPartialResponseCallbacksInvoked(String p) { return delegate.checkPartialResponseCallbacksInvoked(p); }

	public TraceCheckResult checkCompleteResponseCallbacksInvoked() {
		return delegate.checkCompleteResponseCallbacksInvoked();
	}
	public TraceCheckResult checkCompleteResponseCallbacksInvoked(String p) { return delegate.checkCompleteResponseCallbacksInvoked(p); }

	public TraceCheckResult
			checkProviderIndependentNonStreamingDataMatchesAcrossProviders() {
		return delegate
				.checkProviderIndependentNonStreamingDataMatchesAcrossProviders();
	}

	public TraceCheckResult
			checkProviderIndependentNonStreamingRequestsMatchAcrossProviders() {
		return delegate
				.checkProviderIndependentNonStreamingRequestsMatchAcrossProviders();
	}

	public TraceCheckResult
			checkProviderIndependentNonStreamingResponsesMatchAcrossProviders() {
		return delegate
				.checkProviderIndependentNonStreamingResponsesMatchAcrossProviders();
	}

	public TraceCheckResult checkExpectedInputs() {
		return delegate.checkExpectedInputs();
	}

	public TraceCheckResult checkExpectedPrompts() {
		return delegate.checkExpectedPrompts();
	}
	public TraceCheckResult checkExpectedPrompts(String p) { return delegate.checkExpectedPrompts(p); }

	public TraceCheckResult checkProviderGenericImagesMatch() {
		return delegate.checkProviderGenericImagesMatch();
	}
	public TraceCheckResult checkProviderGenericImagesMatch(String p) {
		return delegate.checkProviderGenericImagesMatch(p);
	}
	public TraceCheckResult checkExpectedGenericImagesMatch() {
		return delegate.checkExpectedGenericImagesMatch();
	}
	public TraceCheckResult checkExpectedGenericImagesMatch(String p) {
		return delegate.checkExpectedGenericImagesMatch(p);
	}

	public TraceFilesProcessor getProcessor() {
		return delegate.getProcessor();
	}

	public Map<String, GenericTracesFileProcessor.TraceFiles>
			bridgeDemoTraceFiles() {
		return delegate.bridgeDemoTraceFiles();
	}

	public GenericTracesFileProcessor.ExpectedInputs
			bridgeDemoExpectedInputs() {
		return delegate.bridgeDemoExpectedInputs();
	}

	public static void main(String[] args) {
		BridgeDemoTraceFilesProcessor bridgeProcessor =
				new BridgeDemoTraceFilesProcessor();
		bridgeProcessor.generateClassRegistry();
		bridgeProcessor.runChecks();
		bridgeProcessor.writeTraceReports();
		System.out.println(
				"Reports written: "
						+ bridgeProcessor.lineBasedTraceReportFile()
						+ ", "
						+ bridgeProcessor.deduplicatedTraceReportFile()
						+ ", "
						+ bridgeProcessor.checkBasedTraceReportFile());
		try {
			for (String line :
					java.nio.file.Files.readAllLines(
							bridgeProcessor.checkBasedTraceReportFile())) {
				System.out.println(line);
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
