package eduAI.trace.processors._tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import eduAI.trace.GenericTrace;
import eduAI.trace.TraceLineParser;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.EssentialTraceEvents;
import eduAI.trace.processors.NativeTraceFilesProcessor;

/** Exercises emitted evidence and missing/wrong-source mutations without a model. */
public class CLIDispatcherTraceTest {
    private static final class Participant {
        private final String contents = "must not be reflected";
        @Override public String toString() { throw new AssertionError("toString called"); }
        @Override public int hashCode() { throw new AssertionError("hashCode called"); }
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("cli-dispatcher-", ".txt");
        boolean output = GenericTrace.isOutputEnabled();
        try {
            Participant registry = new Participant();
            Participant handler = new Participant();
            GenericTrace.setOutputEnabled(false);
            GenericTrace.setTraceFile(file);
            CLITraceActions.traceCommandHandlerFetched(CLIDispatcherTraceTest.class,
                    "prop", registry, handler);
            GenericTrace.clearTraceFile();
            String line = Files.readString(file).trim();
            var parsed = TraceLineParser.parse(line);
            require(parsed != null && "command_handler_fetched".equals(parsed.getEventName()), "lookup event");
            require("prop".equals(parsed.getAuxiliaryData().get("commandName")), "lookup key");
            require(parsed.getAuxiliaryData().get("commandHandler").contains(
                    "@" + Integer.toHexString(System.identityHashCode(handler))), "handler identity");
            require(!line.contains("must not be reflected") && !line.contains("fields="), "compact participants");
            require(EssentialTraceEvents.all().contains("command_handler_fetched"), "filter retains lookup");
            require(classes(file).equals(List.of(CLIDispatcherTraceTest.class.getName())), "source tags dispatcher");
            Files.writeString(file, line.replace(CLIDispatcherTraceTest.class.getName() + ")",
                    "student.OtherDispatcher)"));
            require(classes(file).equals(List.of("student.OtherDispatcher")), "source, not participant, supplies role");
            Files.writeString(file, "");
            NativeTraceFilesProcessor missing = new NativeTraceFilesProcessor(file);
            missing.processTraceFiles();
            require(!missing.checkClassExistsForTag("CLICommandDispatcher").passed(), "missing lookup fails discovery");
            checkHandlerRolesAndInstances(file);
            System.out.println("CLIDispatcherTraceTest passed");
        } finally {
            GenericTrace.clearTraceFile();
            GenericTrace.setOutputEnabled(output);
            Files.deleteIfExists(file);
        }
    }

    private static List<String> classes(Path file) {
        NativeTraceFilesProcessor processor = new NativeTraceFilesProcessor(file);
        processor.processTraceFiles();
        return processor.classesForTag("CLICommandDispatcher");
    }

    private static void checkHandlerRolesAndInstances(Path file) throws Exception {
        Participant registry = new Participant();
        Participant[] handlers = {new Participant(), new Participant(), new Participant(), new Participant()};
        String[] commands = {"prompt", "prop", "transcript", "messages"};
        String[] tags = {"PromptCommandHandler", "PropertyCommandHandler", "TranscriptCommandHandler", "MessagesCommandHandler"};
        GenericTrace.setTraceFile(file);
        for (int repetition = 0; repetition < 2; repetition++) {
            for (int i = 0; i < commands.length; i++) {
                CLITraceActions.traceCommandHandlerFetched(CLIDispatcherTraceTest.class, commands[i], registry, handlers[i]);
                CLITraceActions.traceCommandHandlerResults(CLIDispatcherTraceTest.class, commands[i], handlers[i], java.util.Map.of("answer", "returned data"));
            }
        }
        GenericTrace.clearTraceFile();
        String original = Files.readString(file);
        NativeTraceFilesProcessor correct = new NativeTraceFilesProcessor(file);
        correct.processTraceFiles();
        for (String tag : tags) require(correct.classesForTag(tag).size() == 1, "handler role " + tag);
        require(correct.classesForTag("CommandHandlerRegistry").size() == 1, "registry role");
        require(instancesPass(file), "repeated matching handlers pass");
        require(EssentialTraceEvents.all().contains("command_handler_results"), "filter retains results");
        require(!EssentialTraceEvents.all().contains("command_handler_invoked"), "obsolete invocation event removed");
        var returned = TraceLineParser.parse(Files.readAllLines(file).get(1));
        require(!returned.getAuxiliaryData().containsKey("commandHandlerRegistry"), "return has no registry field");
        require(new eduAI.trace.processors.ReflectiveValueCollector().matchingValueOffset(
                returned.getAuxiliaryData().get("commandResult"), "returned data", java.util.Set.of()) >= 0,
                "returned data can be flattened");

        List<String> lines = Files.readAllLines(file);
        // A new instance of the SAME class is fetched and called on the second prompt.
        String oldIdentity = "@" + Integer.toHexString(System.identityHashCode(handlers[0]));
        lines.set(8, lines.get(8).replace(oldIdentity, "@changed"));
        lines.set(9, lines.get(9).replace(oldIdentity, "@changed"));
        Files.write(file, lines);
        require(!instancesPass(file), "same-class replacement must fail");
        Files.writeString(file, original);
        lines = Files.readAllLines(file);
        lines.set(1, lines.get(1).replace(oldIdentity, "@wrongReturn"));
        Files.write(file, lines);
        require(!instancesPass(file), "return from a different instance must fail");
        Files.writeString(file, original);
        lines = Files.readAllLines(file);
        lines.removeIf(line -> line.contains("{command_handler_results}"));
        Files.write(file, lines);
        require(!instancesPass(file), "lookups alone must not prove a handler return");
        NativeTraceFilesProcessor lookupOnly = new NativeTraceFilesProcessor(file);
        lookupOnly.processTraceFiles();
        for (String tag : tags) require(lookupOnly.classesForTag(tag).isEmpty(), "lookups do not tag handlers");
        Files.writeString(file, original);
        lines = Files.readAllLines(file);
        lines.removeIf(line -> line.contains("{command_handler_fetched}"));
        Files.write(file, lines);
        NativeTraceFilesProcessor resultsOnly = new NativeTraceFilesProcessor(file);
        resultsOnly.processTraceFiles();
        for (String tag : tags) require(resultsOnly.classesForTag(tag).size() == 1, "results supply handler tags");
        require(!instancesPass(file), "return without lookup fails identity pairing");
        Files.writeString(file, original.replace("commandHandlerIdentity=", "missingIdentity="));
        require(!instancesPass(file), "missing identity must fail");
    }

    private static boolean instancesPass(Path file) {
        NativeTraceFilesProcessor processor = new NativeTraceFilesProcessor(file);
        processor.processTraceFiles();
        return java.util.Arrays.stream(eduAI.trace.processors.cli.CLICommandHandlerChecks.Check.values()).allMatch(check ->
                eduAI.trace.processors.cli.CLICommandHandlerChecks.check(processor.getTraceRecords().values(),check).passed());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
