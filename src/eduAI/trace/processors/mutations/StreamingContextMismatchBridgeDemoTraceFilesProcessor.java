package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;

public class StreamingContextMismatchBridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public StreamingContextMismatchBridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(Path.of(
				"broken_filtered",
				"streaming_context_mismatch"));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertCheckerMessage(
				aRun,
				GenericTracesFileProcessor
						.STREAMING_CHUNKS_MERGED_RESPONSE_IN_CONTEXT_WINDOW,
				"provider=Gemini",
				"mode=STREAMING",
				"match=false");
	}

	public static void main(String[] args) {
		new StreamingContextMismatchBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
