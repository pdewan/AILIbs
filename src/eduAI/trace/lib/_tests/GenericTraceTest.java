package eduAI.trace.lib._tests;





import eduAI.trace.lib.LibTraceData;
import eduAI.trace.lib.LibTraceKeys;
import eduAI.trace.TraceData;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import eduAI.trace.GenericTrace;
import eduAI.trace.GenericTraceEmitterFactory;
import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;
import eduAI.trace.TraceEmitterFactorySelector;
import eduAI.trace.lib.LibDesignPattern;
import eduAI.trace.lib.LibThreadPattern;
import eduAI.trace.lib.LibTrace;
import eduAI.trace.lib.LibTraceActions;
import eduAI.trace.lib.LibTraceEvent;

public class GenericTraceTest {
	private static final boolean TRACE = true;

	public static void main(String[] args) {
		GenericTraceTest test = new GenericTraceTest();
		programGoal(
				TRACE,
				test,
				"test the new generic trace grammar and lib wrapper");
		run(
				TRACE,
				test,
				"testGenericTraceFormatParsesDelimitedFields",
				"generic trace lines expose layer, event, thread pattern, thread name, design pattern, source class, and auxiliary data",
				() -> test.testGenericTraceFormatParsesDelimitedFields());
		run(
				TRACE,
				test,
				"testLibTraceAddsOnlyLibLayer",
				"the lib wrapper adds the lib layer without inventing CLI command-number data",
				() -> test.testLibTraceAddsOnlyLibLayer());
		run(
				TRACE,
				test,
				"testLibTraceStaysQuietWhenTraceMessagingIsDisabled",
				"the lib wrapper does not print when trace_messaging is false",
				() -> test
						.testLibTraceStaysQuietWhenTraceMessagingIsDisabled());
		run(
				TRACE,
				test,
				"testLibTraceThreadPatternHelper",
				"the lib thread-pattern helper emits summary and related threads in generic trace grammar",
				() -> test.testLibTraceThreadPatternHelper());
		run(
				TRACE,
				test,
				"testLibTracePatternEmitsBothPatternNames",
				"the lib wrapper emits thread and design pattern names together",
				() -> test.testLibTracePatternEmitsBothPatternNames());
		run(
				TRACE,
				test,
				"testLibTraceCanUseGenericEmitter",
				"the lib wrapper can emit through the generic emitter without property gates",
				() -> test.testLibTraceCanUseGenericEmitter());
		run(
				TRACE,
				test,
				"testLibTraceLimitsRawInputAndSummary",
				"the lib wrapper limits long raw input and summary auxiliary values",
				() -> test.testLibTraceLimitsRawInputAndSummary());
		run(
				TRACE,
				test,
				"testServerHandleFactorySetForProviderTrace",
				"server_handle_factory_set_for_provider names the server-handle factory class",
				() -> test
						.testServerHandleFactorySetForProviderTrace());
		run(
				TRACE,
				test,
				"testServerHandleFactoryFetchedTrace",
				"server_handle_factory_fetched names provider and server-handle factory class",
				() -> test.testServerHandleFactoryFetchedTrace());
		run(
				TRACE,
				test,
				"testParameterTranslatedTrace",
				"parameter_translated names the handler, provider target, property name, and property value",
				() -> test.testParameterTranslatedTrace());
		run(
				TRACE,
				test,
				"testMetadataTranslatedTrace",
				"metadata_translated names the handler, provider source, property name, and property value",
				() -> test.testMetadataTranslatedTrace());
		run(
				TRACE,
				test,
				"testServerHandleFetchedTrace",
				"server_handle_fetched links the exact factory object to its returned server handle",
				() -> test.testServerHandleFetchedTrace());
		run(
				TRACE,
				test,
				"testServerHandleSelectedForProviderTrace",
				"server_handle_selected_for_provider names the selected provider server handle",
				() -> test.testServerHandleSelectedForProviderTrace());
		run(
				TRACE,
				test,
				"testServerHandleCreatedTrace",
				"server_handle_created names the provider/server handle class and value",
				() -> test.testServerHandleCreatedTrace());
		run(
				TRACE,
				test,
				"testModelNamesFetchedFromServerHandleTrace",
				"model_names_fetched_from_server_handle names the handle and its model names",
				() -> test.testModelNamesFetchedFromServerHandleTrace());
		run(
				TRACE,
				test,
				"testServerHandleSetForModelTrace",
				"server_handle_set_for_model names the model and supporting server handle",
				() -> test.testServerHandleSetForModelTrace());
		run(
				TRACE,
				test,
				"testServerHandleFetchedForModelTrace",
				"server_handle_fetched_for_model names the model and resolved server handle",
				() -> test.testServerHandleFetchedForModelTrace());
	}

