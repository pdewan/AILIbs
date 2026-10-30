package eduAI.trace.processors;
import java.util.List;
import eduAI.trace.lib.StreamingMode;
import static eduAI.trace.processors.GenericTracesFileProcessor.*;

/** Searches provider/mode groups for explicitly supplied fixed inputs. */
public class FixedInputContentChecks implements ExpectedContentChecks {
    private final GenericTracesFileProcessor processor;
    public FixedInputContentChecks(GenericTracesFileProcessor processor) {
        this.processor = java.util.Objects.requireNonNull(processor);
    }
	public TraceCheckResult checkExpectedInputs() {
		if (processor.expectedInputs.systemPromptTexts().isEmpty()
				&& processor.expectedInputs.userPromptTexts().isEmpty()
				&& processor.expectedInputs.expectedImageBytes().length == 0) {
			return processor.traceCheckResult(processor.promptBasedCheckReportLines());
		}
		for (String provider : processor.providers()) {
			checkExpectedInputs(provider, StreamingMode.NON_STREAMING);
			checkExpectedInputs(provider, StreamingMode.STREAMING);
		}
		return processor.traceCheckResult(processor.promptBasedCheckReportLines());
	}

	public TraceCheckResult checkExpectedPrompts() {
		checkExpectedInputs();
		return processor.checkerResult(EXPECTED_PROMPT_IN_CONTEXT_WINDOW);
	}

	public TraceCheckResult checkExpectedPrompts(String aProvider) {
		checkExpectedInputs(aProvider, StreamingMode.NON_STREAMING);
		checkExpectedInputs(aProvider, StreamingMode.STREAMING);
		return processor.checkerResultForProvider(
				EXPECTED_PROMPT_IN_CONTEXT_WINDOW, aProvider);
	}

	public TraceCheckResult checkExpectedGenericImagesMatch() {
		for (String provider : processor.providers()) {
			checkImageMatches(provider, StreamingMode.NON_STREAMING,
					EXPECTED_GENERIC_IMAGE_MATCH);
			checkImageMatches(provider, StreamingMode.STREAMING,
					EXPECTED_GENERIC_IMAGE_MATCH);
		}
		return processor.checkerResult(EXPECTED_GENERIC_IMAGE_MATCH);
	}

	public TraceCheckResult checkExpectedGenericImagesMatch(String aProvider) {
		checkImageMatches(aProvider, StreamingMode.NON_STREAMING,
				EXPECTED_GENERIC_IMAGE_MATCH);
		checkImageMatches(aProvider, StreamingMode.STREAMING,
				EXPECTED_GENERIC_IMAGE_MATCH);
		return processor.checkerResultForProvider(EXPECTED_GENERIC_IMAGE_MATCH, aProvider);
	}

