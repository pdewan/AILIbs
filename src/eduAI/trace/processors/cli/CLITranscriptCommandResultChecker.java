package eduAI.trace.processors.cli;

import java.util.*;

/** Transcript inputs and retained outputs are checked against the history before this command. */
public final class CLITranscriptCommandResultChecker implements CLICommandResultChecker {
    @Override public List<String> check(CLICommandResultContext context) {
        if (!context.current().input().argument().isBlank()) return List.of("Transcript result expectations unavailable for these options");
        List<String> errors = new ArrayList<>();
        errors.addAll(CLISemanticEvidence.historyExchanges(context,false));
        if (context.result().text().contains("[" + context.current().number() + "] " + context.current().input().rawInput().strip()))
            errors.add("Transcript includes its own executing command");
        for (var entry : context.history()) {
            var input = entry.input();
            boolean labelled = java.util.regex.Pattern.compile("(?m)^\\[\\d+\\] ").matcher(context.result().text()).find();
            boolean present = labelled ? java.util.regex.Pattern.compile("(?m)^\\[" + entry.number() + "\\] +"
                    + java.util.regex.Pattern.quote(input.rawInput().strip()) + "[ \\t]*$").matcher(context.result().text()).find()
                    : context.result().contains(input.rawInput().strip());
            if (!present)
                errors.add("Transcript lacks earlier input [" + entry.number() + "]: " + input.rawInput());
            if (input.kind() == CLIInteraction.Kind.PROMPT) {
                var responses = context.priorResponses().get(input.inputOrdinal());
                if (responses == null || responses.isEmpty()) errors.add("Response history unavailable for input [" + entry.number() + "]");
                else for (String response : responses) if (!context.result().contains(response))
                    errors.add("Transcript lacks earlier response [" + entry.number() + "]");
            }
            if (input.kind() == CLIInteraction.Kind.PROPERTY_SET) {
                var prior = context.priorResults().get(input.inputOrdinal());
                Object value = input.properties().get(input.propertyName());
                if (prior == null) errors.add("Setter return history unavailable for input [" + entry.number() + "]");
                else {
                    // A formatted confirmation is one leaf; structured returns can carry separate values.
                    List<String> confirmations = prior.values().stream().filter(text -> text.contains(input.propertyName())
                            && text.contains(String.valueOf(value)) && !text.equals(input.propertyName())).toList();
                    for (String confirmation : confirmations) if (!context.result().contains(confirmation))
                        errors.add("Transcript lacks setter confirmation [" + entry.number() + "]");
                }
            }
        }
        return errors;
    }
}