	public void testGenericTraceFormatParsesDelimitedFields() {
		String oldName = Thread.currentThread().getName();
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			Thread.currentThread().setName("model sender");
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			GenericTrace.traceDesignPattern(
				LibDesignPattern.PROVIDER_EXECUTION,
				GenericTraceTest.class,
				LibTraceEvent.MODEL_SEND_STARTED,
				Map.of(
							"model",
							"llama3.2",
							"prompt",
							"compare <a> and \"b\""));
		} finally {
			System.setOut(oldOut);
			Thread.currentThread().setName(oldName);
		}
		String output = bytes.toString(StandardCharsets.UTF_8);
		String line = firstTraceLine(output);
		trace("formatted line: " + line);
		TraceLine parsed = TraceLineParser.parse(line);
		assertEquals("lib", parsed.getLayerName());
		assertEquals("model_send_started", parsed.getEventName());
		assertEquals(
				"none",
				parsed.getThreadPatternName());
		assertEquals("model sender", parsed.getThreadName());
		assertEquals(
				"provider_execution",
				parsed.getDesignPatternName());
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest",
				parsed.getSourceClassName());
		assertEquals(
				"compare <a> and \"b\"",
				parsed.getAuxiliaryData().get("prompt"));
	}

	public void testLibTraceAddsOnlyLibLayer() {
		enableTraceEvents(LibTraceEvent.MODEL_SEND_STARTED);
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			LibTrace.traceDesignPattern(
				LibDesignPattern.PROVIDER_EXECUTION,
				GenericTraceTest.class,
				LibTraceEvent.MODEL_SEND_STARTED,
				Map.of("model", "llama3.2"));
		} finally {
			System.setOut(oldOut);
		}
		String output = bytes.toString(StandardCharsets.UTF_8);
		trace("lib trace output: " + output);
		TraceLine parsed = TraceLineParser.parse(firstTraceLine(output));
		assertEquals("lib", parsed.getLayerName());
		assertEquals(null, parsed.getAuxiliaryData().get("layers"));
		assertEquals(null, parsed.getAuxiliaryData().get("command_number"));
		assertEquals("llama3.2", parsed.getAuxiliaryData().get("model"));
	}

	public void testLibTraceStaysQuietWhenTraceMessagingIsDisabled() {
		configureTraceEvents(false, List.of("all"));
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			LibTrace.traceDesignPattern(
				LibDesignPattern.PROVIDER_EXECUTION,
				GenericTraceTest.class,
				LibTraceEvent.MODEL_SEND_STARTED,
				Map.of("model", "llama3.2"));
		} finally {
			System.setOut(oldOut);
		}
		assertEquals("", bytes.toString(StandardCharsets.UTF_8));
	}

	public void testLibTraceThreadPatternHelper() {
		enableTraceEvents(LibTraceEvent.THREAD_PATTERN_OBSERVED);
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			LibTrace.traceThreadPattern(
				LibThreadPattern.CONTEXT_COMPACTION_THREADS,
				GenericTraceTest.class,
				LibTraceEvent.THREAD_PATTERN_OBSERVED,
				LibTraceData.threadPatternData(
						"queued context compaction",
						"context worker"));
		} finally {
			System.setOut(oldOut);
		}
		TraceLine parsed =
				TraceLineParser.parse(
						firstTraceLine(
								bytes.toString(StandardCharsets.UTF_8)));
		assertEquals("lib", parsed.getLayerName());
		assertEquals(
				"thread_pattern_observed",
				parsed.getEventName());
		assertEquals(
				"context_compaction_threads",
				parsed.getThreadPatternName());
		assertEquals("none", parsed.getDesignPatternName());
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest",
				parsed.getSourceClassName());
		assertEquals(
				"queued context compaction",
				parsed.getAuxiliaryData().get(LibTraceKeys.SUMMARY));
		assertEquals(
				"context worker",
				parsed.getAuxiliaryData().get(
						LibTraceKeys.RELATED_THREADS));
	}

	public void testLibTracePatternEmitsBothPatternNames() {
		enableTraceEvents(LibTraceEvent.MODEL_REQUEST_DELEGATED);
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			LibTrace.tracePattern(
				LibThreadPattern.MODEL_REQUEST_THREADS,
				LibDesignPattern.MESSAGE_PROCESSING,
				GenericTraceTest.class,
				LibTraceEvent.MODEL_REQUEST_DELEGATED,
				Map.of("model", "llama3.2"));
		} finally {
			System.setOut(oldOut);
		}
		TraceLine parsed =
				TraceLineParser.parse(
						firstTraceLine(
								bytes.toString(StandardCharsets.UTF_8)));
		assertEquals("lib", parsed.getLayerName());
		assertEquals(
				"model_request_threads",
				parsed.getThreadPatternName());
		assertEquals(
				"message_processing",
				parsed.getDesignPatternName());
	}

	public void testLibTraceCanUseGenericEmitter() {
		configureTraceEvents(false, List.of());
		TraceEmitterFactorySelector.setTraceEmitterFactory(
				new GenericTraceEmitterFactory());
		GenericTrace.setEnabled(true);
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			LibTrace.tracePattern(
				LibThreadPattern.MODEL_REQUEST_THREADS,
				LibDesignPattern.MESSAGE_PROCESSING,
				GenericTraceTest.class,
				LibTraceEvent.MODEL_REQUEST_DELEGATED,
				Map.of("model", "llama3.2"));
		} finally {
			System.setOut(oldOut);
			TraceEmitterFactorySelector.setTraceEmitterFactory(
					new GenericTraceEmitterFactory());
		}
		TraceLine parsed =
				TraceLineParser.parse(
						firstTraceLine(
								bytes.toString(StandardCharsets.UTF_8)));
		assertEquals("lib", parsed.getLayerName());
		assertEquals(
				"model_request_delegated",
				parsed.getEventName());
		assertEquals(
				"model_request_threads",
				parsed.getThreadPatternName());
		assertEquals(
				"message_processing",
				parsed.getDesignPatternName());
	}

	public void testLibTraceLimitsRawInputAndSummary() {
		enableTraceEvents(LibTraceEvent.RESPONSE_TRANSLATED);
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			LibTrace.traceDesignPattern(
				LibDesignPattern.MESSAGE_PROCESSING,
				GenericTraceTest.class,
				LibTraceEvent.RESPONSE_TRANSLATED,
				TraceData.limitedData(
						TraceData.data(
							LibTraceKeys.RAW_INPUT,
							"0123456789abcdef",
							LibTraceKeys.SUMMARY,
							"summary text beyond limit"),
						7));
		} finally {
			System.setOut(oldOut);
		}
		TraceLine parsed =
				TraceLineParser.parse(
						firstTraceLine(
								bytes.toString(StandardCharsets.UTF_8)));
		assertEquals(
				"0123456...",
				parsed.getAuxiliaryData().get(LibTraceKeys.RAW_INPUT));
		assertEquals(
				"summary...",
				parsed.getAuxiliaryData().get(LibTraceKeys.SUMMARY));
	}

	public void testServerHandleCreatedTrace() {
		enableTraceEvents(LibTraceEvent.SERVER_HANDLE_CREATED);
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions.traceServerHandleCreated(
								GenericTraceTest.class,
								"Gemini"));
		assertEquals("lib", parsed.getLayerName());
		assertEquals("server_handle_created", parsed.getEventName());
		assertEquals(
				"single_model_request_processing",
				parsed.getDesignPatternName());
		assertEquals(
				"java.lang.String",
				parsed.getAuxiliaryData().get("serverHandleClass"));
		assertEquals(
				"Gemini",
				parsed.getAuxiliaryData().get("server"));
	}

	public void testServerHandleFactorySetForProviderTrace() {
		enableTraceEvents(
				LibTraceEvent.SERVER_HANDLE_FACTORY_SET_FOR_PROVIDER);
		Object factory = new FakeClientFactory();
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions
								.traceServerHandleFactorySetForProvider(
										GenericTraceTest.class,
										factory));
		assertEquals(
				"server_handle_factory_set_for_provider",
				parsed.getEventName());
		assertEquals(null, parsed.getAuxiliaryData().get("provider"));
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeClientFactory",
				parsed.getAuxiliaryData().get(
						"serverHandleFactoryClass"));
		assertEquals(
				"[eduAI.trace.lib._tests.GenericTraceTest$FakeServerHandleFactoryInterface]",
				parsed.getAuxiliaryData().get(
						"serverHandleFactoryInterfaces"));
	}

	public void testServerHandleFactoryFetchedTrace() {
		enableTraceEvents(
				LibTraceEvent.SERVER_HANDLE_FACTORY_FETCHED);
		Object factory = new FakeClientFactory();
		Object registry = new FakeServerHandleFactoryRegistry();
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions
								.traceServerHandleFactoryFetched(
										GenericTraceTest.class,
										"Gemini",
										registry,
										factory));
		assertEquals(
				"server_handle_factory_fetched",
				parsed.getEventName());
		assertEquals(
				"Gemini",
				parsed.getAuxiliaryData().get("provider"));
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeServerHandleFactoryRegistry",
				parsed.getAuxiliaryData().get(
						"serverHandleFactoryRegistryClass"));
		assertEquals(
				"[eduAI.trace.lib._tests.GenericTraceTest$FakeServerHandleFactoryRegistryInterface]",
				parsed.getAuxiliaryData().get(
						"serverHandleFactoryRegistryInterfaces"));
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeClientFactory",
				parsed.getAuxiliaryData().get(
						"serverHandleFactoryClass"));
		assertEquals(
				"[eduAI.trace.lib._tests.GenericTraceTest$FakeServerHandleFactoryInterface]",
				parsed.getAuxiliaryData().get(
						"serverHandleFactoryInterfaces"));
	}

	public void testParameterTranslatedTrace() {
		enableTraceEvents(
				LibTraceEvent.PARAMETER_TRANSLATED);
		Object handler = new FakeParameterAdapter();
		Object target = new FakeProviderTarget();
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions
								.traceParameterTranslated(
										GenericTraceTest.class,
										"temperature",
										Double.valueOf(0.4d),
										handler,
										target));
		assertEquals(
				"parameter_translated",
				parsed.getEventName());
		assertHandlerTrace(
				parsed,
				"parameterHandlerClass",
				"parameterHandlerInterfaces",
				"providerDependentTargetClass",
				"providerDependentTargetInterfaces");
		assertEquals(
				"temperature",
				parsed.getAuxiliaryData().get("propertyName"));
		assertEquals(
				"0.4",
				parsed.getAuxiliaryData().get("propertyValue"));
	}

	public void testMetadataTranslatedTrace() {
		enableTraceEvents(
				LibTraceEvent.METADATA_TRANSLATED);
		Object handler = new FakeParameterAdapter();
		Object source = new FakeProviderTarget();
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions
								.traceMetadataTranslated(
										GenericTraceTest.class,
										"inputTokens",
										Integer.valueOf(12),
										handler,
										source));
		assertEquals(
				"metadata_translated",
				parsed.getEventName());
		assertHandlerTrace(
				parsed,
				"metadataHandlerClass",
				"metadataHandlerInterfaces",
				"providerDependentSourceClass",
				"providerDependentSourceInterfaces");
		assertEquals(
				"inputTokens",
				parsed.getAuxiliaryData().get("propertyName"));
		assertEquals(
				"12",
				parsed.getAuxiliaryData().get("propertyValue"));
	}

	public void testServerHandleFetchedTrace() {
		enableTraceEvents(
				LibTraceEvent.SERVER_HANDLE_FETCHED);
		Object serverHandle = new FakeServerHandle();
		Object serverHandleFactory = new FakeClientFactory();
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions.traceServerHandleFetched(
								GenericTraceTest.class,
								serverHandleFactory,
								serverHandle));
		assertEquals(
				"server_handle_fetched",
				parsed.getEventName());
		assertEquals(null, parsed.getAuxiliaryData().get("provider"));
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeClientFactory",
				parsed.getAuxiliaryData().get("serverHandleFactoryClass"));
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeServerHandle",
				parsed.getAuxiliaryData().get("serverHandleClass"));
		assertEquals(
				"[eduAI.trace.lib._tests.GenericTraceTest$FakeServerHandleInterface]",
				parsed.getAuxiliaryData().get(
						"serverHandleInterfaces"));
	}

	public void testServerHandleSelectedForProviderTrace() {
		enableTraceEvents(
				LibTraceEvent.SERVER_HANDLE_SELECTED_FOR_PROVIDER);
		Object factory = new FakeClientFactory();
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions
								.traceServerHandleSelectedForProvider(
										GenericTraceTest.class,
										factory));
		assertEquals(
				"server_handle_selected_for_provider",
				parsed.getEventName());
		assertEquals(null, parsed.getAuxiliaryData().get("provider"));
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeClientFactory",
				parsed.getAuxiliaryData().get(
						"serverHandleClass"));
		assertEquals(
				"[eduAI.trace.lib._tests.GenericTraceTest$FakeServerHandleFactoryInterface]",
				parsed.getAuxiliaryData().get(
						"serverHandleInterfaces"));
	}

	public void testModelNamesFetchedFromServerHandleTrace() {
		enableTraceEvents(
				LibTraceEvent.MODEL_NAMES_FETCHED_FROM_SERVER_HANDLE);
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions
								.traceModelNamesFetchedFromServerHandle(
										GenericTraceTest.class,
										"Gemini",
										List.of("gemini-3.1-flash-lite")));
		assertEquals(
				"model_names_fetched_from_server_handle",
				parsed.getEventName());
		assertEquals(
				"java.lang.String",
				parsed.getAuxiliaryData().get("serverHandleClass"));
		assertEquals(
				"Gemini",
				parsed.getAuxiliaryData().get("server"));
		assertEquals(
				true,
				parsed.getAuxiliaryData().get("modelNames")
						.contains("modelNames:"));
		assertEquals(
				true,
				parsed.getAuxiliaryData().get("modelNames")
						.contains("gemini-3.1-flash-lite"));
	}

	public void testServerHandleSetForModelTrace() {
		enableTraceEvents(LibTraceEvent.SERVER_HANDLE_SET_FOR_MODEL);
		TraceLine parsed =
				firstTraceLine(
						() -> LibTraceActions
								.traceServerHandleSetForModel(
										GenericTraceTest.class,
										"gemini-3.1-flash-lite",
										"Gemini"));
		assertEquals(
				"server_handle_set_for_model",
				parsed.getEventName());
		assertEquals(
				"java.lang.String",
				parsed.getAuxiliaryData().get("serverHandleClass"));
		assertEquals(
				"gemini-3.1-flash-lite",
				parsed.getAuxiliaryData().get("model"));
		assertEquals(
				"Gemini",
				parsed.getAuxiliaryData().get("server"));
	}

	public void testServerHandleFetchedForModelTrace() {
		enableTraceEvents(LibTraceEvent.SERVER_HANDLE_FETCHED_FOR_MODEL);
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
									bytes,
									true,
									StandardCharsets.UTF_8));
			LibTraceActions.traceServerHandleFetchedForModel(
					GenericTraceTest.class,
					"gemini-3.1-flash-lite",
					"Gemini");
		} finally {
			System.setOut(oldOut);
		}
		TraceLine parsed =
				TraceLineParser.parse(
						firstTraceLine(
								bytes.toString(StandardCharsets.UTF_8)));
		assertEquals("lib", parsed.getLayerName());
		assertEquals(
				"server_handle_fetched_for_model",
				parsed.getEventName());
		assertEquals(
				"single_model_request_processing",
				parsed.getDesignPatternName());
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest",
				parsed.getSourceClassName());
		assertEquals(
				"java.lang.String",
				parsed.getAuxiliaryData().get("serverHandleClass"));
		assertEquals(
				"gemini-3.1-flash-lite",
				parsed.getAuxiliaryData().get("model"));
		assertEquals(
				"Gemini",
				parsed.getAuxiliaryData().get("server"));
	}

	private TraceLine firstTraceLine(Runnable aTraceAction) {
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			aTraceAction.run();
		} finally {
			System.setOut(oldOut);
		}
		return TraceLineParser.parse(
				firstTraceLine(bytes.toString(StandardCharsets.UTF_8)));
	}

	private String firstTraceLine(String anOutput) {
		String[] lines = anOutput.split("\\R");
		for (String line : lines) {
			if (line.startsWith(GenericTrace.TRACE_PREFIX)) {
				return line;
			}
		}
		return "";
	}

	private void enableTraceEvents(LibTraceEvent... anEvents) {
		configureTraceEvents(true, List.of());
	}

	private void configureTraceEvents(
			boolean isTraceMessaging,
			List<String> aTraceEvents) {
		GenericTrace.setEnabled(isTraceMessaging);
		TraceEmitterFactorySelector.setTraceEmitterFactory(
				new GenericTraceEmitterFactory());
	}

	private void trace(String aMessage) {
		if (TRACE) {
			System.out.println(aMessage);
		}
	}

	private static void programGoal(
			boolean isTracing,
			Object aTest,
			String aGoal) {
		if (isTracing) {
			System.out.println("Goal: " + aGoal);
		}
	}

	private static void run(
			boolean isTracing,
			Object aTest,
			String aName,
			String aDescription,
			Runnable anAction) {
		anAction.run();
		if (isTracing) {
			System.out.println("Passed: " + aName + " - " + aDescription);
		}
	}

	private void assertEquals(Object anExpected, Object anActual) {
		if (anExpected == null
				? anActual != null
				: !anExpected.equals(anActual)) {
			throw new AssertionError(
					"Expected <" + anExpected + "> but got <"
							+ anActual + ">");
		}
	}

	private void assertHandlerTrace(
			TraceLine aParsed,
			String aHandlerClassKey,
			String aHandlerInterfacesKey,
			String anObjectClassKey,
			String anObjectInterfacesKey) {
		assertEquals(
				"single_model_request_processing",
				aParsed.getDesignPatternName());
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeParameterAdapter",
				aParsed.getAuxiliaryData().get(aHandlerClassKey));
		assertEquals(
				"[eduAI.trace.lib._tests.GenericTraceTest$FakeParameterAdapterInterface]",
				aParsed.getAuxiliaryData().get(aHandlerInterfacesKey));
		assertEquals(
				"eduAI.trace.lib._tests.GenericTraceTest$FakeProviderTarget",
				aParsed.getAuxiliaryData().get(anObjectClassKey));
		assertEquals(
				"[]",
				aParsed.getAuxiliaryData().get(anObjectInterfacesKey));
	}

	private interface FakeServerHandleFactoryInterface {
	}

	private interface FakeServerHandleFactoryRegistryInterface {
	}

	private interface FakeServerHandleInterface {
	}

	private static class FakeClientFactory
			implements FakeServerHandleFactoryInterface {
	}

	private static class FakeServerHandleFactoryRegistry
			implements FakeServerHandleFactoryRegistryInterface {
	}

	private static class FakeServerHandle
			implements FakeServerHandleInterface {
	}

	private static class FakeProviderTarget {
	}

	private interface FakeParameterAdapterInterface {
		void apply(FakeProviderTarget aTarget, Double aValue);
	}

	private static class FakeParameterAdapter
			implements FakeParameterAdapterInterface {
		public void apply(
				FakeProviderTarget aServerOrClientSide,
				Double aValue) {
		}
	}
}
