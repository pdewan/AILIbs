package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.*;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.cli.TraceDataPaths;
import static eduAI.trace.cli.MinimalCLITrace.*;
import static eduAI.trace.processors.cli.MinimalCLIContractChecks.*;

/** No input script or I/O file is read by these consistency rules. */
public final class CLIPropertyConsistencyChecks {
    public enum Check { REGISTRATION, HANDLER_REUSE, DISTINCT_HANDLERS, RESULT_TYPES, ALTERNATIVES, GETTERS, STORE, STORE_IDENTITY }
    public static final String INSUFFICIENT = "Insufficient evidence: ";
    private static final int MINIMUM_COMPARABLE_OBSERVATIONS = 2;
    private CLIPropertyConsistencyChecks() { }

    public static TraceCheckResult check(Check check, Collection<TraceRecord> records,
            CLIPropertyCheckerRegistry contracts) {
        var history = check==Check.GETTERS || check==Check.STORE
            ? CLIPropertyTraceHistory.read(records) : CLIPropertyTraceHistory.returns(records);
        List<String> errors = new ArrayList<>(history.errors());
        var operations = history.operations();
        var rows = operations.stream().map(CLIPropertyTraceHistory.Operation::fields).toList();
        if (rows.isEmpty()) errors.add(INSUFFICIENT + "no property returns");
        Map<String,String> handlers = new HashMap<>(), owners = new HashMap<>();
        Map<String,List<String>> settings = new LinkedHashMap<>();
        Map<String,String> registries = new HashMap<>();
        String store = null;
        int comparisons = 0;
        boolean preservedOtherProperty = false;
        for (var operation : operations) {
            var row = operation.fields();
            String name=row.get(NAME), handler=row.get(HANDLER);
            // Store identity uses only the store reference; all other rules identify properties.
            if (check != Check.STORE_IDENTITY && (name == null || name.isBlank())) {
                errors.add("Missing property name");
                continue;
            }
            var value=values(row,VALUE);
            switch (check) {
                case REGISTRATION -> {
                    String current=row.get(REGISTRY);
                    if (!identity(current) || !identity(handler) || !identity(row.get(CALLER))
                            || !Objects.equals(current,row.get(REGISTRY+TraceDataPaths.ID))
                            || !mapsIdentity(prefix(row,REGISTRY),name,handler))
                        errors.add("Registry does not map property to returning handler: " + name);
                    String registry=registries.putIfAbsent(name,current);
                    if (registry!=null && !registry.equals(current)) errors.add("Property registry changed: " + name);
                }
                case HANDLER_REUSE -> {
                    String previous=handlers.putIfAbsent(name,handler);
                    if (!identity(handler)) errors.add("Missing handler identity: " + name);
                    if (previous!=null) {
                        comparisons++;
                        if (!previous.equals(handler)) errors.add("Property handler changed: " + name);
                    }
                }
                case DISTINCT_HANDLERS -> {
                    handlers.put(name,handler);
                    if (!identity(handler)) errors.add("Missing handler identity: " + name);
                    String previous=owners.putIfAbsent(handler,name);
                    if (previous!=null && !previous.equals(name)) errors.add("Properties share handler: " + previous + ", " + name);
                }
                case GETTERS -> {
                    if (value.isEmpty()) errors.add("Missing property value: " + name);
                    if (operation.setter()) settings.put(name,value);
                    else if (settings.containsKey(name)) {
                        comparisons++;
                        if (!settings.get(name).equals(value)) errors.add("Getter differs from preceding setter: " + name);
                    }
                }
                case STORE_IDENTITY -> {
                    String current=row.get(STORE+TraceDataPaths.ID);
                    if (!identity(current) || store!=null && !store.equals(current)) errors.add("Property store identity changed or missing: " + name);
                    store=current;
                }
                case STORE -> {
                    if(operation.setter() && settings.keySet().stream().anyMatch(key->!key.equals(name)))
                        preservedOtherProperty=true;
                    if (value.isEmpty()) errors.add("Missing property value: " + name);
                    if (operation.setter() || !settings.containsKey(name)) settings.put(name,value);
                    for (var setting:settings.entrySet())
                        if (!mappedValues(row,STORE,setting.getKey(),setting.getValue()))
                            errors.add("Store lost or changed accumulated value: " + setting.getKey());
                }
                default -> { }
            }
        }
        switch (check) {
            case STORE -> {
                if(!preservedOtherProperty) errors.add(INSUFFICIENT+"need a setter after observing another property to test preservation");
            }
            case HANDLER_REUSE -> { if (comparisons==0) errors.add(INSUFFICIENT+"no repeated property handler"); }
            case DISTINCT_HANDLERS -> { if (handlers.size()<MINIMUM_COMPARABLE_OBSERVATIONS) errors.add(INSUFFICIENT+"fewer than two properties"); }
            case GETTERS -> { if (comparisons==0) errors.add(INSUFFICIENT+"no setter followed by a getter for the same property"); }
            case STORE_IDENTITY -> { if (rows.size()<MINIMUM_COMPARABLE_OBSERVATIONS) errors.add(INSUFFICIENT+"fewer than two store observations"); }
            case RESULT_TYPES -> types(rows,contracts,errors);
            case ALTERNATIVES -> alternatives(rows,errors);
            default -> { }
        }
        return new TraceCheckResult(List.of(),List.of(),errors);
    }

