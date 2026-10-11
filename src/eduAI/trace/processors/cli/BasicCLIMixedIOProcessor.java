package eduAI.trace.processors.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eduAI.trace.processors.cli.CLIInputProcessingResult.Diagnostic;
import eduAI.trace.processors.cli.CLIInteraction.Command;
import eduAI.trace.processors.cli.CLIInteraction.Kind;
import eduAI.trace.processors.cli.CLIMixedIO.Chunk;
import eduAI.trace.processors.cli.CLIMixedIO.Entry;
import eduAI.trace.processors.cli.CLIMixedIO.Output;

/** Parses serial CLI output. History bodies are opaque, never reinterpreted as input. */
public final class BasicCLIMixedIOProcessor implements CLIMixedIOProcessor {
    private static final Pattern ECHO = Pattern.compile("^\\[(\\d+)\\] (.*)$");
    private static final Pattern START = Pattern.compile("^@@start \\[(\\d+)\\] (.+)$");
    private static final Pattern END = Pattern.compile(
            "^@@end \\[(\\d+)\\] \\(Time: \\d{2,}:\\d{2}:\\d{2}\\.\\d{3}\\)$");
    private static final Pattern STREAM = Pattern.compile("^@@STREAM\\[([^]\\r\\n]+)\\] (.*)$");
    private final CLIInputProcessor inputProcessor;

    public BasicCLIMixedIOProcessor(CLIInputProcessor inputProcessor) {
        this.inputProcessor = Objects.requireNonNull(inputProcessor);
    }

    @Override
    public CLIMixedIOProcessingResult process(Path file) {
        try {
            return processLines(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return failure(0, "Cannot read mixed I/O file " + file + ": " + e.getMessage());
        }
    }

    private record Observed(int number, int line, String input, Optional<Output> output,
            List<Chunk> chunks) { }

    @Override
    public CLIMixedIOProcessingResult processLines(List<String> lines) {
        List<Observed> observations = new ArrayList<>();
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        try {
            while (index < lines.size()) {
                String line = withoutInputPrefix(lines.get(index));
                if (line.isBlank() || line.startsWith("## ")) { index++; continue; }
                Matcher stream = STREAM.matcher(line);
                if (stream.matches()) {
                    chunks.add(new Chunk(stream.group(1), stream.group(2)));
                    index++;
                    continue;
                }
                Matcher echo = ECHO.matcher(line);
                if (!echo.matches()) return failure(index + 1, "Expected an input echo or streaming marker");
                int number = Integer.parseInt(echo.group(1));
                String input = echo.group(2);
                int sourceLine = ++index;
                Optional<Output> output = Optional.empty();
                if (index < lines.size() && lines.get(index).startsWith("@@start")) {
                    Matcher start = START.matcher(lines.get(index));
                    if (!start.matches() || Integer.parseInt(start.group(1)) != number) {
                        return failure(index + 1, "Start marker does not match input echo");
                    }
                    Map<String, String> attributes = attributes(start.group(2));
                    String success = attributes.get("success");
                    String conversation = attributes.get("conversation_id");
                    if (!("true".equals(success) || "false".equals(success)) || conversation == null) {
                        return failure(index + 1, "Start marker requires success and conversation_id");
                    }
                    List<String> body = new ArrayList<>();
                    index++;
                    while (index < lines.size() && !lines.get(index).startsWith("@@end")) {
                        if (lines.get(index).startsWith("@@start")) {
                            return failure(index + 1, "Nested completed-output block");
                        }
                        body.add(lines.get(index++));
                    }
                    if (index == lines.size()) return failure(sourceLine, "Unclosed completed-output block");
                    Matcher end = END.matcher(lines.get(index));
                    if (!end.matches() || Integer.parseInt(end.group(1)) != number) {
                        return failure(index + 1, "End marker or elapsed-time syntax does not match input echo");
                    }
                    output = Optional.of(new Output(Boolean.parseBoolean(success), conversation,
                            String.join("\n", body), attributes));
                    index++;
                }
                observations.add(new Observed(number, sourceLine, input, output, List.copyOf(chunks)));
                chunks.clear();
            }
            if (!chunks.isEmpty()) return failure(lines.size(), "Streaming chunks have no completed input echo");
            CLIInputProcessingResult parsed = inputProcessor.processLines(
                    observations.stream().map(Observed::input).toList());
            if (!parsed.succeeded()) {
                List<Diagnostic> diagnostics = parsed.diagnostics().stream().map(d -> new Diagnostic(
                        d.lineNumber() == 0 ? 0 : observations.get(d.lineNumber() - 1).line(), d.message())).toList();
                return new CLIMixedIOProcessingResult(Optional.empty(), diagnostics);
            }
            List<Command> commands = parsed.interaction().orElseThrow().commands();
            if (commands.size() != observations.size()) return failure(0, "Blank echoed input cannot be reconstructed");
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < commands.size(); i++) {
                Command c = commands.get(i);
                Observed o = observations.get(i);
                index = o.line() - 1;
                Command located = new Command(c.inputOrdinal(), o.line(), c.rawInput(), c.kind(),
                        c.commandName(), c.argument(), c.propertyName(), c.properties());
                List<CLIAttachment> attachments = c.kind() == Kind.PROMPT
                        ? CLIAttachment.extract(c.argument()) : List.of();
                entries.add(new Entry(o.number(), located, attachments, o.output(), o.chunks()));
            }
            return new CLIMixedIOProcessingResult(Optional.of(new CLIMixedIO(entries)), List.of());
        } catch (IllegalArgumentException e) {
            return failure(index + 1, e.getMessage());
        }
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String field : text.split(" +")) {
            int equals = field.indexOf('=');
            if (equals <= 0 || equals == field.length() - 1
                    || attributes.putIfAbsent(field.substring(0, equals), field.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("Malformed or repeated output attribute: " + field);
            }
        }
        return attributes;
    }

    /** Interactive prefixes may repeat when a script supplies inputs rapidly. */
    private static String withoutInputPrefix(String line) {
        int bracket = line.indexOf('[');
        int marker = line.indexOf("@@");
        int start = marker >= 0 && (bracket < 0 || marker < bracket) ? marker : bracket;
        if (start > 0 && line.substring(0, start).endsWith("> ")) return line.substring(start);
        if (start < 0 && line.endsWith("> ")) return "";
        return line;
    }

    private static CLIMixedIOProcessingResult failure(int line, String message) {
        return new CLIMixedIOProcessingResult(Optional.empty(), List.of(new Diagnostic(line, message)));
    }
}
