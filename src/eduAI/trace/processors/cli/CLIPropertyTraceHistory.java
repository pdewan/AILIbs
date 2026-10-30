package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.TraceRecord;
import static eduAI.trace.cli.MinimalCLITrace.*;

/** Property operations reconstructed solely from the trace protocol. */
public final class CLIPropertyTraceHistory {
    public record Operation(int command, boolean setter, Map<String,String> fields) { }
    public record History(List<Operation> operations, List<String> errors) { }
    private CLIPropertyTraceHistory() { }

    /** Identity, registry and descriptor rules consume returns, not setter markers. */
    public static History returns(Collection<TraceRecord> records) {
        var operations=new ArrayList<Operation>();var errors=new ArrayList<String>();
        for(var record:records.stream().sorted(Comparator.comparing(TraceRecord::getTraceFileName)
                .thenComparingInt(TraceRecord::getTraceFileLineNumber)).toList()) if(Event.PROPERTY_HANDLED.eventName().equals(record.getTraceLine().getEventName())) {
            var fields=record.getTraceLine().getAuxiliaryData();
            operations.add(new Operation(CLITraceEvidence.number(fields),false,fields));
        }
        return new History(List.copyOf(operations),List.copyOf(errors));
    }
    public static History read(Collection<TraceRecord> records) {
        List<Operation> operations = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Map<String,String>> pending = new ArrayList<>();
        Set<Integer> setters = new HashSet<>();
        String file = null;
        for (var record : records.stream().sorted(Comparator.comparing(TraceRecord::getTraceFileName)
                .thenComparingInt(TraceRecord::getTraceFileLineNumber)).toList()) {
            if (!Objects.equals(file, record.getTraceFileName())) {
                if (!pending.isEmpty()) errors.add("Property return without a command boundary");
                pending.clear(); setters.clear(); file = record.getTraceFileName();
            }
            var line = record.getTraceLine();
            var data = line.getAuxiliaryData();
            if (Event.PROPERTY_HANDLED.eventName().equals(line.getEventName())) pending.add(data);
            if (Event.HISTORY_WITH_PARAMETERS_UPDATED.eventName().equals(line.getEventName())) {
                if (pending.isEmpty() || !setters.add(pending.size()-1))
                    errors.add("Setter history marker without a new property return");
            }
            if (Event.COMMAND_HANDLED.eventName().equals(line.getEventName())) {
                int command = CLITraceEvidence.number(data);
                for (int i=0; i<pending.size(); i++)
                    operations.add(new Operation(command, setters.contains(i), pending.get(i)));
                pending.clear(); setters.clear();
            }
        }
        if (!pending.isEmpty()) errors.add("Property return without a command boundary");
        return new History(List.copyOf(operations), List.copyOf(errors));
    }
}
