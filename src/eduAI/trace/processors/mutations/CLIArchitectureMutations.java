package eduAI.trace.processors.mutations;
import java.util.*;
import eduAI.trace.TraceLineParser;
import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.cli.CLITraceActions;
import static eduAI.trace.cli.CLIArchitectureTrace.*;
import eduAI.trace.processors.cli.CLIArchitectureChecks.Check;

/** Every mutation names the check and diagnostic it must trigger. */
public final class CLIArchitectureMutations {
    public static final String EXPECTATION_FILE="architecture-expectation.tsv";
    public record Mutation(List<String> lines, Check check, String diagnostic) { }
    private final List<String> original;
    private final Map<String,Mutation> mutations=new LinkedHashMap<>();
    private CLIArchitectureMutations(List<String> lines) { original=lines; }
    public static Map<String,Mutation> create(List<String> lines) {
        var m=new CLIArchitectureMutations(lines);
        m.field("command_registry",CLITraceActions.Event.COMMAND_HANDLER_FETCHED.eventName(),CLITraceActions.REGISTRY,"other@123",Check.HANDLERS,"Command registry identity",1);
        m.field("property_registry",CLITraceActions.Event.PROPERTY_HANDLER_FETCHED.eventName(),CLITraceActions.PROPERTY_REGISTRY,"other@123",Check.HANDLERS,"Property registry identity",1);
        m.field("property_reuse",CLITraceActions.Event.PROPERTY_HANDLER_FETCHED.eventName(),CLITraceActions.PROPERTY_HANDLER_IDENTITY,"other@123",Check.HANDLERS,"Property handler reuse",1);
        m.remove("exit_return",CLITraceActions.Event.COMMAND_HANDLER_RESULTS.eventName(),Check.HANDLERS,"Exit result count",true);
        m.field("model_provider",Event.CLI_MODELS_AVAILABLE.eventName(),OWNER,"",Check.MODELS,"Model provider identity",0);
        m.field("model_catalog",Event.CLI_MODELS_AVAILABLE.eventName(),VALUE,TraceObjectPrinter.format(VALUE,"invented-model"),Check.MODELS,"Complete model alternatives",0);
        m.remove("model_catalog_missing",Event.CLI_MODELS_AVAILABLE.eventName(),Check.MODELS,"Complete model alternatives",false);
        for(boolean stream:List.of(false,true)) {
            String prefix=stream?"stream_":"command_"; Check check=stream?Check.STREAM_OUTPUT:Check.COMMAND_OUTPUT;
            String n=(stream?Event.CLI_STREAM_NOTIFIED:Event.CLI_COMMAND_NOTIFIED).eventName();
            String d=(stream?Event.CLI_STREAM_DISPLAYED:Event.CLI_COMMAND_DISPLAYED).eventName();
            m.remove(prefix+"notifications_missing",n,check,"Observer notification pairing",false);
            m.remove(prefix+"displays_missing",d,check,"Missing observer display evidence",false);
            for(String f:List.of(KEY,ITEM,OBSERVER)) m.field(prefix+"wrong_"+f,d,f,"other@123",check,"Observer notification pairing",0);
            m.field(prefix+"wrong_owner",n,OWNER,"",check,"Processing/display object separation",-1);
            m.field(prefix+"wrong_value",n,VALUE,TraceObjectPrinter.format(VALUE,"invented output"),check,"Displayed notification content",-1);
            m.field(prefix+"wrong_output",d,OUTPUT,TraceObjectPrinter.format(VALUE,"invented output"),check,"Displayed notification content",0);
        }
        m.remove("getter_missing",Event.CLI_PROPERTY_READ.eventName(),Check.STORE,"Getter trace count",false);
        m.remove("snapshot_missing",Event.CLI_REQUEST_SNAPSHOT.eventName(),Check.STORE,"Request snapshot count",false);
        m.field("getter_store",Event.CLI_PROPERTY_READ.eventName(),STORE,"other@123",Check.STORE,"Getter source store identity",0);
        m.field("getter_name",Event.CLI_PROPERTY_READ.eventName(),NAME,"wrong",Check.STORE,"Getter property name",0);
        m.field("getter_value",Event.CLI_PROPERTY_READ.eventName(),VALUE,"wrong",Check.STORE,"Getter value",0);
        m.field("snapshot_store",Event.CLI_REQUEST_SNAPSHOT.eventName(),STORE,"other@123",Check.STORE,"Request source store identity",0);
        m.field("snapshot_identity",Event.CLI_REQUEST_SNAPSHOT.eventName(),SNAPSHOT,"",Check.STORE,"Request snapshot isolation",0);
        m.field("snapshot_values",Event.CLI_REQUEST_SNAPSHOT.eventName(),VALUE,"wrong",Check.STORE,"Request snapshot values",0);
        m.remove("expansion_missing",Event.CLI_FILE_EXPANDED.eventName(),Check.FILE_CONTENTS,"File expansion count",false);
        for(var f:Map.of(PATH,"File expansion path",KIND,"File expansion kind",VALUE,"File expansion content",EXPANDER,"File expander identity").entrySet())
            m.field("expansion_"+f.getKey(),Event.CLI_FILE_EXPANDED.eventName(),f.getKey(),"wrong",f.getKey().equals(EXPANDER)?Check.FILE_SEPARATION:Check.FILE_CONTENTS,f.getValue(),0);
        m.sameExpander();
        m.field("history_unknown_input",Event.CLI_HISTORY_WRITTEN.eventName(),KEY,"99999",Check.HISTORY,"History write has no input",0);
        m.remove("history_reads_missing",Event.CLI_HISTORY_READ.eventName(),Check.HISTORY,"Missing history reads",false);
        m.remove("history_writes_missing",Event.CLI_HISTORY_WRITTEN.eventName(),Check.HISTORY,"History read linkage",false);
        m.field("history_write_store",Event.CLI_HISTORY_WRITTEN.eventName(),STORE,"",Check.HISTORY,"History participant identity",0);
        m.field("history_write_link",Event.CLI_HISTORY_WRITTEN.eventName(),LINK,"missing",Check.HISTORY,"Transcript request link",0);
        m.field("history_read_store",Event.CLI_HISTORY_READ.eventName(),STORE,"other@123",Check.HISTORY,"History read linkage",0);
        m.field("history_read_link",Event.CLI_HISTORY_READ.eventName(),LINK,"missing",Check.HISTORY,"History read linkage",0);
        m.historyMessageField("history_snapshot",true,SNAPSHOT,"other@123","History request/snapshot link");
        m.historyMessageField("history_copy",false,ITEM,"other@123","Shared history request identity");
        m.historyMessageField("history_read_snapshot",false,SNAPSHOT,"other@123","Shared history request identity");
        var duplicate=new ArrayList<>(lines);int at=m.index(Event.CLI_HISTORY_WRITTEN.eventName(),0);duplicate.add(at,duplicate.get(at));
        m.put("duplicate_history_write",duplicate,Check.HISTORY,"Duplicate history write");
        return Collections.unmodifiableMap(m.mutations);
    }
    private int index(String event,int occurrence) {
        for(int i=0;i<original.size();i++) if(TraceLineParser.parse(original.get(i)).getEventName().equals(event) && occurrence--==0) return i;
        throw new IllegalArgumentException("Missing mutation event: "+event);
    }
    private void field(String name,String event,String field,String value,Check check,String diagnostic,int occurrence) {
        var lines=new ArrayList<>(original);
        if(occurrence<0) { for(int i=0;i<lines.size();i++) if(TraceLineParser.parse(lines.get(i)).getEventName().equals(event)) lines.set(i,TraceFixtureFields.replace(lines.get(i),field,value)); }
        else {int i=index(event,occurrence);lines.set(i,TraceFixtureFields.replace(lines.get(i),field,value));}
        put(name,lines,check,diagnostic);
    }
    private void remove(String name,String event,Check check,String diagnostic,boolean exitOnly) {
        var lines=original.stream().filter(line->{var p=TraceLineParser.parse(line);return !p.getEventName().equals(event) || (exitOnly && !eduAI.trace.processors.cli.CLICommandNames.EXIT.equals(p.getAuxiliaryData().get(CLITraceActions.COMMAND_NAME)));}).toList();
        put(name,lines,check,diagnostic);
    }
    private void put(String name,List<String> lines,Check check,String diagnostic) {
        if(lines.equals(original)) throw new IllegalArgumentException("No-op architecture mutation: "+name);
        mutations.put("architecture_"+name,new Mutation(List.copyOf(lines),check,diagnostic));
    }
    private void sameExpander() {
        var lines=new ArrayList<>(original);String identity=TraceLineParser.parse(lines.get(index(Event.CLI_FILE_EXPANDED.eventName(),0))).getAuxiliaryData().get(EXPANDER);
        for(int i=0;i<lines.size();i++) if(TraceLineParser.parse(lines.get(i)).getEventName().equals(Event.CLI_FILE_EXPANDED.eventName())) lines.set(i,TraceFixtureFields.replace(lines.get(i),EXPANDER,identity));
        put("shared_file_expander",lines,Check.FILE_SEPARATION,"Separate text/image expanders");
    }
    private void historyMessageField(String name,boolean write,String field,String value,String diagnostic) {
        var lines=new ArrayList<>(original);String event=(write?Event.CLI_HISTORY_WRITTEN:Event.CLI_HISTORY_READ).eventName();
        for(int i=0;i<lines.size();i++) {var p=TraceLineParser.parse(lines.get(i)); if(p.getEventName().equals(event) && MESSAGES.equals(p.getAuxiliaryData().get(KIND))) {lines.set(i,TraceFixtureFields.replace(lines.get(i),field,value));break;}}
        put(name,lines,Check.HISTORY,diagnostic);
    }
}