    private static void types(List<Map<String,String>> rows, CLIPropertyCheckerRegistry contracts, List<String> errors) {
        Map<CLIPropertyDescriptor.ValueType,Set<String>> groups=new HashMap<>();
        var normalized=new ArrayList<Map<String,String>>();
        for (var row:rows) {
            String name=row.get(NAME);
            var contract=contracts.get(name);
            if (contract==null) { errors.add("No property type contract: "+name); continue; }
            groups.computeIfAbsent(contract.descriptor().type(), key->new HashSet<>()).add(contract.canonicalName());
            var copy=new LinkedHashMap<>(row);
            copy.put(CLITraceActions.PROPERTY_NAME,name);
            normalized.add(copy);
            if (!identity(row.get(RESULT+TraceDataPaths.ID)) || Objects.equals(row.get(HANDLER),row.get(RESULT+TraceDataPaths.ID)))
                errors.add("Missing or invalid property result identity: "+name);
        }
        if (groups.size()<MINIMUM_COMPARABLE_OBSERVATIONS || groups.values().stream().noneMatch(names->names.size()>1))
            errors.add(INSUFFICIENT+"need different type groups and distinct properties of a shared type");
        if (CLIPropertyResultFields.typeField(normalized,contracts)==null)
            errors.add("Missing or ambiguous consistent type field");
    }

    private static void alternatives(List<Map<String,String>> rows,List<String> errors) {
        String path=CLIPropertyResultFields.choicesFieldFromSelections(rows,errors);
        if(path==null) return;
        Map<String,List<String>> previous=new HashMap<>();
        boolean compared=false;
        for (var row:rows) {
            var choices=CLIPropertyResultFields.choices(row,path);
            String name=row.get(NAME);
            if (choices==null) { errors.add("Missing choices field: "+name); continue; }
            var old=previous.putIfAbsent(name,choices);
            if (old!=null && !choices.isEmpty()) compared=true;
            if (old!=null && !old.equals(choices)) errors.add("Alternatives changed: "+name);
            if (!choices.isEmpty() && !choices.containsAll(values(row,VALUE))) errors.add("Selected value absent from alternatives: "+name);
        }
        if (!compared) errors.add(INSUFFICIENT+"no repeated nonempty alternatives to compare");
    }

    public static boolean mappedValues(Map<String,String> row,String root,String name,List<String> expected) {
        String ending=".key"+TraceDataPaths.VALUE;
        return prefix(row,root).entrySet().stream().anyMatch(entry->entry.getKey().endsWith(ending)
            && Objects.equals(name,entry.getValue()) && values(row,entry.getKey().substring(0,entry.getKey().length()-ending.length())+".value").equals(expected));
    }
    private static boolean identity(String value) { return value!=null && value.matches(".+@[0-9a-f]+"); }
}
