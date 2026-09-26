package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eduAI.trace.lib.StreamingMode;

public class ClassRegistryFromTraceTest {
	private static final boolean TRACE = true;
	private static final Path DEFAULT_TRACE_FILE =
			Path.of("TraceGeminiBridgeStreamingDemo.txt");
	private static final Path DEFAULT_CLASS_REGISTRY_FILE =
			Path.of("ClassRegistry.csv");

	public static void main(String[] args) {
		ClassRegistryFromTraceTest test =
				new ClassRegistryFromTraceTest();
		programGoal(
				TRACE,
				test,
				"test Assignment1 class-tag registry generation from traces");
		run(
				TRACE,
				test,
				"testCreatesClassRegistryFromDefaultStreamingTrace",
				() -> test.runCreatesClassRegistryFromDefaultStreamingTrace());
		run(
				TRACE,
				test,
				"testDerivesFactoriesAndInstancesFromRegistrationTraces",
				() -> test.runDerivesFactoriesAndInstancesFromRegistrationTraces());
		run(
				TRACE,
				test,
				"testClassRegistryAllowsMultipleTagsForOneClass",
				() -> test.runClassRegistryAllowsMultipleTagsForOneClass());
		run(
				TRACE,
				test,
				"testClassTagChecksReportDerivationEvidence",
				() -> test.runClassTagChecksReportDerivationEvidence());
		run(
				TRACE,
				test,
				"testNonStreamingModeIgnoresStreamingOnlyTraceLines",
				() -> test.runNonStreamingModeIgnoresStreamingOnlyTraceLines());
		run(
				TRACE,
				test,
				"testMissingTraceFilesReturnEnumErrors",
				() -> test.runMissingTraceFilesReturnEnumErrors());
		run(
				TRACE,
				test,
				"testCheckTraceFilesExistReturnsEnumErrors",
				() -> test.runCheckTraceFilesExistReturnsEnumErrors());
		run(
				TRACE,
				test,
				"testBridgeProcessorUsesConfiguredTraceDirectory",
				() -> test.runBridgeProcessorUsesConfiguredTraceDirectory());
		run(
				TRACE,
				test,
				"testTraceRecordsHaveIdsAndExtractedData",
				() -> test.runTraceRecordsHaveIdsAndExtractedData());
		run(
				TRACE,
				test,
				"testContextWindowContinuityIsAttachedToRequestRecords",
				() -> test.runContextWindowContinuityIsAttachedToRequestRecords());
		run(
				TRACE,
				test,
				"testRequestSentContextWindowMatchIsReported",
				() -> test.runRequestSentContextWindowMatchIsReported());
		run(
				TRACE,
				test,
				"testStreamingChunksAreReflectedInNextContextWindow",
				() -> test.runStreamingChunksAreReflectedInNextContextWindow());
		run(
				TRACE,
				test,
				"testProviderIndependentNonStreamingDataComparedByTraceKind",
				() -> test.runProviderIndependentNonStreamingDataComparedByTraceKind());
		run(
				TRACE,
				test,
				"testChecksUseOnlyRequiredTraceEvents",
				() -> new TraceCheckDependencyTest()
						.testChecksUseOnlyRequiredTraceEvents());
	}

