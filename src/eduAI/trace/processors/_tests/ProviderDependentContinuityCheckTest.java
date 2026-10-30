package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.nio.file.Path;
import java.util.function.Function;

public class ProviderDependentContinuityCheckTest {
	public static void main(String[] args) {
		ProviderDependentContinuityCheckTest test =
				new ProviderDependentContinuityCheckTest();
		test.assertPasses(
				Path.of("correct_filtered"),
				TraceFilesProcessor::checkContextWindowContinuity,
				"correct continuity");
		test.assertFails(
				"missing_previous_system_prompt",
				TraceFilesProcessor::checkPreviousSystemPromptsRetained);
		test.assertFails(
				"ollama_missing_previous_system_prompt",
				TraceFilesProcessor::checkPreviousSystemPromptsRetained);
		test.assertFails(
				"missing_previous_user_prompt",
				TraceFilesProcessor::checkPreviousUserPromptsRetained);
		test.assertFails(
				"ollama_missing_previous_user_prompt",
				TraceFilesProcessor::checkPreviousUserPromptsRetained);
		test.assertFails(
				"missing_previous_completed_response",
				TraceFilesProcessor::checkPreviousCompletedResponsesRetained);
		test.assertFails(
				"ollama_missing_previous_completed_response",
				TraceFilesProcessor::checkPreviousCompletedResponsesRetained);
		test.assertFails(
				"missing_expected_compaction",
				TraceFilesProcessor::checkExpectedContextWindowCompactions);
		test.assertFails(
				Path.of(
						"validated_broken",
						"wrong_field_streaming_chunk_accumulated_streamingchunk_file_tracegeminibridgestreamingdemo_txt"),
				TraceFilesProcessor
						::checkStreamingChunksReflectedInNextContextWindow);
		test.assertFails(
				Path.of(
						"validated_broken",
						"wrong_field_streaming_chunk_accumulated_streamingchunk_file_traceollamastreamingbridgedemo_txt"),
				TraceFilesProcessor
						::checkStreamingChunksReflectedInNextContextWindow);
		System.out.println("Provider-dependent continuity checks passed");
	}

	private void assertFails(
			String aDirectoryName,
			Function<TraceFilesProcessor, TraceCheckResult> aCheck) {
		assertFails(Path.of("behavior_broken", aDirectoryName), aCheck);
	}

	private void assertFails(
			Path aDirectory,
			Function<TraceFilesProcessor, TraceCheckResult> aCheck) {
		TraceCheckResult result = run(aDirectory, aCheck);
		if (result.passed()) {
			throw new AssertionError(
					"Expected continuity failure for " + aDirectory);
		}
	}

	private void assertPasses(
			Path aDirectory,
			Function<TraceFilesProcessor, TraceCheckResult> aCheck,
			String aDescription) {
		TraceCheckResult result = run(aDirectory, aCheck);
		if (!result.passed()) {
			throw new AssertionError(
					aDescription + " failed: " + result.level3ErrorMessages());
		}
	}

	private TraceCheckResult run(
			Path aDirectory,
			Function<TraceFilesProcessor, TraceCheckResult> aCheck) {
		BridgeDemoTraceInformation information = new BridgeDemoTraceInformation(aDirectory);
		TraceFilesProcessor processor = new NativeTraceFilesProcessor(
				information.traceFiles(), information.expectedInputs());
		processor.processTraceFiles();
		return aCheck.apply(processor);
	}
}
