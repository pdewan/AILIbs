package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;
import eduAI.trace.lib.StreamingMode;

public class TraceCheckDependencyTest {
	private static final Path DEFAULT_TRACE_DIRECTORY = Path.of(".");
	private static final Path BROKEN_TRACE_DIRECTORY = Path.of(
			"broken_filtered");
	private static final String EXPECTED_PROMPT_MISSING_DIRECTORY =
			"expected_prompt_missing";
	private static final String MISSING_PROMPT =
			"Expected prompt missing from provider request";
	private static final Set<String> REQUIRED_CLASS_REGISTRY_EVENTS = Set.of(
			"server_handle_factory_fetched",
			"server_handle_fetched",
			"message_translated",
			"response_translated",
			"parameter_translated",
			"metadata_translated",
			"message_merger_factory_fetched",
			"streaming_chunk_accumulated");
	private static final List<String> TRACE_FILE_NAMES = List.of(
			"TraceGeminiBridgeNonStreamingDemo.txt",
			"TraceGeminiBridgeStreamingDemo.txt",
			"TraceOllamaNonStreamingBridgeDemo.txt",
			"TraceOllamaStreamingBridgeDemo.txt");

	@FunctionalInterface
	private interface CheckCall {
		TraceCheckResult check(BridgeDemoTraceFilesProcessor aProcessor);
	}

	@FunctionalInterface
	private interface ResultProbe {
		List<Boolean> results(TraceFilesProcessor aProcessor);
	}

	private record CheckSpecification(
			String name,
			Set<String> requiredEvents,
			CheckCall checkCall,
			ResultProbe resultProbe) {
	}

	private record TraceDataset(
			Path directory,
			GenericTracesFileProcessor.ExpectedInputs expectedInputs) {
	}

	public static void main(String[] args) {
		new TraceCheckDependencyTest().testChecksUseOnlyRequiredTraceEvents();
		System.out.println("Trace-check dependency audit passed");
	}

	public static Set<String> requiredTraceEvents() {
		Set<String> result = new LinkedHashSet<>(
				REQUIRED_CLASS_REGISTRY_EVENTS);
		for (CheckSpecification specification : specifications()) {
			result.addAll(specification.requiredEvents());
		}
		return Set.copyOf(result);
	}

