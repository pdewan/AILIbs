package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.*;

/** Compares stored leaves, independent of Java classes and student type vocabulary. */
public final class CLIPropertyResultChecks {
    private CLIPropertyResultChecks() { }
    public static TraceCheckResult check(Collection<TraceRecord> records,
            CLIPropertyCheckerRegistry registry, Collection<String> expected) {
        var relationships = new CLIPropertyRelationships();
        Map<String, String> lookups = new HashMap<>();
        Map<String, String> registries = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<String> errors = new ArrayList<>();
        var ordered = records.stream().sorted(Comparator.comparing(TraceRecord::getTraceFileName)
                .thenComparingInt(TraceRecord::getTraceFileLineNumber)).toList();
        var resultRows = ordered.stream().filter(r -> CLITraceActions.Event.PROPERTY_HANDLER_RESULTS.eventName()
                .equals(r.getTraceLine().getEventName()) || CLITraceEvidence.is(r,eduAI.trace.cli.MinimalCLITrace.Event.PROPERTY_HANDLED))
                .map(CLIPropertyResultChecks::fields)
                .filter(d -> expected.contains(d.get(CLITraceActions.PROPERTY_NAME))
                    && d.containsKey(CLITraceActions.PROPERTY_RESULT + eduAI.trace.cli.TraceDataPaths.ID)).toList();
        String typeField = CLIPropertyResultFields.typeField(resultRows, registry);
        var modelRegistration = registry.get(eduAI.properties.AIPropertyNames.CURRENT_MODELS);
        Set<String> knownModels = modelRegistration == null ? Set.of()
                : new HashSet<>(modelRegistration.descriptor().alternatives());
        Set<String> choicesFields = CLIPropertyResultFields.choicesFields(resultRows, knownModels);
        String choicesField = choicesFields.size() == 1 ? choicesFields.iterator().next() : null;
        for (var record : ordered) {
            var line = record.getTraceLine();
            var data = fields(record);
            boolean combined=CLITraceEvidence.is(record,eduAI.trace.cli.MinimalCLITrace.Event.PROPERTY_HANDLED);
            String name = data.get(CLITraceActions.PROPERTY_NAME);
            if (name == null || !expected.contains(name)) continue;
            String identity = data.get(CLITraceActions.PROPERTY_HANDLER_IDENTITY);
            String key = record.getTraceFileName() + ":" + line.getSourceClassName() + ":" + name;
            if (CLITraceActions.Event.PROPERTY_HANDLER_FETCHED.eventName().equals(line.getEventName())) {
                String owner = data.get(CLITraceActions.PROPERTY_REGISTRY);
                String first = registries.putIfAbsent(line.getSourceClassName() + ":" + name, owner);
                if (owner == null || owner.isBlank() || (first != null && !first.equals(owner)))
                    errors.add("Property registry changed or missing: " + name);
                lookups.put(key, identity);
                continue;
            }
            if (!combined && !CLITraceActions.Event.PROPERTY_HANDLER_RESULTS.eventName().equals(line.getEventName())) continue;
            String where = " property=" + name + " record=" + record.getId();
            if (combined) {
                String owner=data.get(eduAI.trace.cli.MinimalCLITrace.REGISTRY);
                String previous=registries.putIfAbsent(name,owner);
                if(owner==null || previous!=null && !previous.equals(owner)
                    || !MinimalCLIContractChecks.mapsIdentity(MinimalCLIContractChecks.prefix(data,eduAI.trace.cli.MinimalCLITrace.REGISTRY),name,identity))
                    errors.add("Property registry/key/handler association:" + where);
            }
            if (identity == null || identity.isBlank() || !combined && !identity.equals(lookups.remove(key))) {
                errors.add("Property results do not match a preceding handler lookup:" + where);
            }
            String type = data.get(CLITraceActions.RETURNED_TYPE);
            String alternatives = data.get(CLITraceActions.RETURNED_ALTERNATIVES);
            if (data.keySet().stream().anyMatch(k -> k.startsWith(CLITraceActions.PROPERTY_RESULT + "."))) {
                String resultIdentity = data.get(CLITraceActions.PROPERTY_RESULT + eduAI.trace.cli.TraceDataPaths.ID);
                if (resultIdentity == null || !resultIdentity.matches(".+@[0-9a-f]+") || resultIdentity.equals(identity))
                    errors.add("Missing or invalid property result identity:" + where);
                if (typeField == null) errors.add("Missing or ambiguous property type field:" + where);
                if (choicesField == null) errors.add("Missing or ambiguous property choices field:" + where);
                type = typeField == null ? null : eduAI.trace.TraceObjectPrinter.format("type", data.get(typeField));
                var choices = CLIPropertyResultFields.choices(data, choicesField);
                alternatives = choices == null ? null : eduAI.trace.TraceObjectPrinter.format("choices", choices);
            }
            for (String error : registry.checkMetadata(name, type, alternatives)) {
                errors.add(error + where);
            }
            var registration = registry.get(name);
            if (registration != null) {
                for (String error : relationships.check(registration, identity, type)) errors.add(error + where);
            }
            seen.add(name);
        }
        for (String name : expected) {
            if (!seen.contains(name)) errors.add("Missing property-handler results: property=" + name);
        }
        if (expected.isEmpty()) errors.add("No property expectations available.");
        return new TraceCheckResult(List.of(), errors.isEmpty()
                ? List.of("Property results contain consistent type information and required alternatives.") : List.of(), errors);
    }
    private static Map<String,String> fields(TraceRecord record) {
        var data=new LinkedHashMap<>(record.getTraceLine().getAuxiliaryData());
        if(CLITraceEvidence.is(record,eduAI.trace.cli.MinimalCLITrace.Event.PROPERTY_HANDLED)) {
            data.put(CLITraceActions.PROPERTY_NAME,data.get(eduAI.trace.cli.MinimalCLITrace.NAME));
            data.put(CLITraceActions.PROPERTY_HANDLER_IDENTITY,data.get(eduAI.trace.cli.MinimalCLITrace.HANDLER));
        }
        return data;
    }
}
