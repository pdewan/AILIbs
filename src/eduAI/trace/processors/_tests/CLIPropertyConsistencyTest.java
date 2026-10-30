package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.GenericTrace;
import eduAI.trace.cli.MinimalCLITrace;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
import eduAI.trace.processors.cli.CLIPropertyConsistencyChecks.Check;
import eduAI.trace.processors.cli.CLIPropertyDescriptor.ValueType;
import eduAI.trace.processors.mutations.CLIPropertyConsistencyMutations;

/** Independent trace histories, arbitrary values/type labels, and targeted mutations. */
public final class CLIPropertyConsistencyTest {
    private record Result(String classification,List<String> candidates) { }
    private record Registry(Map<String,Object> registrations) { }
    private static final Map<String,CLIPropertyDescriptor> TYPES=Map.of(
        "timeout",new CLIPropertyDescriptor(ValueType.INTEGER,List.of()),
        "top_k",new CLIPropertyDescriptor(ValueType.INTEGER,List.of()),
        "stream",new CLIPropertyDescriptor(ValueType.BOOLEAN,List.of()),
        "current_models",new CLIPropertyDescriptor(ValueType.STRING_LIST,List.of()));
    private static int assertions;
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply a test output directory");
        Path directory=Path.of(args[0]); Files.createDirectories(directory);
        boolean enabled=GenericTrace.isEnabled(),output=GenericTrace.isOutputEnabled();
        try {
            GenericTrace.setEnabled(true); GenericTrace.setOutputEnabled(false);
            for(int value:List.of(17,83)) {
                Path trace=directory.resolve("correct-"+value+".txt");
                write(trace,value);
                var records=read(trace);
                for(var rule:Check.values()) require(check(rule,records).passed(),rule+": "+check(rule,records));
                for(var entry:CLIPropertyConsistencyMutations.create(Files.readAllLines(trace)).entrySet()) {
                    Path mutant=directory.resolve(entry.getKey()+"-"+value+".txt");
                    Files.write(mutant,entry.getValue().lines());
                    require(!check(entry.getValue().check(),read(mutant)).passed(),"Surviving mutation "+entry.getKey());
                    if (entry.getKey().equals(CLIPropertyConsistencyMutations.PREFIX+"changed_store"))
                        require(check(Check.STORE,read(mutant)).passed(),"Identity change must not fail stored-value semantics");
                    if (entry.getKey().equals(CLIPropertyConsistencyMutations.PREFIX+"lost_store_values"))
                        require(check(Check.STORE_IDENTITY,read(mutant)).passed(),"Lost values must not fail stable store identity");
                }
                var history=CLIPropertyTraceHistory.read(records);
                int firstCommand=history.operations().get(0).command();
                var oneCommand=records.stream().filter(record->record.getTraceFileLineNumber()<=records.stream()
                    .filter(r->CLITraceEvidence.is(r,MinimalCLITrace.Event.COMMAND_HANDLED)
                        && CLITraceEvidence.number(r.getTraceLine().getAuxiliaryData())==firstCommand)
                    .findFirst().orElseThrow().getTraceFileLineNumber()).toList();
                for(var rule:List.of(Check.HANDLER_REUSE,Check.DISTINCT_HANDLERS,Check.GETTERS,Check.STORE,Check.STORE_IDENTITY,Check.RESULT_TYPES,Check.ALTERNATIVES))
                    require(check(rule,oneCommand).level3ErrorMessages().stream().anyMatch(e->e.startsWith(CLIPropertyConsistencyChecks.INSUFFICIENT)),"Insufficient evidence not reported: "+rule);
            }
            System.out.println("CLIPropertyConsistencyTest passed "+assertions+" assertions; two independent sessions, no I/O files");
        } finally { GenericTrace.clearTraceFile(); GenericTrace.setEnabled(enabled); GenericTrace.setOutputEnabled(output); }
    }
    private static TraceCheckResult check(Check check,Collection<TraceRecord> records) {
        return CLIPropertyConsistencyChecks.check(check,records,CLIPropertyCheckerRegistry.fromDescriptors(TYPES));
    }
    private static Collection<TraceRecord> read(Path path) {
        var processor=new NativeTraceFilesProcessor(path); processor.processTraceFiles(); return processor.getTraceRecords().values();
    }
    private static void write(Path path,int integer) {
        GenericTrace.setTraceFile(path);
        Object caller=new Object(),dispatcher=new Object(),commandHandler=new Object();
        Map<String,Object> handlers=new LinkedHashMap<>(),store=new LinkedHashMap<>();
        List<String> names=List.of("current_models","stream","top_k","timeout");
        names.forEach(name->handlers.put(name,new Object()));
        var registry=new Registry(handlers); var commands=new Registry(Map.of("prop",commandHandler));
        int command=0;
        for(boolean setter:List.of(true,false)) for(String name:names) {
            command++;
            Object value=switch(name) { case "current_models"->List.of("model-"+integer); case "stream"->Boolean.TRUE; default->integer; };
            String type=switch(name) { case "current_models"->"selection"; case "stream"->"flag"; default->"quantity"; };
            List<String> alternatives=switch(name) { case "current_models"->List.of("model-"+integer,"another-model"); case "stream"->List.of("true","false"); default->List.of(); };
            if(setter) store.put(name,value);
            var result=new Result(type,alternatives);
            MinimalCLITrace.propertyHandled(caller,name,registry,handlers.get(name),result,store,value);
            if(setter) MinimalCLITrace.historyWithParametersUpdated(caller,command,List.of());
            MinimalCLITrace.commandHandled(dispatcher,command,"prop",commands,commandHandler,result);
        }
        GenericTrace.clearTraceFile();
    }
    private static void require(boolean condition,String message) { assertions++; if(!condition) throw new AssertionError(message); }
}