	private void runCreatesClassRegistryFromDefaultStreamingTrace() {
		try {
			testCreatesClassRegistryFromDefaultStreamingTrace();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testCreatesClassRegistryFromDefaultStreamingTrace()
			throws IOException {
		trace("read the default streaming trace");
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Gemini",
				new GenericTracesFileProcessor.TraceFiles(
						DEFAULT_TRACE_FILE,
						Path.of("TraceGeminiBridgeNonStreamingDemo.txt")));
		GenericTracesFileProcessor processor =
				newTraceFilesProcessor(
						traces,
						new BridgeDemoTraceFilesProcessor()
								.bridgeDemoExpectedInputs());
		List<TraceFilesProcessorError> errors =
				processor.generateClassRegistry(
						DEFAULT_CLASS_REGISTRY_FILE);
		trace("verify the class registry file was written");
		if (!errors.isEmpty()) {
			throw new AssertionError("Unexpected errors: " + errors);
		}
		if (!Files.exists(DEFAULT_CLASS_REGISTRY_FILE)) {
			throw new AssertionError(
					"Expected " + DEFAULT_CLASS_REGISTRY_FILE);
		}
		List<String> rows =
				Files.readAllLines(
						DEFAULT_CLASS_REGISTRY_FILE,
						StandardCharsets.UTF_8);
		assertContainsTag(rows, "GenericContextWindowMessage");
		assertContainsTag(rows, "ParameterStore");
		assertContainsTag(rows, "GeminiChunkCallbackAdapter");
		assertDoesNotContainTag(rows, "ElizaServerHandleFactory");
		assertDoesNotContainTag(rows, "TabularServerHandleFactory");
		assertDoesNotContainTag(rows, "OpenAIServerHandleFactory");
		assertExpectedInputsMatched(processor);
	}

	private void runDerivesFactoriesAndInstancesFromRegistrationTraces() {
		try {
			testDerivesFactoriesAndInstancesFromRegistrationTraces();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testDerivesFactoriesAndInstancesFromRegistrationTraces()
			throws IOException {
		trace("create a small registration trace fixture");
		Path fixture = Path.of(
				"build",
				"ClassRegistryRegistrationFixture.txt");
		Path registry = Path.of(
				"build",
				"ClassRegistryRegistrationFixture.csv");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {server_handle_factory_set_for_provider} [none: main] (single_model_request_processing: student.impl.ProviderFactoryRegistryInitializer) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryInterfaces=\"[student.api.SpecificAIClientFactory]\" provider=\"Provider\" serverHandleFactory=\"serverHandleFactory: student.impl.ProviderClientFactory interfaces=[student.api.SpecificAIClientFactory] toString=\\\"factory\\\" fields={client: null}\"> ##",
						"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <provider=\"Provider\" serverHandleFactoryRegistryClass=\"student.impl.ProviderClientFactoryRegistry\" serverHandleFactoryRegistryInterfaces=\"[student.api.ServerHandleFactoryRegistry]\" serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryInterfaces=\"[student.api.SpecificAIClientFactory]\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\"> ##",
						"## lib {server_handle_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\" serverHandleClass=\"student.impl.ProviderClient\" serverHandleInterfaces=\"[]\" serverHandleIdentity=\"student.impl.ProviderClient@1\" serverHandle=\"serverHandle: student.impl.ProviderClient interfaces=[] toString=\\\"client\\\" fields={}\"> ##",
						streamChunkMergerFactoryFetchedTraceLine(),
						streamingChunkTraceLine("chunk"),
						mergedMessageTraceLine("merged"),
						"## lib {request_sent} [none: main] (single_model_request_processing: student.impl.ProviderClient) <provider=\"Provider\" providerIndependentParameterStore=\"providerIndependentParameterStore: student.impl.ProviderParameterStore interfaces=[student.api.ParameterStore] toString=\\\"store\\\" fields={}\" providerIndependentContextWindow=\"providerIndependentContextWindow: java.util.ArrayList interfaces=[java.util.List] toString=\\\"[message]\\\" elements=[[0]: student.impl.ProviderMessage interfaces=[student.api.AIMessage] toString=\\\"message\\\" fields={}]\"> ##",
						"## lib {message_translated} [none: main] (single_model_request_processing: student.impl.ProviderPromptMessageAdapter) <provider=\"Provider\" providerIndependentMessage=\"message\" providerDependentMessage=\"nativeMessage\"> ##",
						"## lib {response_translated} [none: main] (single_model_request_processing: student.impl.ProviderResponseMessageAdapter) <provider=\"Provider\" providerDependentResponse=\"nativeResponse\" providerIndependentResponse=\"providerIndependentResponse: student.impl.ProviderMessage interfaces=[student.api.AIMessage] toString=\\\"message\\\" fields={}\"> ##",
						"## lib {parameter_translated} [none: main] (single_model_request_processing: student.impl.ProviderRequestPropertyHandlerRegistrar) <parameterHandlerClass=\"eduAI.trace.processors._tests.ClassRegistryFromTraceTest.NamedParameterHandler\" parameterHandlerInterfaces=\"[student.api.PropertyHandler]\" propertyName=\"temperature\" parameterHandler=\"parameterAdapter: eduAI.trace.processors._tests.ClassRegistryFromTraceTest.NamedParameterHandler interfaces=[student.api.PropertyHandler] toString=\\\"eduAI.trace.processors._tests.ClassRegistryFromTraceTest$NamedParameterHandler@1\\\" fields={}\"> ##",
						streamingCallbackTraceLine(
								"partial_response_callback_invoked",
								"chunk"),
						streamingCallbackTraceLine(
								"complete_response_callback_invoked",
								"merged")),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(fixture, fixture));
		GenericTracesFileProcessor processor =
				newTraceFilesProcessor(traces);
		List<TraceFilesProcessorError> errors =
				processor.generateClassRegistry(registry);
		if (!errors.isEmpty()) {
			throw new AssertionError("Unexpected errors: " + errors);
		}
		if (!processor.getStreamingCallbackInvocationResults()
				.equals(List.of(true, true))) {
			throw new AssertionError(
					"Unexpected callback check results: "
							+ processor
									.getStreamingCallbackInvocationResults());
		}
		List<String> rows =
				Files.readAllLines(registry, StandardCharsets.UTF_8);
		assertContainsRow(
				rows,
				"student.impl.ProviderClientFactory",
				"ProviderServerHandleFactory");
		assertContainsRow(
				rows,
				"student.impl.ProviderClientFactoryRegistry",
				"ServerHandleFactoryRegistry");
		assertContainsRow(
				rows,
				"student.impl.ProviderClient",
				"ProviderServerHandleAdapter");
		assertContainsRow(
				rows,
				NamedParameterHandler.class.getName(),
				"ProviderParameterAdapter");
		assertRegistryClassLoads(rows, NamedParameterHandler.class.getName());
		assertContainsRow(
				rows,
				"student.impl.ProviderChunkCallbackAdapter",
				"ProviderChunkCallbackAdapter");
		assertContainsRow(
				rows,
				"student.impl.ProviderMessage",
				"GenericContextWindowMessage");
		assertContainsRow(
				rows,
				"student.impl.StreamChunkMergerRegistry",
				"StreamChunkMergerFactoryRegistry");
		assertContainsRow(
				rows,
				"student.impl.StreamChunkMergerFactory",
				"StreamChunkMergerFactory");
		assertContainsRow(
				rows,
				"student.impl.StreamChunkMerger",
				"StreamChunkMerger");
	}

	private void assertRegistryClassLoads(
			List<String> someRows,
			String anExpectedClassName) {
		for (String row : someRows) {
			if (!row.startsWith(anExpectedClassName + ",")) {
				continue;
			}
			try {
				Class.forName(row.substring(0, row.indexOf(',')));
				return;
			} catch (ClassNotFoundException e) {
				throw new AssertionError(
						"Generated registry class name is not loadable: " + row,
						e);
			}
		}
		throw new AssertionError(
				"Expected registry class row: " + anExpectedClassName);
	}

	private static class NamedParameterHandler {
	}

	private void runClassRegistryAllowsMultipleTagsForOneClass() {
		try {
			testClassRegistryAllowsMultipleTagsForOneClass();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testClassRegistryAllowsMultipleTagsForOneClass()
			throws IOException {
		trace("create a trace fixture with one class playing two roles");
		Path fixture = Path.of(
				"build",
				"ClassRegistryMultiTagFixture.txt");
		Path registry = Path.of(
				"build",
				"ClassRegistryMultiTagFixture.csv");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <provider=\"Provider\" serverHandleFactoryRegistryClass=\"student.impl.Registry\" serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\"> ##",
						"## lib {server_handle_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\" serverHandleClass=\"student.impl.MultiRole\" serverHandleIdentity=\"student.impl.MultiRole@1\"> ##",
						"## lib {parameter_translated} [none: main] (single_model_request_processing: student.impl.ProviderRequestPropertyHandlerRegistrar) <parameterHandlerClass=\"student.impl.MultiRole\" provider=\"Provider\" propertyName=\"temperature\"> ##"),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(null, fixture));
		GenericTracesFileProcessor processor =
				newTraceFilesProcessor(traces);
		processor.generateClassRegistry(registry);
		List<String> rows =
				Files.readAllLines(registry, StandardCharsets.UTF_8);
		assertContainsRow(
				rows,
				"student.impl.MultiRole",
				"ProviderServerHandleAdapter");
		assertContainsRow(
				rows,
				"student.impl.MultiRole",
				"ProviderParameterAdapter");
		if (!processor.classesForTag("ProviderServerHandleAdapter")
				.contains("student.impl.MultiRole")) {
			throw new AssertionError(
					"Expected MultiRole for ProviderServerHandleAdapter");
		}
		if (!processor.classesForTag("ProviderParameterAdapter")
				.contains("student.impl.MultiRole")) {
			throw new AssertionError(
					"Expected MultiRole for ProviderParameterAdapter");
		}
		List<String> tags =
				processor.tagsForClass("student.impl.MultiRole");
		if (!tags.contains("ProviderServerHandleAdapter")
				|| !tags.contains("ProviderParameterAdapter")) {
			throw new AssertionError(
					"Expected both tags for MultiRole: " + tags);
		}
	}

	private void runClassTagChecksReportDerivationEvidence() {
		try {
			testClassTagChecksReportDerivationEvidence();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testClassTagChecksReportDerivationEvidence()
			throws IOException {
		trace("create a trace fixture for tag derivation messages");
		Path fixture = Path.of(
				"build",
				"ClassRegistryTagDerivationFixture.txt");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <provider=\"Provider\" serverHandleFactoryRegistryClass=\"student.impl.Registry\" serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\"> ##",
						"## lib {server_handle_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\" serverHandleClass=\"student.impl.MultiRole\" serverHandleIdentity=\"student.impl.MultiRole@1\"> ##",
						"## lib {parameter_translated} [none: main] (single_model_request_processing: student.impl.ProviderRequestPropertyHandlerRegistrar) <parameterHandlerClass=\"student.impl.MultiRole\" provider=\"Provider\" propertyName=\"temperature\"> ##"),
				StandardCharsets.UTF_8);
		GenericTracesFileProcessor processor =
				newTraceFilesProcessor(Map.of());
		processor.processTraceFile(
				"Provider",
				StreamingMode.NON_STREAMING,
				fixture);
		processor.checkClassExistsForTag("ProviderServerHandleAdapter");
		processor.checkClassExistsForTag("MissingParameterAdapter");
		processor.checkClassHasMultipleTags("student.impl.MultiRole");
		if (!processor.getErrors()
				.contains(TraceFilesProcessorError.MISSING_CLASS_FOR_TAG)) {
			throw new AssertionError(
					"Expected missing class tag error: "
							+ processor.getErrors());
		}
		assertCheckerMessage(
				processor,
				"class_tag_derived",
				"serverHandleClass");
		assertCheckerMessage(
				processor,
				"class_tag_derived",
				"parameterHandlerClass");
		assertCheckerMessage(
				processor,
				"class_tag_missing",
				"expectedTraceEvents=[parameter_translated");
		assertCheckerMessage(
				processor,
				"class_tag_multiplicity",
				"tagCount=2");
		List<String> derivations =
				processor.classTagDerivations("student.impl.MultiRole");
		if (derivations.size() != 2) {
			throw new AssertionError(
					"Expected two MultiRole derivations: "
							+ derivations);
		}
	}

	private void runNonStreamingModeIgnoresStreamingOnlyTraceLines() {
		try {
			testNonStreamingModeIgnoresStreamingOnlyTraceLines();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testNonStreamingModeIgnoresStreamingOnlyTraceLines()
			throws IOException {
		trace("create a small non-streaming trace fixture");
		Path fixture = Path.of(
				"build",
				"ClassRegistryNonStreamingFixture.txt");
		Path registry = Path.of(
				"build",
				"ClassRegistryNonStreamingFixture.csv");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {request_sent} [none: main] (single_model_request_processing: student.impl.ProviderClient) <provider=\"Provider\" providerIndependentParameterStore=\"providerIndependentParameterStore: student.impl.ProviderParameterStore interfaces=[student.api.ParameterStore] toString=\\\"store\\\" fields={}\" providerIndependentContextWindow=\"providerIndependentContextWindow: java.util.ArrayList interfaces=[java.util.List] toString=\\\"[message]\\\" elements=[[0]: student.impl.ProviderMessage interfaces=[student.api.AIMessage] toString=\\\"message\\\" fields={}]\"> ##",
						"## lib {response_translated} [none: main] (single_model_request_processing: student.impl.ProviderClient) <provider=\"Provider\" providerDependentResponse=\"nativeResponse\" providerIndependentResponse=\"providerIndependentResponse: student.impl.ProviderMessage interfaces=[student.api.AIMessage] toString=\\\"message\\\" fields={}\"> ##",
						streamingCallbackTraceLine(
								"partial_response_callback_invoked",
								"AIMessage")),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(null, fixture));
		List<TraceFilesProcessorError> errors =
				newTraceFilesProcessor(traces)
						.generateClassRegistry(registry);
		if (errors.contains(
				TraceFilesProcessorError.MISSING_NON_STREAMING_TRACE_FILE)) {
			throw new AssertionError("Unexpected errors: " + errors);
		}
		List<String> rows =
				Files.readAllLines(registry, StandardCharsets.UTF_8);
		assertContainsRow(
				rows,
				"student.impl.ProviderParameterStore",
				"ParameterStore");
		assertContainsRow(
				rows,
				"student.impl.ProviderMessage",
				"GenericContextWindowMessage");
		assertDoesNotContainTag(rows, "ProviderChunkCallbackAdapter");
	}

	private void runMissingTraceFilesReturnEnumErrors() {
		testMissingTraceFilesReturnEnumErrors();
	}

	public void testMissingTraceFilesReturnEnumErrors() {
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(
						Path.of("build", "missing-streaming.txt"),
						Path.of("build", "missing-non-streaming.txt")));
		List<TraceFilesProcessorError> errors =
				newTraceFilesProcessor(traces)
						.generateClassRegistry(
								Path.of(
										"build",
										"MissingTraceRegistry.csv"));
		if (!errors.contains(
				TraceFilesProcessorError.MISSING_STREAMING_TRACE_FILE)
				|| !errors.contains(
						TraceFilesProcessorError
								.MISSING_NON_STREAMING_TRACE_FILE)) {
			throw new AssertionError("Expected missing-file errors: "
					+ errors);
		}
	}

