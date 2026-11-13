package eduAI.trace.processors;

import eduAI.properties.AIPropertyNames;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Reverse adapter for SDK parameter evidence. Only native fields have prescribed
 * names. Translated values are compared with native settings and generic leaves.
 * Equal values can have several meanings; containment does not prove placement. */
public final class NativeParameterValues implements ProviderParameterChecker {
    public static final String MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String PROVIDER_VALUE = "providerValue=";
    private static final String OLLAMA_OPTIONS = "options";
    private static final String NUMBER = "[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?";
    private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(java.time.Duration.ofSeconds(1).toMillis());
    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(java.time.Duration.ofSeconds(1).toNanos());
    private final String provider;
    private final Map<String, String> fields;

    public NativeParameterValues(String provider) {
        this.provider = TraceProviderNames.canonical(provider);
        fields = switch (this.provider) {
            case TraceProviderNames.GEMINI -> Map.of(AIPropertyNames.TEMPERATURE, "temperature",
                AIPropertyNames.TOP_K, "topK", MAX_OUTPUT_TOKENS, "maxOutputTokens", AIPropertyNames.TIMEOUT, "timeout");
            case TraceProviderNames.OLLAMA -> Map.of(AIPropertyNames.TEMPERATURE, "temperature",
                AIPropertyNames.TOP_K, "top_k", AIPropertyNames.NUM_CTX, "num_ctx",
                MAX_OUTPUT_TOKENS, "num_predict", AIPropertyNames.TIMEOUT, "requestTimeoutSeconds");
            default -> throw new IllegalArgumentException("No native parameter adapter for " + provider);
        };
    }
    @Override public String providerName() { return provider; }

