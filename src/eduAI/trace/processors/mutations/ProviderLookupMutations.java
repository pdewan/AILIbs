package eduAI.trace.processors.mutations;

import java.util.*;
import eduAI.trace.lib.LibTraceEvent;
import eduAI.trace.processors.*;

/** Real caller error: a lookup key disagrees with the native provider returned.
 * Only the supplied key changes; generated object identities and dumps stay intact. */
public final class ProviderLookupMutations {
    public record Mutation(List<String> lines, int requestLine) { }
    private ProviderLookupMutations() { }
    public static Map<String, Mutation> create(List<String> lines, Collection<TraceRecord> records) {
        Map<String, Mutation> result = new LinkedHashMap<>();
        for (var request : records) {
            if (!event(request, LibTraceEvent.REQUEST_SENT)) continue;
            String provider = request.getId().provider();
            String opposite = switch (provider) {
                case TraceProviderNames.GEMINI -> TraceProviderNames.OLLAMA;
                case TraceProviderNames.OLLAMA -> TraceProviderNames.GEMINI;
                default -> throw new IllegalArgumentException("Unsupported baseline provider: " + provider);
            };
            String name = "wrong_provider_lookup_" + provider.toLowerCase(Locale.ROOT)
                + "_" + request.getId().mode().name().toLowerCase(Locale.ROOT);
            if (result.containsKey(name)) continue;
            var lookup = records.stream().filter(r -> event(r, LibTraceEvent.SERVER_HANDLE_FACTORY_FETCHED)
                && r.getTraceFileName().equals(request.getTraceFileName())
                && r.getTraceFileLineNumber() < request.getTraceFileLineNumber())
                .max(Comparator.comparingInt(TraceRecord::getTraceFileLineNumber)).orElseThrow();
            var changed = new ArrayList<>(lines);
            int index = lookup.getTraceFileLineNumber() - 1;
            changed.set(index, TraceFixtureFields.replace(changed.get(index), ProviderIdentityFields.PROVIDER, opposite));
            result.put(name, new Mutation(List.copyOf(changed), request.getTraceFileLineNumber()));
        }
        return result;
    }
    /** A consistently swapped registry binding defeats comparisons of the key with itself. */
    public static List<String> swapAllProviderKeys(List<String> lines, Collection<TraceRecord> records) {
        var changed = new ArrayList<>(lines);
        for (var record : records) {
            if (!event(record, LibTraceEvent.SERVER_HANDLE_FACTORY_FETCHED)) continue;
            String provider = record.getTraceLine().getAuxiliaryData().get(ProviderIdentityFields.PROVIDER);
            // Extra registered providers are outside this two-provider swap.
            if (!Set.of(TraceProviderNames.GEMINI, TraceProviderNames.OLLAMA)
                    .contains(TraceProviderNames.canonical(provider))) continue;
            String opposite = switch (TraceProviderNames.canonical(provider)) {
                case TraceProviderNames.GEMINI -> TraceProviderNames.OLLAMA;
                case TraceProviderNames.OLLAMA -> TraceProviderNames.GEMINI;
                default -> throw new IllegalArgumentException("Unsupported baseline provider: " + provider);
            };
            int index = record.getTraceFileLineNumber() - 1;
            changed.set(index, TraceFixtureFields.replace(changed.get(index), ProviderIdentityFields.PROVIDER, opposite));
        }
        return List.copyOf(changed);
    }
    private static boolean event(TraceRecord record, LibTraceEvent event) {
        return event.name().toLowerCase(Locale.ROOT).equals(record.getTraceLine().getEventName());
    }
}
