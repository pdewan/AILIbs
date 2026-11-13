package eduAI.trace.processors.cli;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.cli.MinimalCLITrace;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.MinimalCLIContractChecks.*;

/** Builds the six-call contract from captured input, attachment bytes and A1 request/callback evidence. */
public final class CLISixCallChecks {
    /** Only prepare evidence consumed by the selected rule. SETTERS permits
     * unknown default getters in snapshot checks; STREAMS leaves nonstreaming
     * request contents undecoded. Missing consumed evidence still fails. */
    public enum Evidence { PROPERTIES, SETTERS, REQUESTS, STREAMS, ATTACHMENTS }
    private static final Set<String> COMMAND_DELIVERY_RULES=Set.of("result-delivery","display-count",
        "notification-count","notification-order","notification-result",CLIObserverSeparationChecks.COMMAND_SEPARATION);
    private static final Set<String> STREAM_DELIVERY_RULES=Set.of(MinimalCLIContractChecks.STREAM_NOTIFICATION,
        CLIObserverSeparationChecks.STREAM_SEPARATION);
    private static final Set<String> COMMAND_AGREEMENT_RULES=Set.of("command-io","display-count");
    private static final Set<String> STREAM_AGREEMENT_RULES=Set.of("stream-sequence","stream-io");
    private CLISixCallChecks() { }
    /** The full contract retains both independently graded dimensions. */
    public static Set<String> observerRules(boolean streaming) {
        var rules=new HashSet<>(streaming ? STREAM_DELIVERY_RULES : COMMAND_DELIVERY_RULES);
        rules.addAll(streaming ? STREAM_AGREEMENT_RULES : COMMAND_AGREEMENT_RULES);
        return Set.copyOf(rules);
    }
    /** Delivery uses only traced returns, notifications and receipts. It does not
     * need a console capture to establish that the notified object was received. */
    public static TraceCheckResult notificationDelivery(Collection<TraceRecord> records, boolean streaming) {
        var ordered=records.stream().sorted(Comparator.comparingInt(TraceRecord::getTraceFileLineNumber)).toList();
        Map<Integer,String> commands=new LinkedHashMap<>();
        for(var record:ordered) if(CLITraceEvidence.is(record,MinimalCLITrace.Event.COMMAND_HANDLED))
            commands.put(CLITraceEvidence.number(record.getTraceLine().getAuxiliaryData()),CLITraceEvidence.commandName(record));
        Set<String> selected=streaming ? STREAM_DELIVERY_RULES : COMMAND_DELIVERY_RULES;
        var expected=new Expectations(commands,List.of(),List.of(),Map.of(),List.of(),List.of());
        var errors=new ArrayList<>(MinimalCLIContractChecks.check(ordered.stream().map(TraceRecord::getTraceLine).toList(),expected,selected)
            .stream().filter(f->selected.contains(f.check())).map(f->f.check()+": "+f.detail()).toList());
        if(streaming && ordered.stream().noneMatch(r->CLITraceEvidence.is(r,MinimalCLITrace.Event.STREAM_OUTPUT_NOTIFIED)))
            errors.add("Insufficient evidence: no streaming notifications to compare");
        return new TraceCheckResult(List.of(),List.of(),errors);
    }

