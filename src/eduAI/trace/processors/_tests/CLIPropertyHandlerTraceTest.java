package eduAI.trace.processors._tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import eduAI.trace.GenericTrace;
import eduAI.trace.TraceLineParser;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.NativeTraceFilesProcessor;
import eduAI.trace.processors.EssentialTraceEvents;
import eduAI.trace.processors.cli.CLIPropertyHandlerTags;

public class CLIPropertyHandlerTraceTest {
    private static final class Handler {
        private final String privateState = "must not expand property handler";
        @Override public String toString() { throw new AssertionError("toString called"); }
        @Override public int hashCode() { throw new AssertionError("hashCode called"); }
    }
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("cli-properties-", ".txt");
        boolean output = GenericTrace.isOutputEnabled();
        try {
            GenericTrace.setOutputEnabled(false);
            GenericTrace.setTraceFile(file);
            Object registry = new Object();
            String[] properties = {"current_models", "system_prompt", "temperature", "timeout", "stream", "num_ctx", "top_k"};
            String[] tags = {"CurrentModelsPropertyHandler", "SystemPromptPropertyHandler", "TemperaturePropertyHandler",
                    "TimeoutPropertyHandler", "StreamPropertyHandler", "NumCtxPropertyHandler", "TopKPropertyHandler"};
            for (String property : properties) {
                CLITraceActions.tracePropertyHandlerFetched(CLIPropertyHandlerTraceTest.class,
                        property, registry, new Handler());
            }
            GenericTrace.clearTraceFile();
            String original = Files.readString(file);
            require(!original.contains("must not expand") && !original.contains("fields="), "compact handler evidence");
            for (String line : Files.readAllLines(file)) {
                var parsed = TraceLineParser.parse(line);
                require(parsed != null && parsed.getEventName().equals("property_handler_fetched"), "event contract");
                require(parsed.getAuxiliaryData().containsKey("propertyHandlerIdentity"), "identity retained");
            }
            var processor = read(file);
            for (String tag : tags) require(processor.classesForTag(tag).size() == 1, "tag " + tag);
            require(processor.classesForTag("CLIPropertyHandler").size() == 1, "one reusable implementation class");
            require(CLIPropertyHandlerTags.checkSeparation(processor).passed(), "property roles may share a class");
            require(CLIPropertyHandlerTags.checkLookups(processor, List.of(properties)).passed(), "expected properties found");
            require(EssentialTraceEvents.all().contains("property_handler_fetched"), "filter retention");
            Files.writeString(file, original.replace("propertyName=\"stream\"", "propertyName=\"streaming\""));
            require(read(file).classesForTag("StreamPropertyHandler").size() == 1, "stream alias");
            var lines = Files.readAllLines(file);
            lines.removeIf(line -> line.contains("propertyName=\"timeout\""));
            Files.write(file, lines);
            require(!CLIPropertyHandlerTags.checkLookups(read(file), List.of(properties)).passed(), "missing property rejected");
            GenericTrace.setTraceFile(file);
            CLITraceActions.traceCommandHandlerFetched(Handler.class, "prop", registry, new Object());
            GenericTrace.clearTraceFile();
            String invocation = Files.readString(file);
            String source = TraceLineParser.parse(invocation.trim()).getSourceClassName();
            invocation = invocation.replace(source, processor.classesForTag("CLIPropertyHandler").get(0));
            Files.writeString(file, original + invocation);
            require(!CLIPropertyHandlerTags.checkSeparation(read(file)).passed(), "unrelated dispatcher role rejected");
            Files.writeString(file, original.replace("propertyHandler=", "missingHandler="));
            require(!CLIPropertyHandlerTags.checkLookups(read(file), List.of(properties)).passed(), "missing handler field rejected");
            System.out.println("CLIPropertyHandlerTraceTest passed");
        } finally {
            GenericTrace.clearTraceFile();
            GenericTrace.setOutputEnabled(output);
            Files.deleteIfExists(file);
        }
    }
    private static NativeTraceFilesProcessor read(Path file) {
        var processor = new NativeTraceFilesProcessor(file);
        processor.processTraceFiles();
        return processor;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
