package eduAI.trace.processors.mutations;

import java.util.*;
import java.util.function.Consumer;
import eduAI.trace.TraceLineParser;
import eduAI.trace.cli.TraceDataPaths;
import eduAI.trace.processors.cli.CLIPropertyConsistencyChecks.Check;
import static eduAI.trace.cli.MinimalCLITrace.*;

/** Each mutation declares the independent property rule that must reject it. */
public final class CLIPropertyConsistencyMutations {
    public static final String EXPECTATION_FILE="property-consistency-expectation.txt";
    public static final String PREFIX="property_consistency_";
    public record Mutation(Check check,List<String> lines) { }
    private CLIPropertyConsistencyMutations() { }
    public static Map<String,Mutation> create(List<String> correct) {
        Map<String,Mutation> out=new LinkedHashMap<>();
        List<Integer> properties=new ArrayList<>();
        Set<Integer> setters=new HashSet<>();
        int pending=-1;
        for(int i=0;i<correct.size();i++) {
            String event=TraceLineParser.parse(correct.get(i)).getEventName();
            if(event.equals(Event.PROPERTY_HANDLED.eventName())) { properties.add(i); pending=i; }
            if(event.equals(Event.HISTORY_WITH_PARAMETERS_UPDATED.eventName())) setters.add(pending);
            if(event.equals(Event.COMMAND_HANDLED.eventName())) pending=-1;
        }
        if(properties.isEmpty()) throw new IllegalArgumentException("No property mutation targets");
        int first=properties.get(0);
        Map<String,Integer> seen=new HashMap<>();
        int repeated=-1, different=-1, getter=-1;
        String firstName=fields(correct.get(first)).get(NAME);
        Set<String> setNames=new HashSet<>();
        for(int index:properties) {
            String name=fields(correct.get(index)).get(NAME);
            if(!name.equals(firstName) && different<0) different=index;
            if(seen.putIfAbsent(name,index)!=null && repeated<0) repeated=index;
            if(setters.contains(index)) setNames.add(name);
            else if(setNames.contains(name) && getter<0) getter=index;
        }
        if(repeated<0 || different<0 || getter<0) throw new IllegalArgumentException("Need repeated and distinct properties and a setter/getter pair");
        edit(out,correct,first,"missing_registration",Check.REGISTRATION,d->d.keySet().removeIf(k->k.startsWith(REGISTRY+".")));
        edit(out,correct,repeated,"changed_registry",Check.REGISTRATION,d->{d.put(REGISTRY,"student.OtherRegistry@abc");d.put(REGISTRY+TraceDataPaths.ID,"student.OtherRegistry@abc");});
        edit(out,correct,repeated,"changed_handler",Check.HANDLER_REUSE,d->d.put(HANDLER,"student.OtherHandler@abc"));
        String firstHandler=fields(correct.get(first)).get(HANDLER);
        edit(out,correct,different,"shared_handler",Check.DISTINCT_HANDLERS,d->d.put(HANDLER,firstHandler));
        edit(out,correct,first,"missing_result",Check.RESULT_TYPES,d->d.keySet().removeIf(k->k.startsWith(RESULT+".")));
        edit(out,correct,first,"handler_as_result",Check.RESULT_TYPES,d->d.put(RESULT+TraceDataPaths.ID,d.get(HANDLER)));
        edit(out,correct,repeated,"changed_type",Check.RESULT_TYPES,d->d.replaceAll((k,v)->k.startsWith(RESULT+".")&&k.endsWith(TraceDataPaths.VALUE)&&!k.contains(".items.")?"mutated-type":v));
        edit(out,correct,first,"missing_choices",Check.ALTERNATIVES,d->d.keySet().removeIf(k->k.startsWith(RESULT+".")&&k.contains(".items.")));
        edit(out,correct,getter,"wrong_getter",Check.GETTERS,d->d.replaceAll((k,v)->k.startsWith(VALUE+".")&&k.endsWith(TraceDataPaths.VALUE)?"wrong-getter":v));
        edit(out,correct,getter,"missing_value",Check.GETTERS,d->d.keySet().removeIf(k->k.startsWith(VALUE+".")));
        edit(out,correct,repeated,"changed_store",Check.STORE_IDENTITY,d->d.put(STORE+TraceDataPaths.ID,"student.OtherStore@abc"));
        edit(out,correct,repeated,"lost_store_values",Check.STORE,d->d.keySet().removeIf(k->k.startsWith(STORE+".")&&k.endsWith(TraceDataPaths.VALUE)));
        for(var check:Check.values()) {
            var empty=correct.stream().filter(line->{var event=TraceLineParser.parse(line).getEventName();
                return !event.equals(Event.PROPERTY_HANDLED.eventName())&&!event.equals(Event.HISTORY_WITH_PARAMETERS_UPDATED.eventName());}).toList();
            out.put(PREFIX+"insufficient_"+check.name().toLowerCase(Locale.ROOT),new Mutation(check,empty));
        }
        var noSetters=correct.stream().filter(line->!TraceLineParser.parse(line).getEventName().equals(Event.HISTORY_WITH_PARAMETERS_UPDATED.eventName())).toList();
        out.put(PREFIX+"no_preceding_setter",new Mutation(Check.GETTERS,noSetters));
        return Collections.unmodifiableMap(out);
    }
    private static Map<String,String> fields(String line) { return new LinkedHashMap<>(TraceLineParser.parse(line).getAuxiliaryData()); }
    private static void edit(Map<String,Mutation> out,List<String> correct,int index,String name,Check check,Consumer<Map<String,String>> change) {
        var lines=new ArrayList<>(correct); var data=fields(lines.get(index)); change.accept(data);
        lines.set(index,TraceFixtureFields.replaceFields(lines.get(index),data));
        if(lines.equals(correct)) throw new IllegalArgumentException("No-op property mutation: "+name);
        out.put(PREFIX+name,new Mutation(check,List.copyOf(lines)));
    }
}
