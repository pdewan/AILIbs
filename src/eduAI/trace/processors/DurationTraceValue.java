package eduAI.trace.processors;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Typed Duration evidence, independent of the printer's variable label. */
final class DurationTraceValue {
    static final Pattern DUMP = Pattern.compile(
            "(?:\\[\\d+\\]|[^,:={}\\[\\]\\s]+): " + Pattern.quote(Duration.class.getName())
            + " interfaces=\\[[^]]*\\] toString=\"([^\"]+)\"(?: value=<opaque>)?");

    static Duration fromDump(String text) {
        if (text == null) return null;
        var match = DUMP.matcher(text);
        return match.matches() ? parse(match.group(1)) : null;
    }

    static Duration parse(String text) {
        try { return Duration.parse(text); }
        catch (DateTimeParseException invalid) { return null; }
    }

    static String normalized(String text) {
        if (text == null) return null;
        var match = DUMP.matcher(text);
        Duration value = parse(match.matches() ? match.group(1) : text);
        return value == null ? text : value.toString();
    }
    private DurationTraceValue() { }
}