    /** Canonical numbers: timeout is in seconds. Absent native evidence remains absent. */
    public Map<String, BigDecimal> read(String nativeDump) {
        var result = new LinkedHashMap<String, BigDecimal>();
        if (nativeDump == null) return result;
        // Ollama serializes complete requests as JSON. Read only its options object;
        // numbers in message text or another JSON field are not parameter evidence.
        int jsonStart = nativeDump.indexOf(PROVIDER_VALUE);
        if (provider.equals(TraceProviderNames.OLLAMA) && jsonStart >= 0
                && nativeDump.contains("io.github.ollama4j.models.chat.OllamaChatRequest")) {
            var json = com.google.gson.JsonParser.parseString(nativeDump.substring(jsonStart + PROVIDER_VALUE.length())).getAsJsonObject();
            if (json.has(OLLAMA_OPTIONS) && json.get(OLLAMA_OPTIONS).isJsonObject()) {
                var options = json.getAsJsonObject(OLLAMA_OPTIONS);
                fields.forEach((meaning, field) -> {
                    if (!meaning.equals(AIPropertyNames.TIMEOUT) && options.has(field)
                            && options.get(field).isJsonPrimitive() && options.get(field).getAsJsonPrimitive().isNumber())
                        result.put(meaning, options.get(field).getAsBigDecimal());
                });
            }
            return result;
        }
        fields.forEach((meaning, field) -> {
            // Native SDK toString formats, including Optional-valued Gemini fields.
            var m = Pattern.compile("(?:^|[,{\\s])" + Pattern.quote(field)
                + "=(?:Optional\\[)?(" + NUMBER + ")(?=[,}\\]\\s]|$)").matcher(nativeDump);
            if (m.find()) result.put(meaning, canonical(meaning, new BigDecimal(m.group(1))));
        });
        // The trace printer may expose an SDK field or an Ollama options map
        // structurally rather than through its SDK toString formatter.
        var leaves = new ReflectiveValueCollector().collect(nativeDump);
        for (var field : fields.entrySet()) {
            for (var leaf : leaves) {
                if (leaf.kind() != ReflectiveValueCollector.ValueKind.NUMBER) continue;
                boolean sdkField = leaf.path().endsWith("." + field.getValue())
                    && leaf.typesOnPath().stream().anyMatch(t -> t.startsWith(nativePackage()));
                if (sdkField) result.put(field.getKey(), canonical(field.getKey(), new BigDecimal(leaf.value())));
            }
            if (provider.equals(TraceProviderNames.OLLAMA) && !field.getKey().equals(AIPropertyNames.TIMEOUT)) {
                // The printer explicitly pairs map keys and values with an arrow.
                var entry = Pattern.compile("key: java\\.lang\\.String value=\"" + Pattern.quote(field.getValue())
                    + "\" -> value: java\\.lang\\.(?:Integer|Long|Float|Double|Short|Byte) value=(" + NUMBER + ")(?=[,}\\s]|$)")
                    .matcher(nativeDump);
                if (entry.find()) result.put(field.getKey(), new BigDecimal(entry.group(1)));
            }
        }
        return result;
    }
    private String nativePackage() {
        return provider.equals(TraceProviderNames.GEMINI) ? "com.google.genai." : "io.github.ollama4j.";
    }
    private BigDecimal canonical(String meaning, BigDecimal value) {
        return meaning.equals(AIPropertyNames.TIMEOUT) && provider.equals(TraceProviderNames.GEMINI)
            ? value.divide(MILLIS_PER_SECOND) : value;
    }
    @SuppressWarnings("unchecked")
    public Map<String, BigDecimal> values(TraceRecord request) {
        var result = new LinkedHashMap<String, BigDecimal>();
        Object evidence = request.getExtractedData().get(RequestTraceFields.PARAMETER_TRANSLATIONS);
        if (evidence instanceof List<?>) {
            for (var item : (List<ParameterEvidence>) evidence) {
                // A timeout may belong to the native client rather than request config.
                // Read only recognized SDK state, never custom fields on a student wrapper.
                if (item.providerDependentTarget() != null && item.providerDependentTarget().contains(nativePackage())) {
                    var targetValues = read(item.providerDependentTargetState());
                    targetValues.putAll(read(item.providerDependentTarget()));
                    if (targetValues.containsKey(AIPropertyNames.TIMEOUT)) result.put(AIPropertyNames.TIMEOUT, targetValues.get(AIPropertyNames.TIMEOUT));
                }
            }
        }
        result.putAll(read(request.getTraceLine().getAuxiliaryData().get(RequestTraceFields.NATIVE_PARAMETERS)));
        return result;
    }
    @Override public String difference(TraceRecord request) {
        var values = values(request);
        if (values.isEmpty()) return "No readable native parameter values in this request or its native timeout trace";
        String store = request.getTraceLine().getAuxiliaryData().get(RequestTraceFields.GENERIC_PARAMETERS);
        Object evidence = request.getExtractedData().get(RequestTraceFields.PARAMETER_TRANSLATIONS);
        if (!(evidence instanceof List<?> translations)) return "No parameter translation evidence associated with this request";
        // Translation events identify supplied values without prescribing their keys.
        // Native-only defaults are not translations, and generic stores can retain
        // settings unsupported by this provider. Neither should create a mismatch.
        int compared = 0;
        for (Object item : translations) {
            String supplied = ((ParameterEvidence) item).propertyValue();
            if (supplied == null) continue;
            var numeric = new ReflectiveValueCollector().collect(supplied).stream()
                .filter(leaf -> leaf.kind() == ReflectiveValueCollector.ValueKind.NUMBER).toList();
            boolean duration = DurationTraceValue.fromDump(supplied) != null;
            if (numeric.isEmpty() && !duration && !supplied.matches(NUMBER)) continue;
            compared++;
            var matches = values.entrySet().stream().filter(value -> contains(supplied, value.getKey(), value.getValue())).toList();
            if (matches.isEmpty()) return "Translated parameter value " + supplied
                + " has no matching recognized native setting; native settings=" + values
                + ". Trace the native configuration/client after applying the setting; an intermediate wrapper is not native evidence.";
            if (matches.stream().noneMatch(value -> contains(store, value.getKey(), value.getValue())))
                return "Generic parameter store does not contain the translated native value for " + supplied
                    + "; generic key and field names are unrestricted";
        }
        if (compared == 0) return "No numeric or Duration parameter translations to compare for this request";
        return "";
    }
    private static boolean contains(String store, String meaning, BigDecimal value) {
        if (store != null && store.matches(NUMBER)) {
            BigDecimal number = new BigDecimal(store);
            return number.compareTo(value) == 0 || meaning.equals(AIPropertyNames.TIMEOUT)
                && number.compareTo(value.multiply(MILLIS_PER_SECOND)) == 0;
        }
        var collector = new ReflectiveValueCollector();
        for (var leaf : collector.collect(store)) {
            if (leaf.kind() != ReflectiveValueCollector.ValueKind.NUMBER) continue;
            BigDecimal stored = new BigDecimal(leaf.value());
            if (stored.compareTo(value) == 0 || meaning.equals(AIPropertyNames.TIMEOUT)
                    && stored.compareTo(value.multiply(MILLIS_PER_SECOND)) == 0) return true;
        }
        if (!meaning.equals(AIPropertyNames.TIMEOUT)) return false;
        if (store == null) return false;
        var durations = DurationTraceValue.DUMP.matcher(store);
        while (durations.find()) {
            var duration = DurationTraceValue.parse(durations.group(1));
            if (duration != null && BigDecimal.valueOf(duration.getSeconds())
                    .add(BigDecimal.valueOf(duration.getNano()).divide(NANOS_PER_SECOND)).compareTo(value) == 0
                    && collector.matchingValueOffset(store, durations.group(), Set.of()) >= 0) return true;
        }
        return false;
    }
}
