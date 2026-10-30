package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.ImageByteSummary;
import eduAI.trace.TraceLine;
import eduAI.trace.processors.cli.MinimalCLIContractChecks.Finding;
import eduAI.trace.processors.*;
import static eduAI.trace.cli.MinimalCLITrace.*;

/** Checks expander identities using traced value kinds; attachment contents are
 * checked separately against files. Both text and image evidence are required. */
public final class CLIExpanderSeparationChecks {
    private CLIExpanderSeparationChecks() { }
    public static TraceCheckResult check(Collection<TraceRecord> records) {
        return new TraceCheckResult(List.of(),List.of(),findings(records.stream().map(TraceRecord::getTraceLine).toList())
            .stream().map(f->f.check()+": "+f.detail()).toList());
    }
    public static List<Finding> findings(List<TraceLine> traces) {
        Map<CLIFileExpansionContentsChecks.Kind,String> expanders=new EnumMap<>(CLIFileExpansionContentsChecks.Kind.class);
        List<Finding> errors=new ArrayList<>();
        for(var trace:traces) if(Event.FILE_EXPANDED.eventName().equals(trace.getEventName())) {
            var data=trace.getAuxiliaryData();
            var values=MinimalCLIContractChecks.values(data,EXPANDED_DATA);
            if(values.isEmpty()) { errors.add(new Finding("expander-instances","Missing expanded value kind")); continue; }
            var kind=values.stream().anyMatch(value->ImageByteSummary.parseFirstNonempty(value)!=null)
                ? CLIFileExpansionContentsChecks.Kind.IMAGE : CLIFileExpansionContentsChecks.Kind.TEXT;
            String identity=data.get(EXPANDER);
            if(identity==null || !identity.contains("@") || identity.endsWith("@null")) {
                errors.add(new Finding("expander-reuse","Missing expander identity for "+kind)); continue;
            }
            String previous=expanders.putIfAbsent(kind,identity);
            if(previous!=null && !previous.equals(identity)) errors.add(new Finding("expander-reuse","Expander changed for "+kind));
        }
        if(expanders.size()!=CLIFileExpansionContentsChecks.Kind.values().length)
            errors.add(new Finding("expander-instances","Insufficient evidence: require both text and image expansions"));
        if(new HashSet<>(expanders.values()).size()!=expanders.size())
            errors.add(new Finding("expander-instances","Text and image use the same expander object"));
        return List.copyOf(errors);
    }
}
