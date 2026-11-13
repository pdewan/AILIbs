package eduAI.trace.processors.cli;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import eduAI.properties.AIPropertyNames;
import static eduAI.trace.processors.cli.CLIOutputSyntax.*;

/** Direct stdout checks: no trace records or trace-derived expectations.
 * Reuses the registered history content rules with captured-response evidence,
 * then checks exact printed entry counts/order and property-value associations. */
public final class CLIInputOutputChecks {
    public enum Check { PROPERTIES, TRANSCRIPT, MESSAGES, SYSTEM_MESSAGES }
    private CLIInputOutputChecks() { }
    public static List<String> check(Check check,CLIMixedIO io,Path directory,CLIAttachmentKinds kinds) throws java.io.IOException {
        if(check==Check.PROPERTIES) return propertyValues(io);
        CLIMessageView view=check==Check.SYSTEM_MESSAGES?CLIMessageView.SYSTEM:CLIMessageView.ORDINARY;
        CLICommandResultChecker checker=check==Check.TRANSCRIPT?new CLITranscriptCommandResultChecker()
            :new CLIMessagesCommandResultChecker(io,directory,view,kinds);
        List<String> errors=new ArrayList<>();List<CLIMixedIO.Entry> history=new ArrayList<>();
        Map<Integer,List<String>> responses=new LinkedHashMap<>();
        Map<Integer,CLIResultEvidence> results=new LinkedHashMap<>();
        int lastSelected=io.entries().stream().filter(entry->check==Check.TRANSCRIPT
            ? CLICommandNames.TRANSCRIPT.equals(entry.input().commandName())
            : CLICommandNames.MESSAGES.equals(entry.input().commandName()) && view.includes(entry))
            .mapToInt(CLIMixedIO.Entry::number).max().orElse(0);
        int inspected=0;
        for(var entry:io.entries()) {
            // Later answers cannot be part of any selected history display.
            if(entry.number()>lastSelected) break;
            if(CLICommandNames.NEW.equals(entry.input().commandName())) { history.clear();responses.clear();results.clear();continue; }
            boolean selected=check==Check.TRANSCRIPT?CLICommandNames.TRANSCRIPT.equals(entry.input().commandName())
                :CLICommandNames.MESSAGES.equals(entry.input().commandName()) && view.includes(entry);
            if(selected) {
                inspected++;
                if(entry.output().isEmpty() || !entry.output().get().success()) errors.add("Missing successful output at "+entry.number());
                else {
                    var context=new CLICommandResultContext(entry,history,CLIResultEvidence.printed(entry.output().get().body()),
                        List.of(),responses,results,List.of());
                    for(var error:checker.check(context)) errors.add("Input "+entry.number()+": "+error);
                    try {
                        if(check==Check.TRANSCRIPT) transcriptEntries(context,errors);
                        else messageEntries(context,check==Check.SYSTEM_MESSAGES,errors);
                    } catch(IllegalArgumentException error) { errors.add("Input "+entry.number()+": "+error.getMessage()); }
                }
            }
            if(entry.output().isPresent() && entry.output().get().success()) {
                var body=entry.output().get().body();results.put(entry.input().inputOrdinal(),CLIResultEvidence.printed(body));
                if(entry.input().kind()==CLIInteraction.Kind.PROMPT) responses.put(entry.input().inputOrdinal(),answers(entry));
            }
            history.add(entry);
        }
        if(inspected==0) errors.add("Insufficient evidence: no "+check+" outputs");
        return errors;
    }
    /** The specification permits bare single-model answers; EduCopilot prefixes
     * each answer with its selected model. Neither form determines correctness. */
    private static List<String> answers(CLIMixedIO.Entry entry) {
        String body=entry.output().orElseThrow().body();
        var selected=entry.input().properties().get(AIPropertyNames.CURRENT_MODELS);
        if(!(selected instanceof List<?> models) || models.isEmpty())
            throw new IllegalArgumentException("Selected model evidence missing at "+entry.number());
        String names=String.join("|",models.stream().map(Object::toString).map(Pattern::quote).toList());
        var marker=Pattern.compile("(?m)^\\[(?:"+names+")] ?").matcher(body);
        List<String> result=new ArrayList<>();int from=-1;
        while(marker.find()) {
            if(from<0 && marker.start()!=0) throw new IllegalArgumentException("Text before model answer label");
            if(from>=0) result.add(body.substring(from,marker.start()));
            from=marker.end();
        }
        if(from>=0) result.add(body.substring(from));
        else if(models.size()==1) result.add(body);
        else throw new IllegalArgumentException("Multiple model answers need model labels");
        return result;
    }
    private static List<String> propertyValues(CLIMixedIO io) {
        List<String> errors=new ArrayList<>();Map<String,Object> set=new LinkedHashMap<>();int getters=0,setters=0;
        for(var entry:io.entries()) {
            if(CLICommandNames.NEW.equals(entry.input().commandName())) { set.clear();continue; }
            if(!selects(Kind.PROPERTY,entry)) continue;
            var input=entry.input();boolean setter=input.kind()==CLIInteraction.Kind.PROPERTY_SET;
            if(entry.output().isEmpty() || !entry.output().get().success()) { errors.add("Missing successful property output at "+entry.number());continue; }
            try {
                var values=properties(entry.output().get().body(),setter);
                if(values.size()!=1 || !values.get(0).name().equals(input.propertyName())) {
                    errors.add("Wrong property count/name at "+entry.number());continue;
                }
                var value=values.get(0);
                Object expected=setter?input.properties().get(input.propertyName()):set.get(input.propertyName());
                if(expected!=null && !sameValue(value.value(),expected)) errors.add("Wrong printed "+input.propertyName()+" at "+entry.number());
                if(setter) { setters++;set.put(input.propertyName(),input.properties().get(input.propertyName())); }
                else if(expected!=null) getters++;
                String expectedSource=setter?entry.output().get().conversation():CONVERSATION_SOURCE+entry.output().get().conversation();
                if(!value.source().equals(expectedSource)) errors.add("Wrong property source at "+entry.number());
            } catch(IllegalArgumentException error) { errors.add("Input "+entry.number()+": "+error.getMessage()); }
        }
        if(setters==0 || getters==0) errors.add("Insufficient evidence: require a successful setter followed by its getter");
        return errors;
    }
    static boolean sameValue(String printed,Object expected) {
        if(expected instanceof Number) {
            try { return new java.math.BigDecimal(printed.strip()).compareTo(new java.math.BigDecimal(expected.toString()))==0; }
            catch(NumberFormatException invalid) { return false; }
        }
        if(expected instanceof List<?> list) {
            String text=printed.strip();
            if(!text.startsWith("[") || !text.endsWith("]")) return false;
            String inside=text.substring(1,text.length()-1).strip();
            var actual=inside.isEmpty()?List.<String>of():Arrays.stream(inside.split(",",-1)).map(String::strip).toList();
            return actual.equals(list.stream().map(Object::toString).toList());
        }
        return printed.strip().equals(String.valueOf(expected).strip());
    }
    private static void transcriptEntries(CLICommandResultContext context,List<String> errors) {
        var actual=transcript(context.result().text());List<Saved> wanted=new ArrayList<>();
        Set<Integer> responseRows = new HashSet<>();
        for(var entry:context.history()) {
            wanted.add(new Saved(entry.number(),entry.input().rawInput().strip()));
            if(entry.output().isEmpty() || !entry.output().get().success()) continue;
            if(entry.input().kind()==CLIInteraction.Kind.PROPERTY_SET)
                wanted.add(new Saved(entry.number(),entry.output().get().body().strip()));
            if(entry.input().kind()==CLIInteraction.Kind.PROMPT) {
                var models=(List<?>)entry.input().properties().get(AIPropertyNames.CURRENT_MODELS);
                var answers=context.priorResponses().get(entry.input().inputOrdinal());
                if(models.size()!=answers.size()) { errors.add("Captured model-answer count differs at "+entry.number());continue; }
                for (int i = 0; i < answers.size(); i++) {
                    responseRows.add(wanted.size());
                    wanted.add(new Saved(entry.number(), "[" + models.get(i) + "] " + answers.get(i).stripTrailing()));
                }
            }
        }
        if(actual.size()!=wanted.size()) { errors.add("Transcript entry count differs at "+context.current().number());return; }
        for(int i=0;i<wanted.size();i++) {
            var a=actual.get(i);var w=wanted.get(i);String text=a.content();
            if (responseRows.contains(i) && text.startsWith(TRANSCRIPT_RESPONSE_PREFIX))
                text = text.substring(TRANSCRIPT_RESPONSE_PREFIX.length());
            if(a.number()!=w.number() || !text.strip().equals(w.content().strip()))
                errors.add("Transcript entry/order differs at "+context.current().number()+", entry "+i);
        }
    }
    private static void messageEntries(CLICommandResultContext context,boolean includeSystem,List<String> errors) {
        var actual=messages(context.result().text());List<Turn> wanted=new ArrayList<>();String previousSystem=null;
        for(var entry:context.history()) {
            if(entry.input().kind()!=CLIInteraction.Kind.PROMPT || !context.priorResponses().containsKey(entry.input().inputOrdinal())) continue;
            Object system=entry.input().properties().get(AIPropertyNames.SYSTEM_PROMPT);
            if(includeSystem && system instanceof String instruction && !instruction.isBlank() && !instruction.equals(previousSystem)) {
                wanted.add(new Turn(entry.number(),SYSTEM,instruction,""));previousSystem=instruction;
            }
            wanted.add(new Turn(entry.number(),USER,"",""));
            for(var response:context.priorResponses().get(entry.input().inputOrdinal())) wanted.add(new Turn(entry.number(),ASSISTANT,response,""));
        }
        if(actual.size()!=wanted.size()) { errors.add("Messages turn count differs at "+context.current().number());return; }
        for(int i=0;i<wanted.size();i++) {
            var a=actual.get(i);var w=wanted.get(i);
            if(!a.role().equals(w.role()) || a.number()!=w.number() && !(w.role().equals(SYSTEM) && a.number()==UNNUMBERED_SYSTEM))
                errors.add("Messages turn number/role/order differs at "+context.current().number()+", turn "+i);
            if(w.role().equals(SYSTEM) && !a.content().equals(w.content())) errors.add("Wrong system instruction at "+context.current().number());
            if(w.role().equals(ASSISTANT) && !CLIResultEvidence.printed("response = "+a.content()).containsHistoryText(w.content()))
                errors.add("Wrong answer for message turn "+w.number()+" at "+context.current().number());
            if(w.role().equals(USER)) {
                var input=context.history().stream().filter(e->e.number()==w.number()).findFirst().orElseThrow();
                for(String name:CLISemanticEvidence.NUMERIC_PROPERTIES) checkParameter(a.details(),name,input.input().properties().get(name),errors);
                checkParameter(a.details(),AIPropertyNames.STREAM,input.input().properties().get(AIPropertyNames.STREAM),errors);
            }
        }
    }
    private static void checkParameter(String details,String name,Object expected,List<String> errors) {
        if(expected==null) return; // Unset inputs supply no expected value.
        var matches=Pattern.compile("(?:^|[,{])\\s*"+Pattern.quote(name)+"\\s*=\\s*([^,}]+)").matcher(details);
        if(!matches.find() || !sameValue(matches.group(1),expected) || matches.find()) errors.add("Wrong or duplicated historical parameter "+name);
    }
}
