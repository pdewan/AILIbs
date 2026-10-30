package eduAI.trace.processors.cli;
import java.util.Set;
/** Explicit classification of full-contract diagnostics; unknown rules are errors. */
public final class CLIContractDomains {
    public enum Domain { STRUCTURE, SEMANTICS, EVIDENCE }
    private static final Set<String> STRUCTURAL_RULES = Set.of(MinimalCLIContractChecks.STRUCTURE_EVIDENCE,"participant-identity","registry-current","registry-association","registry-reuse","handler-reuse","property-instance","store-identity","dispatcher-identity","notification-source","history-source","expander-reuse","expander-instances",CLIObserverSeparationChecks.COMMAND_SEPARATION,CLIObserverSeparationChecks.STREAM_SEPARATION,CLIObserverSeparationChecks.OBSERVER_INSTANCES,CLIObserverSeparationChecks.SEPARATION_EVIDENCE,"property-result","property-result-handler");
    private static final Set<String> SEMANTIC_RULES = Set.of("property-count","property-order","property-value","store-values","command-order","notification-count","notification-order","notification-result","result-delivery","command-io","display-count","history-count","history-order","snapshot-values","stream-sequence","stream-io","attachment-count","attachment-content","command-count","type-field","choices-field",MinimalCLIContractChecks.STREAM_NOTIFICATION);
    private static final Set<String> EVIDENCE_RULES = Set.of("readable-data", "expectations");
    private CLIContractDomains() { }
    public static Domain domain(String rule) {
        if (STRUCTURAL_RULES.contains(rule)) return Domain.STRUCTURE;
        if (SEMANTIC_RULES.contains(rule)) return Domain.SEMANTICS;
        if (EVIDENCE_RULES.contains(rule)) return Domain.EVIDENCE;
        throw new IllegalArgumentException("Unclassified CLI contract rule: " + rule);
    }
}
