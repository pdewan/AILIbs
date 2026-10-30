package eduAI.trace.processors.cli;

import java.util.*;
import java.util.regex.*;

/** Printed-body grammar, independent of trace objects and implementation types.
 * Accepts the lecture's system label and EduCopilot's system_prompt label.
 * Content between labels may span lines; types and metadata keys are not fixed. */
public final class CLIOutputSyntax {
    public static final int UNNUMBERED_SYSTEM=0;
    public enum Kind { PROPERTY, TRANSCRIPT, MESSAGES }
    public static final String USER="user", ASSISTANT="assistant", SYSTEM="system";
    public static final String TRANSCRIPT_HEADER="Transcript:", MESSAGES_HEADER="Messages:";
    public static final String CONVERSATION_SOURCE="conversation ";
    private static final String PROPERTY_NAME="[A-Za-z_][A-Za-z_0-9]*";
    private static final Pattern SETTER=Pattern.compile("Set ("+PROPERTY_NAME+") to (.+) for "+CONVERSATION_SOURCE+"(\\S+)",Pattern.DOTALL);
    private static final Pattern GETTER=Pattern.compile("("+PROPERTY_NAME+") = (.+) \\(([^()\\r\\n]+)\\) \\[source: ([^]\\r\\n]+)](?: alternatives: (.+))?");
    private static final Pattern TURN=Pattern.compile("(?m)^\\[([^]\\r\\n]+)] ");
    private static final Pattern MESSAGE_LABEL=Pattern.compile("(?:(\\d+)\\.)?(user|assistant|system)");
    private CLIOutputSyntax() { }
    public record Property(String name,String value,String type,String source,String alternatives) { }
    public record Turn(int number,String role,String content,String details) { }
    public record Saved(int number,String content) { }
    public static List<Property> properties(String body,boolean setter) {
        if(setter) {
            var match=SETTER.matcher(body.strip());
            if(!match.matches()) throw new IllegalArgumentException("Expected Set <property> to <value> for conversation <id>");
            return List.of(new Property(match.group(1),match.group(2),"",match.group(3),""));
        }
        List<Property> result=new ArrayList<>();
        for(String line:body.strip().split("\\R")) {
            var match=GETTER.matcher(line);
            if(!match.matches()) throw new IllegalArgumentException("Expected <property> = <value> (<type>) [source: <source>] with optional alternatives");
            result.add(new Property(match.group(1),match.group(2),match.group(3),match.group(4),
                match.group(5)==null?"":match.group(5)));
        }
        return List.copyOf(result);
    }
    private record Label(String label,String body) { }
    private static List<Label> labels(String body,String heading) {
        String text=body.strip();
        if(!text.equals(heading) && !text.startsWith(heading+"\n"))
            throw new IllegalArgumentException("Expected "+heading+" heading");
        text=text.substring(heading.length()).strip();
        if(text.isEmpty()) return List.of();
        var matcher=TURN.matcher(text);List<Label> result=new ArrayList<>();
        int start=-1;String label=null;
        while(matcher.find()) {
            if(start<0 && matcher.start()!=0) throw new IllegalArgumentException("Unlabelled history entry");
            if(start>=0) result.add(new Label(label,text.substring(start,matcher.start()).stripTrailing()));
            label=matcher.group(1);start=matcher.end();
        }
        if(start<0) throw new IllegalArgumentException("Missing history entry labels");
        result.add(new Label(label,text.substring(start).stripTrailing()));
        return result;
    }
    public static List<Saved> transcript(String body) {
        List<Saved> result=new ArrayList<>();
        for(var row:labels(body,TRANSCRIPT_HEADER)) {
            if(!row.label.matches("\\d+") || row.body.isBlank()) throw new IllegalArgumentException("Expected [<number>] <input or saved output>");
            result.add(new Saved(Integer.parseInt(row.label),row.body));
        }
        return List.copyOf(result);
    }
    public static List<Turn> messages(String body) {
        List<Turn> result=new ArrayList<>();
        for(var row:labels(body,MESSAGES_HEADER)) {
            var match=MESSAGE_LABEL.matcher(row.label);
            if(!match.matches()) throw new IllegalArgumentException("Expected [<number>.user|assistant|system] or [system]");
            String role=match.group(2);boolean system=role.equals(SYSTEM);
            if(match.group(1)==null && !system) throw new IllegalArgumentException("Only a system entry may omit its number");
            int number=match.group(1)==null?UNNUMBERED_SYSTEM:Integer.parseInt(match.group(1));
            String prefix=system?(row.body.startsWith("system_prompt = ")?"system_prompt = ":"system = ")
                :role.equals(USER)?"prompt = ":"response = ";
            String delimiter=role.equals(USER)?", parameters = ":", metadata = ";
            if(!row.body.startsWith(prefix)) throw new IllegalArgumentException("Missing "+prefix+"after "+row.label);
            if(system) result.add(new Turn(number,role,row.body.substring(prefix.length()),""));
            else {
                int end=row.body.lastIndexOf(delimiter);
                if(end<prefix.length()) throw new IllegalArgumentException("Missing "+delimiter+"after "+row.label);
                String details=row.body.substring(end+delimiter.length());
                balancedDetails(details);
                result.add(new Turn(number,role,row.body.substring(prefix.length(),end),details));
            }
        }
        return List.copyOf(result);
    }
    private static void balancedDetails(String value) {
        if(value.isBlank()) throw new IllegalArgumentException("Empty parameters or metadata");
        int depth=0;
        for(char c:value.toCharArray()) {
            if(c=='{') depth++;
            if(c=='}' && --depth<0) throw new IllegalArgumentException("Unbalanced parameters or metadata");
        }
        if(depth!=0) throw new IllegalArgumentException("Unbalanced parameters or metadata");
    }
    public static boolean selects(Kind kind,CLIMixedIO.Entry entry) {
        return switch(kind) {
            case PROPERTY -> entry.input().kind()==CLIInteraction.Kind.PROPERTY_GET || entry.input().kind()==CLIInteraction.Kind.PROPERTY_SET;
            case TRANSCRIPT -> CLICommandNames.TRANSCRIPT.equals(entry.input().commandName());
            case MESSAGES -> CLICommandNames.MESSAGES.equals(entry.input().commandName());
        };
    }
    public static List<String> check(Kind kind,CLIMixedIO io) {
        var errors=new ArrayList<String>();int inspected=0;
        for(var entry:io.entries()) if(selects(kind,entry)) {
            if(entry.output().isEmpty()) { errors.add("Missing output at input "+entry.number()); continue; }
            if(!entry.output().get().success()) continue; // Error bodies have no command-specific grammar.
            inspected++;
            try {
                String body=entry.output().get().body();
                switch(kind) {
                    case PROPERTY -> properties(body,entry.input().kind()==CLIInteraction.Kind.PROPERTY_SET);
                    case TRANSCRIPT -> transcript(body);
                    case MESSAGES -> messages(body);
                }
            } catch(IllegalArgumentException error) { errors.add("Input "+entry.number()+": "+error.getMessage()); }
        }
        if(inspected==0) errors.add("Insufficient evidence: no successful "+kind+" output");
        return errors;
    }
}
