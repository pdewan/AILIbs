package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.TraceLine;
import eduAI.trace.cli.MinimalCLITrace.Event;
import static eduAI.trace.cli.MinimalCLITrace.*;
import static eduAI.trace.cli.TraceDataPaths.ID;
import static eduAI.trace.cli.TraceDataPaths.ERROR;

/** Shared structural checks over traces and semantic comparisons against I/O and A1 evidence. */
public final class MinimalCLIContractChecks {
    /** Operation kind and command number come from parsed input, not trace claims. */
    public record Property(int command, CLIInteraction.Kind kind, String name, String value) { }
    /** Original request evidence supplied by the reused A1 request checks. */
    public record Request(int command, String id, Map<String,String> parameters) { }
    public record Stream(int command, String model, String text, String output, String identity) { }
    public record Attachment(String fileName, String content, boolean image) { }
    public record Expectations(Map<Integer,String> commands, List<Property> properties,
            List<Request> requests, Map<Integer,String> commandOutput, List<Stream> streams,
            List<Attachment> attachments) { }
    public record Finding(String check, String detail) { }
    public static final String STREAM_NOTIFICATION = "stream-notification";
    private MinimalCLIContractChecks() { }
    private static boolean changedParameter(Property setter,Request request) {
        String original=request.parameters.get(setter.name);
        if(original==null || !CLISemanticEvidence.NUMERIC_PROPERTIES.contains(setter.name)) return false;
        try { return new java.math.BigDecimal(original).compareTo(new java.math.BigDecimal(setter.value))!=0; }
        catch(NumberFormatException invalid) { return false; }
    }
    public static boolean attachmentMatches(Attachment wanted, Map<String,String> fields) {
        return wanted.fileName.equals(fields.get(INPUT_FILE_NAME))
            && values(fields,EXPANDED_DATA).contains(wanted.content);
    }

