package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;
import static eduAI.trace.processors.mutations.TraceMutationAssertions.*;

public class RequestSentContextMismatchBridgeDemoTraceFilesProcessor {
	private final TraceFilesProcessor processor;

	public RequestSentContextMismatchBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(Path.of(
				"broken_filtered",
				"request_sent_context_mismatch"));
		processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify(processor.checkProviderGenericTextualContextWindowMatches());
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify(TraceCheckResult aRun) {
		assertCheckerMessage(aRun.level3Messages(),
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
