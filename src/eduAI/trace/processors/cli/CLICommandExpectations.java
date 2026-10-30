package eduAI.trace.processors.cli;
import java.util.*;
import eduAI.trace.processors.*;
/** Required session shape is explicit suite policy, separate from consistency of returned data. */
public record CLICommandExpectations(List<Step> steps) {
    public record Step(String command, Optional<String> argument, List<String> attachments) {
        public Step { Objects.requireNonNull(command); Objects.requireNonNull(argument); attachments=List.copyOf(attachments); }
    }
    public CLICommandExpectations { steps=List.copyOf(steps); if(steps.isEmpty()) throw new IllegalArgumentException("Configure required command inputs"); }
    public TraceCheckResult check(Collection<TraceRecord> records, CLIMixedIO io) {
        var errors=new ArrayList<String>();
        if(io.entries().size()!=steps.size()) errors.add("Expected command input count="+steps.size()+" actual="+io.entries().size());
        for(int i=0;i<Math.min(steps.size(),io.entries().size());i++) {
            var step=steps.get(i); var entry=io.entries().get(i);
            if(!step.command().equals(entry.input().commandName())) errors.add("Unexpected command at input "+entry.number());
            if(step.argument().isPresent()&&!step.argument().get().equals(entry.input().argument().strip()))
                errors.add("Unexpected command options at input "+entry.number());
            if(!step.attachments().equals(entry.attachments().stream().map(CLIAttachment::path).toList()))
                errors.add("Unexpected attachments at input "+entry.number());
        }
        var history=CLICommandTraceHistory.read(records); errors.addAll(history.errors());
        if(history.operations().size()!=io.entries().size()) errors.add("Command return/input count differs");
        for(int i=0;i<Math.min(history.operations().size(),io.entries().size());i++) {
            var operation=history.operations().get(i); var input=io.entries().get(i);
            if(operation.number()!=input.number()||!Objects.equals(operation.command(),input.input().commandName()))
                errors.add("Command return disagrees with actual input at "+input.number());
        }
        return new TraceCheckResult(List.of(),List.of(),errors);
    }
}
