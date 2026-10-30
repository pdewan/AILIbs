package eduAI.trace.processors;

import java.nio.file.Path;
import static eduAI.trace.processors.mutations.TraceMutationAssertions.*;

public class MissingRequestSentBridgeDemoTraceFilesProcessor {
	private final TraceFilesProcessor processor;

	public MissingRequestSentBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(Path.of(
				"broken_filtered",
				"missing_request_sent"));
		processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify(processor.checkRequestsTraced());
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify(TraceCheckResult aRun) {
		assertContains(
				aRun.errors(),
				TraceFilesProcessorError.MISSING_REQUEST_SENT);
	}

	public static void main(String[] args) {
		new MissingRequestSentBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
