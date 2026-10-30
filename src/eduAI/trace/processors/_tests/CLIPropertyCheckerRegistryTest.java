package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.GenericTrace;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.NativeTraceFilesProcessor;
import eduAI.trace.processors.cli.*;

public class CLIPropertyCheckerRegistryTest {
    public static void main(String[] args) throws Exception {
        var integer = new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.INTEGER, List.of());
        var registry = CLIPropertyCheckerRegistry.fromDescriptors(Map.of("budget", integer, "retries", integer));
        registry.registerAlias("budget_alias", "budget");
        registry.addChecker("budget", new FlattenedCLIPropertyChecker() {
            @Override public List<String> checkValue(CLIPropertyDescriptor expected, Object value, String returned) {
                return ((Number) value).intValue() > 0 ? List.of() : List.of("Budget must be positive");
            }
        });
        require(!registry.checkValue("budget", -1, "payload: student.Value value=-1").isEmpty(), "registered custom rule executes");
        require(!registry.checkValue("budget_alias", -1, "payload: student.Value value=-1").isEmpty(), "alias receives added rules");
        require(!registry.checkValue("budget", 1, "payload: student.Value value=2").isEmpty(), "shared rule still executes");
        require(registry.checkValue("retries", -1, "payload: student.Value value=-1").isEmpty(), "unrelated registration unaffected");
        require(!registry.checkValue("unregistered", 1, "payload: student.Value value=1").isEmpty(), "missing value checker fails");
        require(!registry.checkMetadata("unregistered", "payload: student.Value value=COUNT", "elements=[]").isEmpty(), "missing metadata checker fails");
        registry.setDescriptor("budget", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.INTEGER, List.of("1")));
        require(!registry.checkMetadata("budget_alias", "payload: student.Value value=COUNT", "elements=[]").isEmpty(), "alias shares updated expectations");
        require(registry.checkMetadata("budget", "payload: student.Value value=COUNT", "payload: student.Value value=1").isEmpty(), "registered metadata passes");

        Path file = Files.createTempFile("property-registry-", ".txt");
        boolean output = GenericTrace.isOutputEnabled();
        try {
            GenericTrace.setOutputEnabled(false);
            Object budget = new Object();
            for (boolean shared : List.of(false, true)) {
                GenericTrace.setTraceFile(file);
                Object retries = shared ? budget : new Object();
                emit("budget", budget);
                emit("budget_alias", budget);
                emit("retries", retries);
                GenericTrace.clearTraceFile();
                var processor = new NativeTraceFilesProcessor(file);
                processor.processTraceFiles();
                var result = CLIPropertyResultChecks.check(processor.getTraceRecords().values(), registry,
                        List.of("budget", "budget_alias", "retries"));
                require(result.passed() != shared, "distinct properties need distinct instances; aliases may share: " + result);
            }
        } finally {
            GenericTrace.clearTraceFile();
            GenericTrace.setOutputEnabled(output);
            Files.deleteIfExists(file);
        }
        System.out.println("CLIPropertyCheckerRegistryTest: custom rules, aliases, missing registrations, and handler separation passed");
    }
    private static void emit(String name, Object handler) {
        CLITraceActions.tracePropertyHandlerFetched(CLIPropertyCheckerRegistryTest.class, name, new Object(), handler);
        CLITraceActions.tracePropertyHandlerResults(CLIPropertyCheckerRegistryTest.class, name, handler, "COUNT", List.of("1"));
    }
    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
