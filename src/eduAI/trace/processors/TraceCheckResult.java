package eduAI.trace.processors;

import java.util.List;

public record TraceCheckResult(
		List<TraceFilesProcessorError> errors,
		List<String> level3Messages,
		List<String> level3ErrorMessages) {
	public TraceCheckResult {
		errors = errors == null ? List.of() : List.copyOf(errors);
		level3Messages = level3Messages == null
				? List.of()
				: List.copyOf(level3Messages);
		level3ErrorMessages = level3ErrorMessages == null
				? List.of()
				: List.copyOf(level3ErrorMessages);
	}

	public boolean passed() {
		return errors.isEmpty() && level3ErrorMessages.isEmpty();
	}
}
