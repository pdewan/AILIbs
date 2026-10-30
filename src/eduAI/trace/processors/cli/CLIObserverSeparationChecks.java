package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.TraceLine;
import eduAI.trace.cli.MinimalCLITrace.Event;
import static eduAI.trace.cli.MinimalCLITrace.*;
import eduAI.trace.processors.cli.MinimalCLIContractChecks.Finding;

/** Trace-only role separation, shared by the full contract and the grading check. */
public final class CLIObserverSeparationChecks {
    public static final String COMMAND_SEPARATION = "command-separation";
    public static final String STREAM_SEPARATION = "stream-separation";
    public static final String OBSERVER_INSTANCES = "observer-instances";
    public static final String SEPARATION_EVIDENCE = "observer-separation-evidence";
    private CLIObserverSeparationChecks() { }

    public static List<Finding> check(List<TraceLine> traces) {
        Set<String> processors = new HashSet<>(), regular = new HashSet<>(), stream = new HashSet<>();
        List<Finding> errors = new ArrayList<>();
        for (var trace : traces) {
            if (is(trace, Event.COMMAND_DISPLAYED) || is(trace, Event.STREAM_DISPLAYED)) continue;
            for (String role : List.of(CALLER, HANDLER, EXPANDER, OBSERVABLE)) {
                String participant = trace.getAuxiliaryData().get(role);
                if (identity(participant)) processors.add(participant);
            }
        }
        for (var trace : traces) {
            boolean command = is(trace, Event.COMMAND_DISPLAYED);
            if (!command && !is(trace, Event.STREAM_DISPLAYED)) continue;
            String observer = trace.getAuxiliaryData().get(OBSERVER);
            (command ? regular : stream).add(observer);
            if (!identity(observer) || processors.contains(observer))
                errors.add(new Finding(command ? COMMAND_SEPARATION : STREAM_SEPARATION, "Observer is missing or is a processor"));
        }
        if (processors.isEmpty() || regular.isEmpty() || stream.isEmpty())
            errors.add(new Finding(SEPARATION_EVIDENCE, "Requires processing identities and both command and stream observers"));
        if (!Collections.disjoint(regular, stream))
            errors.add(new Finding(OBSERVER_INSTANCES, "Command and stream observer are the same object"));
        return errors;
    }
    private static boolean is(TraceLine trace, Event event) { return trace.getEventName().equals(event.eventName()); }
    private static boolean identity(String value) { return value != null && value.contains("@") && !value.endsWith("@null"); }
}
