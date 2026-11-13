package eduAI.trace.processors._tests;

import java.util.*;
import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.cli.TraceDataPaths;
import eduAI.trace.processors.cli.BasicCLIMixedIOProcessor;

/** Class values remain scalar even inside collections; complete printed records
 * and body-only records preserve payload whitespace. Wrong numbers, malformed
 * delimiters and altered payloads must fail, not be repaired by stripping text. */
public final class CLIPrinterAndDisplayTest {
    private record Typed(Class<?> type) { }
    public static void main(String[] args) {
        for (Class<?> type : List.of(Double.class, int.class, String[].class, Typed.class)) {
            for (String dump : List.of(TraceObjectPrinter.format("type", type),
                    TraceObjectPrinter.formatFullEvidence("type", type))) {
                require(dump.equals("type: java.lang.Class value=\""+type.getName()+"\""), dump);
            }
            var data=TraceDataPaths.flatten("result",new Typed(type),Set.of(),false);
            require(data.get("result.Typed.type"+TraceDataPaths.VALUE).equals(type.getName()),data.toString());
            require(data.keySet().stream().noneMatch(k->k.endsWith(TraceDataPaths.ERROR)),data.toString());
        }
        var repeated=TraceDataPaths.flatten("values",List.of(Double.class,Double.class),Set.of(),false);
        require(repeated.values().stream().filter("java.lang.Double"::equals).count()==2,repeated.toString());
        String body=" answer \nsecond line\n";
        String full="[7] hello\n@@start [7] success=true conversation_id=default\n"+body+"\n@@end [7] (Time: 00:00:00.001)\n";
        for(String text:List.of(body,body+"\n",full,full.replace("\n","\r\n")))
            require(BasicCLIMixedIOProcessor.matchesDisplayedOutput(7,body,text),text);
        for(String text:List.of(full.replace("@@end [7]","@@end [8]"),full.replace("[7] hello","[8] hello"),
                full.replace("success=true","success=maybe"),full.replace(" answer ","answer"),full+"extra"))
            require(!BasicCLIMixedIOProcessor.matchesDisplayedOutput(7,body,text),"Accepted altered record: "+text);
        require(BasicCLIMixedIOProcessor.matchesDisplayedOutput(7,"","[7] hello\n"),"Empty output");
        System.out.println("CLIPrinterAndDisplayTest passed");
    }
    private static void require(boolean condition,String detail) {
        if(!condition) throw new AssertionError(detail);
    }
}
