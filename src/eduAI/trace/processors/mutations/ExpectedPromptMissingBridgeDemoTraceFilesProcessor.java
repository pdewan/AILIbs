package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;
import java.util.List;

public class ExpectedPromptMissingBridgeDemoTraceFilesProcessor {
	private static final String MISSING_PROMPT =
			"Expected prompt missing from provider request";

	private final BridgeDemoTraceFilesProcessorDelegate delegate;

	public ExpectedPromptMissingBridgeDemoTraceFilesProcessor() {
		GenericTracesFileProcessor.ExpectedInputs expectedInputs =
				new BridgeDemoTraceFilesProcessorDelegate()
						.bridgeDemoExpectedInputs();
		delegate =
				new BridgeDemoTraceFilesProcessorDelegate(
						Path.of(
								"broken_filtered",
								"expected_prompt_missing"),
						new GenericTracesFileProcessor.ExpectedInputs(
								expectedInputs.systemPromptTexts(),
								List.of(
										MISSING_PROMPT,
										expectedInputs.userPromptTexts().get(1),
										expectedInputs.userPromptTexts().get(2),
										expectedInputs.userPromptTexts().get(3)),
								expectedInputs.compactionPromptTexts(),
								expectedInputs.expectedImageBytes()));
	}

	public void runBrokenTraceAndVerify() {
		delegate.runAndVerify(getClass().getSimpleName(), this::verify);
	}

	private void verify(BridgeDemoTraceFilesProcessorDelegate.TraceRun aRun) {
		delegate.assertCheckerMessage(
				aRun,
				GenericTracesFileProcessor.EXPECTED_PROMPT_IN_CONTEXT_WINDOW,
				"provider=Ollama",
				"mode=NON_STREAMING",
				"expectedRole=USER",
				"match=false");
	}

	public static void main(String[] args) {
		new ExpectedPromptMissingBridgeDemoTraceFilesProcessor()
				.runBrokenTraceAndVerify();
	}
}
