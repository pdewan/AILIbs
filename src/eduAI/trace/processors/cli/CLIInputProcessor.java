package eduAI.trace.processors.cli;

import java.nio.file.Path;
import java.util.List;

public interface CLIInputProcessor {
    CLIInputProcessingResult process(Path inputFile);
    CLIInputProcessingResult processLines(List<String> lines);
}
