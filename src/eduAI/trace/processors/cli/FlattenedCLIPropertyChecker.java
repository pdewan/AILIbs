package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.ReflectiveValueCollector;

/** Shared A1 leaf matching; no assumptions about a student's Java return types. */
public class FlattenedCLIPropertyChecker implements CLIPropertyChecker {
    @Override public List<String> checkMetadata(CLIPropertyDescriptor expected, String type, String alternatives) {
        var collector = new ReflectiveValueCollector();
        List<String> errors = new ArrayList<>();
        if (collector.collect(type).isEmpty()) errors.add("Missing flattened type information");
        if (alternatives == null || alternatives.isBlank()) errors.add("Missing alternatives return");
        for (String choice : expected.alternatives()) {
            boolean found = collector.matchingValueOffset(alternatives, choice, Set.of()) >= 0
                    || collector.collect(alternatives).stream().anyMatch(value ->
                    collector.matchingSerializedValueOffset(value.value(), choice) >= 0);
            if (!found) errors.add("Missing alternative=" + choice);
        }
        return errors;
    }

    @Override public List<String> checkValue(CLIPropertyDescriptor expected, Object value, String returned) {
        var collector = new ReflectiveValueCollector();
        List<String> errors = new ArrayList<>();
        if (value == null) return List.of("Assigned value expectation unavailable");
        List<?> values = value instanceof List<?> list ? list : List.of(value);
        for (Object leaf : values) {
            if (collector.matchingValueOffset(returned, String.valueOf(leaf), Set.of()) < 0)
                errors.add("Wrong assigned value: expected=" + leaf);
        }
        return errors;
    }
}
