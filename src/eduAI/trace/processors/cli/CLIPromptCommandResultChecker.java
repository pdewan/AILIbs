package eduAI.trace.processors.cli;

import java.util.*;

/** Compares actual model response evidence; never predicts a model's answer. */
public final class CLIPromptCommandResultChecker implements CLICommandResultChecker {
    @Override public List<String> check(CLICommandResultContext context) {
        if (context.responses().isEmpty()) return List.of("No completed model response evidence available for this prompt");
        List<String> errors = new ArrayList<>();
        for (String response : context.responses())
            if (!context.result().contains(response)) errors.add("Prompt result differs from the recorded model response");
        return errors;
    }
}
