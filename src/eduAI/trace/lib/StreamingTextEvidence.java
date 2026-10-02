package eduAI.trace.lib;

import java.util.IdentityHashMap;
import java.util.Map;
import eduAI.trace.GenericTrace;
import eduAI.trace.RunningTextHash;
import eduAI.trace.TraceTextSummary;
import eduAI.trace.TraceMessageText;
import eduAI.trace.TraceObjectPrinter;

/** Independent text accounting: never derives the expected hash from the merger result. */
final class StreamingTextEvidence {
	private static final IdentityHashMap<Object, State> STATES = new IdentityHashMap<>();
	private static long session = -1;
	private static long nextId;
	private static final class State {
		final RunningTextHash hash = new RunningTextHash();
		final String id = session + ":" + (++nextId);
		int count;
		boolean readable = true;
	}
	private static void checkSession() {
		long current = GenericTrace.getSessionId();
		if (session != current) { STATES.clear(); session = current; }
	}
	static synchronized void accumulate(Object merger, Object chunkMessage, Object accumulatedMessage, Map<String, String> data) {
		checkSession();
		State state = STATES.computeIfAbsent(merger, ignored -> new State());
		String chunk = text(TraceObjectPrinter.formatFullEvidence("streamingChunk", chunkMessage));
		String accumulated = text(TraceObjectPrinter.formatFullEvidence("accumulatedMessage", accumulatedMessage));
		data.put("streamTextEvidenceVersion", "1");
		data.put("streamHashId", state.id);
		data.put("streamHashIndex", Integer.toString(++state.count));
		data.put("previousAccumulatedTextSummary", state.hash.snapshot().formatted());
		state.readable &= chunk != null;
		if (chunk != null) {
			state.hash.append(chunk);
			data.put("chunkTextSummary", TraceTextSummary.from(chunk).formatted());
		}
		if (state.readable) data.put("expectedAccumulatedTextSummary", state.hash.snapshot().formatted());
		if (accumulated != null) data.put("accumulatedTextSummary", TraceTextSummary.from(accumulated).formatted());
		data.put("streamTextHashStatus", state.readable && accumulated != null ? "complete" : "unreadable-text");
	}
	static synchronized void finish(Object merger, Object mergedMessage, Map<String, String> data) {
		checkSession();
		State state = STATES.remove(merger);
		String dump = TraceObjectPrinter.formatFullEvidence("mergedMessage", mergedMessage);
		// The same merger also assembles system instructions without streamed chunks.
		if (state == null && java.util.regex.Pattern.compile("role:\\s+[^,}]+\\s+value=SYSTEM\\b").matcher(dump).find()) return;
		String merged = text(dump);
		data.put("streamTextEvidenceVersion", "1");
		if (state != null) {
			data.put("streamHashId", state.id);
			data.put("streamHashIndex", Integer.toString(state.count));
			if (state.readable) data.put("expectedMergedTextSummary", state.hash.snapshot().formatted());
		}
		if (merged != null) data.put("mergedTextSummary", TraceTextSummary.from(merged).formatted());
		data.put("streamTextHashStatus", state != null && state.readable && merged != null ? "complete" : "unreadable-text");
	}
	private static String text(String dump) {
		return TraceMessageText.fromFullDump(dump);
	}
}
