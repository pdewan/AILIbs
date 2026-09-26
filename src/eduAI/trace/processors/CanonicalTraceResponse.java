package eduAI.trace.processors;

import java.util.List;
import java.util.Map;

public record CanonicalTraceResponse(
		List<GenericTracesFileProcessor.TraceMessage> messages,
		Map<String, String> metadata) {
	public CanonicalTraceResponse {
		messages = messages == null ? List.of() : List.copyOf(messages);
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
