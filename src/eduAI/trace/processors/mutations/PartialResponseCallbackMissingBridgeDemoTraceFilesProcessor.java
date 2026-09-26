package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;

public class PartialResponseCallbackMissingBridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public PartialResponseCallbackMissingBridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(Path.of(
				"broken_filtered",
				"partial_response_callback_missing"));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertContains(
				aRun.errors(),
				TraceFilesProcessorError
						.MISSING_PARTIAL_RESPONSE_CALLBACK_INVOKED);
		delegate.assertCheckerMessage(
				aRun,
				GenericTracesFileProcessor.PARTIAL_RESPONSE_CALLBACK_INVOKED,
				"match=false");
	}

	public static void main(String[] args) {
		new PartialResponseCallbackMissingBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
