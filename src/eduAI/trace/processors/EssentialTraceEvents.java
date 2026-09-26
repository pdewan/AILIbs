package eduAI.trace.processors;

import java.util.Set;

public final class EssentialTraceEvents {
	private static final Set<String> ALL = Set.of(
			"server_handle_factory_fetched",
			"server_handle_fetched",
			"message_translated",
			"response_translated",
			"parameter_translated",
			"metadata_translated",
			"provider_streaming_chunk_received",
			"message_merger_factory_fetched",
			"streaming_chunk_accumulated",
			"request_sent",
			"streaming_chunks_merged",
			"partial_response_callback_invoked",
			"complete_response_callback_invoked");

	private EssentialTraceEvents() {
	}

	public static Set<String> all() {
		return ALL;
	}
}
