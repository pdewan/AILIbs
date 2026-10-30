package eduAI.trace.processors.mutations;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import eduAI.trace.processors.ReflectiveValueCollector;
import eduAI.trace.GenericTrace;
import eduAI.trace.TraceLineParser;

/** Changes one parsed field while preserving the trace envelope and its escaping. */
final class TraceFixtureFields {
    private TraceFixtureFields() { }
    static String replaceLeaf(String dump, String value, String replacement) {
        int offset = new ReflectiveValueCollector().matchingValueOffset(dump, value, Set.of());
        if (offset < 0) throw new IllegalArgumentException("Cannot find stored leaf: " + value);
        var token = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\]\\s]+").matcher(dump);
        token.region(offset, dump.length());
        if (!token.lookingAt()) throw new IllegalArgumentException("Cannot read stored leaf");
        return dump.substring(0, offset - "value=".length()) + replacement + dump.substring(token.end());
    }
    static String replace(String line, String field, String value) {
        var fields = new LinkedHashMap<>(TraceLineParser.parse(line).getAuxiliaryData());
        if (value == null) fields.remove(field); else fields.put(field, value);
        return replaceFields(line, fields);
    }
    static String replaceFields(String line, java.util.Map<String,String> fields) {
        int start = line.indexOf(" <"), end = line.lastIndexOf("> ##");
        if (start < 0 || end < start) throw new IllegalArgumentException("Cannot locate trace fields");
        return line.substring(0, start + 2) + GenericTrace.formatAuxiliaryData(fields) + line.substring(end);
    }
}
