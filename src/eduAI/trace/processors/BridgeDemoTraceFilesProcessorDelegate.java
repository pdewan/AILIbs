package eduAI.trace.processors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import eduAI.trace.lib.StreamingMode;

public class BridgeDemoTraceFilesProcessorDelegate {
	private static final Path CLASS_REGISTRY_FILE =
			Path.of("ClassRegistry.csv");
	private static final Path LINE_BASED_TRACE_REPORT_FILE =
			Path.of("LineBasedTraceReport.txt");
	private static final Path DEDUPLICATED_TRACE_REPORT_FILE =
			Path.of("DeduplicatedTraceReport.txt");
	private static final Path CHECK_BASED_TRACE_REPORT_FILE =
			Path.of("CheckBasedTraceReport.txt");
	private static final Path DEFAULT_TRACE_DIRECTORY = Path.of(".");
	private static final String SUMMARY_PROMPT =
			"Summarize our conversation and remember that summary as "
					+ "the new context for future questions.";

	private Path traceDirectory;
	private TraceFilesProcessor processor;
	private final GenericTracesFileProcessor.ExpectedInputs expectedInputs;

	public record TraceRun(
			List<TraceFilesProcessorError> errors,
			List<String> checkerMessages,
			List<String> groupedCheckerMessages,
			List<String> classRegistryRows) {
	}

	public BridgeDemoTraceFilesProcessorDelegate() {
		this(DEFAULT_TRACE_DIRECTORY);
	}

	public BridgeDemoTraceFilesProcessorDelegate(Path aTraceDirectory) {
		this(aTraceDirectory, null);
	}

	public BridgeDemoTraceFilesProcessorDelegate(
			Path aTraceDirectory,
			GenericTracesFileProcessor.ExpectedInputs anExpectedInputs) {
		traceDirectory = aTraceDirectory == null
				? DEFAULT_TRACE_DIRECTORY
				: aTraceDirectory;
		expectedInputs = anExpectedInputs == null
				? bridgeDemoExpectedInputs()
				: anExpectedInputs;
		processor = newProcessor();
	}

	public void setTraceDirectory(String aTraceDirectory) {
		traceDirectory = traceDirectory(aTraceDirectory);
		processor = newProcessor();
	}

	private Path traceDirectory(String aTraceDirectory) {
		if (aTraceDirectory == null
				|| aTraceDirectory.isBlank()
				|| ".".equals(aTraceDirectory.trim())) {
			return DEFAULT_TRACE_DIRECTORY;
		}
		return Path.of(aTraceDirectory.trim());
	}

	private TraceFilesProcessor newProcessor() {
		return new NativeTraceFilesProcessor(
				bridgeDemoTraceFiles(),
				expectedInputs);
	}

	public List<TraceFilesProcessorError> generateClassRegistry() {
		processTraceFiles();
		buildClassRegistry();
		return processor.getErrors();
	}

	public void processTraceFiles() {
		processor.processTraceFiles();
	}

	public TraceCheckResult checkTraceFilesExist() {
		return processor.checkTraceFilesExist();
	}

	public TraceCheckResult checkProviderNamesConsistent() {
		return processor.checkProviderNamesConsistent();
	}

	public TraceCheckResult checkRequestsTraced() {
		return processor.checkRequestsTraced();
	}

	public TraceCheckResult checkTraceEventExists(
			String anEventName,
			Map<String, List<StreamingMode>> expectedProviderModes) {
		return processor.checkTraceEventExists(
				anEventName,
				expectedProviderModes);
	}

	public void buildClassRegistry() {
		processor.buildClassRegistry(classRegistryFile());
	}

	public List<String> classesForTag(String aTag) {
		return processor.classesForTag(aTag);
	}

	public List<String> tagsForClass(String aClassName) {
		return processor.tagsForClass(aClassName);
	}

	public List<String> classTagDerivations(String aClassName) {
		return processor.classTagDerivations(aClassName);
	}

	public TraceCheckResult checkClassExistsForTag(
			String aTag) {
		return processor.checkClassExistsForTag(aTag);
	}

