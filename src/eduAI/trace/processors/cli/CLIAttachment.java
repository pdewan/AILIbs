package eduAI.trace.processors.cli;

import java.util.ArrayList;
import java.util.List;

/** A literal attachment reference and its offsets in the original prompt. */
public record CLIAttachment(String path, int start, int end) {
    private static final String MARKER = "\\@";

    /** Extracts references without opening files or changing prompt whitespace. */
    public static List<CLIAttachment> extract(String prompt) {
        List<CLIAttachment> result = new ArrayList<>();
        int from = 0;
        int start;
        while ((start = prompt.indexOf(MARKER, from)) >= 0) {
            int nameStart = start + MARKER.length();
            if (nameStart == prompt.length()) {
                throw new IllegalArgumentException("Attachment is missing a file name");
            }
            int end;
            String path;
            if (prompt.charAt(nameStart) == '"') {
                int quote = prompt.indexOf('"', nameStart + 1);
                if (quote < 0) throw new IllegalArgumentException("Attachment is missing its closing quote");
                path = prompt.substring(nameStart + 1, quote);
                end = quote + 1;
            } else {
                end = nameStart;
                while (end < prompt.length() && !Character.isWhitespace(prompt.charAt(end))) end++;
                path = prompt.substring(nameStart, end);
            }
            if (path.isBlank()) throw new IllegalArgumentException("Attachment is missing a file name");
            result.add(new CLIAttachment(path, start, end));
            from = end;
        }
        return List.copyOf(result);
    }
}
