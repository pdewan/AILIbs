package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.cli.TraceDataPaths;
import static eduAI.trace.processors.cli.MinimalCLIContractChecks.*;

/** Infers paths from evidence and expected type groups, never student member names. */
public final class CLIPropertyResultFields {
    private CLIPropertyResultFields() { }

    public static String typeField(List<Map<String,String>> rows, CLIPropertyCheckerRegistry registry) {
        Set<String> candidates = null;
        for (var row : rows) {
            Set<String> paths = new HashSet<>();
            row.keySet().stream().filter(k -> k.startsWith(CLITraceActions.PROPERTY_RESULT + ".")
                && k.endsWith(TraceDataPaths.VALUE) && !k.contains(".items.")).forEach(paths::add);
            if (candidates == null) candidates = paths; else candidates.retainAll(paths);
        }
        if (candidates == null) return null;
        candidates.removeIf(path -> {
            Map<CLIPropertyDescriptor.ValueType,String> byType = new HashMap<>();
            Map<String,CLIPropertyDescriptor.ValueType> byValue = new HashMap<>();
            for (var row : rows) {
                var registration = registry.get(row.get(CLITraceActions.PROPERTY_NAME));
                if (registration == null) return true;
                var type = registration.descriptor().type();
                String value = row.get(path);
                String previous = byType.putIfAbsent(type, value);
                var previousType = byValue.putIfAbsent(value, type);
                if (value == null || value.isBlank() || previous != null && !previous.equals(value)
                    || previousType != null && previousType != type) return true;
            }
            return byType.size() < 2; // One group alone cannot distinguish a type from a constant.
        });
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    public static Set<String> choicesFields(List<Map<String,String>> rows, Set<String> knownModels) {
        if (knownModels.isEmpty()) return Set.of();
        var normalized = rows.stream().map(row -> {
            var copy = new LinkedHashMap<>(row);
            copy.put(eduAI.trace.cli.MinimalCLITrace.NAME, row.get(CLITraceActions.PROPERTY_NAME));
            return (Map<String,String>) copy;
        }).toList();
        return choicesPathCandidates(normalized, eduAI.properties.AIPropertyNames.CURRENT_MODELS, knownModels);
    }

    public static List<String> choices(Map<String,String> row, String path) {
        if (path == null || !row.containsKey(path + TraceDataPaths.ID)
            || row.keySet().stream().anyMatch(k -> k.startsWith(path + ".") && k.endsWith(TraceDataPaths.ERROR)))
            return null;
        return values(row, path);
    }

    /** Locate choices using traced selections, not field names or required-choice guesses. */
    public static String choicesFieldFromSelections(List<Map<String,String>> rows,List<String> errors) {
        String models=eduAI.properties.AIPropertyNames.CURRENT_MODELS;
        Set<String> selected=new HashSet<>();
        rows.stream().filter(row->models.equals(row.get(eduAI.trace.cli.MinimalCLITrace.NAME)))
            .forEach(row->selected.addAll(values(row,eduAI.trace.cli.MinimalCLITrace.VALUE)));
        if(selected.isEmpty()) { errors.add(CLIPropertyConsistencyChecks.INSUFFICIENT+"no selected model evidence"); return null; }
        var candidates=choicesPathCandidates(rows,models,selected);
        if(candidates.size()!=1) { errors.add("Missing or ambiguous stable choices field containing selected models"); return null; }
        return candidates.iterator().next();
    }
}
