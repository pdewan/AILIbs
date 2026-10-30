package eduAI.trace.processors._tests;
import java.util.*;
import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.processors.cli.*;
import eduAI.properties.AIPropertyNames;

/** Arbitrary two-exchange histories pass without files or changed settings.
 * Removing exchanges, completed responses or system evidence prevents credit. */
public final class CLIHistoryEvidenceTest {
    public static void main(String[] args) {
        int mutations=0;
        for(String seed:List.of("orchard","lighthouse")) {
            for(String command:List.of(CLICommandNames.TRANSCRIPT,CLICommandNames.MESSAGES)) {
                for(String option:command.equals(CLICommandNames.TRANSCRIPT)?List.of(""):List.of("",CLICommandNames.SYSTEM_OPTION)) {
                    var correct=context(seed,command,option,2,true,true);
                    var checker=command.equals(CLICommandNames.TRANSCRIPT)?(CLICommandResultChecker)new CLITranscriptCommandResultChecker():new CLIMessagesCommandResultChecker();
                    require(checker.check(correct).isEmpty(),"Correct arbitrary history: "+checker.check(correct));
                    for(int count:List.of(0,1)) {
                        insufficient(checker.check(context(seed,command,option,count,true,true)));mutations++;
                    }
                    insufficient(checker.check(context(seed,command,option,2,false,true)));mutations++;
                    if(command.equals(CLICommandNames.MESSAGES)) {
                        insufficient(checker.check(context(seed,command,option,2,true,false)));mutations++;
                    }
                }
            }
        }
        System.out.println("CLIHistoryEvidenceTest passed: 6 arbitrary two-exchange controls without attachments or setting changes; "+mutations+" mutations rejected");
    }
    private static CLICommandResultContext context(String seed,String command,String option,int count,boolean complete,boolean systems) {
        var history=new ArrayList<CLIMixedIO.Entry>();var responses=new LinkedHashMap<Integer,List<String>>();
        var text=new StringBuilder();
        for(int n=1;n<=count;n++) {
            String prompt=seed+" question "+n,answer=seed+" answer "+n;
            Map<String,Object> properties=systems?Map.of(AIPropertyNames.SYSTEM_PROMPT,seed+" instruction"):Map.of();
            var input=new CLIInteraction.Command(n,n,prompt,CLIInteraction.Kind.PROMPT,CLICommandNames.PROMPT,prompt,null,properties);
            history.add(new CLIMixedIO.Entry(n,input,List.of(),Optional.empty(),List.of()));
            if(complete || n==1) responses.put(n,List.of(answer));
            text.append(prompt).append('\n').append(answer).append('\n');
        }
        if(option.equals(CLICommandNames.SYSTEM_OPTION) && systems) text.append(seed).append(" instruction");
        var input=new CLIInteraction.Command(count+1,count+1,"/"+command,CLIInteraction.Kind.COMMAND,command,option,null,Map.of());
        var current=new CLIMixedIO.Entry(count+1,input,List.of(),Optional.empty(),List.of());
        return new CLICommandResultContext(current,history,new CLIResultEvidence(TraceObjectPrinter.formatFullEvidence("result",text.toString())),
            List.of(),responses,Map.of(),List.of());
    }
    private static void insufficient(List<String> errors) { require(errors.stream().anyMatch(e->e.startsWith("Insufficient evidence:")),"Missing evidence earned credit: "+errors); }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
