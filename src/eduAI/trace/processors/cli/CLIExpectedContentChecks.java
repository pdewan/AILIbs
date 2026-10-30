package eduAI.trace.processors.cli;
import java.util.ArrayList;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.*;

/** Checks a chronological session against expectations reconstructed from its I/O. */
public final class CLIExpectedContentChecks implements ExpectedContentChecks {
    @FunctionalInterface private interface Preparation { CLITraceExpectations prepare(CLITraceExpectations.Check check) throws java.io.IOException; }
    private final Preparation preparation;
    public CLIExpectedContentChecks(CLITraceExpectations expectations) {
        java.util.Objects.requireNonNull(expectations);
        this.preparation=check->expectations;
    }
    private CLIExpectedContentChecks(Preparation preparation) { this.preparation=preparation; }
    /** Delay file reads until the consuming check requests its own content kind. */
    public static CLIExpectedContentChecks forSession(CLIMixedIO io,java.nio.file.Path directory,CLIAttachmentKinds kinds) {
        return new CLIExpectedContentChecks(check->check==CLITraceExpectations.Check.SETTINGS
            ? CLITraceExpectations.settingsFrom(io) : CLITraceExpectations.contentFrom(io,directory,check,kinds));
    }
    private TraceCheckResult compare(java.util.Collection<TraceRecord> records,String provider,CLITraceExpectations.Check check) {
        try { return preparation.prepare(check).check(records,provider,check); }
        catch(java.io.IOException | IllegalArgumentException error) {
            return new TraceCheckResult(java.util.List.of(),java.util.List.of(),java.util.List.of("Cannot prepare "+check+": "+error.getMessage()));
        }
    }
    public TraceCheckResult check(GenericTracesFileProcessor processor, String provider, StreamingMode mode, Kind kind) {
        if (mode != null) throw new IllegalArgumentException("CLI expectations describe the whole session, not a mode group");
        var records = processor.getTraceRecords().values();
        if (kind == Kind.ALL) {
            var prompts = compare(records, provider, CLITraceExpectations.Check.PROMPTS);
            var images = compare(records, provider, CLITraceExpectations.Check.IMAGES);
            var messages = new ArrayList<>(prompts.level3Messages()); messages.addAll(images.level3Messages());
            var errors = new ArrayList<>(prompts.level3ErrorMessages()); errors.addAll(images.level3ErrorMessages());
            return new TraceCheckResult(java.util.List.of(), messages, errors);
        }
        return compare(records, provider, switch (kind) {
            case PROMPTS -> CLITraceExpectations.Check.PROMPTS;
            case IMAGES -> CLITraceExpectations.Check.IMAGES;
            case SETTINGS -> CLITraceExpectations.Check.SETTINGS;
            case ALL -> throw new IllegalStateException("ALL must combine prompt and image checks");
        });
    }
}
