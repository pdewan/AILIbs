package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.TraceRecord;

/** Immutable input history and pre-display return evidence available to a registered checker. */
public record CLICommandResultContext(Optional<CLIMixedIO.Entry> input, List<CLIMixedIO.Entry> history,
        CLIResultEvidence result, List<String> responses,
        Map<Integer, List<String>> priorResponses, Map<Integer, CLIResultEvidence> priorResults,
        List<TraceRecord> commandTraces) {
    public CLICommandResultContext {
        Objects.requireNonNull(input);
        history = List.copyOf(history);
        responses = List.copyOf(responses);
        priorResponses = Map.copyOf(priorResponses);
        priorResults = Map.copyOf(priorResults);
        commandTraces = List.copyOf(commandTraces);
    }
    public CLICommandResultContext(CLIMixedIO.Entry current, List<CLIMixedIO.Entry> history,
            CLIResultEvidence result, List<String> responses, Map<Integer,List<String>> priorResponses,
            Map<Integer,CLIResultEvidence> priorResults, List<TraceRecord> traces) {
        this(Optional.of(current), history, result, responses, priorResponses, priorResults, traces);
    }
    public CLIMixedIO.Entry current() {
        return input.orElseThrow(() -> new IllegalStateException("This checker requires actual input evidence"));
    }
}