    /** File/display agreement is scored independently of notification delivery.
     * Streaming also retains the original callback identity, ordering and text
     * comparisons; regular command result semantics are checked elsewhere. */
    public static TraceCheckResult outputAgreement(Collection<TraceRecord> records, CLIMixedIO io, Path directory, boolean streaming) {
        return check(records,io,directory,streaming ? STREAM_AGREEMENT_RULES : COMMAND_AGREEMENT_RULES,
            streaming ? Set.of(Evidence.STREAMS) : Set.of());
    }
    public static List<Finding> findings(Collection<TraceRecord> records, CLIMixedIO io, Path directory) {
        return findings(records, io, directory, EnumSet.allOf(Evidence.class),null);
    }
    private static List<Finding> findings(Collection<TraceRecord> records, CLIMixedIO io, Path directory,
            Set<Evidence> needed,Set<String> selected) {
        try {
            var ordered=records.stream().sorted(Comparator.comparingInt(TraceRecord::getTraceFileLineNumber)).toList();
            Map<Integer,String> commands=new LinkedHashMap<>(), output=new LinkedHashMap<>();
            List<Property> properties=new ArrayList<>();
            List<Attachment> attachments=new ArrayList<>();
            List<CLIMixedIO.Entry> prompts=new ArrayList<>();
            for(var entry:io.entries()) {
                var input=entry.input();
                if (hasOutputPipe(input.rawInput()))
                    throw new IllegalArgumentException("Filter-specific grading is not implemented for command "+entry.number());
                if(commands.put(entry.number(),input.commandName())!=null)
                    throw new IllegalArgumentException("Duplicate command number in the assignment session");
                output.put(entry.number(),entry.output().map(CLIMixedIO.Output::body).orElse(""));
                // Snapshot preservation consumes setter values, not unknown default getters.
                if((needed.contains(Evidence.PROPERTIES) && (input.kind()==CLIInteraction.Kind.PROPERTY_SET || input.kind()==CLIInteraction.Kind.PROPERTY_GET))
                        || needed.contains(Evidence.SETTERS) && input.kind()==CLIInteraction.Kind.PROPERTY_SET) {
                    Object value=input.properties().get(input.propertyName());
                    if(value==null) throw new IllegalArgumentException("No input-derived value for "+input.propertyName());
                    properties.add(new Property(entry.number(),input.kind(),input.propertyName(),scalar(value)));
                }
                if(input.kind()==CLIInteraction.Kind.PROMPT) prompts.add(entry);
                if(needed.contains(Evidence.ATTACHMENTS)) for(var attachment:entry.attachments()) {
                    attachments.add(CLIFileExpansionContentsChecks.readAttachment(directory,attachment.path()));
                }
            }
            List<Request> requests=new ArrayList<>();
            List<Stream> streams=new ArrayList<>();
            List<Integer> callbackLines=new ArrayList<>();
            int promptIndex=0;
            CLIMixedIO.Entry active=null;
            String model=null;
            boolean activeStreaming=false;
            for(var record:ordered) {
                String event=record.getTraceLine().getEventName();
                if((needed.contains(Evidence.REQUESTS) || needed.contains(Evidence.STREAMS)) && event.equals("request_sent")) {
                    if(promptIndex>=prompts.size()) throw new IllegalArgumentException("More A1 requests than input prompts");
                    active=prompts.get(promptIndex++);
                    activeStreaming=record.getId().mode()==eduAI.trace.lib.StreamingMode.STREAMING;
                    model=null;
                    // Preserve positional command pairing, but do not decode nonstreaming
                    // contents for observer delivery. Snapshot checks still decode all requests.
                    if(!needed.contains(Evidence.REQUESTS) && !activeStreaming) continue;
                    if(!(record.getExtractedData().get("providerIndependentCanonicalRequest") instanceof CanonicalTraceRequest request))
                        throw new IllegalArgumentException("Missing A1 request parameters");
                    model=request.model();
                    if(needed.contains(Evidence.REQUESTS)) {
                        Map<String,String> parameters=new LinkedHashMap<>();
                        for(var setting:active.input().properties().entrySet())
                            if(request.parameters().containsKey(setting.getKey()))
                                parameters.put(setting.getKey(),request.parameters().get(setting.getKey()));
                        if(parameters.isEmpty()) throw new IllegalArgumentException("No parameter snapshot evidence from A1");
                        // History entries carry the originating input number; no application field name is assumed.
                        requests.add(new Request(active.number(),Integer.toString(active.number()),parameters));
                    }
                // Partial callbacks supply required chunk evidence. A completion
                // callback participates only if the observable announces that it
                // sends that object; an unused optional completion is independent.
                } else if(needed.contains(Evidence.STREAMS) && (event.equals(
                        eduAI.trace.lib.LibTraceEvent.PARTIAL_RESPONSE_CALLBACK_INVOKED.name().toLowerCase(Locale.ROOT))
                        || event.equals(eduAI.trace.lib.LibTraceEvent.COMPLETE_RESPONSE_CALLBACK_INVOKED.name().toLowerCase(Locale.ROOT))
                            && completionObserved(record,active,model,ordered,selected))) {
                    if(active==null) throw new IllegalArgumentException("A1 callback has no request");
                    if(!activeStreaming) throw new IllegalArgumentException("A1 streaming callback belongs to a nonstreaming request");
                    if(!(record.getExtractedData().get("callbackArgumentStructure") instanceof GenericTracesFileProcessor.TraceMessage message))
                        throw new IllegalArgumentException("Missing A1 callback data");
                    // A1's aggregate preserves exact whitespace through its content hash.
                    // Individual reflected parts can still contain escaped newline spellings.
                    String text=message.textAggregate()!=null ? message.textAggregate()
                        : message.parts().stream().filter(p->"text".equals(p.kind()))
                            .map(GenericTracesFileProcessor.TracePart::value).reduce("",String::concat);
                    streams.add(new Stream(active.number(),model,text,
                        eduAI.trace.TraceTextSummary.fromToken(text)==null ? CLIOutputSyntax.streamOutput(model,text) : null,
                        record.getTraceLine().getAuxiliaryData().get(eduAI.trace.lib.LibTraceKeys.CALLBACK_ARGUMENT_IDENTITY)));
                    callbackLines.add(record.getTraceFileLineNumber());
                }
            }
            if((needed.contains(Evidence.REQUESTS) || needed.contains(Evidence.STREAMS)) && promptIndex!=prompts.size()) throw new IllegalArgumentException("Missing A1 requests");
            var expected=new Expectations(commands,properties,requests,output,streams,attachments);
            var errors=new ArrayList<>(MinimalCLIContractChecks.check(ordered.stream().map(TraceRecord::getTraceLine).toList(),expected,selected));
            if(needed.contains(Evidence.STREAMS)) {
            var displayed=ordered.stream().filter(r->CLITraceEvidence.is(r,MinimalCLITrace.Event.STREAM_DISPLAYED)).toList();
            for(int i=0;i<Math.min(displayed.size(),streams.size());i++) {
                var data=displayed.get(i).getTraceLine().getAuxiliaryData();
                var wanted=streams.get(i);
                if (displayed.get(i).getTraceFileLineNumber()<=callbackLines.get(i))
                    errors.add(new Finding("stream-sequence","Display precedes its callback at "+i));
                // Content is recovered from printed I/O, never decoded from a student result object.
                String printed=data.getOrDefault(MinimalCLITrace.OUTPUT,"");
                try {
                    String text = CLIOutputSyntax.streamText(wanted.model(), printed);
                    if (!eduAI.trace.TraceTextSummary.matches(wanted.text(), text))
                        errors.add(new Finding("stream-io", "Printed text differs from A1 callback at " + i));
                } catch (IllegalArgumentException error) {
                    errors.add(new Finding("stream-io", "Malformed streaming output at " + i + ": " + error.getMessage()));
                }
            }
            // Compare captured streaming lines too; chunks may contain more than one printed line.
            for(var entry:io.entries()) {
                var actual=ordered.stream().filter(r->CLITraceEvidence.is(r,MinimalCLITrace.Event.STREAM_DISPLAYED))
                    .map(r->r.getTraceLine().getAuxiliaryData()).filter(d->CLITraceEvidence.number(d)==entry.number())
                    .flatMap(d->CLIOutputSyntax.streamRecord(d.getOrDefault(MinimalCLITrace.OUTPUT,"")).lines()).toList();
                var captured=entry.chunks().stream().map(c->CLIOutputSyntax.streamOutput(c.model(),c.text())).toList();
                if(!actual.equals(captured)) errors.add(new Finding("stream-io","Streaming I/O differs for "+entry.number()));
            }
            }
            return List.copyOf(errors);
        } catch(Exception e) {
            return List.of(new Finding("expectations","Cannot establish six-call expectations: "+e.getMessage()));
        }
    }
    /** The combined contract consumes announced completions. Independently
     * scored output agreement consumes displayed completions, so missing
     * notification evidence cannot change its callback expectations. */
    private static boolean completionObserved(TraceRecord callback,CLIMixedIO.Entry active,String model,List<TraceRecord> records,Set<String> selected) {
        if(active==null) return false;
        String identity=callback.getTraceLine().getAuxiliaryData().get(eduAI.trace.lib.LibTraceKeys.CALLBACK_ARGUMENT_IDENTITY);
        if(identity==null) return false;
        var event=selected==null || selected.contains(MinimalCLIContractChecks.STREAM_NOTIFICATION)
            ? MinimalCLITrace.Event.STREAM_OUTPUT_NOTIFIED : MinimalCLITrace.Event.STREAM_DISPLAYED;
        return records.stream().filter(r->CLITraceEvidence.is(r,event))
            .map(r->r.getTraceLine().getAuxiliaryData()).anyMatch(data->CLITraceEvidence.number(data)==active.number()
                && Objects.equals(model,data.get(MinimalCLITrace.MODEL))
                && identity.equals(data.get(MinimalCLITrace.CHUNK+eduAI.trace.cli.TraceDataPaths.ID)));
    }

