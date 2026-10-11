package eduAI.trace._tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eduAI.trace.GenericTrace;
import eduAI.trace.lib.LibTraceActions;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.GenericTracesFileProcessor;

/** Growing internal chunk lists must not cause quadratic serialized trace growth. */
public final class StreamingTraceSizeTest {
	private static final int SMALL_STREAM_CHUNKS = 64;
	private static final long MAX_EVENT_BYTES = 12_000;
	private static final double MAX_DOUBLED_STREAM_SIZE_RATIO = 2.5;
	private record TextPart(String text) { }
	private record Message(String role, List<TextPart> parts) { }
	private static final class GrowingObject {
		final List<Message> chunks = new ArrayList<>();
		int descriptions;
		@Override public String toString() { descriptions++; return chunks.toString(); }
	}
	private static Message message(String text) {
		return new Message("assistant", List.of(new TextPart(text)));
	}
	public static void main(String[] args) throws Exception {
		Path file = Files.createTempFile("stream-size-", ".txt");
		try {
			GenericTrace.setOutputEnabled(false);
			long small = emitAndCheck(file, SMALL_STREAM_CHUNKS);
			long large = emitAndCheck(file, SMALL_STREAM_CHUNKS * 2);
			if (large > small * MAX_DOUBLED_STREAM_SIZE_RATIO) throw new AssertionError("Superlinear trace growth: " + small + " -> " + large);
			System.out.println("StreamingTraceSizeTest: " + SMALL_STREAM_CHUNKS + " chunks=" + small
					+ " bytes; " + SMALL_STREAM_CHUNKS * 2 + " chunks=" + large + " bytes");
		} finally {
			GenericTrace.resetTraceDestinations();
			Files.deleteIfExists(file);
		}
	}
	private static long emitAndCheck(Path file, int count) throws Exception {
		GenericTrace.setTraceFile(file);
		LibTraceActions.traceServerHandleFactoryFetched(StreamingTraceSizeTest.class, "Ollama", new Object(), new Object());
		LibTraceActions.traceStreamingChunksMerged(StreamingTraceSizeTest.class, new Object(),
				new Message("system", List.of(new TextPart("Answer concisely."))));
		GrowingObject merger = new GrowingObject();
		GrowingObject callback = new GrowingObject();
		GrowingObject registry = new GrowingObject();
		GrowingObject factory = new GrowingObject();
		StringBuilder accumulated = new StringBuilder();
		for (int i = 0; i < count; i++) {
			String text = "chunk " + i + " with enough text to exercise compact summaries; ";
			Message chunk = message(text);
			merger.chunks.add(chunk);
			callback.chunks.add(chunk);
			registry.chunks.add(chunk);
			factory.chunks.add(chunk);
			accumulated.append(text);
			LibTraceActions.traceServerHandleFactoryFetched(StreamingTraceSizeTest.class, "Ollama", registry, factory);
			LibTraceActions.traceServerHandleFetched(StreamingTraceSizeTest.class, factory, new Object());
			LibTraceActions.traceMessageMergerFactoryFetched(StreamingTraceSizeTest.class, registry, factory);
			LibTraceActions.traceStreamingChunkSentToMerger(StreamingTraceSizeTest.class, merger, chunk);
			LibTraceActions.traceStreamingChunkAccumulated(StreamingTraceSizeTest.class, merger, chunk, message(accumulated.toString()));
			LibTraceActions.tracePartialResponseCallbackInvoked(StreamingTraceSizeTest.class, callback, chunk);
		}
		LibTraceActions.traceStreamingChunksMerged(StreamingTraceSizeTest.class, merger, message(accumulated.toString()));
		GenericTrace.clearTraceFile();
		if (merger.descriptions != 0 || callback.descriptions != 0
				|| registry.descriptions != 0 || factory.descriptions != 0) {
			throw new AssertionError("Stateful toString was invoked");
		}
		for (String line : Files.readAllLines(file)) {
			if (line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_EVENT_BYTES) {
				throw new AssertionError("Unbounded per-chunk event");
			}
		}
		GenericTracesFileProcessor processor = new GenericTracesFileProcessor();
		processor.processTraceFile("Ollama", StreamingMode.STREAMING, file);
		var merge = processor.checkStreamingChunksAccumulatedCorrectly("Ollama");
		if (!merge.passed()) throw new AssertionError(merge);
		if (!processor.classesForTag("StreamChunkMerger").contains(GrowingObject.class.getName())) {
			throw new AssertionError("Compact merger lost class-discovery evidence");
		}
		for (String event : List.of("streaming_chunk_accumulated", "streaming_chunks_merged",
				"partial_response_callback_invoked", "server_handle_factory_fetched", "server_handle_fetched",
				"message_merger_factory_fetched")) {
			var integrity = processor.checkTraceEventExists(event, Map.of("Ollama", List.of(StreamingMode.STREAMING)));
			if (!integrity.passed()) throw new AssertionError(integrity);
		}
		return Files.size(file);
	}
}
