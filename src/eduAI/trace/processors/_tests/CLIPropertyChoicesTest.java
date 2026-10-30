package eduAI.trace.processors._tests;
import java.nio.file.*;
import java.util.*;
import eduAI.trace.GenericTrace;
import eduAI.trace.cli.MinimalCLITrace;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
import eduAI.trace.processors.cli.CLIPropertyChoicesChecks.Check;
import eduAI.trace.processors.cli.CLIModelChoiceRequirement.Match;
import eduAI.trace.processors.mutations.CLIPropertyChoicesMutations;
/**
 * Exercises required choices with real trace calls and no I/O file. A boolean
 * represented by enabled/disabled and configurable model families must pass.
 * Missing, duplicate or extra boolean alternatives and missing model families
 * must fail. Exact names must not accept a longer name or a prefix substitute.
 */
public final class CLIPropertyChoicesTest {
    private record Result(String category,List<String> choices) { }
    private record Registry(Map<String,Object> registrations) { }
    private static int assertions;
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply a test output directory");
        Path directory=Path.of(args[0]); Files.createDirectories(directory);
        boolean enabled=GenericTrace.isEnabled(),output=GenericTrace.isOutputEnabled();
        try {
            GenericTrace.setEnabled(true); GenericTrace.setOutputEnabled(false);
            var policy=policy(List.of(new CLIModelChoiceRequirement(Match.PREFIX,"gemini-"),new CLIModelChoiceRequirement(Match.PREFIX,"llava")));
            Path trace=directory.resolve("correct.txt");
            write(trace,"gemini-example"); var records=read(trace);
            for(var check:Check.values()) require(CLIPropertyChoicesChecks.check(check,records,policy).passed(),"Correct choices "+check);
            for(var entry:CLIPropertyChoicesMutations.create(Files.readAllLines(trace),policy).entrySet()) {
                Path path=directory.resolve(entry.getKey()+".txt"); Files.write(path,entry.getValue().lines());
                require(!CLIPropertyChoicesChecks.check(entry.getValue().check(),read(path),policy).passed(),"Survived "+entry.getKey());
            }
            require(CLIPropertyChoicesChecks.check(Check.MODEL_CHOICES,records,policy(List.of(new CLIModelChoiceRequirement(Match.EXACT,"gemini-example")))).passed(),"Exact name should match");
            for(var wrong:List.of(new CLIModelChoiceRequirement(Match.EXACT,"gemini"),new CLIModelChoiceRequirement(Match.EXACT,"gemini-example-new"),
                    new CLIModelChoiceRequirement(Match.PREFIX,"GEMINI-"),new CLIModelChoiceRequirement(Match.PREFIX,"example")))
                require(!CLIPropertyChoicesChecks.check(Check.MODEL_CHOICES,records,policy(List.of(wrong))).passed(),"Wrong requirement accepted: "+wrong);
            write(trace,"gemini-another-version");
            require(CLIPropertyChoicesChecks.check(Check.MODEL_CHOICES,read(trace),policy).passed(),"Configured prefix must allow a new version");
            for(var check:Check.values()) require(!CLIPropertyChoicesChecks.check(check,List.of(),policy).passed(),"Empty evidence accepted");
            System.out.println("CLIPropertyChoicesTest passed "+assertions+" assertions, including custom boolean spellings, exact/prefix policies, and targeted mutations");
        } finally { GenericTrace.clearTraceFile(); GenericTrace.setEnabled(enabled); GenericTrace.setOutputEnabled(output); }
    }
    private static CLIPropertyExpectations policy(List<CLIModelChoiceRequirement> required) {
        return new CLIPropertyExpectations(Map.of("stream",new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.BOOLEAN,List.of()),
            "current_models",new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.STRING_LIST,List.of())),List.of(),required);
    }
    private static Collection<TraceRecord> read(Path file) {
        var processor=new NativeTraceFilesProcessor(file); processor.processTraceFiles(); return processor.getTraceRecords().values();
    }
    private static void write(Path path,String selected) {
        GenericTrace.setTraceFile(path);
        Object caller=new Object(),dispatcher=new Object(),commandHandler=new Object();
        var registrations=new LinkedHashMap<String,Object>(); registrations.put("stream",new Object()); registrations.put("current_models",new Object());
        var registry=new Registry(registrations); var commands=new Registry(Map.of("prop",commandHandler));
        var store=new LinkedHashMap<String,Object>(); int command=0;
        for(String name:List.of("current_models","stream")) {
            command++;
            Object value=name.equals("stream")?"enabled":List.of(selected);
            var result=new Result(name.equals("stream")?"flag":"models",name.equals("stream")?List.of("enabled","disabled"):List.of(selected,"llava:example"));
            store.put(name,value);
            MinimalCLITrace.propertyHandled(caller,name,registry,registrations.get(name),result,store,value);
            MinimalCLITrace.historyWithParametersUpdated(caller,command,List.of());
            MinimalCLITrace.commandHandled(dispatcher,command,"prop",commands,commandHandler,result);
        }
        GenericTrace.clearTraceFile();
    }
    private static void require(boolean condition,String description) { assertions++; if(!condition) throw new AssertionError(description); }
}
