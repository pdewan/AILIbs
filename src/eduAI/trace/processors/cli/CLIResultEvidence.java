package eduAI.trace.processors.cli;

import java.util.*;
import java.util.regex.Pattern;
import eduAI.trace.TraceTextSummary;
import eduAI.trace.processors.ReflectiveValueCollector;

/** Reuses A1's leaf extraction, accepting structured returns and formatted text alike. */
public final class CLIResultEvidence {
    private final List<String> values;
    public CLIResultEvidence(String dump) {
        values = new ReflectiveValueCollector().collect(dump).stream().map(v -> v.value()).toList();
    }
    private CLIResultEvidence(List<String> values) { this.values = List.copyOf(values); }
    /** Captured stdout is already text, not a reflective serialization. */
    public static CLIResultEvidence printed(String text) { return new CLIResultEvidence(List.of(text)); }
    public List<String> values() { return values; }
    public String text() { return String.join("\n", values); }
    public boolean contains(String expected) { return matchingText(expected).isPresent(); }
    public Optional<String> matchingText(String expected) {
        if (expected == null || expected.isBlank()) return Optional.empty();
        var collector = new ReflectiveValueCollector();
        var summary = TraceTextSummary.fromToken(expected);
        for (String value : values) {
            if (TraceTextSummary.matches(value, expected)) return Optional.of(value);
            if (summary == null) {
                if (collector.matchingSerializedValueOffset(value, expected) >= 0) return Optional.of(expected);
            } else {
                for (int start = value.indexOf(summary.prefix()); start >= 0;
                        start = value.indexOf(summary.prefix(), start + 1)) {
                    if (value.codePointCount(start, value.length()) < summary.length()) continue;
                    String candidate = value.substring(start, value.offsetByCodePoints(start, summary.length()));
                    if (TraceTextSummary.matches(expected, candidate)) return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
    /** The supplied implementation's short history view advertises its omitted length. */
    public boolean containsHistoryText(String expected) {
        if (contains(expected)) return true;
        if (expected == null || expected.isEmpty() || TraceTextSummary.fromToken(expected) != null) return false;
        String normalized = expected.replaceAll("\\s+", " ").trim();
        for (String value : values) {
            String shown = value.replaceAll("\\s+", " ");
            if (new ReflectiveValueCollector().matchingSerializedValueOffset(shown, normalized) >= 0) return true;
            var marker = Pattern.compile("\\.\\.\\. length=(\\d+)(?!\\d)").matcher(shown);
            while (marker.find()) {
                int length = Integer.parseInt(marker.group(1));
                if (length != expected.length() && length != normalized.length()) continue;
                int start = shown.lastIndexOf(" = ", marker.start());
                if (start >= 0) {
                    String prefix = shown.substring(start + " = ".length(), marker.start()).strip();
                    if (!prefix.isEmpty() && normalized.startsWith(prefix)) return true;
                }
            }
        }
        return false;
    }
    /** Scope formatted histories to a turn when labels exist; structured data still uses leaf matching. */
    public CLIResultEvidence turn(int number, String role) {
        String label = "[" + number + "." + role + "]";
        String text = text();
        int start = text.indexOf(label);
        if (start < 0) return Pattern.compile("\\[\\d+\\.(user|assistant|system)\\]").matcher(text).find()
                ? new CLIResultEvidence(List.of()) : this;
        var next = Pattern.compile("\\[\\d+\\.(user|assistant|system)\\]").matcher(text);
        int end = next.find(start + label.length()) ? next.start() : text.length();
        return new CLIResultEvidence(List.of(text.substring(start, end)));
    }
    public List<String> require(String label, Object expected) {
        if (expected == null) return List.of("Expectation unavailable: " + label);
        List<?> leaves = expected instanceof List<?> list ? list : List.of(expected);
        List<String> errors = new ArrayList<>();
        for (Object leaf : leaves) if (!contains(String.valueOf(leaf))) errors.add("Returned result lacks " + label + "=" + leaf);
        return errors;
    }
}
