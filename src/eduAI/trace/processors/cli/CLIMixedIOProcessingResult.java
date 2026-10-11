package eduAI.trace.processors.cli;

import java.util.List;
import java.util.Optional;

import eduAI.trace.processors.cli.CLIInputProcessingResult.Diagnostic;

/** No partially reconstructed expectations escape a failed prerequisite. */
public record CLIMixedIOProcessingResult(Optional<CLIMixedIO> interaction,
        List<Diagnostic> diagnostics) {
    public CLIMixedIOProcessingResult {
        diagnostics = List.copyOf(diagnostics);
        if (interaction.isPresent() == !diagnostics.isEmpty()) {
            throw new IllegalArgumentException("provide either an interaction or diagnostics");
        }
    }
    public boolean succeeded() { return interaction.isPresent(); }
}
