package eduAI.trace.processors.cli;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import eduAI.trace.processors.TraceCheckResult;
import eduAI.trace.processors.TraceFilesProcessor;

/** Property names identify uses of a role; they do not require distinct implementation classes. */
public final class CLIPropertyHandlerTags {
    public static final String HANDLER_TAG = "CLIPropertyHandler";
    public static final Map<String, String> PROPERTY_TAGS = Map.ofEntries(
            Map.entry(eduAI.properties.AIPropertyNames.CURRENT_MODELS, "CurrentModelsPropertyHandler"),
            Map.entry(eduAI.properties.AIPropertyNames.SYSTEM_PROMPT, "SystemPromptPropertyHandler"),
            Map.entry(eduAI.properties.AIPropertyNames.TEMPERATURE, "TemperaturePropertyHandler"),
            Map.entry(eduAI.properties.AIPropertyNames.TIMEOUT, "TimeoutPropertyHandler"),
            Map.entry(eduAI.properties.AIPropertyNames.STREAM, "StreamPropertyHandler"),
            Map.entry(eduAI.properties.AIPropertyNames.STREAMING_ALIAS, "StreamPropertyHandler"),
            Map.entry(eduAI.properties.AIPropertyNames.NUM_CTX, "NumCtxPropertyHandler"),
            Map.entry(eduAI.properties.AIPropertyNames.TOP_K, "TopKPropertyHandler"));
    private CLIPropertyHandlerTags() { }

    public static boolean isPropertyHandlerTag(String tag) {
        return HANDLER_TAG.equals(tag) || PROPERTY_TAGS.containsValue(tag);
    }

    public static TraceCheckResult checkLookups(TraceFilesProcessor processor, Collection<String> properties) {
        List<String> errors = new ArrayList<>();
        if (properties.isEmpty()) errors.add("No property inputs were available to check property-handler lookups.");
        for (String property : properties) {
            String tag = PROPERTY_TAGS.get(property);
            if (tag == null) errors.add("No property-handler tag defined for input property=" + property);
            else if (processor.classesForTag(tag).isEmpty()) {
                errors.add("Missing property-handler lookup: property=" + property + " expectedTag=" + tag);
            }
        }
        return new TraceCheckResult(List.of(), List.of(), errors);
    }

    public static TraceCheckResult checkSeparation(TraceFilesProcessor processor) {
        List<String> errors = new ArrayList<>();
        var classes = processor.classesForTag(HANDLER_TAG);
        if (classes.isEmpty()) errors.add("No CLI property handler class found in trace.");
        for (String name : classes) {
            var otherRoles = processor.tagsForClass(name).stream()
                    .filter(tag -> !isPropertyHandlerTag(tag)).toList();
            if (!otherRoles.isEmpty()) errors.add("Property handler class=" + name
                    + " also implements unrelated roles=" + otherRoles);
        }
        return new TraceCheckResult(List.of(), List.of(), errors);
    }
}
