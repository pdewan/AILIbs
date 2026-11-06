package eduAI.trace.processors;

import java.util.*;
import eduAI.trace.lib.LibTraceEvent;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.GenericTracesFileProcessor.TraceMessage;

/** Selects completed answers in request order. Translated streaming responses are
 * chunks; only the merged-message event supplies the completed streaming answer.
 * Missing completion evidence is reported explicitly, never replaced by a chunk. */
public final class CompletedResponseEvidence {
    public static final String CANONICAL_REQUEST = "providerIndependentCanonicalRequest";
    public static final String CANONICAL_RESPONSE = "providerIndependentCanonicalResponse";
    public static final String MERGED_MESSAGE = "mergedMessageStructure";
    private CompletedResponseEvidence() { }

    public record Result(List<TraceMessage> messages, boolean complete) {
        public Result { messages = List.copyOf(messages); }
    }

    /** Input must be one chronological trace interval, including its requests.
     * Every request needs its own completion, including multi-model commands. */
    public static Result select(Collection<TraceRecord> records) {
        List<TraceMessage> messages = new ArrayList<>();
        List<TraceMessage> answer = List.of();
        TraceRecord request = null;
        boolean complete = true;
        for (var record : records) {
            if (record.getExtractedData().get(CANONICAL_REQUEST) instanceof CanonicalTraceRequest) {
                if (request != null) {
                    complete &= !answer.isEmpty();
                    messages.addAll(answer);
                }
                request = record;
                answer = List.of();
            } else if (request != null && request.getId().provider().equals(record.getId().provider())
                    && request.getId().mode() == record.getId().mode()) {
                if (request.getId().mode() == StreamingMode.STREAMING) {
                    if (event(record, LibTraceEvent.STREAMING_CHUNKS_MERGED)) {
                        var merged = record.getExtractedData().get(MERGED_MESSAGE);
                        answer = merged instanceof TraceMessage message ? List.of(message) : List.of();
                    }
                } else if (event(record, LibTraceEvent.RESPONSE_TRANSLATED)) {
                    var translated = record.getExtractedData().get(CANONICAL_RESPONSE);
                    answer = translated instanceof CanonicalTraceResponse response ? response.messages() : List.of();
                }
            }
        }
        messages.addAll(answer);
        return new Result(messages, request != null && complete && !answer.isEmpty());
    }

    private static boolean event(TraceRecord record, LibTraceEvent event) {
        return event.name().toLowerCase(Locale.ROOT).equals(record.getTraceLine().getEventName());
    }
}
