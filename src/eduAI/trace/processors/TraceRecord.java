package eduAI.trace.processors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import eduAI.trace.TraceLine;

public class TraceRecord {
	private final TraceRecordId id;
	private final TraceLine traceLine;
	private final String traceFileName;
	private final int traceFileLineNumber;
	private final Map<String, Object> extractedData =
			new LinkedHashMap<>();

	public TraceRecord(
			TraceRecordId anId,
			TraceLine aTraceLine,
			String aTraceFileName,
			int aTraceFileLineNumber) {
		id = anId;
		traceLine = aTraceLine;
		traceFileName = aTraceFileName == null ? "" : aTraceFileName;
		traceFileLineNumber = aTraceFileLineNumber;
	}

	public TraceRecordId getId() {
		return id;
	}

	public TraceLine getTraceLine() {
		return traceLine;
	}

	public String getTraceFileName() {
		return traceFileName;
	}

	public int getTraceFileLineNumber() {
		return traceFileLineNumber;
	}

	public void putExtractedData(
			String aName,
			Object aValue) {
		if (aName != null && !aName.isBlank()) {
			extractedData.put(aName, aValue);
		}
	}

	public Map<String, Object> getExtractedData() {
		return Collections.unmodifiableMap(extractedData);
	}
}
