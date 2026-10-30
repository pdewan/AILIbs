package eduAI.trace.processors;

import java.util.*;
import java.util.function.BiPredicate;
import eduAI.trace.lib.LibTraceEvent;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.GenericTracesFileProcessor.TraceMessage;

/** Shared callback evidence for provider checks and CLI command checks. */
public final class StreamingCallbackEvidence {
    public static final String CALLBACK_ARGUMENT_STRUCTURE="callbackArgumentStructure";
    public static final String CHUNK_MESSAGE_STRUCTURE="streamingChunkMessageStructure";
    private StreamingCallbackEvidence() { }
    public static boolean isPartialCallback(TraceRecord record) {
        return event(record,LibTraceEvent.PARTIAL_RESPONSE_CALLBACK_INVOKED);
    }
    public static List<TraceRecord> callbacks(Collection<TraceRecord> records, String provider,
            StreamingMode mode, String eventName) {
        return records.stream().filter(record->provider.equals(record.getId().provider())
            && mode==record.getId().mode() && eventName.equals(record.getTraceLine().getEventName())).toList();
    }
    /** Preserves A1's rule: each partial argument matches the latest preceding
     * translated chunk for that provider and mode, using its existing comparator.
     * Presence is checked separately; an empty callback list does not prove streaming. */
    public static boolean partialArgumentsMatch(Collection<TraceRecord> records, String provider,
            List<TraceRecord> callbacks, BiPredicate<TraceMessage,TraceMessage> sameMessage) {
        var chunks=records.stream().filter(record->provider.equals(record.getId().provider())
            && record.getId().mode()==StreamingMode.STREAMING
            && event(record,LibTraceEvent.STREAMING_CHUNK_ACCUMULATED)).toList();
        for(var callback:callbacks) {
            TraceMessage preceding=null;
            for(var chunk:chunks) {
                if(chunk.getId().sequenceNumber()>=callback.getId().sequenceNumber()) break;
                var value=chunk.getExtractedData().get(CHUNK_MESSAGE_STRUCTURE);
                if(value instanceof TraceMessage message) preceding=message;
            }
            var argument=callback.getExtractedData().get(CALLBACK_ARGUMENT_STRUCTURE);
            if(!(argument instanceof TraceMessage message) || preceding==null || !sameMessage.test(message,preceding)) return false;
        }
        return true;
    }
    private static boolean event(TraceRecord record,LibTraceEvent event) {
        return event.name().toLowerCase(Locale.ROOT).equals(record.getTraceLine().getEventName());
    }
}
