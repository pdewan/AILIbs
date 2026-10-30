package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;
import static eduAI.trace.processors.mutations.TraceMutationAssertions.*;

public class PartialResponseCallbackMissingBridgeDemoTraceFilesProcessor {
	private final TraceFilesProcessor processor;

	public PartialResponseCallbackMissingBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(Path.of(
				"broken_filtered",
				"partial_response_callback_missing"));
		processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify(processor.checkPartialResponseCallbacksInvoked());
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify(TraceCheckResult aRun) {
		assertContains(
				aRun.errors(),
				TraceFilesProcessorError
						.MISSING_PARTIAL_RESPONSE_CALLBACK_INVOKED);
		assertCheckerMessage(aRun.level3Messages(),
				GenericTracesFileProcessor.PARTIAL_RESPONSE_CALLBACK_INVOKED,
				"match=false");
	}

	public static void main(String[] args) {
		new PartialResponseCallbackMissingBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
