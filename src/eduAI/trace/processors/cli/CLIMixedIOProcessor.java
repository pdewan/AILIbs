package eduAI.trace.processors.cli;

import java.nio.file.Path;
import java.util.List;

public interface CLIMixedIOProcessor {
    CLIMixedIOProcessingResult process(Path file);
    CLIMixedIOProcessingResult processLines(List<String> lines);
}
