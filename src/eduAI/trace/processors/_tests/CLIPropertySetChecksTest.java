package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.GenericTrace;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
import eduAI.trace.processors.mutations.CLIPropertySetMutations;

public class CLIPropertySetChecksTest {
    private static final class Store {
        @Override public int hashCode() { throw new AssertionError("Overridden hashCode called"); }
        @Override public String toString() { throw new AssertionError("Store expanded"); }
    }
    private record State(Object contents) { }
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("cli-property-set-", ".txt");
        boolean output = GenericTrace.isOutputEnabled();
        try {
            GenericTrace.setOutputEnabled(false);
            List<CLIInteraction.Command> commands = new ArrayList<>();
            Map<String, Object> expected = new LinkedHashMap<>();
            var registry = CLIPropertyCheckerRegistry.fromDescriptors(Map.of(
                    "timeout", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.INTEGER, List.of()),
                    "stream", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.BOOLEAN, List.of("true", "false")),
                    "current_models", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.STRING_LIST, List.of())));
            String[] names = {"timeout", "stream", "timeout", "current_models"};
            Object[] values = {300, false, 120, List.of("llava", "gemini")};
            for (int i = 0; i < names.length; i++) {
                expected.put(names[i], values[i]);
                commands.add(new CLIInteraction.Command(i + 1, i + 1, "", CLIInteraction.Kind.PROPERTY_SET,
                        "prop", "", names[i], expected));
            }
            List<String> correct = null;
            for (int mutation = 0; mutation <= 7; mutation++) {
                GenericTrace.setTraceFile(file);
                Object store = new Store();
                Map<String, Object> state = new LinkedHashMap<>();
                for (int i = 0; i < names.length; i++) {
                    state.put(names[i], values[i]);
                    if (i == 1 && mutation == 4) state.remove("timeout");
                    if (i == 2 && mutation == 5) state.put("timeout", 300);
                    if (i == 1 && mutation == 6) continue;
                    CLITraceActions.tracePropertySet(CLIPropertySetChecksTest.class,
                            i == 1 && mutation == 2 ? "wrong" : names[i],
                            i == 1 && mutation == 3 ? true : values[i],
                            i == 1 && mutation == 1 ? new Store() : store,
                            i == 1 && mutation == 7 ? null : new State(new LinkedHashMap<>(state)));
                }
                GenericTrace.clearTraceFile();
                if (mutation == 0) correct = Files.readAllLines(file);
                var processor = new NativeTraceFilesProcessor(file);
                processor.processTraceFiles();
                var records = new ArrayList<>(processor.getTraceRecords().values());
                Collections.reverse(records);
                var result = CLIPropertySetChecks.check(records, commands, registry);
                if (result.passed() != (mutation == 0)) throw new AssertionError("mutation=" + mutation + " " + result);
            }
            var mutations = CLIPropertySetMutations.create(correct);
            var withMetadataSetter = new ArrayList<>(correct);
            withMetadataSetter.add(correct.get(0).replace("{cli_property_set}", "{property_set}"));
            Files.write(file, withMetadataSetter);
            var withMetadata = new NativeTraceFilesProcessor(file);
            withMetadata.processTraceFiles();
            if (!CLIPropertySetChecks.check(withMetadata.getTraceRecords().values(), commands, registry).passed()) {
                throw new AssertionError("A1 metadata setters must not count as CLI assignments");
            }
            for (var mutation : mutations.entrySet()) {
                Files.write(file, mutation.getValue());
                var processor = new NativeTraceFilesProcessor(file);
                processor.processTraceFiles();
                var result = CLIPropertySetChecks.check(processor.getTraceRecords().values(), commands, registry);
                if (result.passed()) throw new AssertionError("Surviving generated mutation: " + mutation.getKey());
            }
            if (!EssentialTraceEvents.all().contains("cli_property_set")) throw new AssertionError("Filter drops setters");
            System.out.println("CLIPropertySetChecksTest: valid baseline passed; 7 original and " + mutations.size() + " generated mutations rejected");
        } finally {
            GenericTrace.clearTraceFile();
            GenericTrace.setOutputEnabled(output);
            Files.deleteIfExists(file);
        }
    }
}
