package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;

public class RequestSentContextMismatchBridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public RequestSentContextMismatchBridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(Path.of(
				"broken_filtered",
				"request_sent_context_mismatch"));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertCheckerMessage(
				aRun,
				GenericTracesFileProcessor.PROVIDER_GENERIC_TEXTUAL_CONTEXT_WINDOW_MATCH,
				"provider=Gemini",
				"mode=NON_STREAMING",
				"match=false");
	}

	public static void main(String[] args) {
		new RequestSentContextMismatchBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
