package eduAI.trace.processors._tests;
import java.util.*;
import eduAI.trace.*;
import eduAI.trace.cli.*;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
/**
 * Trace-only command checks must accept arbitrary values and multi-property
 * returns. Missing evidence, a dropped property, or borrowing a prior prompt's
 * answer must fail. These controls use neither I/O nor assignment input policy.
 */
public final class CLICommandConsistencyTest {
    private static int assertions;
    public static void main(String[] args) {
        var registry=new CLICommandResultCheckerRegistry();
        registry.register("prop",new CLIPropertyCommandResultChecker());
        registry.register("prompt",new CLIPromptCommandResultChecker());
        var records=new ArrayList<TraceRecord>();
        property(records,"timeout",120);property(records,"top_k",7);
        command(records,1,"prop",Map.of("timeout",120,"top_k",7));
        require(check(records,registry,"prop"),"Multi-property result must pass without input");
        var missing=new ArrayList<>(records);missing.remove(missing.size()-1);
        command(missing,1,"prop",Map.of("timeout",120));
        require(!check(missing,registry,"prop"),"Dropped property must fail");
        var noEvidence=new ArrayList<TraceRecord>();command(noEvidence,1,"prop","timeout=120");
        require(!check(noEvidence,registry,"prop"),"No property traces must earn no credit");
        require(!check(List.of(),registry,"prop"),"No returns must earn no credit");
        response(records,"arbitrary first answer");command(records,2,"","arbitrary first answer");
        response(records,"different second answer");command(records,3,"","different second answer");
        require(check(records,registry,"prompt"),"Unnamed prompts must use their own model responses");
        var wrong=new ArrayList<>(records);wrong.remove(wrong.size()-1);command(wrong,3,"","arbitrary first answer");
        require(!check(wrong,registry,"prompt"),"Prior answer must not satisfy current prompt");
        var absent=records.stream().filter(r->!r.getTraceLine().getEventName().equals("response_translated")).toList();
        require(!check(absent,registry,"prompt"),"No responses must earn no credit");
        require(!check(List.of(),registry,"prompt"),"No prompts must earn no credit");
        var duplicate=new ArrayList<>(records);command(duplicate,3,"","different second answer");
        require(!check(duplicate,registry,"prompt"),"Duplicate command numbers must fail");
        registry.register("messages",new CLIMessagesCommandResultChecker());
        registry.register("transcript",new CLITranscriptCommandResultChecker());
        for(String name:List.of("messages","transcript")) {
            var input=new CLIInteraction.Command(1,1,"/"+name,CLIInteraction.Kind.COMMAND,name,"",null,Map.of());
            var entry=new CLIMixedIO.Entry(1,input,List.of(),Optional.empty(),List.of());
            var context=new CLICommandResultContext(entry,List.of(),new CLIResultEvidence("value=history"),
                List.of(),Map.of(),Map.of(),List.of());
            require(registry.check(name,context).stream().anyMatch(error->error.startsWith("Insufficient evidence:")),
                "Empty prior history must not earn credit: "+name);
        }
        System.out.println("CLICommandConsistencyTest passed "+assertions+" assertions without an I/O file");
    }
    private static boolean check(Collection<TraceRecord> records,CLICommandResultCheckerRegistry registry,String name) {
        return CLICommandResultChecks.checkTraces(records,registry,name).passed();
    }
    private static void property(List<TraceRecord> records,String name,Object value) {
        var data=new LinkedHashMap<String,String>();data.put("name",name);
        data.putAll(TraceDataPaths.flatten("value",value,Set.of(),false));add(records,"property_handled",data);
    }
    private static void command(List<TraceRecord> records,int number,String name,Object result) {
        var data=new LinkedHashMap<String,String>();data.put("commandNumber",Integer.toString(number));data.put("name",name);
        data.putAll(TraceDataPaths.flatten("result",result,Set.of(),false));add(records,"command_handled",data);
    }
    private static void response(List<TraceRecord> records,String text) {
        var record=add(records,"response_translated",Map.of());
        record.putExtractedData("providerIndependentCanonicalResponse",new CanonicalTraceResponse(List.of(
            new GenericTracesFileProcessor.TraceMessage("ASSISTANT",List.of(new GenericTracesFileProcessor.TracePart("text",text)))),Map.of()));
    }
    private static TraceRecord add(List<TraceRecord> records,String event,Map<String,String> data) {
        int index=records.size();
        var record=new TraceRecord(new TraceRecordId("test",StreamingMode.NON_STREAMING,index),
            new TraceLine("cli",event,"none","test","test","Student",data,""),"session.txt",index+1);
        records.add(record);return record;
    }
    private static void require(boolean condition,String label) { assertions++;if(!condition) throw new AssertionError(label); }
}
