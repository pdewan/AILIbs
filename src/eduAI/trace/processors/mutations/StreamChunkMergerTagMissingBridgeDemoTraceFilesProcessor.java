package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;

public class StreamChunkMergerTagMissingBridgeDemoTraceFilesProcessor {
	private final TraceFilesProcessor processor;

	public StreamChunkMergerTagMissingBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(Path.of(
				"broken_filtered",
				"stream_chunk_merger_tag_missing"));
		processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify();
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify() {
		for (String tag : java.util.List.of("StreamChunkMergerFactoryRegistry", "StreamChunkMergerFactory")) {
			if (!processor.classesForTag(tag).isEmpty()) {
				throw new AssertionError("Unexpected class registry tag " + tag);
			}
		}
		if (processor.classesForTag("StreamChunkMerger").isEmpty()) {
			throw new AssertionError("Expected class registry tag StreamChunkMerger");
		}
	}

	public static void main(String[] args) {
		new StreamChunkMergerTagMissingBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
