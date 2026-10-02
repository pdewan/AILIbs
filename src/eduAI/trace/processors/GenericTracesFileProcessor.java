package eduAI.trace.processors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;
import eduAI.trace.ImageByteSummary;
import eduAI.trace.TraceTextSummary;
import eduAI.trace.lib.StreamingMode;

public class GenericTracesFileProcessor implements TraceFilesProcessor {
	public static final String PREVIOUS_COMPLETED_RESPONSE_IN_CONTEXT_WINDOW =
			"previous_completed_response_in_context_window";
	public static final String PREVIOUS_SYSTEM_PROMPT_IN_CONTEXT_WINDOW =
			"previous_system_prompt_in_context_window";
	public static final String PREVIOUS_PROMPT_IN_NEW_CONTEXT_WINDOW =
			"previous_prompt_in_new_context_window";
	public static final String PREVIOUS_IMAGE_IN_CONTEXT_WINDOW =
			"previous_image_in_context_window";
	public static final String PREVIOUS_MESSAGE_IN_CONTEXT_WINDOW =
			"previous_message_in_context_window";
	public static final String PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH =
			"provider_generic_textual_context_window_match";
	public static final String PROVIDER_GENERIC_PARAMETER_MATCH =
			"provider_generic_parameter_match";
	public static final String PROVIDER_GENERIC_RESPONSE_MATCH =
			"provider_generic_response_match";
	public static final String PROVIDER_GENERIC_METADATA_MATCH =
			"provider_generic_metadata_match";
	public static final String PROVIDER_INDEPENDENT_CONTEXT_WINDOW_TYPE_INTEGRITY =
			"provider_independent_context_window_type_integrity";
	public static final String PROVIDER_INDEPENDENT_PARAMETER_STORE_TYPE_INTEGRITY =
			"provider_independent_parameter_store_type_integrity";
	public static final String PROVIDER_INDEPENDENT_RESPONSE_TYPE_INTEGRITY =
			"provider_independent_response_type_integrity";
	public static final String EXPECTED_CONTEXT_WINDOW_COMPACTION =
			"expected_context_window_compaction";
	public static final String STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW =
			"streaming_chunks_merged_response_in_context_window";
	public static final String STREAMING_CHUNKS_ACCUMULATED_CORRECTLY =
			"streaming_chunks_accumulated_correctly";
	public static final String PARTIAL_RESPONSE_CALLBACK_INVOKED =
			"partial_response_callback_invoked";
	public static final String COMPLETE_RESPONSE_CALLBACK_INVOKED =
			"complete_response_callback_invoked";
	public static final String PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS =
			"provider_independent_structure_match_across_providers";
	public static final String EXPECTED_PROMPT_IN_CONTEXT_WINDOW =
			"expected_prompt_in_context_window";
	public static final String PROVIDER_GENERIC_IMAGE_MATCH =
			"provider_generic_image_match";
	public static final String EXPECTED_GENERIC_IMAGE_MATCH =
			"expected_generic_image_match";
	public static final String INFO_MESSAGE_PREFIX = "I***";
	public static final String WARNING_MESSAGE_PREFIX = "W***";
	public static final String ERROR_MESSAGE_PREFIX = "E***";
	public static final String UNCHECKED_MESSAGE_PREFIX = "U***";

	private static final Map<String, String> CHECKER_EXPLANATIONS =
			Map.ofEntries(
					Map.entry(
					PREVIOUS_COMPLETED_RESPONSE_IN_CONTEXT_WINDOW,
					"Checks that a completed assistant response from one context-window snapshot appears in the next context window."),
					Map.entry(
					PREVIOUS_SYSTEM_PROMPT_IN_CONTEXT_WINDOW,
					"Checks that a previous system prompt is retained when the next context-window snapshot is formed."),
					Map.entry(
					PREVIOUS_PROMPT_IN_NEW_CONTEXT_WINDOW,
					"Checks that a previous user prompt is retained when the next context-window snapshot is formed."),
					Map.entry(
					PREVIOUS_IMAGE_IN_CONTEXT_WINDOW,
					"Checks that images from earlier turns remain until an expected context-window compaction."),
					Map.entry(
					PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH,
					"Checks that the provider-specific context window represents the same messages and parts as the generic context window in a request."),
					Map.entry(
					PROVIDER_GENERIC_PARAMETER_MATCH,
					"Checks that translated provider parameters correspond to the names and values in the generic parameter store and are assigned to the native provider target."),
					Map.entry(
					PROVIDER_GENERIC_RESPONSE_MATCH,
					"Checks that the provider response and translated generic response contain equivalent message content."),
					Map.entry(
					PROVIDER_GENERIC_METADATA_MATCH,
					"Checks that translated provider metadata names and values appear in the generic metadata store."),
					Map.entry(
					PROVIDER_INDEPENDENT_CONTEXT_WINDOW_TYPE_INTEGRITY,
					"Checks that a generic context window contains no registered provider-specific types."),
					Map.entry(
					PROVIDER_INDEPENDENT_PARAMETER_STORE_TYPE_INTEGRITY,
					"Checks that a generic parameter store contains no registered provider-specific types."),
					Map.entry(
					PROVIDER_INDEPENDENT_RESPONSE_TYPE_INTEGRITY,
					"Checks that a generic response contains no registered provider-specific types."),
					Map.entry(
					EXPECTED_CONTEXT_WINDOW_COMPACTION,
					"Checks that the context window is compacted before a prompt where the demo expects compaction."),
					Map.entry(
					STREAMING_CHUNKS_ACCUMULATED_CORRECTLY,
					"Checks that each accumulated streaming message contains all chunks received so far."),
					Map.entry(
					STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW,
					"Checks that streaming chunks were accumulated into the completed assistant response before that response appears in a later context window."),
					Map.entry(
					PARTIAL_RESPONSE_CALLBACK_INVOKED,
					"Checks that streaming partial responses are delivered to the registered streaming callback."),
					Map.entry(
					COMPLETE_RESPONSE_CALLBACK_INVOKED,
					"Checks that the completed streaming response is delivered to the registered streaming callback."),
					Map.entry(
					PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS,
					"Checks that provider-independent request or response structures have the same shape across provider traces."),
					Map.entry(
					EXPECTED_PROMPT_IN_CONTEXT_WINDOW,
					"Checks that an expected prompt from the demo input appears in a traced context-window message."),
					Map.entry(
					PROVIDER_GENERIC_IMAGE_MATCH,
					"Checks that generic and provider request images have the same byte prefix, suffix, and count."),
					Map.entry(
					EXPECTED_GENERIC_IMAGE_MATCH,
					"Checks that the generic request image has the expected byte prefix, suffix, and count."));

	private final Map<String, TraceFiles> providerTraceFiles;
	private final Map<String, TraceLineHandler> handlers =
			new LinkedHashMap<>();
	private final List<ProviderTraceInterpreter> providerTraceInterpreters =
			new ArrayList<>();
	private final Map<String, ProviderParameterChecker>
			providerParameterCheckers = new LinkedHashMap<>();
	private final ProviderSpecificTypeRegistry providerSpecificTypeRegistry =
			new ProviderSpecificTypeRegistry();
	private final ProviderIndependentValueIgnoreRegistry
			providerIndependentValueIgnoreRegistry =
					new ProviderIndependentValueIgnoreRegistry();
	private ProviderIndependentValueComparator
			providerIndependentValueComparator =
					new CanonicalValueContainmentComparator();
	private final CanonicalValueProjector canonicalValueProjector =
			new CanonicalValueProjector();
	private final ReflectiveValueCollector reflectiveValueCollector =
			new ReflectiveValueCollector();
	private ProviderIndependentKeyValueComparator
			providerIndependentKeyValueComparator =
					new FlattenedKeyValueComparator();
	private final Map<String, Set<String>> classTags =
			new LinkedHashMap<>();
	private final Map<String, List<ClassTagDerivation>> classTagDerivations =
			new LinkedHashMap<>();
	private final Map<TraceRecordId, TraceRecord> traceRecords =
			new LinkedHashMap<>();
	private final Map<String, String> providersByServerHandleFactoryIdentity =
			new LinkedHashMap<>();
	private final Map<String, String> providersByServerHandleIdentity =
			new LinkedHashMap<>();
	private final List<Boolean> contextWindowContinuityResults =
			new ArrayList<>();
	private final List<Boolean> requestContextWindowMatchResults =
			new ArrayList<>();
	private final List<Boolean> requestParameterMatchResults =
			new ArrayList<>();
	private final List<Boolean> responseMatchResults = new ArrayList<>();
	private final List<Boolean> metadataMatchResults = new ArrayList<>();
	private final List<Boolean> expectedInputMatchResults =
			new ArrayList<>();
	private final List<Boolean> streamingChunkContextWindowMatchResults =
			new ArrayList<>();
	private final List<Boolean> streamingChunkAccumulationResults =
			new ArrayList<>();
	private final List<Boolean> streamingCallbackInvocationResults =
			new ArrayList<>();
	private final List<Boolean> providerIndependentNonStreamingMatchResults =
			new ArrayList<>();
	private final List<String> checkerMessages = new ArrayList<>();
	private final List<String> groupedCheckerMessages = new ArrayList<>();
	private final Map<String, CheckerMessageGroup> checkerMessageGroups =
			new LinkedHashMap<>();
	private final EnumSet<TraceFilesProcessorError> errors =
			EnumSet.noneOf(TraceFilesProcessorError.class);
	private final ExpectedInputs expectedInputs;
	private boolean falseCheckMessagesAreWarnings = true;
	private boolean falseCheckMessagesAreErrors = true;
	private boolean uncheckedCheckMessagesAreWarnings = true;
	private boolean uncheckedCheckMessagesAreErrors = true;
	private boolean trueCheckMessagesAreWarnings;
	private boolean trueCheckMessagesAreErrors;
	private String currentProvider;
	private boolean currentTraceIsStreaming;
	private TraceFileStats currentStats;
	private final Map<String, String> pendingTranslatedParameters =
			new LinkedHashMap<>();
	private final List<ProviderParameterChecker.ParameterEvidence>
			pendingParameterEvidence = new ArrayList<>();
	private final Map<String, String> pendingTranslatedMetadata =
			new LinkedHashMap<>();
	private TraceRecord currentResponseTranslationRecord;
	private boolean traceFilesProcessed;

	public record TraceFiles(
			Path streamingTraceFile,
			Path nonStreamingTraceFile) {
	}

	public record ExpectedInputs(
			List<String> systemPromptTexts,
			List<String> userPromptTexts,
			List<String> compactionPromptTexts,
			byte[] expectedImageBytes) {
		public ExpectedInputs(
				List<String> systemPromptTexts,
				List<String> userPromptTexts,
				byte[] expectedImageBytes) {
			this(
					systemPromptTexts,
					userPromptTexts,
					List.of(),
					expectedImageBytes);
		}

		public ExpectedInputs(
				List<String> userPromptTexts,
			byte[] expectedImageBytes) {
			this(List.of(), userPromptTexts, List.of(), expectedImageBytes);
		}

		public ExpectedInputs {
			systemPromptTexts = systemPromptTexts == null
					? List.of()
					: List.copyOf(systemPromptTexts);
			userPromptTexts = userPromptTexts == null
					? List.of()
					: List.copyOf(userPromptTexts);
			compactionPromptTexts = compactionPromptTexts == null
					? List.of()
					: List.copyOf(compactionPromptTexts);
			expectedImageBytes = expectedImageBytes == null
					? new byte[0]
					: expectedImageBytes.clone();
		}
	}

	private record ReflectedObject(
			String className,
			List<String> interfaceNames) {
	}

	public static record TraceMessage(
			String role,
			List<TracePart> parts,
			String textAggregate) {
		public TraceMessage(String role, List<TracePart> parts) { this(role, parts, ""); }
	}

	public static record TracePart(
			String kind,
			String value) {
	}

	private record ContextWindowComparison(
			boolean matches,
			String detail) {
	}

	private record ImageComparison(
			boolean matches,
			TraceRecord traceRecord,
			ImageByteSummary expected,
			ImageByteSummary generic,
			ImageByteSummary provider) {
	}

	private record ParsedTraceLine(
			TraceLine traceLine,
			int traceFileLineNumber) {
	}

	private record ContextWindowSnapshot(
			TraceRecord traceRecord,
			List<TraceMessage> messages) {
	}

	private record StreamingChunkComposition(
			TraceRecord firstChunkRecord,
			TraceRecord lastChunkRecord,
			TraceRecord mergedRecord,
			TraceMessage composedMessage,
			TraceMessage mergedMessage) {
	}

	private record ClassTagDerivation(
			String className,
			String tag,
			String traceEvent,
			String traceLine,
			String fieldName,
			String reason) {
		String message() {
			return "class_tag_derived className="
					+ className
					+ " tag="
					+ tag
					+ " traceEvent="
					+ traceEvent
					+ " traceLine="
					+ traceLine
					+ " fieldName="
					+ fieldName
					+ " reason=\""
					+ reason
					+ "\"";
		}
	}

	private interface TraceLineHandler {
		void handle(TraceRecord aTraceRecord);
	}

	private static class CheckerMessageGroup {
		private final TraceProcessorMessageLevel level;
		private final String message;
		private final List<String> traceLines = new ArrayList<>();
		private final int groupedMessageIndex;
		private int count;

		CheckerMessageGroup(
				TraceProcessorMessageLevel aLevel,
				String aMessage,
				int aGroupedMessageIndex) {
			level = aLevel;
			message = aMessage;
			groupedMessageIndex = aGroupedMessageIndex;
		}

		private String formattedMessage() {
			StringBuilder builder = new StringBuilder();
			builder.append(level.prefix()).append(' ').append(message);
			if (!traceLines.isEmpty()) {
				builder.append(" traceLines=")
						.append(displayTraceLines());
			}
			if (count > traceLines.size()) {
				builder.append(" occurrences=").append(count);
			}
			return builder.toString();
		}

		private List<String> displayTraceLines() {
			int maxDisplayedTraceLines = 12;
			if (traceLines.size() <= maxDisplayedTraceLines) {
				return traceLines;
			}
			ArrayList<String> result =
					new ArrayList<>(
							traceLines.subList(0, maxDisplayedTraceLines));
			result.add(
					"... "
							+ (traceLines.size() - maxDisplayedTraceLines)
							+ " more");
			return result;
		}
	}

	public enum TraceProcessorMessageLevel {
		INFO(INFO_MESSAGE_PREFIX),
		WARNING(WARNING_MESSAGE_PREFIX),
		ERROR(ERROR_MESSAGE_PREFIX);

		private final String prefix;

		TraceProcessorMessageLevel(String aPrefix) {
			prefix = aPrefix;
		}

		public String prefix() {
			return prefix;
		}
	}

	private static class TraceFileStats {
		private boolean requestSent;
		private boolean responseTranslated;
		private boolean partialResponseCallbackInvoked;
		private boolean completeResponseCallbackInvoked;
	}

	public GenericTracesFileProcessor(
			) {
		this(Map.of(), new ExpectedInputs(List.of(), null));
	}

	public GenericTracesFileProcessor(
			Map<String, TraceFiles> aProviderTraceFiles) {
		this(aProviderTraceFiles, new ExpectedInputs(List.of(), null));
	}

	public GenericTracesFileProcessor(
			Map<String, TraceFiles> aProviderTraceFiles,
			ExpectedInputs anExpectedInputs) {
		providerTraceFiles =
				new LinkedHashMap<>(aProviderTraceFiles);
		expectedInputs = anExpectedInputs == null
				? new ExpectedInputs(List.of(), null)
				: anExpectedInputs;
		registerDefaultProviderIndependentIgnoredValues();
	}

	private void registerDefaultProviderIndependentIgnoredValues() {
		for (String role : List.of("SYSTEM", "USER", "ASSISTANT")) {
			registerProviderIndependentIgnoredValue(
					CanonicalValueKind.ROLE, role);
		}
		for (String partKind : List.of("text", "image", "imageBytes")) {
			registerProviderIndependentIgnoredValue(
					CanonicalValueKind.PART_KIND, partKind);
		}
	}

	public void registerProviderTraceInterpreter(
			ProviderTraceInterpreter anInterpreter) {
		if (anInterpreter != null) {
			providerTraceInterpreters.add(anInterpreter);
		}
	}

	@Override
	public void registerProviderParameterChecker(
			ProviderParameterChecker aChecker) {
		if (aChecker != null) {
			providerParameterCheckers.put(
					parameterCheckerKey(
							aChecker.providerName(),
							aChecker.parameterName()),
					aChecker);
		}
	}

	@Override
	public void setProviderIndependentValueComparator(
			ProviderIndependentValueComparator aComparator) {
		if (aComparator == null) {
			throw new IllegalArgumentException("Comparator must not be null");
		}
		providerIndependentValueComparator = aComparator;
	}

	@Override
	public void setProviderIndependentKeyValueComparator(
			ProviderIndependentKeyValueComparator aComparator) {
		if (aComparator == null) {
			throw new IllegalArgumentException("Comparator must not be null");
		}
		providerIndependentKeyValueComparator = aComparator;
	}

	public void registerProviderSpecificTypes(
			String aProvider,
			Collection<String> someClassNames,
			Collection<String> somePackagePrefixes) {
		providerSpecificTypeRegistry.register(
				aProvider,
				someClassNames,
				somePackagePrefixes);
	}

	@Override
	public void registerProviderIndependentIgnoredValue(
			CanonicalValueKind aKind,
			String aValue) {
		providerIndependentValueIgnoreRegistry.register(aKind, aValue);
	}

	@Override
	public boolean isProviderIndependentValueIgnored(CanonicalValue aValue) {
		return providerIndependentValueIgnoreRegistry.isIgnored(aValue);
	}

	@Override
	public List<CanonicalValue> providerIndependentIgnoredValues() {
		return providerIndependentValueIgnoreRegistry.ignoredValues();
	}

	public void processTraceFiles(
			Map<String, TraceFiles> aProviderTraceFiles) {
		providerTraceFiles.clear();
		providerTraceFiles.putAll(aProviderTraceFiles);
		processTraceFiles();
	}

	public void processTraceFiles(
			String aProvider,
			Path aStreamingTraceFile,
			Path aNonStreamingTraceFile) {
		processTraceFiles(
				Map.of(
						aProvider,
						new TraceFiles(
								aStreamingTraceFile,
								aNonStreamingTraceFile)));
	}

	public void processTraceFiles() {
		initializeTraceProcessing();
		checkTraceFilesExist();
		for (Map.Entry<String, TraceFiles> entry :
				providerTraceFiles.entrySet()) {
			currentProvider = entry.getKey();
			processNonStreamingTrace(entry.getValue());
			processStreamingTrace(entry.getValue());
		}
		traceFilesProcessed = true;
		checkExpectedClassTags();
	}

	public TraceCheckResult checkTraceFilesExist() {
		for (Map.Entry<String, TraceFiles> entry :
				providerTraceFiles.entrySet()) {
			currentProvider = entry.getKey();
			currentTraceIsStreaming = false;
			checkTraceFileExists(entry.getValue(), false);
			currentTraceIsStreaming = true;
			checkTraceFileExists(entry.getValue(), true);
		}
		return traceCheckResult(traceFileCheckReportLines());
	}

	public TraceCheckResult checkProviderNamesConsistent() {
		ensureTraceFilesProcessedForClassRegistry();
		return processorErrorCheckResult(
				TraceFilesProcessorError.INCONSISTENT_PROVIDER_NAME,
				"provider names consistent");
	}

	public TraceCheckResult checkRequestsTraced() {
		ensureTraceFilesProcessedForClassRegistry();
		return processorErrorCheckResult(
				TraceFilesProcessorError.MISSING_REQUEST_SENT,
				"request traced");
	}

	public TraceCheckResult checkTraceEventExists(
			String anEventName,
			Map<String, List<StreamingMode>> expectedProviderModes) {
		ensureTraceFilesProcessedForClassRegistry();
		List<String> messages = new ArrayList<>();
		for (Map.Entry<String, List<StreamingMode>> entry :
				expectedProviderModes.entrySet()) {
			for (StreamingMode mode : entry.getValue()) {
				List<TraceRecord> matchingRecords = traceRecords.values().stream()
						.filter(record -> entry.getKey().equals(
								record.getId().provider()))
						.filter(record -> mode == record.getId().mode())
						.filter(record -> anEventName.equals(
								record.getTraceLine().getEventName()))
						.toList();
				List<String> evidence = matchingRecords.stream()
						.map(this::traceLineLocation)
						.toList();
				List<String> invalidEvidence = matchingRecords.stream()
						.filter(record -> !validTraceEventArguments(
								record, anEventName))
						.map(this::traceLineLocation)
						.toList();
				boolean passed = !evidence.isEmpty()
						&& invalidEvidence.isEmpty();
				String prefix = !passed
						? ERROR_MESSAGE_PREFIX
						: INFO_MESSAGE_PREFIX;
				messages.add(prefix
						+ " trace event exists check="
						+ (passed ? "passed" : "failed")
						+ " event=" + anEventName
						+ " provider=" + entry.getKey()
						+ " mode=" + mode
						+ " count=" + evidence.size()
						+ " evidence=" + evidence
						+ " invalidEvidence=" + invalidEvidence);
			}
		}
		return traceCheckResult(messages);
	}

	private boolean validTraceEventArguments(
			TraceRecord aRecord,
			String anEventName) {
		Map<String, String> data =
				aRecord.getTraceLine().getAuxiliaryData();
		return switch (anEventName) {
		case "server_handle_factory_fetched" ->
				classMatchesDump(data, "serverHandleFactoryRegistryClass",
						"serverHandleFactoryRegistry")
				&& classMatchesDump(data, "serverHandleFactoryClass",
						"serverHandleFactory")
				&& identityMatchesDump(data, "serverHandleFactoryIdentity",
						"serverHandleFactory");
		case "server_handle_fetched" ->
				classMatchesDump(data, "serverHandleFactoryClass",
						"serverHandleFactory")
				&& identityMatchesDump(data, "serverHandleFactoryIdentity",
						"serverHandleFactory")
				&& classMatchesDump(data, "serverHandleClass", "serverHandle")
				&& identityMatchesDump(data, "serverHandleIdentity", "serverHandle");
		case "message_translated" -> hasSourceClass(aRecord)
				&& classMatchesDump(data, "providerDependentMessageClass",
						"providerDependentMessage")
				&& firstCanonicalMessageFromArgument(
						aRecord, "providerIndependentMessage") != null;
		case "response_translated" -> hasSourceClass(aRecord)
				&& required(data,
						"providerIndependentResponse",
						"providerDependentResponse");
		case "parameter_translated" ->
				classMatchesDump(data, "parameterHandlerClass", "parameterHandler")
				&& classMatchesDump(data, "providerDependentTargetClass",
						"providerDependentTarget")
				&& required(data, "propertyName", "propertyValue",
						"providerDependentTargetState");
		case "metadata_translated" ->
				classMatchesDump(data, "metadataHandlerClass", "metadataHandler")
				&& classMatchesDump(data, "providerDependentSourceClass",
						"providerDependentSource")
				&& required(data, "propertyName", "propertyValue");
		case "message_merger_factory_fetched" ->
				classMatchesDump(data, "streamChunkMergerFactoryRegistryClass",
						"streamChunkMergerFactoryRegistry")
				&& classMatchesDump(data, "streamChunkMergerFactoryClass",
						"streamChunkMergerFactory");
		case "streaming_chunk_accumulated" ->
				classMatchesDump(data, "messageMergerClass", "messageMerger")
				&& firstCanonicalMessageFromArgument(aRecord, "streamingChunk") != null
				&& firstCanonicalMessageFromArgument(aRecord, "accumulatedMessage") != null;
		case "streaming_chunks_merged" ->
				classMatchesDump(data, "messageMergerClass", "messageMerger")
				&& firstCanonicalMessageFromArgument(aRecord, "mergedMessage") != null;
		case "partial_response_callback_invoked",
				"complete_response_callback_invoked" -> hasSourceClass(aRecord)
				&& classMatchesDump(data, "streamingCallbackClass",
						"streamingCallback")
				&& firstCanonicalMessageFromArgument(aRecord, "callbackArgument") != null;
		case "provider_streaming_chunk_received" ->
				classMatchesDump(data, "providerStreamingChunkClass",
						"providerStreamingChunk");
		case "request_sent" -> aRecord.getId().mode().name().equals(data.get("mode"))
				&& required(data, "providerIndependentParameterStore",
						"providerIndependentContextWindow",
						"providerDependentConfiguration",
						"providerDependentContextWindow");
		default -> true;
		};
	}

	private boolean hasSourceClass(TraceRecord aRecord) {
		return !isBlank(aRecord.getTraceLine().getSourceClassName());
	}

	private boolean required(Map<String, String> data, String... fields) {
		for (String field : fields) {
			if (isBlank(data.get(field))) {
				return false;
			}
		}
		return true;
	}

	private boolean classMatchesDump(
			Map<String, String> data,
			String aClassField,
			String aDumpField) {
		String className = data.get(aClassField);
		return !isBlank(className)
				&& className.equals(rootClassNameInReflectiveDump(
						data.get(aDumpField)));
	}

	private boolean identityMatchesDump(
			Map<String, String> data,
			String anIdentityField,
			String aDumpField) {
		String identity = data.get(anIdentityField);
		String dump = data.get(aDumpField);
		return !isBlank(identity) && dump != null
				&& dump.contains("toString=\"" + identity + "\"");
	}

	public void processTraceFile(
			String aProvider,
			StreamingMode aMode,
			Path aTraceFile) {
		currentProvider = aProvider;
		processTraceFile(aTraceFile, aMode == StreamingMode.STREAMING);
		traceFilesProcessed = true;
	}

	public void initializeTraceProcessing() {
		clearProcessingState();
	}

	public void writeClassRegistry(Path aClassRegistryFile) {
		buildClassRegistry(aClassRegistryFile);
	}

	public void buildClassRegistry(Path aClassRegistryFile) {
		try {
			writeClassRegistryRows(aClassRegistryFile);
		} catch (IOException e) {
			recordError(TraceFilesProcessorError.UNREADABLE_TRACE_FILE);
		}
	}

	public List<TraceFilesProcessorError> getErrors() {
		return List.copyOf(errors);
	}

	private TraceCheckResult traceCheckResult(List<String> aLevel3Messages) {
		ArrayList<String> level3ErrorMessages = new ArrayList<>();
		ArrayList<TraceFilesProcessorError> level3Errors =
				new ArrayList<>();
		for (String message : aLevel3Messages) {
			if (!message.startsWith(ERROR_MESSAGE_PREFIX)) {
				continue;
			}
			level3ErrorMessages.add(message);
			String errorName = checkerFieldValue(message, "error");
			if (isBlank(errorName)) {
				continue;
			}
			try {
				TraceFilesProcessorError error =
						TraceFilesProcessorError.valueOf(errorName);
				if (!level3Errors.contains(error)) {
					level3Errors.add(error);
				}
			} catch (IllegalArgumentException e) {
				// Some level-3 error lines describe a failed check without an enum.
			}
		}
		return new TraceCheckResult(
				level3Errors,
				aLevel3Messages,
				level3ErrorMessages);
	}

	private TraceCheckResult checkerResult(String aCheckName) {
		return checkerResult(aCheckName, null, null);
	}

	private TraceCheckResult checkerResultForProvider(
			String aCheckName,
			String aProvider) {
		return checkerResult(aCheckName, "provider", aProvider);
	}

	private TraceCheckResult checkerResult(
			String aCheckName,
			String aFieldName,
			String aFieldValue) {
		ArrayList<String> messages = new ArrayList<>();
		for (String message : checkerMessages) {
			if (!aCheckName.equals(checkName(message))) {
				continue;
			}
			if (aFieldName != null
					&& !aFieldValue.equals(
							checkerFieldValue(
									checkerMessageText(message),
									aFieldName))) {
				continue;
			}
			messages.add(message);
		}
		if (messages.isEmpty()) {
			messages.add(
					ERROR_MESSAGE_PREFIX
							+ " "
							+ aCheckName
							+ " check=failed evidence=[]");
		}
		return traceCheckResult(messages);
	}

