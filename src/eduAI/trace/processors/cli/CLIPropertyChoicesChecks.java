package eduAI.trace.processors.cli;
import java.util.*;
import eduAI.trace.processors.*;
import static eduAI.trace.cli.MinimalCLITrace.*;
/** Required choices are checked separately from choices consistency and input matching. */
public final class CLIPropertyChoicesChecks {
    public enum Check { BOOLEAN_CHOICES, MODEL_CHOICES }
    public static final int BOOLEAN_ALTERNATIVE_COUNT=2;
    private CLIPropertyChoicesChecks() { }
    public static TraceCheckResult check(Check check,Collection<TraceRecord> records,CLIPropertyExpectations policy) {
        var history=CLIPropertyTraceHistory.returns(records);
        var errors=new ArrayList<>(history.errors());
        var rows=history.operations().stream().map(CLIPropertyTraceHistory.Operation::fields).toList();
        String path=CLIPropertyResultFields.choicesFieldFromSelections(rows,errors);
        int observed=0;
        if(path!=null) for(var row:rows) {
            String name=row.get(NAME);
            // Unidentified returns cannot establish evidence for this property kind.
            // The dedicated registration/type checks diagnose their missing names.
            if(name==null || name.isBlank()) continue;
            var descriptor=policy.descriptors().get(name);
            boolean relevant=switch(check) {
                case BOOLEAN_CHOICES -> descriptor!=null && descriptor.type()==CLIPropertyDescriptor.ValueType.BOOLEAN;
                case MODEL_CHOICES -> eduAI.properties.AIPropertyNames.CURRENT_MODELS.equals(name);
            };
            if(!relevant) continue;
            observed++;
            var choices=CLIPropertyResultFields.choices(row,path);
            if(choices==null) { errors.add("Missing choices field: "+name); continue; }
            switch(check) {
                case BOOLEAN_CHOICES -> {
                    if(choices.size()!=BOOLEAN_ALTERNATIVE_COUNT || new HashSet<>(choices).size()!=BOOLEAN_ALTERNATIVE_COUNT)
                        errors.add("Boolean alternatives must contain exactly two distinct values: "+name);
                    var values=MinimalCLIContractChecks.values(row,VALUE);
                    if(values.isEmpty() || !choices.containsAll(values)) errors.add("Observed boolean value missing from alternatives: "+name);
                }
                case MODEL_CHOICES -> {
                    for(var required:policy.requiredModels()) if(choices.stream().noneMatch(required::matches))
                        errors.add("Required model alternative missing: "+required.match()+" "+required.name());
                }
            }
        }
        if(observed==0) errors.add(CLIPropertyConsistencyChecks.INSUFFICIENT+"no usable "+check+" results");
        return new TraceCheckResult(List.of(),List.of(),errors);
    }
}
