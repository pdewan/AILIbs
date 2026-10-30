package eduAI.trace.cli;

import java.util.*;
import eduAI.trace.TraceEmitterFactorySelector;
import eduAI.trace.TraceObjectPrinter;

/** Explicit architectural evidence: participants are identities, values are data. */
public final class CLIArchitectureTrace {
    public enum Event {
        CLI_COMMAND_NOTIFIED, CLI_COMMAND_DISPLAYED, CLI_STREAM_NOTIFIED, CLI_STREAM_DISPLAYED,
        CLI_FILE_EXPANDED, CLI_PROPERTY_READ, CLI_REQUEST_SNAPSHOT, CLI_MODELS_AVAILABLE,
        CLI_HISTORY_WRITTEN, CLI_HISTORY_READ;
        public String eventName() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final String OWNER="owner", OBSERVER="observer", ITEM="item", KEY="key",
        VALUE="value", OUTPUT="output", KIND="kind", STORE="store", SNAPSHOT="snapshot",
        LINK="link", PATH="path", EXPANDER="expander", NAME="name";
    public static final String TEXT="text", IMAGE="image", TRANSCRIPT="transcript", MESSAGES="messages";
    private enum Pattern { CLI_ARCHITECTURE }
    private CLIArchitectureTrace() { }
    private static String id(Object value) { return value == null ? "null" : TraceObjectPrinter.objectIdentity(value); }
    private static String data(Object value) { return TraceObjectPrinter.formatFullEvidence(VALUE, value); }
    public static void emit(Class<?> source, Event event, Map<String,String> fields) {
        TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(Pattern.CLI_ARCHITECTURE, source, event, fields);
    }
    public static void notification(Class<?> source, boolean stream, Object owner, Object observer,
            String key, Object item, Object value) {
        emit(source, stream ? Event.CLI_STREAM_NOTIFIED : Event.CLI_COMMAND_NOTIFIED,
            Map.of(OWNER,id(owner), OBSERVER,id(observer), KEY,key, ITEM,id(item), VALUE,data(value)));
    }
    public static void displayed(Class<?> source, boolean stream, Object observer,
            String key, Object item, String output) {
        emit(source, stream ? Event.CLI_STREAM_DISPLAYED : Event.CLI_COMMAND_DISPLAYED,
            Map.of(OBSERVER,id(observer), KEY,key, ITEM,id(item), OUTPUT,data(output)));
    }
    public static void expanded(Class<?> source, String kind, String path, Object expander, Object value) {
        emit(source, Event.CLI_FILE_EXPANDED, Map.of(KIND,kind, PATH,path, EXPANDER,id(expander), VALUE,data(value)));
    }
    public static void propertyRead(Class<?> source, String name, Object store, Object value) {
        emit(source, Event.CLI_PROPERTY_READ, Map.of(NAME,name, STORE,id(store), VALUE,data(value)));
    }
    public static void snapshot(Class<?> source, Object store, Object snapshot, String requestID, Object values) {
        emit(source, Event.CLI_REQUEST_SNAPSHOT,
            Map.of(STORE,id(store), SNAPSHOT,id(snapshot), KEY,requestID, VALUE,data(values)));
    }
    public static void models(Class<?> source, Object registry, Object names) {
        emit(source, Event.CLI_MODELS_AVAILABLE, Map.of(OWNER,id(registry), VALUE,data(names)));
    }
    /** Stable storage keys also support serialized histories whose Java objects are reloaded. */
    public static void history(Class<?> source, boolean write, String kind, Object store,
            String key, String link, Object entry, Object snapshot) {
        emit(source, write ? Event.CLI_HISTORY_WRITTEN : Event.CLI_HISTORY_READ,
            Map.of(KIND,kind, STORE,id(store), KEY,key, LINK,link == null ? "" : link,
                ITEM,id(entry), SNAPSHOT,id(snapshot)));
    }
}
