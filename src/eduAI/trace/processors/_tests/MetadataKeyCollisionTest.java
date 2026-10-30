package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.function.Consumer;
import eduAI.trace.GenericTrace;
import eduAI.trace.lib.*;
import eduAI.trace.processors.*;
import io.github.ollama4j.models.chat.OllamaChatResponseModel;

/** Real API emission controls: arbitrary keys and evolving responses pass;
 * one key for disjoint native meanings in the same snapshot fails. Equal native
 * values are ambiguous and must not manufacture a failure. */
public final class MetadataKeyCollisionTest {
    private static final Object HANDLER = new Object();
    private static void metadata(String key, Object value, Object source) {
        LibTraceActions.traceMetadataTranslated(MetadataKeyCollisionTest.class,key,value,HANDLER,source);
    }
    private static void request(StreamingMode mode) {
        LibTraceActions.traceRequestSent(MetadataKeyCollisionTest.class,mode,
            new Object(),new Object(),new Object(),new Object());
    }
    private static void verify(String name, StreamingMode mode, boolean expected,
            Consumer<OllamaChatResponseModel> events) throws Exception {
        Path trace = Files.createTempFile("metadata-collision-", ".txt");
        try {
            GenericTrace.setOutputEnabled(false);
            GenericTrace.setTraceFile(trace);
            request(mode);
            var response = new OllamaChatResponseModel();
            response.setPromptEvalCount(120);
            response.setEvalCount(30);
            response.setModel("example-model");
            response.setDoneReason("stop");
            response.setTotalDuration(1234567890L);
            events.accept(response);
            GenericTrace.clearTraceFile();
            var processor = new NativeTraceFilesProcessor();
            processor.processTraceFile(TraceProviderNames.OLLAMA,mode,trace);
            var result = processor.checkProviderGenericMetadataMatches(TraceProviderNames.OLLAMA,mode);
            if (result.passed()!=expected || (!expected && result.level3ErrorMessages().stream()
                    .noneMatch(s -> s.contains("metadata key collision"))))
                throw new AssertionError(name+": "+result);
        } finally {
            GenericTrace.resetTraceDestinations();
            Files.deleteIfExists(trace);
        }
    }
    public static void main(String[] args) throws Exception {
        for (var mode : StreamingMode.values()) {
            verify("arbitrary separate keys",mode,true,r->{metadata("received",120,r);metadata("generated",30,r);});
            verify("integer meaning collision",mode,false,r->{metadata("tokens",120,r);metadata("tokens",30,r);});
            verify("string meaning collision",mode,false,r->{metadata("label","example-model",r);metadata("label","stop",r);});
            verify("ambiguous equal values",mode,true,r->{r.setEvalCount(120);metadata("tokens",120,r);metadata("tokens",120,r);});
            verify("later chunk snapshot",mode,true,r->{metadata("tokens",30,r);r.setEvalCount(31);metadata("tokens",31,r);});
            verify("request isolation",mode,true,r->{metadata("tokens",120,r);request(mode);metadata("tokens",30,r);});
            verify("equivalent units",mode,true,r->{metadata("elapsed",1234567890L,r);metadata("elapsed",1234,r);
                metadata("elapsed",java.time.Duration.ofNanos(1234567890L),r);});
        }
        System.out.println("MetadataKeyCollisionTest passed: emitted collisions, arbitrary names, ambiguity, snapshots, requests and units");
    }
}
