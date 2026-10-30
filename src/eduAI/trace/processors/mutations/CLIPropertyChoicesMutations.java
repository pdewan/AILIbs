package eduAI.trace.processors.mutations;
import java.util.*;
import java.util.function.Consumer;
import eduAI.trace.TraceLineParser;
import eduAI.trace.cli.TraceDataPaths;
import eduAI.trace.processors.cli.*;
import eduAI.trace.processors.cli.CLIPropertyChoicesChecks.Check;
import static eduAI.trace.cli.MinimalCLITrace.*;
/** Targeted required-choice defects, including absent evidence and configurable families. */
public final class CLIPropertyChoicesMutations {
    public static final String EXPECTATION_FILE="property-choices-expectation.txt";
    public record Mutation(Check check,List<String> lines) { }
    private CLIPropertyChoicesMutations() { }
    public static Map<String,Mutation> create(List<String> correct,CLIPropertyExpectations policy) {
        var rows=correct.stream().filter(CLIPropertyChoicesMutations::property).map(CLIPropertyChoicesMutations::fields).toList();
        var errors=new ArrayList<String>();
        String path=CLIPropertyResultFields.choicesFieldFromSelections(rows,errors);
        if(path==null) throw new IllegalArgumentException("Cannot mutate choices: "+errors);
        String booleanName=rows.stream().map(row->row.get(NAME)).filter(name->policy.descriptors().containsKey(name)
            && policy.descriptors().get(name).type()==CLIPropertyDescriptor.ValueType.BOOLEAN).findFirst().orElseThrow();
        var first=rows.stream().filter(row->booleanName.equals(row.get(NAME))).findFirst().orElseThrow();
        List<String> choices=CLIPropertyResultFields.choices(first,path);
        if(choices==null || choices.size()!=CLIPropertyChoicesChecks.BOOLEAN_ALTERNATIVE_COUNT)
            throw new IllegalArgumentException("Need correct boolean choices to mutate");
        var out=new LinkedHashMap<String,Mutation>();
        edit(out,correct,"boolean_empty",Check.BOOLEAN_CHOICES,booleanName,d->replaceChoices(d,path,List.of()));
        edit(out,correct,"boolean_single",Check.BOOLEAN_CHOICES,booleanName,d->replaceChoices(d,path,List.of(choices.get(0))));
        edit(out,correct,"boolean_duplicate",Check.BOOLEAN_CHOICES,booleanName,d->replaceChoices(d,path,List.of(choices.get(0),choices.get(0))));
        edit(out,correct,"boolean_extra",Check.BOOLEAN_CHOICES,booleanName,d->replaceChoices(d,path,List.of(choices.get(0),choices.get(1),"third-choice")));
        edit(out,correct,"boolean_wrong_value",Check.BOOLEAN_CHOICES,booleanName,d->d.replaceAll((k,v)->k.startsWith(VALUE+".")&&k.endsWith(TraceDataPaths.VALUE)?"outside-choices":v));
        edit(out,correct,"boolean_missing_field",Check.BOOLEAN_CHOICES,booleanName,d->d.keySet().removeIf(k->k.startsWith(path+".")));
        String modelName=eduAI.properties.AIPropertyNames.CURRENT_MODELS;
        for(var target:List.of(Map.entry(booleanName,Check.BOOLEAN_CHOICES),Map.entry(modelName,Check.MODEL_CHOICES))) {
            var lines=correct.stream().filter(line->!property(line)||!target.getKey().equals(fields(line).get(NAME))).toList();
            put(out,correct,"missing_"+target.getKey(),target.getValue(),lines);
        }
        int ordinal=0;
        for(var requirement:policy.requiredModels()) {
            var lines=new ArrayList<>(correct);
            for(int i=0;i<lines.size();i++) if(property(lines.get(i)) && modelName.equals(fields(lines.get(i)).get(NAME))) {
                var data=fields(lines.get(i));
                // Change choices and traced selections together: failure must not rely on the I/O log.
                data.replaceAll((k,v)->(k.startsWith(path+".")||k.startsWith(VALUE+"."))
                    && k.endsWith(TraceDataPaths.VALUE)&&requirement.matches(v)?"unconfigured-choice":v);
                lines.set(i,TraceFixtureFields.replaceFields(lines.get(i),data));
            }
            put(out,correct,"missing_required_model_"+ordinal++,Check.MODEL_CHOICES,lines);
        }
        return Collections.unmodifiableMap(out);
    }
    private static boolean property(String line) { return Event.PROPERTY_HANDLED.eventName().equals(TraceLineParser.parse(line).getEventName()); }
    private static Map<String,String> fields(String line) { return new LinkedHashMap<>(TraceLineParser.parse(line).getAuxiliaryData()); }
    private static void replaceChoices(Map<String,String> data,String path,List<String> choices) {
        data.keySet().removeIf(k->k.startsWith(path+".")&&!k.equals(path+TraceDataPaths.ID));
        for(int i=0;i<choices.size();i++) data.put(path+".items."+i+TraceDataPaths.VALUE,choices.get(i));
    }
    private static void edit(Map<String,Mutation> out,List<String> correct,String name,Check check,String property,Consumer<Map<String,String>> change) {
        var lines=new ArrayList<>(correct);
        for(int i=0;i<lines.size();i++) if(property(lines.get(i))&&property.equals(fields(lines.get(i)).get(NAME))) {
            var data=fields(lines.get(i)); change.accept(data); lines.set(i,TraceFixtureFields.replaceFields(lines.get(i),data)); break;
        }
        put(out,correct,name,check,lines);
    }
    private static void put(Map<String,Mutation> out,List<String> correct,String name,Check check,List<String> lines) {
        if(correct.equals(lines)) throw new IllegalArgumentException("No-op required-choices mutation: "+name);
        out.put("property_choices_"+name,new Mutation(check,List.copyOf(lines)));
    }
}
