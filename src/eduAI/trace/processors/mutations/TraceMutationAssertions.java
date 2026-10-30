package eduAI.trace.processors.mutations;

import java.util.Arrays;
import java.util.List;
import eduAI.trace.processors.TraceFilesProcessorError;

/** Assertions shared by the standalone trace mutation checks. */
public final class TraceMutationAssertions {
	private TraceMutationAssertions() { }

	public static void assertContains(List<TraceFilesProcessorError> errors,
			TraceFilesProcessorError expected) {
		if (!errors.contains(expected)) {
			throw new AssertionError("Expected " + expected + " in " + errors);
		}
	}

	public static void assertCheckerMessage(List<String> messages, String... needles) {
		if (messages.stream().noneMatch(message ->
				Arrays.stream(needles).allMatch(message::contains))) {
			throw new AssertionError("Expected checker message containing "
					+ Arrays.toString(needles) + " in " + messages);
		}
	}
}
