package eduAI.trace.cli;

import java.util.*;
import eduAI.trace.GenericTrace;
import eduAI.trace.TraceEmitterFactorySelector;
import eduAI.trace.TraceObjectPrinter;

/**
 * CLI contract shared by application instrumentation and assignment checks.
 */
public final class MinimalCLITrace {
    public enum Event {
        COMMAND_HANDLED, PROPERTY_HANDLED, HISTORY_WITH_PARAMETERS_UPDATED,
        COMMAND_OUTPUT_NOTIFIED, STREAM_OUTPUT_NOTIFIED, COMMAND_DISPLAYED, STREAM_DISPLAYED, FILE_EXPANDED;
        public String eventName() { return name().toLowerCase(Locale.ROOT); }
    }
    private enum Pattern { MINIMAL_CLI }
    /** Ordinary prompt input has no user-visible command name. */
    public static final String UNNAMED_COMMAND = "";
    public static final String CALLER="caller", COMMAND_NUMBER="commandNumber",
        NAME="name", REGISTRY="registry", HANDLER="handler", RESULT="result",
        STORE="store", VALUE="value", HISTORY="history", OBSERVER="observer", OBSERVABLE="observable",
        OUTPUT="output", MODEL="model", CHUNK="chunk", INPUT_FILE_NAME="inputFileName",
        EXPANDER="expander", EXPANDED_DATA="expandedData";
    private static final Set<Class<?>> participants = new HashSet<>();
    private static long session = Long.MIN_VALUE;
    private MinimalCLITrace() { }

    private static void prepare(Object... objects) {
        long current = GenericTrace.getSessionId();
        if (session != current) {
            participants.clear();
            session = current;
        }
        for (Object object : objects) participants.add(Objects.requireNonNull(object).getClass());
    }
    private static String identity(Object object) {
        return TraceObjectPrinter.objectIdentity(Objects.requireNonNull(object));
    }
    private static Map<String,String> base(Object caller) {
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put(CALLER, identity(caller));
        return fields;
    }
    private static void number(Map<String,String> fields, int number) {
        if (number < 1) throw new IllegalArgumentException("Command number must be positive");
        fields.put(COMMAND_NUMBER, Integer.toString(number));
    }
    private static void data(Map<String,String> fields, String name, Object value) {
        if (value!=null) fields.put(name+TraceDataPaths.ID,identity(value));
        fields.putAll(TraceDataPaths.flatten(name, value, participants, false));
    }
    private static void registry(Map<String,String> fields, Object registry) {
        fields.put(REGISTRY, identity(registry));
        fields.putAll(TraceDataPaths.flatten(REGISTRY, registry, participants, true));
    }
    private static void emit(Object caller, Event event, Map<String,String> fields) {
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
            Pattern.MINIMAL_CLI, caller.getClass(), event, fields);
    }

    public static synchronized void commandHandled(Object dispatcher, int commandNumber,
            String commandName, Object registry, Object handler, Object result) {
        if (!GenericTrace.isEnabled()) return;
        prepare(dispatcher, registry, handler);
        var fields = base(dispatcher);
        number(fields, commandNumber);
        fields.put(NAME, Objects.requireNonNull(commandName));
        registry(fields, registry);
        fields.put(HANDLER, identity(handler));
        data(fields, RESULT, result);
        emit(dispatcher, Event.COMMAND_HANDLED, fields);
    }
    public static synchronized void propertyHandled(Object caller, String propertyName,
            Object registry, Object handler, Object result, Object store, Object value) {
        if (!GenericTrace.isEnabled()) return;
        prepare(caller, registry, handler);
        var fields = base(caller);
        fields.put(NAME, Objects.requireNonNull(propertyName));
        registry(fields, registry);
        fields.put(HANDLER, identity(handler));
        data(fields, RESULT, result);
        data(fields, STORE, store);
        data(fields, VALUE, value);
        emit(caller, Event.PROPERTY_HANDLED, fields);
    }
    /** After a property setter only; A1 requests and displayed histories supply other evidence. */
    public static synchronized void historyWithParametersUpdated(Object caller, int commandNumber,
            Object historyWithParameters) {
        if (!GenericTrace.isEnabled()) return;
        prepare(caller);
        var fields = base(caller);
        number(fields, commandNumber);
        data(fields, HISTORY, Objects.requireNonNull(historyWithParameters));
        emit(caller, Event.HISTORY_WITH_PARAMETERS_UPDATED, fields);
    }
    /** After filtering and immediately before sending the actual result to observers. */
    public static synchronized void commandOutputNotified(Object observable, int commandNumber, Object result) {
        if (!GenericTrace.isEnabled()) return;
        prepare(observable);
        Map<String,String> fields = new LinkedHashMap<>();
        number(fields, commandNumber);
        fields.put(OBSERVABLE, identity(observable));
        fields.put(RESULT+TraceDataPaths.ID, identity(result));
        emit(observable, Event.COMMAND_OUTPUT_NOTIFIED, fields);
    }
    public static synchronized void commandDisplayed(Object observer,
            int commandNumber, Object result, String output) {
        if (!GenericTrace.isEnabled()) return;
        prepare(observer);
        Map<String,String> fields = new LinkedHashMap<>();
        number(fields, commandNumber);
        fields.put(OBSERVER, identity(observer));
        fields.put(RESULT+TraceDataPaths.ID, identity(result));
        fields.put(OUTPUT, Objects.requireNonNull(output));
        emit(observer, Event.COMMAND_DISPLAYED, fields);
    }
    /** Immediately before sending a model chunk to streaming observers. */
    public static synchronized void streamOutputNotified(Object observable,
            int commandNumber, String model, Object chunk) {
        if (!GenericTrace.isEnabled()) return;
        prepare(observable);
        Map<String,String> fields = new LinkedHashMap<>();
        number(fields, commandNumber);
        fields.put(OBSERVABLE, identity(observable));
        fields.put(MODEL, Objects.requireNonNull(model));
        fields.put(CHUNK+TraceDataPaths.ID, identity(chunk));
        emit(observable, Event.STREAM_OUTPUT_NOTIFIED, fields);
    }
    public static synchronized void streamDisplayed(Object observer,
            int commandNumber, String model, Object chunk, String output) {
        if (!GenericTrace.isEnabled()) return;
        prepare(observer);
        Map<String,String> fields = new LinkedHashMap<>();
        number(fields, commandNumber);
        fields.put(OBSERVER, identity(observer));
        fields.put(MODEL, Objects.requireNonNull(model));
        fields.put(CHUNK+TraceDataPaths.ID, identity(chunk));
        fields.put(OUTPUT, Objects.requireNonNull(output));
        emit(observer, Event.STREAM_DISPLAYED, fields);
    }
    public static synchronized void fileExpanded(Object caller, String inputFileName,
            Object expander, Object expandedData) {
        if (!GenericTrace.isEnabled()) return;
        prepare(caller, expander);
        var fields = base(caller);
        fields.put(INPUT_FILE_NAME, Objects.requireNonNull(inputFileName));
        fields.put(EXPANDER, identity(expander));
        data(fields, EXPANDED_DATA, expandedData);
        emit(caller, Event.FILE_EXPANDED, fields);
    }
}
