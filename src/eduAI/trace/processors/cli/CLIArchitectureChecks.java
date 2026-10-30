package eduAI.trace.processors.cli;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.cli.CLIArchitectureTrace;
import static eduAI.trace.cli.CLIArchitectureTrace.*;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.*;

/** Independent architectural checks sharing the A1 leaf comparator and parsed CLI input. */
public final class CLIArchitectureChecks {
    public static final String EXIT_HANDLER_TAG="ExitCommandHandler", COMMAND_OBSERVER_TAG="CLICommandOutputObserver",
        STREAM_OBSERVABLE_TAG="CLIStreamObservable", STREAM_OBSERVER_TAG="CLIStreamOutputObserver", TEXT_EXPANDER_TAG="CLITextFileExpander",
        IMAGE_EXPANDER_TAG="CLIImageFileExpander", PROPERTY_REGISTRY_TAG="CLIPropertyHandlerRegistry";
    public enum Check { HANDLERS, MODELS, STORE, COMMAND_OUTPUT, STREAM_OUTPUT, FILE_CONTENTS, TEXT_FILE_CONTENTS, IMAGE_FILE_CONTENTS, FILE_SEPARATION, HISTORY }
    private CLIArchitectureChecks() { }
    public static TraceCheckResult check(Check check, Collection<TraceRecord> records, CLIMixedIO io, Path directory) {
        if(check==Check.TEXT_FILE_CONTENTS || check==Check.IMAGE_FILE_CONTENTS)
            throw new IllegalArgumentException("Kind-specific content checks require an explicit CLIAttachmentKinds policy");
        if(CLITraceEvidence.sixCalls(records)) {
            Set<String> selected=switch(check) {
                case HANDLERS -> Set.of("participant-identity","registry-current","registry-association","registry-reuse","handler-reuse","command-count","command-order","dispatcher-identity");
                case MODELS -> Set.of("type-field","choices-field","property-result","property-result-handler");
                case STORE -> Set.of("property-value","property-count","property-order","store-values","store-identity");
                case COMMAND_OUTPUT -> Set.of("result-delivery","command-io","display-count",
                    "notification-count","notification-order","notification-result",CLIObserverSeparationChecks.COMMAND_SEPARATION);
                case STREAM_OUTPUT -> Set.of(MinimalCLIContractChecks.STREAM_NOTIFICATION, "stream-sequence","stream-io",CLIObserverSeparationChecks.STREAM_SEPARATION);
                case FILE_CONTENTS -> Set.of("attachment-count","attachment-content");
                case TEXT_FILE_CONTENTS, IMAGE_FILE_CONTENTS -> throw new IllegalStateException("Kind-specific checks must run directly");
                case FILE_SEPARATION -> Set.of("expander-reuse","expander-instances");
                case HISTORY -> Set.of("history-count","history-order","snapshot-values");
            };
            // Property registration and reuse now have independent trace-history checks.
            var inspected = check == Check.HANDLERS ? records.stream().filter(record ->
                !CLITraceEvidence.is(record,eduAI.trace.cli.MinimalCLITrace.Event.PROPERTY_HANDLED)).toList() : records;
            if(check==Check.FILE_SEPARATION) return CLIExpanderSeparationChecks.check(inspected);
            Set<CLISixCallChecks.Evidence> needed=switch(check) {
                case MODELS, STORE -> Set.of(CLISixCallChecks.Evidence.PROPERTIES);
                case HISTORY -> Set.of(CLISixCallChecks.Evidence.SETTERS,CLISixCallChecks.Evidence.REQUESTS);
                case STREAM_OUTPUT -> Set.of(CLISixCallChecks.Evidence.STREAMS);
                case FILE_CONTENTS -> Set.of(CLISixCallChecks.Evidence.ATTACHMENTS);
                case HANDLERS, COMMAND_OUTPUT, FILE_SEPARATION -> Set.of();
                case TEXT_FILE_CONTENTS, IMAGE_FILE_CONTENTS -> throw new IllegalStateException("Kind-specific checks run directly");
            };
            return CLISixCallChecks.check(inspected,io,directory,selected,needed);
        }
        List<String> errors = new ArrayList<>();
        List<TraceRecord> ordered = records.stream().sorted(Comparator.comparing(TraceRecord::getTraceFileName)
            .thenComparingInt(TraceRecord::getTraceFileLineNumber)).toList();
        switch (check) {
            case HANDLERS -> handlers(ordered, errors);
            case MODELS -> models(ordered, errors);
            case STORE -> store(ordered, io, errors);
            case COMMAND_OUTPUT -> output(ordered, io, false, errors);
            case STREAM_OUTPUT -> output(ordered, io, true, errors);
            case FILE_CONTENTS -> files(ordered, io, directory, false, errors);
            case FILE_SEPARATION -> files(ordered, io, directory, true, errors);
            case HISTORY -> history(ordered, io, errors);
        }
        return new TraceCheckResult(List.of(), errors.isEmpty() ? List.of(check + " architectural evidence matches.") : List.of(), errors);
    }
    private static String event(TraceRecord r) { return r.getTraceLine().getEventName(); }
    private static Map<String,String> data(TraceRecord r) { return r.getTraceLine().getAuxiliaryData(); }
    private static List<TraceRecord> select(List<TraceRecord> records, Event event) {
        return records.stream().filter(r -> event(r).equals(event.eventName())).toList();
    }
    private static String className(String identity) { return identity == null ? "" : identity.split("@",2)[0]; }
    private static boolean identity(String value) { return value != null && value.contains("@") && !value.endsWith("@null"); }
    private static void require(boolean condition, List<String> errors, String message) { if (!condition) errors.add(message); }
    private static List<String> leaves(String value) { return new CLIResultEvidence(value).values(); }
    private static void stable(Map<String,String> map, String key, String value, List<String> errors, String message) {
        String first = map.putIfAbsent(key, value);
        require(identity(value) && (first == null || first.equals(value)), errors, message);
    }
    private static void handlers(List<TraceRecord> records, List<String> errors) {
        Map<String,String> registries = new HashMap<>(), handlers = new HashMap<>(), owners = new HashMap<>();
        String exitLookup = null; int exits = 0;
        for (var r : records) {
            var d=data(r); String e=event(r);
            if (e.equals(CLITraceActions.Event.COMMAND_HANDLER_FETCHED.eventName())) {
                // Registry dumps already contain compact identity evidence in the existing contract.
                String value=d.get(CLITraceActions.REGISTRY), first=registries.putIfAbsent("command",value);
                require(value != null && value.contains("@") && (first==null || first.equals(value)),errors,"Command registry identity");
                if (CLICommandNames.EXIT.equals(d.get(CLITraceActions.COMMAND_NAME))) exitLookup=d.get(CLITraceActions.HANDLER_IDENTITY);
            }
            if (e.equals(CLITraceActions.Event.PROPERTY_HANDLER_FETCHED.eventName())) {
                String name=d.get(CLITraceActions.PROPERTY_NAME);
                String value=d.get(CLITraceActions.PROPERTY_REGISTRY), first=registries.putIfAbsent("property:"+name,value);
                require(value != null && value.contains("@") && (first==null || first.equals(value)),errors,"Property registry identity");
                stable(handlers,name,d.get(CLITraceActions.PROPERTY_HANDLER_IDENTITY),errors,"Property handler reuse");
                String previous=owners.putIfAbsent(d.get(CLITraceActions.PROPERTY_HANDLER_IDENTITY),name);
                require(previous==null || previous.equals(name),errors,"Distinct property handlers");
            }
            if (e.equals(CLITraceActions.Event.COMMAND_HANDLER_RESULTS.eventName()) && CLICommandNames.EXIT.equals(d.get(CLITraceActions.COMMAND_NAME))) {
                exits++;
                require(identity(exitLookup) && exitLookup.equals(d.get(CLITraceActions.HANDLER_IDENTITY)),errors,"Exit lookup/result pairing");
                require(!leaves(d.get(CLITraceActions.COMMAND_RESULT)).isEmpty(),errors,"Exit result evidence");
                exitLookup=null;
            }
        }
        require(exits==1,errors,"Exit result count");
        for(String name : List.of(eduAI.properties.AIPropertyNames.CURRENT_MODELS,eduAI.properties.AIPropertyNames.SYSTEM_PROMPT,eduAI.properties.AIPropertyNames.STREAM,eduAI.properties.AIPropertyNames.TEMPERATURE,eduAI.properties.AIPropertyNames.TIMEOUT,eduAI.properties.AIPropertyNames.NUM_CTX,eduAI.properties.AIPropertyNames.TOP_K))
            require(handlers.containsKey(name),errors,"Missing property coverage: " + name);
    }
    private static void models(List<TraceRecord> records, List<String> errors) {
        Set<String> available=new HashSet<>(); int comparisons=0;
        for(var r:records) {
            var d=data(r);
            if(event(r).equals(Event.CLI_MODELS_AVAILABLE.eventName())) {
                require(identity(d.get(OWNER)),errors,"Model provider identity");
                available.addAll(leaves(d.get(VALUE)));
            }
            if(event(r).equals(CLITraceActions.Event.PROPERTY_HANDLER_RESULTS.eventName()) && eduAI.properties.AIPropertyNames.CURRENT_MODELS.equals(d.get(CLITraceActions.PROPERTY_NAME))) {
                Set<String> alternatives=new HashSet<>(leaves(d.get(CLITraceActions.RETURNED_ALTERNATIVES)));
                if(d.containsKey(CLITraceActions.PROPERTY_RESULT + eduAI.trace.cli.TraceDataPaths.ID)) {
                    var fields = CLIPropertyResultFields.choicesFields(List.of(d), available);
                    var choices = fields.size()==1
                        ? CLIPropertyResultFields.choices(d,fields.iterator().next()) : null;
                    alternatives = choices == null ? Set.of() : new HashSet<>(choices);
                }
                require(!available.isEmpty() && alternatives.size()==available.size() && alternatives.stream().allMatch(a->available.stream().anyMatch(b->eduAI.trace.TraceTextSummary.matches(a,b))),errors,"Complete model alternatives");
                comparisons++;
            }
        }
        require(comparisons>0,errors,"Missing model alternatives evidence");
    }
    private static void store(List<TraceRecord> records, CLIMixedIO io, List<String> errors) {
        String current=null; Set<String> snapshots=new HashSet<>();
        Map<String,Object> accumulated=new LinkedHashMap<>(); int read=0, requests=0;
        var getters=io.entries().stream().filter(e->e.input().kind()==CLIInteraction.Kind.PROPERTY_GET).toList();
        var prompts=io.entries().stream().filter(e->e.input().kind()==CLIInteraction.Kind.PROMPT).toList();
        var comparator=new FlattenedKeyValueComparator();
        for(var r:records) {
            var d=data(r); String e=event(r);
            if(e.equals(CLITraceActions.Event.CLI_PROPERTY_SET.eventName())) {
                if(current==null) current=d.get(CLITraceActions.PROPERTY_STORE_IDENTITY);
            }
            if(e.equals(Event.CLI_PROPERTY_READ.eventName())) {
                require(identity(d.get(STORE)) && Objects.equals(current,d.get(STORE)),errors,"Getter source store identity");
                if(read<getters.size()) {
                    var input=getters.get(read).input();
                    require(input.propertyName().equals(d.get(NAME)),errors,"Getter property name");
                    Object expected=input.properties().get(input.propertyName());
                    if(expected!=null) for(Object v: expected instanceof List<?> list ? list : List.of(expected))
                        require(new CLIResultEvidence(d.get(VALUE)).contains(String.valueOf(v)),errors,"Getter value");
                }
                read++;
            }
            if(e.equals(Event.CLI_REQUEST_SNAPSHOT.eventName())) {
                require(identity(d.get(STORE)) && Objects.equals(current,d.get(STORE)),errors,"Request source store identity");
                require(identity(d.get(SNAPSHOT)) && !Objects.equals(d.get(STORE),d.get(SNAPSHOT)) && snapshots.add(d.get(SNAPSHOT)),errors,"Request snapshot isolation");
                if(requests<prompts.size()) {
                    accumulated.clear(); accumulated.putAll(prompts.get(requests).input().properties());
                    for(var entry:accumulated.entrySet()) {
                        // Model selection and system instructions travel outside the provider parameter map;
                        // the reused request/message checks validate them.
                        if (entry.getKey().equals(eduAI.properties.AIPropertyNames.CURRENT_MODELS) || entry.getKey().equals(eduAI.properties.AIPropertyNames.SYSTEM_PROMPT)) continue;
                        for(Object v:entry.getValue() instanceof List<?> list ? list : List.of(entry.getValue()))
                        require(comparator.compare(d.get(VALUE),Map.of(entry.getKey(),String.valueOf(v))).matches(),errors,"Request snapshot values: " + entry.getKey());
                    }
                }
                requests++;
            }
        }
        require(read==getters.size() && read>0,errors,"Getter trace count");
        require(requests==prompts.size() && requests>0,errors,"Request snapshot count");
    }
    private static void output(List<TraceRecord> records, CLIMixedIO io, boolean stream, List<String> errors) {
        Event notified=stream?Event.CLI_STREAM_NOTIFIED:Event.CLI_COMMAND_NOTIFIED;
        Event displayed=stream?Event.CLI_STREAM_DISPLAYED:Event.CLI_COMMAND_DISPLAYED;
        Map<String,Map<String,String>> pending=new LinkedHashMap<>(); Set<String> viewers=new HashSet<>(), keys=new HashSet<>();
        List<String> actual=new ArrayList<>(); String owner=null;
        for(var r:records) {
            var d=data(r); String pair=d.get(KEY)+":"+d.get(ITEM)+":"+d.get(OBSERVER);
            if(event(r).equals(notified.eventName())) pending.put(pair,d);
            if(!event(r).equals(displayed.eventName())) continue;
            viewers.add(d.get(OBSERVER)); keys.add(d.get(KEY));
            var n=pending.remove(pair);
            require(n!=null,errors,"Observer notification pairing");
            require(identity(d.get(OBSERVER)) && identity(d.get(ITEM)),errors,"Observer/result identity");
            if(n==null) continue;
            if(owner==null) owner=n.get(OWNER);
            require(identity(n.get(OWNER)) && Objects.equals(owner,n.get(OWNER)) && !className(n.get(OWNER)).equals(className(d.get(OBSERVER))),errors,"Processing/display object separation");
            String output=String.join("\n",leaves(d.get(OUTPUT)));
            for(String value:leaves(n.get(VALUE))) {
                // Text may be prefixed per line by a stream observer.
                for(String part:value.split("\\R")) if(!part.isBlank())
                    require(output.contains(part),errors,"Displayed notification content");
            }
            actual.add(output);
            if(!stream) {
                var entry=io.entries().stream().filter(x->String.valueOf(x.number()).equals(d.get(KEY))).findFirst();
                require(entry.isPresent() && entry.get().output().isPresent() &&
                    entry.get().output().get().body().replace("\r\n","\n").strip().equals(output.replace("\r\n","\n").strip()),errors,"Command display/I-O agreement");
            }
        }
        require(!actual.isEmpty(),errors,"Missing observer display evidence");
        require(pending.values().stream().noneMatch(d->viewers.contains(d.get(OBSERVER))),errors,"Undisplayed notification");
        if(!stream) require(keys.size()==io.entries().size(),errors,"Command display count");
        else {
            List<String> expected=io.entries().stream().flatMap(e->e.chunks().stream()).map(CLIMixedIO.Chunk::text).toList();
            // The parser returns one entry per printed chunk line; compare text in chronological order.
            String emitted=String.join("\n",actual);
            int from=0;
            for(String chunk:expected) { int at=emitted.indexOf(chunk,from); require(at>=0,errors,"Stream display/I-O agreement"); if(at>=0) from=at+chunk.length(); }
            require(!expected.isEmpty(),errors,"Missing streaming I/O");
        }
    }
    private static void files(List<TraceRecord> records, CLIMixedIO io, Path directory, boolean separation, List<String> errors) {
        var expected=io.entries().stream().flatMap(e->e.attachments().stream()).toList();
        var actual=select(records,Event.CLI_FILE_EXPANDED); Map<String,String> expanders=new HashMap<>();
        if (!separation) require(expected.size()==actual.size(),errors,"File expansion count");
        for(int i=0;i<actual.size();i++) {
            var d=data(actual.get(i));
            if (separation) { stable(expanders,d.get(KIND),d.get(EXPANDER),errors,"File expander identity"); continue; }
            if(i>=expected.size()) continue;
            require(expected.get(i).path().equals(d.get(PATH)),errors,"File expansion path");
            try {
                byte[] bytes=Files.readAllBytes(directory.resolve(expected.get(i).path()));
                boolean image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes))!=null;
                require((image?IMAGE:TEXT).equals(d.get(KIND)),errors,"File expansion kind");
                String value=image?eduAI.trace.ImageByteSummary.from(bytes).formatted():new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                if(image) value=value.substring("imageBytes(".length(),value.length()-1);
                require(image ? d.getOrDefault(VALUE, "").contains("value=imageBytes(" + value + ")") : new CLIResultEvidence(d.get(VALUE)).contains(value),errors,"File expansion content");
            } catch(java.io.IOException e) { errors.add("Attachment unavailable: "+e.getMessage()); }
        }
        if (separation) require(expanders.containsKey(TEXT) && expanders.containsKey(IMAGE) && !Objects.equals(expanders.get(TEXT),expanders.get(IMAGE)),errors,"Separate text/image expanders");
    }
    private static void history(List<TraceRecord> records, CLIMixedIO io, List<String> errors) {
        Map<String,Map<String,String>> writes=new HashMap<>(), requests=new HashMap<>();
        Set<String> readKinds=new HashSet<>(), linked=new HashSet<>();
        for(var r:records) {
            var d=data(r); String e=event(r), key=d.get(KIND)+":"+d.get(KEY);
            if(e.equals(Event.CLI_REQUEST_SNAPSHOT.eventName())) requests.put(d.get(KEY),d);
            if(e.equals(Event.CLI_HISTORY_WRITTEN.eventName())) {
                var previous=writes.putIfAbsent(key,d);
                require(previous==null,errors,"Duplicate history write");
                require(identity(d.get(STORE)) && identity(d.get(ITEM)),errors,"History participant identity");
                if(TRANSCRIPT.equals(d.get(KIND))) require(io.entries().stream().anyMatch(x->String.valueOf(x.number()).equals(d.get(KEY))),errors,"History write has no input");
                if(MESSAGES.equals(d.get(KIND))) {
                    var request=requests.get(d.get(LINK));
                    require(request!=null && Objects.equals(d.get(KEY),d.get(LINK)) && Objects.equals(request.get(SNAPSHOT),d.get(SNAPSHOT)),errors,"History request/snapshot link");
                }
                if(TRANSCRIPT.equals(d.get(KIND)) && !d.getOrDefault(LINK,"").isEmpty()) {
                    require(requests.containsKey(d.get(LINK)),errors,"Transcript request link"); linked.add(d.get(LINK));
                }
            }
            if(e.equals(Event.CLI_HISTORY_READ.eventName())) {
                readKinds.add(d.get(KIND)); var written=writes.get(key);
                require(written!=null && Objects.equals(written.get(STORE),d.get(STORE)) && Objects.equals(written.get(LINK),d.get(LINK)),errors,"History read linkage");
                if(MESSAGES.equals(d.get(KIND))) require(written!=null && Objects.equals(written.get(ITEM),d.get(ITEM)) && Objects.equals(written.get(SNAPSHOT),d.get(SNAPSHOT)),errors,"Shared history request identity");
            }
        }
        require(readKinds.containsAll(List.of(TRANSCRIPT,MESSAGES)),errors,"Missing history reads");
        require(!requests.isEmpty() && linked.containsAll(requests.keySet()),errors,"Missing transcript request links");
        require(requests.keySet().stream().allMatch(k->writes.containsKey(MESSAGES+":"+k)),errors,"Missing request history writes");
    }
}
