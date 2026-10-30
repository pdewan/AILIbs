package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.properties.AIPropertyNames;
import eduAI.trace.cli.MinimalCLITrace;
import eduAI.trace.processors.StreamingCallbackEvidence;
import eduAI.trace.processors.TraceRecord;

/** Per-command streaming evidence, independent of the prescribed assignment script. */
public final class CLIStreamingSettingChecks {
    private CLIStreamingSettingChecks() { }
    public static List<String> checkOutput(CLIMixedIO io) {
        return check(List.of(),io,false);
    }
    public static List<String> check(Collection<TraceRecord> records, CLIMixedIO io) {
        return check(records,io,true);
    }
    private static List<String> check(Collection<TraceRecord> records, CLIMixedIO io, boolean requireTraces) {
        var errors=new ArrayList<String>();
        Map<Integer,Integer> callbacks=new HashMap<>();
        Map<Integer,Set<String>> notified=new HashMap<>(), displayed=new HashMap<>();
        Set<Integer> handled=new HashSet<>();
        int pending=0;
        for(var record:records.stream().sorted(Comparator.comparingInt(TraceRecord::getTraceFileLineNumber)).toList()) {
            if(StreamingCallbackEvidence.isPartialCallback(record)) pending++;
            var data=record.getTraceLine().getAuxiliaryData();
            if(CLITraceEvidence.is(record,MinimalCLITrace.Event.COMMAND_HANDLED)) {
                int number=CLITraceEvidence.number(data);
                if(!handled.add(number)) errors.add("Duplicate command return: "+number);
                callbacks.merge(number,pending,Integer::sum);
                pending=0;
            }
            for(var event:List.of(MinimalCLITrace.Event.STREAM_OUTPUT_NOTIFIED,MinimalCLITrace.Event.STREAM_DISPLAYED))
                if(CLITraceEvidence.is(record,event)) {
                    var target=event==MinimalCLITrace.Event.STREAM_OUTPUT_NOTIFIED?notified:displayed;
                    target.computeIfAbsent(CLITraceEvidence.number(data),key->new HashSet<>()).add(data.get(MinimalCLITrace.MODEL));
                }
        }
        if(pending>0) errors.add("Streaming callbacks without a completed command");
        Set<Integer> known=new HashSet<>();
        Boolean enabled=null;
        boolean enabledPrompt=false,disabledPrompt=false;
        for(var entry:io.entries()) {
            known.add(entry.number());
            var input=entry.input();
            if(input.kind()==CLIInteraction.Kind.PROPERTY_SET &&
                    Set.of(AIPropertyNames.STREAM,AIPropertyNames.STREAMING_ALIAS).contains(input.propertyName())) {
                Object value=input.properties().get(input.propertyName());
                if(!(value instanceof Boolean)) errors.add("Invalid streaming setter at "+entry.number());
                else enabled=(Boolean)value;
            }
            var notifications=notified.getOrDefault(entry.number(),Set.of());
            var displays=displayed.getOrDefault(entry.number(),Set.of());
            int count=callbacks.getOrDefault(entry.number(),0);
            boolean prompt=input.kind()==CLIInteraction.Kind.PROMPT;
            if(prompt && enabled==null) errors.add("No explicit stream setting before prompt "+entry.number());
            if(prompt && Boolean.TRUE.equals(enabled)) {
                enabledPrompt=true;
                Object selection=input.properties().get(AIPropertyNames.CURRENT_MODELS);
                if(!(selection instanceof List<?> models) || models.isEmpty()) {
                    errors.add("No selected models for streaming prompt "+entry.number());
                    continue;
                }
                var expected=new HashSet<>(models);
                var captured=new HashSet<String>();
                entry.chunks().forEach(chunk->captured.add(chunk.model()));
                if(!captured.equals(expected) || (requireTraces && (!handled.contains(entry.number())
                        || count==0 || !notifications.equals(expected) || !displays.equals(expected))))
                    errors.add("Prompt "+entry.number()+" with stream=true lacks streaming evidence for its selected models");
            } else {
                if(prompt && Boolean.FALSE.equals(enabled)) disabledPrompt=true;
                if(count>0 || !notifications.isEmpty() || !displays.isEmpty() || !entry.chunks().isEmpty())
                    errors.add("Unexpected streaming for command "+entry.number()+" while streaming is disabled or the command is not a prompt");
            }
        }
        for(var numbers:List.of(callbacks.keySet(),notified.keySet(),displayed.keySet()))
            for(int number:numbers) if(!known.contains(number)) errors.add("Streaming evidence for unknown command "+number);
        if(!enabledPrompt || !disabledPrompt) errors.add("Insufficient evidence: execute prompts after both stream=true and stream=false");
        return errors;
    }
}
