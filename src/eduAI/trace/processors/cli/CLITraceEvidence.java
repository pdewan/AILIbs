package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.cli.MinimalCLITrace;
import eduAI.trace.cli.TraceDataPaths;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.TraceRecord;

/** Shared access to return evidence; preserves the older trace format for A1 fixtures. */
public final class CLITraceEvidence {
    private CLITraceEvidence() { }
    public static boolean sixCalls(Collection<TraceRecord> records) {
        return records.stream().anyMatch(r -> is(r, MinimalCLITrace.Event.COMMAND_HANDLED));
    }
    public static boolean is(TraceRecord record, MinimalCLITrace.Event event) {
        return event.eventName().equals(record.getTraceLine().getEventName());
    }
    public static boolean commandResult(TraceRecord record) {
        return is(record, MinimalCLITrace.Event.COMMAND_HANDLED)
            || CLITraceActions.Event.COMMAND_HANDLER_RESULTS.eventName().equals(record.getTraceLine().getEventName());
    }
    public static String commandName(TraceRecord record) {
        return CLICommandNames.role(record.getTraceLine().getAuxiliaryData().get(is(record, MinimalCLITrace.Event.COMMAND_HANDLED)
            ? MinimalCLITrace.NAME : CLITraceActions.COMMAND_NAME));
    }
    public static String resultDump(TraceRecord record) {
        return is(record, MinimalCLITrace.Event.COMMAND_HANDLED)
            ? leafDump(record.getTraceLine().getAuxiliaryData(), MinimalCLITrace.RESULT)
            : record.getTraceLine().getAuxiliaryData().get(CLITraceActions.COMMAND_RESULT);
    }
    public static String leafDump(Map<String,String> data, String root) {
        return eduAI.trace.TraceObjectPrinter.formatFullEvidence(root,
            MinimalCLIContractChecks.values(data,root));
    }
    public static int number(Map<String,String> data) {
        try { return Integer.parseInt(data.get(MinimalCLITrace.COMMAND_NUMBER)); }
        catch (RuntimeException e) { return -1; }
    }
}
