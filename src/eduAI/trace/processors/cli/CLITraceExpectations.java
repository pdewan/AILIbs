package eduAI.trace.processors.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import eduAI.trace.TraceTextSummary;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.CanonicalTraceRequest;
import eduAI.trace.processors.NativeParameterValues;
import eduAI.trace.processors.GenericTracesFileProcessor.TraceMessage;
import eduAI.trace.processors.TraceCheckResult;
import eduAI.trace.processors.TraceRecord;

/** Input-derived expectations; model response wording is never an expected value. */
public final class CLITraceExpectations {
    public enum Check { PROMPTS, IMAGES, SETTINGS }
    private record Request(int inputNumber, String model, StreamingMode mode,
            java.util.Optional<String> system, List<String> texts, List<String> images, Map<String, Object> properties,
            List<List<String>> earlierTexts) { }
    private final List<Request> requests;

    private final java.util.Set<Check> supportedChecks;
    private CLITraceExpectations(List<Request> requests, java.util.Set<Check> supportedChecks) {
        this.requests = List.copyOf(requests); this.supportedChecks=java.util.Set.copyOf(supportedChecks);
    }

    public static CLITraceExpectations from(CLIMixedIO io, Path directory) throws IOException {
        return from(io,java.util.Optional.of(directory),java.util.EnumSet.allOf(Check.class),java.util.Optional.empty());
    }
    /** Settings require echoed properties, not prompt bytes or attachment files.
     * Content checks reject this projection rather than treating absent content as valid. */
    public static CLITraceExpectations settingsFrom(CLIMixedIO io) {
        try { return from(io,java.util.Optional.empty(),java.util.EnumSet.of(Check.SETTINGS),java.util.Optional.empty()); }
        catch(IOException impossible) { throw new IllegalStateException("Settings preparation attempted attachment I/O",impossible); }
    }
    public static CLITraceExpectations contentFrom(CLIMixedIO io,Path directory,Check check,CLIAttachmentKinds kinds) throws IOException {
        if(check==Check.SETTINGS) throw new IllegalArgumentException("Use settingsFrom for settings");
        return from(io,java.util.Optional.of(directory),java.util.EnumSet.of(check),java.util.Optional.of(kinds));
    }
    private static CLITraceExpectations from(CLIMixedIO io,java.util.Optional<Path> directory,
            java.util.Set<Check> supported,java.util.Optional<CLIAttachmentKinds> kinds) throws IOException {
        boolean prompts=supported.contains(Check.PROMPTS);
        boolean content=supported.contains(Check.PROMPTS) || supported.contains(Check.IMAGES);
        List<Request> requests = new ArrayList<>();
        List<List<String>> history = new ArrayList<>();
        Boolean streaming = null;
        for (var entry : io.entries()) {
            var input = entry.input();
            if (CLICommandNames.NEW.equals(input.commandName())) history.clear();
            if (input.kind() == CLIInteraction.Kind.PROPERTY_SET
                    && (eduAI.properties.AIPropertyNames.STREAM.equals(input.propertyName()) || eduAI.properties.AIPropertyNames.STREAMING_ALIAS.equals(input.propertyName()))) {
                streaming = (Boolean) input.properties().get(input.propertyName());
            }
            if (input.kind() != CLIInteraction.Kind.PROMPT) continue;
            var properties = input.properties();
            if (!(properties.get(eduAI.properties.AIPropertyNames.CURRENT_MODELS) instanceof List<?> models) || models.isEmpty()
                    || prompts && !(properties.get(eduAI.properties.AIPropertyNames.SYSTEM_PROMPT) instanceof String) || streaming == null) {
                throw new IllegalArgumentException("Cannot derive request expectations for input [" + entry.number()
                        + "]: set " + eduAI.properties.AIPropertyNames.CURRENT_MODELS + ", "
                        + eduAI.properties.AIPropertyNames.SYSTEM_PROMPT + ", and "
                        + eduAI.properties.AIPropertyNames.STREAM + " (or "
                        + eduAI.properties.AIPropertyNames.STREAMING_ALIAS + ") before the prompt.");
            }
            CLIInputAttachments.Expanded expanded;
            if(!content) expanded=new CLIInputAttachments.Expanded(List.of(),List.of());
            else if(kinds.isPresent()) expanded=CLIInputAttachments.expand(entry,directory.orElseThrow(),kinds.orElseThrow(),
                prompts ? CLIFileExpansionContentsChecks.Kind.TEXT : CLIFileExpansionContentsChecks.Kind.IMAGE);
            else expanded=CLIInputAttachments.expand(entry,directory.orElseThrow());
            var texts = expanded.texts();
            var images = expanded.images();
            for (Object model : models) requests.add(new Request(entry.number(), model.toString(),
                    streaming ? StreamingMode.STREAMING : StreamingMode.NON_STREAMING,
                    prompts ? java.util.Optional.of((String)properties.get(eduAI.properties.AIPropertyNames.SYSTEM_PROMPT)) : java.util.Optional.empty(), List.copyOf(texts), List.copyOf(images), properties,
                    List.copyOf(history)));
            history.add(List.copyOf(texts));
        }
        if (requests.isEmpty()) throw new IllegalArgumentException("The I/O log contains no prompts to check.");
        return new CLITraceExpectations(requests,supported);
    }

