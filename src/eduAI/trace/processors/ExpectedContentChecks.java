package eduAI.trace.processors;
import eduAI.trace.lib.StreamingMode;

/** Explicitly selected expected-content policy; the processor does not select assignments. */
public interface ExpectedContentChecks {
    enum Kind { PROMPTS, IMAGES, SETTINGS, ALL }
    TraceCheckResult check(GenericTracesFileProcessor processor, String provider, StreamingMode mode, Kind kind);
}
