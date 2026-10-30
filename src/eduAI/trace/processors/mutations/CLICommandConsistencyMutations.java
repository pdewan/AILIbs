package eduAI.trace.processors.mutations;
import java.util.*;
import java.util.function.Consumer;
import eduAI.trace.TraceLineParser;
import eduAI.trace.processors.cli.*;
import static eduAI.trace.cli.MinimalCLITrace.*;
/** Each defect names the independent command rule that must reject it. */
public final class CLICommandConsistencyMutations {
    public static final String EXPECTATION_FILE="command-consistency-expectation.txt";
    private static final String OUT_OF_ORDER_COMMAND_NUMBER="99999";
    public enum Check { REGISTRATION, REUSE, PROPERTY_RESULT, EXPECTED_INPUT }
    public record Mutation(Check check,List<String> lines) { }
    private CLICommandConsistencyMutations() { }
    public static Map<String,Mutation> create(List<String> correct) {
        var result=new LinkedHashMap<String,Mutation>();
        var commands=new ArrayList<Integer>();
        for(int i=0;i<correct.size();i++) if(is(correct.get(i),Event.COMMAND_HANDLED)) commands.add(i);
        int first=commands.get(0), second=commands.get(1);
        edit(result,correct,"registry_missing",Check.REGISTRATION,first,data->data.remove(REGISTRY));
        edit(result,correct,"registry_changed",Check.REGISTRATION,second,data->data.put(REGISTRY,"ChangedRegistry@123"));
        edit(result,correct,"dispatcher_changed",Check.REGISTRATION,second,data->data.put(CALLER,"ChangedDispatcher@123"));
        edit(result,correct,"handler_mapping",Check.REGISTRATION,first,data->{
            String handler=data.get(HANDLER);
            data.replaceAll((key,value)->key.startsWith(REGISTRY+".")&&value.equals(handler)?"OtherHandler@123":value);
        });
        String name=fields(correct.get(first)).get(NAME);
        int repeated=commands.stream().skip(1).filter(i->name.equals(fields(correct.get(i)).get(NAME))).findFirst().orElseThrow();
        edit(result,correct,"handler_changed",Check.REUSE,repeated,data->data.put(HANDLER,"OtherHandler@123"));
        put(result,correct,"no_repetition",Check.REUSE,List.of(correct.get(first)));
        put(result,correct,"no_returns",Check.REGISTRATION,correct.stream().filter(line->!is(line,Event.COMMAND_HANDLED)).toList());
        int property=commands.stream().filter(i->CLICommandNames.PROPERTY.equals(fields(correct.get(i)).get(NAME))).findFirst().orElseThrow();
        var missing=new ArrayList<>(correct);
        for(int i=property-1;i>=0&&!is(correct.get(i),Event.COMMAND_HANDLED);i--)
            if(is(correct.get(i),Event.PROPERTY_HANDLED)) missing.remove(i);
        put(result,correct,"property_evidence_missing",Check.PROPERTY_RESULT,missing);
        int propertyEvent=java.util.stream.IntStream.range(0,property).filter(i->is(correct.get(i),Event.PROPERTY_HANDLED)).findFirst().orElseThrow();
        edit(result,correct,"property_value_missing",Check.PROPERTY_RESULT,propertyEvent,data->data.keySet().removeIf(key->key.startsWith(VALUE+".")));
        edit(result,correct,"wrong_command_number",Check.EXPECTED_INPUT,second,data->data.put(COMMAND_NUMBER,OUT_OF_ORDER_COMMAND_NUMBER));
        var removed=new ArrayList<>(correct);removed.remove(first);
        put(result,correct,"missing_return",Check.EXPECTED_INPUT,removed);
        return Collections.unmodifiableMap(result);
    }
    private static boolean is(String line,Event event) { return event.eventName().equals(TraceLineParser.parse(line).getEventName()); }
    private static Map<String,String> fields(String line) { return new LinkedHashMap<>(TraceLineParser.parse(line).getAuxiliaryData()); }
    private static void edit(Map<String,Mutation> result,List<String> correct,String name,Check check,int index,Consumer<Map<String,String>> change) {
        var data=fields(correct.get(index));change.accept(data);var lines=new ArrayList<>(correct);
        lines.set(index,TraceFixtureFields.replaceFields(lines.get(index),data));put(result,correct,name,check,lines);
    }
    private static void put(Map<String,Mutation> result,List<String> correct,String name,Check check,List<String> lines) {
        if(correct.equals(lines)) throw new IllegalArgumentException("No-op command mutation: "+name);
        result.put("command_consistency_"+name,new Mutation(check,List.copyOf(lines)));
    }
}
