package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;
import static eduAI.trace.processors.mutations.TraceMutationAssertions.*;

public class StreamingContextMismatchBridgeDemoTraceFilesProcessor {
	private final TraceFilesProcessor processor;

	public StreamingContextMismatchBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(Path.of(
				"broken_filtered",
				"streaming_context_mismatch"));
		processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify(processor.checkStreamingChunksReflectedInNextContextWindow());
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify(TraceCheckResult aRun) {
		assertCheckerMessage(aRun.level3Messages(),
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
