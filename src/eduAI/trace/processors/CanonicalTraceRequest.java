package eduAI.trace.processors;

import java.util.List;
import java.util.Map;

public record CanonicalTraceRequest(
		String model,
		Map<String, String> parameters,
		List<GenericTracesFileProcessor.TraceMessage> contextWindow) {
	public CanonicalTraceRequest {
		model = model == null ? "" : model;
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
		contextWindow = contextWindow == null
				? List.of()
				: List.copyOf(contextWindow);
	}
}
