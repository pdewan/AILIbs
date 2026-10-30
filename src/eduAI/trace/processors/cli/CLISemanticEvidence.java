package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.properties.AIPropertyNames;

/** Minimum exercised behavior, independent of the assignment's prescribed script. */
public final class CLISemanticEvidence {
    public static final int MINIMUM_HISTORY_EXCHANGES=2;
    public static final List<String> NUMERIC_PROPERTIES=List.of(AIPropertyNames.TEMPERATURE,
        AIPropertyNames.TIMEOUT,AIPropertyNames.NUM_CTX,AIPropertyNames.TOP_K);
    private CLISemanticEvidence() { }
    public static List<String> historyExchanges(CLICommandResultContext context,boolean requireSystem) {
        long completed=context.history().stream().filter(e->e.input().kind()==CLIInteraction.Kind.PROMPT)
            .filter(e->{
                var responses=context.priorResponses().get(e.input().inputOrdinal());
                return responses!=null && responses.stream().anyMatch(value->value!=null && !value.isBlank());
            }).filter(e->!requireSystem || e.input().properties().get(AIPropertyNames.SYSTEM_PROMPT) instanceof String system && !system.isBlank())
            .count();
        return completed>=MINIMUM_HISTORY_EXCHANGES ? List.of() : List.of("Insufficient evidence: need "
            +MINIMUM_HISTORY_EXCHANGES+" completed earlier exchanges"+(requireSystem?" using system instructions":""));
    }
    public static boolean numericChange(List<Map<String,Object>> snapshots) {
        for(String name:NUMERIC_PROPERTIES) {
            var values=new HashSet<Double>();
            for(var snapshot:snapshots) if(snapshot.get(name) instanceof Number value) values.add(value.doubleValue());
            if(values.size()>1) return true;
        }
        return false;
    }
    public static List<String> messages(CLIMixedIO io) {
        return messages(io,CLIMessageView.ALL);
    }
    public static List<String> messages(CLIMixedIO io,CLIMessageView view) {
        var errors=new ArrayList<String>();
        boolean ordinary=false,system=false;
        for(var entry:io.entries()) {
            if(!CLICommandNames.MESSAGES.equals(entry.input().commandName())) continue;
            if(!view.includes(entry)) continue;
            String option=entry.input().argument().strip();
            ordinary |= option.isEmpty();
            system |= option.equals(CLICommandNames.SYSTEM_OPTION);
        }
        if(view!=CLIMessageView.SYSTEM && !ordinary) errors.add("Insufficient evidence: no ordinary messages view");
        if(view!=CLIMessageView.ORDINARY && !system) errors.add("Insufficient evidence: no -system messages view");
        return errors;
    }
}
