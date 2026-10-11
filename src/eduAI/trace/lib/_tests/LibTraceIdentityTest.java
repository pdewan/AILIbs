package eduAI.trace.lib._tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import eduAI.trace.GenericTrace;
import eduAI.trace.TraceLineParser;
import eduAI.trace.lib.LibTraceActions;

/** Tag-bearing participants must never expose their object graphs. */
public class LibTraceIdentityTest {
	private static final String PRIVATE_STATE = "private participant state must not be traced";
	private static final class Participant {
		private final List<String> state = List.of(PRIVATE_STATE);
		private int descriptions;
		@Override public String toString() {
			descriptions++;
			return state.toString();
		}
	}
	private record Message(String role, List<TextPart> parts) { }
	private record TextPart(String text) { }

	public static void main(String[] args) throws Exception {
		Path trace = Files.createTempFile("identity-trace-", ".txt");
		Participant participant = new Participant();
		Message message = new Message("assistant", List.of(new TextPart("hello")));
		Class<?> source = LibTraceIdentityTest.class;
		Map<String, List<String>> expected = Map.ofEntries(
				Map.entry("server_handle_factory_fetched", List.of("serverHandleFactoryRegistry", "serverHandleFactory")),
				Map.entry("server_handle_factory_set_for_provider", List.of("serverHandleFactory")),
				Map.entry("server_handle_fetched", List.of("serverHandleFactory", "serverHandle")),
				Map.entry("server_handle_selected_for_provider", List.of("serverHandle")),
				Map.entry("stream_handler_registered", List.of("streamHandler")),
				Map.entry("message_merger_factory_set", List.of("messageMergerFactory")),
				Map.entry("message_merger_factory_fetched", List.of("streamChunkMergerFactoryRegistry", "streamChunkMergerFactory")),
				Map.entry("message_merger_created", List.of("messageMerger")),
				Map.entry("streaming_chunk_sent_to_merger", List.of("messageMerger")),
				Map.entry("streaming_chunk_accumulated", List.of("messageMerger")),
				Map.entry("streaming_chunks_merged", List.of("messageMerger")),
				Map.entry("streaming_callback_invoked", List.of("streamingCallback")),
				Map.entry("partial_response_callback_invoked", List.of("streamingCallback")),
				Map.entry("complete_response_callback_invoked", List.of("streamingCallback")),
				Map.entry("parameter_translated", List.of("parameterHandler")),
				Map.entry("metadata_translated", List.of("metadataHandler")));
		try {
			GenericTrace.setOutputEnabled(false);
			GenericTrace.setTraceFile(trace);
			LibTraceActions.traceServerHandleFactoryFetched(source, "Ollama", participant, participant);
			LibTraceActions.traceServerHandleFactorySetForProvider(source, participant);
			LibTraceActions.traceServerHandleFetched(source, participant, participant);
			LibTraceActions.traceServerHandleSelectedForProvider(source, participant);
			LibTraceActions.traceStreamHandlerRegistered(source, participant);
			LibTraceActions.traceMessageMergerFactorySet(source, participant);
			LibTraceActions.traceMessageMergerFactoryFetched(source, participant, participant);
			LibTraceActions.traceMessageMergerCreated(source, participant);
			LibTraceActions.traceStreamingChunkSentToMerger(source, participant, message);
			LibTraceActions.traceStreamingChunkAccumulated(source, participant, message, message);
			LibTraceActions.traceStreamingChunksMerged(source, participant, message);
			LibTraceActions.traceStreamingCallbackInvoked(source, participant, message);
			LibTraceActions.tracePartialResponseCallbackInvoked(source, participant, message);
			LibTraceActions.traceCompleteResponseCallbackInvoked(source, participant, message);
			LibTraceActions.traceParameterTranslated(source, "temperature", 0.4, participant, Map.of("temperature", 0.4));
			LibTraceActions.traceMetadataTranslated(source, "count", 42, participant, Map.of("count", 42));
			GenericTrace.clearTraceFile();
			var lines = Files.readAllLines(trace);
			if (lines.size() != expected.size()) throw new AssertionError("Missing events: " + lines.size());
			for (String line : lines) {
				var parsed = TraceLineParser.parse(line);
				if (parsed == null || !parsed.getSourceClassName().equals(source.getCanonicalName())) {
					throw new AssertionError("Lost source class: " + line);
				}
				for (String key : expected.get(parsed.getEventName())) {
					String value = parsed.getAuxiliaryData().get(key);
					if (value == null || !value.contains("identity=\"") || value.contains("fields=") || value.contains("toString=")) {
						throw new AssertionError("Participant expanded: " + parsed.getEventName() + "/" + key);
					}
				}
				if (line.contains(PRIVATE_STATE)) throw new AssertionError("Private state leaked");
				if (parsed.getEventName().equals("metadata_translated")
						&& !"42".equals(parsed.getAuxiliaryData().get("propertyValue"))) {
					throw new AssertionError("Lost explicit payload");
				}
			}
			if (participant.descriptions != 0) throw new AssertionError("Participant toString invoked");
			System.out.println("LibTraceIdentityTest: all " + expected.size() + " participant events use identity-only evidence");
		} finally {
			GenericTrace.resetTraceDestinations();
			Files.deleteIfExists(trace);
		}
	}
}