	public TraceCheckResult checkClassHasMultipleTags(
			String aClassName) {
		return processor.checkClassHasMultipleTags(aClassName);
	}

	public Path classRegistryFile() {
		return traceDirectory.resolve(CLASS_REGISTRY_FILE);
	}

	public Path lineBasedTraceReportFile() {
		return traceDirectory.resolve(LINE_BASED_TRACE_REPORT_FILE);
	}

	public Path deduplicatedTraceReportFile() {
		return traceDirectory.resolve(DEDUPLICATED_TRACE_REPORT_FILE);
	}

	public Path checkBasedTraceReportFile() {
		return traceDirectory.resolve(CHECK_BASED_TRACE_REPORT_FILE);
	}

	public List<TraceFilesProcessorError> runChecks() {
		checkContextWindowContinuity();
		checkProviderGenericTextualContextWindowMatches();
		checkProviderGenericParameterMatches();
		checkProviderGenericResponseMatches();
		checkProviderGenericMetadataMatches();
		checkStreamingChunksReflectedInNextContextWindow();
		checkStreamingCallbacksInvoked();
		checkProviderIndependentNonStreamingDataMatchesAcrossProviders();
		checkExpectedInputs();
		return processor.getErrors();
	}

	public void writeTraceReports() {
		processor.writeLineBasedTraceReport(lineBasedTraceReportFile());
		processor.writeDeduplicatedTraceReport(
				deduplicatedTraceReportFile());
		processor.writeCheckBasedTraceReport(checkBasedTraceReportFile());
	}

	public TraceCheckResult checkContextWindowContinuity() {
		return processor.checkContextWindowContinuity();
	}

	public TraceCheckResult checkPreviousSystemPromptsRetained() {
		return processor.checkPreviousSystemPromptsRetained();
	}
	public TraceCheckResult checkPreviousSystemPromptsRetained(String p) { return processor.checkPreviousSystemPromptsRetained(p); }

	public TraceCheckResult checkPreviousUserPromptsRetained() {
		return processor.checkPreviousUserPromptsRetained();
	}
	public TraceCheckResult checkPreviousUserPromptsRetained(String p) { return processor.checkPreviousUserPromptsRetained(p); }

	public TraceCheckResult checkPreviousCompletedResponsesRetained() {
		return processor.checkPreviousCompletedResponsesRetained();
	}
	public TraceCheckResult checkPreviousCompletedResponsesRetained(String p) { return processor.checkPreviousCompletedResponsesRetained(p); }
	public TraceCheckResult checkPreviousImagesRetained() {
		return processor.checkPreviousImagesRetained();
	}
	public TraceCheckResult checkPreviousImagesRetained(String p) {
		return processor.checkPreviousImagesRetained(p);
	}

	public TraceCheckResult checkExpectedContextWindowCompactions() {
		return processor.checkExpectedContextWindowCompactions();
	}
	public TraceCheckResult checkExpectedContextWindowCompactions(String p) { return processor.checkExpectedContextWindowCompactions(p); }

	public TraceCheckResult checkProviderGenericTextualContextWindowMatches() {
		return processor.checkProviderGenericTextualContextWindowMatches();
	}
	public TraceCheckResult checkProviderGenericTextualContextWindowMatches(String p) { return processor.checkProviderGenericTextualContextWindowMatches(p); }
	public TraceCheckResult checkProviderIndependentContextWindowTypes(String p) { return processor.checkProviderIndependentContextWindowTypes(p); }
	public TraceCheckResult checkProviderIndependentParameterStoreTypes(String p) { return processor.checkProviderIndependentParameterStoreTypes(p); }
	public TraceCheckResult checkProviderIndependentResponseTypes(String p) { return processor.checkProviderIndependentResponseTypes(p); }

	public TraceCheckResult checkProviderGenericParameterMatches() {
		return processor.checkProviderGenericParameterMatches();
	}
	public TraceCheckResult checkProviderGenericParameterMatches(String p) { return processor.checkProviderGenericParameterMatches(p); }

