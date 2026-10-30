package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.TraceRecord;
import eduAI.trace.cli.MinimalCLITrace;

/** Command boundaries and their preceding evidence, never reconstructed from output text. */
public final class CLICommandTraceHistory {
    public record Operation(TraceRecord returned, List<TraceRecord> traces) {
        public String command() { return CLITraceEvidence.commandName(returned); }
        public int number() { return CLITraceEvidence.number(returned.getTraceLine().getAuxiliaryData()); }
        public CLIResultEvidence result() { return new CLIResultEvidence(CLITraceEvidence.resultDump(returned)); }
    }
    public record History(List<Operation> operations, List<String> errors) { }
    private CLICommandTraceHistory() { }
    public static History read(Collection<TraceRecord> records) {
        return read(records,operation->true);
    }
    /** Keep every boundary, but validate only operations consumed by the caller. */
    public static History read(Collection<TraceRecord> records, java.util.function.Predicate<Operation> selected) {
        var operations = new ArrayList<Operation>();
        var errors = new ArrayList<String>();
        var pending = new ArrayList<TraceRecord>();
        String file = null;
        int previous = 0;
        for (var record : records.stream().sorted(Comparator.comparing(TraceRecord::getTraceFileName)
                .thenComparingInt(TraceRecord::getTraceFileLineNumber)).toList()) {
            if (!Objects.equals(file, record.getTraceFileName())) {
                pending.clear(); previous = 0; file = record.getTraceFileName();
            }
            if (!CLITraceEvidence.commandResult(record)) { pending.add(record); continue; }
            var operation = new Operation(record, List.copyOf(pending));
            if (selected.test(operation) && CLITraceEvidence.is(record, MinimalCLITrace.Event.COMMAND_HANDLED)) {
                if (operation.number() <= previous)
                    errors.add("Command-result count/order: command numbers must be positive and strictly increasing");
                previous = operation.number();
            }
            if (selected.test(operation) && operation.command() == null) errors.add("Missing command name (an empty name denotes a prompt)");
            operations.add(operation); pending.clear();
        }
        return new History(List.copyOf(operations), List.copyOf(errors));
    }
}
