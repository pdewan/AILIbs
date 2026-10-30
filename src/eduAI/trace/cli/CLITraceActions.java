package eduAI.trace.cli;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import eduAI.trace.TraceEmitterFactorySelector;
import eduAI.trace.TraceObjectPrinter;

/** Public assignment traces, independent of student CLI implementations. */
public final class CLITraceActions {
    public enum Event {
        COMMAND_HANDLER_FETCHED, COMMAND_HANDLER_RESULTS, PROPERTY_HANDLER_FETCHED, PROPERTY_HANDLER_RESULTS, CLI_PROPERTY_SET;
        public String eventName() { return name().toLowerCase(Locale.ROOT); }
    }
    private enum Pattern { COMMAND_DISPATCH }
    public static final String COMMAND_NAME = "commandName";
    public static final String REGISTRY = "commandHandlerRegistry";
    public static final String HANDLER = "commandHandler";
    public static final String HANDLER_IDENTITY = "commandHandlerIdentity";
    public static final String COMMAND_RESULT = "commandResult";
    public static final String PROPERTY_NAME = "propertyName";
    public static final String PROPERTY_HANDLER = "propertyHandler";
    public static final String PROPERTY_REGISTRY = "propertyHandlerRegistry";
    public static final String PROPERTY_HANDLER_IDENTITY = "propertyHandlerIdentity";
    public static final String RETURNED_TYPE = "returnedType";
    public static final String RETURNED_ALTERNATIVES = "returnedAlternatives";
    public static final String PROPERTY_RESULT = "result";
    public static final String PROPERTY_VALUE = "propertyValue";
    public static final String PROPERTY_STORE = "propertyStore";
    public static final String PROPERTY_STORE_IDENTITY = "propertyStoreIdentity";
    public static final String PROPERTY_STORE_STATE = "propertyStoreState";
    private CLITraceActions() { }

    /** Emit after assignment. The participant stays compact; explicit state is data. */
    public static void tracePropertySet(Class<?> sourceClass, String propertyName,
            Object value, Object store, Object storeState) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(PROPERTY_NAME, Objects.requireNonNull(propertyName));
        data.put(PROPERTY_VALUE, TraceObjectPrinter.format(PROPERTY_VALUE, value));
        data.put(PROPERTY_STORE, TraceObjectPrinter.formatIdentity(PROPERTY_STORE, store));
        data.put(PROPERTY_STORE_IDENTITY, TraceObjectPrinter.objectIdentity(store));
        data.put(PROPERTY_STORE_STATE, TraceObjectPrinter.format(PROPERTY_STORE_STATE, storeState));
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
                Pattern.COMMAND_DISPATCH, Objects.requireNonNull(sourceClass), Event.CLI_PROPERTY_SET, data);
    }

    /** The actual returned object, with paths retained; no student accessor contract. */
    public static void tracePropertyHandlerResults(Class<?> sourceClass, String propertyName,
            Object handler, Object result) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(PROPERTY_NAME, Objects.requireNonNull(propertyName));
        data.put(PROPERTY_HANDLER, TraceObjectPrinter.formatIdentity(PROPERTY_HANDLER, handler));
        data.put(PROPERTY_HANDLER_IDENTITY, TraceObjectPrinter.objectIdentity(handler));
        if (result != null) data.put(PROPERTY_RESULT + TraceDataPaths.ID, TraceObjectPrinter.objectIdentity(result));
        data.putAll(TraceDataPaths.flatten(PROPERTY_RESULT, result, java.util.Set.of(handler.getClass()), false));
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
                Pattern.COMMAND_DISPATCH, Objects.requireNonNull(sourceClass), Event.PROPERTY_HANDLER_RESULTS, data);
    }

    /** Legacy two-return contract, retained for previously instrumented implementations. */
    public static void tracePropertyHandlerResults(Class<?> sourceClass, String propertyName,
            Object handler, Object returnedType, Object returnedAlternatives) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(PROPERTY_NAME, Objects.requireNonNull(propertyName));
        data.put(PROPERTY_HANDLER, TraceObjectPrinter.formatIdentity(PROPERTY_HANDLER, handler));
        data.put(PROPERTY_HANDLER_IDENTITY, TraceObjectPrinter.objectIdentity(handler));
        data.put(RETURNED_TYPE, TraceObjectPrinter.format(RETURNED_TYPE, returnedType));
        data.put(RETURNED_ALTERNATIVES, TraceObjectPrinter.format(RETURNED_ALTERNATIVES, returnedAlternatives));
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
                Pattern.COMMAND_DISPATCH, Objects.requireNonNull(sourceClass), Event.PROPERTY_HANDLER_RESULTS, data);
    }

    /** Identifies the object supplying property-specific behavior, not its stored value. */
    public static void tracePropertyHandlerFetched(Class<?> sourceClass,
            String propertyName, Object registry, Object handler) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(PROPERTY_NAME, Objects.requireNonNull(propertyName));
        data.put(PROPERTY_REGISTRY, TraceObjectPrinter.formatIdentity(PROPERTY_REGISTRY, registry));
        data.put(PROPERTY_HANDLER, TraceObjectPrinter.formatIdentity(PROPERTY_HANDLER, handler));
        data.put(PROPERTY_HANDLER_IDENTITY, TraceObjectPrinter.objectIdentity(handler));
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
                Pattern.COMMAND_DISPATCH, Objects.requireNonNull(sourceClass),
                Event.PROPERTY_HANDLER_FETCHED, data);
    }

    /** The source is the dispatcher performing the registry lookup. */
    public static void traceCommandHandlerFetched(Class<?> sourceClass,
            String commandName, Object registry, Object handler) {
        Map<String, String> data = commandHandlerData(commandName, handler);
        data.put(REGISTRY, TraceObjectPrinter.formatIdentity(REGISTRY, registry));
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
                Pattern.COMMAND_DISPATCH, Objects.requireNonNull(sourceClass), Event.COMMAND_HANDLER_FETCHED, data);
    }

    /** Call immediately after the handler returns, before result transformation or display. */
    public static void traceCommandHandlerResults(Class<?> sourceClass,
            String commandName, Object handler, Object result) {
        Map<String, String> data = commandHandlerData(commandName, handler);
        data.put(COMMAND_RESULT, TraceObjectPrinter.formatFullEvidence(COMMAND_RESULT, result));
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
                Pattern.COMMAND_DISPATCH, Objects.requireNonNull(sourceClass), Event.COMMAND_HANDLER_RESULTS, data);
    }

    private static Map<String, String> commandHandlerData(String commandName, Object handler) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(COMMAND_NAME, Objects.requireNonNull(commandName));
        data.put(HANDLER, TraceObjectPrinter.formatIdentity(HANDLER, handler));
        data.put(HANDLER_IDENTITY, TraceObjectPrinter.objectIdentity(handler));
        return data;
    }
}
