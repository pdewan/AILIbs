package eduAI.trace.processors.cli;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.TraceCheckResult;
import eduAI.trace.processors.TraceRecord;

/** Assignment command roles and instance checks, independent of student classes. */
public final class CLICommandHandlerChecks {
    public static final String REGISTRY_TAG = "CommandHandlerRegistry";
    public static final String PROMPT_TAG = "PromptCommandHandler";
    public static final String PROPERTY_TAG = "PropertyCommandHandler";
    public static final String TRANSCRIPT_TAG = "TranscriptCommandHandler";
    public static final String MESSAGES_TAG = "MessagesCommandHandler";
    public static final Map<String, String> COMMAND_TAGS = Map.of(
            CLICommandNames.PROMPT, PROMPT_TAG, CLICommandNames.PROPERTY, PROPERTY_TAG,
            CLICommandNames.TRANSCRIPT, TRANSCRIPT_TAG, CLICommandNames.MESSAGES, MESSAGES_TAG);
    private CLICommandHandlerChecks() { }

    public enum Check { REGISTRATION, REUSE }

    /** Observed commands are checked; assignment coverage belongs to the input policy. */
    public static TraceCheckResult check(Collection<TraceRecord> records, Check check) {
        var errors = new ArrayList<String>();
        var firstHandlers = new LinkedHashMap<String,String>();
        var pendingLookups = new LinkedHashMap<String,String>();
        String registry = null, dispatcher = null, file = null;
        int observed = 0, comparisons = 0;
        for (var record : records.stream().sorted(java.util.Comparator.comparing(TraceRecord::getTraceFileName)
                .thenComparingInt(TraceRecord::getTraceFileLineNumber)).toList()) {
            if (!java.util.Objects.equals(file, record.getTraceFileName())) {
                firstHandlers.clear(); pendingLookups.clear();
                registry = null; dispatcher = null; file = record.getTraceFileName();
            }
            var line = record.getTraceLine(); var data = line.getAuxiliaryData();
            boolean combined = CLITraceEvidence.is(record, eduAI.trace.cli.MinimalCLITrace.Event.COMMAND_HANDLED);
            boolean fetched = CLITraceActions.Event.COMMAND_HANDLER_FETCHED.eventName().equals(line.getEventName());
            boolean returned = combined || CLITraceActions.Event.COMMAND_HANDLER_RESULTS.eventName().equals(line.getEventName());
            if (!fetched && !returned) continue;
            String command = combined ? CLITraceEvidence.commandName(record) : data.get(CLITraceActions.COMMAND_NAME);
            String handler = data.get(combined ? eduAI.trace.cli.MinimalCLITrace.HANDLER : CLITraceActions.HANDLER_IDENTITY);
            if (command == null || !identity(handler)) { errors.add("Missing command or handler identity"); continue; }
            if (check == Check.REGISTRATION) {
                if (fetched || combined) {
                    String owner = data.get(combined ? eduAI.trace.cli.MinimalCLITrace.REGISTRY : CLITraceActions.REGISTRY);
                    if (!identity(owner) || registry != null && !registry.equals(owner)) errors.add("Command registry changed or missing");
                    registry = owner;
                    if (combined) {
                        String caller = data.get(eduAI.trace.cli.MinimalCLITrace.CALLER);
                        if (!identity(caller) || dispatcher != null && !dispatcher.equals(caller)) errors.add("Dispatcher identity changed or missing");
                        dispatcher = caller;
                        if (!MinimalCLIContractChecks.commandRegistered(MinimalCLIContractChecks.prefix(data,eduAI.trace.cli.MinimalCLITrace.REGISTRY),
                                data.get(eduAI.trace.cli.MinimalCLITrace.NAME),handler)) errors.add("Registry does not map command to returned handler");
                    }
                    pendingLookups.put(command,handler);
                }
                if (returned && !handler.equals(pendingLookups.remove(command))) errors.add("Returning handler does not match preceding lookup");
            }
            if (!returned) continue;
            observed++;
            if (check == Check.REUSE) {
                String first = firstHandlers.putIfAbsent(command,handler);
                if (first != null) {
                    comparisons++;
                    if (!first.equals(handler)) errors.add("Handler instance changed: command="+command);
                }
            }
        }
        if (observed == 0) errors.add("Insufficient evidence: no command handler returns");
        if (check == Check.REUSE && comparisons == 0) errors.add("Insufficient evidence: no repeated command to compare handler reuse");
        return new TraceCheckResult(List.of(),List.of(),errors);
    }
    private static boolean identity(String value) {
        return value != null && value.contains("@") && !value.endsWith("@null");
    }
}