	private TraceCheckResult processorErrorCheckResult(
			TraceFilesProcessorError anError,
			String aCheckLabel) {
		ArrayList<String> messages = new ArrayList<>();
		String errorToken = "error=" + anError.name();
		for (String message : checkerMessages) {
			if (message.contains(errorToken)) {
				messages.add(message);
			}
		}
		if (messages.isEmpty()) {
			messages.add(
					INFO_MESSAGE_PREFIX
							+ " "
							+ aCheckLabel
							+ " check=passed");
		}
		return traceCheckResult(messages);
	}

	public List<TraceFilesProcessorError> generateClassRegistry(
			Path aClassRegistryFile) {
		processTraceFiles();
		checkContextWindowContinuity();
		checkProviderGenericTextualContextWindowMatches();
		checkProviderGenericParameterMatches();
		checkProviderGenericResponseMatches();
		checkProviderGenericMetadataMatches();
		checkStreamingChunksReflectedInNextContextWindow();
		checkStreamingCallbacksInvoked();
		checkProviderIndependentNonStreamingDataMatchesAcrossProviders();
		checkExpectedInputs();
		writeClassRegistry(aClassRegistryFile);
		return List.copyOf(errors);
	}

	public List<String> classesForTag(String aTag) {
		ensureTraceFilesProcessedForClassRegistry();
		ArrayList<String> result = new ArrayList<>();
		if (isBlank(aTag)) {
			return result;
		}
		for (Map.Entry<String, Set<String>> entry : classTags.entrySet()) {
			if (entry.getValue().contains(aTag)) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	public List<String> tagsForClass(String aClassName) {
		ensureTraceFilesProcessedForClassRegistry();
		if (isBlank(aClassName)) {
			return List.of();
		}
		Set<String> tags = classTags.get(aClassName);
		if (tags == null) {
			return List.of();
		}
		return List.copyOf(tags);
	}

	public List<String> classTagDerivations(String aClassName) {
		ensureTraceFilesProcessedForClassRegistry();
		if (isBlank(aClassName)) {
			return List.of();
		}
		List<ClassTagDerivation> derivations =
				classTagDerivations.get(aClassName);
		if (derivations == null) {
			return List.of();
		}
		ArrayList<String> result = new ArrayList<>();
		for (ClassTagDerivation derivation : derivations) {
			result.add(derivation.message());
		}
		return result;
	}

	public TraceCheckResult checkClassExistsForTag(
			String aTag) {
		return checkClassExistsForTag(
				aTag,
				expectedTraceEventsForTag(aTag));
	}

	public TraceCheckResult checkClassExistsForTag(
			String aTag,
			List<String> anExpectedTraceEvents) {
		ensureTraceFilesProcessedForClassRegistry();
		if (isBlank(aTag)) {
			return traceCheckResult(List.of());
		}
		List<String> classes = classesForTag(aTag);
		if (classes.isEmpty()) {
			errors.add(TraceFilesProcessorError.MISSING_CLASS_FOR_TAG);
			recordCheckerMessage(
					TraceProcessorMessageLevel.ERROR,
					"class_tag_missing tag="
							+ aTag
							+ " expectedTraceEvents="
							+ listValue(anExpectedTraceEvents)
							+ " reason=\"no class was derived for the required tag\"");
		} else {
			recordCheckerMessage(
					TraceProcessorMessageLevel.INFO,
					"class_tag_present tag="
							+ aTag
							+ " classes="
							+ listValue(classes));
		}
		return traceCheckResult(classTagCheckReportLines(aTag));
	}

	private void ensureTraceFilesProcessedForClassRegistry() {
		if (!traceFilesProcessed && !providerTraceFiles.isEmpty()) {
			processTraceFiles();
		}
	}

	public TraceCheckResult checkClassHasMultipleTags(
			String aClassName) {
		List<String> tags = tagsForClass(aClassName);
		recordCheckerMessage(
				TraceProcessorMessageLevel.INFO,
				"class_tag_multiplicity className="
						+ aClassName
						+ " tagCount="
						+ tags.size()
						+ " tags="
						+ listValue(tags)
						+ " derivations="
						+ listValue(classTagDerivations(aClassName)));
		return traceCheckResult(
				List.of(
						INFO_MESSAGE_PREFIX
								+ " class tags check=passed className="
								+ reportValue(aClassName)
								+ " tagCount="
								+ tags.size()
								+ " tags="
								+ compactListValue(tags)
								+ " evidence="
								+ deduplicatedEvidenceForMessage(
										"class_tag_multiplicity className="
												+ aClassName)));
	}

	public void writeLineBasedTraceReport(Path aReportFile) {
		try {
			Files.writeString(
					aReportFile,
					String.join(
							System.lineSeparator(),
							lineBasedTraceReportLines())
							+ System.lineSeparator(),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			recordError(TraceFilesProcessorError.UNREADABLE_TRACE_FILE);
		}
	}

	public void writeDeduplicatedTraceReport(Path aReportFile) {
		try {
			Files.writeString(
					aReportFile,
					String.join(
							System.lineSeparator(),
							deduplicatedTraceReportLines())
							+ System.lineSeparator(),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			recordError(TraceFilesProcessorError.UNREADABLE_TRACE_FILE);
		}
	}

	public void writeCheckBasedTraceReport(Path aReportFile) {
		try {
			Files.writeString(
					aReportFile,
					String.join(
							System.lineSeparator(),
							checkBasedTraceReportLines())
							+ System.lineSeparator(),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			recordError(TraceFilesProcessorError.UNREADABLE_TRACE_FILE);
		}
	}

	private void clearProcessingState() {
		classTags.clear();
		classTagDerivations.clear();
		traceRecords.clear();
		providersByServerHandleFactoryIdentity.clear();
		providersByServerHandleIdentity.clear();
		contextWindowContinuityResults.clear();
		requestContextWindowMatchResults.clear();
		requestParameterMatchResults.clear();
		responseMatchResults.clear();
		metadataMatchResults.clear();
		expectedInputMatchResults.clear();
		streamingChunkContextWindowMatchResults.clear();
		streamingChunkAccumulationResults.clear();
		streamingCallbackInvocationResults.clear();
		providerIndependentNonStreamingMatchResults.clear();
		checkerMessages.clear();
		groupedCheckerMessages.clear();
		checkerMessageGroups.clear();
		errors.clear();
		pendingTranslatedParameters.clear();
		pendingParameterEvidence.clear();
		pendingTranslatedMetadata.clear();
		currentResponseTranslationRecord = null;
		traceFilesProcessed = false;
	}

	private void processNonStreamingTrace(TraceFiles aTraceFiles) {
		currentTraceIsStreaming = false;
		Path traceFile = traceFile(aTraceFiles, false);
		if (!traceFileExists(traceFile)) {
			return;
		}
		processTraceFile(traceFile, false);
	}

	private void processStreamingTrace(TraceFiles aTraceFiles) {
		currentTraceIsStreaming = true;
		Path traceFile = traceFile(aTraceFiles, true);
		if (!traceFileExists(traceFile)) {
			return;
		}
		processTraceFile(traceFile, true);
	}

	private boolean checkTraceFileExists(
			TraceFiles aTraceFiles,
			boolean isStreaming) {
		Path traceFile = traceFile(aTraceFiles, isStreaming);
		if (!traceFileExists(traceFile)) {
			recordTraceFileError(
					isStreaming
							? TraceFilesProcessorError
									.MISSING_STREAMING_TRACE_FILE
							: TraceFilesProcessorError
									.MISSING_NON_STREAMING_TRACE_FILE,
					traceFile);
			return false;
		}
		if (!Files.isReadable(traceFile)) {
			recordTraceFileError(
					TraceFilesProcessorError.UNREADABLE_TRACE_FILE,
					traceFile);
			return false;
		}
		return true;
	}

	private Path traceFile(
			TraceFiles aTraceFiles,
			boolean isStreaming) {
		if (aTraceFiles == null) {
			return null;
		}
		return isStreaming
				? aTraceFiles.streamingTraceFile()
				: aTraceFiles.nonStreamingTraceFile();
	}

	private boolean traceFileExists(Path aTraceFile) {
		return aTraceFile != null && Files.exists(aTraceFile);
	}

	private void processTraceFile(
			Path aTraceFile,
			boolean isStreaming) {
		currentProvider = "";
		currentStats = new TraceFileStats();
		pendingTranslatedParameters.clear();
		pendingParameterEvidence.clear();
		pendingTranslatedMetadata.clear();
		currentResponseTranslationRecord = null;
		List<ParsedTraceLine> lines;
		try {
			lines = parsedTraceLines(aTraceFile);
		} catch (IOException e) {
			recordError(TraceFilesProcessorError.UNREADABLE_TRACE_FILE);
			return;
		}
		StreamingMode inferredMode = modeFromRequestSent(lines);
		currentTraceIsStreaming = inferredMode == null
				? isStreaming
				: inferredMode == StreamingMode.STREAMING;
		installHandlers(true);
		int sequenceNumber = 0;
		for (ParsedTraceLine parsedTraceLine : lines) {
			TraceLine traceLine = parsedTraceLine.traceLine();
			adoptProviderFromAuthoritativeTrace(traceLine);
			TraceRecord record =
					new TraceRecord(
							new TraceRecordId(
									currentProvider,
									traceMode(currentTraceIsStreaming),
									sequenceNumber++),
							traceLine,
							aTraceFile.toString(),
							parsedTraceLine.traceFileLineNumber());
			traceRecords.put(record.getId(), record);
			validateProvider(traceLine);
			TraceLineHandler handler = handlers.get(traceLine.getEventName());
			if (handler != null) {
				handler.handle(record);
			}
		}
		recordMissingTraceErrors();
	}

	private StreamingMode modeFromRequestSent(
			List<ParsedTraceLine> someTraceLines) {
		StreamingMode result = null;
		for (ParsedTraceLine parsedTraceLine : someTraceLines) {
			TraceLine traceLine = parsedTraceLine.traceLine();
			if (!"request_sent".equals(traceLine.getEventName())) {
				continue;
			}
			String mode = traceLine.getAuxiliaryData().get("mode");
			if (isBlank(mode)) {
				continue;
			}
			StreamingMode parsedMode;
			try {
				parsedMode = StreamingMode.valueOf(mode);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			if (result != null && result != parsedMode) {
				return null;
			}
			result = parsedMode;
		}
		return result;
	}

	public Map<TraceRecordId, TraceRecord> getTraceRecords() {
		return Map.copyOf(traceRecords);
	}

	public List<Boolean> getContextWindowContinuityResults() {
		return List.copyOf(contextWindowContinuityResults);
	}

	public List<Boolean> getRequestContextWindowMatchResults() {
		return List.copyOf(requestContextWindowMatchResults);
	}

	public List<Boolean> getRequestParameterMatchResults() {
		return List.copyOf(requestParameterMatchResults);
	}

	public List<Boolean> getResponseMatchResults() {
		return List.copyOf(responseMatchResults);
	}

	public List<Boolean> getMetadataMatchResults() {
		return List.copyOf(metadataMatchResults);
	}

	public List<Boolean> getExpectedInputMatchResults() {
		return List.copyOf(expectedInputMatchResults);
	}

	public List<Boolean> getStreamingChunkContextWindowMatchResults() {
		return List.copyOf(streamingChunkContextWindowMatchResults);
	}

	public List<Boolean> getStreamingChunkAccumulationResults() {
		return List.copyOf(streamingChunkAccumulationResults);
	}

	public List<Boolean> getStreamingCallbackInvocationResults() {
		return List.copyOf(streamingCallbackInvocationResults);
	}

	public List<Boolean> getProviderIndependentNonStreamingMatchResults() {
		return List.copyOf(providerIndependentNonStreamingMatchResults);
	}

	public List<String> getCheckerMessages() {
		return List.copyOf(checkerMessages);
	}

	public List<String> getGroupedCheckerMessages() {
		return List.copyOf(groupedCheckerMessages);
	}

	public Map<String, String> getCheckerExplanations() {
		return CHECKER_EXPLANATIONS;
	}

	public boolean isFalseCheckMessagesAreErrors() {
		return falseCheckMessagesAreErrors;
	}

	public boolean isFalseCheckMessagesAreWarnings() {
		return falseCheckMessagesAreWarnings;
	}

	public void setFalseCheckMessagesAreWarnings(
			boolean areFalseCheckMessagesWarnings) {
		falseCheckMessagesAreWarnings = areFalseCheckMessagesWarnings;
	}

	public void setFalseCheckMessagesAreErrors(
			boolean areFalseCheckMessagesErrors) {
		falseCheckMessagesAreErrors = areFalseCheckMessagesErrors;
	}

	public boolean isUncheckedCheckMessagesAreErrors() {
		return uncheckedCheckMessagesAreErrors;
	}

	public boolean isUncheckedCheckMessagesAreWarnings() {
		return uncheckedCheckMessagesAreWarnings;
	}

	public void setUncheckedCheckMessagesAreWarnings(
			boolean areUncheckedCheckMessagesWarnings) {
		uncheckedCheckMessagesAreWarnings = areUncheckedCheckMessagesWarnings;
	}

	public void setUncheckedCheckMessagesAreErrors(
			boolean areUncheckedCheckMessagesErrors) {
		uncheckedCheckMessagesAreErrors = areUncheckedCheckMessagesErrors;
	}

	public boolean isTrueCheckMessagesAreWarnings() {
		return trueCheckMessagesAreWarnings;
	}

	public void setTrueCheckMessagesAreWarnings(
			boolean areTrueCheckMessagesWarnings) {
		trueCheckMessagesAreWarnings = areTrueCheckMessagesWarnings;
	}

	public boolean isTrueCheckMessagesAreErrors() {
		return trueCheckMessagesAreErrors;
	}

	public void setTrueCheckMessagesAreErrors(
			boolean areTrueCheckMessagesErrors) {
		trueCheckMessagesAreErrors = areTrueCheckMessagesErrors;
	}

	private StreamingMode traceMode(boolean isStreaming) {
		return isStreaming ? StreamingMode.STREAMING : StreamingMode.NON_STREAMING;
	}

	private List<ParsedTraceLine> parsedTraceLines(Path aTraceFile)
			throws IOException {
		List<ParsedTraceLine> result = new ArrayList<>();
		List<String> fileLines =
				TraceFileIO.readLines(aTraceFile);
		for (int i = 0; i < fileLines.size(); i++) {
			String line = fileLines.get(i);
			TraceLine traceLine = TraceLineParser.parse(line);
			if (traceLine != null) {
				result.add(new ParsedTraceLine(traceLine, i + 1));
			}
		}
		return result;
	}

	private void installHandlers(boolean isStreaming) {
		handlers.clear();
		handlers.put(
				"server_handle_factory_fetched",
				this::handleServerHandleFactory);
		handlers.put(
				"server_handle_fetched",
				this::handleServerHandleFetched);
		handlers.put("request_sent", this::handleRequestSent);
		handlers.put("message_translated", this::handleMessageTranslated);
		handlers.put("response_translated", this::handleResponseTranslated);
		handlers.put(
				"parameter_translated",
				this::handleParameterTranslated);
		handlers.put(
				"metadata_translated",
				this::handleMetadataTranslated);
		if (isStreaming) {
			handlers.put(
					"provider_streaming_chunk_received",
					this::handleProviderStreamingChunkReceived);
			handlers.put(
					"message_merger_factory_fetched",
					this::handleStreamChunkMergerFactoryFetched);
			handlers.put(
					"streaming_chunk_accumulated",
					this::handleStreamingChunkAccumulated);
			handlers.put(
					"streaming_chunks_merged",
					this::handleStreamingChunksMerged);
			handlers.put(
					"partial_response_callback_invoked",
					this::handlePartialResponseCallbackInvoked);
			handlers.put(
					"complete_response_callback_invoked",
					this::handleCompleteResponseCallbackInvoked);
		}
	}

	private void handleServerHandleFactory(TraceRecord aTraceRecord) {
		TraceLine traceLine = aTraceRecord.getTraceLine();
		aTraceRecord.putExtractedData("provider", providerFor(traceLine));
		String provider = providerFor(traceLine);
		String registryClass =
				traceLine.getAuxiliaryData().get(
						"serverHandleFactoryRegistryClass");
		String factoryClass =
				traceLine.getAuxiliaryData().get(
						"serverHandleFactoryClass");
		String factoryIdentity =
				traceLine.getAuxiliaryData().get(
						"serverHandleFactoryIdentity");
		if (!isBlank(provider) && !isBlank(factoryIdentity)) {
			String previousProvider =
					providersByServerHandleFactoryIdentity.putIfAbsent(
							factoryIdentity,
							provider);
			if (previousProvider != null
					&& !previousProvider.equals(provider)) {
				recordError(
						TraceFilesProcessorError.INCONSISTENT_PROVIDER_NAME);
			}
		}
		if (providerMatches(provider) && !isBlank(factoryClass)) {
			add(
					factoryClass,
					provider + "ServerHandleFactory",
					aTraceRecord,
					"serverHandleFactoryClass",
					"server handle factory was fetched for the provider");
			aTraceRecord.putExtractedData(
					"serverHandleFactoryClass",
					factoryClass);
		}
		if (providerMatches(provider) && !isBlank(registryClass)) {
			add(
					registryClass,
					"ServerHandleFactoryRegistry",
					aTraceRecord,
					"serverHandleFactoryRegistryClass",
					"server handle factory was fetched from this registry");
			aTraceRecord.putExtractedData(
					"serverHandleFactoryRegistryClass",
					registryClass);
		}
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleServerHandleFetched(TraceRecord aTraceRecord) {
		TraceLine traceLine = aTraceRecord.getTraceLine();
		String factoryIdentity =
				traceLine.getAuxiliaryData().get(
						"serverHandleFactoryIdentity");
		String provider =
				providersByServerHandleFactoryIdentity.get(factoryIdentity);
		String serverHandleClass =
				traceLine.getAuxiliaryData().get("serverHandleClass");
		String serverHandleIdentity =
				traceLine.getAuxiliaryData().get("serverHandleIdentity");
		aTraceRecord.putExtractedData("provider", provider);
		aTraceRecord.putExtractedData(
				"serverHandleFactoryIdentity",
				factoryIdentity);
		aTraceRecord.putExtractedData(
				"serverHandleClass",
				serverHandleClass);
		if (isBlank(provider)) {
			recordError(TraceFilesProcessorError.INCONSISTENT_PROVIDER_NAME);
		}
		if (!isBlank(provider) && !isBlank(serverHandleIdentity)) {
			providersByServerHandleIdentity.put(
					serverHandleIdentity,
					provider);
		}
		if (providerMatches(provider) && !isBlank(serverHandleClass)) {
			add(
					serverHandleClass,
					provider + "ServerHandleAdapter",
					aTraceRecord,
					"serverHandleClass",
					"server handle was fetched for the provider");
		}
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleRequestSent(TraceRecord aTraceRecord) {
		currentStats.requestSent = true;
		currentResponseTranslationRecord = null;
		pendingTranslatedMetadata.clear();
		attachAuxiliaryExtractedData(aTraceRecord);
		aTraceRecord.putExtractedData(
				"providerIndependentContextWindowMessages",
				providerIndependentContextWindowMessages(aTraceRecord));
		aTraceRecord.putExtractedData(
				"providerIndependentContextWindowStructure",
				providerIndependentContextWindowStructure(aTraceRecord));
		aTraceRecord.putExtractedData(
				"providerDependentContextWindowStructure",
				providerDependentContextWindowStructure(aTraceRecord));
		aTraceRecord.putExtractedData(
				"providerDependentRequestStructure",
				providerDependentRequestStructure(aTraceRecord));
		aTraceRecord.putExtractedData(
				"providerIndependentCanonicalRequest",
				new CanonicalTraceRequest(
						requestModel(aTraceRecord),
						canonicalPropertyValues(
								aTraceRecord.getTraceLine()
										.getAuxiliaryData()
										.get("providerIndependentParameterStore")),
						providerIndependentContextWindowStructure(aTraceRecord)));
		aTraceRecord.putExtractedData(
				"providerDependentCanonicalRequest",
				new CanonicalTraceRequest(
						requestModel(aTraceRecord),
						pendingTranslatedParameters,
						providerDependentRequestStructure(aTraceRecord)));
		aTraceRecord.putExtractedData(
				"providerDependentContextWindowMessages",
				canonicalMessageKeys(
						providerDependentRequestStructure(aTraceRecord)));
		aTraceRecord.putExtractedData(
				"translatedParameterEvidence",
				List.copyOf(pendingParameterEvidence));
		pendingTranslatedParameters.clear();
		pendingParameterEvidence.clear();
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleMessageTranslated(TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		addSourceClassTag(
				aTraceRecord,
				currentProvider + "PromptMessageAdapter",
				"provider-independent message was translated to a provider message");
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleResponseTranslated(TraceRecord aTraceRecord) {
		currentStats.responseTranslated = true;
		attachAuxiliaryExtractedData(aTraceRecord);
		currentResponseTranslationRecord = aTraceRecord;
		updateResponseCanonicalData(
				aTraceRecord,
				new LinkedHashMap<>(pendingTranslatedMetadata));
		pendingTranslatedMetadata.clear();
		if (aTraceRecord.getId().mode() == StreamingMode.NON_STREAMING) {
			addSourceClassTag(
					aTraceRecord,
					currentProvider + "ResponseMessageAdapter",
					"provider response was translated to a provider-independent response message");
		}
		tagClassesInsideTrace(aTraceRecord);
	}

	private void addSourceClassTag(
			TraceRecord aTraceRecord,
			String aTag,
			String aReason) {
		String sourceClass =
				aTraceRecord.getTraceLine().getSourceClassName();
		if (!isBlank(sourceClass)) {
			add(
					sourceClass,
					aTag,
					aTraceRecord,
					"sourceClass",
					aReason);
		}
	}

	private void handleContextWindowMessageAdded(
			TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		String contextWindowMessage =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("contextWindowMessage");
		List<String> messages = canonicalMessageStrings(contextWindowMessage);
		if (!messages.isEmpty()) {
			aTraceRecord.putExtractedData(
					"contextWindowAddedMessage",
					messages.get(0));
		}
		aTraceRecord.putExtractedData(
				"contextWindowAddedMessageStructure",
				firstCanonicalMessageFromArgument(
						aTraceRecord,
						"contextWindowMessage"));
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleParameterTranslated(TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		Map<String, String> auxiliaryData =
				aTraceRecord.getTraceLine().getAuxiliaryData();
		putCanonicalProperty(
				pendingTranslatedParameters,
				auxiliaryData.get("propertyName"),
				auxiliaryData.get("propertyValue"));
		String handlerClass = loadableClassName(
				auxiliaryData.get("parameterHandlerClass"),
				auxiliaryData.get("parameterHandler"));
		pendingParameterEvidence.add(
				new ProviderParameterChecker.ParameterEvidence(
						auxiliaryData.get("propertyName"),
						auxiliaryData.get("propertyValue"),
						handlerClass,
						auxiliaryData.get("providerDependentTarget"),
						auxiliaryData.get("providerDependentTargetState")));
		add(
				handlerClass,
				currentProvider + "ParameterAdapter",
				aTraceRecord,
				"parameterHandlerClass",
				"provider parameter handler translated a parameter");
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleMetadataTranslated(TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		Map<String, String> auxiliaryData =
				aTraceRecord.getTraceLine().getAuxiliaryData();
		putCanonicalProperty(
				pendingTranslatedMetadata,
				auxiliaryData.get("propertyName"),
				auxiliaryData.get("propertyValue"));
		if (currentResponseTranslationRecord != null) {
			Map<String, String> metadata = responseMetadata(
					currentResponseTranslationRecord);
			putCanonicalProperty(
					metadata,
					auxiliaryData.get("propertyName"),
					auxiliaryData.get("propertyValue"));
			updateResponseCanonicalData(
					currentResponseTranslationRecord,
					metadata);
		}
		String handlerClass = loadableClassName(
				auxiliaryData.get("metadataHandlerClass"),
				auxiliaryData.get("metadataHandler"));
		add(
				handlerClass,
				currentProvider + "ResponseMetadataAdapter",
				aTraceRecord,
				"metadataHandlerClass",
				"provider metadata handler translated response metadata");
		tagClassesInsideTrace(aTraceRecord);
	}

	private void updateResponseCanonicalData(
			TraceRecord aTraceRecord,
			Map<String, String> someMetadata) {
		Map<String, String> metadata = someMetadata == null
				? Map.of()
				: new LinkedHashMap<>(someMetadata);
		Map<String, String> auxiliaryData =
				aTraceRecord.getTraceLine().getAuxiliaryData();
		aTraceRecord.putExtractedData(
				"providerIndependentCanonicalResponse",
				new CanonicalTraceResponse(
						reflectedMessageStructures(
								auxiliaryData.get(
										"providerIndependentResponse")),
						metadata));
		aTraceRecord.putExtractedData(
				"providerDependentCanonicalResponse",
				new CanonicalTraceResponse(
						providerResponseMessages(
								auxiliaryData.get(
										"providerDependentResponse")),
						metadata));
		aTraceRecord.putExtractedData(
				"translatedMetadata",
				metadata);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> responseMetadata(TraceRecord aTraceRecord) {
		Object metadata = aTraceRecord.getExtractedData()
				.get("translatedMetadata");
		if (metadata instanceof Map<?, ?> map) {
			return new LinkedHashMap<>((Map<String, String>) map);
		}
		return new LinkedHashMap<>();
	}

	private void handleStreamChunkMergerFactoryFetched(
			TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		aTraceRecord.putExtractedData(
				"provider",
				providerFor(aTraceRecord.getTraceLine()));
		String registryClass =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("streamChunkMergerFactoryRegistryClass");
		String factoryClass =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("streamChunkMergerFactoryClass");
		if (!isBlank(registryClass)) {
			add(
					registryClass,
					"StreamChunkMergerFactoryRegistry",
					aTraceRecord,
					"streamChunkMergerFactoryRegistryClass",
					"stream chunk merger factory was fetched from this registry");
		}
		if (!isBlank(factoryClass)) {
			add(
					factoryClass,
					"StreamChunkMergerFactory",
					aTraceRecord,
					"streamChunkMergerFactoryClass",
					"stream chunk merger factory was fetched");
		}
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleProviderStreamingChunkReceived(
			TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		List<TraceMessage> messages = providerResponseMessages(
				aTraceRecord.getTraceLine().getAuxiliaryData()
						.get("providerStreamingChunk"));
		aTraceRecord.putExtractedData(
				"providerStreamingChunkMessageStructure",
				messages.isEmpty() ? null : messages.get(0));
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleStreamingChunkAccumulated(
			TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		aTraceRecord.putExtractedData(
				"streamingChunkMessageStructure",
				firstCanonicalMessageFromArgument(aTraceRecord, "streamingChunk"));
		aTraceRecord.putExtractedData(
				"accumulatedMessageStructure",
				firstCanonicalMessageFromArgument(
						aTraceRecord,
						"accumulatedMessage"));
		String mergerClass =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("messageMergerClass");
		String mergerDump =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("messageMerger");
		String reflectedMergerClass =
				rootClassNameInReflectiveDump(mergerDump);
		if (!isBlank(reflectedMergerClass)) {
			mergerClass = reflectedMergerClass;
		}
		if (!isBlank(mergerClass)) {
			add(
					mergerClass,
					"StreamChunkMerger",
					aTraceRecord,
					"messageMergerClass",
					"streaming chunk was accumulated by this merger");
		}
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleStreamingChunksMerged(TraceRecord aTraceRecord) {
		attachAuxiliaryExtractedData(aTraceRecord);
		aTraceRecord.putExtractedData(
				"mergedMessageStructure",
				firstCanonicalMessageFromArgument(aTraceRecord, "mergedMessage"));
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handleStreamingCallbackInvoked(TraceRecord aTraceRecord) {
		TraceLine traceLine = aTraceRecord.getTraceLine();
		aTraceRecord.putExtractedData(
				"chunkCallbackAdapterClass",
				traceLine.getSourceClassName());
		attachAuxiliaryExtractedData(aTraceRecord);
		if (!isBlank(traceLine.getSourceClassName())) {
			add(
					traceLine.getSourceClassName(),
					currentProvider + "ChunkCallbackAdapter",
					aTraceRecord,
					"sourceClass",
					"streaming callback invocation was emitted by this callback adapter");
		}
		tagClassesInsideTrace(aTraceRecord);
	}

	private void handlePartialResponseCallbackInvoked(
			TraceRecord aTraceRecord) {
		currentStats.partialResponseCallbackInvoked = true;
		aTraceRecord.putExtractedData(
				"callbackArgumentStructure",
				firstCanonicalMessageFromArgument(
						aTraceRecord,
						"callbackArgument"));
		handleStreamingCallbackInvoked(aTraceRecord);
	}

	private void handleCompleteResponseCallbackInvoked(
			TraceRecord aTraceRecord) {
		currentStats.completeResponseCallbackInvoked = true;
		aTraceRecord.putExtractedData(
				"callbackArgumentStructure",
				firstCanonicalMessageFromArgument(
						aTraceRecord,
						"callbackArgument"));
		handleStreamingCallbackInvoked(aTraceRecord);
	}

	private void validateProvider(TraceLine aTraceLine) {
		if (providerShouldMatchTrace(aTraceLine)
				&& isBlank(aTraceLine.getAuxiliaryData().get("provider"))) {
			recordError(TraceFilesProcessorError.INCONSISTENT_PROVIDER_NAME);
		}
	}

	private void adoptProviderFromAuthoritativeTrace(TraceLine aTraceLine) {
		if (!providerShouldMatchTrace(aTraceLine)) {
			return;
		}
		String provider = TraceProviderNames.canonical(aTraceLine.getAuxiliaryData().get("provider"));
		if (isBlank(provider)) {
			return;
		}
		if (!isBlank(currentProvider) && !currentProvider.equals(provider)) {
			recordError(TraceFilesProcessorError.INCONSISTENT_PROVIDER_NAME);
		}
		currentProvider = provider;
	}

	private boolean providerShouldMatchTrace(TraceLine aTraceLine) {
		return "server_handle_factory_fetched".equals(
				aTraceLine.getEventName());
	}

	private boolean providerMatches(String aProvider) {
		return !isBlank(aProvider) && currentProvider.equals(aProvider);
	}

	private String providerFor(TraceLine aTraceLine) {
		String provider = TraceProviderNames.canonical(aTraceLine.getAuxiliaryData().get("provider"));
		if (!isBlank(provider)) {
			return provider;
		}
		return currentProvider;
	}

	private List<String> providers() {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (TraceRecord record : traceRecords.values()) {
			String provider = record.getId().provider();
			if (!isBlank(provider)) {
				result.add(provider);
			}
		}
		return List.copyOf(result);
	}

	private void recordMissingTraceErrors() {
		if (!currentStats.requestSent) {
			recordError(TraceFilesProcessorError.MISSING_REQUEST_SENT);
		}
		if (!currentStats.responseTranslated) {
			recordError(TraceFilesProcessorError.MISSING_RESPONSE_TRANSLATED);
		}
		if (currentTraceIsStreaming) {
			if (!currentStats.partialResponseCallbackInvoked) {
				recordError(
						TraceFilesProcessorError
								.MISSING_PARTIAL_RESPONSE_CALLBACK_INVOKED);
			}
			if (!currentStats.completeResponseCallbackInvoked) {
				recordError(
						TraceFilesProcessorError
								.MISSING_COMPLETE_RESPONSE_CALLBACK_INVOKED);
			}
		}
	}

	private void checkExpectedClassTags() {
		for (String provider : providers()) {
			checkClassExistsForTag(provider + "ServerHandleFactory");
			checkClassExistsForTag(provider + "ServerHandleAdapter");
			checkClassExistsForTag(provider + "PromptMessageAdapter");
			checkClassExistsForTag(provider + "ResponseMessageAdapter");
			checkClassExistsForTag(provider + "ParameterAdapter");
			checkClassExistsForTag(provider + "ChunkCallbackAdapter");
		}
		checkClassExistsForTag("StreamChunkMergerFactoryRegistry");
		checkClassExistsForTag("StreamChunkMergerFactory");
		checkClassExistsForTag("StreamChunkMerger");
	}

	private List<String> expectedTraceEventsForTag(String aTag) {
		if (isBlank(aTag)) {
			return List.of();
		}
		if (aTag.endsWith("ServerHandleFactory")) {
			return List.of("server_handle_factory_fetched");
		}
		if (aTag.endsWith("ServerHandleAdapter")) {
			return List.of("server_handle_fetched");
		}
		if (aTag.endsWith("PromptMessageAdapter")) {
			return List.of("message_translated");
		}
		if (aTag.endsWith("ResponseMessageAdapter")) {
			return List.of("response_translated");
		}
		if ("ServerHandleFactoryRegistry".equals(aTag)) {
			return List.of("server_handle_factory_fetched");
		}
		if (aTag.endsWith("ParameterAdapter")) {
			return List.of("parameter_translated");
		}
		if (aTag.endsWith("ResponseMetadataAdapter")) {
			return List.of("metadata_translated");
		}
		if (aTag.endsWith("ChunkCallbackAdapter")) {
			return List.of(
					"partial_response_callback_invoked",
					"complete_response_callback_invoked");
		}
		if ("StreamChunkMergerFactoryRegistry".equals(aTag)
				|| "StreamChunkMergerFactory".equals(aTag)) {
			return List.of("message_merger_factory_fetched");
		}
		if ("StreamChunkMerger".equals(aTag)) {
			return List.of("streaming_chunk_accumulated");
		}
		if ("ParameterStore".equals(aTag)) {
			return List.of("request_sent");
		}
		if ("GenericContextWindowMessage".equals(aTag)) {
			return List.of(
					"request_sent",
					"response_translated");
		}
		return List.of();
	}

	public TraceCheckResult checkContextWindowContinuity() {
		for (String provider : providers()) {
			checkContextWindowContinuity(provider, StreamingMode.NON_STREAMING);
			checkContextWindowContinuity(provider, StreamingMode.STREAMING);
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	public TraceCheckResult checkPreviousSystemPromptsRetained() {
		checkContextWindowContinuity();
		return checkerResult(PREVIOUS_SYSTEM_PROMPT_IN_CONTEXT_WINDOW);
	}

	public TraceCheckResult checkPreviousSystemPromptsRetained(String aProvider) {
		checkContextWindowContinuity(aProvider, StreamingMode.NON_STREAMING);
		checkContextWindowContinuity(aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PREVIOUS_SYSTEM_PROMPT_IN_CONTEXT_WINDOW, aProvider);
	}

	public TraceCheckResult checkPreviousUserPromptsRetained() {
		checkContextWindowContinuity();
		return checkerResult(PREVIOUS_PROMPT_IN_NEW_CONTEXT_WINDOW);
	}

	public TraceCheckResult checkPreviousUserPromptsRetained(String aProvider) {
		checkContextWindowContinuity(aProvider, StreamingMode.NON_STREAMING);
		checkContextWindowContinuity(aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PREVIOUS_PROMPT_IN_NEW_CONTEXT_WINDOW, aProvider);
	}

	public TraceCheckResult checkPreviousCompletedResponsesRetained() {
		checkContextWindowContinuity();
		return checkerResult(PREVIOUS_COMPLETED_RESPONSE_IN_CONTEXT_WINDOW);
	}

	public TraceCheckResult checkPreviousCompletedResponsesRetained(
			String aProvider) {
		checkContextWindowContinuity(aProvider, StreamingMode.NON_STREAMING);
		checkContextWindowContinuity(aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PREVIOUS_COMPLETED_RESPONSE_IN_CONTEXT_WINDOW, aProvider);
	}

	public TraceCheckResult checkPreviousImagesRetained() {
		for (String provider : providers()) {
			checkPreviousImagesRetained(provider);
		}
		return checkerResult(PREVIOUS_IMAGE_IN_CONTEXT_WINDOW);
	}

	public TraceCheckResult checkPreviousImagesRetained(String aProvider) {
		checkImageContinuity(aProvider, StreamingMode.NON_STREAMING);
		checkImageContinuity(aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PREVIOUS_IMAGE_IN_CONTEXT_WINDOW, aProvider);
	}

	private void checkImageContinuity(
			String aProvider,
			StreamingMode aMode) {
		TraceRecord previousRecord = null;
		List<String> previousImages = List.of();
		List<String> previousMessageKeys = List.of();
		for (TraceRecord record : requestRecords(aProvider, aMode)) {
			CanonicalTraceRequest request = (CanonicalTraceRequest)
					record.getExtractedData().get(
							"providerDependentCanonicalRequest");
			List<TraceMessage> messages = request == null
					? List.of() : request.contextWindow();
			List<String> currentImages = imagePartValues(messages);
			@SuppressWarnings("unchecked")
			List<String> currentMessageKeys = (List<String>)
					record.getExtractedData().get(
							"providerDependentContextWindowMessages");
			if (currentMessageKeys == null) {
				currentMessageKeys = List.of();
			}
			if (previousRecord != null && !previousImages.isEmpty()) {
				boolean expectedCompaction = expectedCompactionFor(
						previousMessageKeys, currentMessageKeys).isExpected();
				boolean retained = containsAllOccurrences(
						currentImages, previousImages);
				boolean passed = retained || expectedCompaction;
				recordCheckerMessage(
						PREVIOUS_IMAGE_IN_CONTEXT_WINDOW
								+ " provider=" + aProvider
								+ " mode=" + aMode
								+ " previousTraceLine="
								+ traceLineLocation(previousRecord)
								+ " currentTraceLine="
								+ traceLineLocation(record)
								+ " previousImages=" + previousImages
								+ " currentImages=" + currentImages
								+ " expectedCompaction=" + expectedCompaction
								+ " match=" + passed);
			}
			previousRecord = record;
			previousImages = currentImages;
			previousMessageKeys = currentMessageKeys;
		}
	}

	private List<String> imagePartValues(List<TraceMessage> someMessages) {
		ArrayList<String> result = new ArrayList<>();
		for (TraceMessage message : someMessages) {
			for (TracePart part : message.parts()) {
				if ("imageBytes".equals(part.kind())) {
					result.add(part.value());
				}
			}
		}
		return List.copyOf(result);
	}

	private boolean containsAllOccurrences(
			List<String> someCurrentValues,
			List<String> somePreviousValues) {
		ArrayList<String> unmatched = new ArrayList<>(someCurrentValues);
		for (String previous : somePreviousValues) {
			if (!unmatched.remove(previous)) {
				return false;
			}
		}
		return true;
	}

	public TraceCheckResult checkExpectedContextWindowCompactions() {
		checkContextWindowContinuity();
		return checkerResult(EXPECTED_CONTEXT_WINDOW_COMPACTION);
	}

	public TraceCheckResult checkExpectedContextWindowCompactions(
			String aProvider) {
		checkContextWindowContinuity(aProvider, StreamingMode.NON_STREAMING);
		checkContextWindowContinuity(aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				EXPECTED_CONTEXT_WINDOW_COMPACTION, aProvider);
	}

	public TraceCheckResult checkProviderGenericTextualContextWindowMatches() {
		for (String provider : providers()) {
			checkProviderGenericTextualContextWindowMatches(
					provider,
					StreamingMode.NON_STREAMING);
			checkProviderGenericTextualContextWindowMatches(provider, StreamingMode.STREAMING);
		}
		return checkerResult(PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH);
	}

	public TraceCheckResult checkProviderGenericTextualContextWindowMatches(
			String aProvider) {
		checkProviderGenericTextualContextWindowMatches(
				aProvider, StreamingMode.NON_STREAMING);
		checkProviderGenericTextualContextWindowMatches(aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH, aProvider);
	}

	public TraceCheckResult checkProviderGenericTextualContextWindowMatches(
			String aProvider,
			StreamingMode aMode) {
		for (TraceRecord record : requestRecords(aProvider, aMode)) {
			CanonicalTraceRequest providerIndependentRequest =
					(CanonicalTraceRequest) record.getExtractedData()
							.get("providerIndependentCanonicalRequest");
			CanonicalTraceRequest providerDependentRequest =
					(CanonicalTraceRequest) record.getExtractedData()
							.get("providerDependentCanonicalRequest");
			List<TraceMessage> providerDependentMessages =
					providerDependentRequest.contextWindow();
			String independentDump = record.getTraceLine()
					.getAuxiliaryData().get("providerIndependentContextWindow");
			boolean independentHasMessages = providerIndependentRequest != null
					&& !providerIndependentRequest.contextWindow().isEmpty();
			CanonicalValueComparison valueComparison;
			if (independentHasMessages && providerDependentMessages.isEmpty()) {
				valueComparison = new CanonicalValueComparison(
						false,
						List.of(),
						"provider-dependent context window produced no canonical messages");
			} else {
				valueComparison =
						providerIndependentValueComparator.compare(
								independentDump,
								canonicalValueProjector.projectContextWindowText(
										providerDependentRequest),
								this::isProviderIndependentValueIgnored);
			}
			ContextWindowComparison comparison = new ContextWindowComparison(
					valueComparison.matches(), valueComparison.detail());
			requestContextWindowMatchResults.add(comparison.matches());
			record.putExtractedData(
					"providerIndependentContextWindowMatchesProviderDependentContextWindow",
					comparison.matches());
			recordCheckerMessage(
					requestContextWindowMatchMessage(
							aProvider,
							aMode,
							record,
							valueComparison,
							providerDependentMessages,
							comparison));
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	public TraceCheckResult checkProviderGenericParameterMatches() {
		for (String provider : providers()) {
			checkProviderGenericParameterMatches(
					provider, StreamingMode.NON_STREAMING);
			checkProviderGenericParameterMatches(
					provider, StreamingMode.STREAMING);
		}
		return checkerResult(PROVIDER_GENERIC_PARAMETER_MATCH);
	}

	public TraceCheckResult checkProviderGenericParameterMatches(
			String aProvider) {
		checkProviderGenericParameterMatches(
				aProvider, StreamingMode.NON_STREAMING);
		checkProviderGenericParameterMatches(
				aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PROVIDER_GENERIC_PARAMETER_MATCH, aProvider);
	}

	public TraceCheckResult checkProviderGenericParameterMatches(
			String aProvider,
			StreamingMode aMode) {
		for (TraceRecord record : requestRecords(aProvider, aMode)) {
			String difference = parameterEvidenceDifference(
					aProvider, aMode, record);
			boolean matches = difference.isEmpty();
			requestParameterMatchResults.add(matches);
			record.putExtractedData(
					"providerIndependentParametersMatchProviderDependentParameters",
					matches);
			recordCheckerMessage(
					matches
							? TraceProcessorMessageLevel.INFO
							: TraceProcessorMessageLevel.ERROR,
					PROVIDER_GENERIC_PARAMETER_MATCH
							+ " provider=" + aProvider
							+ " mode=" + aMode
							+ " match=" + matches
							+ " detail=\""
							+ (matches ? "translated parameters match" : difference)
							+ "\" traceLine=" + traceLineLocation(record));
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	public TraceCheckResult checkProviderGenericResponseMatches() {
		for (String provider : providers()) {
			checkProviderGenericResponseMatches(
					provider, StreamingMode.NON_STREAMING);
			checkProviderGenericResponseMatches(
					provider, StreamingMode.STREAMING);
		}
		return checkerResult(PROVIDER_GENERIC_RESPONSE_MATCH);
	}

	public TraceCheckResult checkProviderGenericResponseMatches(
			String aProvider) {
		checkProviderGenericResponseMatches(
				aProvider, StreamingMode.NON_STREAMING);
		checkProviderGenericResponseMatches(
				aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PROVIDER_GENERIC_RESPONSE_MATCH, aProvider);
	}

	public TraceCheckResult checkProviderGenericResponseMatches(
			String aProvider,
			StreamingMode aMode) {
		for (TraceRecord record :
				records(aProvider, aMode, "response_translated")) {
			CanonicalTraceResponse independent =
					(CanonicalTraceResponse) record.getExtractedData()
							.get("providerIndependentCanonicalResponse");
			CanonicalTraceResponse dependent =
					(CanonicalTraceResponse) record.getExtractedData()
							.get("providerDependentCanonicalResponse");
			List<TraceMessage> dependentMessages = dependent.messages();
			if (aMode == StreamingMode.STREAMING
					&& messagesHaveNoParts(dependentMessages)) {
				TraceMessage mergedMessage = precedingMergedMessage(
						aProvider, record);
				if (mergedMessage != null) {
					dependentMessages = List.of(mergedMessage);
				}
			}
			CanonicalTraceResponse effectiveDependent =
					new CanonicalTraceResponse(
							dependentMessages, dependent.metadata());
			String independentDump = record.getTraceLine()
					.getAuxiliaryData().get("providerIndependentResponse");
			boolean independentHasMessages = independent != null
					&& !independent.messages().isEmpty();
			CanonicalValueComparison valueComparison;
			if (independentHasMessages && dependentMessages.isEmpty()) {
				valueComparison = new CanonicalValueComparison(
						false,
						List.of(),
						"provider-dependent response produced no canonical messages");
			} else {
				valueComparison =
						providerIndependentValueComparator.compare(
								independentDump,
								canonicalValueProjector.projectResponseContent(
										effectiveDependent),
								this::isProviderIndependentValueIgnored);
			}
			ContextWindowComparison comparison = new ContextWindowComparison(
					valueComparison.matches(), valueComparison.detail());
			responseMatchResults.add(comparison.matches());
			record.putExtractedData(
					"providerIndependentResponseMatchesProviderDependentResponse",
					comparison.matches());
			recordCheckerMessage(
					comparison.matches()
							? TraceProcessorMessageLevel.INFO
							: TraceProcessorMessageLevel.ERROR,
					PROVIDER_GENERIC_RESPONSE_MATCH
							+ " provider=" + aProvider
							+ " mode=" + aMode
							+ " match=" + comparison.matches()
							+ " detail=\""
							+ comparison.detail().replace("\"", "'")
							+ "\" traceLine=" + traceLineLocation(record));
		}
		return checkerResultForProvider(
				PROVIDER_GENERIC_RESPONSE_MATCH, aProvider);
	}

	public TraceCheckResult checkProviderIndependentContextWindowTypes(
			String aProvider) {
		checkProviderIndependentTypes(aProvider, "request_sent",
				"providerIndependentContextWindow",
				PROVIDER_INDEPENDENT_CONTEXT_WINDOW_TYPE_INTEGRITY);
		return checkerResultForProvider(
				PROVIDER_INDEPENDENT_CONTEXT_WINDOW_TYPE_INTEGRITY, aProvider);
	}

	public TraceCheckResult checkProviderIndependentParameterStoreTypes(
			String aProvider) {
		checkProviderIndependentTypes(aProvider, "request_sent",
				"providerIndependentParameterStore",
				PROVIDER_INDEPENDENT_PARAMETER_STORE_TYPE_INTEGRITY);
		return checkerResultForProvider(
				PROVIDER_INDEPENDENT_PARAMETER_STORE_TYPE_INTEGRITY, aProvider);
	}

	public TraceCheckResult checkProviderIndependentResponseTypes(
			String aProvider) {
		checkProviderIndependentTypes(aProvider, "response_translated",
				"providerIndependentResponse",
				PROVIDER_INDEPENDENT_RESPONSE_TYPE_INTEGRITY);
		return checkerResultForProvider(
				PROVIDER_INDEPENDENT_RESPONSE_TYPE_INTEGRITY, aProvider);
	}

	private void checkProviderIndependentTypes(
			String aProvider,
			String anEventName,
			String anArgumentName,
			String aCheckName) {
		for (StreamingMode mode : StreamingMode.values()) {
			for (TraceRecord record : records(aProvider, mode, anEventName)) {
				List<String> foundTypes = providerSpecificTypesInArguments(
						record, anArgumentName);
				boolean passed = foundTypes.isEmpty();
				recordCheckerMessage(
						passed ? TraceProcessorMessageLevel.INFO
								: TraceProcessorMessageLevel.ERROR,
						aCheckName
								+ " provider=" + aProvider
								+ " mode=" + mode
								+ " match=" + passed
								+ (passed ? "" : " error="
										+ TraceFilesProcessorError.PROVIDER_SPECIFIC_TYPE_IN_PROVIDER_INDEPENDENT_STRUCTURE)
								+ " providerSpecificTypes=" + foundTypes
								+ " traceLine=" + traceLineLocation(record));
			}
		}
	}

	private boolean messagesHaveNoParts(List<TraceMessage> someMessages) {
		if (someMessages == null || someMessages.isEmpty()) {
			return true;
		}
		for (TraceMessage message : someMessages) {
			if (message == null) {
				continue;
			}
			for (TracePart part : message.parts()) {
				if (!"text".equals(part.kind())
						|| !normalizedText(part.value()).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	private TraceMessage precedingMergedMessage(
			String aProvider,
			TraceRecord aResponseRecord) {
		int responseSequence = aResponseRecord.getId().sequenceNumber();
		TraceMessage result = null;
		for (TraceRecord candidate : records(
				aProvider, StreamingMode.STREAMING)) {
			if (candidate.getId().sequenceNumber() >= responseSequence) {
				break;
			}
			String event = candidate.getTraceLine().getEventName();
			if ("response_translated".equals(event)) {
				result = null;
			}
			if ("streaming_chunks_merged".equals(event)) {
				result = (TraceMessage) candidate.getExtractedData()
						.get("mergedMessageStructure");
			}
		}
		return result;
	}

	public TraceCheckResult checkProviderGenericMetadataMatches() {
		for (String provider : providers()) {
			checkProviderGenericMetadataMatches(
					provider, StreamingMode.NON_STREAMING);
			checkProviderGenericMetadataMatches(
					provider, StreamingMode.STREAMING);
		}
		return checkerResult(PROVIDER_GENERIC_METADATA_MATCH);
	}

	public TraceCheckResult checkProviderGenericMetadataMatches(
			String aProvider) {
		checkProviderGenericMetadataMatches(
				aProvider, StreamingMode.NON_STREAMING);
		checkProviderGenericMetadataMatches(
				aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				PROVIDER_GENERIC_METADATA_MATCH, aProvider);
	}

	public TraceCheckResult checkProviderGenericMetadataMatches(
			String aProvider,
			StreamingMode aMode) {
		List<TraceRecord> metadataRecords =
				records(aProvider, aMode, "metadata_translated");
		for (TraceRecord record : metadataRecords) {
			Map<String, String> data =
					record.getTraceLine().getAuxiliaryData();
			String propertyName = data.get("propertyName");
			String propertyValue = data.get("propertyValue");
			String providerSource = data.get("providerDependentSource");
			String difference = providerMetadataDifference(
							providerSource,
							propertyValue);
			boolean matches = difference == null;
			if (matches) {
				difference = "translated value matches native metadata evidence";
			}
			metadataMatchResults.add(matches);
			record.putExtractedData(
					"translatedMetadataMatchesProviderDependentSource",
					matches);
			recordCheckerMessage(
					matches
							? TraceProcessorMessageLevel.INFO
							: TraceProcessorMessageLevel.ERROR,
					PROVIDER_GENERIC_METADATA_MATCH
							+ " provider=" + aProvider
							+ " mode=" + aMode
							+ " propertyName=" + propertyName
							+ " match=" + matches
							+ " detail=\""
							+ difference.replace("\"", "'")
							+ "\" traceLine=" + traceLineLocation(record));
		}
		return checkerResultForProvider(
				PROVIDER_GENERIC_METADATA_MATCH, aProvider);
	}

	private String providerMetadataDifference(String source, String value) {
		for (ProviderTraceInterpreter interpreter : providerTraceInterpreters) {
			NativeMetadataRegistry registry = interpreter.nativeMetadataRegistry(source);
			if (registry != null) return registry.difference(source, value);
		}
		return "no native metadata mapping registered for source";
	}

	@SuppressWarnings("unchecked")
	private String parameterEvidenceDifference(
			String aProvider,
			StreamingMode aMode,
			TraceRecord aRecord) {
		List<ProviderParameterChecker.ParameterEvidence> evidence =
				(List<ProviderParameterChecker.ParameterEvidence>)
						aRecord.getExtractedData()
								.get("translatedParameterEvidence");
		if (evidence == null) {
			evidence = List.of();
		}
		String parameterStore = aRecord.getTraceLine()
				.getAuxiliaryData()
				.get("providerIndependentParameterStore");
		String firstDifference = "";
		List<String> expectedParameterNames =
				observedParameterNames(aProvider);
		if (expectedParameterNames.stream().noneMatch(name ->
				providerParameterCheckers.containsKey(parameterCheckerKey(aProvider, name)))) {
			return "no supported parameter translation was traced for provider " + aProvider;
		}
		for (String expectedName : expectedParameterNames) {
			if (!providerParameterCheckers.containsKey(parameterCheckerKey(aProvider, expectedName))) {
				recordCheckerMessage(TraceProcessorMessageLevel.INFO, "additional_parameter provider=" + aProvider
						+ " propertyName=" + expectedName + " checked=false reason=no_registered_rule");
				continue;
			}
			ProviderParameterChecker.ParameterEvidence item =
					parameterEvidence(evidence, expectedName);
			if (item == null) {
				recordCheckerMessage(
						TraceProcessorMessageLevel.ERROR,
						"generic_parameter_name_value_match provider="
								+ aProvider
								+ " mode="
								+ aMode
								+ " propertyName="
								+ expectedName
								+ " translationPresent=false match=false traceLine="
								+ traceLineLocation(aRecord));
				recordError(
						TraceFilesProcessorError
								.GENERIC_PARAMETER_NAME_OR_VALUE_MISSING);
				if (firstDifference.isEmpty()) {
					firstDifference = "parameter translation missing: "
							+ expectedName;
				}
				continue;
			}
			boolean genericMatch = genericStoreContains(
					parameterStore,
					item.propertyName(),
					item.propertyValue());
			recordCheckerMessage(
					genericMatch
							? TraceProcessorMessageLevel.INFO
							: TraceProcessorMessageLevel.ERROR,
					"generic_parameter_name_value_match provider="
							+ aProvider
							+ " mode="
							+ aMode
							+ " propertyName="
							+ item.propertyName()
							+ " propertyValue=\""
							+ String.valueOf(item.propertyValue())
									.replace("\"", "'")
							+ "\" match="
							+ genericMatch
							+ " traceLine="
							+ traceLineLocation(aRecord));
			if (!genericMatch) {
				recordError(
						TraceFilesProcessorError
								.GENERIC_PARAMETER_NAME_OR_VALUE_MISSING);
				if (firstDifference.isEmpty()) {
					firstDifference = "generic store lacks "
							+ item.propertyName()
							+ "="
							+ item.propertyValue();
				}
			}
			ProviderParameterChecker checker =
					providerParameterCheckers.get(
							parameterCheckerKey(
									aProvider,
									item.propertyName()));
			ProviderParameterChecker.ParameterEvidence completeEvidence =
					new ProviderParameterChecker.ParameterEvidence(
							item.propertyName(),
							item.propertyValue(),
							item.parameterAdapterClass(),
							item.providerDependentTarget(),
							item.providerDependentTargetState());
			boolean targetMatches = checker != null
					&& checker.isExpectedTarget(completeEvidence);
			boolean providerAssigned = targetMatches
					&& checker.isAssigned(completeEvidence);
			recordCheckerMessage(
					providerAssigned
							? TraceProcessorMessageLevel.INFO
							: TraceProcessorMessageLevel.ERROR,
					"provider_parameter_assigned provider="
							+ aProvider
							+ " mode="
							+ aMode
							+ " propertyName="
							+ item.propertyName()
							+ " checkerRegistered="
							+ (checker != null)
							+ " targetMatches="
							+ targetMatches
							+ " assigned="
							+ providerAssigned
							+ " traceLine="
							+ traceLineLocation(aRecord));
			if (!providerAssigned) {
				recordError(
						TraceFilesProcessorError
								.PROVIDER_PARAMETER_NOT_ASSIGNED);
				if (firstDifference.isEmpty()) {
					firstDifference = "provider parameter not assigned: "
							+ item.propertyName();
				}
			}
		}
		return firstDifference;
	}

	private ProviderParameterChecker.ParameterEvidence parameterEvidence(
			List<ProviderParameterChecker.ParameterEvidence> someEvidence,
			String aPropertyName) {
		for (ProviderParameterChecker.ParameterEvidence evidence : someEvidence) {
			if (aPropertyName.equals(evidence.propertyName())) {
				return evidence;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private List<String> observedParameterNames(String aProvider) {
		java.util.LinkedHashSet<String> result =
				new java.util.LinkedHashSet<>();
		for (StreamingMode mode : StreamingMode.values()) {
			for (TraceRecord record : requestRecords(aProvider, mode)) {
				Object value = record.getExtractedData()
						.get("translatedParameterEvidence");
				if (!(value instanceof List<?>)) {
					continue;
				}
				for (ProviderParameterChecker.ParameterEvidence evidence :
						(List<ProviderParameterChecker.ParameterEvidence>) value) {
					if (!isBlank(evidence.propertyName())) {
						result.add(evidence.propertyName());
					}
				}
			}
		}
		return List.copyOf(result);
	}

	private boolean genericStoreContains(
			String aStoreDump,
			String aPropertyName,
			String aPropertyValue) {
		if (isBlank(aStoreDump) || isBlank(aPropertyName)) {
			return false;
		}
		return providerIndependentKeyValueComparator.compare(
				aStoreDump,
				Map.of(aPropertyName, aPropertyValue)).matches();
	}

	private String parameterCheckerKey(
			String aProvider,
			String aParameterName) {
		return String.valueOf(aProvider).trim().toLowerCase()
				+ "\u0000"
				+ String.valueOf(aParameterName).trim().toLowerCase();
	}

	public TraceCheckResult checkStreamingChunksReflectedInNextContextWindow() {
		for (String provider : providers()) {
			checkStreamingChunksReflectedInNextContextWindow(provider);
		}
		return checkerResult(
				STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW);
	}

	public TraceCheckResult checkStreamingChunksAccumulatedCorrectly() {
		for (String provider : providers()) {
			checkStreamingChunksAccumulatedCorrectly(provider);
		}
		return checkerResult(STREAMING_CHUNKS_ACCUMULATED_CORRECTLY);
	}

	public TraceCheckResult checkStreamingChunksAccumulatedCorrectly(
			String aProvider) {
		StringBuilder expectedText = new StringBuilder();
		Map<String, TraceTextSummary> previousHashes = new LinkedHashMap<>();
		Map<String, Integer> indexes = new LinkedHashMap<>();
		for (TraceRecord record : records(aProvider, StreamingMode.STREAMING)) {
			String eventName = record.getTraceLine().getEventName();
			Map<String, String> data = record.getTraceLine().getAuxiliaryData();
			if ("streaming_chunks_merged".equals(eventName)) {
				TraceMessage mergedMessage = (TraceMessage) record.getExtractedData().get("mergedMessageStructure");
				if (mergedMessage != null && "SYSTEM".equals(mergedMessage.role())
						&& !data.containsKey("streamHashId")) continue;
				if (data.containsKey("streamTextEvidenceVersion")) {
					String id = data.get("streamHashId");
					TraceTextSummary expected = TraceTextSummary.parse(data.get("expectedMergedTextSummary"));
					TraceTextSummary actual = TraceTextSummary.parse(data.get("mergedTextSummary"));
					TraceMessage merged = (TraceMessage) record.getExtractedData().get("mergedMessageStructure");
					boolean match = "1".equals(data.get("streamTextEvidenceVersion"))
							&& "complete".equals(data.get("streamTextHashStatus"))
							&& expected != null && expected.equals(previousHashes.get(id)) && expected.equals(actual)
							&& java.util.Objects.equals(indexes.get(id), integerValue(data.get("streamHashIndex")))
							&& merged != null && TraceTextSummary.matches(actual.token(), textOf(merged));
					streamingChunkAccumulationResults.add(match);
					recordCheckerMessage(STREAMING_CHUNKS_ACCUMULATED_CORRECTLY + " provider=" + aProvider
							+ " mode=" + StreamingMode.STREAMING + " traceLine=" + traceLineLocation(record)
							+ " comparison=independent_running_sha256 final=true match=" + match);
					previousHashes.remove(id); indexes.remove(id);
				}
				expectedText.setLength(0);
				continue;
			}
			if (!"streaming_chunk_accumulated".equals(eventName)) {
				continue;
			}
			TraceMessage chunk = (TraceMessage) record.getExtractedData()
					.get("streamingChunkMessageStructure");
			TraceMessage accumulated = (TraceMessage) record.getExtractedData()
					.get("accumulatedMessageStructure");
			if (chunk != null && !data.containsKey("streamTextEvidenceVersion")) {
				expectedText.append(textOf(chunk));
			}
			boolean matches;
			if (data.containsKey("streamTextEvidenceVersion")) {
				String id = data.get("streamHashId");
				TraceTextSummary expected = TraceTextSummary.parse(data.get("expectedAccumulatedTextSummary"));
				TraceTextSummary actual = TraceTextSummary.parse(data.get("accumulatedTextSummary"));
				TraceTextSummary chunkHash = TraceTextSummary.parse(data.get("chunkTextSummary"));
				TraceTextSummary previous = TraceTextSummary.parse(data.get("previousAccumulatedTextSummary"));
				int index = integerValue(data.get("streamHashIndex"));
				matches = "1".equals(data.get("streamTextEvidenceVersion")) && !isBlank(id)
						&& "complete".equals(data.get("streamTextHashStatus")) && expected != null
						&& expected.equals(actual) && chunkHash != null && chunk != null && accumulated != null
						&& index == indexes.getOrDefault(id, 0) + 1
						&& previous != null && previous.equals(previousHashes.getOrDefault(id, TraceTextSummary.from("")))
						&& TraceTextSummary.matches(chunkHash.token(), textOf(chunk))
						&& TraceTextSummary.matches(actual.token(), textOf(accumulated));
				if (expected != null) previousHashes.put(id, expected);
				indexes.put(id, index);
			} else {
				matches = accumulated != null && expectedText.toString().equals(textOf(accumulated));
			}
			streamingChunkAccumulationResults.add(matches);
			recordCheckerMessage(
					STREAMING_CHUNKS_ACCUMULATED_CORRECTLY
							+ " provider=" + aProvider
							+ " mode=" + StreamingMode.STREAMING
							+ " traceLine=" + traceLineLocation(record)
							+ " expectedCharacters=" + expectedText.length()
							+ " accumulatedCharacters="
							+ (accumulated == null ? 0 : textOf(accumulated).length())
							+ " match=" + matches);
		}
		return checkerResultForProvider(
				STREAMING_CHUNKS_ACCUMULATED_CORRECTLY, aProvider);
	}

	public TraceCheckResult checkStreamingCallbacksInvoked() {
		for (String provider : providers()) {
			checkStreamingCallbacksInvoked(provider);
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	public TraceCheckResult checkPartialResponseCallbacksInvoked() {
		for (String provider : providers()) {
			checkStreamingCallbackInvoked(
					provider,
					PARTIAL_RESPONSE_CALLBACK_INVOKED,
					TraceFilesProcessorError
							.MISSING_PARTIAL_RESPONSE_CALLBACK_INVOKED);
		}
		return checkerResult(PARTIAL_RESPONSE_CALLBACK_INVOKED);
	}

	public TraceCheckResult checkPartialResponseCallbacksInvoked(
			String aProvider) {
		checkStreamingCallbackInvoked(
				aProvider,
				PARTIAL_RESPONSE_CALLBACK_INVOKED,
				TraceFilesProcessorError
						.MISSING_PARTIAL_RESPONSE_CALLBACK_INVOKED);
		return checkerResultForProvider(
				PARTIAL_RESPONSE_CALLBACK_INVOKED, aProvider);
	}

	public TraceCheckResult checkCompleteResponseCallbacksInvoked() {
		for (String provider : providers()) {
			checkStreamingCallbackInvoked(
					provider,
					COMPLETE_RESPONSE_CALLBACK_INVOKED,
					TraceFilesProcessorError
							.MISSING_COMPLETE_RESPONSE_CALLBACK_INVOKED);
		}
		return checkerResult(COMPLETE_RESPONSE_CALLBACK_INVOKED);
	}

	public TraceCheckResult checkCompleteResponseCallbacksInvoked(
			String aProvider) {
		checkStreamingCallbackInvoked(
				aProvider,
				COMPLETE_RESPONSE_CALLBACK_INVOKED,
				TraceFilesProcessorError
						.MISSING_COMPLETE_RESPONSE_CALLBACK_INVOKED);
		return checkerResultForProvider(
				COMPLETE_RESPONSE_CALLBACK_INVOKED, aProvider);
	}

	public TraceCheckResult checkStreamingCallbacksInvoked(
			String aProvider) {
		checkStreamingCallbackInvoked(
				aProvider,
				PARTIAL_RESPONSE_CALLBACK_INVOKED,
				TraceFilesProcessorError
						.MISSING_PARTIAL_RESPONSE_CALLBACK_INVOKED);
		checkStreamingCallbackInvoked(
				aProvider,
				COMPLETE_RESPONSE_CALLBACK_INVOKED,
				TraceFilesProcessorError
						.MISSING_COMPLETE_RESPONSE_CALLBACK_INVOKED);
		return traceCheckResult(promptBasedCheckReportLines());
	}

	private void checkStreamingCallbackInvoked(
			String aProvider,
			String anEventName,
			TraceFilesProcessorError anError) {
		List<TraceRecord> callbackRecords =
				records(aProvider, StreamingMode.STREAMING, anEventName);
		boolean found = !callbackRecords.isEmpty();
		if (found && PARTIAL_RESPONSE_CALLBACK_INVOKED.equals(anEventName)) {
			found = partialCallbackArgumentsMatchTranslatedChunks(
					aProvider,
					callbackRecords);
			if (!found) {
				recordError(
						TraceFilesProcessorError
								.INVALID_PARTIAL_RESPONSE_CALLBACK_ARGUMENT);
			}
		}
		if (found && COMPLETE_RESPONSE_CALLBACK_INVOKED.equals(anEventName)) {
			found = completeCallbackArgumentsMatchMergedResponses(
					aProvider,
					callbackRecords);
			if (!found) {
				recordError(
						TraceFilesProcessorError
								.INVALID_COMPLETE_RESPONSE_CALLBACK_ARGUMENT);
			}
		}
		streamingCallbackInvocationResults.add(found);
		if (!found) {
			recordError(anError);
		}
		recordCheckerMessage(
				streamingCallbackInvokedMessage(
						aProvider,
						anEventName,
						callbackRecords,
						found));
	}

	private boolean partialCallbackArgumentsMatchTranslatedChunks(
			String aProvider,
			List<TraceRecord> someCallbackRecords) {
		List<TraceRecord> accumulatedChunkRecords = records(
				aProvider,
				StreamingMode.STREAMING,
				"streaming_chunk_accumulated");
		for (TraceRecord callbackRecord : someCallbackRecords) {
			TraceMessage callbackArgument =
					(TraceMessage) callbackRecord.getExtractedData()
							.get("callbackArgumentStructure");
			TraceMessage translatedChunk = nearestPrecedingMessage(
					callbackRecord,
					accumulatedChunkRecords,
					"streamingChunkMessageStructure");
			if (callbackArgument == null
					|| translatedChunk == null
					|| !messagesHaveSameRoleAndText(
							callbackArgument,
							translatedChunk)) {
				return false;
			}
		}
		return true;
	}

	private boolean completeCallbackArgumentsMatchMergedResponses(
			String aProvider,
			List<TraceRecord> someCallbackRecords) {
		List<TraceRecord> mergedRecords = records(
				aProvider,
				StreamingMode.STREAMING,
				"streaming_chunks_merged");
		for (TraceRecord callbackRecord : someCallbackRecords) {
			TraceMessage callbackArgument =
					(TraceMessage) callbackRecord
							.getExtractedData()
							.get("callbackArgumentStructure");
			TraceMessage mergedMessage = nearestPrecedingMergedMessage(
					callbackRecord,
					mergedRecords);
			if (callbackArgument == null
					|| mergedMessage == null
					|| !messagesHaveSameRoleAndText(
							callbackArgument,
							mergedMessage)) {
				return false;
			}
		}
		return true;
	}

	private TraceMessage nearestPrecedingMergedMessage(
			TraceRecord aCallbackRecord,
			List<TraceRecord> someMergedRecords) {
		return nearestPrecedingMessage(
				aCallbackRecord,
				someMergedRecords,
				"mergedMessageStructure");
	}

	private TraceMessage nearestPrecedingMessage(
			TraceRecord aCallbackRecord,
			List<TraceRecord> someCandidateRecords,
			String anExtractedDataKey) {
		TraceMessage result = null;
		int callbackSequence = aCallbackRecord.getId().sequenceNumber();
		for (TraceRecord candidateRecord : someCandidateRecords) {
			if (candidateRecord.getId().sequenceNumber() >= callbackSequence) {
				break;
			}
			TraceMessage candidate =
					(TraceMessage) candidateRecord.getExtractedData()
							.get(anExtractedDataKey);
			if (candidate != null) {
				result = candidate;
			}
		}
		return result;
	}

	private String streamingCallbackInvokedMessage(
			String aProvider,
			String anEventName,
			List<TraceRecord> aCallbackRecords,
			boolean isFound) {
		return anEventName
				+ " provider="
				+ aProvider
				+ " mode="
				+ StreamingMode.STREAMING
				+ " count="
				+ aCallbackRecords.size()
				+ " traceLine="
				+ traceLineLocation(
						aCallbackRecords.isEmpty()
								? null
								: aCallbackRecords.get(0))
				+ " match="
				+ isFound;
	}

	public TraceCheckResult checkProviderIndependentNonStreamingDataMatchesAcrossProviders() {
		if (providers().size() < 2) {
			return traceCheckResult(promptBasedCheckReportLines());
		}
		for (String eventName :
				List.of("request_sent", "response_translated")) {
			checkProviderIndependentNonStreamingDataMatchesAcrossProviders(
					eventName);
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	public TraceCheckResult checkProviderIndependentNonStreamingRequestsMatchAcrossProviders() {
		checkProviderIndependentNonStreamingDataMatchesAcrossProviders(
				"request_sent");
		return checkerResult(
				PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS,
				"event",
				"request_sent");
	}

	public TraceCheckResult checkProviderIndependentNonStreamingResponsesMatchAcrossProviders() {
		checkProviderIndependentNonStreamingDataMatchesAcrossProviders(
				"response_translated");
		return checkerResult(
				PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS,
				"event",
				"response_translated");
	}

	public TraceCheckResult checkProviderIndependentNonStreamingDataMatchesAcrossProviders(
			String anEventName) {
		List<String> providers = new ArrayList<>(providers());
		String baselineProvider = providers.get(0);
		List<TraceRecord> baselineRecords =
				records(baselineProvider, StreamingMode.NON_STREAMING, anEventName);
		for (int i = 1; i < providers.size(); i++) {
			String comparedProvider = providers.get(i);
			List<TraceRecord> comparedRecords =
					records(
							comparedProvider,
							StreamingMode.NON_STREAMING,
							anEventName);
			int comparisons =
					Math.min(baselineRecords.size(), comparedRecords.size());
			for (int occurrence = 0; occurrence < comparisons; occurrence++) {
				TraceRecord baselineRecord =
						baselineRecords.get(occurrence);
				TraceRecord comparedRecord =
						comparedRecords.get(occurrence);
				Object baselineData =
						providerIndependentComparableData(baselineRecord);
				Object comparedData =
						providerIndependentComparableData(comparedRecord);
				boolean genericStructureMatches =
						baselineData.equals(comparedData);
				Object baselineInputs = "request_sent".equals(anEventName)
						? providerDependentRequestInputs(baselineRecord)
						: List.of();
				Object comparedInputs = "request_sent".equals(anEventName)
						? providerDependentRequestInputs(comparedRecord)
						: List.of();
				boolean providerInputsMatch =
						baselineInputs.equals(comparedInputs);
				boolean result = genericStructureMatches
						&& providerInputsMatch;
				providerIndependentNonStreamingMatchResults.add(result);
				baselineRecord.putExtractedData(
						"providerIndependentDataMatches"
								+ comparedProvider,
						result);
				comparedRecord.putExtractedData(
						"providerIndependentDataMatches"
								+ baselineProvider,
						result);
				recordCheckerMessage(
						providerIndependentNonStreamingMatchMessage(
								anEventName,
								occurrence,
								baselineProvider,
								baselineRecord,
								comparedProvider,
								comparedRecord,
								result,
								crossProviderDataSummary(
										baselineData,
										comparedData,
										baselineInputs,
										comparedInputs)));
			}
			if (baselineRecords.size() != comparedRecords.size()) {
				providerIndependentNonStreamingMatchResults.add(false);
				recordCheckerMessage(
							providerIndependentNonStreamingCountMismatchMessage(
								anEventName,
								baselineProvider,
								baselineRecords.size(),
								comparedProvider,
								comparedRecords.size()));
			}
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	private String canonicalMapDifference(
			Map<String, String> providerIndependentValues,
			Map<String, String> providerDependentValues) {
		for (Map.Entry<String, String> entry :
				providerDependentValues.entrySet()) {
			String independentValue =
					providerIndependentValues.get(entry.getKey());
			if (independentValue == null) {
				return "missing " + entry.getKey();
			}
			if (!normalizedCanonicalValue(independentValue).equals(
					normalizedCanonicalValue(entry.getValue()))) {
				return entry.getKey()
						+ " providerIndependent="
						+ independentValue
						+ " providerDependent="
						+ entry.getValue();
			}
		}
		return "";
	}

	private String normalizedCanonicalValue(String aValue) {
		return aValue == null ? "" : aValue.trim().toLowerCase();
	}

	private List<String> providerSpecificTypesInArguments(
			TraceRecord aRecord,
			String... someArgumentNames) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		ReflectiveValueCollector collector = new ReflectiveValueCollector();
		for (String argumentName : someArgumentNames) {
			String dump = aRecord.getTraceLine()
					.getAuxiliaryData()
					.get(argumentName);
			// Leaf paths provide precise evidence. The complete dump remains
			// necessary for objects that expose types but no stored leaf values.
			result.addAll(
					providerSpecificTypeRegistry
							.providerSpecificTypesIn(collector.collect(dump)));
			result.addAll(
					providerSpecificTypeRegistry
							.providerSpecificTypesIn(dump));
		}
		return new ArrayList<>(result);
	}

	private Object providerIndependentComparableData(
			TraceRecord aTraceRecord) {
		String eventName = aTraceRecord.getTraceLine().getEventName();
		if ("request_sent".equals(eventName)) {
			return providerIndependentRequestStructure(aTraceRecord);
		}
		if ("response_translated".equals(eventName)) {
			return providerIndependentResponseStructure(aTraceRecord);
		}
		return "";
	}

	private List<String> providerIndependentRequestStructure(
			TraceRecord aTraceRecord) {
		CanonicalTraceRequest request =
				(CanonicalTraceRequest) aTraceRecord.getExtractedData()
						.get("providerIndependentCanonicalRequest");
		ArrayList<String> result = new ArrayList<>();
		result.add("modelPresent=" + !request.model().isBlank());
		addCanonicalMapShape(result, "parameter", request.parameters());
		for (TraceMessage message : request.contextWindow()) {
			result.add("message=" + canonicalMessageTypeStructure(message));
		}
		return result;
	}

	private List<String> providerIndependentResponseStructure(
			TraceRecord aTraceRecord) {
		CanonicalTraceResponse response =
				(CanonicalTraceResponse) aTraceRecord.getExtractedData()
						.get("providerIndependentCanonicalResponse");
		ArrayList<String> result = new ArrayList<>();
		for (TraceMessage message : response.messages()) {
			result.add("message=" + canonicalMessageTypeStructure(message));
		}
		return result;
	}

	private List<String> providerDependentRequestInputs(
			TraceRecord aTraceRecord) {
		CanonicalTraceRequest request =
				(CanonicalTraceRequest) aTraceRecord.getExtractedData()
						.get("providerDependentCanonicalRequest");
		if (request == null) {
			return List.of();
		}
		ArrayList<String> result = new ArrayList<>();
		StringBuilder systemText = new StringBuilder();
		List<String> systemPieces = new ArrayList<>();
		int userIndex = 0;
		for (TraceMessage message : request.contextWindow()) {
			String role = standardRole(message.role());
			if ("SYSTEM".equals(role)) {
				for (TracePart part : message.parts()) {
					if ("text".equals(part.kind())) {
						systemText.append(normalizedText(part.value()));
						systemPieces.add(part.value());
					}
				}
				continue;
			}
			if (!"USER".equals(role)) {
				continue;
			}
			StringBuilder user = new StringBuilder(
					"user[" + userIndex++ + "]");
			for (TracePart part : message.parts()) {
				user.append('|').append(part.kind()).append('=');
				user.append("text".equals(part.kind())
						? canonicalInputText(part.value())
						: part.value());
			}
			result.add(user.toString());
		}
		result.add(0, "system=" + (systemPieces.stream().anyMatch(p -> TraceTextSummary.fromToken(p) != null)
				? TraceTextSummary.concatenateEdges(systemPieces) : systemText));
		return List.copyOf(result);
	}

	private String canonicalInputText(String value) {
		if (TraceTextSummary.matches(value, BridgeDemoTraceInputs.SUMMARY_PROMPT)
				|| TraceTextSummary.matches(value, BridgeDemoTraceInputs.LEGACY_SUMMARY_PROMPT)
				|| TraceTextSummary.matches(value, BridgeDemoTraceInputs.LEGACY_SUMMARY_PROMPT.strip()))
			return TraceTextSummary.from(BridgeDemoTraceInputs.SUMMARY_PROMPT).token();
		return normalizedText(value);
	}

	private void addCanonicalMapShape(
			List<String> aResult,
			String aPrefix,
			Map<String, String> someValues) {
		for (String key : someValues.keySet().stream().sorted().toList()) {
			aResult.add(
					aPrefix
							+ "="
							+ key
							+ ":"
							+ canonicalValueKind(someValues.get(key)));
		}
	}

	private String canonicalValueKind(String aValue) {
		if (aValue == null) {
			return "null";
		}
		String value = aValue.trim();
		if (value.matches("[-+]?\\d+")) {
			return "integer";
		}
		if (value.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)")) {
			return "decimal";
		}
		if ("true".equalsIgnoreCase(value)
				|| "false".equalsIgnoreCase(value)) {
			return "boolean";
		}
		if (value.startsWith("[") && value.endsWith("]")) {
			return "list";
		}
		return "string";
	}

	private void addMessageObjectSignatures(
			List<String> aResult,
			String aDump) {
		for (ReflectedObject reflectedObject : messageObjectsIn(aDump)) {
			aResult.add(
					"message=" + objectSignature(reflectedObject));
		}
	}

	private String canonicalMessageTypeStructure(TraceMessage aMessage) {
		if (aMessage == null) {
			return "";
		}
		StringBuilder builder =
				new StringBuilder(standardRole(aMessage.role()));
		for (TracePart part : aMessage.parts()) {
			builder.append('|')
					.append(part.kind());
		}
		return builder.toString();
	}

	private String rootObjectSignature(String aDump) {
		if (aDump == null) {
			return "";
		}
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile(
								"^(?:[^:]+: )?"
										+ "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)"
										+ "\\s+interfaces=\\[([^\\]]*)\\]")
						.matcher(aDump);
		if (!matcher.find()) {
			return "";
		}
		return objectSignature(
				new ReflectedObject(
						matcher.group(1).trim(),
						interfaceNames(matcher.group(2))));
	}

	private String objectSignature(ReflectedObject aReflectedObject) {
		return aReflectedObject.className()
				+ " interfaces="
				+ aReflectedObject.interfaceNames();
	}

	private String crossProviderDataSummary(
			Object aBaselineData,
			Object aComparedData,
			Object someBaselineInputs,
			Object someComparedInputs) {
		String genericDifference = providerIndependentDataSummary(
				aBaselineData,
				aComparedData);
		if (!aBaselineData.equals(aComparedData)) {
			return "generic structure: " + genericDifference;
		}
		if (!someBaselineInputs.equals(someComparedInputs)) {
			return "provider request inputs: "
					+ providerIndependentDataSummary(
							someBaselineInputs,
							someComparedInputs);
		}
		return "generic structure and provider request inputs match";
	}

	private String providerIndependentDataSummary(
			Object aBaselineData,
			Object aComparedData) {
		if (aBaselineData instanceof List<?>
				&& aComparedData instanceof List<?>) {
			List<?> baseline = (List<?>) aBaselineData;
			List<?> compared = (List<?>) aComparedData;
			int commonSize = Math.min(baseline.size(), compared.size());
			for (int index = 0; index < commonSize; index++) {
				if (!baseline.get(index).equals(compared.get(index))) {
					return "item "
							+ index
							+ " differs: baseline="
							+ baseline.get(index)
							+ " compared="
							+ compared.get(index);
				}
			}
			if (baseline.size() != compared.size()) {
				return "item count differs: baseline="
						+ baseline.size()
						+ " compared="
						+ compared.size()
						+ " firstUnmatched="
						+ (baseline.size() > commonSize
								? baseline.get(commonSize)
								: compared.get(commonSize));
			}
			return "structures match with " + baseline.size() + " items";
		}
		return "baselineCharacters="
				+ String.valueOf(aBaselineData).length()
				+ " comparedCharacters="
				+ String.valueOf(aComparedData).length();
	}

	private String providerIndependentNonStreamingMatchMessage(
			String anEventName,
			int anOccurrence,
			String aBaselineProvider,
			TraceRecord aBaselineRecord,
			String aComparedProvider,
			TraceRecord aComparedRecord,
			boolean isMatch,
			String aDetail) {
		return PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS
				+ " event="
				+ anEventName
				+ " occurrence="
				+ anOccurrence
				+ " baselineProvider="
				+ aBaselineProvider
				+ " baselineTraceLine="
				+ traceLineLocation(aBaselineRecord)
				+ " comparedProvider="
				+ aComparedProvider
				+ " comparedTraceLine="
				+ traceLineLocation(aComparedRecord)
				+ " match="
				+ isMatch
				+ " detail=\""
				+ aDetail.replace("\"", "'")
				+ "\"";
	}

	private String providerIndependentNonStreamingCountMismatchMessage(
			String anEventName,
			String aBaselineProvider,
			int aBaselineCount,
			String aComparedProvider,
			int aComparedCount) {
		return PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS
				+ " event="
				+ anEventName
				+ " baselineProvider="
				+ aBaselineProvider
				+ " baselineCount="
				+ aBaselineCount
				+ " comparedProvider="
				+ aComparedProvider
				+ " comparedCount="
				+ aComparedCount
				+ " match=false detail=\"event occurrence count differs\"";
	}

	public TraceCheckResult checkStreamingChunksReflectedInNextContextWindow(
			String aProvider) {
		List<StreamingChunkComposition> compositions =
				streamingChunkCompositions(aProvider);
		List<ContextWindowSnapshot> snapshots =
				requestContextWindowSnapshots(
						aProvider,
						StreamingMode.STREAMING);
		for (int i = 0; i < compositions.size(); i++) {
			StreamingChunkComposition composition = compositions.get(i);
			ContextWindowSnapshot matchingSnapshot =
					nextSnapshotWithPreviousAssistantMessage(
							snapshots,
							composition.mergedRecord());
			if (matchingSnapshot == null) {
				recordCheckerMessage(
						i == compositions.size() - 1
								? TraceProcessorMessageLevel.INFO
								: TraceProcessorMessageLevel.WARNING,
						streamingChunksContextNotCheckedMessage(
								aProvider,
								composition,
								"no following context window has a previous assistant message"));
				continue;
			}
			TraceMessage previousAssistant =
					previousAssistantMessage(matchingSnapshot.messages());
			boolean composedMatches =
					messagesHaveSameRoleAndText(
							composition.composedMessage(),
							previousAssistant);
			boolean mergedMatches =
					messagesHaveSameRoleAndText(
							composition.mergedMessage(),
							previousAssistant);
			boolean result = composedMatches && mergedMatches;
			streamingChunkContextWindowMatchResults.add(result);
			composition.mergedRecord().putExtractedData(
					"streamingChunksReflectedInNextContextWindow",
					result);
			matchingSnapshot.traceRecord().putExtractedData(
					"secondLastMessageReflectsPreviousStreamingChunks",
					result);
			recordCheckerMessage(
					streamingChunksContextMessage(
							aProvider,
							composition,
							matchingSnapshot,
							previousAssistant,
							result,
							"composedChunksMatchSecondLast="
									+ composedMatches
									+ " mergedMessageMatchesSecondLast="
									+ mergedMatches));
		}
		return checkerResultForProvider(
				STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW,
				aProvider);
	}

	private List<ContextWindowSnapshot> requestContextWindowSnapshots(
			String aProvider,
			StreamingMode aMode) {
		ArrayList<ContextWindowSnapshot> result = new ArrayList<>();
		for (TraceRecord record : requestRecords(aProvider, aMode)) {
			CanonicalTraceRequest request =
					(CanonicalTraceRequest) record.getExtractedData().get(
							"providerDependentCanonicalRequest");
			List<TraceMessage> messages = request == null
					? List.of()
					: request.contextWindow();
			if (messages != null) {
				result.add(
						new ContextWindowSnapshot(
								record,
								List.copyOf(messages)));
			}
		}
		return result;
	}

	private List<StreamingChunkComposition> streamingChunkCompositions(
			String aProvider) {
		ArrayList<StreamingChunkComposition> result = new ArrayList<>();
		ArrayList<String> chunkTexts = new ArrayList<>();
		TraceRecord firstChunkRecord = null;
		TraceRecord lastChunkRecord = null;
		for (TraceRecord record :
				records(aProvider, StreamingMode.STREAMING)) {
			String eventName = record.getTraceLine().getEventName();
			if ("streaming_chunk_accumulated".equals(eventName)) {
				TraceMessage chunk =
						(TraceMessage) record.getExtractedData()
								.get("streamingChunkMessageStructure");
				// Whitespace-only chunks are part of the answer too.
				if (chunk == null
						|| !"ASSISTANT".equals(standardRole(chunk.role()))
						|| textOf(chunk).isEmpty()) {
					continue;
				}
				if (firstChunkRecord == null) {
					firstChunkRecord = record;
				}
				lastChunkRecord = record;
				chunkTexts.add(textOf(chunk));
			} else if ("streaming_chunks_merged".equals(eventName)) {
				TraceTextSummary independentHash = TraceTextSummary.parse(record.getTraceLine()
						.getAuxiliaryData().get("expectedMergedTextSummary"));
				TraceMessage merged =
						(TraceMessage) record.getExtractedData()
								.get("mergedMessageStructure");
				if (isAssistantTextMessage(merged)
						&& !chunkTexts.isEmpty()) {
					result.add(
							new StreamingChunkComposition(
									firstChunkRecord,
									lastChunkRecord,
									record,
									new TraceMessage(
											"ASSISTANT",
											List.of(
													new TracePart(
															"text",
															independentHash != null ? independentHash.token() : String.join(
																	"",
																	chunkTexts)))),
									merged));
				}
				chunkTexts.clear();
				firstChunkRecord = null;
				lastChunkRecord = null;
			}
		}
		return result;
	}

	private List<ContextWindowSnapshot> contextWindowSnapshots(
			String aProvider,
			StreamingMode aMode) {
		ArrayList<ContextWindowSnapshot> result = new ArrayList<>();
		ArrayList<TraceMessage> currentMessages = new ArrayList<>();
		for (TraceRecord record :
				contextWindowMessageAddedRecords(aProvider, aMode)) {
			Integer index = contextWindowIndex(record);
			TraceMessage message =
					(TraceMessage) record.getExtractedData()
							.get("contextWindowAddedMessageStructure");
			if (index == null || message == null) {
				continue;
			}
			if (index == 0 && !currentMessages.isEmpty()) {
				currentMessages.clear();
			}
			while (currentMessages.size() > index) {
				currentMessages.remove(currentMessages.size() - 1);
			}
			if (currentMessages.size() == index) {
				currentMessages.add(message);
			} else {
				currentMessages.set(index, message);
			}
			result.add(
					new ContextWindowSnapshot(
							record,
							List.copyOf(currentMessages)));
		}
		return result;
	}

	private ContextWindowSnapshot nextSnapshotWithPreviousAssistantMessage(
			List<ContextWindowSnapshot> aSnapshots,
			TraceRecord aMergedRecord) {
		for (ContextWindowSnapshot snapshot : aSnapshots) {
			if (snapshot.traceRecord().getId().sequenceNumber()
					<= aMergedRecord.getId().sequenceNumber()) {
				continue;
			}
			if (previousAssistantMessage(snapshot.messages()) != null) {
				return snapshot;
			}
		}
		return null;
	}

	private TraceMessage previousAssistantMessage(
			List<TraceMessage> aMessages) {
		if (aMessages == null || aMessages.size() < 2) {
			return null;
		}
		for (int i = aMessages.size() - 2; i >= 0; i--) {
			TraceMessage message = aMessages.get(i);
			if (isAssistantTextMessage(message)) {
				return message;
			}
		}
		return null;
	}

	private boolean messagesHaveSameRoleAndText(
			TraceMessage aFirst,
			TraceMessage aSecond) {
		if (aFirst == null || aSecond == null) {
			return false;
		}
		return standardRole(aFirst.role()).equals(standardRole(aSecond.role()))
				&& textMatches(textOf(aFirst), textOf(aSecond));
	}

	private boolean isAssistantTextMessage(TraceMessage aMessage) {
		return aMessage != null
				&& "ASSISTANT".equals(standardRole(aMessage.role()))
				&& !isBlank(textOf(aMessage));
	}

	private String textOf(TraceMessage aMessage) {
		if (aMessage == null) {
			return "";
		}
		if (!aMessage.textAggregate().isEmpty()) return aMessage.textAggregate();
		if ("SYSTEM".equals(standardRole(aMessage.role())) && aMessage.parts().stream()
				.anyMatch(p -> "text".equals(p.kind()) && TraceTextSummary.fromToken(p.value()) != null)) {
			return TraceTextSummary.concatenateEdges(aMessage.parts().stream()
					.filter(p -> "text".equals(p.kind())).map(TracePart::value).toList());
		}
		StringBuilder builder = new StringBuilder();
		for (TracePart part : aMessage.parts()) {
			if ("text".equals(part.kind())) {
				builder.append(part.value());
			}
		}
		return builder.toString();
	}

	private String streamingChunksContextMessage(
			String aProvider,
			StreamingChunkComposition aComposition,
			ContextWindowSnapshot aSnapshot,
			TraceMessage aSecondLast,
			boolean isMatch,
			String aDetail) {
		return STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW
				+ " provider="
				+ aProvider
				+ " mode="
				+ StreamingMode.STREAMING
				+ " firstChunkTraceLine="
				+ traceLineLocation(aComposition.firstChunkRecord())
				+ " lastChunkTraceLine="
				+ traceLineLocation(aComposition.lastChunkRecord())
				+ " mergedTraceLine="
				+ traceLineLocation(aComposition.mergedRecord())
				+ " contextTraceLine="
				+ traceLineLocation(
						aSnapshot == null ? null : aSnapshot.traceRecord())
				+ " composedCharacters="
				+ normalizedText(textOf(aComposition.composedMessage()))
						.length()
				+ " secondLastCharacters="
				+ normalizedText(textOf(aSecondLast)).length()
				+ " match="
				+ isMatch
				+ " detail=\""
				+ aDetail.replace("\"", "'")
				+ "\"";
	}

	private String streamingChunksContextNotCheckedMessage(
			String aProvider,
			StreamingChunkComposition aComposition,
			String aDetail) {
		return STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW
				+ " provider="
				+ aProvider
				+ " mode="
				+ StreamingMode.STREAMING
				+ " firstChunkTraceLine="
				+ traceLineLocation(aComposition.firstChunkRecord())
				+ " lastChunkTraceLine="
				+ traceLineLocation(aComposition.lastChunkRecord())
				+ " mergedTraceLine="
				+ traceLineLocation(aComposition.mergedRecord())
				+ " checked=false detail=\""
				+ aDetail.replace("\"", "'")
				+ "\"";
	}

	public TraceCheckResult checkExpectedInputs() {
		if (expectedInputs.systemPromptTexts().isEmpty()
				&& expectedInputs.userPromptTexts().isEmpty()
				&& expectedInputs.expectedImageBytes().length == 0) {
			return traceCheckResult(promptBasedCheckReportLines());
		}
		for (String provider : providers()) {
			checkExpectedInputs(provider, StreamingMode.NON_STREAMING);
			checkExpectedInputs(provider, StreamingMode.STREAMING);
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	public TraceCheckResult checkExpectedPrompts() {
		checkExpectedInputs();
		return checkerResult(EXPECTED_PROMPT_IN_CONTEXT_WINDOW);
	}

	public TraceCheckResult checkExpectedPrompts(String aProvider) {
		checkExpectedInputs(aProvider, StreamingMode.NON_STREAMING);
		checkExpectedInputs(aProvider, StreamingMode.STREAMING);
		return checkerResultForProvider(
				EXPECTED_PROMPT_IN_CONTEXT_WINDOW, aProvider);
	}

	public TraceCheckResult checkProviderGenericImagesMatch() {
		for (String provider : providerTraceFiles.keySet()) {
			checkImageMatches(provider, StreamingMode.NON_STREAMING,
					PROVIDER_GENERIC_IMAGE_MATCH);
			checkImageMatches(provider, StreamingMode.STREAMING,
					PROVIDER_GENERIC_IMAGE_MATCH);
		}
		return checkerResult(PROVIDER_GENERIC_IMAGE_MATCH);
	}

	public TraceCheckResult checkProviderGenericImagesMatch(String aProvider) {
		checkImageMatches(aProvider, StreamingMode.NON_STREAMING,
				PROVIDER_GENERIC_IMAGE_MATCH);
		checkImageMatches(aProvider, StreamingMode.STREAMING,
				PROVIDER_GENERIC_IMAGE_MATCH);
		return checkerResultForProvider(PROVIDER_GENERIC_IMAGE_MATCH, aProvider);
	}

	public TraceCheckResult checkExpectedGenericImagesMatch() {
		for (String provider : providerTraceFiles.keySet()) {
			checkImageMatches(provider, StreamingMode.NON_STREAMING,
					EXPECTED_GENERIC_IMAGE_MATCH);
			checkImageMatches(provider, StreamingMode.STREAMING,
					EXPECTED_GENERIC_IMAGE_MATCH);
		}
		return checkerResult(EXPECTED_GENERIC_IMAGE_MATCH);
	}

	public TraceCheckResult checkExpectedGenericImagesMatch(String aProvider) {
		checkImageMatches(aProvider, StreamingMode.NON_STREAMING,
				EXPECTED_GENERIC_IMAGE_MATCH);
		checkImageMatches(aProvider, StreamingMode.STREAMING,
				EXPECTED_GENERIC_IMAGE_MATCH);
		return checkerResultForProvider(EXPECTED_GENERIC_IMAGE_MATCH, aProvider);
	}

	private void checkImageMatches(String aProvider, StreamingMode aMode,
			String aCheckName) {
		ImageComparison comparison = findImageComparison(
				requestRecords(aProvider, aMode), expectedInputs.expectedImageBytes());
		boolean matches = PROVIDER_GENERIC_IMAGE_MATCH.equals(aCheckName)
				? comparison.generic() != null
						&& comparison.generic().equals(comparison.provider())
				: comparison.expected().equals(comparison.generic());
		recordCheckerMessage(imageComparisonMessage(
				aCheckName, aProvider, aMode, comparison, matches));
	}

	public TraceCheckResult checkExpectedInputs(
			String aProvider,
			StreamingMode aMode) {
		List<TraceRecord> requestRecords =
				requestRecords(aProvider, aMode);
		for (int i = 0; i < expectedInputs.systemPromptTexts().size(); i++) {
			String expectedText = expectedInputs.systemPromptTexts().get(i);
			TraceRecord matchingRecord =
					findRequestWithText(requestRecords, "SYSTEM", expectedText);
			boolean found = matchingRecord != null;
			expectedInputMatchResults.add(found);
			recordCheckerMessage(
					expectedTextMessage(
							aProvider,
							aMode,
							i,
							"SYSTEM",
							expectedText,
							matchingRecord,
							found));
		}
		for (int i = 0; i < expectedInputs.userPromptTexts().size(); i++) {
			String expectedText = expectedInputs.userPromptTexts().get(i);
			TraceRecord matchingRecord =
					findRequestWithText(requestRecords, "USER", expectedText);
			boolean found = matchingRecord != null;
			expectedInputMatchResults.add(found);
			recordCheckerMessage(
					expectedTextMessage(
							aProvider,
							aMode,
							i,
							"USER",
							expectedText,
							matchingRecord,
							found));
		}
		if (expectedInputs.expectedImageBytes().length > 0) {
			ImageComparison comparison = findImageComparison(
					requestRecords, expectedInputs.expectedImageBytes());
			boolean providerGenericMatches = comparison.generic() != null
					&& comparison.generic().equals(comparison.provider());
			boolean expectedGenericMatches =
					comparison.expected().equals(comparison.generic());
			expectedInputMatchResults.add(providerGenericMatches);
			expectedInputMatchResults.add(expectedGenericMatches);
			recordCheckerMessage(imageComparisonMessage(
					PROVIDER_GENERIC_IMAGE_MATCH, aProvider, aMode,
					comparison, providerGenericMatches));
			recordCheckerMessage(imageComparisonMessage(
					EXPECTED_GENERIC_IMAGE_MATCH, aProvider, aMode,
					comparison, expectedGenericMatches));
		}
		return traceCheckResult(promptBasedCheckReportLines());
	}

	private TraceRecord findRequestWithText(
			List<TraceRecord> aRequestRecords,
			String aRole,
			String anExpectedText) {
		for (TraceRecord record : aRequestRecords) {
			for (TraceMessage message :
					providerDependentRequestMessages(record)) {
				if (!standardRole(aRole).equals(standardRole(message.role()))) {
					continue;
				}
				for (TracePart part : message.parts()) {
					if ("text".equals(part.kind())
							&& (promptTextMatches(
									part.value(),
									anExpectedText) || ("SYSTEM".equals(standardRole(aRole))
									&& knownSystemSequenceContains(part.value(), anExpectedText)))) {
						return record;
					}
				}
			}
		}
		return null;
	}

	private boolean promptTextMatches(
			String anObservedText,
			String anExpectedText) {
		if (BridgeDemoTraceInputs.SUMMARY_PROMPT.equals(anExpectedText)) {
			for (String alternative : List.of(BridgeDemoTraceInputs.LEGACY_SUMMARY_PROMPT,
					BridgeDemoTraceInputs.LEGACY_SUMMARY_PROMPT.strip())) {
				if (TraceTextSummary.matches(anObservedText, alternative)) return true;
			}
		}
		if (TraceTextSummary.fromToken(anObservedText) != null || TraceTextSummary.fromToken(anExpectedText) != null)
			return TraceTextSummary.matches(anObservedText, anExpectedText);
		String observed = normalizedText(anObservedText);
		String expected = normalizedText(anExpectedText);
		return textMatches(observed, expected)
				|| observed.contains(expected)
				|| expected.contains(observed);
	}

	private ImageComparison findImageComparison(
			List<TraceRecord> aRequestRecords,
			byte[] someExpectedBytes) {
		ImageByteSummary expected = ImageByteSummary.from(someExpectedBytes);
		for (TraceRecord record : aRequestRecords) {
			ImageByteSummary generic = ImageByteSummary.parseFirstNonempty(
					record.getTraceLine().getAuxiliaryData()
							.get("providerIndependentContextWindow"));
			ImageByteSummary provider = ImageByteSummary.parseFirstNonempty(
					record.getTraceLine().getAuxiliaryData()
							.get("providerDependentContextWindow"));
			if (generic != null || provider != null) {
				return new ImageComparison(
						expected.equals(generic) && expected.equals(provider),
						record,
						expected,
						generic,
						provider);
			}
		}
		return new ImageComparison(false, null, expected, null, null);
	}

	@SuppressWarnings("unchecked")
	private List<TraceMessage> providerDependentRequestMessages(
			TraceRecord aRecord) {
		Object value =
				aRecord.getExtractedData()
						.get("providerDependentRequestStructure");
		if (value instanceof List<?>) {
			return (List<TraceMessage>) value;
		}
		return List.of();
	}

	private TraceRecord findAddedMessageWithText(
			List<TraceRecord> aAddedRecords,
			String anExpectedText) {
		for (TraceRecord record : aAddedRecords) {
			for (TracePart part : addedMessageParts(record)) {
				if ("text".equals(part.kind())
						&& textMatches(part.value(), anExpectedText)) {
					return record;
				}
			}
		}
		return null;
	}

	private List<TracePart> addedMessageParts(TraceRecord aRecord) {
		String messageDump =
				aRecord.getTraceLine()
						.getAuxiliaryData()
						.get("contextWindowMessage");
		List<TraceMessage> messages =
				reflectedMessageStructures(messageDump);
		if (messages.isEmpty()) {
			return List.of();
		}
		return messages.get(0).parts();
	}

	private String expectedTextMessage(
			String aProvider,
			StreamingMode aMode,
			int anExpectedIndex,
			String anExpectedRole,
			String anExpectedText,
			TraceRecord aMatchingRecord,
			boolean isFound) {
		return EXPECTED_PROMPT_IN_CONTEXT_WINDOW
				+ " provider="
				+ aProvider
				+ " mode="
				+ aMode
				+ " expectedPromptId="
				+ expectedPromptId(anExpectedRole, anExpectedIndex)
				+ " expectedPromptIndex="
				+ anExpectedIndex
				+ " expectedRole="
				+ anExpectedRole
				+ " traceLine="
				+ traceLineLocation(aMatchingRecord)
				+ " match="
				+ isFound
				+ " promptPreview=\""
				+ preview(anExpectedText)
				+ "\"";
	}

	private String imageComparisonMessage(
			String aCheckName,
			String aProvider,
			StreamingMode aMode,
			ImageComparison aComparison,
			boolean matches) {
		return aCheckName
				+ " provider="
				+ aProvider
				+ " mode="
				+ aMode
				+ " traceLine="
				+ traceLineLocation(aComparison.traceRecord())
				+ " expected=" + aComparison.expected()
				+ " generic=" + aComparison.generic()
				+ " provider=" + aComparison.provider()
				+ " match="
				+ matches;
	}

	public TraceCheckResult checkContextWindowContinuity(
			String aProvider,
			StreamingMode aMode) {
		boolean[] observedCompactions =
				new boolean[expectedInputs.compactionPromptTexts().size()];
		List<TraceRecord> requestRecords =
				requestRecords(aProvider, aMode);
		TraceRecord previousRecord = null;
		List<String> previousMessages = null;
		for (TraceRecord record : requestRecords) {
			@SuppressWarnings("unchecked")
			List<String> currentMessages =
					(List<String>) record.getExtractedData()
							.get(
									"providerDependentContextWindowMessages");
			if (currentMessages == null) {
				currentMessages = List.of();
			}
			if (previousMessages != null) {
				Boolean result = contextWindowContinues(
						previousMessages,
						currentMessages);
				contextWindowContinuityResults.add(result);
				recordContextWindowContinuityMessage(
						aProvider,
						aMode,
						previousRecord,
						record,
						previousMessages,
						currentMessages,
						result,
						observedCompactions);
				record.putExtractedData(
						"previousContextWindowLastInCurrentContextWindow",
						result);
			}
			previousRecord = record;
			previousMessages = currentMessages;
		}
		recordMissingExpectedCompactions(
				aProvider,
				aMode,
				observedCompactions);
		return traceCheckResult(promptBasedCheckReportLines());
	}

	private void checkAddedContextWindowContinuity(
			String aProvider,
			StreamingMode aMode,
			List<TraceRecord> aAddedRecords) {
		ArrayList<String> currentMessages = new ArrayList<>();
		boolean[] observedCompactions =
				new boolean[expectedInputs.compactionPromptTexts().size()];
		List<String> previousMessages = null;
		TraceRecord previousRecord = null;
		for (TraceRecord record : aAddedRecords) {
			Integer index = contextWindowIndex(record);
			String message =
					(String) record.getExtractedData()
							.get("contextWindowAddedMessage");
			if (index == null || isBlank(message)) {
				continue;
			}
			if (index == 0 && !currentMessages.isEmpty()) {
				currentMessages.clear();
			}
			while (currentMessages.size() > index) {
				currentMessages.remove(currentMessages.size() - 1);
			}
			if (currentMessages.size() == index) {
				currentMessages.add(message);
			} else {
				currentMessages.set(index, message);
			}
			List<String> snapshot = List.copyOf(currentMessages);
			if (previousMessages != null
					&& (snapshot.size() >= 2 || index == 0)) {
				Boolean result =
						contextWindowContinues(previousMessages, snapshot);
				contextWindowContinuityResults.add(result);
				recordContextWindowContinuityMessage(
						aProvider,
						aMode,
						previousRecord,
						record,
						previousMessages,
						snapshot,
						result,
						observedCompactions);
				record.putExtractedData(
						"previousContextWindowLastInCurrentContextWindow",
						result);
			}
			previousMessages = snapshot;
			previousRecord = record;
		}
		recordMissingExpectedCompactions(
				aProvider,
				aMode,
				observedCompactions);
	}

	private void recordContextWindowContinuityMessage(
			String aProvider,
			StreamingMode aMode,
			TraceRecord aPreviousRecord,
			TraceRecord aCurrentRecord,
			List<String> aPreviousMessages,
			List<String> aCurrentMessages,
			Boolean aContinuityResult,
			boolean[] anObservedCompactions) {
		ExpectedCompaction expectedCompaction =
				expectedCompactionFor(
						aPreviousMessages,
						aCurrentMessages);
		if (expectedCompaction.isExpected()) {
			boolean compactionObserved =
					contextWindowWasReset(
							aPreviousMessages,
							aCurrentMessages);
			if (compactionObserved
					&& expectedCompaction.promptIndex() >= 0
					&& expectedCompaction.promptIndex()
							< anObservedCompactions.length) {
				anObservedCompactions[expectedCompaction.promptIndex()] = true;
			}
			recordCheckerMessage(
					compactionObserved
							? TraceProcessorMessageLevel.INFO
							: TraceProcessorMessageLevel.WARNING,
					expectedContextWindowCompactionMessage(
							aProvider,
							aMode,
							aPreviousRecord,
							aCurrentRecord,
							expectedCompaction,
							compactionObserved));
			return;
		}
		recordRoleContinuityMessages(
				aProvider,
				aMode,
				aPreviousRecord,
				aCurrentRecord,
				aPreviousMessages,
				aCurrentMessages);
	}

	private void recordRoleContinuityMessages(
			String aProvider,
			StreamingMode aMode,
			TraceRecord aPreviousRecord,
			TraceRecord aCurrentRecord,
			List<String> aPreviousMessages,
			List<String> aCurrentMessages) {
		recordRoleContinuityMessage(
				PREVIOUS_SYSTEM_PROMPT_IN_CONTEXT_WINDOW,
				"SYSTEM",
				aProvider,
				aMode,
				aPreviousRecord,
				aCurrentRecord,
				aPreviousMessages,
				aCurrentMessages);
		recordRoleContinuityMessage(
				PREVIOUS_PROMPT_IN_NEW_CONTEXT_WINDOW,
				"USER",
				aProvider,
				aMode,
				aPreviousRecord,
				aCurrentRecord,
				aPreviousMessages,
				aCurrentMessages);
		recordRoleContinuityMessage(
				PREVIOUS_COMPLETED_RESPONSE_IN_CONTEXT_WINDOW,
				"ASSISTANT",
				aProvider,
				aMode,
				aPreviousRecord,
				aCurrentRecord,
				aPreviousMessages,
				aCurrentMessages);
	}

	private void recordRoleContinuityMessage(
			String aCheckerName,
			String aRole,
			String aProvider,
			StreamingMode aMode,
			TraceRecord aPreviousRecord,
			TraceRecord aCurrentRecord,
			List<String> aPreviousMessages,
			List<String> aCurrentMessages) {
		String previousMessage =
				lastMessageForRole(aPreviousMessages, aRole);
		if (previousMessage == null) {
			return;
		}
		boolean retained = aCurrentMessages.contains(previousMessage)
				|| ("SYSTEM".equals(aRole)
						&& systemMessageTextRetained(
								previousMessage,
								aCurrentMessages));
		recordCheckerMessage(
				contextWindowContinuityMessage(
						aCheckerName,
						aProvider,
						aMode,
						aPreviousRecord,
						aCurrentRecord,
						retained));
	}

	private boolean systemMessageTextRetained(
			String aPreviousMessage,
			List<String> someCurrentMessages) {
		List<String> previousTexts = textValues(aPreviousMessage);
		if (previousTexts.isEmpty()) {
			return false;
		}
		StringBuilder currentSystemText = new StringBuilder();
		List<String> currentTexts = new ArrayList<>();
		for (String currentMessage : someCurrentMessages) {
			if (!"SYSTEM".equals(
					standardRole(roleInMessageDump(currentMessage)))) {
				continue;
			}
			for (String text : textValues(currentMessage)) {
				currentTexts.add(text);
				currentSystemText.append(' ').append(text);
			}
		}
		String current = normalizedText(currentSystemText.toString());
		for (String previousText : previousTexts) {
			if (TraceTextSummary.fromToken(previousText) != null
					|| currentTexts.stream().anyMatch(t -> TraceTextSummary.fromToken(t) != null)) {
				if (currentTexts.stream().noneMatch(t -> textMatches(t, previousText)
						|| knownSystemSequenceContains(t, previousText))) return false;
				continue;
			}
			if (!current.contains(normalizedText(previousText))) {
				return false;
			}
		}
		return true;
	}

	/** With compact system text, validate a complete known sequence instead of
	 * searching an unavailable middle substring or concatenating serialized tokens. */
	private boolean knownSystemSequenceContains(String observed, String required) {
		var summary = TraceTextSummary.fromToken(observed);
		if (summary == null || !summary.sha256().isEmpty()) return false;
		List<String> prompts = expectedInputs.systemPromptTexts();
		for (int start = 0; start < prompts.size(); start++) {
			StringBuilder combined = new StringBuilder();
			boolean contains = false;
			for (int end = start; end < prompts.size(); end++) {
				combined.append(prompts.get(end));
				contains |= textMatches(prompts.get(end), required)
						|| textMatches(combined.toString(), required);
				if (contains && TraceTextSummary.matches(observed, combined.toString())) return true;
			}
		}
		return false;
	}

	private List<String> textValues(String aMessageDump) {
		ArrayList<String> result = new ArrayList<>();
		for (TraceMessage message : reflectedMessageStructures(aMessageDump)) {
			for (TracePart part : message.parts()) {
				if ("text".equals(part.kind()) && !isBlank(part.value())) {
					result.add(part.value());
				}
			}
		}
		if (result.isEmpty()) {
			int textStart = aMessageDump == null
					? -1
					: aMessageDump.indexOf("|text=");
			if (textStart >= 0) {
				result.add(aMessageDump.substring(textStart + "|text=".length()));
			}
		}
		return result;
	}

	private String lastMessageForRole(
			List<String> aMessages,
			String aRole) {
		for (int i = aMessages.size() - 1; i >= 0; i--) {
			String message = aMessages.get(i);
			if (aRole.equals(standardRole(roleInMessageDump(message)))) {
				return message;
			}
		}
		return null;
	}

	private record ExpectedCompaction(
			boolean isExpected,
			int promptIndex,
			String promptText) {
	}

	private ExpectedCompaction expectedCompactionFor(
			List<String> aPreviousMessages,
			List<String> aCurrentMessages) {
		for (int i = 0;
				i < expectedInputs.compactionPromptTexts().size();
				i++) {
			String promptText = expectedInputs.compactionPromptTexts().get(i);
			if (firstMessageContainsText(aCurrentMessages, promptText)
					&& !firstMessageContainsText(
							aPreviousMessages,
							promptText)
					&& contextWindowWasReset(
							aPreviousMessages,
							aCurrentMessages)) {
				return new ExpectedCompaction(true, i, promptText);
			}
		}
		return new ExpectedCompaction(false, -1, "");
	}

	private boolean contextWindowWasReset(
			List<String> aPreviousMessages,
			List<String> aCurrentMessages) {
		if (aPreviousMessages == null || aCurrentMessages == null) {
			return false;
		}
		return aCurrentMessages.size() <= aPreviousMessages.size();
	}

	private void recordMissingExpectedCompactions(
			String aProvider,
			StreamingMode aMode,
			boolean[] anObservedCompactions) {
		for (int i = 0;
				i < expectedInputs.compactionPromptTexts().size();
				i++) {
			if (i < anObservedCompactions.length
					&& anObservedCompactions[i]) {
				continue;
			}
			recordCheckerMessage(
					TraceProcessorMessageLevel.WARNING,
					missingExpectedContextWindowCompactionMessage(
							aProvider,
							aMode,
							i,
							expectedInputs.compactionPromptTexts().get(i)));
		}
	}

	private boolean firstMessageContainsText(
			List<String> aMessageDumps,
			String anExpectedText) {
		if (aMessageDumps == null || isBlank(anExpectedText)) {
			return false;
		}
		if (aMessageDumps.isEmpty()) {
			return false;
		}
		String messageDump = aMessageDumps.get(0);
		// Native message dumps embed the compact token rather than the original
		// prompt. Compare that evidence with the known complete compaction prompt.
		var compactTokens = java.util.regex.Pattern.compile("@text:[0-9]+:[A-Za-z0-9_-]*:[A-Za-z0-9_-]*:[0-9a-f]*").matcher(messageDump);
		while (compactTokens.find()) {
			if (TraceTextSummary.fromToken(compactTokens.group()) != null
					&& promptTextMatches(compactTokens.group(), anExpectedText)) return true;
		}
		for (TraceMessage message :
				reflectedMessageStructures(messageDump)) {
			for (TracePart part : message.parts()) {
				if ("text".equals(part.kind())
						&& promptTextMatches(
								part.value(),
								anExpectedText)) {
					return true;
				}
			}
		}
		if (promptTextMatches(messageDump, anExpectedText)) {
			return true;
		}
		return false;
	}

	private String contextWindowContinuityMessage(
			String aCheckerName,
			String aProvider,
			StreamingMode aMode,
			TraceRecord aPreviousRecord,
			TraceRecord aCurrentRecord,
			Boolean aResult) {
		return aCheckerName
				+ " provider="
				+ aProvider
				+ " mode="
				+ aMode
				+ " previousTraceLine="
				+ traceLineLocation(aPreviousRecord)
				+ " currentTraceLine="
				+ traceLineLocation(aCurrentRecord)
				+ " previousLastInCurrentContextWindow="
				+ aResult;
	}

	private String expectedContextWindowCompactionMessage(
			String aProvider,
			StreamingMode aMode,
			TraceRecord aPreviousRecord,
			TraceRecord aCurrentRecord,
			ExpectedCompaction anExpectedCompaction,
			boolean isCompactionObserved) {
		return EXPECTED_CONTEXT_WINDOW_COMPACTION
				+ " provider="
				+ aProvider
				+ " mode="
				+ aMode
				+ " previousTraceLine="
				+ traceLineLocation(aPreviousRecord)
				+ " currentTraceLine="
				+ traceLineLocation(aCurrentRecord)
				+ " expectedCompactionBeforePromptIndex="
				+ anExpectedCompaction.promptIndex()
				+ " expectedPromptId="
				+ expectedPromptId(anExpectedCompaction.promptText())
				+ " compactionObserved="
				+ isCompactionObserved;
	}

	private String missingExpectedContextWindowCompactionMessage(
			String aProvider,
			StreamingMode aMode,
			int anExpectedCompactionIndex,
			String anExpectedCompactionPrompt) {
		return EXPECTED_CONTEXT_WINDOW_COMPACTION
				+ " provider="
				+ aProvider
				+ " mode="
				+ aMode
				+ " expectedCompactionBeforePromptIndex="
				+ anExpectedCompactionIndex
				+ " expectedPromptId="
				+ expectedPromptId(anExpectedCompactionPrompt)
				+ " compactionObserved=false";
	}

	private String expectedPromptId(String aRole, int anExpectedIndex) {
		return standardRole(aRole) + "[" + anExpectedIndex + "]";
	}

	private String expectedPromptId(String anExpectedPromptText) {
		for (int i = 0; i < expectedInputs.systemPromptTexts().size(); i++) {
			if (promptTextMatches(
					expectedInputs.systemPromptTexts().get(i),
					anExpectedPromptText)) {
				return expectedPromptId("SYSTEM", i);
			}
		}
		for (int i = 0; i < expectedInputs.userPromptTexts().size(); i++) {
			if (promptTextMatches(
					expectedInputs.userPromptTexts().get(i),
					anExpectedPromptText)) {
				return expectedPromptId("USER", i);
			}
		}
		for (int i = 0;
				i < expectedInputs.compactionPromptTexts().size();
				i++) {
			if (promptTextMatches(
					expectedInputs.compactionPromptTexts().get(i),
					anExpectedPromptText)) {
				return "COMPACTION[" + i + "]";
			}
		}
		return "UNKNOWN";
	}

	private String continuityCheckerName(List<String> aPreviousMessages) {
		if (aPreviousMessages == null || aPreviousMessages.isEmpty()) {
			return PREVIOUS_MESSAGE_IN_CONTEXT_WINDOW;
		}
		String role =
				standardRole(
						roleInMessageDump(
								aPreviousMessages.get(
										aPreviousMessages.size() - 1)));
		if ("ASSISTANT".equals(role)) {
			return PREVIOUS_COMPLETED_RESPONSE_IN_CONTEXT_WINDOW;
		}
		if ("SYSTEM".equals(role)) {
			return PREVIOUS_SYSTEM_PROMPT_IN_CONTEXT_WINDOW;
		}
		if ("USER".equals(role)) {
			return PREVIOUS_PROMPT_IN_NEW_CONTEXT_WINDOW;
		}
		return PREVIOUS_MESSAGE_IN_CONTEXT_WINDOW;
	}

	private String roleInMessageDump(String aMessageDump) {
		if (aMessageDump == null) {
			return "";
		}
		int canonicalSeparator = aMessageDump.indexOf('|');
		if (canonicalSeparator > 0) {
			String canonicalRole =
					aMessageDump.substring(0, canonicalSeparator).trim();
			if ("SYSTEM".equals(standardRole(canonicalRole))
					|| "USER".equals(standardRole(canonicalRole))
					|| "ASSISTANT".equals(standardRole(canonicalRole))) {
				return canonicalRole;
			}
		}
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile("role=([^,\\)]+)")
						.matcher(aMessageDump);
		return matcher.find() ? matcher.group(1).trim() : "";
	}

	private String requestContextWindowMatchMessage(
			String aProvider,
			StreamingMode aMode,
			TraceRecord aRecord,
			CanonicalValueComparison aValueComparison,
			List<TraceMessage> aProviderDependentMessages,
			ContextWindowComparison aComparison) {
		return PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH
				+ " provider="
				+ aProvider
				+ " mode="
				+ aMode
				+ " traceLine="
				+ traceLineLocation(aRecord)
				+ " missingProviderValues="
				+ aValueComparison.missingValues().size()
				+ " providerDependentMessages="
				+ sizeOf(aProviderDependentMessages)
				+ " match="
				+ aComparison.matches()
				+ (!aComparison.detail().startsWith(
						"provider-independent request contains provider-specific types")
						? ""
						: " error="
								+ TraceFilesProcessorError
										.PROVIDER_SPECIFIC_TYPE_IN_PROVIDER_INDEPENDENT_STRUCTURE)
				+ " detail=\""
				+ aComparison.detail().replace("\"", "'")
				+ "\"";
	}

	private int sizeOf(List<?> aList) {
		return aList == null ? 0 : aList.size();
	}

	private String traceLineLocation(TraceRecord aTraceRecord) {
		if (aTraceRecord == null) {
			return "";
		}
		return aTraceRecord.getTraceFileName()
				+ ":"
				+ aTraceRecord.getTraceFileLineNumber();
	}

	private List<TraceRecord> requestRecords(
			String aProvider,
			StreamingMode aMode) {
		ArrayList<TraceRecord> result = new ArrayList<>();
		for (TraceRecord record : records(aProvider, aMode)) {
			if ("request_sent".equals(
					record.getTraceLine().getEventName())) {
				result.add(record);
			}
		}
		return result;
	}

	private List<TraceRecord> contextWindowMessageAddedRecords(
			String aProvider,
			StreamingMode aMode) {
		ArrayList<TraceRecord> result = new ArrayList<>();
		for (TraceRecord record : records(aProvider, aMode)) {
			if ("context_window_message_added".equals(
					record.getTraceLine().getEventName())) {
				result.add(record);
			}
		}
		return result;
	}

	private List<TraceRecord> records(
			String aProvider,
			StreamingMode aMode) {
		ArrayList<TraceRecord> result = new ArrayList<>();
		for (TraceRecord record : traceRecords.values()) {
			if (aProvider.equals(record.getId().provider())
					&& aMode == record.getId().mode()) {
				result.add(record);
			}
		}
		return result;
	}

	private List<TraceRecord> records(
			String aProvider,
			StreamingMode aMode,
			String anEventName) {
		ArrayList<TraceRecord> result = new ArrayList<>();
		for (TraceRecord record : records(aProvider, aMode)) {
			if (anEventName.equals(record.getTraceLine().getEventName())) {
				result.add(record);
			}
		}
		return result;
	}

	private Integer contextWindowIndex(TraceRecord aTraceRecord) {
		String index =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("index");
		try {
			return Integer.valueOf(index);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private Boolean contextWindowContinues(
			List<String> aPreviousMessages,
			List<String> aCurrentMessages) {
		if (aPreviousMessages.isEmpty() || aCurrentMessages.size() < 2) {
			return false;
		}
		String previousLast =
				aPreviousMessages.get(aPreviousMessages.size() - 1);
		return aCurrentMessages.contains(previousLast);
	}

	private List<String> providerIndependentContextWindowMessages(
			TraceRecord aTraceRecord) {
		ArrayList<String> result = new ArrayList<>();
		for (TraceMessage message :
				providerIndependentContextWindowStructure(aTraceRecord)) {
			result.add(canonicalMessageKey(message));
		}
		return result;
	}

	private List<TraceMessage> providerIndependentContextWindowStructure(
			TraceRecord aTraceRecord) {
		String contextWindow =
				providerIndependentContextWindowDump(aTraceRecord);
		List<TraceMessage> reflectedMessages =
				reflectedMessageStructures(contextWindow);
		if (!reflectedMessages.isEmpty()) {
			return reflectedMessages;
		}
		List<TraceMessage> providerStyleMessages =
				providerContextWindowMessages(contextWindow);
		if (!providerStyleMessages.isEmpty()) {
			return providerStyleMessages;
		}
		return List.of();
	}

	private String providerIndependentContextWindowDump(
			TraceRecord aTraceRecord) {
		String contextWindow =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("providerIndependentContextWindow");
		if (contextWindow == null) {
			contextWindow =
					aTraceRecord.getTraceLine()
							.getAuxiliaryData()
							.get("contextWindow");
		}
		return contextWindow;
	}

	private TraceMessage firstCanonicalMessageFromArgument(
			TraceRecord aTraceRecord,
			String anArgumentName) {
		String argumentValue =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get(anArgumentName);
		List<TraceMessage> messages =
				reflectedMessageStructures(argumentValue);
		if (!messages.isEmpty()) {
			return messages.get(0);
		}
		return null;
	}

	private List<TraceMessage> reflectedMessageStructures(
			String aContextWindowDump) {
		if (aContextWindowDump == null) {
			return List.of();
		}
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile(
								"(?:(?:^|[\\[{,] )|(?:^|[\\[{,]))(?:[^:]+: )?"
										+ "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)"
										+ "\\s+interfaces=\\[([^\\]]*)\\]")
						.matcher(aContextWindowDump);
		ArrayList<Integer> starts = new ArrayList<>();
		ArrayList<Integer> ends = new ArrayList<>();
		ArrayList<ReflectedObject> objects = new ArrayList<>();
		ArrayList<Integer> allStarts = new ArrayList<>();
		ArrayList<Integer> allEnds = new ArrayList<>();
		ArrayList<ReflectedObject> allObjects = new ArrayList<>();
		while (matcher.find()) {
			ReflectedObject reflectedObject =
					new ReflectedObject(
							matcher.group(1).trim(),
							interfaceNames(matcher.group(2)));
			allStarts.add(matcher.start(1));
			allEnds.add(matcher.end());
			allObjects.add(reflectedObject);
		}
		for (int i = 0; i < allStarts.size(); i++) {
			int objectEnd = i + 1 < allStarts.size()
					? allStarts.get(i + 1)
					: aContextWindowDump.length();
			String headerAndDirectFields =
					aContextWindowDump.substring(allStarts.get(i), objectEnd);
			if (isMessageShapedObject(
					headerAndDirectFields,
					allObjects.get(i))) {
				starts.add(allStarts.get(i));
				ends.add(allEnds.get(i));
				objects.add(allObjects.get(i));
			}
		}
		ArrayList<TraceMessage> result = new ArrayList<>();
		for (int i = 0; i < starts.size(); i++) {
			int nextMessageStart = i + 1 < starts.size()
					? starts.get(i + 1)
					: aContextWindowDump.length();
			int objectEnd = reflectedFieldsEnd(
					aContextWindowDump,
					starts.get(i),
					nextMessageStart);
			int segmentEnd = objectEnd > starts.get(i)
					? objectEnd
					: nextMessageStart;
			TraceMessage message =
					reflectedMessage(
							aContextWindowDump.substring(
									starts.get(i),
									segmentEnd),
							objects.get(i),
							ends.get(i) - starts.get(i));
			if (message != null) {
				result.add(message);
			}
		}
		return result;
	}

	private int reflectedFieldsEnd(
			String aDump,
			int aStart,
			int aFallbackEnd) {
		int fieldsStart = aDump.indexOf(" fields={", aStart);
		if (fieldsStart < 0 || fieldsStart >= aFallbackEnd) {
			return aFallbackEnd;
		}
		int depth = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int index = fieldsStart + " fields=".length();
				index < aDump.length();
				index++) {
			char character = aDump.charAt(index);
			if (escaped) {
				escaped = false;
				continue;
			}
			if (character == '\\') {
				escaped = true;
				continue;
			}
			if (character == '"') {
				quoted = !quoted;
				continue;
			}
			if (quoted) {
				continue;
			}
			if (character == '{') {
				depth++;
			} else if (character == '}' && --depth == 0) {
				return index + 1;
			}
		}
		return aFallbackEnd;
	}

	private TraceMessage reflectedMessage(
			String aSegment,
			ReflectedObject aReflectedObject,
			int anObjectHeaderEnd) {
		String role = reflectedMessageRole(aSegment);
		List<TracePart> parts = reflectedMessageParts(aSegment);
		if (isBlank(role)) {
			return null;
		}
		var aggregate = java.util.regex.Pattern.compile(" textAggregate=\"([^\"]+)\"").matcher(aSegment);
		String summary = aggregate.find() && TraceTextSummary.fromToken(aggregate.group(1)) != null ? aggregate.group(1) : "";
		return new TraceMessage(standardRole(role), parts, summary);
	}

	private String reflectedMessageRole(String aSegment) {
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile("role:\\s+[^,}]+\\s+value=([A-Za-z_]+)")
						.matcher(aSegment);
		return matcher.find() ? matcher.group(1) : "";
	}

	private List<TracePart> reflectedMessageParts(String aSegment) {
		ArrayList<TracePart> result = new ArrayList<>();
		int fieldsStart = aSegment.indexOf(" fields={");
		String directHeader = fieldsStart < 0
				? aSegment
				: aSegment.substring(0, fieldsStart);
		java.util.regex.Matcher textMatcher =
				java.util.regex.Pattern
						.compile(
								"fields=\\{text:\\s+java\\.lang\\.String\\s+value=\\\"((?:\\\\.|[^\"\\\\])*)\\\"",
								java.util.regex.Pattern.DOTALL)
						.matcher(aSegment);
		while (textMatcher.find()) {
			result.add(
					new TracePart(
							"text",
							unescapeTraceString(textMatcher.group(1))));
		}
		java.util.regex.Matcher imageMatcher =
				java.util.regex.Pattern
						.compile("(?:bytes|imageBytes)=(\\d+)")
						.matcher(directHeader);
		while (imageMatcher.find()) {
			result.add(
					new TracePart("imageBytes", imageMatcher.group(1)));
		}
		if (result.isEmpty()) {
			java.util.regex.Matcher formattedText =
					java.util.regex.Pattern
							.compile(
									"\\d+:text\\(\\\\?\"((?:\\\\.|[^\"\\\\])*)\\\\?\"\\)",
									java.util.regex.Pattern.DOTALL)
						.matcher(directHeader);
			while (formattedText.find()) {
				result.add(
						new TracePart(
								"text",
								unescapeTraceString(formattedText.group(1))));
			}
		}
		return result;
	}

	private boolean isMessageShapedObject(
			String anObjectSegment,
			ReflectedObject aReflectedObject) {
		if (anObjectSegment == null) {
			return false;
		}
		if (aReflectedObject.interfaceNames().stream().anyMatch(
				name -> name.equals("java.util.Collection")
						|| name.equals("java.util.List")
						|| name.equals("java.util.Map")
						|| name.equals("java.util.Set"))) {
			return false;
		}
		int fields = anObjectSegment.indexOf(" fields={");
		if (fields < 0) {
			return false;
		}
		String directFields = anObjectSegment.substring(fields);
		return directFields.matches("(?s).*\\brole:\\s+.*");
	}

	private List<TraceMessage> providerDependentContextWindowStructure(
			TraceRecord aTraceRecord) {
		String contextWindow =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("providerDependentContextWindow");
		if (contextWindow == null) {
			contextWindow =
					aTraceRecord.getTraceLine()
							.getAuxiliaryData()
							.get("providerContextWindow");
		}
		return providerContextWindowMessages(contextWindow);
	}

	private List<TraceMessage> providerDependentRequestStructure(
			TraceRecord aTraceRecord) {
		ArrayList<TraceMessage> result = new ArrayList<>();
		result.addAll(providerDependentConfigurationStructure(aTraceRecord));
		result.addAll(providerDependentContextWindowStructure(aTraceRecord));
		return result;
	}

	private List<TraceMessage> providerDependentConfigurationStructure(
			TraceRecord aTraceRecord) {
		String configuration =
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.get("providerDependentConfiguration");
		return providerConfigurationMessages(configuration);
	}

	private List<TraceMessage> providerContextWindowMessages(
			String aContextWindowDump) {
		if (aContextWindowDump == null) {
			return List.of();
		}
		for (ProviderTraceInterpreter interpreter :
				providerTraceInterpreters) {
			if (interpreter.canInterpretProviderContextWindow(
					aContextWindowDump)) {
				return interpreter.providerContextWindowMessages(
						aContextWindowDump);
			}
		}
		return List.of();
	}

	private List<TraceMessage> providerConfigurationMessages(
			String aConfigurationDump) {
		if (aConfigurationDump == null) {
			return List.of();
		}
		for (ProviderTraceInterpreter interpreter :
				providerTraceInterpreters) {
			if (interpreter.canInterpretProviderConfiguration(
					aConfigurationDump)) {
				return interpreter.providerConfigurationMessages(
						aConfigurationDump);
			}
		}
		return List.of();
	}

	private List<TraceMessage> providerResponseMessages(String aResponseDump) {
		if (aResponseDump == null) {
			return List.of();
		}
		for (ProviderTraceInterpreter interpreter :
				providerTraceInterpreters) {
			List<TraceMessage> messages =
					interpreter.providerResponseMessages(aResponseDump);
			if (!messages.isEmpty()) {
				return messages;
			}
		}
		return List.of();
	}

	private String requestModel(TraceRecord aRecord) {
		String model = aRecord.getTraceLine()
				.getAuxiliaryData()
				.get("requestModel");
		if (!isBlank(model)) {
			return model;
		}
		Map<String, String> values = canonicalPropertyValues(
				aRecord.getTraceLine()
						.getAuxiliaryData()
						.get("providerIndependentParameterStore"));
		return values.getOrDefault("current_models", "");
	}

	private Map<String, String> canonicalPropertyValues(String aDump) {
		if (aDump == null) {
			return Map.of();
		}
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile(
						"key:\\s+java\\.lang\\.String\\s+value=\\\"([^\"]+)\\\""
								+ "\\s+(?:->|-\\\\x3e)\\s+value:\\s+[^ ]+\\s+"
								+ "(?:value=\\\"((?:\\\\.|[^\"\\\\])*)\\\"|value=([^,}]+)|toString=\\\"([^\"]*)\\\")")
				.matcher(aDump);
		while (matcher.find()) {
			String value = matcher.group(2) != null
					? unescapeTraceString(matcher.group(2))
					: matcher.group(3) != null
							? matcher.group(3).trim()
							: matcher.group(4);
			result.put(matcher.group(1), value);
		}
		return result;
	}

	private void putCanonicalProperty(
			Map<String, String> aTarget,
			String aName,
			String aValue) {
		if (!isBlank(aName) && aValue != null) {
			aTarget.put(aName, aValue);
		}
	}

	private ContextWindowComparison compareContextWindows(
			List<TraceMessage> aProviderIndependentMessages,
			List<TraceMessage> aProviderDependentMessages) {
		List<TraceMessage> independent =
				comparableProviderIndependentMessages(
						aProviderIndependentMessages,
						aProviderDependentMessages);
		List<TraceMessage> dependent =
				aProviderDependentMessages == null
						? List.of()
						: aProviderDependentMessages;
		if (flattenedMessagesMatch(independent, dependent)) {
			return new ContextWindowComparison(true, "request content matches");
		}
		if (independent.size() != dependent.size()) {
			return new ContextWindowComparison(
					false,
					"message count differs: providerIndependent="
							+ independent.size()
							+ " providerDependent="
							+ dependent.size()
							+ " providerIndependentMessages="
							+ independent
							+ " providerDependentMessages="
							+ dependent);
		}
		for (int i = 0; i < independent.size(); i++) {
			ContextWindowComparison comparison =
					compareMessage(independent.get(i), dependent.get(i), i);
			if (!comparison.matches()) {
				return comparison;
			}
		}
		return new ContextWindowComparison(true, "all messages match");
	}

	private boolean flattenedMessagesMatch(
			List<TraceMessage> aProviderIndependentMessages,
			List<TraceMessage> aProviderDependentMessages) {
		return flattenedParts(aProviderIndependentMessages)
				.equals(flattenedParts(aProviderDependentMessages));
	}

	private List<String> flattenedParts(List<TraceMessage> aMessages) {
		ArrayList<String> result = new ArrayList<>();
		if (aMessages == null) {
			return result;
		}
		for (TraceMessage message : aMessages) {
			for (TracePart part : message.parts()) {
				result.add(
						part.kind()
								+ ":"
								+ normalizedText(part.value()));
			}
		}
		return result;
	}

	private List<TraceMessage> comparableProviderIndependentMessages(
			List<TraceMessage> aProviderIndependentMessages,
			List<TraceMessage> aProviderDependentMessages) {
		List<TraceMessage> independent =
				aProviderIndependentMessages == null
						? List.of()
						: aProviderIndependentMessages;
		List<TraceMessage> dependent =
				aProviderDependentMessages == null
						? List.of()
						: aProviderDependentMessages;
		if (systemMessageCount(dependent) == 1
				&& systemMessageCount(independent) > 1) {
			return combinedSystemMessages(independent);
		}
		if (hasRole(dependent, "SYSTEM")) {
			return independent;
		}
		ArrayList<TraceMessage> result = new ArrayList<>();
		for (TraceMessage message : independent) {
			if (!"SYSTEM".equals(message.role())) {
				result.add(message);
			}
		}
		return result;
	}

	private int systemMessageCount(List<TraceMessage> aMessages) {
		int result = 0;
		for (TraceMessage message : aMessages) {
			if ("SYSTEM".equals(standardRole(message.role()))) {
				result++;
			}
		}
		return result;
	}

	private List<TraceMessage> combinedSystemMessages(
			List<TraceMessage> aMessages) {
		StringBuilder systemText = new StringBuilder();
		List<String> pieces = new ArrayList<>();
		ArrayList<TraceMessage> result = new ArrayList<>();
		for (TraceMessage message : aMessages) {
			if ("SYSTEM".equals(standardRole(message.role()))) {
				systemText.append(textOf(message));
				pieces.add(textOf(message));
			}
		}
		if (systemText.length() > 0) {
			result.add(
					new TraceMessage(
							"SYSTEM",
							List.of(
									new TracePart(
											"text",
											pieces.stream().anyMatch(p -> TraceTextSummary.fromToken(p) != null)
												? TraceTextSummary.concatenateEdges(pieces) : systemText.toString()))));
		}
		for (TraceMessage message : aMessages) {
			if (!"SYSTEM".equals(standardRole(message.role()))) {
				result.add(message);
			}
		}
		return result;
	}

	private ContextWindowComparison compareMessage(
			TraceMessage aProviderIndependentMessage,
			TraceMessage aProviderDependentMessage,
			int aMessageIndex) {
		if (!standardRole(aProviderIndependentMessage.role())
				.equals(standardRole(aProviderDependentMessage.role()))) {
			return new ContextWindowComparison(
					false,
					"message "
							+ aMessageIndex
							+ " role differs: providerIndependent="
							+ aProviderIndependentMessage.role()
							+ " providerDependent="
							+ aProviderDependentMessage.role());
		}
		List<TracePart> independentParts =
				coalescedTextParts(aProviderIndependentMessage.parts());
		List<TracePart> dependentParts =
				coalescedTextParts(aProviderDependentMessage.parts());
		if (independentParts.size() != dependentParts.size()) {
			return new ContextWindowComparison(
					false,
					"message "
							+ aMessageIndex
							+ " part count differs: providerIndependent="
							+ independentParts.size()
							+ " providerDependent="
							+ dependentParts.size()
							+ " providerIndependentParts="
							+ independentParts
							+ " providerDependentParts="
							+ dependentParts);
		}
		for (int i = 0; i < independentParts.size(); i++) {
			if (!partsMatch(independentParts.get(i), dependentParts.get(i))) {
				return new ContextWindowComparison(
						false,
						"message "
								+ aMessageIndex
								+ " part "
								+ i
								+ " differs: providerIndependent="
								+ independentParts.get(i)
								+ " providerDependent="
								+ dependentParts.get(i));
			}
		}
		return new ContextWindowComparison(true, "message matches");
	}

	private List<TracePart> coalescedTextParts(List<TracePart> someParts) {
		ArrayList<TracePart> result = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		for (TracePart part : someParts) {
			if ("text".equals(part.kind())) {
				text.append(part.value());
				continue;
			}
			appendCoalescedText(result, text);
			result.add(part);
		}
		appendCoalescedText(result, text);
		return result;
	}

	private void appendCoalescedText(
			List<TracePart> someParts,
			StringBuilder someText) {
		if (someText.length() == 0) {
			return;
		}
		someParts.add(new TracePart("text", someText.toString()));
		someText.setLength(0);
	}

	private boolean partsMatch(
			TracePart aProviderIndependentPart,
			TracePart aProviderDependentPart) {
		if (!aProviderIndependentPart.kind()
				.equals(aProviderDependentPart.kind())) {
			return false;
		}
		if ("text".equals(aProviderIndependentPart.kind())) {
			return textMatches(
					aProviderIndependentPart.value(),
					aProviderDependentPart.value());
		}
		return aProviderIndependentPart.value()
				.equals(aProviderDependentPart.value());
	}

	private boolean textMatches(
			String aProviderIndependentText,
			String aProviderDependentText) {
		if (TraceTextSummary.fromToken(aProviderIndependentText) != null
				|| TraceTextSummary.fromToken(aProviderDependentText) != null)
			return TraceTextSummary.matches(aProviderIndependentText, aProviderDependentText);
		String independent = normalizedText(aProviderIndependentText);
		String dependent = normalizedText(aProviderDependentText);
		if (independent.equals(dependent)) {
			return true;
		}
		if (ellipsisPatternMatches(independent, dependent)
				|| ellipsisPatternMatches(dependent, independent)) {
			return true;
		}
		if (independent.endsWith("...")) {
			String prefix =
					independent.substring(0, independent.length() - 3);
			return dependent.startsWith(prefix);
		}
		if (dependent.endsWith("...")) {
			String prefix = dependent.substring(0, dependent.length() - 3);
			return independent.startsWith(prefix);
		}
		return false;
	}

	private boolean ellipsisPatternMatches(
			String aPossiblyTruncatedText,
			String aFullText) {
		if (aPossiblyTruncatedText == null
				|| aFullText == null
				|| !aPossiblyTruncatedText.contains("...")) {
			return false;
		}
		int searchFrom = 0;
		for (String chunk : aPossiblyTruncatedText.split("\\.\\.\\.", -1)) {
			if (chunk.isEmpty()) {
				continue;
			}
			int index = aFullText.indexOf(chunk, searchFrom);
			if (index < 0) {
				return false;
			}
			searchFrom = index + chunk.length();
		}
		return true;
	}

	private String normalizedText(String aText) {
		if (aText == null) {
			return "";
		}
		return aText.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\\"", "\"")
				.trim();
	}

	private boolean hasRole(
			List<TraceMessage> aMessages,
			String aRole) {
		for (TraceMessage message : aMessages) {
			if (aRole.equals(standardRole(message.role()))) {
				return true;
			}
		}
		return false;
	}

	private String providerRole(String aRole) {
		String role = standardRole(aRole);
		if ("MODEL".equals(role)) {
			return "ASSISTANT";
		}
		return role;
	}

	private String standardRole(String aRole) {
		if (aRole == null) {
			return "";
		}
		String role = aRole.trim();
		if (role.startsWith("\"") && role.endsWith("\"")
				&& role.length() >= 2) {
			role = role.substring(1, role.length() - 1);
		}
		if ("null".equalsIgnoreCase(role)) {
			return "";
		}
		return role.toUpperCase();
	}

	private int integerValue(String aText) {
		try {
			return Integer.parseInt(aText);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private String reflectedObjectToString(String aDump, int aStartIndex) {
		int toStringIndex = aDump.indexOf(" toString=", aStartIndex);
		int fieldsIndex = aDump.indexOf(" fields=", aStartIndex);
		if (toStringIndex < 0
				|| (fieldsIndex >= 0 && fieldsIndex < toStringIndex)) {
			return "";
		}
		int valueStart = toStringIndex + " toString=".length();
		if (valueStart < aDump.length() && aDump.charAt(valueStart) == '"') {
			valueStart++;
		}
		int valueEnd = fieldsIndex >= 0 ? fieldsIndex : aDump.length();
		while (valueEnd > valueStart
				&& Character.isWhitespace(aDump.charAt(valueEnd - 1))) {
			valueEnd--;
		}
		if (valueEnd > valueStart && aDump.charAt(valueEnd - 1) == '"') {
			valueEnd--;
		}
		return aDump.substring(valueStart, valueEnd);
	}

	private String unescapeTraceString(String aValue) {
		if (aValue == null) {
			return "";
		}
		return aValue.replace("\\\"", "\"").replace("\\\\", "\\");
	}

	private void attachAuxiliaryExtractedData(TraceRecord aTraceRecord) {
		for (Map.Entry<String, String> entry :
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.entrySet()) {
			aTraceRecord.putExtractedData(entry.getKey(), entry.getValue());
		}
	}

	private void tagClassesInsideTrace(TraceRecord aTraceRecord) {
		for (Map.Entry<String, String> entry :
				aTraceRecord.getTraceLine()
						.getAuxiliaryData()
						.entrySet()) {
			tagClassesFromArgument(
					aTraceRecord,
					entry.getKey(),
					entry.getValue());
		}
	}

	private void tagClassesFromArgument(
			TraceRecord aTraceRecord,
			String aKey,
			String aValue) {
		if (isParameterStoreArgument(aKey)) {
			add(
					rootClassNameInReflectiveDump(aValue),
					"ParameterStore",
					aTraceRecord,
					aKey,
					"argument name identifies a provider-independent parameter store");
		}
		List<ReflectedObject> messageObjects =
				isProviderIndependentResponseArgument(aKey)
						? rootObjectIn(aValue)
						: isProviderIndependentContextWindowArgument(aKey)
								? rootCollectionElementObjects(aValue)
								: List.of();
		for (ReflectedObject reflectedObject : messageObjects) {
			add(
					reflectedObject.className(),
					"GenericContextWindowMessage",
					aTraceRecord,
					aKey,
					"argument position identifies a provider-independent message");
		}
	}

	private boolean isProviderIndependentContextWindowArgument(String aKey) {
		return aKey != null
				&& aKey.toLowerCase().contains(
						"providerindependentcontextwindow");
	}

	private boolean isProviderIndependentResponseArgument(String aKey) {
		return aKey != null
				&& aKey.toLowerCase().contains("providerindependentresponse");
	}

	private List<ReflectedObject> rootObjectIn(String aDump) {
		String className = rootClassNameInReflectiveDump(aDump);
		if (isBlank(className)) {
			return List.of();
		}
		List<ReflectedObject> objects = reflectedObjectsIn(aDump);
		for (ReflectedObject object : objects) {
			if (className.equals(object.className())) {
				return List.of(object);
			}
		}
		return List.of(new ReflectedObject(className, List.of()));
	}

	private List<ReflectedObject> rootCollectionElementObjects(String aDump) {
		if (aDump == null) {
			return List.of();
		}
		int elements = aDump.indexOf(" elements=[");
		if (elements < 0) {
			return List.of();
		}
		int start = elements + " elements=".length();
		ArrayList<ReflectedObject> result = new ArrayList<>();
		int depth = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int index = start; index < aDump.length(); index++) {
			char character = aDump.charAt(index);
			if (escaped) {
				escaped = false;
				continue;
			}
			if (quoted && character == '\\') {
				escaped = true;
				continue;
			}
			if (character == '"') {
				quoted = !quoted;
				continue;
			}
			if (quoted) {
				continue;
			}
			if (character == '[') {
				depth++;
				if (depth == 2) {
					ReflectedObject element =
							reflectedObjectAt(aDump, index);
					if (element != null) {
						result.add(element);
					}
				}
			} else if (character == ']') {
				depth--;
				if (depth <= 0) {
					break;
				}
			}
		}
		return result;
	}

	private ReflectedObject reflectedObjectAt(String aDump, int anIndex) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile(
						"\\[\\d+\\]:\\s+"
								+ "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)"
								+ "\\s+interfaces=\\[([^\\]]*)\\]")
				.matcher(aDump);
		matcher.region(anIndex, aDump.length());
		if (!matcher.lookingAt()) {
			return null;
		}
		return new ReflectedObject(
				matcher.group(1),
				interfaceNames(matcher.group(2)));
	}

	private boolean isParameterStoreArgument(String aKey) {
		return aKey != null
				&& aKey.toLowerCase().contains("parameterstore");
	}

	private String rootClassNameInReflectiveDump(String aValue) {
		if (aValue == null) {
			return "";
		}
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile(
								"^(?:[^:]+: )?"
										+ "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)"
										+ "(?= interfaces=| value=| toString=)")
						.matcher(aValue);
		return matcher.find() ? matcher.group(1).trim() : "";
	}

	private String loadableClassName(
			String aReportedClassName,
			String aReflectiveDump) {
		if (aReflectiveDump != null) {
			java.util.regex.Matcher matcher =
					java.util.regex.Pattern
							.compile(
									"toString=\\\"([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)@")
							.matcher(aReflectiveDump);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return aReportedClassName;
	}

	private List<ReflectedObject> reflectedObjectsIn(String aValue) {
		if (aValue == null) {
			return List.of();
		}
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile(
								"(?:(?:^|[\\[{,] )|(?:^|[\\[{,]))(?:[^:]+: )?"
										+ "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)"
										+ "\\s+interfaces=\\[([^\\]]*)\\]")
						.matcher(aValue);
		ArrayList<ReflectedObject> result = new ArrayList<>();
		while (matcher.find()) {
			String className = matcher.group(1).trim();
			if (isBlank(className) || !className.contains(".")) {
				continue;
			}
			result.add(
					new ReflectedObject(
							className,
							interfaceNames(matcher.group(2))));
		}
		return result;
	}

	private List<ReflectedObject> messageObjectsIn(String aValue) {
		if (aValue == null) {
			return List.of();
		}
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile(
								"(?:(?:^|[\\[{,] )|(?:^|[\\[{,]))(?:[^:]+: )?"
										+ "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)"
										+ "\\s+interfaces=\\[([^\\]]*)\\]")
						.matcher(aValue);
		ArrayList<Integer> starts = new ArrayList<>();
		ArrayList<ReflectedObject> objects = new ArrayList<>();
		while (matcher.find()) {
			starts.add(matcher.start(1));
			objects.add(
					new ReflectedObject(
							matcher.group(1).trim(),
							interfaceNames(matcher.group(2))));
		}
		ArrayList<ReflectedObject> result = new ArrayList<>();
		for (int index = 0; index < starts.size(); index++) {
			int end = index + 1 < starts.size()
					? starts.get(index + 1)
					: aValue.length();
			if (isMessageShapedObject(
					aValue.substring(starts.get(index), end),
					objects.get(index))) {
				result.add(objects.get(index));
			}
		}
		return result;
	}

	private String canonicalMessageKey(TraceMessage aMessage) {
		StringBuilder result = new StringBuilder(standardRole(aMessage.role()));
		for (TracePart part : aMessage.parts()) {
			result.append('|')
					.append(part.kind())
					.append('=')
					.append(normalizedText(part.value()));
		}
		return result.toString();
	}

	private List<String> canonicalMessageKeys(
			List<TraceMessage> someMessages) {
		ArrayList<String> result = new ArrayList<>();
		if (someMessages == null) {
			return result;
		}
		for (TraceMessage message : someMessages) {
			result.add(canonicalMessageKey(message));
		}
		return List.copyOf(result);
	}

	private List<String> canonicalMessageStrings(String aDump) {
		ArrayList<String> result = new ArrayList<>();
		for (TraceMessage message : reflectedMessageStructures(aDump)) {
			result.add(canonicalMessageKey(message));
		}
		if (result.isEmpty()) {
			for (ProviderTraceInterpreter interpreter :
					providerTraceInterpreters) {
				if (!interpreter.canInterpretProviderContextWindow(aDump)) {
					continue;
				}
				for (TraceMessage message :
						interpreter.providerContextWindowMessages(aDump)) {
					result.add(canonicalMessageKey(message));
				}
				break;
			}
		}
		return result;
	}

	private List<String> interfaceNames(String anInterfacesText) {
		ArrayList<String> result = new ArrayList<>();
		if (anInterfacesText == null || anInterfacesText.isBlank()) {
			return result;
		}
		for (String interfaceName : anInterfacesText.split(",")) {
			String trimmed = interfaceName.trim();
			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}
		return result;
	}

	private boolean hasInterfaceSuffix(
			ReflectedObject aReflectedObject,
			String anInterfaceSuffix) {
		for (String interfaceName : aReflectedObject.interfaceNames()) {
			if (interfaceName.endsWith("." + anInterfaceSuffix)
					|| interfaceName.equals(anInterfaceSuffix)) {
				return true;
			}
		}
		return false;
	}

	private void add(
			String aClassName,
			String aTag,
			TraceRecord aTraceRecord,
			String aFieldName,
			String aReason) {
		if (providerSpecificTypeRegistry.isProviderSpecificType(aClassName)) {
			recordCheckerMessage(
					TraceProcessorMessageLevel.INFO,
					"class_tag_ignored className="
							+ aClassName
							+ " proposedTag="
							+ aTag
							+ " traceEvent="
							+ (aTraceRecord == null
									? ""
									: aTraceRecord.getTraceLine().getEventName())
							+ " fieldName="
							+ aFieldName
							+ " reason=\"provider-specific classes cannot implement provider-independent trace roles\"");
			return;
		}
		if (!isBlank(aClassName) && !isBlank(aTag)) {
			classTags.computeIfAbsent(
					aClassName,
					ignored -> new LinkedHashSet<>()).add(aTag);
			ClassTagDerivation derivation =
					new ClassTagDerivation(
							aClassName,
							aTag,
							aTraceRecord == null
									? ""
									: aTraceRecord.getTraceLine()
											.getEventName(),
							aTraceRecord == null
									? ""
									: traceLineLocation(aTraceRecord),
							aFieldName,
							aReason);
			List<ClassTagDerivation> derivations =
					classTagDerivations.computeIfAbsent(
							aClassName,
							ignored -> new ArrayList<>());
			if (!derivations.contains(derivation)) {
				derivations.add(derivation);
				addClassTagDerivationToTraceRecord(
						aTraceRecord,
						derivation.message());
				recordCheckerMessage(
						TraceProcessorMessageLevel.INFO,
						derivation.message());
			}
		}
	}

	private void addClassTagDerivationToTraceRecord(
			TraceRecord aTraceRecord,
			String aDerivationMessage) {
		if (aTraceRecord == null || isBlank(aDerivationMessage)) {
			return;
		}
		ArrayList<String> derivations = new ArrayList<>();
		Object existing =
				aTraceRecord.getExtractedData().get("classTagDerivations");
		if (existing instanceof List<?>) {
			for (Object value : (List<?>) existing) {
				derivations.add(String.valueOf(value));
			}
		}
		if (!derivations.contains(aDerivationMessage)) {
			derivations.add(aDerivationMessage);
		}
		aTraceRecord.putExtractedData("classTagDerivations", derivations);
	}

	private List<String> lineBasedTraceReportLines() {
		ArrayList<String> result = new ArrayList<>();
		result.add("LineBasedTraceReport");
		result.add("Each numbered entry is one parsed trace record.");
		int index = 1;
		for (TraceRecord record : traceRecords.values()) {
			TraceLine line = record.getTraceLine();
			result.add(
					"["
							+ index++
							+ "] "
							+ traceLineLocation(record)
							+ " provider="
							+ record.getId().provider()
							+ " mode="
							+ record.getId().mode()
							+ " sequence="
							+ record.getId().sequenceNumber()
							+ " event="
							+ line.getEventName()
							+ " source="
							+ line.getSourceClassName());
			addMapReportLines(
					result,
					"  auxiliary",
					line.getAuxiliaryData());
			addMapReportLines(
					result,
					"  extracted",
					record.getExtractedData());
		}
		return result;
	}

	private List<String> deduplicatedTraceReportLines() {
		ArrayList<String> result = new ArrayList<>();
		result.add("DeduplicatedTraceReport");
		result.add(
				"Each numbered entry is a grouped checker result and cites LineBasedTraceReport entries.");
		Map<String, Integer> lineReportNumbers = lineReportNumbersByLocation();
		int index = 1;
		for (String message : groupedCheckerMessages) {
			result.add("[" + index++ + "] " + message);
			List<Integer> evidence =
					lineReportEvidenceNumbers(message, lineReportNumbers);
			if (!evidence.isEmpty()) {
				result.add(
						"  evidence="
								+ reportRefs(
										"LineBasedTraceReport",
										evidence));
			}
		}
		return result;
	}

	private List<String> checkBasedTraceReportLines() {
		ArrayList<String> result = new ArrayList<>();
		result.add("CheckBasedTraceReport");
		result.add(
				"Verdict: errors="
						+ countMessagesWithPrefix(ERROR_MESSAGE_PREFIX)
						+ " warnings="
						+ countMessagesWithPrefix(WARNING_MESSAGE_PREFIX)
						+ " info="
						+ countMessagesWithPrefix(INFO_MESSAGE_PREFIX));
		if (!errors.isEmpty()) {
			result.add("Errors: " + errors);
		}
		result.add("");
		result.addAll(traceFileCheckReportLines());
		result.add("");
		result.addAll(classTagCheckReportLines());
		result.add("");
		result.addAll(promptBasedCheckReportLines());
		return result;
	}

	private List<String> traceFileCheckReportLines() {
		ArrayList<String> result = new ArrayList<>();
		result.add("Trace File Checks");
		for (Map.Entry<String, TraceFiles> entry :
				providerTraceFiles.entrySet()) {
			addTraceFileCheckReportLine(
					result,
					StreamingMode.NON_STREAMING,
					traceFile(entry.getValue(), false));
			addTraceFileCheckReportLine(
					result,
					StreamingMode.STREAMING,
					traceFile(entry.getValue(), true));
		}
		if (providerTraceFiles.isEmpty()) {
			result.add("[none]");
		}
		return result;
	}

	private void addTraceFileCheckReportLine(
			List<String> aReportLines,
			StreamingMode aMode,
			Path aTraceFile) {
		String messageNeedle =
				"trace_file_error mode=" + aMode;
		String evidence = deduplicatedEvidenceForMessage(messageNeedle);
		if (traceFileExists(aTraceFile) && Files.isReadable(aTraceFile)) {
			aReportLines.add(
					INFO_MESSAGE_PREFIX
							+ " trace file exists check=passed mode="
							+ aMode
							+ " traceFile="
							+ reportValue(aTraceFile));
			return;
		}
		aReportLines.add(
				ERROR_MESSAGE_PREFIX
						+ " trace file exists check=failed error="
						+ traceFileError(aMode, aTraceFile)
						+ " mode="
						+ aMode
						+ " traceFile="
						+ reportValue(aTraceFile)
						+ " evidence="
						+ evidence);
	}

	private TraceFilesProcessorError traceFileError(
			StreamingMode aMode,
			Path aTraceFile) {
		if (!traceFileExists(aTraceFile)) {
			return aMode == StreamingMode.STREAMING
					? TraceFilesProcessorError.MISSING_STREAMING_TRACE_FILE
					: TraceFilesProcessorError.MISSING_NON_STREAMING_TRACE_FILE;
		}
		return TraceFilesProcessorError.UNREADABLE_TRACE_FILE;
	}

	private List<String> classTagCheckReportLines() {
		ArrayList<String> result = new ArrayList<>();
		result.add("Class Tag Checks");
		Set<String> tags = reportedClassTags();
		if (tags.isEmpty()) {
			result.add("[none]");
			return result;
		}
		for (String tag : tags) {
			addClassTagCheckReportLines(result, tag);
		}
		return result;
	}

	private List<String> classTagCheckReportLines(String aTag) {
		ArrayList<String> result = new ArrayList<>();
		result.add("Class Tag Checks");
		if (isBlank(aTag)) {
			result.add("[none]");
			return result;
		}
		addClassTagCheckReportLines(result, aTag);
		return result;
	}

	private void addClassTagCheckReportLines(
			List<String> aResult,
			String aTag) {
		List<String> classes = classesForTag(aTag);
		if (classes.isEmpty()) {
			aResult.add(
					ERROR_MESSAGE_PREFIX
							+ " class exists for tag check=failed error="
							+ TraceFilesProcessorError.MISSING_CLASS_FOR_TAG
							+ " tag="
							+ aTag
							+ " classes=[] expectedTraceEvents="
							+ compactListValue(expectedTraceEventsForTag(aTag))
							+ " evidence="
							+ deduplicatedEvidenceForMessage(
									"class_tag_missing tag=" + aTag));
			return;
		}
		aResult.add(
				INFO_MESSAGE_PREFIX
						+ " class exists for tag check=passed tag="
						+ aTag
						+ " classes="
						+ compactListValue(classes)
						+ " evidence="
						+ deduplicatedEvidenceForMessage(
								"class_tag_present tag=" + aTag));
		for (String className : classes) {
			List<String> classTagsForClass = tagsForClass(className);
			String multiplicity =
					classTagsForClass.size() > 1
							? " multipleTags=true tags="
									+ compactListValue(classTagsForClass)
							: "";
			aResult.add(
					"  class="
							+ className
							+ multiplicity
							+ " derivedFrom="
							+ deduplicatedEvidenceForMessage(
									"class_tag_derived className="
											+ className
											+ " tag="
											+ aTag));
		}
	}

	private Set<String> reportedClassTags() {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (Set<String> tags : classTags.values()) {
			result.addAll(tags);
		}
		for (String message : groupedCheckerMessages) {
			if (message.contains("class_tag_missing")) {
				String tag = checkerFieldValue(message, "tag");
				if (!isBlank(tag)) {
					result.add(tag);
				}
			}
		}
		return result;
	}

	private String deduplicatedEvidenceForMessage(String aNeedle) {
		ArrayList<Integer> refs = new ArrayList<>();
		for (int i = 0; i < groupedCheckerMessages.size(); i++) {
			if (groupedCheckerMessages.get(i).contains(aNeedle)) {
				refs.add(i + 1);
			}
		}
		return compactReportRefs("DeduplicatedTraceReport", refs);
	}

	private void addMapReportLines(
			List<String> aLines,
			String aLabel,
			Map<String, ?> aMap) {
		if (aMap == null || aMap.isEmpty()) {
			return;
		}
		StringBuilder builder = new StringBuilder(aLabel).append('=');
		boolean first = true;
		for (Map.Entry<String, ?> entry : aMap.entrySet()) {
			if (!first) {
				builder.append(", ");
			}
			first = false;
			builder.append(entry.getKey())
					.append('=')
					.append(reportValue(entry.getValue()));
		}
		aLines.add(builder.toString());
	}

	private List<String> promptBasedCheckReportLines() {
		ArrayList<String> result = new ArrayList<>();
		result.add("Prompt Checks");
		List<PromptReportSubject> prompts = expectedPromptReportSubjects();
		if (prompts.isEmpty()) {
			result.add("[none]");
			return result;
		}
		Map<String, List<String>> providerGenericByTraceLine =
				checkerMessagesByTraceLine(
						PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH);
		Map<String, List<String>> providerGenericImageMessages =
				checkerMessagesByCheck(PROVIDER_GENERIC_IMAGE_MATCH);
		Map<String, List<String>> expectedGenericImageMessages =
				checkerMessagesByCheck(EXPECTED_GENERIC_IMAGE_MATCH);
		int promptNumber = 1;
		for (PromptReportSubject prompt : prompts) {
			result.add(
					"Prompt "
							+ promptNumber++
							+ " "
							+ prompt.id()
							+ " "
							+ prompt.role()
							+ ": "
							+ prompt.textPreview());
			List<String> promptMessages =
					checkerMessagesForPrompt(prompt.id());
			if (promptMessages.isEmpty()) {
				result.add(
						"  "
								+ ERROR_MESSAGE_PREFIX
								+ " no checks reported for this prompt");
				continue;
			}
			addPromptCheckerMessages(
					result,
					"expected prompt in provider request",
					promptMessages);
			addProviderGenericMatchesForPrompt(
					result,
					promptMessages,
					providerGenericByTraceLine);
			addExpectedCompactionForPrompt(
					result,
					prompt.id());
			if ("USER[0]".equals(prompt.id())) {
				addPromptCheckerMessages(
						result,
						"generic and provider image comparison",
						providerGenericImageMessages.getOrDefault(
								PROVIDER_GENERIC_IMAGE_MATCH,
								List.of()));
				addPromptCheckerMessages(
						result,
						"expected and generic image comparison",
						expectedGenericImageMessages.getOrDefault(
								EXPECTED_GENERIC_IMAGE_MATCH,
								List.of()));
			}
			if ("USER".equals(prompt.role())) {
				addUserPromptIndependentChecks(
						result,
						promptIndex(prompt.id()));
			}
		}
		return result;
	}

	private void addUserPromptIndependentChecks(
			List<String> aReportLines,
			int aPromptIndex) {
		if (aPromptIndex < 0) {
			return;
		}
		addPromptCheckerMessages(
				aReportLines,
				"provider-independent non-streaming request structure matches across providers",
				checkerMessagesForOccurrence(
						PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS,
						"request_sent",
						aPromptIndex));
		addPromptCheckerMessages(
				aReportLines,
				"provider-independent non-streaming response structure matches across providers",
				checkerMessagesForOccurrence(
						PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS,
						"response_translated",
						aPromptIndex));
		addStreamingChunkReflectionForPrompt(
				aReportLines,
				aPromptIndex);
		addStreamingCallbacksForPrompt(
				aReportLines,
				aPromptIndex);
	}

	private record PromptReportSubject(
			String id,
			String role,
			String textPreview) {
	}

	private List<PromptReportSubject> expectedPromptReportSubjects() {
		ArrayList<PromptReportSubject> expectedPrompts =
				new ArrayList<>();
		int maxPrompts =
				Math.max(
						expectedInputs.systemPromptTexts().size(),
						expectedInputs.userPromptTexts().size());
		for (int i = 0; i < maxPrompts; i++) {
			if (i < expectedInputs.systemPromptTexts().size()) {
				expectedPrompts.add(
						new PromptReportSubject(
								expectedPromptId("SYSTEM", i),
								"SYSTEM",
								quotedPreview(
										expectedInputs.systemPromptTexts()
												.get(i))));
			}
			if (i < expectedInputs.userPromptTexts().size()) {
				expectedPrompts.add(
						new PromptReportSubject(
								expectedPromptId("USER", i),
								"USER",
								quotedPreview(
										expectedInputs.userPromptTexts()
												.get(i))));
			}
		}
		return expectedPrompts;
	}

	private List<PromptReportSubject> promptsInObservedOrder(
			List<PromptReportSubject> aPrompts) {
		ArrayList<PromptReportSubject> result = new ArrayList<>();
		for (TraceRecord record : traceRecords.values()) {
			if (!"request_sent".equals(record.getTraceLine().getEventName())) {
				continue;
			}
			for (TraceMessage message :
					providerDependentRequestMessages(record)) {
				for (TracePart part : message.parts()) {
					if (!"text".equals(part.kind())) {
						continue;
					}
					PromptReportSubject prompt =
							matchingPromptSubject(
									aPrompts,
									message.role(),
									part.value());
					if (prompt != null && !result.contains(prompt)) {
						result.add(prompt);
					}
				}
			}
		}
		for (PromptReportSubject prompt : aPrompts) {
			if (!result.contains(prompt)) {
				result.add(prompt);
			}
		}
		return result;
	}

	private PromptReportSubject matchingPromptSubject(
			List<PromptReportSubject> aPrompts,
			String aRole,
			String aText) {
		for (PromptReportSubject prompt : aPrompts) {
			if (!standardRole(prompt.role()).equals(standardRole(aRole))) {
				continue;
			}
			if (promptTextMatches(aText, promptText(prompt))) {
				return prompt;
			}
		}
		return null;
	}

	private String promptText(PromptReportSubject aPrompt) {
		if (aPrompt == null) {
			return "";
		}
		String id = aPrompt.id();
		if (id.startsWith("SYSTEM[")) {
			int index = promptIndex(id);
			if (index >= 0
					&& index < expectedInputs.systemPromptTexts().size()) {
				return expectedInputs.systemPromptTexts().get(index);
			}
		}
		if (id.startsWith("USER[")) {
			int index = promptIndex(id);
			if (index >= 0
					&& index < expectedInputs.userPromptTexts().size()) {
				return expectedInputs.userPromptTexts().get(index);
			}
		}
		return "";
	}

	private int promptIndex(String aPromptId) {
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile("\\[(\\d+)\\]")
						.matcher(aPromptId == null ? "" : aPromptId);
		if (!matcher.find()) {
			return -1;
		}
		return integerValue(matcher.group(1));
	}

	private String quotedPreview(String aText) {
		return "\"" + preview(aText) + "\"";
	}

	private List<String> checkerMessagesForPrompt(String aPromptId) {
		ArrayList<String> result = new ArrayList<>();
		for (String message : checkerMessages) {
			if (message.contains("expectedPromptId=" + aPromptId)) {
				result.add(message);
			}
		}
		return result;
	}

	private Map<String, List<String>> checkerMessagesByTraceLine(
			String aCheckName) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String message :
				checkerMessagesByCheck(aCheckName)
						.getOrDefault(aCheckName, List.of())) {
			String traceLine = checkerFieldValue(
					checkerMessageText(message),
					"traceLine");
			if (isBlank(traceLine)) {
				continue;
			}
			result.computeIfAbsent(
					traceLine,
					ignored -> new ArrayList<>()).add(message);
		}
		return result;
	}

	private Map<String, List<String>> checkerMessagesByCheck(
			String aCheckName) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String message : checkerMessages) {
			if (aCheckName.equals(checkName(message))) {
				result.computeIfAbsent(
						aCheckName,
						ignored -> new ArrayList<>()).add(message);
			}
		}
		return result;
	}

	private List<String> checkerMessagesForOccurrence(
			String aCheckName,
			String anEventName,
			int anOccurrence) {
		ArrayList<String> result = new ArrayList<>();
		for (String message : checkerMessages) {
			if (!aCheckName.equals(checkName(message))) {
				continue;
			}
			String messageText = checkerMessageText(message);
			if (!anEventName.equals(checkerFieldValue(messageText, "event"))) {
				continue;
			}
			if (anOccurrence != integerValue(
					checkerFieldValue(messageText, "occurrence"))) {
				continue;
			}
			result.add(message);
		}
		return result;
	}

	private void addStreamingChunkReflectionForPrompt(
			List<String> aReportLines,
			int aPromptIndex) {
		for (String provider : providers()) {
			List<String> messages =
					checkerMessagesForProviderOccurrence(
							STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW,
							provider,
							aPromptIndex);
			addPromptCheckerMessages(
					aReportLines,
					streamingChunkReflectionLabel(messages),
					messages);
		}
	}

	private String streamingChunkReflectionLabel(List<String> aMessages) {
		for (String message : aMessages) {
			if (message.contains("checked=false")) {
				return "streaming chunks merged response has no following context window to check";
			}
		}
		return "streaming chunks merged response reflected in next context window";
	}

	private List<String> checkerMessagesForProviderOccurrence(
			String aCheckName,
			String aProvider,
			int anOccurrence) {
		ArrayList<String> result = new ArrayList<>();
		int seen = 0;
		for (String message : checkerMessages) {
			if (!aCheckName.equals(checkName(message))) {
				continue;
			}
			String messageText = checkerMessageText(message);
			if (!aProvider.equals(checkerFieldValue(messageText, "provider"))) {
				continue;
			}
			if (seen++ == anOccurrence) {
				result.add(message);
				break;
			}
		}
		return result;
	}

	private void addStreamingCallbacksForPrompt(
			List<String> aReportLines,
			int aPromptIndex) {
		for (String provider : providers()) {
			TraceRecord requestRecord =
					recordAtOccurrence(
							provider,
							StreamingMode.STREAMING,
							"request_sent",
							aPromptIndex);
			TraceRecord nextRequestRecord =
					recordAtOccurrence(
							provider,
							StreamingMode.STREAMING,
							"request_sent",
							aPromptIndex + 1);
			addStreamingCallbackForPrompt(
					aReportLines,
					provider,
					requestRecord,
					nextRequestRecord,
					PARTIAL_RESPONSE_CALLBACK_INVOKED,
					"partial response callback invoked during streaming response");
			addStreamingCallbackForPrompt(
					aReportLines,
					provider,
					requestRecord,
					nextRequestRecord,
					COMPLETE_RESPONSE_CALLBACK_INVOKED,
					"complete response callback invoked with merged streaming response");
		}
	}

	private TraceRecord recordAtOccurrence(
			String aProvider,
			StreamingMode aMode,
			String anEventName,
			int anOccurrence) {
		List<TraceRecord> records = records(aProvider, aMode, anEventName);
		if (anOccurrence < 0 || anOccurrence >= records.size()) {
			return null;
		}
		return records.get(anOccurrence);
	}

	private void addStreamingCallbackForPrompt(
			List<String> aReportLines,
			String aProvider,
			TraceRecord aRequestRecord,
			TraceRecord aNextRequestRecord,
			String anEventName,
			String aLabel) {
		List<TraceRecord> records =
				recordsBetween(
						aProvider,
						StreamingMode.STREAMING,
						anEventName,
						aRequestRecord,
						aNextRequestRecord);
		String level =
				records.isEmpty()
						? ERROR_MESSAGE_PREFIX
						: INFO_MESSAGE_PREFIX;
		aReportLines.add(
				"  "
						+ level
						+ " "
						+ aLabel
						+ " check="
						+ checkOutcome(level, records.isEmpty() ? "false" : "true")
						+ " provider="
						+ aProvider
						+ " count="
						+ records.size()
						+ " files="
						+ callbackTraceFiles(aProvider, records)
						+ " evidence="
						+ callbackEvidence(records, aRequestRecord));
	}

	private List<String> callbackTraceFiles(
			String aProvider,
			List<TraceRecord> aRecords) {
		List<String> files = traceFiles(aRecords);
		if (!files.isEmpty()) {
			return files;
		}
		return traceFiles(
				records(aProvider, StreamingMode.STREAMING));
	}

	private String callbackEvidence(
			List<TraceRecord> aRecords,
			TraceRecord aRequestRecord) {
		if (!aRecords.isEmpty()) {
			return lineReportRangeRefs(aRecords);
		}
		if (aRequestRecord == null) {
			return "[]";
		}
		return "searched after "
				+ lineReportRangeRefs(List.of(aRequestRecord));
	}

	private List<TraceRecord> recordsBetween(
			String aProvider,
			StreamingMode aMode,
			String anEventName,
			TraceRecord aStartRecord,
			TraceRecord anEndRecord) {
		ArrayList<TraceRecord> result = new ArrayList<>();
		if (aStartRecord == null) {
			return result;
		}
		int startSequence = aStartRecord.getId().sequenceNumber();
		int endSequence = anEndRecord == null
				? Integer.MAX_VALUE
				: anEndRecord.getId().sequenceNumber();
		for (TraceRecord record : records(aProvider, aMode, anEventName)) {
			int sequence = record.getId().sequenceNumber();
			if (sequence > startSequence && sequence < endSequence) {
				result.add(record);
			}
		}
		return result;
	}

	private List<String> traceFiles(List<TraceRecord> aRecords) {
		ArrayList<String> result = new ArrayList<>();
		for (TraceRecord record : aRecords) {
			addIfAbsent(result, traceFileName(traceLineLocation(record)));
		}
		return result;
	}

	private String lineReportRefs(List<TraceRecord> aRecords) {
		Map<String, Integer> numbers = lineReportNumbersByLocation();
		ArrayList<Integer> refs = new ArrayList<>();
		for (TraceRecord record : aRecords) {
			addIfAbsent(refs, numbers.get(traceLineLocation(record)));
		}
		return reportRefs("LineBasedTraceReport", refs);
	}

	private String lineReportRangeRefs(List<TraceRecord> aRecords) {
		Map<String, Integer> numbers = lineReportNumbersByLocation();
		ArrayList<Integer> refs = new ArrayList<>();
		for (TraceRecord record : aRecords) {
			addIfAbsent(refs, numbers.get(traceLineLocation(record)));
		}
		if (refs.isEmpty()) {
			return "[]";
		}
		if (refs.size() == 1) {
			return reportRef("LineBasedTraceReport", refs.get(0));
		}
		return reportRef("LineBasedTraceReport", refs.get(0))
				+ ".."
				+ reportRef(
						"LineBasedTraceReport",
						refs.get(refs.size() - 1))
				+ " count="
				+ refs.size();
	}

	private void addPromptCheckerMessages(
			List<String> aReportLines,
			String aLabel,
			List<String> aMessages) {
		Map<String, List<String>> filesByStatus = new LinkedHashMap<>();
		Map<String, List<Integer>> evidenceByStatus = new LinkedHashMap<>();
		for (String prefixedMessage : aMessages) {
			String message = checkerMessageText(prefixedMessage);
			String status = checkerStatus(prefixedMessage);
			filesByStatus.computeIfAbsent(
					status,
					ignored -> new ArrayList<>());
			evidenceByStatus.computeIfAbsent(
					status,
					ignored -> new ArrayList<>());
			addCheckerMessageFiles(filesByStatus.get(status), message);
			addIfAbsent(
					evidenceByStatus.get(status),
					deduplicatedReportNumber(prefixedMessage));
		}
		for (Map.Entry<String, List<String>> entry :
				filesByStatus.entrySet()) {
			aReportLines.add(
					"  "
							+ entry.getKey()
							+ " "
							+ aLabel
							+ " check="
							+ checkOutcome(
									entry.getKey(),
									checkOutcomeValue(
											aMessages,
											entry.getKey()))
							+ " files="
							+ entry.getValue()
							+ " evidence="
							+ reportRefs(
									"DeduplicatedTraceReport",
									evidenceByStatus.get(entry.getKey())));
		}
	}

	private void addProviderGenericMatchesForPrompt(
			List<String> aReportLines,
			List<String> aPromptMessages,
			Map<String, List<String>> aProviderGenericByTraceLine) {
		ArrayList<String> matchedMessages = new ArrayList<>();
		for (String prefixedMessage : aPromptMessages) {
			String traceLine =
					checkerFieldValue(
							checkerMessageText(prefixedMessage),
							"traceLine");
			matchedMessages.addAll(
					aProviderGenericByTraceLine.getOrDefault(
							traceLine,
							List.of()));
		}
		addPromptCheckerMessages(
				aReportLines,
				"provider-generic context-window match at prompt request",
				matchedMessages);
	}

	private void addExpectedCompactionForPrompt(
			List<String> aReportLines,
			String aPromptId) {
		ArrayList<String> compactionMessages = new ArrayList<>();
		for (String message : checkerMessages) {
			if (EXPECTED_CONTEXT_WINDOW_COMPACTION.equals(checkName(message))
					&& message.contains("expectedPromptId=" + aPromptId)) {
				compactionMessages.add(message);
			}
		}
		addPromptCheckerMessages(
				aReportLines,
				"expected context-window compaction before this prompt",
				compactionMessages);
	}

	private String checkerMessageText(String aPrefixedMessage) {
		if (aPrefixedMessage == null) {
			return "";
		}
		return aPrefixedMessage.replaceFirst("^[IWE]\\*{2,3}\\s+", "");
	}

	private String checkerStatus(String aPrefixedMessage) {
		if (aPrefixedMessage == null) {
			return UNCHECKED_MESSAGE_PREFIX;
		}
		if (aPrefixedMessage.startsWith(ERROR_MESSAGE_PREFIX)) {
			return ERROR_MESSAGE_PREFIX;
		}
		if (aPrefixedMessage.startsWith(WARNING_MESSAGE_PREFIX)) {
			return WARNING_MESSAGE_PREFIX;
		}
		return INFO_MESSAGE_PREFIX;
	}

	private String checkOutcome(String aStatus, String aMatchValue) {
		if (ERROR_MESSAGE_PREFIX.equals(aStatus)) {
			return "failed";
		}
		if ("false".equals(aMatchValue)) {
			return "failed";
		}
		if ("checked=false".equals(aMatchValue)) {
			return "not_checked";
		}
		return "passed";
	}

	private String checkOutcomeValue(
			List<String> aMessages,
			String aStatus) {
		for (String message : aMessages) {
			if (!aStatus.equals(checkerStatus(message))) {
				continue;
			}
			if (message.contains("checked=false")) {
				return "checked=false";
			}
			if (message.contains("match=false")
					|| message.contains(
							"previousLastInCurrentContextWindow=false")) {
				return "false";
			}
		}
		return "true";
	}

	private String traceFileName(String aTraceLineLocation) {
		if (isBlank(aTraceLineLocation)) {
			return "";
		}
		String location = aTraceLineLocation;
		int arrow = location.indexOf("->");
		if (arrow >= 0) {
			location = location.substring(0, arrow);
		}
		int colon = location.lastIndexOf(':');
		if (colon > 0) {
			location = location.substring(0, colon);
		}
		int slash = Math.max(location.lastIndexOf('/'), location.lastIndexOf('\\'));
		if (slash >= 0) {
			location = location.substring(slash + 1);
		}
		return location;
	}

	private void addCheckerMessageFiles(
			List<String> aFiles,
			String aMessage) {
		addIfAbsent(
				aFiles,
				traceFileName(checkerFieldValue(aMessage, "traceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						traceFileForCheckerMessage(aMessage)));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "previousTraceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "currentTraceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "baselineTraceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "comparedTraceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "firstChunkTraceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "lastChunkTraceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "mergedTraceLine")));
		addIfAbsent(
				aFiles,
				traceFileName(
						checkerFieldValue(aMessage, "contextTraceLine")));
	}

	private String traceFileForCheckerMessage(String aMessage) {
		String provider = checkerFieldValue(aMessage, "provider");
		String modeText = checkerFieldValue(aMessage, "mode");
		if (isBlank(provider) || isBlank(modeText)) {
			return "";
		}
		List<String> files;
		if (StreamingMode.STREAMING.name().equals(modeText)) {
			files = traceFiles(records(provider, StreamingMode.STREAMING));
			return files.isEmpty() ? "" : files.get(0);
		}
		if (StreamingMode.NON_STREAMING.name().equals(modeText)) {
			files = traceFiles(records(provider, StreamingMode.NON_STREAMING));
			return files.isEmpty() ? "" : files.get(0);
		}
		return "";
	}

	private void addIfAbsent(List<String> aValues, String aValue) {
		if (!isBlank(aValue) && !aValues.contains(aValue)) {
			aValues.add(aValue);
		}
	}

	private void addIfAbsent(List<Integer> aValues, Integer aValue) {
		if (aValue != null && !aValues.contains(aValue)) {
			aValues.add(aValue);
		}
	}

	private Integer deduplicatedReportNumber(String aPrefixedMessage) {
		String message = checkerMessageText(aPrefixedMessage);
		TraceProcessorMessageLevel level =
				levelFromPrefixedMessage(aPrefixedMessage);
		CheckerMessageGroup group =
				checkerMessageGroups.get(checkerMessageKey(level, message));
		if (group == null) {
			return null;
		}
		return group.groupedMessageIndex + 1;
	}

	private TraceProcessorMessageLevel levelFromPrefixedMessage(
			String aPrefixedMessage) {
		if (aPrefixedMessage != null
				&& aPrefixedMessage.startsWith(ERROR_MESSAGE_PREFIX)) {
			return TraceProcessorMessageLevel.ERROR;
		}
		if (aPrefixedMessage != null
				&& aPrefixedMessage.startsWith(WARNING_MESSAGE_PREFIX)) {
			return TraceProcessorMessageLevel.WARNING;
		}
		return TraceProcessorMessageLevel.INFO;
	}

	private String reportValue(Object aValue) {
		String value = String.valueOf(aValue);
		value = value.replace("\r", "\\r").replace("\n", "\\n");
		int maxLength = 300;
		if (value.length() > maxLength) {
			return value.substring(0, maxLength - 3) + "...";
		}
		return value;
	}

	private String listValue(List<?> aValues) {
		if (aValues == null || aValues.isEmpty()) {
			return "[]";
		}
		ArrayList<String> values = new ArrayList<>();
		for (Object value : aValues) {
			values.add(reportValue(value));
		}
		return values.toString();
	}

	private String compactListValue(List<?> aValues) {
		if (aValues == null || aValues.isEmpty()) {
			return "[]";
		}
		int maxValues = 8;
		ArrayList<String> values = new ArrayList<>();
		int displayed = Math.min(maxValues, aValues.size());
		for (int i = 0; i < displayed; i++) {
			values.add(reportValue(aValues.get(i)));
		}
		if (aValues.size() > maxValues) {
			values.add("... " + (aValues.size() - maxValues) + " more");
		}
		return values.toString();
	}

	private List<String> derivationsForTag(String aTag) {
		ArrayList<String> result = new ArrayList<>();
		if (isBlank(aTag)) {
			return result;
		}
		for (List<ClassTagDerivation> derivations :
				classTagDerivations.values()) {
			for (ClassTagDerivation derivation : derivations) {
				if (aTag.equals(derivation.tag())) {
					result.add(derivation.message());
				}
			}
		}
		return result;
	}

	private Map<String, Integer> lineReportNumbersByLocation() {
		Map<String, Integer> result = new LinkedHashMap<>();
		int index = 1;
		for (TraceRecord record : traceRecords.values()) {
			result.put(traceLineLocation(record), index++);
		}
		return result;
	}

	private List<Integer> lineReportEvidenceNumbers(
			String aMessage,
			Map<String, Integer> aLineReportNumbers) {
		ArrayList<Integer> result = new ArrayList<>();
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile(
								"(?:^|[=:\\[,>])"
										+ "([^\\s,\\[\\]=:]*Trace"
										+ "[^\\s,\\[\\]=]*\\.txt:\\d+)")
						.matcher(aMessage == null ? "" : aMessage);
		while (matcher.find()) {
			Integer number = aLineReportNumbers.get(matcher.group(1));
			if (number != null && !result.contains(number)) {
				result.add(number);
			}
		}
		return result;
	}

	private Map<String, List<Integer>> deduplicatedEntriesByPrompt() {
		Map<String, List<Integer>> result = new LinkedHashMap<>();
		for (int i = 0; i < groupedCheckerMessages.size(); i++) {
			String message = groupedCheckerMessages.get(i);
			for (String promptId : promptIds(message)) {
				result.computeIfAbsent(
						"Prompt " + promptId,
						ignored -> new ArrayList<>()).add(i + 1);
			}
		}
		return result;
	}

	private List<String> promptIds(String aMessage) {
		ArrayList<String> result = new ArrayList<>();
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile("expectedPromptId=([^,\\s]+)")
						.matcher(aMessage == null ? "" : aMessage);
		while (matcher.find()) {
			String promptId = matcher.group(1);
			if (!result.contains(promptId)) {
				result.add(promptId);
			}
		}
		return result;
	}

	private Map<String, List<Integer>> deduplicatedEntriesByCheck() {
		Map<String, List<Integer>> result = new LinkedHashMap<>();
		for (int i = 0; i < groupedCheckerMessages.size(); i++) {
			String message = groupedCheckerMessages.get(i);
			result.computeIfAbsent(
					checkName(message),
					ignored -> new ArrayList<>()).add(i + 1);
		}
		return result;
	}

	private String checkName(String aMessage) {
		if (aMessage == null || aMessage.isBlank()) {
			return "unknown_check";
		}
		String[] parts = aMessage.split("\\s+", 3);
		if (parts.length >= 2) {
			return parts[1];
		}
		return aMessage.trim();
	}

	private int countMessagesWithPrefix(String aPrefix) {
		int result = 0;
		for (String message : groupedCheckerMessages) {
			if (message.startsWith(aPrefix)) {
				result++;
			}
		}
		return result;
	}

	private String reportRefs(
			String aReportName,
			List<Integer> aReportLines) {
		ArrayList<String> refs = new ArrayList<>();
		for (Integer line : aReportLines) {
			refs.add(reportRef(aReportName, line));
		}
		return String.join(", ", refs);
	}

	private String compactReportRefs(
			String aReportName,
			List<Integer> aReportLines) {
		if (aReportLines == null || aReportLines.isEmpty()) {
			return "[]";
		}
		int maxRefs = 8;
		ArrayList<String> refs = new ArrayList<>();
		int displayed = Math.min(maxRefs, aReportLines.size());
		for (int i = 0; i < displayed; i++) {
			refs.add(reportRef(aReportName, aReportLines.get(i)));
		}
		if (aReportLines.size() > maxRefs) {
			refs.add("... " + (aReportLines.size() - maxRefs) + " more");
		}
		return refs.toString();
	}

	private String reportRef(
			String aReportName,
			int aReportLine) {
		return aReportName + "[" + aReportLine + "]";
	}

	private void writeClassRegistryRows(Path aClassRegistryFile)
			throws IOException {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, Set<String>> entry : classTags.entrySet()) {
			for (String tag : entry.getValue()) {
				builder.append(csv(entry.getKey()))
						.append(',')
						.append(csv(tag))
						.append(System.lineSeparator());
			}
		}
		Files.writeString(
				aClassRegistryFile,
				builder.toString(),
				StandardCharsets.UTF_8);
	}

	private String csv(String aValue) {
		String value = aValue == null ? "" : aValue;
		if (value.contains(",")
				|| value.contains("\"")
				|| value.contains("\n")
				|| value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private void recordCheckerMessage(String aMessage) {
		recordCheckerMessage(inferredLevel(aMessage), aMessage);
	}

	private void recordError(TraceFilesProcessorError anError) {
		errors.add(anError);
		recordCheckerMessage(
				TraceProcessorMessageLevel.ERROR,
				"processor_error error="
						+ anError
						+ " provider="
						+ currentProvider
						+ " mode="
						+ traceMode(currentTraceIsStreaming));
	}

	private void recordTraceFileError(
			TraceFilesProcessorError anError,
			Path aTraceFile) {
		errors.add(anError);
		recordCheckerMessage(
				TraceProcessorMessageLevel.ERROR,
				"trace_file_error error="
						+ anError
						+ " mode="
						+ traceMode(currentTraceIsStreaming)
						+ " traceFile="
						+ reportValue(aTraceFile));
	}

	private void recordCheckerMessage(
			TraceProcessorMessageLevel aLevel,
			String aMessage) {
		if (aLevel == TraceProcessorMessageLevel.WARNING) {
			aLevel = TraceProcessorMessageLevel.ERROR;
		}
		checkerMessages.add(aLevel.prefix() + " " + aMessage);
		recordGroupedCheckerMessage(aLevel, aMessage);
	}

	private void recordGroupedCheckerMessage(
			TraceProcessorMessageLevel aLevel,
			String aMessage) {
		String key = checkerMessageKey(aLevel, aMessage);
		CheckerMessageGroup group = checkerMessageGroups.get(key);
		if (group == null) {
			group =
					new CheckerMessageGroup(
							aLevel,
							checkerMessageBase(aMessage),
							groupedCheckerMessages.size());
			checkerMessageGroups.put(key, group);
			groupedCheckerMessages.add(group.formattedMessage());
		}
		group.count++;
		addCheckerTraceLineEvidence(group, aMessage);
		groupedCheckerMessages.set(
				group.groupedMessageIndex,
				group.formattedMessage());
	}

	private String checkerMessageKey(
			TraceProcessorMessageLevel aLevel,
			String aMessage) {
		return aLevel.name() + ":" + checkerMessageBase(aMessage);
	}

	private String checkerMessageBase(String aMessage) {
		String result = aMessage == null ? "" : aMessage;
		List<String> fieldsToRemove =
				new ArrayList<>(
						List.of(
								"provider",
								"mode",
								"baselineProvider",
								"comparedProvider",
								"previousTraceLine",
								"currentTraceLine",
								"traceLine",
								"firstChunkTraceLine",
								"lastChunkTraceLine",
								"mergedTraceLine",
								"contextTraceLine",
								"baselineTraceLine",
								"comparedTraceLine",
								"occurrence",
								"expectedPromptId",
								"expectedPromptIndex",
								"expectedRole",
								"expectedCompactionBeforePromptIndex",
								"promptPreview",
								"detail",
								"providerIndependentMessages",
								"providerDependentMessages",
								"baselineCount",
								"comparedCount",
								"composedCharacters",
								"secondLastCharacters",
								"observedPrefixBytes",
								"comparedBytes"));
		for (String field :
				fieldsToRemove) {
			result =
					result.replaceAll(
							"\\s+"
									+ field
									+ "=(?:\"[^\"]*\"|[^\\s]*)",
							"");
		}
		return result.trim().replaceAll("\\s+", " ");
	}

	private void addCheckerTraceLineEvidence(
			CheckerMessageGroup aGroup,
			String aMessage) {
		String evidence = checkerTraceLineEvidence(aMessage);
		if (!isBlank(evidence) && !aGroup.traceLines.contains(evidence)) {
			aGroup.traceLines.add(evidence);
		}
	}

	private String checkerTraceLineEvidence(String aMessage) {
		String context = checkerEvidenceContext(aMessage);
		String traceLineEvidence = checkerTraceLineOnlyEvidence(aMessage);
		if (isBlank(context)) {
			return traceLineEvidence;
		}
		if (isBlank(traceLineEvidence)) {
			return context;
		}
		return context + ":" + traceLineEvidence;
	}

	private String checkerEvidenceContext(String aMessage) {
		List<String> parts = new ArrayList<>();
		addCheckerEvidenceField(parts, aMessage, "provider");
		addCheckerEvidenceField(parts, aMessage, "mode");
		addCheckerEvidenceField(parts, aMessage, "baselineProvider");
		addCheckerEvidenceField(parts, aMessage, "comparedProvider");
		addCheckerEvidenceField(parts, aMessage, "occurrence");
		addCheckerEvidenceField(parts, aMessage, "expectedPromptId");
		addCheckerEvidenceField(parts, aMessage, "expectedRole");
		addCheckerEvidenceField(
				parts,
				aMessage,
				"expectedCompactionBeforePromptIndex");
		addCheckerEvidenceField(parts, aMessage, "detail");
		addCheckerEvidenceField(
				parts,
				aMessage,
				"providerIndependentMessages");
		addCheckerEvidenceField(
				parts,
				aMessage,
				"providerDependentMessages");
		addCheckerEvidenceField(parts, aMessage, "baselineCount");
		addCheckerEvidenceField(parts, aMessage, "comparedCount");
		addCheckerEvidenceField(parts, aMessage, "composedCharacters");
		addCheckerEvidenceField(parts, aMessage, "secondLastCharacters");
		addCheckerEvidenceField(parts, aMessage, "observedPrefixBytes");
		addCheckerEvidenceField(parts, aMessage, "comparedBytes");
		return String.join(",", parts);
	}

	private void addCheckerEvidenceField(
			List<String> aParts,
			String aMessage,
			String aFieldName) {
		String value = checkerFieldValue(aMessage, aFieldName);
		if (!isBlank(value)) {
			aParts.add(aFieldName + "=" + value);
		}
	}

	private String checkerTraceLineOnlyEvidence(String aMessage) {
		String previous = checkerFieldValue(aMessage, "previousTraceLine");
		String current = checkerFieldValue(aMessage, "currentTraceLine");
		if (!isBlank(previous) || !isBlank(current)) {
			return previous + "->" + current;
		}
		String baseline = checkerFieldValue(aMessage, "baselineTraceLine");
		String compared = checkerFieldValue(aMessage, "comparedTraceLine");
		if (!isBlank(baseline) || !isBlank(compared)) {
			return baseline + "->" + compared;
		}
		String firstChunk = checkerFieldValue(aMessage, "firstChunkTraceLine");
		String lastChunk = checkerFieldValue(aMessage, "lastChunkTraceLine");
		String merged = checkerFieldValue(aMessage, "mergedTraceLine");
		String context = checkerFieldValue(aMessage, "contextTraceLine");
		if (!isBlank(firstChunk)
				|| !isBlank(lastChunk)
				|| !isBlank(merged)
				|| !isBlank(context)) {
			return "firstChunk="
					+ firstChunk
					+ ",lastChunk="
					+ lastChunk
					+ ",merged="
					+ merged
					+ ",context="
					+ context;
		}
		return checkerFieldValue(aMessage, "traceLine");
	}

	private String checkerFieldValue(String aMessage, String aFieldName) {
		if (aMessage == null) {
			return "";
		}
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern
						.compile(
								"(?:^|\\s)"
										+ java.util.regex.Pattern.quote(
												aFieldName)
										+ "=(\"[^\"]*\"|[^\\s]*)")
						.matcher(aMessage);
		if (!matcher.find()) {
			return "";
		}
		String value = matcher.group(1);
		if (value.length() >= 2
				&& value.startsWith("\"")
				&& value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private TraceProcessorMessageLevel inferredLevel(String aMessage) {
		if (isUncheckedMessage(aMessage)) {
			return levelFor(
					uncheckedCheckMessagesAreWarnings,
					uncheckedCheckMessagesAreErrors);
		}
		if (isFalseCheckMessage(aMessage)) {
			return levelFor(
					falseCheckMessagesAreWarnings,
					falseCheckMessagesAreErrors);
		}
		return levelFor(
				trueCheckMessagesAreWarnings,
				trueCheckMessagesAreErrors);
	}

	private TraceProcessorMessageLevel levelFor(
			boolean isWarning,
			boolean isError) {
		if (isError) {
			return TraceProcessorMessageLevel.ERROR;
		}
		if (isWarning) {
			return TraceProcessorMessageLevel.WARNING;
		}
		return TraceProcessorMessageLevel.INFO;
	}

	private boolean isUncheckedMessage(String aMessage) {
		return aMessage != null && aMessage.contains("checked=false");
	}

	private boolean isFalseCheckMessage(String aMessage) {
		return aMessage != null
				&& (aMessage.contains("match=false")
						|| aMessage.contains(
								"previousLastInCurrentContextWindow=false"));
	}

	private String preview(String aText) {
		if (aText == null) {
			return "";
		}
		String singleLine =
				aText.replace("\r", "\\r").replace("\n", "\\n");
		if (singleLine.length() <= 80) {
			return singleLine.replace("\"", "'");
		}
		return singleLine.substring(0, 77).replace("\"", "'")
				+ "...";
	}

	private boolean isBlank(String aText) {
		return aText == null || aText.isBlank();
	}
}
