package eduAI.trace.processors.mutations;

import java.util.*;
import java.util.function.Consumer;
import eduAI.properties.AIPropertyNames;
import eduAI.trace.TraceLineParser;
import eduAI.trace.processors.ReflectiveValueCollector;
import static eduAI.trace.cli.CLITraceActions.*;

/** Focused setter defects shared by regression tests and generated A2 datasets. */
public final class CLIPropertySetMutations {
    private CLIPropertySetMutations() { }

    public static Map<String, List<String>> create(List<String> correct) {
        List<Integer> setters = new ArrayList<>();
        for (int i = 0; i < correct.size(); i++) {
            var line = TraceLineParser.parse(correct.get(i));
            if (line != null && Event.CLI_PROPERTY_SET.eventName().equals(line.getEventName())) setters.add(i);
        }
        if (setters.size() < 2) throw new IllegalArgumentException("Setter mutations require at least two assignments.");
        Map<String, List<String>> result = new LinkedHashMap<>();
        int first = setters.get(0), second = setters.get(1);
        add(result, correct, "extra_trace", lines -> lines.add(second, lines.get(second)));
        add(result, correct, "reordered_traces", lines -> Collections.swap(lines, first, second));
        add(result, correct, "missing_trace", lines -> lines.remove(second));
        field(result, correct, second, "missing_identity", PROPERTY_STORE_IDENTITY, null);
        field(result, correct, second, "changed_identity", PROPERTY_STORE_IDENTITY, "mutated.Store@1");
        field(result, correct, second, "wrong_name", PROPERTY_NAME, "incorrect_property");
        field(result, correct, second, "missing_value", PROPERTY_VALUE, null);
        field(result, correct, second, "wrong_value", PROPERTY_VALUE, "value=incorrect_value");
        field(result, correct, second, "missing_contents", PROPERTY_STORE_STATE, null);

        String earlierName = data(correct.get(first)).get(PROPERTY_NAME);
        String laterState = data(correct.get(second)).get(PROPERTY_STORE_STATE);
        field(result, correct, second, "lost_earlier_property", PROPERTY_STORE_STATE,
                replaceLeaf(laterState, earlierName));
        String ownName = data(correct.get(second)).get(PROPERTY_NAME);
        field(result, correct, second, "missing_key_value_retained", PROPERTY_STORE_STATE,
                replaceLeaf(laterState, ownName));

        Map<String, String> previous = new HashMap<>();
        boolean stale = false, list = false;
        var collector = new ReflectiveValueCollector();
        for (int index : setters) {
            var fields = data(correct.get(index));
            String name = fields.get(PROPERTY_NAME), value = fields.get(PROPERTY_VALUE);
            String oldValue = previous.put(name, value);
            if (!stale && oldValue != null && !oldValue.equals(value)) {
                String currentLeaf = collector.collect(value).get(0).value();
                String oldLeaf = collector.collect(oldValue).get(0).value();
                field(result, correct, index, "stale_overwritten_value", PROPERTY_STORE_STATE,
                        replaceLeaf(fields.get(PROPERTY_STORE_STATE), currentLeaf, "value=\"" + oldLeaf + "\""));
                stale = true;
            }
            if (!list && AIPropertyNames.CURRENT_MODELS.equals(name)) {
                String model = collector.collect(value).get(0).value();
                field(result, correct, index, "missing_list_value_in_store", PROPERTY_STORE_STATE,
                        replaceLeaf(fields.get(PROPERTY_STORE_STATE), model));
                field(result, correct, index, "missing_list_value_in_result", PROPERTY_VALUE, replaceLeaf(value, model));
                list = true;
            }
        }
        if (!stale || !list) throw new IllegalArgumentException("Setter mutations require an overwrite and a current_models assignment.");
        return result;
    }

    private static Map<String, String> data(String line) {
        return TraceLineParser.parse(line).getAuxiliaryData();
    }
    private static void field(Map<String, List<String>> result, List<String> correct,
            int index, String name, String field, String value) {
        add(result, correct, name, lines -> {
            lines.set(index, TraceFixtureFields.replace(lines.get(index), field, value));
        });
    }
    private static String replaceLeaf(String dump, String value) {
        return replaceLeaf(dump, value, "value=\"mutation_removed_leaf\"");
    }
    private static String replaceLeaf(String dump, String value, String replacement) {
        return TraceFixtureFields.replaceLeaf(dump, value, replacement);
    }
    private static void add(Map<String, List<String>> result, List<String> correct,
            String name, Consumer<List<String>> mutation) {
        var lines = new ArrayList<>(correct);
        mutation.accept(lines);
        if (lines.equals(correct)) throw new IllegalArgumentException("No-op setter mutation: " + name);
        result.put("property_set_" + name, List.copyOf(lines));
    }
}
