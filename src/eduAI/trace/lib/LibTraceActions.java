package eduAI.trace.lib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import eduAI.trace.TraceObjectPrinter;

public class LibTraceActions {
	/**
	 * Trace the registry lookup using its actual provider key. The assignment
	 * providers are {@code gemini} and {@code ollama}; their capitalization is
	 * ignored by the checks. New records write the key in lowercase.
	 */
	public static void traceServerHandleFactoryFetched(
			Class<?> aSourceClass,
			String aProviderName,
			Object aServerHandleFactoryRegistry,
			Object aServerHandleFactory) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("provider", aProviderName.trim().toLowerCase(java.util.Locale.ROOT));
		data.put(
				"serverHandleFactoryRegistryClass",
				TraceObjectPrinter.className(aServerHandleFactoryRegistry));
		data.put(
				"serverHandleFactoryRegistryInterfaces",
				interfaceNames(aServerHandleFactoryRegistry));
		data.put(
				"serverHandleFactoryRegistry",
				traceValue(
						"serverHandleFactoryRegistry",
						aServerHandleFactoryRegistry));
		data.put(
				"serverHandleFactoryClass",
				TraceObjectPrinter.className(aServerHandleFactory));
		data.put(
				"serverHandleFactoryInterfaces",
				interfaceNames(aServerHandleFactory));
		data.put(
				"serverHandleFactory",
				traceValue("serverHandleFactory", aServerHandleFactory));
		data.put(
				"serverHandleFactoryIdentity",
				objectIdentity(aServerHandleFactory));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SERVER_HANDLE_FACTORY_FETCHED,
				data);
	}

	public static void traceParameterTranslated(
			Class<?> aSourceClass,
			String aPropertyName,
			Object aPropertyValue,
			Object aPropertyHandler,
			Object aProviderDependentTarget) {
		Map<String, String> data = handlerInvocationData(
				"parameterHandlerClass",
				"parameterHandler",
				aPropertyHandler,
				"providerDependentTargetClass",
				"providerDependentTarget",
				aProviderDependentTarget,
				aPropertyName,
				aPropertyValue);
		data.put(
				"providerDependentTargetState",
				NativePayloadFormatter.formatProperties(
						aProviderDependentTarget));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.PARAMETER_TRANSLATED,
				data);
	}

	public static void traceMetadataTranslated(
			Class<?> aSourceClass,
			String aPropertyName,
			Object aPropertyValue,
			Object aPropertyHandler,
			Object aProviderDependentSource) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.METADATA_TRANSLATED,
				handlerInvocationData(
						"metadataHandlerClass",
						"metadataHandler",
						aPropertyHandler,
						"providerDependentSourceClass",
						"providerDependentSource",
						aProviderDependentSource,
						aPropertyName,
						aPropertyValue));
	}

	public static void traceServerHandleFactorySetForProvider(
			Class<?> aSourceClass,
			Object aServerHandleFactory) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SERVER_HANDLE_FACTORY_SET_FOR_PROVIDER,
				data(
						"serverHandleFactoryClass",
						aServerHandleFactory,
						"serverHandleFactory",
						aServerHandleFactory));
	}

	public static void traceServerHandleFetched(
			Class<?> aSourceClass,
			Object aServerHandleFactory,
			Object aServerHandle) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put(
				"serverHandleFactoryClass",
				TraceObjectPrinter.className(aServerHandleFactory));
		data.put(
				"serverHandleFactoryInterfaces",
				interfaceNames(aServerHandleFactory));
		data.put(
				"serverHandleFactory",
				traceValue("serverHandleFactory", aServerHandleFactory));
		data.put(
				"serverHandleFactoryIdentity",
				objectIdentity(aServerHandleFactory));
		data.put(
				"serverHandleClass",
				TraceObjectPrinter.className(aServerHandle));
		data.put(
				"serverHandleInterfaces",
				interfaceNames(aServerHandle));
		data.put(
				"serverHandle",
				traceValue("serverHandle", aServerHandle));
		data.put("serverHandleIdentity", objectIdentity(aServerHandle));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SERVER_HANDLE_FETCHED,
				data);
	}

	public static void traceServerHandleSelectedForProvider(
			Class<?> aSourceClass,
			Object aServerHandle) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SERVER_HANDLE_SELECTED_FOR_PROVIDER,
				data(
						"serverHandleClass",
						aServerHandle,
						"serverHandle",
						aServerHandle));
	}

	public static void traceServerHandleCreated(
			Class<?> aSourceClass,
			String aServerName) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SERVER_HANDLE_CREATED,
				data(
						"serverHandleClass",
						aServerName,
						"server",
						aServerName));
	}

	public static void traceModelNamesFetchedFromServerHandle(
			Class<?> aSourceClass,
			String aServerName,
			List<String> aModelNames) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.MODEL_NAMES_FETCHED_FROM_SERVER_HANDLE,
				data(
						"serverHandleClass",
						aServerName,
						"server",
						aServerName,
						"modelNames",
						aModelNames));
	}

	public static void traceServerHandleSetForModel(
			Class<?> aSourceClass,
			String aModelName,
			String aServerName) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SERVER_HANDLE_SET_FOR_MODEL,
				data(
						"serverHandleClass",
						aServerName,
						"model",
						aModelName,
						"server",
						aServerName));
	}

	public static void traceServerHandleFetchedForModel(
			Class<?> aSourceClass,
			String aModelName,
			String aServerName) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SERVER_HANDLE_FETCHED_FOR_MODEL,
				data(
						"serverHandleClass",
						aServerName,
						"model",
						aModelName,
						"server",
						aServerName));
	}

	public static void traceParameterStoreCreated(
			Class<?> aSourceClass,
			Object aParameterStore) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.PARAMETER_STORE_CREATED,
				data(
						"parameterStoreClass",
						aParameterStore,
						"parameterStore",
						aParameterStore));
				
				
	}

	public static void traceMetadataPropertyStoreCreated(
			Class<?> aSourceClass,
			Object aParameterStore) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.METADATA_PROPERTY_STORE_CREATED,
				data(
						"metadataPropertyStoreClass",
						aParameterStore,
						"metadataPropertyStore",
						aParameterStore));
	}

	public static void tracePropertySet(
			Class<?> aSourceClass,
			Object aPropertyStore,
			String aPropertyName,
			Object aPropertyValue) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.PROPERTY_SET,
				data(
						"propertyStoreClass",
						aPropertyStore,
						"propertyName",
						aPropertyName,
						"propertyValue",
						aPropertyValue));
	}

	public static void traceSenderFetchedProperty(
			Class<?> aSourceClass,
			Object aParameterStore,
			String aPropertyName,
			Object aPropertyValue) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SENDER_FETCHED_PROPERTY,
				data(
						"parameterStoreClass",
						aParameterStore,
						"propertyName",
						aPropertyName,
						"propertyValue",
						aPropertyValue));
	}

	public static void traceStreamHandlerRegistered(
			Class<?> aSourceClass,
			Object aStreamHandler) {
		Map<String, String> data;
		if (aStreamHandler == null) {
			data = new LinkedHashMap<>();
			data.put("streamHandlerClass", "null");
			data.put("streamHandlerInterfaces", "[]");
			data.put("streamHandler", "null");
		} else {
			data =
					data(
							"streamHandlerClass",
							aStreamHandler,
							"streamHandler",
							aStreamHandler);
		}
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.STREAM_HANDLER_REGISTERED,
				data);
	}

	public static void traceMessageMergerFactorySet(
			Class<?> aSourceClass,
			Object aMessageMergerFactory) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.MESSAGE_MERGER_FACTORY_SET,
				data(
						"messageMergerFactoryClass",
						aMessageMergerFactory,
						"messageMergerFactory",
						aMessageMergerFactory));
	}

	public static void traceMessageMergerFactoryFetched(
			Class<?> aSourceClass,
			Object aMessageMergerFactoryRegistry,
			Object aMessageMergerFactory) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put(
				"streamChunkMergerFactoryRegistryClass",
				TraceObjectPrinter.className(aMessageMergerFactoryRegistry));
		data.put(
				"streamChunkMergerFactoryRegistryInterfaces",
				interfaceNames(aMessageMergerFactoryRegistry));
		data.put(
				"streamChunkMergerFactoryRegistry",
				traceValue(
						"streamChunkMergerFactoryRegistry",
						aMessageMergerFactoryRegistry));
		data.put(
				"streamChunkMergerFactoryClass",
				TraceObjectPrinter.className(aMessageMergerFactory));
		data.put(
				"streamChunkMergerFactoryInterfaces",
				interfaceNames(aMessageMergerFactory));
		data.put(
				"streamChunkMergerFactoryMethods",
				TraceObjectPrinter.methodSignatures(aMessageMergerFactory));
		data.put(
				"streamChunkMergerFactory",
				traceValue(
						"streamChunkMergerFactory",
						aMessageMergerFactory));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.MESSAGE_MERGER_FACTORY_FETCHED,
				data);
	}

	public static void traceMessageMergerCreated(
			Class<?> aSourceClass,
			Object aMessageMerger) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.MESSAGE_MERGER_CREATED,
				data(
						"messageMergerClass",
						aMessageMerger,
						"messageMerger",
						aMessageMerger));
	}

	public static void traceProviderStreamingChunkReceived(
			Class<?> aSourceClass,
			Object aProviderSpecificChunkObject) {
		Map<String, String> data =
				data(
						"providerStreamingChunkClass",
						aProviderSpecificChunkObject,
						"providerStreamingChunk",
						aProviderSpecificChunkObject);
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.PROVIDER_STREAMING_CHUNK_RECEIVED,
				data);
	}

	public static void traceStreamingChunkSentToMerger(
			Class<?> aSourceClass,
			Object aMessageMerger,
			Object aChunkMessage) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.STREAMING_CHUNK_SENT_TO_MERGER,
				data(
						"messageMergerClass",
						aMessageMerger,
						"streamingChunk",
						aChunkMessage,
						"messageMerger",
						aMessageMerger));
	}

	public static synchronized void traceStreamingChunkAccumulated(
			Class<?> aSourceClass,
			Object aMessageMerger,
			Object aChunkMessage,
			Object anAccumulatedMessage) {
		Map<String, String> evidence = data(
				"messageMergerClass", aMessageMerger,
				"streamingChunk", aChunkMessage,
				"accumulatedMessage", anAccumulatedMessage,
				"messageMerger", aMessageMerger);
		StreamingTextEvidence.accumulate(aMessageMerger, aChunkMessage, anAccumulatedMessage, evidence);
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.STREAMING_CHUNK_ACCUMULATED,
				evidence);
	}

	public static synchronized void traceStreamingChunksMerged(
			Class<?> aSourceClass,
			Object aMessageMerger,
			Object aMergedMessage) {
		Map<String, String> evidence = data(
				"messageMergerClass", aMessageMerger,
				"mergedMessage", aMergedMessage,
				"messageMerger", aMessageMerger);
		StreamingTextEvidence.finish(aMessageMerger, aMergedMessage, evidence);
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.STREAMING_CHUNKS_MERGED,
				evidence);
	}

	public static void traceStreamingCallbackInvoked(
			Class<?> aSourceClass,
			Object aStreamingCallback,
			Object aCallbackArgument) {
		traceStreamingCallbackInvoked(
				aSourceClass,
				LibTraceEvent.STREAMING_CALLBACK_INVOKED,
				aStreamingCallback,
				aCallbackArgument);
	}

	public static void tracePartialResponseCallbackInvoked(
			Class<?> aSourceClass,
			Object aStreamingCallback,
			Object aCallbackArgument) {
		traceStreamingCallbackInvoked(
				aSourceClass,
				LibTraceEvent.PARTIAL_RESPONSE_CALLBACK_INVOKED,
				aStreamingCallback,
				aCallbackArgument);
	}

	public static void traceCompleteResponseCallbackInvoked(
			Class<?> aSourceClass,
			Object aStreamingCallback,
			Object aCallbackArgument) {
		traceStreamingCallbackInvoked(
				aSourceClass,
				LibTraceEvent.COMPLETE_RESPONSE_CALLBACK_INVOKED,
				aStreamingCallback,
				aCallbackArgument);
	}

	private static void traceStreamingCallbackInvoked(
			Class<?> aSourceClass,
			LibTraceEvent anEvent,
			Object aStreamingCallback,
			Object aCallbackArgument) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put(
				"streamingCallbackClass",
				TraceObjectPrinter.className(aStreamingCallback));
		data.put(
				"streamingCallbackInterfaces",
				interfaceNames(aStreamingCallback));
		data.put(
				"streamingCallbackMethods",
				TraceObjectPrinter.methodSignatures(aStreamingCallback));
		data.put(
				"streamingCallback",
				traceValue("streamingCallback", aStreamingCallback));
		data.put(
				"callbackArgument",
				traceValue("callbackArgument", aCallbackArgument));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				anEvent,
				data);
	}

	public static void traceStreamingResultReturned(
			Class<?> aSourceClass,
			Object aResponse,
			Object aReturnedMessage) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put(
				"streamingResultClass",
				aResponse == null
						? "null"
						: TraceObjectPrinter.className(aResponse));
		data.put("streamingResultInterfaces", interfaceNames(aResponse));
		data.put(
				"returnedMessage",
				objectValue(
						"returnedMessage",
						aReturnedMessage));
		data.put(
				"streamingResult",
				objectValue("streamingResult", aResponse));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.STREAMING_RESULT_RETURNED,
				data);
	}

	public static void traceSystemInstructionCreated(
			Class<?> aSourceClass,
			Object aSystemInstruction) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.SYSTEM_INSTRUCTION_CREATED,
				data(
						"systemInstructionClass",
						aSystemInstruction,
						"systemInstruction",
						aSystemInstruction));
	}

	public static void traceUserPromptMessageCreated(
			Class<?> aSourceClass,
			Object aUserPromptMessage) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.USER_PROMPT_MESSAGE_CREATED,
				data(
						"userPromptMessageClass",
						aUserPromptMessage,
						"userPromptMessage",
						aUserPromptMessage));
	}

	public static void traceResponseTranslated(
			Class<?> aSourceClass,
			Object aProviderDependentResponse,
			Object aProviderIndependentResponse) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put(
				"providerDependentResponse",
				providerResponseValue(
						"providerDependentResponse",
						aProviderDependentResponse));
		data.put(
				"providerIndependentResponse",
				objectValue(
						"providerIndependentResponse",
						aProviderIndependentResponse));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.RESPONSE_TRANSLATED,
				data);
	}

	public static void traceMessageAddedToContextWindow(
			Class<?> aSourceClass,
			int anIndex,
			Object aMessage) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.CONTEXT_WINDOW_MESSAGE_ADDED,
				data(
						"contextWindowMessageClass",
						aMessage,
						"index",
						anIndex,
						"contextWindowMessage",
						aMessage));
	}

	public static void traceMessageRetrievedFromContextWindow(
			Class<?> aSourceClass,
			int anIndex,
			Object aMessage) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.CONTEXT_WINDOW_MESSAGE_RETRIEVED,
				data(
						"contextWindowMessageClass",
						aMessage,
						"index",
						anIndex,
						"contextWindowMessage",
						aMessage));
	}

	public static void traceProviderIndependentContextWindowPrepared(
			Class<?> aSourceClass,
			List<?> aContextWindow) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.PROVIDER_INDEPENDENT_CONTEXT_WINDOW_PREPARED,
				data(
						"contextWindowClass",
						aContextWindow,
						"contextWindow",
						aContextWindow));
	}

	public static void traceProviderDependentContextWindowPrepared(
			Class<?> aSourceClass,
			List<?> aProviderIndependentContextWindow,
			Object aContextWindow) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.PROVIDER_DEPENDENT_CONTEXT_WINDOW_PREPARED,
				data(
						"providerContextWindowClass",
						aContextWindow,
						"providerIndependentContextWindow",
						objectValue(
								"providerIndependentContextWindow",
								aProviderIndependentContextWindow),
						"providerContextWindow",
						providerContextWindowValue(
								"providerContextWindow",
								aContextWindow)));
	}

	public static void traceMessageTranslated(
			Class<?> aSourceClass,
			Object aProviderIndependentMessage,
			Object aProviderDependentMessage) {
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.MESSAGE_TRANSLATED,
				data(
						"providerDependentMessageClass",
						aProviderDependentMessage,
						"providerIndependentMessage",
						objectValue(
								"providerIndependentMessage",
								aProviderIndependentMessage),
						"providerDependentMessage",
						providerMessageValue(
								"providerDependentMessage",
								aProviderDependentMessage)));
	}

	public static void traceRequestSent(
			Class<?> aSourceClass,
			StreamingMode aMode,
			Object aProviderIndependentParameterStore,
			Object aProviderIndependentContextWindow,
			Object aProviderDependentConfiguration,
			Object aProviderDependentContextWindow) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("mode", aMode == null ? null : aMode.name());
		data.put(
				"providerIndependentContextWindow",
				objectValue(
						"providerIndependentContextWindow",
						aProviderIndependentContextWindow));
		data.put(
				"providerIndependentParameterStore",
				objectValue(
						"providerIndependentParameterStore",
						aProviderIndependentParameterStore));
		data.put(
				"providerDependentContextWindow",
				providerContextWindowValue(
						"providerDependentContextWindow",
						aProviderDependentContextWindow));
		data.put(
				"providerDependentConfiguration",
				providerConfigurationValue(
						"providerDependentConfiguration",
						aProviderDependentConfiguration));
		LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				aSourceClass,
				LibTraceEvent.REQUEST_SENT,
				data);
	}

	private static Map<String, String> data(
			String aClassKey,
			Object anObject,
			String aValueKey,
			Object aValue) {
		Map<String, String> result = new LinkedHashMap<>();
		result.put(aClassKey, TraceObjectPrinter.className(anObject));
		result.put(interfaceKey(aClassKey), interfaceNames(anObject));
		result.put(aValueKey, traceValue(aValueKey, aValue));
		return result;
	}

	private static Map<String, String> handlerInvocationData(
			String aHandlerClassKey,
			String aHandlerValueKey,
			Object aHandler,
			String aObjectClassKey,
			String aObjectValueKey,
			Object anObject,
			String aPropertyName,
			Object aPropertyValue) {
		Map<String, String> result = new LinkedHashMap<>();
		result.put(aHandlerClassKey, TraceObjectPrinter.className(aHandler));
		result.put(interfaceKey(aHandlerClassKey), interfaceNames(aHandler));
		result.put(aHandlerValueKey, traceValue(aHandlerValueKey, aHandler));
		result.put(aObjectClassKey, TraceObjectPrinter.className(anObject));
		result.put(interfaceKey(aObjectClassKey), interfaceNames(anObject));
		result.put(aObjectValueKey, traceValue(aObjectValueKey, anObject));
		result.put("propertyName", String.valueOf(aPropertyName));
		result.put("propertyValue", traceValue("propertyValue", aPropertyValue));
		return result;
	}

	private static Map<String, String> data(
			String aClassKey,
			Object anObject,
			String aFirstValueKey,
			Object aFirstValue,
			String aSecondValueKey,
			Object aSecondValue) {
		Map<String, String> result =
				data(aClassKey, anObject, aFirstValueKey, aFirstValue);
		result.put(aSecondValueKey, traceValue(aSecondValueKey, aSecondValue));
		return result;
	}

	private static Map<String, String> data(
			String aClassKey,
			Object anObject,
			String aFirstValueKey,
			Object aFirstValue,
			String aSecondValueKey,
			Object aSecondValue,
			String aThirdValueKey,
			Object aThirdValue) {
		Map<String, String> result =
				data(
						aClassKey,
						anObject,
						aFirstValueKey,
						aFirstValue,
						aSecondValueKey,
						aSecondValue);
		result.put(aThirdValueKey, traceValue(aThirdValueKey, aThirdValue));
		return result;
	}

	private static String interfaceKey(String aClassKey) {
		if (aClassKey != null && aClassKey.endsWith("Class")) {
			return aClassKey.substring(
					0,
					aClassKey.length() - "Class".length())
					+ "Interfaces";
		}
		return String.valueOf(aClassKey) + "Interfaces";
	}

	private static String interfaceNames(Object anObject) {
		return TraceObjectPrinter.interfaceNames(anObject);
	}

	private static String objectIdentity(Object anObject) {
		Objects.requireNonNull(anObject, "trace object identity");
		return anObject.getClass().getName()
				+ "@"
				+ Integer.toHexString(System.identityHashCode(anObject));
	}

	private static String traceValue(String aKey, Object aValue) {
		Objects.requireNonNull(aKey, "trace value key");
		Objects.requireNonNull(aValue, aKey);
		if (isSimpleValue(aValue)) {
			return String.valueOf(aValue);
		}
		return objectValue(aKey, aValue);
	}

	private static String objectValue(String aKey, Object aValue) {
		Objects.requireNonNull(aKey, "object value key");
		Objects.requireNonNull(aValue, aKey);
		return TraceObjectPrinter.format(aKey, aValue);
	}

	private static String providerMessageValue(
			String aKey,
			Object aValue) {
		return providerValue(
				aKey,
				aValue,
				NativePayloadFormatter.formatMessage(aValue));
	}

	private static String providerContextWindowValue(
			String aKey,
			Object aValue) {
		return providerValue(
				aKey,
				aValue,
				NativePayloadFormatter.formatContextWindow(aValue));
	}

	private static String providerConfigurationValue(
			String aKey,
			Object aValue) {
		return providerValue(
				aKey,
				aValue,
				NativePayloadFormatter.formatConfiguration(aValue));
	}

	private static String providerResponseValue(
			String aKey,
			Object aValue) {
		return providerValue(
				aKey,
				aValue,
				NativePayloadFormatter.formatResponse(aValue));
	}

	private static String providerPropertiesValue(
			String aKey,
			Object aValue) {
		return providerValue(
				aKey,
				aValue,
				NativePayloadFormatter.formatProperties(aValue));
	}

	private static String providerValue(
			String aKey,
			Object aValue,
			String aFormattedValue) {
		Objects.requireNonNull(aKey, "provider value key");
		Objects.requireNonNull(aValue, aKey);
		Objects.requireNonNull(aFormattedValue, aKey + " formatted value");
		return aKey
				+ ": "
				+ TraceObjectPrinter.className(aValue)
				+ " interfaces="
				+ TraceObjectPrinter.interfaceNames(aValue)
				+ " providerValue="
				+ eduAI.trace.CompactNativeText.format(aFormattedValue,
						TraceObjectPrinter.className(aValue).contains("GenerateContentConfig"));
	}

	private static boolean isSimpleValue(Object aValue) {
		Class<?> valueClass = aValue.getClass();
		return valueClass.isPrimitive()
				|| aValue instanceof String
				|| aValue instanceof Character
				|| aValue instanceof Boolean
				|| aValue instanceof Number
				|| aValue instanceof Enum<?>;
	}

}