    /** Detect a pipe outside quoted or escaped input; filter semantics are graded separately. */
    public static boolean hasOutputPipe(String input) {
        char quote=0;
        boolean escaped=false;
        for (char c:input.toCharArray()) {
            if (escaped) { escaped=false; continue; }
            if (c=='\\') { escaped=true; continue; }
            if (quote!=0) { if(c==quote) quote=0; continue; }
            if (c=='\'' || c=='"') quote=c;
            else if (c=='|') return true;
        }
        return false;
    }
    private static String scalar(Object value) {
        if(value instanceof List<?> list) {
            if(list.size()!=1) throw new IllegalArgumentException("Multiple selected values need separate request expectations");
            return String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }
    public static TraceCheckResult check(Collection<TraceRecord> records, CLIMixedIO io, Path directory, Set<String> selected) {
        return check(records,io,directory,selected,EnumSet.allOf(Evidence.class));
    }
    public static TraceCheckResult check(Collection<TraceRecord> records, CLIMixedIO io, Path directory,
            Set<String> selected, Set<Evidence> needed) {
        var errors=findings(records,io,directory,needed,selected).stream().filter(f->selected==null || selected.contains(f.check())
            || f.check().equals("expectations")).map(f->f.check()+": "+f.detail()).toList();
        return new TraceCheckResult(List.of(),errors.isEmpty()?List.of("Six-call evidence matches input and A1 evidence."):List.of(),errors);
    }
}
