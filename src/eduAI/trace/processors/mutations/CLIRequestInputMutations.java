package eduAI.trace.processors.mutations;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import eduAI.properties.AIPropertyNames;
import eduAI.trace.TraceLineParser;
import eduAI.trace.processors.cli.*;

/** Changes explicit timeout values and expanded text, retaining the original I/O and attachments. */
public final class CLIRequestInputMutations {
    public static final String EXPECTATION_FILE = "request-input-expectation.tsv";
    private static final String PARAMETERS = "providerIndependentParameterStore";
    private static final String CONTEXT = "providerIndependentContextWindow";
    public record Mutation(CLITraceExpectations.Check check, String diagnostic, List<String> lines) { }
    private CLIRequestInputMutations() { }
    public static Map<String, Mutation> create(List<String> correct, CLIMixedIO io, Path directory) throws IOException {
        List<Integer> requests = new ArrayList<>();
        for (int i = 0; i < correct.size(); i++)
            if ("request_sent".equals(TraceLineParser.parse(correct.get(i)).getEventName())) requests.add(i);
        Map<String, Mutation> result = new LinkedHashMap<>();
        int request = 0;
        String firstTimeout = null;
        Integer firstRequest = null;
        boolean changed = false;
        String attachmentText = null;
        boolean attachmentMutated = false, retainedMutated = false;
        for (var entry : io.entries()) {
            if (entry.input().kind() != CLIInteraction.Kind.PROMPT) continue;
            int index = requests.get(request);
            String timeout = String.valueOf(entry.input().properties().get(AIPropertyNames.TIMEOUT));
            if (firstTimeout == null) { firstTimeout = timeout; firstRequest = index; }
            else if (!changed && !timeout.equals(firstTimeout)) {
                mutate(result, correct, "stale_timeout", index, PARAMETERS, timeout, firstTimeout,
                        CLITraceExpectations.Check.SETTINGS, "Generic parameter store lacks native " + AIPropertyNames.TIMEOUT);
                mutate(result, correct, "retroactive_timeout", firstRequest, PARAMETERS, firstTimeout, timeout,
                        CLITraceExpectations.Check.SETTINGS, "Generic parameter store lacks native " + AIPropertyNames.TIMEOUT);
                changed = true;
            }
            if (attachmentText != null && !retainedMutated) {
                mutate(result, correct, "lost_text_context", index, CONTEXT, attachmentText, "incorrect_attachment",
                        CLITraceExpectations.Check.PROMPTS, "CLI earlier prompt/text attachment history");
                retainedMutated = true;
            }
            for (var attachment : entry.attachments()) {
                byte[] bytes = Files.readAllBytes(directory.resolve(attachment.path()));
                if (javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)) == null && !attachmentMutated) {
                    attachmentText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    mutate(result, correct, "wrong_expanded_text", index, CONTEXT, attachmentText, "incorrect_attachment",
                            CLITraceExpectations.Check.PROMPTS, "CLI prompts");
                    attachmentMutated = true;
                }
            }
            request += ((List<?>) entry.input().properties().get(AIPropertyNames.CURRENT_MODELS)).size();
        }
        if (!changed || !attachmentMutated || !retainedMutated)
            throw new IllegalArgumentException("Request mutations require a timeout change, text attachment, and subsequent prompt.");
        return result;
    }
    private static void mutate(Map<String, Mutation> result, List<String> correct, String name, int index,
            String field, String oldValue, String newValue, CLITraceExpectations.Check check, String diagnostic) {
        String line = correct.get(index);
        String dump = TraceLineParser.parse(line).getAuxiliaryData().get(field);
        boolean parameter = PARAMETERS.equals(field);
        if (parameter && new eduAI.trace.processors.ReflectiveValueCollector().matchingValueOffset(dump, oldValue, Set.of()) < 0) {
            oldValue = String.valueOf(java.util.concurrent.TimeUnit.SECONDS.toMillis(Long.parseLong(oldValue)));
            newValue = String.valueOf(java.util.concurrent.TimeUnit.SECONDS.toMillis(Long.parseLong(newValue)));
        }
        String replacement = parameter ? "value=" + newValue : "value=\"" + newValue + "\"";
        String changed = TraceFixtureFields.replaceLeaf(dump, oldValue, replacement);
        var lines = new ArrayList<>(correct);
        lines.set(index, TraceFixtureFields.replace(line, field, changed));
        if (lines.equals(correct)) throw new IllegalArgumentException("No-op request mutation: " + name);
        result.put("request_input_" + name, new Mutation(check, diagnostic, List.copyOf(lines)));
    }
}
