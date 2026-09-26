package eduAI.trace.processors;

import java.nio.file.Path;

public class MissingRequestSentBridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public MissingRequestSentBridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(Path.of(
				"broken_filtered",
				"missing_request_sent"));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertContains(
				aRun.errors(),
				TraceFilesProcessorError.MISSING_REQUEST_SENT);
	}

	public static void main(String[] args) {
		new MissingRequestSentBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
