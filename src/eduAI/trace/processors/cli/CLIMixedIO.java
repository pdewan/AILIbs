package eduAI.trace.processors.cli;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Observations remain separate from expectations reconstructed from echoed inputs. */
public record CLIMixedIO(List<Entry> entries) {
    public CLIMixedIO { entries = List.copyOf(entries); }

    public record Chunk(String model, String text) { }
    public record Output(boolean success, String conversation, String body,
            Map<String, String> attributes) {
        public Output { attributes = Map.copyOf(attributes); }
    }
    public record Entry(int number, CLIInteraction.Command input,
            List<CLIAttachment> attachments, Optional<Output> output, List<Chunk> chunks) {
        public Entry {
            attachments = List.copyOf(attachments);
            chunks = List.copyOf(chunks);
        }
    }
}
