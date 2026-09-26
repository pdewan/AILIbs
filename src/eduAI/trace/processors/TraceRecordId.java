package eduAI.trace.processors;

import eduAI.trace.lib.StreamingMode;

public record TraceRecordId(
		String provider,
		StreamingMode mode,
		int sequenceNumber) {
}