	public TraceCheckResult checkExpectedInputs(
			String aProvider,
			StreamingMode aMode) {
		List<TraceRecord> requestRecords =
				processor.requestRecords(aProvider, aMode);
		for (int i = 0; i < processor.expectedInputs.systemPromptTexts().size(); i++) {
			String expectedText = processor.expectedInputs.systemPromptTexts().get(i);
			TraceRecord matchingRecord =
					findRequestWithText(requestRecords, "SYSTEM", expectedText);
			boolean found = matchingRecord != null;
			processor.expectedInputMatchResults.add(found);
			processor.recordCheckerMessage(
					expectedTextMessage(
							aProvider,
							aMode,
							i,
							"SYSTEM",
							expectedText,
							matchingRecord,
							found));
		}
		for (int i = 0; i < processor.expectedInputs.userPromptTexts().size(); i++) {
			String expectedText = processor.expectedInputs.userPromptTexts().get(i);
			TraceRecord matchingRecord =
					findRequestWithText(requestRecords, "USER", expectedText);
			boolean found = matchingRecord != null;
			processor.expectedInputMatchResults.add(found);
			processor.recordCheckerMessage(
					expectedTextMessage(
							aProvider,
							aMode,
							i,
							"USER",
							expectedText,
							matchingRecord,
							found));
		}
		if (processor.expectedInputs.expectedImageBytes().length > 0) {
			ImageComparison comparison = processor.findImageComparison(
					requestRecords, processor.expectedInputs.expectedImageBytes());
			boolean providerGenericMatches = comparison.generic() != null
					&& comparison.generic().equals(comparison.provider());
			boolean expectedGenericMatches =
					comparison.expected().equals(comparison.generic());
			processor.expectedInputMatchResults.add(providerGenericMatches);
			processor.expectedInputMatchResults.add(expectedGenericMatches);
			processor.recordCheckerMessage(processor.imageComparisonMessage(
					PROVIDER_GENERIC_IMAGE_MATCH, aProvider, aMode,
					comparison, providerGenericMatches));
			processor.recordCheckerMessage(processor.imageComparisonMessage(
					EXPECTED_GENERIC_IMAGE_MATCH, aProvider, aMode,
					comparison, expectedGenericMatches));
		}
		return processor.traceCheckResult(processor.promptBasedCheckReportLines());
	}

	private TraceRecord findRequestWithText(
			List<TraceRecord> aRequestRecords,
			String aRole,
			String anExpectedText) {
		for (TraceRecord record : aRequestRecords) {
			for (TraceMessage message :
					processor.providerDependentRequestMessages(record)) {
				if (!processor.standardRole(aRole).equals(processor.standardRole(message.role()))) {
					continue;
				}
				for (TracePart part : message.parts()) {
					if ("text".equals(part.kind())
							&& (processor.promptTextMatches(
									part.value(),
									anExpectedText) || ("SYSTEM".equals(processor.standardRole(aRole))
									&& processor.knownSystemSequenceContains(part.value(), anExpectedText)))) {
						return record;
					}
				}
			}
		}
		return null;
	}

	private String expectedTextMessage(
			String aProvider,
			StreamingMode aMode,
			int anExpectedIndex,
			String anExpectedRole,
			String anExpectedText,
			TraceRecord aMatchingRecord,
			boolean isFound) {
		return EXPECTED_PROMPT_IN_CONTEXT_WINDOW
				+ " provider="
				+ aProvider
				+ " mode="
				+ aMode
				+ " expectedPromptId="
				+ processor.expectedPromptId(anExpectedRole, anExpectedIndex)
				+ " expectedPromptIndex="
				+ anExpectedIndex
				+ " expectedRole="
				+ anExpectedRole
				+ " traceLine="
				+ processor.traceLineLocation(aMatchingRecord)
				+ " match="
				+ isFound
				+ " promptPreview=\""
				+ processor.preview(anExpectedText)
				+ "\"";
	}
    private void checkImageMatches(String provider, StreamingMode mode, String checkName) {
        var comparison = processor.findImageComparison(processor.requestRecords(provider, mode), processor.expectedInputs.expectedImageBytes());
        processor.recordCheckerMessage(processor.imageComparisonMessage(checkName, provider, mode, comparison,
                comparison.expected().equals(comparison.generic())));
    }
    @Override public TraceCheckResult check(GenericTracesFileProcessor source, String provider, StreamingMode mode, Kind kind) {
        if (source != processor) throw new IllegalArgumentException("Expected-content checker belongs to a different processor");
        return switch (kind) {
            case PROMPTS -> provider == null ? checkExpectedPrompts() : checkExpectedPrompts(provider);
            case IMAGES -> provider == null ? checkExpectedGenericImagesMatch() : checkExpectedGenericImagesMatch(provider);
            case ALL -> provider == null ? checkExpectedInputs() : checkExpectedInputs(provider, java.util.Objects.requireNonNull(mode));
            case SETTINGS -> throw new IllegalStateException("Fixed-input checks do not define request-setting expectations");
        };
    }

}
