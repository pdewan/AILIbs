package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;
import static eduAI.trace.processors.mutations.TraceMutationAssertions.*;

public class CompleteResponseCallbackMissingBridgeDemoTraceFilesProcessor {
	private final TraceFilesProcessor processor;

	public CompleteResponseCallbackMissingBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(Path.of(
				"broken_filtered",
				"complete_response_callback_missing"));
		processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify(processor.checkCompleteResponseCallbacksInvoked());
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify(TraceCheckResult aRun) {
		assertContains(
				aRun.errors(),
				TraceFilesProcessorError
						.MISSING_COMPLETE_RESPONSE_CALLBACK_INVOKED);
		assertCheckerMessage(aRun.level3Messages(),
				GenericTracesFileProcessor.COMPLETE_RESPONSE_CALLBACK_INVOKED,
				"match=false");
	}

	public static void main(String[] args) {
		new CompleteResponseCallbackMissingBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
