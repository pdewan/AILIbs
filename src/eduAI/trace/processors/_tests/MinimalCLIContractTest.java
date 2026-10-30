package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.*;
import eduAI.trace.cli.MinimalCLITrace;
import eduAI.trace.cli.TraceDataPaths;
import eduAI.trace.processors.cli.MinimalCLIContractChecks;
import eduAI.trace.processors.cli.MinimalCLIContractChecks.*;
import static eduAI.trace.cli.MinimalCLITrace.*;
import eduAI.trace.processors.cli.CLIInteraction.Kind;

/** Model-free contract trial, with saved correct traces and targeted corruptions. */
public final class MinimalCLIContractTest {
    private static final class Participant {
        private final List<String> state=new ArrayList<>();
        @Override public String toString() { throw new AssertionError("Participant toString"); }
        @Override public int hashCode() { throw new AssertionError("Participant hashCode"); }
    }
    private record Result(String text, Map<String,Object> details) {
        Result(String text) { this(text,new LinkedHashMap<>()); }
    }
    private record PropertyResult(String category, List<String> options) { }
    private record Saved(String requestID, Map<String,String> parameters) { }
    private record Registry(Map<String,Object> registrations) { }
    private static final String[] NAMES={"timeout","num_ctx","top_k","stream","current_models","current_models","timeout"};
    private static final String[] VALUES={"37","2048","11","false","llava","gemini-model","83"};
    private static final List<String> MODELS=List.of("llava","gemini-model");
    private static final byte[] IMAGE={1,2,3,4};
    private static final String TEXT="Commentary with \"quotes\", ## and\nanother line.";
    private static final int REQUEST_COMMAND=1, SECOND_REQUEST_COMMAND=2, FIRST_SETTER_COMMAND=3;
    private static final int GETTER_COMMAND=FIRST_SETTER_COMMAND+NAMES.length;
    private static final int DISPLAY_COMMAND=GETTER_COMMAND+1;
    private static int assertions;
    private static final Result CHUNK_RESULT=new Result("chunk");
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args.length==0 ? "build/minimal-cli-contract" : args[0]);
        Files.createDirectories(directory);
        Path trace=directory.resolve("correct.txt");
        boolean output=GenericTrace.isOutputEnabled();
        boolean enabled=GenericTrace.isEnabled();
        try {
            GenericTrace.setEnabled(true);
            GenericTrace.setOutputEnabled(false);
            run(trace,false);
            List<String> lines=Files.readAllLines(trace);
            List<TraceLine> records=parse(lines);
            Expectations expectations=expectations();
            // Separation uses identities only: bad delivery data must not fail
            // it, while missing observer evidence cannot receive credit.
            require(eduAI.trace.processors.cli.CLIObserverSeparationChecks.check(records).isEmpty(),
                "Separate observer roles pass without I/O expectations");
            var wrongDelivery=records.stream().map(row->{
                if(!row.getEventName().equals(Event.COMMAND_DISPLAYED.eventName())) return row;
                var data=new LinkedHashMap<>(row.getAuxiliaryData());
                data.put(RESULT+TraceDataPaths.ID,"student.WrongResult@abc");
                return copy(row,data);
            }).toList();
            require(eduAI.trace.processors.cli.CLIObserverSeparationChecks.check(wrongDelivery).isEmpty(),
                "Delivery inconsistency does not fail role separation");
            require(MinimalCLIContractChecks.check(wrongDelivery,expectations).stream()
                .anyMatch(f->f.check().equals("result-delivery")),"Delivery inconsistency retains its own diagnostic");
            var noStreamObserver=records.stream().filter(row->!row.getEventName().equals(Event.STREAM_DISPLAYED.eventName())).toList();
            require(eduAI.trace.processors.cli.CLIObserverSeparationChecks.check(noStreamObserver).stream()
                .anyMatch(f->f.check().equals(eduAI.trace.processors.cli.CLIObserverSeparationChecks.SEPARATION_EVIDENCE)),
                "Missing stream observer earns no separation credit");
            require(MinimalCLIContractChecks.check(records,expectations).isEmpty(),
                "Correct run: "+MinimalCLIContractChecks.check(records,expectations));
            for(var insufficientRequests:List.of(List.<Request>of(),expectations.requests().subList(0,1),
                    List.of(expectations.requests().get(0),expectations.requests().get(0)))) {
                var missing=new Expectations(expectations.commands(),expectations.properties(),insufficientRequests,
                    expectations.commandOutput(),expectations.streams(),expectations.attachments());
                require(MinimalCLIContractChecks.check(records,missing).stream().anyMatch(f->f.check().equals("snapshot-values")
                    && f.detail().startsWith("Insufficient evidence:")),"Need two distinct prior requests for preservation");
            }
            var whitespacePrompt=records.stream().map(row->{
                if(!row.getEventName().equals("command_handled") || !row.getAuxiliaryData().get(NAME).isBlank()) return row;
                var data=new LinkedHashMap<>(row.getAuxiliaryData());
                data.put(NAME,"   ");
                return copy(row,data);
            }).toList();
            require(MinimalCLIContractChecks.check(whitespacePrompt,expectations).isEmpty(),
                "Whitespace prompt names allow arbitrary registry keys");
            require(MinimalCLIContractChecks.commandRegistered(Map.of(
                "registry.entries.0.key.@identity","student.Key@123",
                "registry.entries.0.value.@identity","student.Handler@456"),"","student.Handler@456"),
                "Unnamed prompt registration does not prescribe the key type");
            var metadata=records.stream().filter(r->r.getEventName().equals("property_handled")).map(TraceLine::getAuxiliaryData).toList();
            require(MinimalCLIContractChecks.typeCandidates(metadata,Set.of("timeout","num_ctx","top_k")).size()==1,"Infer type field");
            require(MinimalCLIContractChecks.choicesCandidates(metadata,"current_models",Set.copyOf(MODELS)).size()==1,"Infer choices field");
            require(records.stream().filter(r->r.getEventName().equals("command_handled")
                && r.getAuxiliaryData().containsKey(REGISTRY+TraceDataPaths.ID)).count()==DISPLAY_COMMAND,"Current command registry on every return");
            require(records.stream().filter(r->r.getEventName().equals("property_handled")
                && r.getAuxiliaryData().containsKey(REGISTRY+TraceDataPaths.ID)).count()==NAMES.length+1,"Current property registry on every return");
            require(lines.stream().filter(l->l.contains("store.@identity=")).count()==NAMES.length+1,"Store every time");
            require(lines.stream().noneMatch(l->l.contains("Participant.state")),"Never inspect participants");
            require(records.stream().filter(r->r.getEventName().equals("command_displayed")
                || r.getEventName().equals("stream_displayed")).noneMatch(r->r.getAuxiliaryData().containsKey(CALLER)),
                "Display traces do not claim a notifying source");
            String dispatcher=records.stream().filter(r->r.getEventName().equals("command_handled"))
                .findFirst().orElseThrow().getAuxiliaryData().get(CALLER);
            // Each case must trigger its intended diagnostic, not just some unrelated failure.
            List<String> report=new ArrayList<>();
            mutation(directory,records,expectations,report,MinimalCLIContractChecks.STREAM_NOTIFICATION,"stream_output_notified",d->d.remove(OBSERVABLE));
            mutation(directory,records,expectations,report,MinimalCLIContractChecks.STREAM_NOTIFICATION,"stream_output_notified",d->d.put(CHUNK+TraceDataPaths.ID,"student.Copy@abc"));
            mutation(directory,records,expectations,report,MinimalCLIContractChecks.STREAM_NOTIFICATION,"stream_output_notified",d->d.put(MODEL,"wrong-model"));
            mutation(directory,records,expectations,report,MinimalCLIContractChecks.STREAM_NOTIFICATION,"stream_output_notified",d->d.put(COMMAND_NUMBER,"-1"));
            mutation(directory,records,expectations,report,"registry-association","command_handled",
                d->{ d.replaceAll((k,v)->k.endsWith(".key.@value") && v.equals("prop") ? "wrong-key" : v); });
            mutation(directory,records,expectations,report,"registry-current","property_handled",
                d->d.keySet().removeIf(k->k.startsWith(REGISTRY+".")));
            for(String kind:List.of("property","command")) {
                var stale=staleRegistry(records,kind+"_handled");
                verify(stale,expectations,"registry-association");
                save(directory.resolve("stale-"+kind+"-registry.txt"),stale);
                report.add("stale-"+kind+"-registry\tregistry-association\tPASS");
            }
            var missingCommandRegistry=records.stream().map(row->{
                if(!row.getEventName().equals("command_handled")) return row;
                var data=new LinkedHashMap<>(row.getAuxiliaryData());
                data.keySet().removeIf(k->k.startsWith(REGISTRY+"."));
                return copy(row,data);
            }).toList();
            verify(missingCommandRegistry,expectations,"registry-current");
            save(directory.resolve("missing-command-registry.txt"),missingCommandRegistry);
            report.add("missing-command-registry\tregistry-current\tPASS");
            mutation(directory,records,expectations,report,"handler-reuse","property_handled",
                d->d.put(HANDLER,"student.Handler@abc"));
            mutation(directory,records,expectations,report,"store-identity","property_handled",
                d->d.put(STORE+TraceDataPaths.ID,"student.Store@abc"));
            mutation(directory,records,expectations,report,"property-value","property_handled",
                d->d.put(VALUE+TraceDataPaths.VALUE,"wrong"));
            mutation(directory,records,expectations,report,"store-values","property_handled",
                d->d.replaceAll((k,v)->k.startsWith("store.") && k.endsWith(".value.@value") ? "wrong" : v));
            var copiedResults=records.stream().map(row->{
                if(!row.getEventName().equals("command_displayed")
                        && !row.getEventName().equals("command_output_notified")) return row;
                var data=new LinkedHashMap<>(row.getAuxiliaryData());
                var leaves=MinimalCLIContractChecks.values(data,RESULT);
                data.keySet().removeIf(k->k.startsWith(RESULT+"."));
                data.put(RESULT+TraceDataPaths.ID,"student.NewResult@abc");
                for(int i=0;i<leaves.size();i++) data.put(RESULT+".NewResult.copied.items."+i+TraceDataPaths.VALUE,leaves.get(i));
                data.put(RESULT+".NewResult.extra"+TraceDataPaths.VALUE,"additional display information");
                return copy(row,data);
            }).toList();
            require(MinimalCLIContractChecks.check(copiedResults,expectations).isEmpty(),
                "Copied results with renamed fields, new nesting and added data must pass");
            save(directory.resolve("correct-copied-results.txt"),copiedResults);
            var multipleObservers=new ArrayList<TraceLine>();
            for(var row:records) {
                multipleObservers.add(row);
                if(row.getEventName().equals("command_displayed")) {
                    var data=new LinkedHashMap<>(row.getAuxiliaryData());
                    data.put(OBSERVER,"student.SecondObserver@abc");
                    multipleObservers.add(copy(row,data));
                }
            }
            require(MinimalCLIContractChecks.check(multipleObservers,expectations).isEmpty(),
                "One notification can deliver to two distinct observers");
            require(eduAI.trace.processors.cli.CLISixCallChecks.hasOutputPipe("/messages | head 2"),"Recognize deferred filter");
            require(!eduAI.trace.processors.cli.CLISixCallChecks.hasOutputPipe("/prop system_prompt = \"a | b\""),"Quoted pipe is data");
            require(!eduAI.trace.processors.cli.CLISixCallChecks.hasOutputPipe("a \\| b"),"Escaped pipe is data");
            for (var row:records) {
                String prefix=switch(row.getEventName()) {
                    case "command_output_notified", "command_displayed" -> RESULT;
                    case "stream_output_notified", "stream_displayed" -> CHUNK;
                    default -> null;
                };
                if(prefix!=null) require(row.getAuxiliaryData().keySet().stream()
                    .noneMatch(k->k.startsWith(prefix+".")&&!k.equals(prefix+TraceDataPaths.ID)),
                    "Notification and display payloads must be identity-only");
            }
            var changedPayload=records.stream().map(row->{
                if(!row.getEventName().equals("command_displayed")) return row;
                var data=new LinkedHashMap<>(row.getAuxiliaryData());
                data.put("result.Result.text.@value","irrelevant payload field");
                return copy(row,data);
            }).toList();
            require(MinimalCLIContractChecks.check(changedPayload,expectations).isEmpty(),"Observer payload fields must not be interpreted");
            save(directory.resolve("changed-original-result-data.txt"),changedPayload);
            report.add("observer-payload-ignored\tpositive\tPASS");
            mutation(directory,records,expectations,report,"command-separation","command_displayed",
                d->d.put(OBSERVER,dispatcher));
            mutation(directory,records,expectations,report,"command-io","command_displayed",d->d.put(OUTPUT,"wrong"));
            mutation(directory,records,expectations,report,"snapshot-values","history_with_parameters_updated",
                d->d.replaceAll((k,v)->k.startsWith("history.") && v.equals("37") ? "83" : v));
            mutation(directory,records,expectations,report,"stream-sequence","stream_displayed",d->d.put(COMMAND_NUMBER,"2"));
            mutation(directory,records,expectations,report,"stream-separation","stream_displayed",d->d.put(OBSERVER,dispatcher));
            String regular=records.stream().filter(r->r.getEventName().equals("command_displayed")).findFirst().orElseThrow().getAuxiliaryData().get(OBSERVER);
            mutation(directory,records,expectations,report,"observer-instances","stream_displayed",d->d.put(OBSERVER,regular));
            mutation(directory,records,expectations,report,"attachment-content","file_expanded",d->d.put(INPUT_FILE_NAME,"wrong.txt"));
            mutation(directory,records,expectations,report,"type-field","property_handled",
                d->{if(d.get(NAME).equals("top_k")) d.replaceAll((k,v)->k.endsWith(".category.@value") ? "wrong" : v);});
            mutation(directory,records,expectations,report,"property-result","property_handled",
                d->d.remove(RESULT+TraceDataPaths.ID));
            mutation(directory,records,expectations,report,"property-result-handler","property_handled",
                d->d.put(RESULT+TraceDataPaths.ID,d.get(HANDLER)));
            mutation(directory,records,expectations,report,"choices-field","property_handled",
                d->d.replaceAll((k,v)->k.startsWith("result.") && v.equals("gemini-model") ? "wrong" : v));
            var ambiguous=records.stream().map(row->{
                if(!row.getEventName().equals("property_handled")) return row;
                var data=new LinkedHashMap<>(row.getAuxiliaryData());
                data.put("result.PropertyResult.anotherCategory.@value",
                    data.get("result.PropertyResult.category.@value"));
                return copy(row,data);
            }).toList();
            verify(ambiguous,expectations,"type-field");
            save(directory.resolve("ambiguous-type.txt"),ambiguous);
            report.add("ambiguous-type\ttype-field\tPASS");
            String firstExpander=records.stream().filter(r->r.getEventName().equals("file_expanded")).findFirst().orElseThrow().getAuxiliaryData().get(EXPANDER);
            mutation(directory,records,expectations,report,"expander-instances","file_expanded",d->d.put(EXPANDER,firstExpander));
            for(var item:Map.of("command_handled","command-count","property_handled","property-count",
                    "command_displayed","display-count","history_with_parameters_updated","history-count",
                    "stream_output_notified",MinimalCLIContractChecks.STREAM_NOTIFICATION,
                    "stream_displayed","stream-sequence","file_expanded","attachment-count").entrySet()) {
                var changed=records.stream().filter(r->!r.getEventName().equals(item.getKey())).toList();
                verify(changed,expectations,item.getValue());
                save(directory.resolve("missing-"+item.getKey()+".txt"),changed);
                report.add("missing-"+item.getKey()+"\t"+item.getValue()+"\tPASS");
            }
            for(int wrongCommand:List.of(REQUEST_COMMAND,GETTER_COMMAND,DISPLAY_COMMAND)) {
                var changed=new ArrayList<>(records);
                var checkpoint=records.stream().filter(r->r.getEventName().equals("history_with_parameters_updated"))
                    .findFirst().orElseThrow();
                var fields=new LinkedHashMap<>(checkpoint.getAuxiliaryData());
                fields.put(COMMAND_NUMBER,Integer.toString(wrongCommand));
                changed.add(copy(checkpoint,fields));
                verify(changed,expectations,"history-count");
                String name="history-after-non-setter-"+wrongCommand;
                save(directory.resolve(name+".txt"),changed);
                report.add(name+"\thistory-count\tPASS");
            }
            // In-place mutation of a shared snapshot must fail even when identities remain equal.
            var premature=new ArrayList<>(records);
            int checkpointIndex=0;
            while(!premature.get(checkpointIndex).getEventName().equals("history_with_parameters_updated")) checkpointIndex++;
            premature.add(0,premature.remove(checkpointIndex));
            verify(premature,expectations,"history-order");
            save(directory.resolve("premature-setter-checkpoint.txt"),premature);
            report.add("premature-setter-checkpoint\thistory-order\tPASS");
            run(directory.resolve("aliased-snapshot.txt"),true);
            verify(parse(Files.readAllLines(directory.resolve("aliased-snapshot.txt"))),expectations,"snapshot-values");
            report.add("aliased-snapshot\tsnapshot-values\tPASS");
            // Registry contents are present in each output session, even when objects survive.
            Object caller=new Participant(), handler=new Participant();
            Registry registry=new Registry(Map.of("prop",handler));
            for(int i=0;i<2;i++) {
                Path sessionFile=directory.resolve("session-"+i+".txt");
                GenericTrace.setTraceFile(sessionFile);
                commandHandled(caller,1,"prop",registry,handler,new Result("ok"));
                GenericTrace.clearTraceFile();
                require(Files.readString(sessionFile).contains("registry.@identity="),"New session registry dump");
            }
            for (var entry : eduAI.trace.processors.mutations.CLISixCallMutations.create(lines).entrySet()) {
                verify(parse(entry.getValue().lines()), expectations, entry.getValue().diagnostic());
                Files.write(directory.resolve(entry.getKey()+".txt"), entry.getValue().lines());
                report.add(entry.getKey()+"\t"+entry.getValue().diagnostic()+"\tPASS");
            }
            Files.write(directory.resolve("mutations.tsv"),report);
            long small=streamSize(directory.resolve("stream-100.txt"),100);
            long large=streamSize(directory.resolve("stream-200.txt"),200);
            require(large<=small*2+256,"Streaming trace grows linearly despite accumulated source state");
            System.out.println("MinimalCLIContractTest: "+assertions+" assertions passed; "+report.size()+" targeted mutations rejected.");
            System.out.println("Streaming sizes: "+small+" / "+large+" bytes for 100 / 200 chunks.");
            System.out.println("Artifacts: "+directory.toAbsolutePath());
        } finally {
            GenericTrace.clearTraceFile();
            GenericTrace.setOutputEnabled(output);
            GenericTrace.setEnabled(enabled);
        }
    }
    private static void run(Path path,boolean alias) {
        GenericTrace.setTraceFile(path);
        Participant dispatcher=new Participant(), propertyCaller=new Participant();
        Participant regular=new Participant(), stream=new Participant();
        Map<String,Object> commandHandlers=new LinkedHashMap<>();
        Registry commands=new Registry(commandHandlers);
        Map<String,Object> propertyHandlers=new LinkedHashMap<>();
        Registry properties=new Registry(propertyHandlers);
        Map<String,String> store=new LinkedHashMap<>();
        store.put("timeout","37");
        List<Saved> history=new ArrayList<>();
        history.add(new Saved("request-one",alias ? store : new LinkedHashMap<>(store)));
        history.add(new Saved("request-two",alias ? store : new LinkedHashMap<>(store)));
        // Request parameters are supplied by A1 evidence; no history checkpoint here.
        complete(dispatcher,regular,commands,REQUEST_COMMAND,"prompt");
        complete(dispatcher,regular,commands,SECOND_REQUEST_COMMAND,"prompt");
        for(int i=0;i<NAMES.length;i++) {
            propertyHandlers.computeIfAbsent(NAMES[i],k->new Participant());
            store.put(NAMES[i],VALUES[i]);
            String type=NAMES[i].equals("stream") ? "switch" : NAMES[i].equals("current_models") ? "model-list" : "count";
            List<String> options=NAMES[i].equals("current_models") ? MODELS : List.of();
            propertyHandled(propertyCaller,NAMES[i],properties,propertyHandlers.get(NAMES[i]),
                new PropertyResult(type,options),store,VALUES[i]);
            historyWithParametersUpdated(propertyCaller,FIRST_SETTER_COMMAND+i,history);
            complete(dispatcher,regular,commands,FIRST_SETTER_COMMAND+i,"prop");
        }
        propertyHandled(propertyCaller,"timeout",properties,propertyHandlers.get("timeout"),
            new PropertyResult("count",List.of()),store,store.get("timeout"));
        complete(dispatcher,regular,commands,GETTER_COMMAND,"prop");
        complete(dispatcher,regular,commands,DISPLAY_COMMAND,"messages");
        streamOutputNotified(dispatcher,REQUEST_COMMAND,"llava",CHUNK_RESULT);
        streamDisplayed(stream,REQUEST_COMMAND,"llava",CHUNK_RESULT,"[llava] chunk");
        fileExpanded(propertyCaller,"Commentary.txt",new Participant(),TEXT);
        fileExpanded(propertyCaller,"Image.png",new Participant(),IMAGE);
        GenericTrace.clearTraceFile();
    }
    private static void complete(Object dispatcher,Object observer,Registry commands,int number,String command) {
        boolean prompt=command.equals("prompt");
        Object handler=commands.registrations.computeIfAbsent(prompt?"student-chosen-default":command,k->new Participant());
        Result result=new Result("ok-"+number);
        commandHandled(dispatcher,number,prompt?UNNAMED_COMMAND:command,commands,handler,result);
        result.details.put("sequence",number);
        commandOutputNotified(dispatcher,number,result);
        commandDisplayed(observer,number,result,"ok-"+number);
    }
    private static long streamSize(Path path,int count) throws Exception {
        Participant source=new Participant(), observer=new Participant();
        GenericTrace.setTraceFile(path);
        for(int i=0;i<count;i++) {
            source.state.add("growing callback state".repeat(100));
            var chunk=new Result("chunk");
            streamOutputNotified(source,1,"llava",chunk);
            streamDisplayed(observer,1,"llava",chunk,"chunk");
        }
        GenericTrace.clearTraceFile();
        return Files.size(path);
    }
    private static Expectations expectations() {
        Map<Integer,String> commands=new LinkedHashMap<>(), outputs=new LinkedHashMap<>();
        List<Property> properties=new ArrayList<>();
        commands.put(REQUEST_COMMAND,"prompt");
        commands.put(SECOND_REQUEST_COMMAND,"prompt");
        for(int i=0;i<NAMES.length;i++) {
            int number=FIRST_SETTER_COMMAND+i;
            commands.put(number,"prop");
            properties.add(new Property(number,Kind.PROPERTY_SET,NAMES[i],VALUES[i]));
        }
        commands.put(GETTER_COMMAND,"prop");
        commands.put(DISPLAY_COMMAND,"messages");
        properties.add(new Property(GETTER_COMMAND,Kind.PROPERTY_GET,"timeout","83"));
        commands.keySet().forEach(n->outputs.put(n,"ok-"+n));
        return new Expectations(commands,properties,List.of(new Request(REQUEST_COMMAND,"request-one",Map.of("timeout","37")),
            new Request(SECOND_REQUEST_COMMAND,"request-two",Map.of("timeout","37"))),
            outputs,List.of(new Stream(REQUEST_COMMAND,"llava","chunk","[llava] chunk",TraceObjectPrinter.objectIdentity(CHUNK_RESULT))),
            List.of(new Attachment("Commentary.txt",TEXT,false),
                new Attachment("Image.png",ImageByteSummary.from(IMAGE).formatted(),true)));
    }
    private static List<TraceLine> staleRegistry(List<TraceLine> records,String event) {
        List<TraceLine> stale=new ArrayList<>();
        Map<String,String> first=null;
        for(var row:records) {
            if(!row.getEventName().equals(event)) { stale.add(row); continue; }
            var data=new LinkedHashMap<>(row.getAuxiliaryData());
            if(first==null) first=MinimalCLIContractChecks.prefix(data,REGISTRY);
            data.keySet().removeIf(k->k.startsWith(REGISTRY+"."));
            data.putAll(first);
            stale.add(copy(row,data));
        }
        return stale;
    }
    private static List<TraceLine> parse(List<String> lines) {
        return lines.stream().filter(l->!l.isBlank()).map(TraceLineParser::parse).toList();
    }
    private static void mutation(Path directory,List<TraceLine> original,Expectations expectations,List<String> report,
            String check,String event,java.util.function.Consumer<Map<String,String>> edit) throws Exception {
        List<TraceLine> changed=new ArrayList<>();
        // Change all occurrences so count/identity checks are exercised across the session.
        for(var row:original) {
            Map<String,String> data=new LinkedHashMap<>(row.getAuxiliaryData());
            if(row.getEventName().equals(event)) edit.accept(data);
            changed.add(copy(row,data));
        }
        // A consistently wrong handler still violates its registry association, but reuse
        // requires changing only the final occurrence.
        if(check.equals("handler-reuse") || check.equals("store-identity")) {
            changed=new ArrayList<>(original);
            for(int i=changed.size()-1;i>=0;i--) if(changed.get(i).getEventName().equals(event)) {
                var data=new LinkedHashMap<>(changed.get(i).getAuxiliaryData()); edit.accept(data);
                changed.set(i,copy(changed.get(i),data)); break;
            }
        }
        verify(changed,expectations,check);
        save(directory.resolve(check+".txt"),changed);
        verify(parse(Files.readAllLines(directory.resolve(check+".txt"))),expectations,check);
        report.add(check+"\t"+check+"\tPASS");
    }
    private static TraceLine copy(TraceLine row,Map<String,String> data) {
        return new TraceLine(row.getLayerName(),row.getEventName(),row.getThreadPatternName(),
            row.getThreadName(),row.getDesignPatternName(),row.getSourceClassName(),data,"");
    }
    private static void save(Path path,List<TraceLine> records) throws Exception {
        // Use the real emitter so fixture encoding follows the production trace grammar.
        GenericTrace.setTraceFile(path);
        for(var row:records) {
            Event event=Event.valueOf(row.getEventName().toUpperCase(Locale.ROOT));
            GenericTrace.traceDesignPattern(event,MinimalCLIContractTest.class,event,row.getAuxiliaryData());
        }
        GenericTrace.clearTraceFile();
    }
    private static void verify(List<TraceLine> changed,Expectations expected,String check) {
        var errors=MinimalCLIContractChecks.check(changed,expected);
        // Every emitted diagnostic must have an explicit structure, semantics
        // or unreadable-evidence classification; new rules cannot disappear.
        errors.forEach(f -> eduAI.trace.processors.cli.CLIContractDomains.domain(f.check()));
        require(errors.stream().anyMatch(f->f.check().equals(check)),"Expected "+check+", got "+errors);
    }
    private static void require(boolean condition,String message) {
        assertions++;
        if(!condition) throw new AssertionError(message);
    }
}
