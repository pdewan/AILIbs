package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.*;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
import eduAI.trace.processors.cli.CLIPropertyDescriptor.ValueType;

/** Common result objects with student-chosen names; mutations use real trace calls. */
public final class CLIPropertyObjectChecksTest {
    private record Information(String category, Object options) { }
    private record Ambiguous(String category, String secondCategory, Object options) { }
    private record AmbiguousChoices(String category, Object options, Object otherOptions) { }
    private static final List<String> MODELS = List.of("llava", "gemini-model");
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "build/property-result-checks" : args[0]);
        Files.createDirectories(directory);
        var expected = Map.of(
            "timeout", new CLIPropertyDescriptor(ValueType.INTEGER,List.of()),
            "top_k", new CLIPropertyDescriptor(ValueType.INTEGER,List.of()),
            "stream", new CLIPropertyDescriptor(ValueType.BOOLEAN,List.of("true","false")),
            "current_models", new CLIPropertyDescriptor(ValueType.STRING_LIST,MODELS));
        String[] mutations = {"correct","wrong-type","missing-choice","missing-result",
            "handler-as-result","unevaluated-choices","ambiguous-type","missing-options",
            "ambiguous-choices","missing-type"};
        boolean output = GenericTrace.isOutputEnabled();
        try {
            GenericTrace.setOutputEnabled(false);
            for(int mutation=0; mutation<mutations.length; mutation++) {
                Path file=directory.resolve(mutations[mutation]+".txt");
                GenericTrace.setTraceFile(file);
                Object registry=new Object();
                eduAI.trace.cli.CLIArchitectureTrace.models(CLIPropertyObjectChecksTest.class,registry,MODELS);
                for(String name:List.of("timeout","top_k","stream","current_models")) {
                    Object handler=new Object();
                    CLITraceActions.tracePropertyHandlerFetched(CLIPropertyObjectChecksTest.class,name,registry,handler);
                    String type=name.equals("stream") ? "flag" : name.equals("current_models") ? "models" : "count";
                    Object choices=name.equals("stream") ? List.of("true","false") : name.equals("current_models") ? MODELS : List.of();
                    if(mutation==1 && name.equals("top_k")) type="wrong";
                    if(mutation==2 && name.equals("current_models")) choices=List.of("llava");
                    if(mutation==5 && name.equals("current_models")) choices=(java.util.function.Supplier<List<String>>)()->MODELS;
                    if(mutation==7 && name.equals("timeout")) choices=null;
                    if(mutation==9 && name.equals("timeout")) type=null;
                    Object result=new Information(type,choices);
                    if(mutation==3) result=null;
                    if(mutation==4) result=handler;
                    if(mutation==6) result=new Ambiguous(type,type,choices);
                    if(mutation==8) result=new AmbiguousChoices(type,choices,new ArrayList<>((List<?>)choices));
                    CLITraceActions.tracePropertyHandlerResults(CLIPropertyObjectChecksTest.class,name,handler,result);
                }
                GenericTrace.clearTraceFile();
                var processor=new NativeTraceFilesProcessor(file);
                processor.processTraceFiles();
                var result=CLIPropertyResultChecks.check(processor.getTraceRecords().values(),
                    CLIPropertyCheckerRegistry.fromDescriptors(expected),expected.keySet());
                if(result.passed()!=(mutation==0)) throw new AssertionError(mutations[mutation]+": "+result);
                var models = CLIArchitectureChecks.check(CLIArchitectureChecks.Check.MODELS,
                    processor.getTraceRecords().values(),null,directory);
                boolean modelFailure = Set.of(2,3,4,5,8).contains(mutation);
                if(models.passed()==modelFailure) throw new AssertionError("model choices "+mutations[mutation]+": "+models);
            }
            Files.write(directory.resolve("mutations.tsv"),
                Arrays.stream(mutations).skip(1).map(name->name+"\tproperty-result\tPASS").toList());
            System.out.println("CLIPropertyObjectChecksTest: correct result objects passed; 9 mutations rejected");
        } finally {
            GenericTrace.clearTraceFile();
            GenericTrace.setOutputEnabled(output);
        }
    }
}
