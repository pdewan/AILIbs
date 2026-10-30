package eduAI.trace.processors.mutations;

import java.util.*;
import java.util.function.Consumer;
import eduAI.trace.TraceLineParser;
import static eduAI.trace.cli.MinimalCLITrace.*;
import static eduAI.trace.cli.TraceDataPaths.ID;

/** Corrupts actual six-call evidence; each dataset names its intended rejecting diagnostic. */
public final class CLISixCallMutations {
    public static final String EXPECTATION_FILE = "six-call-expectation.txt";
    public record Mutation(String diagnostic, List<String> lines) { }
    private CLISixCallMutations() { }

    public static Map<String,Mutation> create(List<String> correct) {
        Map<String,Mutation> result = new LinkedHashMap<>();
        int commandOrdinal=0;
        for(String line:correct) if(is(line,Event.COMMAND_HANDLED)) {
            var d=fields(line);
            String name=d.get(NAME);
            if(name!=null && name.isBlank()) {
                int occurrence=commandOrdinal;
                edit(result,correct,Event.COMMAND_HANDLED,"prompt_unregistered_"+occurrence,"registry-association",occurrence,
                    data->data.entrySet().removeIf(e->e.getKey().startsWith(REGISTRY+".")
                        && e.getKey().endsWith(".value"+ID) && e.getValue().equals(data.get(HANDLER))));
                edit(result,correct,Event.COMMAND_HANDLED,"prompt_incidental_reference_"+occurrence,"registry-association",occurrence,data->{
                    String handler=data.get(HANDLER);
                    data.entrySet().removeIf(e->e.getKey().startsWith(REGISTRY+".")
                        && e.getKey().endsWith(".value"+ID) && handler.equals(e.getValue()));
                    data.put(REGISTRY+".incidental"+ID,handler);
                });
                edit(result,correct,Event.COMMAND_HANDLED,"prompt_named_"+occurrence,"command-order",occurrence,
                    data->data.put(NAME,"messages"));
                edit(result,correct,Event.COMMAND_HANDLED,"prompt_name_missing_"+occurrence,"command-order",occurrence,
                    data->data.remove(NAME));
                break;
            }
            commandOrdinal++;
        }
        int namedOrdinal=0;
        int promptCount=0, promptOrdinal=0;
        for(String line:correct) if(is(line,Event.COMMAND_HANDLED)) {
            String name=fields(line).get(NAME);
            if(name!=null && name.isBlank() && ++promptCount==2) {
                edit(result,correct,Event.COMMAND_HANDLED,"prompt_handler_replaced","handler-reuse",promptOrdinal,data->{
                    String old=data.get(HANDLER);
                    data.replaceAll((k,v)->old.equals(v)?"student.OtherPromptHandler@abc":v);
                });
                break;
            }
            promptOrdinal++;
        }
        for(String line:correct) if(is(line,Event.COMMAND_HANDLED)) {
            if(!fields(line).getOrDefault(NAME,"").isBlank()) {
                edit(result,correct,Event.COMMAND_HANDLED,"named_command_blank","command-order",namedOrdinal,
                    data->data.put(NAME,UNNAMED_COMMAND));
                break;
            }
            namedOrdinal++;
        }
        for (Event event : Event.values()) {
            String diagnostic = switch(event) {
                case COMMAND_HANDLED -> "command-count";
                case PROPERTY_HANDLED -> "property-count";
                case HISTORY_WITH_PARAMETERS_UPDATED -> "history-count";
                case COMMAND_DISPLAYED -> "display-count";
                case COMMAND_OUTPUT_NOTIFIED -> "notification-count";
                case STREAM_OUTPUT_NOTIFIED -> eduAI.trace.processors.cli.MinimalCLIContractChecks.STREAM_NOTIFICATION;
                case STREAM_DISPLAYED -> "stream-sequence";
                case FILE_EXPANDED -> "attachment-count";
            };
            var lines = new ArrayList<>(correct);
            lines.remove(index(correct,event,0));
            put(result,"missing_"+event.eventName(),diagnostic,correct,lines);
        }
        for(Event event:List.of(Event.COMMAND_HANDLED,Event.PROPERTY_HANDLED)) {
            edit(result,correct,event,"registry_identity","registry-current",0,d->d.put(REGISTRY,"wrong.Registry@abc"));
            edit(result,correct,event,"registry_contents","registry-association",0,d->d.keySet().removeIf(k->k.startsWith(REGISTRY+".")&&!k.equals(REGISTRY+ID)));
            edit(result,correct,event,"caller","participant-identity",0,d->d.remove(CALLER));
            edit(result,correct,event,"handler","registry-association",0,d->d.put(HANDLER,"wrong.Handler@abc"));
            int repeated = repeatedName(correct,event);
            edit(result,correct,event,"registry_changed","registry-reuse",repeated,d->{
                d.put(REGISTRY,"wrong.Registry@abc");
                d.put(REGISTRY+ID,"wrong.Registry@abc");
            });
            edit(result,correct,event,"handler_changed","handler-reuse",repeated,d->{
                String old=d.get(HANDLER);
                d.replaceAll((k,v)->Objects.equals(v,old)?"wrong.Handler@abc":v);
            });
        }
        String firstHandler=fields(correct.get(index(correct,Event.PROPERTY_HANDLED,0))).get(HANDLER);
        edit(result,correct,Event.PROPERTY_HANDLED,"shared_handler","property-instance",1,d->d.put(HANDLER,firstHandler));
        edit(result,correct,Event.COMMAND_HANDLED,"order","command-order",0,d->d.put(COMMAND_NUMBER,"-1"));
        edit(result,correct,Event.COMMAND_HANDLED,"dispatcher","dispatcher-identity",1,d->d.put(CALLER,"wrong.Dispatcher@abc"));
        edit(result,correct,Event.PROPERTY_HANDLED,"store","store-identity",1,d->d.put(STORE+ID,"wrong.Store@abc"));
        edit(result,correct,Event.PROPERTY_HANDLED,"value","property-value",0,d->d.replaceAll((k,v)->k.startsWith(VALUE+".")&&k.endsWith(VALUE_SUFFIX)?"wrong":v));
        edit(result,correct,Event.PROPERTY_HANDLED,"store_values","store-values",0,d->d.replaceAll((k,v)->k.startsWith(STORE+".")&&k.endsWith(VALUE_SUFFIX)?"wrong":v));
        edit(result,correct,Event.PROPERTY_HANDLED,"name","property-order",0,d->d.put(NAME,"wrong_property"));
        edit(result,correct,Event.PROPERTY_HANDLED,"result","property-result",0,d->d.remove(RESULT+ID));
        edit(result,correct,Event.PROPERTY_HANDLED,"handler_result","property-result-handler",0,d->d.put(RESULT+ID,d.get(HANDLER)));
        // Remove metadata from every property return to cover both inferred type and choices fields.
        for(String diagnostic:List.of("type-field","choices-field")) {
            var lines=new ArrayList<>(correct);
            for(int i=0;i<lines.size();i++) if(is(lines.get(i),Event.PROPERTY_HANDLED)) {
                var d=fields(lines.get(i));
                d.keySet().removeIf(k->k.startsWith(RESULT+".")&&!k.equals(RESULT+ID));
                lines.set(i,TraceFixtureFields.replaceFields(lines.get(i),d));
            }
            put(result,"missing_"+diagnostic,diagnostic,correct,lines);
        }
        String dispatcher=fields(correct.get(index(correct,Event.COMMAND_HANDLED,0))).get(CALLER);
        String observer=fields(correct.get(index(correct,Event.COMMAND_DISPLAYED,0))).get(OBSERVER);
        edit(result,correct,Event.COMMAND_OUTPUT_NOTIFIED,"source","notification-source",0,d->d.remove(OBSERVABLE));
        edit(result,correct,Event.COMMAND_OUTPUT_NOTIFIED,"command","notification-count",0,d->d.put(COMMAND_NUMBER,"-1"));
        edit(result,correct,Event.COMMAND_OUTPUT_NOTIFIED,"result_identity","notification-result",0,d->d.remove(RESULT+ID));
        edit(result,correct,Event.COMMAND_DISPLAYED,"different_result_identity","result-delivery",0,d->d.put(RESULT+ID,"student.Copy@abc"));
        String observable=fields(correct.get(index(correct,Event.COMMAND_OUTPUT_NOTIFIED,0))).get(OBSERVABLE);
        edit(result,correct,Event.COMMAND_DISPLAYED,"observable","command-separation",0,d->d.put(OBSERVER,observable));
        var duplicateNotification=new ArrayList<>(correct);
        int notification=index(correct,Event.COMMAND_OUTPUT_NOTIFIED,0);
        duplicateNotification.add(notification,correct.get(notification));
        put(result,"duplicate_notification","notification-count",correct,duplicateNotification);
        var prematureNotification=new ArrayList<>(correct);
        prematureNotification.add(index(correct,Event.COMMAND_HANDLED,0),prematureNotification.remove(notification));
        put(result,"premature_notification","notification-order",correct,prematureNotification);
        var lateNotification=new ArrayList<>(correct);
        String notificationLine=lateNotification.remove(notification);
        lateNotification.add(index(lateNotification,Event.COMMAND_DISPLAYED,0)+1,notificationLine);
        put(result,"late_notification","notification-order",correct,lateNotification);
        edit(result,correct,Event.COMMAND_DISPLAYED,"missing_identity","result-delivery",0,d->d.remove(RESULT+ID));
        String streamNotification = eduAI.trace.processors.cli.MinimalCLIContractChecks.STREAM_NOTIFICATION;
        edit(result,correct,Event.STREAM_OUTPUT_NOTIFIED,"missing_source",streamNotification,0,d->d.remove(OBSERVABLE));
        edit(result,correct,Event.STREAM_OUTPUT_NOTIFIED,"chunk",streamNotification,0,d->d.put(CHUNK+ID,"student.OtherChunk@abc"));
        edit(result,correct,Event.STREAM_OUTPUT_NOTIFIED,"model",streamNotification,0,d->d.put(MODEL,"wrong-model"));
        edit(result,correct,Event.STREAM_OUTPUT_NOTIFIED,"command",streamNotification,0,d->d.put(COMMAND_NUMBER,"-1"));
        edit(result,correct,Event.STREAM_DISPLAYED,"missing_identity","stream-sequence",0,d->d.remove(CHUNK+ID));
        edit(result,correct,Event.STREAM_DISPLAYED,"different_identity","stream-sequence",0,d->d.put(CHUNK+ID,"student.Copy@abc"));
        edit(result,correct,Event.STREAM_DISPLAYED,"model","stream-sequence",0,d->d.put(MODEL,"wrong-model"));
        // Model-free fixtures have no A1 callbacks; actual session fixtures exercise producer evidence too.
        for (int i=0;i<correct.size();i++) {
            if (!TraceLineParser.parse(correct.get(i)).getEventName().equals("partial_response_callback_invoked")) continue;
            for (boolean missing:List.of(true,false)) {
                var lines=new ArrayList<>(correct);
                var data=fields(lines.get(i));
                String key=eduAI.trace.lib.LibTraceKeys.CALLBACK_ARGUMENT_IDENTITY;
                if (missing) data.remove(key); else data.put(key,"student.WrongChunk@abc");
                lines.set(i,TraceFixtureFields.replaceFields(lines.get(i),data));
                put(result,missing?"missing_callback_identity":"wrong_callback_identity","stream-sequence",correct,lines);
            }
            break;
        }
        edit(result,correct,Event.COMMAND_DISPLAYED,"processor","command-separation",0,d->d.put(OBSERVER,dispatcher));
        edit(result,correct,Event.COMMAND_DISPLAYED,"output","command-io",0,d->d.put(OUTPUT,"wrong output"));
        edit(result,correct,Event.STREAM_DISPLAYED,"processor","stream-separation",0,d->d.put(OBSERVER,dispatcher));
        edit(result,correct,Event.STREAM_DISPLAYED,"observer","observer-instances",0,d->d.put(OBSERVER,observer));
        edit(result,correct,Event.STREAM_DISPLAYED,"output","stream-io",0,d->d.put(OUTPUT,"wrong output"));
        edit(result,correct,Event.STREAM_DISPLAYED,"command","stream-sequence",0,d->d.put(COMMAND_NUMBER,"-1"));
        edit(result,correct,Event.FILE_EXPANDED,"filename","attachment-content",0,d->d.put(INPUT_FILE_NAME,"wrong.txt"));
        String expander=fields(correct.get(index(correct,Event.FILE_EXPANDED,0))).get(EXPANDER);
        edit(result,correct,Event.FILE_EXPANDED,"shared","expander-instances",1,d->d.put(EXPANDER,expander));
        edit(result,correct,Event.FILE_EXPANDED,"identity","expander-reuse",0,d->d.remove(EXPANDER));
        edit(result,correct,Event.HISTORY_WITH_PARAMETERS_UPDATED,"command","history-count",0,d->d.put(COMMAND_NUMBER,"-1"));
        edit(result,correct,Event.HISTORY_WITH_PARAMETERS_UPDATED,"source","history-source",0,d->d.remove(CALLER));
        var premature=new ArrayList<>(correct);
        int history=index(premature,Event.HISTORY_WITH_PARAMETERS_UPDATED,0);
        String checkpoint=premature.remove(history);
        premature.add(index(premature,Event.PROPERTY_HANDLED,0),checkpoint);
        put(result,"premature_history","history-order",correct,premature);
        int last=0;
        for(String line:correct) if(is(line,Event.HISTORY_WITH_PARAMETERS_UPDATED)) last++;
        edit(result,correct,Event.HISTORY_WITH_PARAMETERS_UPDATED,"snapshots","snapshot-values",last-1,
            d->d.keySet().removeIf(k->k.startsWith(HISTORY+".")&&!k.equals(HISTORY+ID)));
        return Collections.unmodifiableMap(result);
    }
    private static final String VALUE_SUFFIX=eduAI.trace.cli.TraceDataPaths.VALUE;
    private static boolean is(String line,Event event) { return event.eventName().equals(TraceLineParser.parse(line).getEventName()); }
    private static Map<String,String> fields(String line) { return new LinkedHashMap<>(TraceLineParser.parse(line).getAuxiliaryData()); }
    private static int index(List<String> lines,Event event,int occurrence) {
        for(int i=0;i<lines.size();i++) if(is(lines.get(i),event) && occurrence--==0) return i;
        throw new IllegalArgumentException("Missing mutation target "+event);
    }
    private static int repeatedName(List<String> lines,Event event) {
        Set<String> names=new HashSet<>();
        int ordinal=0;
        for(String line:lines) if(is(line,event)) {
            if(!names.add(fields(line).get(NAME))) return ordinal;
            ordinal++;
        }
        throw new IllegalArgumentException("No repeated handler for "+event);
    }
    private static void edit(Map<String,Mutation> out,List<String> correct,Event event,String name,String diagnostic,
            int occurrence,Consumer<Map<String,String>> change) {
        var lines=new ArrayList<>(correct);
        int index=index(lines,event,occurrence);
        var data=fields(lines.get(index));
        change.accept(data);
        lines.set(index,TraceFixtureFields.replaceFields(lines.get(index),data));
        put(out,event.eventName()+"_"+name,diagnostic,correct,lines);
    }
    private static void put(Map<String,Mutation> out,String name,String diagnostic,List<String> correct,List<String> changed) {
        if(correct.equals(changed)) throw new IllegalArgumentException("No-op six-call mutation "+name);
        out.put("six_call_"+name,new Mutation(diagnostic,List.copyOf(changed)));
    }
}
