package eduAI.trace.processors._tests;

import java.util.*;
import eduAI.trace.*;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;

public class CLICommandResultChecksTest {
    private record Failure(int mutation, String name, String command, String diagnostic) { }
    private static final List<Failure> FAILURES = List.of(
        new Failure(1, "property_value", "prop", "Returned result lacks timeout="),
        new Failure(2, "prompt_response", "prompt", "Prompt result differs"),
        new Failure(3, "messages_prompt", "messages", "Messages lack earlier prompt"),
        new Failure(4, "messages_parameter_value", "messages", "Returned result lacks parameter timeout"),
        new Failure(5, "messages_system", "messages", "Messages -system lacks"),
        new Failure(6, "transcript_input", "transcript", "Transcript lacks earlier input"),
        new Failure(7, "transcript_response", "transcript", "Transcript lacks earlier response"),
        new Failure(8, "missing_return", "prop", "Command-result count"),
        new Failure(9, "extra_return", "prop", "Command-result count"),
        new Failure(10, "missing_data", "prop", "Missing flattened command result"),
        new Failure(11, "prompt_evidence_unavailable", "prompt", "No completed model response evidence"),
        new Failure(11, "messages_evidence_unavailable", "messages", "Response history unavailable"),
        new Failure(11, "transcript_evidence_unavailable", "transcript", "Response history unavailable"),
        new Failure(12, "ordinary_messages_system", "messages", "Ordinary messages result includes"),
        new Failure(13, "transcript_confirmation", "transcript", "Transcript lacks setter confirmation"),
        new Failure(15, "property_name", "prop", "Returned result lacks property name"),
        new Failure(16, "messages_parameter_name", "messages", "Messages lack parameter timeout"),
        new Failure(17, "messages_response", "messages", "Messages lack earlier response"),
        new Failure(18, "transcript_self", "transcript", "Transcript includes its own"),
        new Failure(19, "setter_history_unavailable", "transcript", "Setter return history unavailable"),
        new Failure(20, "messages_user_turn", "messages", "Messages lack earlier prompt"),
        new Failure(21, "messages_assistant_turn", "messages", "Messages lack earlier response"),
        new Failure(22, "abbreviation_length", "messages", "Messages lack earlier response"),
        new Failure(23, "abbreviation_prefix", "messages", "Messages lack earlier response"));
    private record Wrapper(Object arbitraryData) { }
    private static final String FIRST = "This is the first recorded answer, with enough text to exercise compact response evidence.";
    private static final String SECOND = "This is the second answer.";
    private static final List<String> INPUTS = List.of("/prop timeout = 5", "/prop system_prompt = Be concise.",
            "/prop timeout", "Hello \\@Note.txt", "/prop timeout = 10", "Again", "/messages", "/messages -system", "/transcript");
    private static CLICommandResultCheckerRegistry registry() {
        var registry = new CLICommandResultCheckerRegistry();
        registry.register("prop", new CLIPropertyCommandResultChecker());
        registry.register("prompt", new CLIPromptCommandResultChecker());
        registry.register("messages", new CLIMessagesCommandResultChecker());
        registry.register("transcript", new CLITranscriptCommandResultChecker());
        return registry;
    }
    public static void main(String[] args) throws Exception {
        var parser = CLIProcessors.input(Map.of(
                "timeout", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.INTEGER, List.of()),
                "system_prompt", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.STRING, List.of())),
                Set.of("messages", "transcript"), Set.of());
        var parsed = parser.processLines(INPUTS);
        if (!parsed.succeeded()) throw new AssertionError(parsed.diagnostics());
        var commands = parsed.interaction().orElseThrow().commands();
        var io = new CLIMixedIO(commands.stream().map(command -> new CLIMixedIO.Entry(command.inputOrdinal(), command,
                CLIAttachment.extract(command.argument()), Optional.empty(), List.of())).toList());
        var attachmentDirectory = java.nio.file.Files.createTempDirectory("command-result-attachments-");
        java.nio.file.Files.writeString(attachmentDirectory.resolve("Note.txt"), "attachment");
        var configured = registry();
        configured.register("messages", new CLIMessagesCommandResultChecker(io,attachmentDirectory,
            new CLIAttachmentKinds(Map.of("Note.txt",CLIFileExpansionContentsChecks.Kind.TEXT))));
        for (int mutation : List.of(0, 14)) {
            var records = records(mutation, commands);
            Collections.reverse(records);
            for (String name : List.of("prop", "prompt", "messages", "transcript")) {
                var result = CLICommandResultChecks.check(records, io, configured, name);
                if (!result.passed()) throw new AssertionError(name + " " + result);
            }
        }
        List<String> coverage = new ArrayList<>(List.of("case\tchecker\trequired_diagnostic\tresult"));
        for (var failure : FAILURES) {
            var records = records(failure.mutation(), commands);
            Collections.reverse(records);
            requireFailure(CLICommandResultChecks.check(records, io, configured, failure.command()), failure, coverage);
        }
        requireFailure(CLICommandResultChecks.check(records(0, commands), io, configured, "unregistered"),
                new Failure(0, "missing_registration", "unregistered", "No command-result checker registered"), coverage);
        requireFailure(CLICommandResultChecks.check(List.of(), new CLIMixedIO(List.of()), configured, "prop"),
                new Failure(0, "missing_inputs", "prop", "No inputs available"), coverage);
        for (String name : List.of("messages", "transcript", "prop")) {
            var input = new CLIInteraction.Command(1, 1, "/" + name, CLIInteraction.Kind.COMMAND,
                    name, "-unsupported", name.equals("prop") ? "timeout" : null, Map.of());
            var entry = new CLIMixedIO.Entry(1, input, List.of(), Optional.empty(), List.of());
            var context = new CLICommandResultContext(entry, List.of(), new CLIResultEvidence("value=timeout"),
                    List.of(), Map.of(), Map.of(), List.of());
            var errors = configured.check(name, context);
            requireFailure(new TraceCheckResult(List.of(), List.of(), errors), new Failure(0,
                    name + "_expectations_unavailable", name, name.equals("prop") ? "no property-handler returns"
                    : "result expectations unavailable"), coverage);
        }
        var extended = configured;
        extended.addChecker("prop", context -> List.of("custom rule reached"));
        var result = CLICommandResultChecks.check(records(0, commands), io, extended, "prop");
        if (result.passed() || result.level3ErrorMessages().stream().noneMatch(e -> e.contains("custom rule reached")))
            throw new AssertionError("Registered extension was not executed");
        if (CLICommandResultChecks.check(records(0, commands), io, extended, "unregistered").passed())
            throw new AssertionError("Missing registration silently passed");
        extended.register("inspect", context -> context.result().require("extension result", "custom-data"));
        var customInput = new CLIInteraction.Command(1, 1, "/inspect", CLIInteraction.Kind.COMMAND,
                "inspect", "", null, Map.of());
        var customIO = new CLIMixedIO(List.of(new CLIMixedIO.Entry(1, customInput, List.of(), Optional.empty(), List.of())));
        var customReturn = record(0, "command_handler_results", Map.of("commandName", "inspect", "commandResult",
                TraceObjectPrinter.formatFullEvidence("data", List.of("custom-data"))));
        if (!CLICommandResultChecks.check(List.of(customReturn), customIO, extended, "inspect").passed())
            throw new AssertionError("New command registration required coordinator changes");
        if (args.length > 0) java.nio.file.Files.write(java.nio.file.Path.of(args[0]), coverage);
        System.out.println("CLICommandResultChecksTest: valid controls and extensions passed; " + (coverage.size()-1)
                + " targeted failure assertions passed");
    }
    private static void requireFailure(TraceCheckResult result, Failure failure, List<String> coverage) {
        if (result.passed() || result.level3ErrorMessages().stream().noneMatch(e -> e.contains(failure.diagnostic())))
            throw new AssertionError("Mutation " + failure.name() + " missed intended diagnostic " + failure.diagnostic() + ": " + result);
        coverage.add(failure.name() + "\t" + failure.command() + "\t" + failure.diagnostic() + "\tREJECTED");
    }
    private static ArrayList<TraceRecord> records(int mutation, List<CLIInteraction.Command> commands) {
        var records = new ArrayList<TraceRecord>();
        for (var command : commands) {
            int n = command.inputOrdinal();
            if (command.kind() == CLIInteraction.Kind.PROMPT && mutation != 11) {
                String response = n == 4 ? FIRST : SECOND;
                var record = record(records.size(), "response_translated", Map.of());
                record.putExtractedData("providerIndependentCanonicalResponse", new CanonicalTraceResponse(List.of(
                        new GenericTracesFileProcessor.TraceMessage("ASSISTANT", List.of(
                                new GenericTracesFileProcessor.TracePart("text", TraceTextSummary.from(response).token())))), Map.of()));
                records.add(record);
            }
            if (command.kind() == CLIInteraction.Kind.PROPERTY_SET || command.kind() == CLIInteraction.Kind.PROPERTY_GET) {
                var propertyFields = new LinkedHashMap<String,String>();
                propertyFields.put(eduAI.trace.cli.MinimalCLITrace.NAME,command.propertyName());
                propertyFields.putAll(eduAI.trace.cli.TraceDataPaths.flatten(eduAI.trace.cli.MinimalCLITrace.VALUE,
                    command.properties().get(command.propertyName()),Set.of(),false));
                records.add(record(records.size(),"property_handled",propertyFields));
            }
            if (mutation == 8 && n == 3) continue;
            if (mutation == 19 && n == 1) continue;
            Object result = switch (n) {
                case 1 -> "Set timeout to 5";
                case 2 -> "Set system_prompt to Be concise.";
                case 3 -> Map.of("name", mutation == 15 ? "incorrect_name" : "timeout", "value", mutation == 1 ? 6 : 5);
                case 4 -> mutation == 2 ? "wrong response" : FIRST;
                case 5 -> "Set timeout to 10";
                case 6 -> SECOND;
                case 7, 8 -> messages(n, mutation);
                default -> transcript(mutation);
            };
            var fields = new LinkedHashMap<String, String>();
            fields.put(CLITraceActions.COMMAND_NAME, command.commandName());
            if (!(mutation == 10 && n == 3)) fields.put(CLITraceActions.COMMAND_RESULT,
                    TraceObjectPrinter.formatFullEvidence("result", new Wrapper(result)));
            records.add(record(records.size(), "command_handler_results", fields));
            if (mutation == 9 && n == 3) records.add(record(records.size(), "command_handler_results", fields));
        }
        return records;
    }
    private static String messages(int n, int mutation) {
        String text = "Messages:\n[4.user] prompt = Hello attachment, parameters = timeout=" + (mutation == 4 ? 10 : 5)
                + "\n[4.assistant] response = " + (mutation == 14 ? FIRST.substring(0, 40) + "... length=" + FIRST.length() : FIRST)
                + "\n[6.user] prompt = Again, parameters = timeout=10\n[6.assistant] response = " + SECOND;
        if ((n == 8 && mutation != 5) || mutation == 12) text += "\n[4.system] system_prompt = Be concise.";
        if (mutation == 3) text = text.replace("Hello", "wrong prompt");
        if (mutation == 16) text = text.replace("timeout", "incorrect_parameter");
        if (mutation == 17) text = text.replace(FIRST, "incorrect_response");
        if (mutation == 20) text = text.replace("[4.user]", "[99.user]");
        if (mutation == 21) text = text.replace("[4.assistant]", "[99.assistant]");
        if (mutation == 22) text = text.replace(FIRST, FIRST.substring(0, 40) + "... length=" + (FIRST.length()+1));
        if (mutation == 23) text = text.replace(FIRST, "incorrect prefix... length=" + FIRST.length());
        return text;
    }
    private static String transcript(int mutation) {
        var text = new StringBuilder("Transcript:\n");
        for (int i = 0; i < INPUTS.size() - 1; i++) {
            if (!(mutation == 6 && i == 2)) text.append('[').append(i + 1).append("] ").append(INPUTS.get(i)).append('\n');
        }
        if (mutation != 7) text.append(FIRST).append('\n');
        text.append(SECOND).append('\n');
        if (mutation != 13) text.append("Set timeout to 5\n");
        if (mutation == 18) text.append("[9] /transcript\n");
        return text.append("Set system_prompt to Be concise.\nSet timeout to 10").toString();
    }
    private static TraceRecord record(int index, String event, Map<String, String> data) {
        return new TraceRecord(new TraceRecordId("test", StreamingMode.NON_STREAMING, index),
                new TraceLine("cli", event, "none", "test", "command_dispatch", "StudentDispatcher", data, ""),
                "CLITrace.txt", index + 1);
    }
}
