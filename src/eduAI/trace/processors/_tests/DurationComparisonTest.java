package eduAI.trace.processors._tests;

import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.processors.FlattenedKeyValueComparator;
import java.time.Duration;
import java.util.Map;

/** Durations compare by value, not printer labels; wrong values and text imitations fail. */
public final class DurationComparisonTest {
    private static final String KEY = "timeout";
    private static final int SAVED_REQUEST_COUNT = 6;
    public static void main(String[] args) throws Exception {
        var comparator = new FlattenedKeyValueComparator();
        for (Duration value : new Duration[]{Duration.ofMinutes(2), Duration.ofSeconds(90),
                Duration.ZERO, Duration.ofNanos(123456789), Duration.ofSeconds(-2)}) {
            String expected = TraceObjectPrinter.formatFullEvidence("propertyValue", value);
            String store = TraceObjectPrinter.formatFullEvidence("store", Map.of(KEY, value));
            require(comparator.compare(store, Map.of(KEY, expected)).matches(), "Matching Duration rejected: " + value);
            require(!comparator.compare(TraceObjectPrinter.formatFullEvidence("store", Map.of(KEY, value.plusNanos(1))),
                    Map.of(KEY, expected)).matches(), "Different Duration accepted");
            require(!comparator.compare(TraceObjectPrinter.formatFullEvidence("store", Map.of(KEY, value.toString())),
                    Map.of(KEY, expected)).matches(), "String imitation accepted");
            require(!comparator.compare(TraceObjectPrinter.formatFullEvidence("store", Map.of(KEY, expected)),
                    Map.of(KEY, expected)).matches(), "Quoted dump imitation accepted");
            require(!comparator.compare(store, Map.of("missing", expected)).matches(), "Missing key accepted");
            require(comparator.compare(TraceObjectPrinter.formatFullEvidence("store",
                    Map.of(KEY, java.util.List.of(value))), Map.of(KEY, expected)).matches(), "Duration in collection rejected");
            require(!comparator.compare(TraceObjectPrinter.formatFullEvidence("store", Map.of(KEY, value.toNanos())),
                    Map.of(KEY, expected)).matches(), "Number accepted without a unit contract");
        }
        String legacy = "value: java.time.Duration interfaces=[java.time.temporal.TemporalAmount] toString=\"PT120S\"";
        String expected = "propertyValue: java.time.Duration interfaces=[java.time.temporal.TemporalAmount] toString=\"PT2M\"";
        var collector = new eduAI.trace.processors.ReflectiveValueCollector();
        int offset = collector.matchingValueOffset(legacy, expected, java.util.Set.of());
        require(offset >= 0, "Equivalent ISO durations rejected");
        require(collector.matchingValueOffset(legacy, expected, java.util.Set.of(offset)) < 0,
                "Excluded evidence reused");
        require(collector.matchingValueOffset(legacy.replace("PT120S", "invalid"), expected, java.util.Set.of()) < 0,
                "Invalid duration accepted");
        if (args.length == 1) {
            // Saved submission: six requests carry PT2M or PT1M30S. Change only
            // stored durations, leaving the translated value unchanged.
            String translated = null;
            int checked = 0;
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))) {
                var event = eduAI.trace.TraceLineParser.parse(line);
                var data = event.getAuxiliaryData();
                if (event.getEventName().equals("parameter_translated") && KEY.equals(data.get("propertyName")))
                    translated = data.get("propertyValue");
                if (event.getEventName().equals("request_sent")) {
                    String store = data.get("providerIndependentParameterStore");
                    require(translated != null, "No translated timeout before request");
                    require(comparator.compare(store, Map.of(KEY, translated)).matches(), "Saved Duration rejected");
                    String changed = store.replace("PT2M", "PT3M").replace("PT1M30S", "PT1M31S");
                    require(!changed.equals(store), "Mutation did not change saved Duration");
                    require(!comparator.compare(changed, Map.of(KEY, translated)).matches(), "Changed saved Duration accepted");
                    checked++;
                }
            }
            require(checked == SAVED_REQUEST_COUNT, "Expected six saved requests, got " + checked);
        }
        System.out.println("DurationComparisonTest passed");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
