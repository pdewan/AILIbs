package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.*;

/** Chronological matching and history plumbing shared by every registered command checker. */
public final class CLICommandResultChecks {
    private CLICommandResultChecks() { }
    public static TraceCheckResult check(Collection<TraceRecord> records, CLIMixedIO io,
            CLICommandResultCheckerRegistry registry, String selectedCommand) {
        return check(records,io,registry,selectedCommand,entry->true);
    }
    public static TraceCheckResult check(Collection<TraceRecord> records, CLIMixedIO io,
            CLICommandResultCheckerRegistry registry, String selectedCommand,
            java.util.function.Predicate<CLIMixedIO.Entry> selectedInput) {
        List<String> errors = new ArrayList<>();
        if (!registry.contains(selectedCommand)) return new TraceCheckResult(List.of(), List.of(),
                List.of("No command-result checker registered for command=" + selectedCommand));
        errors.addAll(registry.checkSession(selectedCommand,io));
        var selectedNumbersForErrors=io.entries().stream()
            .filter(e->selectedCommand.equals(e.input().commandName()) && selectedInput.test(e))
            .map(CLIMixedIO.Entry::number).collect(java.util.stream.Collectors.toSet());
        var traceHistory = CLICommandTraceHistory.read(records,op->selectedNumbersForErrors.contains(op.number())
            || selectedCommand.equals(op.command()) && io.entries().stream().noneMatch(e->e.number()==op.number()));
        errors.addAll(traceHistory.errors());
        Map<String, List<TraceRecord>> returns = new LinkedHashMap<>();
        Map<TraceRecord, List<TraceRecord>> intervals = new IdentityHashMap<>();
        for (var operation : traceHistory.operations()) {
            returns.computeIfAbsent(operation.command(), ignored -> new ArrayList<>()).add(operation.returned());
            intervals.put(operation.returned(), operation.traces());
        }
        var selectedEntries=io.entries().stream().filter(e -> selectedCommand.equals(e.input().commandName()) && selectedInput.test(e)).toList();
        long expectedCount = selectedEntries.size();
        var selectedNumbers=selectedEntries.stream().map(CLIMixedIO.Entry::number).collect(java.util.stream.Collectors.toSet());
        var allNumbers=io.entries().stream().filter(e->selectedCommand.equals(e.input().commandName()))
            .map(CLIMixedIO.Entry::number).collect(java.util.stream.Collectors.toSet());
        int actualCount = (int)returns.getOrDefault(selectedCommand, List.of()).stream().filter(r->
            !CLITraceEvidence.is(r,eduAI.trace.cli.MinimalCLITrace.Event.COMMAND_HANDLED)
            || selectedNumbers.contains(CLITraceEvidence.number(r.getTraceLine().getAuxiliaryData()))
            || !allNumbers.contains(CLITraceEvidence.number(r.getTraceLine().getAuxiliaryData()))).count();
        if (expectedCount == 0) errors.add("No inputs available for command=" + selectedCommand);
        if (actualCount != expectedCount) errors.add("Command-result count for " + selectedCommand
                + ": expected=" + expectedCount + " actual=" + actualCount);
        Map<String, Integer> indices = new HashMap<>();
        List<CLIMixedIO.Entry> history = new ArrayList<>();
        Map<Integer, List<String>> priorResponses = new LinkedHashMap<>();
        Map<Integer, CLIResultEvidence> priorResults = new LinkedHashMap<>();
        for (var entry : io.entries()) {
            var input = entry.input();
            String command = input.commandName();
            if (CLICommandNames.NEW.equals(command)) {
                history.clear(); priorResponses.clear(); priorResults.clear();
                continue;
            }
            int index = indices.merge(command, 1, Integer::sum) - 1;
            var commandReturns = returns.getOrDefault(command, List.of());
            // Modern traces identify commands explicitly, so a missing unselected
            // view cannot shift the selected view's return pairing.
            var numbered=commandReturns.stream().filter(r->CLITraceEvidence.is(r,eduAI.trace.cli.MinimalCLITrace.Event.COMMAND_HANDLED)).toList();
            var record=numbered.isEmpty() ? (index<commandReturns.size()?commandReturns.get(index):null)
                : numbered.stream().filter(r->CLITraceEvidence.number(r.getTraceLine().getAuxiliaryData())==entry.number()).findFirst().orElse(null);
            if (record != null) {
                if (CLITraceEvidence.is(record, eduAI.trace.cli.MinimalCLITrace.Event.COMMAND_HANDLED)
                        && CLITraceEvidence.number(record.getTraceLine().getAuxiliaryData()) != entry.number())
                    errors.add("Command return/input number mismatch at input [" + entry.number() + "]");
                String dump = CLITraceEvidence.resultDump(record);
                var evidence = new CLIResultEvidence(dump);
                var traces = intervals.get(record);
                var responses = completedResponses(traces);
                var context = new CLICommandResultContext(entry, history, evidence, responses,
                        priorResponses, priorResults, traces);
                if (selectedCommand.equals(command) && selectedInput.test(entry)) {
                    String where = " input [" + entry.number() + "] " + record.getTraceFileName() + ":" + record.getTraceFileLineNumber();
                    if (evidence.values().isEmpty()) errors.add("Missing flattened command result" + where);
                    for (String error : registry.check(command, context)) errors.add(error + where);
                }
                priorResults.put(input.inputOrdinal(), evidence);
                if (input.kind() == CLIInteraction.Kind.PROMPT) {
                    priorResponses.put(input.inputOrdinal(), responses.stream()
                            .map(response -> historyResponseEvidence(response,entry,evidence)).toList());
                }
            }
            history.add(entry);
        }
        return new TraceCheckResult(List.of(), errors.isEmpty()
                ? List.of("Registered result checks passed for command=" + selectedCommand) : List.of(), errors);
    }