	public void testChecksUseOnlyRequiredTraceEvents() {
		Set<String> computedEvents = requiredTraceEvents();
		if (!computedEvents.equals(EssentialTraceEvents.all())) {
			throw new AssertionError(
					"Essential trace event declaration differs from computed "
							+ "check dependencies: declared="
							+ EssentialTraceEvents.all()
							+ " computed="
							+ computedEvents);
		}
		Path filteredDirectory = null;
		try {
			for (TraceDataset dataset : traceDatasets()) {
				for (CheckSpecification specification : specifications()) {
					filteredDirectory = Files.createTempDirectory(
							"trace-check-dependency-");
					writeFilteredTraces(
							dataset.directory(),
							filteredDirectory,
							specification.requiredEvents());
					assertSameVerdictWithOnlyRequiredEvents(
							dataset,
							specification,
							filteredDirectory);
					deleteDirectory(filteredDirectory);
					filteredDirectory = null;
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} finally {
			if (filteredDirectory != null) {
				deleteDirectory(filteredDirectory);
			}
		}
	}

	private List<TraceDataset> traceDatasets() throws IOException {
		List<TraceDataset> result = new ArrayList<>();
		result.add(new TraceDataset(DEFAULT_TRACE_DIRECTORY, null));
		if (!Files.isDirectory(BROKEN_TRACE_DIRECTORY)) {
			return result;
		}
		try (var directories = Files.list(BROKEN_TRACE_DIRECTORY)) {
			directories.filter(Files::isDirectory)
					.filter(this::hasAllTraceFiles)
					.sorted()
					.forEach(directory -> result.add(
							new TraceDataset(
									directory,
									expectedInputsFor(directory))));
		}
		return result;
	}

	private boolean hasAllTraceFiles(Path aDirectory) {
		return TRACE_FILE_NAMES.stream()
				.allMatch(name -> Files.isRegularFile(aDirectory.resolve(name)));
	}

	private GenericTracesFileProcessor.ExpectedInputs expectedInputsFor(
			Path aDirectory) {
		if (!EXPECTED_PROMPT_MISSING_DIRECTORY.equals(
				aDirectory.getFileName().toString())) {
			return null;
		}
		GenericTracesFileProcessor.ExpectedInputs expectedInputs =
				new BridgeDemoTraceFilesProcessorDelegate()
						.bridgeDemoExpectedInputs();
		return new GenericTracesFileProcessor.ExpectedInputs(
				expectedInputs.systemPromptTexts(),
				List.of(
						MISSING_PROMPT,
						expectedInputs.userPromptTexts().get(1),
						expectedInputs.userPromptTexts().get(2),
						expectedInputs.userPromptTexts().get(3)),
				expectedInputs.compactionPromptTexts(),
				expectedInputs.expectedImageBytes());
	}

	private static List<CheckSpecification> specifications() {
		Set<String> request = Set.of("request_sent");
		Set<String> response = Set.of("response_translated");
		Set<String> streamingMerge = Set.of(
				"request_sent",
				"streaming_chunk_accumulated",
				"streaming_chunks_merged");
		return List.of(
				new CheckSpecification(
						"provider-independent context-window type integrity", request,
						p -> p.checkProviderIndependentContextWindowTypes("Gemini"),
						p -> List.of()),
				new CheckSpecification(
						"provider-independent parameter-store type integrity", request,
						p -> p.checkProviderIndependentParameterStoreTypes("Gemini"),
						p -> List.of()),
				new CheckSpecification(
						"provider-independent response type integrity", response,
						p -> p.checkProviderIndependentResponseTypes("Gemini"),
						p -> List.of()),
				new CheckSpecification(
						"provider generic context window match", request,
						BridgeDemoTraceFilesProcessor::checkProviderGenericTextualContextWindowMatches,
						TraceFilesProcessor::getRequestContextWindowMatchResults),
				new CheckSpecification(
						"provider generic parameter match",
						Set.of("request_sent", "parameter_translated"),
						BridgeDemoTraceFilesProcessor::checkProviderGenericParameterMatches,
						TraceFilesProcessor::getRequestParameterMatchResults),
				new CheckSpecification(
						"provider generic response match",
						Set.of("response_translated"),
						BridgeDemoTraceFilesProcessor::checkProviderGenericResponseMatches,
						TraceFilesProcessor::getResponseMatchResults),
				new CheckSpecification(
						"provider generic metadata match",
						Set.of("response_translated", "metadata_translated"),
						BridgeDemoTraceFilesProcessor::checkProviderGenericMetadataMatches,
						TraceFilesProcessor::getMetadataMatchResults),
				new CheckSpecification(
						"expected prompts", request,
						BridgeDemoTraceFilesProcessor::checkExpectedPrompts,
						TraceFilesProcessor::getExpectedInputMatchResults),
				new CheckSpecification(
						"provider generic image comparison", request,
						BridgeDemoTraceFilesProcessor::checkProviderGenericImagesMatch,
						TraceFilesProcessor::getExpectedInputMatchResults),
				new CheckSpecification(
						"expected generic image comparison", request,
						BridgeDemoTraceFilesProcessor::checkExpectedGenericImagesMatch,
						TraceFilesProcessor::getExpectedInputMatchResults),
				new CheckSpecification(
						"context window continuity", request,
						BridgeDemoTraceFilesProcessor::checkContextWindowContinuity,
						TraceFilesProcessor::getContextWindowContinuityResults),
				new CheckSpecification(
						"cross-provider request structure", request,
						BridgeDemoTraceFilesProcessor::checkProviderIndependentNonStreamingRequestsMatchAcrossProviders,
						TraceFilesProcessor::getProviderIndependentNonStreamingMatchResults),
				new CheckSpecification(
						"cross-provider response structure", response,
						BridgeDemoTraceFilesProcessor::checkProviderIndependentNonStreamingResponsesMatchAcrossProviders,
						TraceFilesProcessor::getProviderIndependentNonStreamingMatchResults),
				new CheckSpecification(
						"streaming response in next context window", streamingMerge,
						BridgeDemoTraceFilesProcessor::checkStreamingChunksReflectedInNextContextWindow,
						TraceFilesProcessor::getStreamingChunkContextWindowMatchResults),
				new CheckSpecification(
						"streaming chunks accumulated correctly",
						Set.of("streaming_chunk_accumulated", "streaming_chunks_merged"),
						BridgeDemoTraceFilesProcessor::checkStreamingChunksAccumulatedCorrectly,
						TraceFilesProcessor::getStreamingChunkAccumulationResults),
				new CheckSpecification(
						"partial response callback",
						Set.of("provider_streaming_chunk_received",
								"partial_response_callback_invoked"),
						BridgeDemoTraceFilesProcessor::checkPartialResponseCallbacksInvoked,
						TraceFilesProcessor::getStreamingCallbackInvocationResults),
				new CheckSpecification(
						"complete response callback",
						Set.of("streaming_chunks_merged",
								"complete_response_callback_invoked"),
						BridgeDemoTraceFilesProcessor::checkCompleteResponseCallbacksInvoked,
						TraceFilesProcessor::getStreamingCallbackInvocationResults));
	}

	private void assertSameVerdictWithOnlyRequiredEvents(
			TraceDataset aDataset,
			CheckSpecification aSpecification,
			Path aFilteredDirectory) {
		BridgeDemoTraceFilesProcessor completeProcessor =
				new BridgeDemoTraceFilesProcessor(
						aDataset.directory(),
						aDataset.expectedInputs());
		BridgeDemoTraceFilesProcessor filteredProcessor =
				new BridgeDemoTraceFilesProcessor(
						aFilteredDirectory,
						aDataset.expectedInputs());
		TraceCheckResult completeResult =
				aSpecification.checkCall().check(completeProcessor);
		TraceCheckResult filteredResult =
				aSpecification.checkCall().check(filteredProcessor);
		List<Boolean> completeDetails = aSpecification.resultProbe().results(
				completeProcessor.getProcessor());
		List<Boolean> filteredDetails = aSpecification.resultProbe().results(
				filteredProcessor.getProcessor());
		if (completeResult.passed() != filteredResult.passed()
				|| !completeDetails.equals(filteredDetails)) {
			throw new AssertionError(
					"dataset=" + aDataset.directory()
							+ " check=" + aSpecification.name()
							+ " has an undeclared trace dependency; requiredEvents="
							+ aSpecification.requiredEvents()
							+ " completePassed=" + completeResult.passed()
							+ " filteredPassed=" + filteredResult.passed()
							+ " completeDetails=" + completeDetails
							+ " filteredDetails=" + filteredDetails
							+ " filteredMessages="
							+ filteredResult.level3Messages());
		}
	}

	private void writeFilteredTraces(
			Path aSourceDirectory,
			Path aTargetDirectory,
			Set<String> retainedEvents) throws IOException {
		for (String fileName : TRACE_FILE_NAMES) {
			List<String> retainedLines = new ArrayList<>();
			for (String line : Files.readAllLines(
					aSourceDirectory.resolve(fileName),
					StandardCharsets.UTF_8)) {
				TraceLine traceLine = TraceLineParser.parse(line);
				if (traceLine != null
						&& retainedEvents.contains(traceLine.getEventName())) {
					retainedLines.add(line);
				}
			}
			Files.write(
					aTargetDirectory.resolve(fileName),
					retainedLines,
					StandardCharsets.UTF_8);
		}
	}

	private void deleteDirectory(Path aDirectory) {
		try {
			if (aDirectory == null || !Files.exists(aDirectory)) {
				return;
			}
			try (var paths = Files.walk(aDirectory)) {
				paths.sorted((first, second) -> second.compareTo(first))
						.forEach(path -> {
							try {
								Files.deleteIfExists(path);
							} catch (IOException e) {
								throw new IllegalStateException(e);
							}
						});
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
