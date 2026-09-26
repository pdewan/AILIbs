package eduAI.trace.lib;

import java.util.Map;

import eduAI.trace.TraceData;

public final class LibTraceData {
	private LibTraceData() {
	}

	public static Map<String, String> threadPatternData(
			String aSummary,
			String... aRelatedThreadNames) {
		return TraceData.data(
				LibTraceKeys.SUMMARY,
				aSummary,
				LibTraceKeys.RELATED_THREADS,
				relatedThreads(aRelatedThreadNames));
	}

	private static String relatedThreads(String... aRelatedThreadNames) {
		if (aRelatedThreadNames == null) {
			return "";
		}
		return String.join(", ", aRelatedThreadNames);
	}
}