	private void runCheckTraceFilesExistReturnsEnumErrors() {
		testCheckTraceFilesExistReturnsEnumErrors();
	}

	public void testCheckTraceFilesExistReturnsEnumErrors() {
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(
						Path.of("build", "missing-streaming-direct.txt"),
						Path.of("build", "missing-non-streaming-direct.txt")));
		GenericTracesFileProcessor processor =
				newTraceFilesProcessor(traces);
		TraceCheckResult result =
				processor.checkTraceFilesExist();
		if (!result.errors().contains(
				TraceFilesProcessorError.MISSING_STREAMING_TRACE_FILE)
				|| !result.errors().contains(
						TraceFilesProcessorError
								.MISSING_NON_STREAMING_TRACE_FILE)) {
			throw new AssertionError(
					"Expected direct missing-file errors: "
							+ result.errors());
		}
		assertCheckerMessage(
				processor,
				"trace_file_error",
				"mode=STREAMING");
		assertCheckerMessage(
				processor,
				"trace_file_error",
				"mode=NON_STREAMING");
		assertCheckerMessage(
				processor,
				"trace_file_error",
				"missing-streaming-direct.txt");
		assertCheckerMessage(
				processor,
				"trace_file_error",
				"missing-non-streaming-direct.txt");
	}

	private void runBridgeProcessorUsesConfiguredTraceDirectory() {
		try {
			testBridgeProcessorUsesConfiguredTraceDirectory();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testBridgeProcessorUsesConfiguredTraceDirectory()
			throws IOException {
		Path traceDirectory = Path.of(
				"build",
				"configured-trace-directory");
		Files.createDirectories(traceDirectory);
		for (String traceFileName : List.of(
				"TraceGeminiBridgeStreamingDemo.txt",
				"TraceGeminiBridgeNonStreamingDemo.txt",
				"TraceOllamaStreamingBridgeDemo.txt",
				"TraceOllamaNonStreamingBridgeDemo.txt")) {
			Files.writeString(
					traceDirectory.resolve(traceFileName),
					"",
					StandardCharsets.UTF_8);
		}
		BridgeDemoTraceFilesProcessor processor =
				new BridgeDemoTraceFilesProcessor();
		processor.setTraceDirectory(traceDirectory.toString());
		TraceCheckResult result =
				processor.checkTraceFilesExist();
		if (result.errors().contains(
				TraceFilesProcessorError.MISSING_STREAMING_TRACE_FILE)
				|| result.errors().contains(
						TraceFilesProcessorError
								.MISSING_NON_STREAMING_TRACE_FILE)
				|| result.errors().contains(
						TraceFilesProcessorError.UNREADABLE_TRACE_FILE)) {
			throw new AssertionError(
					"Expected configured trace directory files to exist: "
							+ result.errors());
		}
	}

	private void runTraceRecordsHaveIdsAndExtractedData() {
		try {
			testTraceRecordsHaveIdsAndExtractedData();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testTraceRecordsHaveIdsAndExtractedData()
			throws IOException {
		trace("create a trace fixture for record extraction");
		Path fixture = Path.of(
				"build",
				"TraceRecordFixture.txt");
		Path registry = Path.of(
				"build",
				"TraceRecordFixture.csv");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {request_sent} [none: main] (single_model_request_processing: student.impl.ProviderClient) <provider=\"Provider\" providerIndependentParameterStore=\"providerIndependentParameterStore: student.impl.ProviderParameterStore interfaces=[student.api.ParameterStore] toString=\\\"store\\\" fields={}\" providerIndependentContextWindow=\"providerIndependentContextWindow: java.util.ArrayList interfaces=[java.util.List] toString=\\\"[]\\\" elements=[]\"> ##",
						streamingCallbackTraceLine(
								"partial_response_callback_invoked",
								"AIMessage"),
						streamingCallbackTraceLine(
								"complete_response_callback_invoked",
								"AIMessage")),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(fixture, fixture));
		GenericTracesFileProcessor processor = newTraceFilesProcessor(traces);
		processor.generateClassRegistry(registry);
		TraceRecord requestRecord =
				processor.getTraceRecords()
						.get(
								new TraceRecordId(
										"Provider",
										StreamingMode.NON_STREAMING,
										0));
		if (requestRecord == null) {
			throw new AssertionError("Expected non-streaming record 0");
		}
		if (!requestRecord.getExtractedData()
				.containsKey("providerIndependentParameterStore")) {
			throw new AssertionError(
					"Expected request extracted data");
		}
		TraceRecord callbackRecord =
				processor.getTraceRecords()
						.get(
								new TraceRecordId(
										"Provider",
										StreamingMode.STREAMING,
										1));
		if (callbackRecord == null) {
			throw new AssertionError("Expected streaming record 1");
		}
		Object adapterClass =
				callbackRecord.getExtractedData()
						.get("chunkCallbackAdapterClass");
		if (!"student.impl.ProviderChunkCallbackAdapter"
				.equals(adapterClass)) {
			throw new AssertionError(
					"Expected callback adapter extraction: "
							+ adapterClass);
		}
	}

	private void runContextWindowContinuityIsAttachedToRequestRecords() {
		try {
			testContextWindowContinuityIsAttachedToRequestRecords();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testContextWindowContinuityIsAttachedToRequestRecords()
			throws IOException {
		trace("create a trace fixture for context-window continuity");
		Path fixture = Path.of(
				"build",
				"ContextWindowContinuityFixture.txt");
		Path registry = Path.of(
				"build",
				"ContextWindowContinuityFixture.csv");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {server_handle_factory_set_for_provider} [none: main] (single_model_request_processing: student.impl.ProviderFactoryRegistryInitializer) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" provider=\"Provider\"> ##",
						"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <provider=\"Provider\" serverHandleFactoryRegistryClass=\"student.impl.ProviderClientFactoryRegistry\" serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\"> ##",
						"## lib {server_handle_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\" serverHandleClass=\"student.impl.ProviderClient\" serverHandleIdentity=\"student.impl.ProviderClient@1\"> ##",
						"## lib {message_translated} [none: main] (single_model_request_processing: student.impl.ProviderPromptMessageAdapter) <provider=\"Provider\" providerIndependentMessage=\"message\" providerDependentMessage=\"nativeMessage\"> ##",
						requestTraceLine(
								"Provider",
								"USER",
								"First",
								"ASSISTANT",
								"Second"),
						requestTraceLine(
								"Provider",
								"ASSISTANT",
								"Second",
								"USER",
								"Third"),
						requestTraceLine(
								"Provider",
								"USER",
								"Compacted",
								"ASSISTANT",
								"Fourth"),
						requestTraceLine(
								"Provider",
								"ASSISTANT",
								"Fourth",
								"USER",
								"Fifth"),
						"## lib {response_translated} [none: main] (single_model_request_processing: student.impl.ProviderClient) <provider=\"Provider\" providerDependentResponse=\"nativeResponse\" providerIndependentResponse=\"providerIndependentResponse: student.impl.ProviderMessage interfaces=[student.api.AIMessage] toString=\\\"AIMessage(role=ASSISTANT, thinking=\\\\\\\"\\\\\\\", parts=[0:text(\\\\\\\"done\\\\\\\")])\\\" fields={}\"> ##",
						"## lib {parameter_translated} [none: main] (single_model_request_processing: student.impl.ProviderRequestPropertyHandlerRegistrar) <parameterHandlerClass=\"student.impl.TemperatureHandler\" parameterHandlerInterfaces=\"[student.api.PropertyHandler]\" provider=\"Provider\" propertyName=\"temperature\"> ##"),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(fixture, fixture));
		GenericTracesFileProcessor processor = newTraceFilesProcessor(traces);
		processor.generateClassRegistry(registry);
		List<Boolean> continuity =
				processor.getContextWindowContinuityResults();
		if (!continuity.equals(
				List.of(true, false, true, true, false, true))) {
			throw new AssertionError(
					"Unexpected continuity results: " + continuity);
		}
	}

	private String requestTraceLine(
			String aProvider,
			String aFirstRole,
			String aFirstMessage,
			String aSecondRole,
			String aSecondMessage) {
		return "## lib {request_sent} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.ProviderClient) <provider=\""
				+ aProvider
				+ "\" providerIndependentContextWindow=\""
				+ "providerIndependentContextWindow: java.util.ArrayList "
				+ "interfaces=[java.util.List] toString=\\\"[]\\\" "
				+ "elements=[[0]: "
				+ providerMessageObjectDump(aFirstRole, aFirstMessage)
				+ ", [1]: "
				+ providerMessageObjectDump(aSecondRole, aSecondMessage)
				+ "]\"> ##";
	}

	private void runRequestSentContextWindowMatchIsReported() {
		try {
			testRequestSentContextWindowMatchIsReported();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testRequestSentContextWindowMatchIsReported()
			throws IOException {
		trace("create a trace fixture for intra-request context-window matching");
		Path fixture = Path.of(
				"build",
				"RequestSentContextWindowMatchFixture.txt");
		Path registry = Path.of(
				"build",
				"RequestSentContextWindowMatchFixture.csv");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {server_handle_factory_set_for_provider} [none: main] (single_model_request_processing: student.impl.ProviderFactoryRegistryInitializer) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" provider=\"Provider\"> ##",
						"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <provider=\"Provider\" serverHandleFactoryRegistryClass=\"student.impl.Registry\" serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\"> ##",
						"## lib {server_handle_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\" serverHandleClass=\"student.impl.ProviderClient\" serverHandleIdentity=\"student.impl.ProviderClient@1\"> ##",
						matchingRequestSentTraceLine(),
						mismatchingRequestSentTraceLine(),
						"## lib {response_translated} [none: main] (single_model_request_processing: student.impl.ProviderClient) <provider=\"Provider\" providerDependentResponse=\"nativeResponse\" providerIndependentResponse=\"providerIndependentResponse: student.impl.ProviderMessage interfaces=[student.api.AIMessage] toString=\\\"AIMessage(role=ASSISTANT, thinking=\\\\\\\"\\\\\\\", parts=[0:text(\\\\\\\"done\\\\\\\")])\\\" fields={}\"> ##",
						"## lib {parameter_translated} [none: main] (single_model_request_processing: student.impl.ProviderRequestPropertyHandlerRegistrar) <parameterHandlerClass=\"student.impl.TemperatureHandler\" parameterHandlerInterfaces=\"[student.api.PropertyHandler]\" provider=\"Provider\" propertyName=\"temperature\"> ##"),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(fixture, fixture));
		GenericTracesFileProcessor processor = newTraceFilesProcessor(traces);
		processor.generateClassRegistry(registry);
		if (!processor.getRequestContextWindowMatchResults()
				.equals(List.of(true, false, true, false))) {
			throw new AssertionError(
					"Unexpected request context matches: "
							+ processor
									.getRequestContextWindowMatchResults());
		}
		boolean foundMessage = false;
		for (String message : processor.getGroupedCheckerMessages()) {
			if (message.contains(
					GenericTracesFileProcessor
							.PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH)
					&& message.contains(
							"build\\RequestSentContextWindowMatchFixture.txt:3")
					&& message.contains("match=true")) {
				foundMessage = true;
			}
		}
		if (!foundMessage) {
			throw new AssertionError(
					"Expected request-sent context match feedback");
		}
	}

	private void runStreamingChunksAreReflectedInNextContextWindow() {
		try {
			testStreamingChunksAreReflectedInNextContextWindow();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testStreamingChunksAreReflectedInNextContextWindow()
			throws IOException {
		trace("create a trace fixture for streaming chunk composition");
		Path fixture = Path.of(
				"build",
				"StreamingChunkContextWindowFixture.txt");
		Path registry = Path.of(
				"build",
				"StreamingChunkContextWindowFixture.csv");
		Files.createDirectories(fixture.getParent());
		Files.writeString(
				fixture,
				String.join(
						System.lineSeparator(),
						"## lib {server_handle_factory_set_for_provider} [none: main] (single_model_request_processing: student.impl.ProviderFactoryRegistryInitializer) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" provider=\"Provider\"> ##",
						"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <provider=\"Provider\" serverHandleFactoryRegistryClass=\"student.impl.ProviderClientFactoryRegistry\" serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\"> ##",
						"## lib {server_handle_fetched} [none: main] (single_model_request_processing: student.impl.ProviderClientFactory) <serverHandleFactoryClass=\"student.impl.ProviderClientFactory\" serverHandleFactoryIdentity=\"student.impl.ProviderClientFactory@1\" serverHandleClass=\"student.impl.ProviderClient\" serverHandleIdentity=\"student.impl.ProviderClient@1\"> ##",
						"## lib {message_translated} [none: main] (single_model_request_processing: student.impl.ProviderPromptMessageAdapter) <provider=\"Provider\" providerIndependentMessage=\"message\" providerDependentMessage=\"nativeMessage\"> ##",
						requestTraceLine(
								"Provider",
								"SYSTEM",
								"System",
								"USER",
								"Question"),
						streamChunkMergerFactoryFetchedTraceLine(),
						streamingChunkTraceLine("Hello "),
						streamingCallbackTraceLine(
								"partial_response_callback_invoked",
								"Hello "),
						streamingChunkTraceLine("world"),
						streamingCallbackTraceLine(
								"partial_response_callback_invoked",
								"world"),
						mergedMessageTraceLine("Hello world"),
						streamingCallbackTraceLine(
								"complete_response_callback_invoked",
								"Hello world"),
						requestTraceLine(
								"Provider",
								"ASSISTANT",
								"Hello world",
								"USER",
								"Next"),
						responseTraceLine("Hello world"),
						"## lib {response_translated} [none: main] (single_model_request_processing: student.impl.ProviderResponseMessageAdapter) <provider=\"Provider\" providerDependentResponse=\"nativeResponse\" providerIndependentResponse=\"message\"> ##",
						"## lib {parameter_translated} [none: main] (single_model_request_processing: student.impl.ProviderRequestPropertyHandlerRegistrar) <parameterHandlerClass=\"student.impl.TemperatureHandler\" parameterHandlerInterfaces=\"[student.api.PropertyHandler]\" provider=\"Provider\" propertyName=\"temperature\"> ##"),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"Provider",
				new GenericTracesFileProcessor.TraceFiles(fixture, fixture));
		GenericTracesFileProcessor processor = newTraceFilesProcessor(traces);
		List<TraceFilesProcessorError> errors =
				processor.generateClassRegistry(registry);
		if (!errors.isEmpty()) {
			throw new AssertionError("Unexpected errors: " + errors);
		}
		if (!processor.getStreamingChunkContextWindowMatchResults()
				.equals(List.of(true))) {
			throw new AssertionError(
					"Unexpected streaming chunk/context matches: "
							+ processor
									.getStreamingChunkContextWindowMatchResults());
		}
		boolean foundMessage = false;
		for (String message : processor.getCheckerMessages()) {
			if (message.contains(
					GenericTracesFileProcessor
							.STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW)
					&& message.contains("match=true")) {
				foundMessage = true;
			}
		}
		if (!foundMessage) {
			throw new AssertionError(
					"Expected streaming chunk/context feedback");
		}
	}

	private void runProviderIndependentNonStreamingDataComparedByTraceKind() {
		try {
			testProviderIndependentNonStreamingDataComparedByTraceKind();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public void testProviderIndependentNonStreamingDataComparedByTraceKind()
			throws IOException {
		trace("create paired non-streaming traces for provider-independent comparison");
		Path firstFixture = Path.of(
				"build",
				"ProviderIndependentFirstFixture.txt");
		Path secondFixture = Path.of(
				"build",
				"ProviderIndependentSecondFixture.txt");
		Path registry = Path.of(
				"build",
				"ProviderIndependentComparisonFixture.csv");
		Files.createDirectories(firstFixture.getParent());
		Files.writeString(
				firstFixture,
				String.join(
						System.lineSeparator(),
						commonSetup("First"),
						requestTraceLine(
								"First",
								"SYSTEM",
								"System",
								"USER",
								"Question"),
						responseTraceLine("First answer"),
						requestTraceLine(
								"First",
								"ASSISTANT",
								"First answer",
								"USER",
								"Next"),
						responseTraceLine("Different first response"),
						parameterAdapterTraceLine("First")),
				StandardCharsets.UTF_8);
		Files.writeString(
				secondFixture,
				String.join(
						System.lineSeparator(),
						commonSetup("Second"),
						requestTraceLine(
								"Second",
								"SYSTEM",
								"System",
								"USER",
								"Question"),
						responseTraceLine("Second answer"),
						requestTraceLine(
								"Second",
								"ASSISTANT",
								"Second answer",
								"USER",
								"Next"),
						responseTraceLine("Different second response"),
						parameterAdapterTraceLine("Second")),
				StandardCharsets.UTF_8);
		Map<String, GenericTracesFileProcessor.TraceFiles> traces =
				new LinkedHashMap<>();
		traces.put(
				"First",
				new GenericTracesFileProcessor.TraceFiles(null, firstFixture));
		traces.put(
				"Second",
				new GenericTracesFileProcessor.TraceFiles(null, secondFixture));
		GenericTracesFileProcessor processor = newTraceFilesProcessor(traces);
		processor.generateClassRegistry(registry);
		List<Boolean> matches =
				processor.getProviderIndependentNonStreamingMatchResults();
		if (!matches.equals(List.of(true, true, true, true))) {
			throw new AssertionError(
					"Unexpected provider-independent matches: "
							+ matches);
		}
		assertCheckerMessage(
				processor,
				GenericTracesFileProcessor
						.PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS
						+ " event=request_sent occurrence=0",
				"match=true");
		assertCheckerMessage(
				processor,
				GenericTracesFileProcessor
						.PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS
						+ " event=response_translated occurrence=0",
				"match=true");
	}

	private String commonSetup(String aProvider) {
		return String.join(
				System.lineSeparator(),
				"## lib {server_handle_factory_fetched} [none: main] (single_model_request_processing: student.impl."
						+ aProvider
						+ "ClientFactory) <provider=\""
						+ aProvider
						+ "\" serverHandleFactoryRegistryClass=\"student.impl.ServerHandleFactoryRegistry\" serverHandleFactoryClass=\"student.impl."
						+ aProvider
						+ "ClientFactory\" serverHandleFactoryIdentity=\"student.impl."
						+ aProvider
						+ "ClientFactory@1\"> ##",
				"## lib {server_handle_fetched} [none: main] (single_model_request_processing: student.impl."
						+ aProvider
						+ "ClientFactory) <serverHandleFactoryClass=\"student.impl."
						+ aProvider
						+ "ClientFactory\" serverHandleFactoryIdentity=\"student.impl."
						+ aProvider
						+ "ClientFactory@1\" serverHandleClass=\"student.impl."
						+ aProvider
						+ "Client\" serverHandleIdentity=\"student.impl."
						+ aProvider
						+ "Client@1\"> ##");
	}

	private String responseTraceLine(String aText) {
		return "## lib {response_translated} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.ProviderResponseMessageAdapter) "
				+ "<provider=\"Provider\" "
				+ "providerDependentResponse=\"native response\" "
				+ "providerIndependentResponse=\""
				+ providerMessageDump(
						"providerIndependentResponse",
						"ASSISTANT",
						aText)
				+ "\"> ##";
	}

	private String parameterAdapterTraceLine(String aProvider) {
		return "## lib {parameter_translated} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.ProviderRequestPropertyHandlerRegistrar) "
				+ "<parameterHandlerClass=\"student.impl."
				+ aProvider
				+ "TemperatureHandler\" "
				+ "parameterHandlerInterfaces=\"[student.api.PropertyHandler]\" "
				+ "provider=\""
				+ aProvider
				+ "\" propertyName=\"temperature\"> ##";
	}

	private String streamingChunkTraceLine(String aText) {
		return "## lib {streaming_chunk_accumulated} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.StreamChunkMerger) <messageMergerClass=\""
				+ "student.impl.StreamChunkMerger\" streamingChunk=\""
				+ providerMessageDump("streamingChunk", "ASSISTANT", aText)
				+ "\" accumulatedMessage=\""
				+ providerMessageDump(
						"accumulatedMessage",
						"ASSISTANT",
						aText)
				+ "\" messageMerger=\"messageMerger: "
				+ "student.impl.StreamChunkMerger "
				+ "interfaces=[student.api.StreamChunkMerger] "
				+ "toString=\\\"merger\\\" fields={text: "
				+ "java.lang.String value=\\\""
				+ aText
				+ "\\\"}"
				+ "\"> ##";
	}

	private String streamingCallbackTraceLine(
			String anEventName,
			String aMessageText) {
		return "## lib {"
				+ anEventName
				+ "} [none: main] (single_model_request_processing: "
				+ "student.impl.ProviderChunkCallbackAdapter) "
				+ "<streamingCallbackClass=\""
				+ "student.impl.PrintingStreamingHandler\" "
				+ "streamingCallbackInterfaces=\""
				+ "[student.api.AIResponseStreamingHandler]\" "
				+ "streamingCallbackMethods=\""
				+ "[void onCompletion(student.api.Message), "
				+ "void onIncrement(student.api.Message)]\" "
				+ "streamingCallback=\"streamingCallback: "
				+ "student.impl.PrintingStreamingHandler "
				+ "interfaces=[student.api.AIResponseStreamingHandler] "
				+ "toString=\\\"handler\\\" fields={}\" "
				+ "callbackArgument=\""
				+ providerMessageDump(
						"callbackArgument",
						"ASSISTANT",
						aMessageText)
				+ "\"> ##";
	}

	private String streamChunkMergerFactoryFetchedTraceLine() {
		return "## lib {message_merger_factory_fetched} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.ProviderClient) <provider=\"Provider\" "
				+ "streamChunkMergerFactoryRegistryClass=\""
				+ "student.impl.StreamChunkMergerRegistry\" "
				+ "streamChunkMergerFactoryRegistryInterfaces=\""
				+ "[student.api.StreamChunkMergerFactoryRegistry]\" "
				+ "streamChunkMergerFactoryRegistry=\""
				+ "streamChunkMergerFactoryRegistry: "
				+ "student.impl.StreamChunkMergerRegistry "
				+ "interfaces=[student.api.StreamChunkMergerFactoryRegistry] "
				+ "toString=\\\"registry\\\" fields={}\" "
				+ "streamChunkMergerFactoryClass=\""
				+ "student.impl.StreamChunkMergerFactory\" "
				+ "streamChunkMergerFactoryInterfaces=\""
				+ "[student.api.StreamChunkMergerFactory]\" "
				+ "streamChunkMergerFactoryMethods=\""
				+ "[student.api.StreamChunkMerger "
				+ "createStreamChunkMerger()]\" "
				+ "streamChunkMergerFactory=\""
				+ "streamChunkMergerFactory: "
				+ "student.impl.StreamChunkMergerFactory "
				+ "interfaces=[student.api.StreamChunkMergerFactory] "
				+ "toString=\\\"factory\\\" fields={}\"> ##";
	}

	private String mergedMessageTraceLine(String aText) {
		return "## lib {streaming_chunks_merged} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.ProviderMessageMerger) <mergedMessage=\""
				+ providerMessageDump("mergedMessage", "ASSISTANT", aText)
				+ "\"> ##";
	}

	private String contextWindowMessageAddedTraceLine(
			int anIndex,
			String aRole,
			String aText) {
		return "## lib {context_window_message_added} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.ProviderDemo) <index=\""
				+ anIndex
				+ "\" contextWindowMessage=\""
				+ providerMessageDump("contextWindowMessage", aRole, aText)
				+ "\"> ##";
	}

	private String providerMessageDump(
			String anArgumentName,
			String aRole,
			String aText) {
		return anArgumentName + ": " + providerMessageObjectDump(aRole, aText);
	}

	private String providerMessageObjectDump(
			String aRole,
			String aText) {
		return "student.impl.ProviderMessage "
				+ "interfaces=[student.api.Message] "
				+ "toString=\\\"message\\\" fields={role: "
				+ "student.api.Role value="
				+ aRole
				+ ", parts: java.util.ArrayList "
				+ "interfaces=[java.util.List] elements=[[0]: "
				+ "student.impl.TextPart interfaces=[student.api.MessagePart] "
				+ "fields={text: java.lang.String value=\\\""
				+ aText
				+ "\\\"}]}";
	}

	private GenericTracesFileProcessor newTraceFilesProcessor(
			Map<String, GenericTracesFileProcessor.TraceFiles> aTraces) {
		return newTraceFilesProcessor(
				aTraces,
				new GenericTracesFileProcessor.ExpectedInputs(List.of(), null));
	}

	private GenericTracesFileProcessor newTraceFilesProcessor(
			Map<String, GenericTracesFileProcessor.TraceFiles> aTraces,
			GenericTracesFileProcessor.ExpectedInputs anExpectedInputs) {
		return new NativeTraceFilesProcessor(aTraces, anExpectedInputs);
	}

	private String matchingRequestSentTraceLine() {
		return requestSentTraceLine("Hello", "Hello");
	}

	private String mismatchingRequestSentTraceLine() {
		return requestSentTraceLine("Hello", "Different");
	}

	private String requestSentTraceLine(
			String aProviderIndependentText,
			String aProviderDependentText) {
		return "## lib {request_sent} [none: main] "
				+ "(single_model_request_processing: "
				+ "student.impl.ProviderClient) <provider=\"Provider\" "
				+ "providerIndependentContextWindow=\""
				+ "providerIndependentContextWindow: java.util.ArrayList "
				+ "interfaces=[java.util.List] toString=\\\"[]\\\" "
				+ "elements=[[0]: "
				+ providerMessageObjectDump(
						"USER", aProviderIndependentText)
				+ "]\" "
				+ "providerDependentContextWindow=\""
				+ "providerDependentContextWindow: java.util.ArrayList "
				+ "interfaces=[java.util.List] "
				+ "providerValue=[1] OllamaChatMessage(role=user, "
				+ "thinking=\\\"\\\", response=\\\""
				+ aProviderDependentText
				+ "\\\", images=0, imageBytes=0, toolCalls=[])\"> ##";
	}

	private void assertContainsRow(
			List<String> aRows,
			String aClassName,
			String aTag) {
		String row = aClassName + "," + aTag;
		if (!aRows.contains(row)) {
			throw new AssertionError("Expected row: " + row);
		}
	}

	private void assertExpectedInputsMatched(
			GenericTracesFileProcessor aProcessor) {
		for (Boolean result :
				aProcessor.getExpectedInputMatchResults()) {
			if (!Boolean.TRUE.equals(result)) {
				throw new AssertionError(
						"Expected demo input was not found: "
								+ aProcessor.getCheckerMessages());
			}
		}
	}

	private void assertCheckerMessage(
			GenericTracesFileProcessor aProcessor,
			String aFirstNeedle,
			String aSecondNeedle) {
		if (containsCheckerMessage(
				aProcessor.getCheckerMessages(),
				aFirstNeedle,
				aSecondNeedle)) {
			return;
		}
		throw new AssertionError(
				"Expected checker message containing: "
						+ aFirstNeedle
						+ " and "
						+ aSecondNeedle
						+ " in "
						+ aProcessor.getCheckerMessages());
	}

	private boolean containsCheckerMessage(
			List<String> aCheckerMessages,
			String... aNeedles) {
		for (String message : aCheckerMessages) {
			boolean found = true;
			for (String needle : aNeedles) {
				if (!message.contains(needle)) {
					found = false;
					break;
				}
			}
			if (found) {
				return true;
			}
		}
		return false;
	}

	private void assertContainsTag(
			List<String> aRows,
			String aTag) {
		for (String row : aRows) {
			if (row.endsWith("," + aTag)) {
				return;
			}
		}
		throw new AssertionError("Expected tag: " + aTag);
	}

	private void assertDoesNotContainTag(
			List<String> aRows,
			String aTag) {
		for (String row : aRows) {
			if (row.endsWith("," + aTag)) {
				throw new AssertionError(
						"Unexpected tag in row: " + row);
			}
		}
	}

	private void trace(String aMessage) {
		trace(TRACE, this, aMessage);
	}

	private static void programGoal(
			boolean isTrace,
			Object anObject,
			String aMessage) {
		if (isTrace) {
			System.out.println(
					"[main] "
							+ anObject.getClass().getSimpleName()
							+ System.lineSeparator()
							+ "[main]   goal: "
							+ aMessage);
		}
	}

	private static void run(
			boolean isTrace,
			Object anObject,
			String aName,
			Runnable aRunnable) {
		if (isTrace) {
			System.out.println("[main]   " + aName);
			System.out.println(
					"[main]     goal: "
							+ testNameToGoal(aName));
		}
		aRunnable.run();
	}

	private static void trace(
			boolean isTrace,
			Object anObject,
			String aMessage) {
		if (isTrace) {
			System.out.println("[main]     " + aMessage);
		}
	}

	private static String testNameToGoal(String aName) {
		String text = aName;
		if (text.startsWith("test") && text.length() > 4) {
			text = text.substring(4);
		}
		return text.replaceAll("([a-z])([A-Z])", "$1 $2")
				.toLowerCase();
	}
}
