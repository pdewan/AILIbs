package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.*;
import static eduAI.trace.cli.MinimalCLITrace.*;

/** Explicit suite policy; matching does not supply evidence to consistency checks. */
public record CLIPropertyExpectations(Map<String,CLIPropertyDescriptor> descriptors, List<Step> steps,
        List<CLIModelChoiceRequirement> requiredModels) {
    public record Step(String property, CLIInteraction.Kind kind, Optional<String> value, boolean changed) {
        public Step { Objects.requireNonNull(property); Objects.requireNonNull(kind); Objects.requireNonNull(value); }
    }
    public CLIPropertyExpectations {
        descriptors=Map.copyOf(descriptors); steps=List.copyOf(steps); requiredModels=List.copyOf(requiredModels);
        if(requiredModels.isEmpty()) throw new IllegalArgumentException("Configure at least one required model choice");
    }
    public TraceCheckResult check(Collection<TraceRecord> records, CLIMixedIO io) {
        List<String> errors=new ArrayList<>();
        var inputs=io.entries().stream().filter(entry->entry.input().kind()==CLIInteraction.Kind.PROPERTY_GET
            || entry.input().kind()==CLIInteraction.Kind.PROPERTY_SET).toList();
        var history=CLIPropertyTraceHistory.read(records);
        errors.addAll(history.errors());
        if (inputs.size()!=steps.size()) errors.add("Expected property input count="+steps.size()+" actual="+inputs.size());
        if (history.operations().size()!=inputs.size()) errors.add("Property trace/input count differs");
        Map<String,Object> previous=new HashMap<>();
        for(int i=0;i<inputs.size();i++) {
            var entry=inputs.get(i); var input=entry.input();
            Object value=input.properties().get(input.propertyName());
            if(i<steps.size()) {
                var step=steps.get(i);
                if(!step.property().equals(input.propertyName()) || step.kind()!=input.kind())
                    errors.add("Unexpected property operation at input "+entry.number()+": expected "+step.property()+" "+step.kind());
                if(step.value().isPresent() && !step.value().get().equals(String.valueOf(value)))
                    errors.add("Required property value differs at input "+entry.number());
                if(step.changed() && (!previous.containsKey(input.propertyName()) || Objects.equals(previous.get(input.propertyName()),value)))
                    errors.add("Required property change missing at input "+entry.number());
            }
            previous.put(input.propertyName(),value);
            if(i<history.operations().size()) {
                var operation=history.operations().get(i);
                List<String> wanted=value instanceof List<?> list ? list.stream().map(String::valueOf).toList()
                    : value==null ? List.of() : List.of(String.valueOf(value));
                if(operation.command()!=entry.number() || operation.setter()!=(input.kind()==CLIInteraction.Kind.PROPERTY_SET)
                    || !Objects.equals(operation.fields().get(NAME),input.propertyName())
                    || !MinimalCLIContractChecks.values(operation.fields(),VALUE).equals(wanted))
                    errors.add("Property trace disagrees with input "+entry.number());
            }
        }
        return new TraceCheckResult(List.of(),List.of(),errors);
    }
}
