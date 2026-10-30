package eduAI.trace.processors.mutations;

import java.util.*;
import eduAI.trace.TraceLineParser;
import eduAI.trace.processors.*;

/** Changes only a caller-supplied key: two incompatible native meanings now share it.
 * Uses real source snapshots and leaves values, identities and formatting intact. */
public final class MetadataKeyCollisionMutations {
    public static final String PREFIX = "metadata_key_collision_";
    public static final String DIAGNOSTIC = "metadata key collision";
    private static final String NAME = "propertyName", VALUE = "propertyValue";
    private static final String SOURCE = "providerDependentSource";
    private record Evidence(String key, Set<String> meanings) { }
    private MetadataKeyCollisionMutations() { }

    /** One collision per observed provider/mode; no token-count or generic-name assumptions. */
    public static Map<String,List<String>> create(List<String> correct) {
        Map<String,ProviderTraceInterpreter> interpreters = new LinkedHashMap<>();
        interpreters.put(TraceProviderNames.OLLAMA, new OllamaTraceInterpreter());
        interpreters.put(TraceProviderNames.GEMINI, new GeminiTraceInterpreter());
        Map<String,List<Evidence>> snapshots = new LinkedHashMap<>();
        Map<String,List<String>> result = new LinkedHashMap<>();
        String mode = null;
        for (int i=0;i<correct.size();i++) {
            var line = TraceLineParser.parse(correct.get(i));
            if (line == null) continue;
            var data = line.getAuxiliaryData();
            if ("request_sent".equals(line.getEventName())) {
                mode = Objects.requireNonNull(data.get("mode"), "Request mode");
                snapshots.clear();
            }
            if (!"metadata_translated".equals(line.getEventName())) continue;
            String source = data.get(SOURCE), key = data.get(NAME);
            for (var provider : interpreters.entrySet()) {
                var registry = provider.getValue().nativeMetadataRegistry(source);
                if (registry == null) continue;
                var meanings = registry.matchingFields(source,data.get(VALUE));
                if (meanings.isEmpty()) continue;
                String name = PREFIX + provider.getKey().toLowerCase(Locale.ROOT) + "_"
                    + Objects.requireNonNull(mode,"Metadata requires a preceding request").toLowerCase(Locale.ROOT);
                var earlier = snapshots.computeIfAbsent(source, ignored -> new ArrayList<>());
                for (var previous : earlier) {
                    if (!previous.key.equals(key) && Collections.disjoint(previous.meanings,meanings)
                            && !result.containsKey(name)) {
                        var fields = new LinkedHashMap<>(data);
                        fields.put(NAME,previous.key);
                        var mutated = new ArrayList<>(correct);
                        mutated.set(i,TraceFixtureFields.replaceFields(correct.get(i),fields));
                        result.put(name,List.copyOf(mutated));
                    }
                }
                earlier.add(new Evidence(key,meanings));
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No distinct metadata meanings in a shared native snapshot");
        return result;
    }
}
