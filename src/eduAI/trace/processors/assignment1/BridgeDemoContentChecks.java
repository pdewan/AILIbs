package eduAI.trace.processors.assignment1;
import eduAI.trace.processors.*;

/** A1's fixed demo expectations and accepted historical summary wording. */
public final class BridgeDemoContentChecks extends FixedInputContentChecks {
    public BridgeDemoContentChecks(GenericTracesFileProcessor processor) {
        super(processor);
        processor.registerInputTextEquivalence(BridgeDemoTraceInputs.SUMMARY_PROMPT, BridgeDemoTraceInputs.SUMMARY_PROMPT);
        processor.registerInputTextEquivalence(BridgeDemoTraceInputs.SUMMARY_PROMPT, BridgeDemoTraceInputs.LEGACY_SUMMARY_PROMPT);
        processor.registerInputTextEquivalence(BridgeDemoTraceInputs.SUMMARY_PROMPT, BridgeDemoTraceInputs.LEGACY_SUMMARY_PROMPT.strip());
    }
}
