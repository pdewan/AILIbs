package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;

public class ProviderIndependentStructureMismatchBridgeDemoTraceFilesProcessor {
	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public ProviderIndependentStructureMismatchBridgeDemoTraceFilesProcessor() {
		delegate = new BridgeDemoTraceFilesProcessorDelegate(Path.of(
				"broken_filtered",
				"provider_independent_structure_mismatch"));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertCheckerMessage(
				aRun,
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
