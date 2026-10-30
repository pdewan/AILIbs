package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.GenericTrace;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
import eduAI.trace.processors.cli.CLIPropertyDescriptor.ValueType;

public class CLIPropertyResultChecksTest {
    private enum StudentType { COUNT, FLAG }
    private record Wrapper(Object payload) { }
    private static final class Handler {
        private final String state = "handler contents must not leak";
        @Override public String toString() { throw new AssertionError("Handler toString called"); }
        @Override public int hashCode() { throw new AssertionError("Handler hashCode called"); }
    }
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("cli-property-results-", ".txt");
        boolean output = GenericTrace.isOutputEnabled();
        try {
            GenericTrace.setOutputEnabled(false);
            var expected = Map.of(
                    "timeout", new CLIPropertyDescriptor(ValueType.INTEGER, List.of()),
                    "top_k", new CLIPropertyDescriptor(ValueType.INTEGER, List.of()),
                    "stream", new CLIPropertyDescriptor(ValueType.BOOLEAN, List.of("true", "false")),
                    "current_models", new CLIPropertyDescriptor(ValueType.STRING_LIST, List.of("gemini-3.1-flash-lite")));
            for (int mutation = 0; mutation <= 8; mutation++) {
                emit(file, mutation);
                var processor = new NativeTraceFilesProcessor(file);
                processor.processTraceFiles();
                var records = new ArrayList<>(processor.getTraceRecords().values());
                Collections.reverse(records); // Public record maps need not preserve chronology.
                var result = CLIPropertyResultChecks.check(records, CLIPropertyCheckerRegistry.fromDescriptors(expected), expected.keySet());
                if (result.passed() != (mutation == 0 || mutation == 7)) throw new AssertionError("mutation=" + mutation + " " + result);
                String trace = Files.readString(file);
                if (trace.contains("handler contents must not leak")) throw new AssertionError("Expanded handler");
            }
            if (!EssentialTraceEvents.all().contains("property_handler_results")) throw new AssertionError("Filter drops results");
            System.out.println("CLIPropertyResultChecksTest: heterogeneous and compact returns passed; 7 mutations rejected");
        } finally {
            GenericTrace.clearTraceFile();
            GenericTrace.setOutputEnabled(output);
            Files.deleteIfExists(file);
        }
    }
    private static void emit(Path file, int mutation) {
        GenericTrace.setTraceFile(file);
        Object registry = new Object();
        for (String property : List.of("timeout", "top_k", "stream")) {
            Object handler = new Handler();
            if (!(mutation == 6 && property.equals("top_k"))) {
                CLITraceActions.tracePropertyHandlerFetched(CLIPropertyResultChecksTest.class, property, registry, handler);
            }
            Object type = property.equals("timeout") ? StudentType.COUNT
                    : property.equals("top_k") ? new Wrapper("COUNT") : new Wrapper(StudentType.FLAG);
            Object alternatives = property.equals("stream") ? new Wrapper(new boolean[]{true, false}) : new String[0];
            if (property.equals("stream") && mutation == 7) alternatives = new Wrapper("true, false");
            if (property.equals("top_k") && mutation == 1) type = new Wrapper("different-count-type");
            if (property.equals("stream") && mutation == 2) alternatives = List.of(true);
            if (property.equals("top_k") && mutation == 3) handler = new Handler();
            if (property.equals("top_k") && mutation == 4) continue;
            if (property.equals("stream") && mutation == 5) type = "COUNT";
            CLITraceActions.tracePropertyHandlerResults(CLIPropertyResultChecksTest.class,
                    property, handler, type, alternatives);
        }
        Object modelHandler = new Handler();
        CLITraceActions.tracePropertyHandlerFetched(CLIPropertyResultChecksTest.class, "current_models", registry, modelHandler);
        String model = mutation == 8 ? "gemini-3.2-flash-lite" : "gemini-3.1-flash-lite";
        CLITraceActions.tracePropertyHandlerResults(CLIPropertyResultChecksTest.class, "current_models", modelHandler,
                "MODEL_LIST", List.of(model));
        GenericTrace.clearTraceFile();
    }
}
