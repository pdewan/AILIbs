package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;

public class CompleteResponseCallbackMissingBridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public CompleteResponseCallbackMissingBridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(Path.of(
				"broken_filtered",
				"complete_response_callback_missing"));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertContains(
				aRun.errors(),
				TraceFilesProcessorError
						.MISSING_COMPLETE_RESPONSE_CALLBACK_INVOKED);
		delegate.assertCheckerMessage(
				aRun,
				GenericTracesFileProcessor.COMPLETE_RESPONSE_CALLBACK_INVOKED,
				"match=false");
	}

	public static void main(String[] args) {
		new CompleteResponseCallbackMissingBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