	public TraceCheckResult checkProviderGenericResponseMatches() {
		return processor.checkProviderGenericResponseMatches();
	}
	public TraceCheckResult checkProviderGenericResponseMatches(String p) { return processor.checkProviderGenericResponseMatches(p); }

	public TraceCheckResult checkProviderGenericMetadataMatches() {
		return processor.checkProviderGenericMetadataMatches();
	}
	public TraceCheckResult checkProviderGenericMetadataMatches(String p) { return processor.checkProviderGenericMetadataMatches(p); }

	public TraceCheckResult checkStreamingChunksReflectedInNextContextWindow() {
		return processor.checkStreamingChunksReflectedInNextContextWindow();
	}
	public TraceCheckResult checkStreamingChunksReflectedInNextContextWindow(String p) { return processor.checkStreamingChunksReflectedInNextContextWindow(p); }
	public TraceCheckResult checkStreamingChunksAccumulatedCorrectly() {
		return processor.checkStreamingChunksAccumulatedCorrectly();
	}
	public TraceCheckResult checkStreamingChunksAccumulatedCorrectly(String p) {
		return processor.checkStreamingChunksAccumulatedCorrectly(p);
	}

	public TraceCheckResult checkStreamingCallbacksInvoked() {
		return processor.checkStreamingCallbacksInvoked();
	}

	public TraceCheckResult checkPartialResponseCallbacksInvoked() {
		return processor.checkPartialResponseCallbacksInvoked();
	}
	public TraceCheckResult checkPartialResponseCallbacksInvoked(String p) { return processor.checkPartialResponseCallbacksInvoked(p); }

	public TraceCheckResult checkCompleteResponseCallbacksInvoked() {
		return processor.checkCompleteResponseCallbacksInvoked();
	}
	public TraceCheckResult checkCompleteResponseCallbacksInvoked(String p) { return processor.checkCompleteResponseCallbacksInvoked(p); }

	public TraceCheckResult
			checkProviderIndependentNonStreamingDataMatchesAcrossProviders() {
		return processor
				.checkProviderIndependentNonStreamingDataMatchesAcrossProviders();
	}

	public TraceCheckResult
			checkProviderIndependentNonStreamingRequestsMatchAcrossProviders() {
		return processor
				.checkProviderIndependentNonStreamingRequestsMatchAcrossProviders();
	}

	public TraceCheckResult
			checkProviderIndependentNonStreamingResponsesMatchAcrossProviders() {
		return processor
				.checkProviderIndependentNonStreamingResponsesMatchAcrossProviders();
	}

	public TraceCheckResult checkExpectedInputs() {
		return processor.checkExpectedInputs();
	}

	public TraceCheckResult checkExpectedPrompts() {
		return processor.checkExpectedPrompts();
	}
	public TraceCheckResult checkExpectedPrompts(String p) { return processor.checkExpectedPrompts(p); }

	public TraceCheckResult checkProviderGenericImagesMatch() {
		return processor.checkProviderGenericImagesMatch();
	}
	public TraceCheckResult checkProviderGenericImagesMatch(String p) {
		return processor.checkProviderGenericImagesMatch(p);
	}
	public TraceCheckResult checkExpectedGenericImagesMatch() {
		return processor.checkExpectedGenericImagesMatch();
	}
	public TraceCheckResult checkExpectedGenericImagesMatch(String p) {
		return processor.checkExpectedGenericImagesMatch(p);
	}

	public TraceFilesProcessor getProcessor() {
		return processor;
	}

	public Map<String, GenericTracesFileProcessor.TraceFiles>
			bridgeDemoTraceFiles() {
		Map<String, GenericTracesFileProcessor.TraceFiles> result =
				new LinkedHashMap<>();
		result.put(
				TraceProviderNames.GEMINI,
				new GenericTracesFileProcessor.TraceFiles(
						traceFile("TraceGeminiBridgeStreamingDemo.txt"),
						traceFile("TraceGeminiBridgeNonStreamingDemo.txt")));
		result.put(
				TraceProviderNames.OLLAMA,
				new GenericTracesFileProcessor.TraceFiles(
						traceFile("TraceOllamaStreamingBridgeDemo.txt"),
						traceFile("TraceOllamaNonStreamingBridgeDemo.txt")));
		return result;
	}

