package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;

public class StreamChunkMergerTagMissingBridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public StreamChunkMergerTagMissingBridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(Path.of(
				"broken_filtered",
				"stream_chunk_merger_tag_missing"));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertMissingRegistryTag(
				aRun,
				"StreamChunkMergerFactoryRegistry");
		delegate.assertMissingRegistryTag(aRun, "StreamChunkMergerFactory");
		delegate.assertRegistryTag(aRun, "StreamChunkMerger");
	}

	public static void main(String[] args) {
		new StreamChunkMergerTagMissingBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
