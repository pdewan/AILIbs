package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;
import static eduAI.trace.processors.mutations.TraceMutationAssertions.*;

public class ProviderIndependentStructureMismatchBridgeDemoTraceFilesProcessor {
	private final TraceFilesProcessor processor;

	public ProviderIndependentStructureMismatchBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(Path.of(
				"broken_filtered",
				"provider_independent_structure_mismatch"));
		processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify(processor.checkProviderIndependentNonStreamingResponsesMatchAcrossProviders());
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify(TraceCheckResult aRun) {
		assertCheckerMessage(aRun.level3Messages(),
				GenericTracesFileProcessor
						.PROVIDER_INDEPENDENT_STRUCTURE_MATCH_ACROSS_PROVIDERS,
				"event=response_translated",
				"occurrence=0",
				"match=false");
	}

	public static void main(String[] args) {
		new ProviderIndependentStructureMismatchBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
