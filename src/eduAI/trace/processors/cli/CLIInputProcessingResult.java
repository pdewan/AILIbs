package eduAI.trace.processors.cli;

import java.util.List;
import java.util.Optional;

/** A failed prerequisite exposes diagnostics, never partially usable expectations. */
public record CLIInputProcessingResult(Optional<CLIInteraction> interaction,
        List<Diagnostic> diagnostics) {
    public record Diagnostic(int lineNumber, String message) { }

    public CLIInputProcessingResult {
        diagnostics = List.copyOf(diagnostics);
        if (interaction.isPresent() == !diagnostics.isEmpty()) {
            throw new IllegalArgumentException("provide either an interaction or diagnostics");
        }
    }

    public boolean succeeded() {
        return interaction.isPresent();
    }
}
