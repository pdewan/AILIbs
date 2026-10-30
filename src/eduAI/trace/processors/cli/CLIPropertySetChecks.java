package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.*;

/** Checks a single shared-store CLI session using A1's representation-independent comparison. */
public final class CLIPropertySetChecks {
    private CLIPropertySetChecks() { }

    public static TraceCheckResult check(Collection<TraceRecord> records,
            List<CLIInteraction.Command> commands, CLIPropertyCheckerRegistry registry) {
        var expected = commands.stream().filter(c -> c.kind() == CLIInteraction.Kind.PROPERTY_SET).toList();
        var actual = records.stream().filter(r -> CLITraceActions.Event.CLI_PROPERTY_SET.eventName()
                .equals(r.getTraceLine().getEventName()))
                .sorted(Comparator.comparing(TraceRecord::getTraceFileName)
                        .thenComparingInt(TraceRecord::getTraceFileLineNumber)).toList();
        List<String> errors = new ArrayList<>();
        if (expected.isEmpty()) errors.add("No property assignments available in the I/O log.");
        if (actual.size() != expected.size()) errors.add("Property-set trace count: expected="
                + expected.size() + " actual=" + actual.size());
        String storeIdentity = null;
        Map<String, Object> accumulated = new LinkedHashMap<>();
        var comparator = new FlattenedKeyValueComparator();
        for (int i = 0; i < actual.size(); i++) {
            var record = actual.get(i);
            var data = record.getTraceLine().getAuxiliaryData();
            String where = " at " + record.getTraceFileName() + ":" + record.getTraceFileLineNumber();
            String identity = data.get(CLITraceActions.PROPERTY_STORE_IDENTITY);
            if (identity == null || identity.isBlank() || "null".equals(identity)) {
                errors.add("Missing property-store identity" + where);
            } else if (storeIdentity == null) storeIdentity = identity;
            else if (!storeIdentity.equals(identity)) errors.add("Property store changed: expected="
                    + storeIdentity + " actual=" + identity + where);
            if (i >= expected.size()) continue;
            var command = expected.get(i);
            String name = command.propertyName();
            Object value = command.properties().get(name);
            where += " input=" + command.inputOrdinal() + " property=" + name;
            if (!Objects.equals(name, data.get(CLITraceActions.PROPERTY_NAME))) {
                errors.add("Wrong assigned property: actual=" + data.get(CLITraceActions.PROPERTY_NAME) + where);
            }
            if (value == null) {
                errors.add("Assigned value expectation unavailable" + where);
                continue;
            }
            String returned = data.get(CLITraceActions.PROPERTY_VALUE);
            for (String error : registry.checkValue(name, value, returned)) errors.add(error + where);
            accumulated.put(name, value);
            String snapshot = data.get(CLITraceActions.PROPERTY_STORE_STATE);
            if (snapshot == null || snapshot.isBlank()) errors.add("Missing post-set store contents" + where);
            for (var property : accumulated.entrySet()) {
                for (Object leaf : leaves(property.getValue())) {
                    var comparison = comparator.compare(snapshot,
                            Map.of(property.getKey(), String.valueOf(leaf)));
                    if (!comparison.matches()) errors.add("Post-set store lacks " + property.getKey()
                            + "=" + leaf + where);
                }
            }
        }
        return new TraceCheckResult(List.of(), errors.isEmpty()
                ? List.of("All assignments use the same store and preserve accumulated property values.") : List.of(), errors);
    }

    private static List<?> leaves(Object value) {
        return value instanceof List<?> list ? list : List.of(value);
    }
}
