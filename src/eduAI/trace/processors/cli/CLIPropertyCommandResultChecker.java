package eduAI.trace.processors.cli;

import java.util.*;

/** Compares each property name/value with the property-handler trace in this command interval. */
public final class CLIPropertyCommandResultChecker implements CLICommandResultChecker {
    @Override public List<String> check(CLICommandResultContext context) {
        var properties = context.commandTraces().stream().filter(record -> CLITraceEvidence.is(record,
                eduAI.trace.cli.MinimalCLITrace.Event.PROPERTY_HANDLED)).toList();
        List<String> errors = new ArrayList<>();
        if (properties.isEmpty()) errors.add("Insufficient evidence: no property-handler returns for this command");
        for (var property : properties) {
            var data = property.getTraceLine().getAuxiliaryData();
            String name = data.get(eduAI.trace.cli.MinimalCLITrace.NAME);
            errors.addAll(context.result().require("property name", name));
            var values = MinimalCLIContractChecks.values(data, eduAI.trace.cli.MinimalCLITrace.VALUE);
            if (values.isEmpty()) errors.add("Insufficient evidence: no property value for " + name);
            errors.addAll(context.result().require(name, values));
        }
        return errors;
    }
}