	public Path traceFile(String aFileName) {
		return traceDirectory.resolve(aFileName);
	}

	public GenericTracesFileProcessor.ExpectedInputs
			bridgeDemoExpectedInputs() {
		return new GenericTracesFileProcessor.ExpectedInputs(
				List.of(
						BridgeDemoTraceInputs.SYSTEM_PROMPT_1,
						BridgeDemoTraceInputs.SYSTEM_PROMPT_2),
				List.of(
						BridgeDemoTraceInputs.STARTING_PROMPT,
						"Galahad?",
						SUMMARY_PROMPT,
						"Arthur?"),
				List.of(SUMMARY_PROMPT),
				allBytes(Path.of(BridgeDemoTraceInputs.IMAGE_FILE_NAME)));
	}

	public TraceRun runTrace() throws IOException {
		List<TraceFilesProcessorError> errors = generateClassRegistry();
		runChecks();
		writeTraceReports();
		List<String> rows =
				Files.exists(classRegistryFile())
						? Files.readAllLines(
								classRegistryFile(),
								StandardCharsets.UTF_8)
						: List.of();
		return new TraceRun(
				errors,
				getProcessor().getCheckerMessages(),
				getProcessor().getGroupedCheckerMessages(),
				rows);
	}

	public void runBrokenTraceAndVerify() {
		runAndVerify(getClass().getSimpleName(), this::verify);
	}

	public void runAndVerify(Consumer<TraceRun> aVerifier) {
		runAndVerify(getClass().getSimpleName(), aVerifier);
	}

	public void runAndVerify(
			String aProcessorName,
			Consumer<TraceRun> aVerifier) {
		try {
			TraceRun run = runTrace();
			aVerifier.accept(run);
			printTraceRun(run);
			System.out.println(
					aProcessorName
							+ " detected expected trace break");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void verify(TraceRun aRun) {
	}

	public void printTraceRun(TraceRun aRun) {
		try {
			for (String line :
					Files.readAllLines(
							checkBasedTraceReportFile(),
							StandardCharsets.UTF_8)) {
				System.out.println(line);
			}
		} catch (IOException e) {
			for (TraceFilesProcessorError error : aRun.errors()) {
				System.out.println(error.name());
			}
			for (String message : aRun.groupedCheckerMessages()) {
				System.out.println(message);
			}
		}
	}

	public void assertContains(
			List<TraceFilesProcessorError> aErrors,
			TraceFilesProcessorError anError) {
		if (!aErrors.contains(anError)) {
			throw new AssertionError(
					"Expected " + anError + " in " + aErrors);
		}
	}

	public void assertCheckerMessage(
			TraceRun aRun,
			String... aNeedles) {
		for (String message : aRun.checkerMessages()) {
			boolean matched = true;
			for (String needle : aNeedles) {
				if (!message.contains(needle)) {
					matched = false;
					break;
				}
			}
			if (matched) {
				return;
			}
		}
		throw new AssertionError(
				"Expected checker message containing "
						+ Arrays.toString(aNeedles)
						+ " in "
						+ aRun.checkerMessages());
	}

	public void assertRegistryTag(TraceRun aRun, String aTag) {
		for (String row : aRun.classRegistryRows()) {
			if (row.endsWith("," + aTag)) {
				return;
			}
		}
		throw new AssertionError(
				"Expected class registry tag "
						+ aTag
						+ " in "
						+ aRun.classRegistryRows());
	}

	public void assertMissingRegistryTag(
			TraceRun aRun,
			String aTag) {
		for (String row : aRun.classRegistryRows()) {
			if (row.endsWith("," + aTag)) {
				throw new AssertionError(
						"Unexpected class registry tag "
								+ aTag
								+ " in "
								+ row);
			}
		}
	}

	private byte[] allBytes(Path aPath) {
		try {
			return Files.readAllBytes(aPath);
		} catch (IOException e) {
			return new byte[0];
		}
	}
}