    public TraceCheckResult check(Collection<TraceRecord> records, String provider, Check check) {
        if(!supportedChecks.contains(check)) throw new IllegalStateException("Expectations were not prepared for " + check);
        List<TraceRecord> actual = records.stream().filter(r -> "request_sent".equals(r.getTraceLine().getEventName()))
                .sorted(Comparator.comparingInt(r -> r.getId().sequenceNumber())).toList();
        List<String> messages = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if(check==Check.SETTINGS) {
            if(requests.stream().map(Request::mode).distinct().count()<StreamingMode.values().length)
                errors.add("Insufficient evidence: requests must exercise both streaming modes");
            if(requests.stream().map(Request::model).distinct().count()<2)
                errors.add("Insufficient evidence: requests must exercise a model change");
            if(!CLISemanticEvidence.numericChange(requests.stream().map(Request::properties).toList()))
                errors.add("Insufficient evidence: requests must exercise a numeric property change");
        }
        report(messages, errors, actual.size() == requests.size(), "CLI request count expected="
                + requests.size() + " actual=" + actual.size());
        for (int i = 0; i < Math.min(actual.size(), requests.size()); i++) {
            TraceRecord record = actual.get(i);
            if (provider != null && !provider.equals(record.getId().provider())) continue;
            Request expected = requests.get(i);
            var generic = (CanonicalTraceRequest) record.getExtractedData().get("providerIndependentCanonicalRequest");
            var nativeRequest = (CanonicalTraceRequest) record.getExtractedData().get("providerDependentCanonicalRequest");
            String where = " input [" + expected.inputNumber() + "] request=" + (i + 1);
            if (generic == null || nativeRequest == null) {
                report(messages, errors, false, "Unreadable request" + where); continue;
            }
            if (check == Check.SETTINGS) {
                String explicitModel = record.getTraceLine().getAuxiliaryData().get(eduAI.trace.lib.LibTraceKeys.REQUEST_MODEL);
                String store = record.getTraceLine().getAuxiliaryData().get(eduAI.trace.processors.RequestTraceFields.GENERIC_PARAMETERS);
                boolean modelMatches = explicitModel != null && !explicitModel.isBlank()
                    ? expected.model().equals(explicitModel)
                    : new eduAI.trace.processors.ReflectiveValueCollector().collect(store).stream().anyMatch(leaf ->
                        leaf.kind() == eduAI.trace.processors.ReflectiveValueCollector.ValueKind.STRING
                        && TraceTextSummary.matches(expected.model(), leaf.value()));
                report(messages, errors, modelMatches, "CLI model" + where
                        + " expected=" + expected.model() + (explicitModel != null && !explicitModel.isBlank()
                        ? " traced request model=" + explicitModel
                        : " (older request trace: require the selected model as a generic store value; no key name is prescribed)"));
                report(messages, errors, expected.mode() == record.getId().mode(), "CLI streaming mode" + where);
                var parameters = new NativeParameterValues(record.getId().provider()).values(record);
                for (String name : CLISemanticEvidence.NUMERIC_PROPERTIES) {
                    Object value = expected.properties().get(name);
                    if (value == null) continue;
                    // Gemini has no num_ctx request option. The CLI snapshot/history checks
                    // still require this value to survive the switch back to Ollama.
                    if (name.equals(eduAI.properties.AIPropertyNames.NUM_CTX)
                            && "Gemini".equalsIgnoreCase(record.getId().provider())) continue;
                    var number = new java.math.BigDecimal(value.toString());
                    var observed = parameters.get(name);
                    boolean matches = observed != null && number.compareTo(observed) == 0;
                    report(messages, errors, matches, "CLI property " + name + where
                            + " expected=" + number + " native=" + observed
                            + (name.equals(eduAI.properties.AIPropertyNames.TIMEOUT) ? " (seconds)" : "")
                            + (observed == null ? " (required native setting is not evidenced; trace the native configuration or client after applying "
                                + name + ". A generic value or intermediate wrapper alone does not prove the native assignment)" : ""));
                }
            } else {
                for (var request : List.of(generic, nativeRequest)) {
                    TraceMessage user = lastUser(request.contextWindow());
                    boolean matches;
                    if (check == Check.IMAGES) {
                        matches = user != null && values(user, "imageBytes").equals(expected.images());
                    } else {
                        matches = user != null && textsMatch(expected.texts(), values(user, "text"));
                        matches &= request.contextWindow().stream().filter(m -> "SYSTEM".equalsIgnoreCase(m.role()))
                                .anyMatch(m -> textsMatch(List.of(expected.system().orElseThrow()), values(m, "text")));
                        var users = request.contextWindow().stream().filter(m -> "USER".equalsIgnoreCase(m.role())).toList();
                        boolean retained = users.size() == expected.earlierTexts().size() + 1;
                        for (int earlier = 0; earlier < expected.earlierTexts().size() && retained; earlier++)
                            retained = textsMatch(expected.earlierTexts().get(earlier), values(users.get(earlier), "text"));
                        report(messages, errors, retained, "CLI earlier prompt/text attachment history" + where
                                + (request == generic ? " generic" : " provider"));
                    }
                    report(messages, errors, matches, "CLI " + check.name().toLowerCase() + where
                            + (request == generic ? " generic" : " provider"));
                }
            }
        }
        return new TraceCheckResult(List.of(), messages, errors);
    }

    private static TraceMessage lastUser(List<TraceMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--)
            if ("USER".equalsIgnoreCase(messages.get(i).role())) return messages.get(i);
        return null;
    }
    private static List<String> values(TraceMessage message, String kind) {
        return message.parts().stream().filter(p -> kind.equals(p.kind())).map(p -> p.value()).toList();
    }
    private static boolean textsMatch(List<String> expected, List<String> actual) {
        if (expected.size() == actual.size()) {
            boolean matches = true;
            for (int i = 0; i < expected.size(); i++) matches &= TraceTextSummary.matches(expected.get(i), actual.get(i));
            if (matches) return true;
        }
        // Native providers may combine text parts. Preserve every character within each part.
        if (actual.size() == 1) return TraceTextSummary.matches(String.join(" ", expected), actual.get(0))
                || TraceTextSummary.matches(String.join("", expected), actual.get(0));
        return false;
    }
    private static void report(List<String> messages, List<String> errors, boolean passed, String detail) {
        String message = (passed ? "I*** " : "E*** ") + detail + " match=" + passed;
        messages.add(message);
        if (!passed) errors.add(message);
    }
}