    /** Full text from captured output or a traced return may represent a compact
     * model fingerprint only after matching its length, edges and digest.
     * Neither representation's correctness is a prerequisite: each is verified.
     * Without a verified full representation, retain the original model evidence;
     * never invent text or accept an abbreviated prefix as a full response match. */
    private static String historyResponseEvidence(String response,CLIMixedIO.Entry entry,CLIResultEvidence returned) {
        var representations=new ArrayList<CLIResultEvidence>();
        representations.add(returned);
        entry.output().ifPresent(output->representations.add(CLIResultEvidence.printed(output.body())));
        String verified=response;
        for(var representation:representations) {
            var matched=representation.matchingText(response);
            if(matched.isPresent()) verified=matched.get();
        }
        return verified;
    }

    /** Executes registered rules without an input file or a required session script. */
    public static TraceCheckResult checkTraces(Collection<TraceRecord> records,
            CLICommandResultCheckerRegistry registry, String selectedCommand) {
        if (!registry.contains(selectedCommand)) return new TraceCheckResult(List.of(), List.of(),
                List.of("No command-result checker registered for command=" + selectedCommand));
        var history = CLICommandTraceHistory.read(records,op->belongsTo(op,selectedCommand));
        var errors = new ArrayList<>(history.errors());
        int observed = 0;
        for (var operation : history.operations()) {
            if (!selectedCommand.equals(operation.command())) continue;
            observed++;
            var evidence = operation.result();
            if (evidence.values().isEmpty()) errors.add("Missing flattened command result");
            var context = new CLICommandResultContext(Optional.empty(), List.of(), evidence,
                    completedResponses(operation.traces()), Map.of(), Map.of(), operation.traces());
            for (String error : registry.check(selectedCommand, context))
                errors.add(error + " command=" + operation.number());
        }
        if (observed == 0) errors.add("Insufficient evidence: no returns for command=" + selectedCommand);
        return new TraceCheckResult(List.of(), List.of(), errors);
    }

    /** An unnamed malformed return with relevant evidence must fail, not disappear.
     * This diagnoses its missing name; it never invents a command name. */
    private static boolean belongsTo(CLICommandTraceHistory.Operation op,String command) {
        if(command.equals(op.command())) return true;
        if(op.command()!=null) return false;
        return op.traces().stream().anyMatch(r->
            command.equals(CLICommandNames.PROPERTY) && CLITraceEvidence.is(r,eduAI.trace.cli.MinimalCLITrace.Event.PROPERTY_HANDLED)
            || command.equals(CLICommandNames.PROMPT) && r.getTraceLine().getEventName().equals("request_sent"));
    }

    private static List<String> completedResponses(List<TraceRecord> records) {
        Map<String, List<String>> byModel = new LinkedHashMap<>();
        String model = "";
        for (var record : records) {
            if (record.getExtractedData().get("providerIndependentCanonicalRequest") instanceof CanonicalTraceRequest request)
                model = request.model();
            if (record.getExtractedData().get("providerIndependentCanonicalResponse") instanceof CanonicalTraceResponse response) {
                var texts = response.messages().stream().flatMap(message -> message.parts().stream())
                        .filter(part -> "text".equals(part.kind())).map(part -> part.value()).filter(text -> !text.isBlank()).toList();
                if (!texts.isEmpty()) byModel.put(model, texts);
            }
        }
        return byModel.values().stream().flatMap(List::stream).toList();
    }
}
