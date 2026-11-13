package eduAI.trace.processors._tests;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.*;
import java.util.*;
import java.math.BigDecimal;

/** Tests native reverse adaptation and generic leaf containment independently of
 * student keys. Wrong values and absent native evidence fail; supported timeout
 * representations and arbitrary generic object layouts succeed. */
public class ProviderParameterCheckerTest {
    private record Settings(Object arbitraryField) { }
    public static void main(String[] args) {
        var gemini = new NativeParameterValues("Gemini");
        var ollama = new NativeParameterValues("Ollama");
        String config = com.google.genai.types.GenerateContentConfig.builder()
            .temperature(0.4f).topK(40f).httpOptions(com.google.genai.types.HttpOptions.builder().timeout(300000)).build().toString();
        require(gemini.read(config).get("temperature").compareTo(new BigDecimal("0.4")) == 0, "native temperature");
        require(gemini.read(config).get("top_k").compareTo(new BigDecimal("40")) == 0, "native topK");
        require(gemini.read(config).get("timeout").compareTo(new BigDecimal("300")) == 0, "native timeout units");
        for (Object timeout : List.of(300, 300000, java.time.Duration.ofSeconds(300))) {
            var store = new Settings(List.of(0.4f, 40.0, timeout));
            require(gemini.difference(request("Gemini", config, store)).isEmpty(), "generic timeout " + timeout);
        }
        require(!gemini.difference(request("Gemini", config, new Settings(List.of(0.9f,40,300)))).isEmpty(), "wrong value accepted");
        require(!gemini.difference(request("Gemini", "GenerateContentConfig{temperature=Optional.empty}", new Settings(0.4))).isEmpty(), "empty native evidence accepted");
        String options = TraceObjectPrinter.format("target", new LinkedHashMap<>(Map.of("temperature", 0.4)));
        require(ollama.read(options).get("temperature").compareTo(new BigDecimal("0.4")) == 0, "native options map");
        require(ollama.difference(request("Ollama", options, new Settings(0.4))).isEmpty(), "field-only generic store");
        require(!ollama.difference(request("Ollama", options, new Settings(0.9))).isEmpty(), "wrong map value accepted");
        require(ollama.difference(request("Ollama", "Options(optionsMap={temperature=0.4, num_predict=512})", new Settings(0.4))).isEmpty(), "native-only default rejected");
        require(!ollama.difference(request("Ollama", "Options(optionsMap={temperature=0.9, num_predict=512})", new Settings(0.4))).isEmpty(), "default masks wrong temperature");
        System.out.println("Provider parameter checks passed");
    }
    private static TraceRecord request(String provider, String nativeConfig, Object store) {
        var request = new TraceRecord(new TraceRecordId(provider, StreamingMode.NON_STREAMING, 1),
            new TraceLine("lib", "request_sent", "none", "main", "single_model_request_processing", "sample.Client",
                Map.of("providerDependentConfiguration", nativeConfig,
                    "providerIndependentParameterStore", TraceObjectPrinter.format("store", store)), ""), "fixture", 1);
        var evidence = new ArrayList<ProviderParameterChecker.ParameterEvidence>();
        String dump = TraceObjectPrinter.format("store", store);
        for (var leaf : new ReflectiveValueCollector().collect(dump))
            if (leaf.kind() == ReflectiveValueCollector.ValueKind.NUMBER)
                evidence.add(new ProviderParameterChecker.ParameterEvidence(leaf.value(), "", ""));
        var durations = java.util.regex.Pattern.compile("java.time.Duration[^,}]*").matcher(dump);
        while (durations.find()) evidence.add(new ProviderParameterChecker.ParameterEvidence(durations.group(), "", ""));
        request.putExtractedData(RequestTraceFields.PARAMETER_TRANSLATIONS, evidence);
        return request;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