    public static List<Finding> check(List<TraceLine> traces, Expectations expected) {
        return check(traces,expected,null);
    }
    /** Select evaluation before running rules, so unrelated malformed evidence
     * cannot throw and veto an independent check. Null means the full contract. */
    public static List<Finding> check(List<TraceLine> traces, Expectations expected,Set<String> selected) {
        boolean commands=includes(selected,"command-count","command-order","notification-count","notification-order","notification-result","result-delivery","command-io","display-count");
        boolean properties=includes(selected,"property-count","property-order","property-value","store-values");
        boolean history=includes(selected,"history-count","history-order","snapshot-values");
        boolean streams=includes(selected,STREAM_NOTIFICATION,"stream-sequence","stream-io");
        boolean attachments=includes(selected,"attachment-count","attachment-content");
        boolean descriptors=includes(selected,"type-field","choices-field");
        List<Finding> errors=new ArrayList<>();
        Map<Integer,Map<String,String>> returned=new LinkedHashMap<>();
        Map<Integer,Map<String,String>> notified=new LinkedHashMap<>();
        Map<String,Deque<Map<String,String>>> streamNotifications=new LinkedHashMap<>();
        Set<String> receipts=new HashSet<>();
        Set<Integer> displayed=new HashSet<>(), histories=new HashSet<>();
        Set<Integer> setters=new HashSet<>();
        Set<Integer> completedSetters=new HashSet<>();
        boolean snapshotPreservationExercised=false;
        expected.properties.stream().filter(p->p.kind==CLIInteraction.Kind.PROPERTY_SET)
            .forEach(p->setters.add(p.command));
        List<Map<String,String>> metadata=new ArrayList<>();
        Map<String,String> accumulated=new LinkedHashMap<>();
        int propertyIndex=0, streamIndex=0, fileIndex=0;
        String pendingHistoryProperty=null;
        for (TraceLine trace:traces) {
            Map<String,String> d=trace.getAuxiliaryData();
            Event event;
            try { event=Event.valueOf(trace.getEventName().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { continue; }
            boolean relevant=switch(event) {
                case COMMAND_HANDLED -> commands || history;
                case COMMAND_OUTPUT_NOTIFIED,COMMAND_DISPLAYED -> commands;
                case PROPERTY_HANDLED -> properties || history || descriptors;
                case HISTORY_WITH_PARAMETERS_UPDATED -> history;
                case STREAM_OUTPUT_NOTIFIED,STREAM_DISPLAYED -> streams;
                case FILE_EXPANDED -> attachments;
            };
            if(!relevant) continue;
            for (String key:d.keySet()) if (key.endsWith(ERROR))
                fail(errors,"readable-data",key+": "+d.get(key));
            int command=number(d.get(COMMAND_NUMBER));
            switch(event) {
            case COMMAND_HANDLED, PROPERTY_HANDLED -> {
                boolean property=event==Event.PROPERTY_HANDLED;
                String name=d.get(NAME);
                if (property) {
                    if(properties) {
                    if (propertyIndex>=expected.properties.size()) fail(errors,"property-count","Extra property return");
                    else {
                        Property operation=expected.properties.get(propertyIndex);
                        if(operation.kind==CLIInteraction.Kind.PROPERTY_SET) completedSetters.add(operation.command);
                        if (!operation.name.equals(name)) fail(errors,"property-order",name);
                        if (!values(d,VALUE).equals(List.of(operation.value))) fail(errors,"property-value",name);
                        accumulated.put(operation.name,operation.value);
                        for(var setting:accumulated.entrySet())
                            if (!mapsValue(prefix(d,STORE),setting.getKey(),setting.getValue()))
                                fail(errors,"store-values",setting.getKey());
                    }
                    propertyIndex++;
                    }
                    // Property returns have no command number. The following checkpoint
                    // identifies its setter; a command return ends this pending interval.
                    if(history && !properties) pendingHistoryProperty=name;
                    if(descriptors) metadata.add(d);
                } else {
                    pendingHistoryProperty=null;
                    if(!commands) continue;
                    if (name==null || !Objects.equals(expected.commands.get(command),CLICommandNames.role(name)) || returned.putIfAbsent(command,d)!=null)
                        fail(errors,"command-order","Unexpected or duplicate command "+command);
                }
            }
            case COMMAND_OUTPUT_NOTIFIED -> {
                if (!expected.commands.containsKey(command) || notified.putIfAbsent(command,d)!=null)
                    fail(errors,"notification-count","Unexpected or duplicate notification for command "+command);
                var before=returned.get(command);
                if (before==null)
                    fail(errors,"notification-order","Notification precedes handler return for command "+command);
                if (!id(d.get(RESULT+ID)))
                    fail(errors,"notification-result","Missing outgoing result identity for command "+command);
            }
            case COMMAND_DISPLAYED -> {
                var sent=notified.get(command);
                if (sent==null)
                    fail(errors,"notification-order","Display has no preceding notification for command "+command);
                if (sent==null || !id(d.get(RESULT+ID))
                        || !Objects.equals(sent.get(RESULT+ID),d.get(RESULT+ID)))
                    fail(errors,"result-delivery","Received object identity differs from notification for command "+command);
                if (includes(selected,"command-io") && !BasicCLIMixedIOProcessor.matchesDisplayedOutput(command,expected.commandOutput.get(command),d.get(OUTPUT)))
                    fail(errors,"command-io","Output differs for "+command);
                displayed.add(command);
                if (!receipts.add(command+":"+d.get(OBSERVER))) fail(errors,"display-count","Duplicate receipt by the same observer");
            }
            case HISTORY_WITH_PARAMETERS_UPDATED -> {
                if(history && !properties) {
                    for(Property setter:expected.properties) if(setter.kind==CLIInteraction.Kind.PROPERTY_SET
                            && setter.command==command && Objects.equals(setter.name,pendingHistoryProperty))
                        completedSetters.add(command);
                    pendingHistoryProperty=null;
                }
                if (!setters.contains(command) || !histories.add(command))
                    fail(errors,"history-count","Checkpoint must follow a property setter, once per setter");
                if(!completedSetters.contains(command))
                    fail(errors,"history-order","History checkpoint precedes its property setter");
                for (Request request:expected.requests) if (request.command<command) {
                    if(!containsRequest(d,request))
                        fail(errors,"snapshot-values","Missing or changed parameters for "+request.id+" at "+command);
                }
                for(Property setter:expected.properties) if(setter.command==command && setter.kind==CLIInteraction.Kind.PROPERTY_SET) {
                    long affected=expected.requests.stream().filter(request->request.command<command)
                        .filter(request->changedParameter(setter,request)).map(Request::command).distinct().count();
                    if(affected>=CLISemanticEvidence.MINIMUM_HISTORY_EXCHANGES) snapshotPreservationExercised=true;
                }
            }
            case STREAM_OUTPUT_NOTIFIED -> {
                if (!id(d.get(OBSERVABLE)) || !id(d.get(CHUNK+ID)) || d.get(MODEL)==null)
                    fail(errors,STREAM_NOTIFICATION,"Missing streaming observable, model or chunk identity");
                String key=command+":"+d.get(MODEL);
                streamNotifications.computeIfAbsent(key,k->new ArrayDeque<>()).add(d);
            }
            case STREAM_DISPLAYED -> {
                var pending=streamNotifications.get(command+":"+d.get(MODEL));
                var sent=pending==null ? null : pending.poll();
                if(sent==null || !Objects.equals(sent.get(CHUNK+ID),d.get(CHUNK+ID)))
                    fail(errors,STREAM_NOTIFICATION,"Display lacks a preceding notification of the same chunk for command "+command);
                if (streamIndex>=expected.streams.size()) fail(errors,"stream-sequence","Unexpected streaming display");
                else {
                    Stream wanted=expected.streams.get(streamIndex);
                    if (wanted.command!=command || !wanted.model.equals(d.get(MODEL))
                            || !id(wanted.identity) || !Objects.equals(wanted.identity,d.get(CHUNK+ID)))
                        fail(errors,"stream-sequence","Chunk differs at "+streamIndex);
                    if (wanted.output!=null && !Objects.equals(CLIOutputSyntax.streamRecord(wanted.output),
                            d.get(OUTPUT)==null ? null : CLIOutputSyntax.streamRecord(d.get(OUTPUT))))
                        fail(errors,"stream-io","Printed output differs at "+streamIndex);
                }
                streamIndex++;
            }
            case FILE_EXPANDED -> {
                if (fileIndex>=expected.attachments.size()) fail(errors,"attachment-count","Unexpected expansion");
                else {
                    Attachment wanted=expected.attachments.get(fileIndex);
                    if (!attachmentMatches(wanted,d))
                        fail(errors,"attachment-content",wanted.fileName);
                }
                fileIndex++;
            }
            }
        }
        if (streams && streamNotifications.values().stream().anyMatch(queue->!queue.isEmpty()))
            fail(errors,STREAM_NOTIFICATION,"Streaming notifications have no matching display");
        if (commands && !returned.keySet().equals(expected.commands.keySet())) fail(errors,"command-count","Missing handler returns");
        if (commands && !notified.keySet().equals(expected.commands.keySet())) fail(errors,"notification-count","Missing command notifications");
        if (commands && !displayed.equals(expected.commands.keySet())) fail(errors,"display-count","Missing command displays");
        if (history && !histories.equals(setters)) fail(errors,"history-count","Missing property-setter history checkpoints");
        if(properties && propertyIndex!=expected.properties.size()) fail(errors,"property-count","Missing property returns");
        if(streams && streamIndex!=expected.streams.size()) fail(errors,"stream-sequence","Missing streaming displays");
        if(attachments && fileIndex!=expected.attachments.size()) fail(errors,"attachment-count","Missing expansions");
        if(history && !snapshotPreservationExercised) fail(errors,"snapshot-values","Insufficient evidence: need "
            +CLISemanticEvidence.MINIMUM_HISTORY_EXCHANGES+" earlier requests followed by a changed-parameter setter and history checkpoint");
        if(streams && expected.streams.isEmpty()) fail(errors,"stream-sequence","Insufficient evidence: no streaming callbacks to compare");
        if(commands && expected.commands.isEmpty()) fail(errors,"display-count","Insufficient evidence: no command outputs to compare");
        if(selected==null || selected.stream().anyMatch(rule->CLIContractDomains.domain(rule)==CLIContractDomains.Domain.STRUCTURE
                && !OBSERVER_RULES.contains(rule))) errors.addAll(checkStructure(traces));
        else if(selected.stream().anyMatch(OBSERVER_RULES::contains)) errors.addAll(CLIObserverSeparationChecks.check(traces));
        if(descriptors) {
        Set<String> integers=Set.of(eduAI.properties.AIPropertyNames.TIMEOUT,
            eduAI.properties.AIPropertyNames.NUM_CTX,eduAI.properties.AIPropertyNames.TOP_K);
        if(typeCandidates(metadata,integers).size()!=1)
            fail(errors,"type-field","Missing or ambiguous consistent integer-type field");
        String modelProperty=eduAI.properties.AIPropertyNames.CURRENT_MODELS;
        Set<String> models=new HashSet<>();
        expected.properties.stream().filter(p->p.name.equals(modelProperty)).forEach(p->models.add(p.value));
        if(models.size()<2 || choicesCandidates(metadata,modelProperty,models).size()!=1)
            fail(errors,"choices-field","Need a stable choices field containing both selected models");
        }
        return selected==null ? List.copyOf(errors) : errors.stream().filter(f->selected.contains(f.check())).toList();
    }
    private static final Set<String> OBSERVER_RULES=Set.of(CLIObserverSeparationChecks.COMMAND_SEPARATION,
        CLIObserverSeparationChecks.STREAM_SEPARATION,CLIObserverSeparationChecks.OBSERVER_INSTANCES,CLIObserverSeparationChecks.SEPARATION_EVIDENCE);
    private static boolean includes(Set<String> selected,String... rules) {
        return selected==null || Arrays.stream(rules).anyMatch(selected::contains);
    }

    /** Structural identities and associations need only traces, never I/O,
     * attachment bytes, request parameters or callback contents. Missing role
     * evidence fails; unrelated semantic errors do not veto structural credit. */
    public static List<Finding> checkStructure(List<TraceLine> traces) {
        List<Finding> errors=new ArrayList<>();
        Map<String,String> usedRegistries=new HashMap<>(), handlers=new HashMap<>(), propertyOwners=new HashMap<>();
        String store=null, dispatcher=null;
        // Read processing identities from their own events, independent of event order.
        // Display events supply only the observer, never a claimed notifier.
        Set<String> processors=new HashSet<>();
        for(var trace:traces) {
            if(trace.getEventName().equals(Event.COMMAND_DISPLAYED.eventName())
                    || trace.getEventName().equals(Event.STREAM_DISPLAYED.eventName())) continue;
            var fields=trace.getAuxiliaryData();
            for(String role:List.of(CALLER,HANDLER,EXPANDER,OBSERVABLE)) {
                String participant=fields.get(role);
                if(id(participant)) processors.add(participant);
            }
        }
        Set<Event> seen=EnumSet.noneOf(Event.class);
        for(var trace:traces) {
            Event event;
            try { event=Event.valueOf(trace.getEventName().toUpperCase(Locale.ROOT)); }
            catch(IllegalArgumentException ignored) { continue; }
            seen.add(event);
            var d=trace.getAuxiliaryData();
            int command=number(d.get(COMMAND_NUMBER));
            switch(event) {
            case COMMAND_HANDLED, PROPERTY_HANDLED -> {
                boolean property=event==Event.PROPERTY_HANDLED;
                String name=d.get(NAME), registry=d.get(REGISTRY), handler=d.get(HANDLER);
                if (!id(registry) || !id(handler) || !id(d.get(CALLER)))
                    fail(errors,"participant-identity","Missing caller, registry or handler");
                var contents=prefix(d,REGISTRY);
                if(!Objects.equals(registry,d.get(REGISTRY+ID)))
                    fail(errors,"registry-current","Missing or mismatched current registry contents");
                if (!(property ? mapsIdentity(contents,name,handler) : commandRegistered(contents,name,handler)))
                    fail(errors,"registry-association","No entry pairing "+name+" with "+handler);
                stable(usedRegistries,property ? "property:"+name : "command",registry,errors,"registry-reuse");
                stable(handlers,(property?"property:":"command:")+(property?name:CLICommandNames.role(name)),handler,errors,"handler-reuse");
                if(property) {
                    if (!id(d.get(RESULT+ID)))
                        fail(errors,"property-result","Missing result object");
                    if (Objects.equals(handler,d.get(RESULT+ID)))
                        fail(errors,"property-result-handler","Handler used as result");
                    String owner=propertyOwners.putIfAbsent(handler,name);
                    if (owner!=null && !owner.equals(name)) fail(errors,"property-instance","Shared handler for "+owner+" and "+name);
                    if (!id(d.get(STORE+ID)) || store!=null && !store.equals(d.get(STORE+ID)))
                        fail(errors,"store-identity",String.valueOf(name));
                    store=d.get(STORE+ID);
                } else {
                    if (dispatcher!=null && !dispatcher.equals(d.get(CALLER)))
                        fail(errors,"dispatcher-identity","Different dispatcher");
                    dispatcher=d.get(CALLER);
                }
            }
            case COMMAND_OUTPUT_NOTIFIED -> {
                if (!id(d.get(OBSERVABLE)))
                    fail(errors,"notification-source","Missing observable identity for command "+command);
            }
            case HISTORY_WITH_PARAMETERS_UPDATED -> {
                if (!id(d.get(CALLER)) || !processors.contains(d.get(CALLER)))
                    fail(errors,"history-source","History checkpoint has no identified processing caller");
            }
            default -> { }
            }
        }
        for(var required:List.of(Event.COMMAND_HANDLED,Event.PROPERTY_HANDLED,
                Event.COMMAND_OUTPUT_NOTIFIED,Event.HISTORY_WITH_PARAMETERS_UPDATED))
            if(!seen.contains(required)) fail(errors,STRUCTURE_EVIDENCE,"Missing "+required.eventName());
        errors.addAll(CLIObserverSeparationChecks.check(traces));
        errors.addAll(CLIExpanderSeparationChecks.findings(traces));
        return List.copyOf(errors);
    }
    public static final String STRUCTURE_EVIDENCE = "structure-evidence";

    /** Candidates are paths, not names chosen by the checker. No enum spelling is assumed. */
    public static Set<String> typeCandidates(List<Map<String,String>> returns, Set<String> integerNames) {
        Map<String,Set<String>> values=new HashMap<>();
        Map<String,Set<String>> covered=new HashMap<>();
        for(var row:returns) if(row.get(NAME)!=null && integerNames.contains(row.get(NAME)))
            for(var entry:prefix(row,RESULT).entrySet()) if(entry.getKey().endsWith(VALUE_SUFFIX)) {
                values.computeIfAbsent(entry.getKey(),k->new HashSet<>()).add(entry.getValue());
                covered.computeIfAbsent(entry.getKey(),k->new HashSet<>()).add(row.get(NAME));
            }
        Set<String> candidates=new HashSet<>();
        for(var entry:values.entrySet()) if(entry.getValue().size()==1 && covered.get(entry.getKey()).equals(integerNames)) {
            String candidate=entry.getKey(), value=entry.getValue().iterator().next();
            // Reject generic fields shared by all types (success flags, empty strings, etc.).
            boolean distinguished=returns.stream().filter(r->r.get(NAME)==null || !integerNames.contains(r.get(NAME)))
                .anyMatch(r->r.containsKey(candidate) && !Objects.equals(r.get(candidate),value));
            if(distinguished) candidates.add(candidate);
        }
        return Set.copyOf(candidates);
    }
    private static final String VALUE_SUFFIX=eduAI.trace.cli.TraceDataPaths.VALUE;
    public static Set<String> choicesCandidates(List<Map<String,String>> returns, String property, Set<String> models) {
        Set<String> common=new HashSet<>(choicesPathCandidates(returns,property,models));
        common.removeIf(path->returns.stream().filter(r->property.equals(r.get(NAME)))
            .map(r->values(r,path)).distinct().count()!=1);
        return Set.copyOf(common);
    }
    /** Identifies the field path without imposing the separately graded rule
     * that the alternatives stored at that path remain unchanged. */
    public static Set<String> choicesPathCandidates(List<Map<String,String>> returns, String property, Set<String> models) {
        Set<String> common=null;
        for(var row:returns) if(property.equals(row.get(NAME))) {
            Set<String> candidates=new HashSet<>();
            for(String path:prefix(row,RESULT).keySet()) {
                int bracket=path.lastIndexOf(".items.");
                if(bracket<0) continue;
                String container=path.substring(0,bracket);
                if(new HashSet<>(values(row,container)).containsAll(models)) candidates.add(container);
            }
            if(common==null) common=candidates; else common.retainAll(candidates);
        }
        if(common==null) return Set.of();
        return Set.copyOf(common);
    }
    private static void stable(Map<String,String> values,String key,String value,List<Finding> errors,String check) {
        String old=values.putIfAbsent(key,value);
        if(old!=null && !old.equals(value)) fail(errors,check,key);
    }
    private static void fail(List<Finding> errors,String check,String detail) { errors.add(new Finding(check,detail)); }
    private static int number(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return -1; }
    }
    private static boolean id(String value) { return value!=null && value.matches(".+@[0-9a-f]+"); }
    public static Map<String,String> prefix(Map<String,String> data,String root) {
        Map<String,String> result=new LinkedHashMap<>();
        data.forEach((k,v)->{if(k.startsWith(root+".") || k.startsWith(root+"[")) result.put(k,v);});
        return result;
    }
    public static List<String> values(Map<String,String> data,String root) {
        return prefix(data,root).entrySet().stream().filter(e->e.getKey().endsWith(VALUE_SUFFIX))
            .map(Map.Entry::getValue).toList();
    }
    public static boolean mapsIdentity(Map<String,String> data,String key,String identity) {
        return mapped(data,key,ID,identity);
    }
    /** For unnamed input require an actual registration value, not an incidental reference. */
    public static boolean commandRegistered(Map<String,String> data,String name,String identity) {
        if(name==null || !id(identity)) return false;
        if(!name.isBlank()) return mapsIdentity(data,name,identity);
        String ending=".value"+ID;
        return data.entrySet().stream().filter(e->e.getKey().endsWith(ending) && identity.equals(e.getValue()))
            .anyMatch(e->!prefix(data,e.getKey().substring(0,e.getKey().length()-ending.length())+".key").isEmpty());
    }
    private static boolean mapsValue(Map<String,String> data,String key,String value) {
        String ending=".key"+VALUE_SUFFIX;
        return data.entrySet().stream().anyMatch(e->e.getKey().endsWith(ending) && Objects.equals(key,e.getValue())
            && values(data,e.getKey().substring(0,e.getKey().length()-ending.length())+".value").contains(value));
    }
    private static boolean mapped(Map<String,String> data,String key,String suffix,String expected) {
        String ending=".key"+VALUE_SUFFIX;
        return data.entrySet().stream().anyMatch(e->e.getKey().endsWith(ending) && Objects.equals(key,e.getValue())
            && Objects.equals(expected,data.get(e.getKey().substring(0,e.getKey().length()-ending.length())+".value"+suffix)));
    }
    private static boolean containsRequest(Map<String,String> data,Request request) {
        // Find the smallest enclosing record holding this ID and its parameters.
        // Never use unrelated properties elsewhere in the history as evidence.
        for(var leaf:prefix(data,HISTORY).entrySet()) if(leaf.getKey().endsWith(VALUE_SUFFIX) && request.id.equals(leaf.getValue())) {
            String path=leaf.getKey().substring(0,leaf.getKey().length()-VALUE_SUFFIX.length());
            // A collection of records is not itself a record. Never cross its item
            // boundary and borrow another request's parameter map.
            java.util.regex.Matcher boundary=java.util.regex.Pattern.compile("\\.items\\.\\d+").matcher(path);
            int recordEnd=boundary.find() ? boundary.end() : -1;
            while(path.contains(".")) {
                path=path.substring(0,path.lastIndexOf('.'));
                if(path.equals(HISTORY) || recordEnd>=0 && path.length()<recordEnd) break;
                var record=prefix(data,path);
                if(request.parameters.entrySet().stream().allMatch(e->mapsValue(record,e.getKey(),e.getValue()))) return true;
            }
        }
        return false;
    }
}
