package eduAI.trace.processors.mutations;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import eduAI.properties.AIPropertyNames;
import eduAI.trace.TraceLineParser;
import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.processors.cli.*;
import static eduAI.trace.cli.CLITraceActions.*;

/** Trace-only defects with an explicit intended command checker and diagnostic. */
public final class CLICommandResultMutations {
    private static final String INCORRECT_PARAMETER_VALUE = "-999";
    public static final String EXPECTATION_FILE = "command-result-expectation.tsv";
    public static final String HANDLER_INSTANCES_CHECK = "handler_instances";
    public record Mutation(String command, String diagnostic, List<String> lines) { }
    private CLICommandResultMutations() { }

    public static Map<String, Mutation> create(List<String> correct, CLIMixedIO io, java.nio.file.Path directory) throws java.io.IOException {
        Map<String, List<Integer>> indices = new LinkedHashMap<>();
        for (int i = 0; i < correct.size(); i++) {
            var parsed = TraceLineParser.parse(correct.get(i));
            if (commandReturn(parsed))
                indices.computeIfAbsent(commandName(parsed), ignored -> new ArrayList<>()).add(i);
        }
        Map<String, Mutation> mutations = new LinkedHashMap<>();
        for (String command : List.of(CLICommandNames.PROPERTY, CLICommandNames.PROMPT,
                CLICommandNames.MESSAGES, CLICommandNames.TRANSCRIPT)) {
            int first = indices.get(command).get(0);
            add(mutations, correct, command + "_extra_return", command, "Command-result count",
                    lines -> lines.add(first, lines.get(first)));
            add(mutations, correct, command + "_missing_data", command, "Missing flattened command result",
                    lines -> lines.set(first, replaceResult(lines.get(first), null)));
        }
        String prop = CLICommandNames.PROPERTY, prompt = CLICommandNames.PROMPT;
        String messages = CLICommandNames.MESSAGES, transcript = CLICommandNames.TRANSCRIPT;
        int setter = indices.get(prop).get(0), message = indices.get(messages).get(0);
        int transcriptIndex = indices.get(transcript).get(0);
        var firstSetter = io.entries().stream().filter(e -> e.input().kind() == CLIInteraction.Kind.PROPERTY_SET).findFirst().orElseThrow();
        String property = firstSetter.input().propertyName();
        Object propertyValue = firstSetter.input().properties().get(property);
        String leaf = String.valueOf(propertyValue instanceof List<?> list ? list.get(0) : propertyValue);
        edit(mutations, correct, "property_name", prop, "Returned result lacks property name", setter,
                text -> text.replace(property, "incorrect_property"));
        edit(mutations, correct, "property_value", prop, "Returned result lacks " + property + "=", setter,
                text -> text.replace(leaf, "incorrect_value"));
        edit(mutations, correct, "prompt_response", prompt, "Prompt result differs", indices.get(prompt).get(0),
                text -> "incorrect_response");
        for (String command : List.of(prompt, messages, transcript))
            add(mutations, correct, command + "_response_evidence", command,
                    command.equals(prompt) ? "No completed model response evidence" : "Response history unavailable",
                    lines -> lines.removeIf(line -> "response_translated".equals(TraceLineParser.parse(line).getEventName())));
        edit(mutations, correct, "messages_prompt", messages, "Messages lack earlier prompt", message,
                text -> text.replaceAll("prompt = .*?, parameters =", "prompt = incorrect_prompt, parameters ="));
        String parameter = AIPropertyNames.TIMEOUT;
        edit(mutations, correct, "messages_parameter_name", messages, "Messages lack parameter " + parameter, message,
                text -> text.replace(parameter, "incorrect_parameter"));
        edit(mutations, correct, "messages_parameter_value", messages, "Returned result lacks parameter " + parameter, message,
                text -> text.replaceAll(Pattern.quote(parameter) + "=\\d+", parameter + "=" + INCORRECT_PARAMETER_VALUE));
        edit(mutations, correct, "messages_response", messages, "Messages lack earlier response", message,
                text -> text.replaceAll("response = .*", "response = incorrect_response"));
        edit(mutations, correct, "messages_user_turn", messages, "Messages lack earlier prompt", message,
                text -> text.replace(".user]", ".absent]"));
        edit(mutations, correct, "messages_assistant_turn", messages, "Messages lack earlier response", message,
                text -> text.replace(".assistant]", ".absent]"));
        edit(mutations, correct, "messages_ordinary_system", messages, "Ordinary messages result includes", message,
                text -> text + "\n[1.system] unexpected_system");
        var messageInputs = io.entries().stream().filter(e -> messages.equals(e.input().commandName())).toList();
        int systemOrdinal = -1;
        for (int i = 0; i < messageInputs.size(); i++)
            if ("-system".equals(messageInputs.get(i).input().argument().strip())) { systemOrdinal = i; break; }
        if (systemOrdinal < 0) throw new IllegalArgumentException("Command mutations require messages -system");
        edit(mutations, correct, "messages_system", messages, "Messages -system lacks", indices.get(messages).get(systemOrdinal),
                text -> text.replaceAll("(?m)^\\[\\d+\\.system\\].*(?:\\R|$)", ""));
        edit(mutations, correct, "transcript_input", transcript, "Transcript lacks earlier input", transcriptIndex,
                text -> text.replaceAll("(?m)^\\[\\d+\\] /prop[^\\r\\n]*(?:\\R|$)", ""));
        edit(mutations, correct, "transcript_response", transcript, "Transcript lacks earlier response", transcriptIndex,
                text -> text.replaceAll("@@\\[[^]]+\\] .", "@@[model] X"));
        edit(mutations, correct, "transcript_confirmation", transcript, "Transcript lacks setter confirmation", transcriptIndex,
                text -> text.replaceAll("(?m)^\\[\\d+\\] Set .*?(?:\\R|$)", ""));
        var currentTranscript = io.entries().stream().filter(e -> transcript.equals(e.input().commandName())).findFirst().orElseThrow();
        edit(mutations, correct, "transcript_self", transcript, "Transcript includes its own", transcriptIndex,
                text -> text + "\n[" + currentTranscript.number() + "] " + currentTranscript.input().rawInput().strip());
        add(mutations, correct, "transcript_setter_history", transcript, "Setter return history unavailable",
                lines -> lines.removeIf(line -> {
                    var p = TraceLineParser.parse(line);
                    return commandReturn(p) && prop.equals(commandName(p));
                }));
        for (var entry : io.entries()) {
            if (entry.attachments().isEmpty()) continue;
            String argument = entry.input().argument();
            StringBuilder stripped = new StringBuilder();
            int from = 0;
            for (var attachment : entry.attachments()) {
                stripped.append(argument, from, attachment.start());
                from = attachment.end();
            }
            stripped.append(argument.substring(from));
            String withoutAttachments = stripped.toString().strip();
            if (String.join("", CLIInputAttachments.expand(entry, directory).texts()).strip().equals(withoutAttachments)) continue;
            int lastMessages = indices.get(messages).get(indices.get(messages).size() - 1);
            String label = java.util.regex.Pattern.quote("[" + entry.number() + ".user] prompt = ");
            edit(mutations, correct, "messages_text_attachment_omitted", messages, "Messages lack earlier prompt", lastMessages,
                    text -> text.replaceAll("(?s)(" + label + ").*?(, parameters =)",
                            "$1" + java.util.regex.Matcher.quoteReplacement(withoutAttachments) + "$2"));
            break;
        }
        return Collections.unmodifiableMap(mutations);
    }
    private static void edit(Map<String, Mutation> result, List<String> correct, String name, String command,
            String diagnostic, int index, UnaryOperator<String> edit) {
        var parsed = TraceLineParser.parse(correct.get(index));
        String dump = sixCall(parsed) ? CLITraceEvidence.leafDump(parsed.getAuxiliaryData(), eduAI.trace.cli.MinimalCLITrace.RESULT)
                : parsed.getAuxiliaryData().get(COMMAND_RESULT);
        String original = new CLIResultEvidence(dump).text();
        String changed = edit.apply(original);
        if (changed.equals(original)) throw new IllegalArgumentException("No-op command content mutation: " + name);
        add(result, correct, name, command, diagnostic, lines -> lines.set(index, replaceResult(lines.get(index), changed)));
    }
    private static boolean sixCall(eduAI.trace.TraceLine line) {
        return eduAI.trace.cli.MinimalCLITrace.Event.COMMAND_HANDLED.eventName().equals(line.getEventName());
    }
    private static boolean commandReturn(eduAI.trace.TraceLine line) {
        return sixCall(line) || Event.COMMAND_HANDLER_RESULTS.eventName().equals(line.getEventName());
    }
    private static String commandName(eduAI.trace.TraceLine line) {
        return eduAI.trace.processors.cli.CLICommandNames.role(
            line.getAuxiliaryData().get(sixCall(line)?eduAI.trace.cli.MinimalCLITrace.NAME:COMMAND_NAME));
    }
    private static String replaceResult(String line,String text) {
        var parsed=TraceLineParser.parse(line);
        if(!sixCall(parsed)) return TraceFixtureFields.replace(line,COMMAND_RESULT,
            text==null?null:TraceObjectPrinter.formatFullEvidence("result",text));
        var fields=new LinkedHashMap<>(parsed.getAuxiliaryData());
        String root=eduAI.trace.cli.MinimalCLITrace.RESULT;
        fields.keySet().removeIf(k->k.startsWith(root+".")&&!k.equals(root+eduAI.trace.cli.TraceDataPaths.ID));
        if(text!=null) fields.put(root+eduAI.trace.cli.TraceDataPaths.VALUE,text);
        return TraceFixtureFields.replaceFields(line,fields);
    }
    private static void add(Map<String, Mutation> result, List<String> correct, String name, String command,
            String diagnostic, Consumer<List<String>> edit) {
        var lines = new ArrayList<>(correct);
        edit.accept(lines);
        if (lines.equals(correct)) throw new IllegalArgumentException("No-op command mutation: " + name);
        result.put("command_result_" + name, new Mutation(command, diagnostic, List.copyOf(lines)));
    }
}
