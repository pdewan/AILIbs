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
				BridgeDemoTraceFilesProcessor::checkContextWindowContinuity,
				"correct continuity");
		test.assertFails(
				"missing_previous_system_prompt",
				BridgeDemoTraceFilesProcessor::checkPreviousSystemPromptsRetained);
		test.assertFails(
				"ollama_missing_previous_system_prompt",
				BridgeDemoTraceFilesProcessor::checkPreviousSystemPromptsRetained);
		test.assertFails(
				"missing_previous_user_prompt",
				BridgeDemoTraceFilesProcessor::checkPreviousUserPromptsRetained);
		test.assertFails(
				"ollama_missing_previous_user_prompt",
				BridgeDemoTraceFilesProcessor::checkPreviousUserPromptsRetained);
		test.assertFails(
				"missing_previous_completed_response",
				BridgeDemoTraceFilesProcessor::checkPreviousCompletedResponsesRetained);
		test.assertFails(
				"ollama_missing_previous_completed_response",
				BridgeDemoTraceFilesProcessor::checkPreviousCompletedResponsesRetained);
		test.assertFails(
				"missing_expected_compaction",
				BridgeDemoTraceFilesProcessor::checkExpectedContextWindowCompactions);
		test.assertFails(
				Path.of(
						"validated_broken",
						"wrong_field_streaming_chunk_accumulated_streamingchunk_file_tracegeminibridgestreamingdemo_txt"),
				BridgeDemoTraceFilesProcessor
						::checkStreamingChunksReflectedInNextContextWindow);
		test.assertFails(
				Path.of(
						"validated_broken",
						"wrong_field_streaming_chunk_accumulated_streamingchunk_file_traceollamastreamingbridgedemo_txt"),
				BridgeDemoTraceFilesProcessor
						::checkStreamingChunksReflectedInNextContextWindow);
		System.out.println("Provider-dependent continuity checks passed");
	}

	private void assertFails(
			String aDirectoryName,
			Function<BridgeDemoTraceFilesProcessor, TraceCheckResult> aCheck) {
		assertFails(Path.of("behavior_broken", aDirectoryName), aCheck);
	}

	private void assertFails(
			Path aDirectory,
			Function<BridgeDemoTraceFilesProcessor, TraceCheckResult> aCheck) {
		TraceCheckResult result = run(aDirectory, aCheck);
		if (result.passed()) {
			throw new AssertionError(
					"Expected continuity failure for " + aDirectory);
		}
	}

	private void assertPasses(
			Path aDirectory,
			Function<BridgeDemoTraceFilesProcessor, TraceCheckResult> aCheck,
			String aDescription) {
		TraceCheckResult result = run(aDirectory, aCheck);
		if (!result.passed()) {
			throw new AssertionError(
					aDescription + " failed: " + result.level3ErrorMessages());
		}
	}

	private TraceCheckResult run(
			Path aDirectory,
			Function<BridgeDemoTraceFilesProcessor, TraceCheckResult> aCheck) {
		BridgeDemoTraceFilesProcessor processor =
				new BridgeDemoTraceFilesProcessor();
		processor.setTraceDirectory(aDirectory.toString());
		processor.processTraceFiles();
		return aCheck.apply(processor);
	}
}
