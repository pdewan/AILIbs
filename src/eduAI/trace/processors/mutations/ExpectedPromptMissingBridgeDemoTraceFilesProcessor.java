package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.nio.file.Path;
import static eduAI.trace.processors.mutations.TraceMutationAssertions.*;
import java.util.List;

public class ExpectedPromptMissingBridgeDemoTraceFilesProcessor {
	private static final String MISSING_PROMPT =
			"Expected prompt missing from provider request";

	private final TraceFilesProcessor processor;

	public ExpectedPromptMissingBridgeDemoTraceFilesProcessor() {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(
				Path.of("broken_filtered", "expected_prompt_missing"));
		GenericTracesFileProcessor.ExpectedInputs expectedInputs =
				information.expectedInputs();
		processor = new NativeTraceFilesProcessor(information.traceFiles(),
						new GenericTracesFileProcessor.ExpectedInputs(
								expectedInputs.systemPromptTexts(),
								List.of(
										MISSING_PROMPT,
										expectedInputs.userPromptTexts().get(1),
										expectedInputs.userPromptTexts().get(2),
										expectedInputs.userPromptTexts().get(3)),
								expectedInputs.compactionPromptTexts(),
								expectedInputs.expectedImageBytes()));
        processor.setExpectedContentChecks(new eduAI.trace.processors.assignment1.BridgeDemoContentChecks((GenericTracesFileProcessor) processor));
	}

	public void runBrokenTraceAndVerify() {
		processor.processTraceFiles();
		verify(processor.checkExpectedPrompts());
		System.out.println(getClass().getSimpleName() + " detected expected trace break");
	}

	private void verify(TraceCheckResult aRun) {
		assertCheckerMessage(aRun.level3Messages(),
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
