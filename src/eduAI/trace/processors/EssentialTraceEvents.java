package eduAI.trace.processors;

import java.util.Set;

public final class EssentialTraceEvents {
	private static final Set<String> ALL = Set.of(
			eduAI.trace.cli.CLITraceActions.Event.COMMAND_HANDLER_FETCHED.eventName(),
			eduAI.trace.cli.CLITraceActions.Event.COMMAND_HANDLER_RESULTS.eventName(),
			eduAI.trace.cli.CLITraceActions.Event.PROPERTY_HANDLER_FETCHED.eventName(),
			eduAI.trace.cli.CLITraceActions.Event.PROPERTY_HANDLER_RESULTS.eventName(),
			eduAI.trace.cli.CLITraceActions.Event.CLI_PROPERTY_SET.eventName(),
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
		java.util.Set<String> result = new java.util.HashSet<>(ALL);
        for (var event : eduAI.trace.cli.CLIArchitectureTrace.Event.values()) result.add(event.eventName());
        for (var event : eduAI.trace.cli.MinimalCLITrace.Event.values()) result.add(event.eventName());
        return java.util.Set.copyOf(result);
	}
}
