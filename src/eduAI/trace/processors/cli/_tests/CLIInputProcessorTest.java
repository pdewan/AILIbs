package eduAI.trace.processors.cli._tests;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eduAI.trace.processors.cli.BasicCLIInputProcessor;
import eduAI.trace.processors.cli.CLIInputProcessingResult;
import eduAI.trace.processors.cli.CLIInputProcessor;
import eduAI.trace.processors.cli.CLIInteraction;
import eduAI.trace.processors.cli.CLIInteraction.Command;
import eduAI.trace.processors.cli.CLIInteraction.Kind;
import eduAI.trace.processors.cli.CLIPropertyDescriptor;
import eduAI.trace.processors.cli.CLIPropertyDescriptor.ValueType;

/** Deterministic input-contract checks. No providers, credentials, or student code. */
public final class CLIInputProcessorTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        run("property operations and types", CLIInputProcessorTest::propertyOperations);
        run("model changes preserve earlier snapshots", CLIInputProcessorTest::snapshots);
        run("exact text and attachment references", CLIInputProcessorTest::originalText);
        run("first equals separates assignment", CLIInputProcessorTest::assignmentText);
        run("descriptors extend the language", CLIInputProcessorTest::customDescriptors);
        run("getter does not invent a value", CLIInputProcessorTest::unsetGetter);
        run("all invalid values block expectations", CLIInputProcessorTest::invalidValues);
        run("invalid input reports source lines", CLIInputProcessorTest::diagnostics);
        run("missing required settings block prompt", CLIInputProcessorTest::missingSettings);
        run("returned data is immutable", CLIInputProcessorTest::immutableData);
        run("processor calls have independent state", CLIInputProcessorTest::independentRuns);
        run("configuration is defensively copied", CLIInputProcessorTest::configurationCopy);
        run("views remain commands", CLIInputProcessorTest::views);
        run("blank lines preserve physical numbering", CLIInputProcessorTest::blankLines);
        run("exit and end of file", CLIInputProcessorTest::termination);
        run("UTF-8 file and missing file", CLIInputProcessorTest::files);
        System.out.println("CLIInputProcessorTest: " + passed + " tests passed");
    }

    private static Map<String, CLIPropertyDescriptor> descriptors() {
        Map<String, CLIPropertyDescriptor> result = new LinkedHashMap<>();
        result.put("current_models", descriptor(ValueType.STRING_LIST, "model-a", "model-b"));
        result.put("stream", descriptor(ValueType.BOOLEAN));
        result.put("timeout", descriptor(ValueType.INTEGER));
        result.put("temperature", descriptor(ValueType.DOUBLE));
        result.put("system_prompt", descriptor(ValueType.STRING));
        return result;
    }

    private static CLIPropertyDescriptor descriptor(ValueType type, String... alternatives) {
        return new CLIPropertyDescriptor(type, List.of(alternatives));
    }

    private static CLIInputProcessor processor() {
        return new BasicCLIInputProcessor(descriptors(), Set.of("messages", "transcript", "bye"), Set.of());
    }

    private static List<Command> good(CLIInputProcessor processor, String... lines) {
        CLIInputProcessingResult result = processor.processLines(List.of(lines));
        require(result.succeeded(), "unexpected diagnostics: " + result.diagnostics());
        require(result.diagnostics().isEmpty(), "successful result has diagnostics");
        return result.interaction().orElseThrow().commands();
    }

    private static void propertyOperations() {
        List<Command> commands = good(processor(), "/prop current_models = model-a, model-b",
                "/prop timeout=300", "/prop temperature = 0.4", "/prop stream = true",
                "/prop system_prompt = Be concise.", "/prop timeout", "What do you see?");
        equal(Kind.PROPERTY_SET, commands.get(0).kind());
        equal("timeout", commands.get(5).propertyName());
        equal(Kind.PROPERTY_GET, commands.get(5).kind());
        equal(Kind.PROMPT, commands.get(6).kind());
        equal(Map.of("current_models", List.of("model-a", "model-b"), "timeout", 300,
                "temperature", 0.4, "stream", true, "system_prompt", "Be concise."),
                commands.get(6).properties());
        equal(commands.get(4).properties(), commands.get(5).properties());
    }

    private static void snapshots() {
        List<Command> commands = good(processor(), "/prop current_models = model-a",
                "/prop timeout = 5", "/prop stream = false", "First prompt",
                "/prop current_models = model-b", "/prop timeout = 10",
                "/prop stream = true", "Second prompt", "/prop current_models = model-a", "Third prompt");
        equal(List.of("model-a"), commands.get(3).properties().get("current_models"));
        equal(5, commands.get(3).properties().get("timeout"));
        equal(false, commands.get(3).properties().get("stream"));
        equal(List.of("model-b"), commands.get(7).properties().get("current_models"));
        equal(10, commands.get(7).properties().get("timeout"));
        equal(true, commands.get(9).properties().get("stream"));
        equal(List.of("model-a"), commands.get(9).properties().get("current_models"));
    }

    private static void originalText() {
        String prompt = "  Compare café 雪 \\@\"missing image.png\" with \\@\"notes.txt\".  ";
        Command command = good(processor(), prompt).get(0);
        equal(prompt, command.rawInput());
        equal(prompt, command.argument());
        // Referenced files deliberately do not exist: this increment must not open them.
        String property = "  /prop system_prompt = Keep  internal  spaces.  ";
        command = good(processor(), property).get(0);
        equal(property, command.rawInput());
        equal("Keep  internal  spaces.", command.properties().get("system_prompt"));
    }

    private static void assignmentText() {
        List<Command> commands = good(processor(), "/prop system_prompt = Explain x=y = z",
                "/prop system_prompt = \"literal quotes\"", "/prop system_prompt =", "x=y /prop stream = true");
        equal("Explain x=y = z", commands.get(0).properties().get("system_prompt"));
        equal("\"literal quotes\"", commands.get(1).properties().get("system_prompt"));
        equal("", commands.get(2).properties().get("system_prompt"));
        equal(Kind.PROMPT, commands.get(3).kind());
    }

    private static void customDescriptors() {
        Map<String, CLIPropertyDescriptor> definitions = descriptors();
        definitions.put("answer_style", descriptor(ValueType.STRING, "brief", "detailed"));
        definitions.put("attempts", descriptor(ValueType.INTEGER));
        CLIInputProcessor parser = new BasicCLIInputProcessor(definitions, Set.of("inspect"), Set.of());
        List<Command> commands = good(parser, "/prop answer_style = brief", "/prop attempts = 2", "/inspect");
        equal("brief", commands.get(2).properties().get("answer_style"));
        equal(2, commands.get(2).properties().get("attempts"));
        require(!parser.processLines(List.of("/prop answer_style = verbose")).succeeded(), "alternatives ignored");
    }

    private static void unsetGetter() {
        Command getter = good(processor(), "/prop timeout").get(0);
        require(!getter.properties().containsKey("timeout"), "getter created an expected default");
    }

    private static void invalidValues() {
        for (String line : List.of("/prop stream = yes", "/prop timeout = 1.5",
                "/prop timeout = 2147483648", "/prop timeout =", "/prop temperature = NaN",
                "/prop temperature = Infinity", "/prop temperature = abc", "/prop current_models = model-c",
                "/prop current_models = model-a,model-c", "/prop current_models = model-a,",
                "/prop current_models = ,model-a", "/prop current_models =",
                "/prop missing = 1", "/prop timeout 5", "/prop", "/", "/unregistered")) {
            CLIInputProcessingResult result = processor().processLines(List.of(line));
            require(!result.succeeded() && result.interaction().isEmpty(), "invalid input exposed expectations: " + line);
            equal(1, result.diagnostics().get(0).lineNumber());
        }
    }

    private static void diagnostics() {
        CLIInputProcessingResult result = processor().processLines(List.of(
                "/prop timeout=5", "", "/prop stream=perhaps", "valid prompt", "/unknown"));
        require(result.interaction().isEmpty(), "partial interaction escaped failure");
        equal(List.of(3, 5), result.diagnostics().stream().map(d -> d.lineNumber()).toList());
        require(result.diagnostics().get(0).message().contains("stream"), "diagnostic omits property");
    }

    private static void missingSettings() {
        CLIInputProcessor parser = new BasicCLIInputProcessor(descriptors(), Set.of(), Set.of("current_models", "stream"));
        CLIInputProcessingResult result = parser.processLines(List.of("/prop current_models=model-a", "Hello"));
        require(!result.succeeded(), "missing settings accepted");
        equal(2, result.diagnostics().get(0).lineNumber());
        require(result.diagnostics().get(0).message().contains("stream"), "missing property not identified");
        good(parser, "/prop current_models=model-a", "/prop stream=false", "Hello");
    }

    private static void immutableData() {
        List<Command> commands = good(processor(), "/prop current_models=model-a", "Hello");
        unsupported(() -> commands.clear());
        unsupported(() -> commands.get(1).properties().put("timeout", 99));
        unsupported(() -> ((List<?>) commands.get(1).properties().get("current_models")).clear());
        List<String> mutableModels = new ArrayList<>(List.of("model-a"));
        Map<String, Object> mutableProperties = new LinkedHashMap<>();
        mutableProperties.put("current_models", mutableModels);
        Command record = new Command(1, 1, "Hello", Kind.PROMPT, "prompt", "Hello", null, mutableProperties);
        mutableModels.add("model-b");
        mutableProperties.put("timeout", 99);
        equal(Map.of("current_models", List.of("model-a")), record.properties());
    }

    private static void independentRuns() {
        CLIInputProcessor parser = processor();
        good(parser, "/prop timeout=5", "/bye");
        equal(Map.of(), good(parser, "New session").get(0).properties());
    }

    private static void configurationCopy() {
        Map<String, CLIPropertyDescriptor> definitions = descriptors();
        List<String> choices = new ArrayList<>(List.of("brief"));
        definitions.put("style", new CLIPropertyDescriptor(ValueType.STRING, choices));
        CLIInputProcessor parser = new BasicCLIInputProcessor(definitions, Set.of(), Set.of());
        choices.add("verbose");
        definitions.clear();
        good(parser, "/prop style=brief");
        require(!parser.processLines(List.of("/prop style=verbose")).succeeded(), "mutable alternatives escaped");
    }

    private static void views() {
        List<Command> commands = good(processor(), "/transcript", "/messages", "/messages -context -system");
        require(commands.stream().allMatch(c -> c.kind() == Kind.COMMAND), "view became a prompt");
        equal("-context -system", commands.get(2).argument());
    }

    private static void blankLines() {
        List<Command> commands = good(processor(), "", "  ", "/prop timeout=5", "", "Hello");
        equal(3, commands.get(0).lineNumber());
        equal(5, commands.get(1).lineNumber());
        equal(1, commands.get(0).inputOrdinal());
        equal(2, commands.get(1).inputOrdinal());
        require(!processor().processLines(List.of("", " ")).succeeded(), "empty script accepted");
    }

    private static void termination() {
        good(processor(), "Hello"); // EOF is valid; assignment sequencing is a separate check.
        good(processor(), "Hello", "/bye", "");
        require(!processor().processLines(List.of("/bye", "Hello")).succeeded(), "unconsumed prompt accepted");
        require(!processor().processLines(List.of("/bye extra")).succeeded(), "invalid exit accepted");
    }

    private static void files() throws Exception {
        Path file = Files.createTempFile("cli-input-contract-", ".txt");
        try {
            Files.writeString(file, "/prop system_prompt = café\r\n\r\n 雪 \\@\"notes.txt\"  \r\n", StandardCharsets.UTF_8);
            CLIInputProcessingResult result = processor().process(file);
            require(result.succeeded(), "UTF-8 file failed: " + result.diagnostics());
            Command prompt = result.interaction().orElseThrow().commands().get(1);
            equal(3, prompt.lineNumber());
            equal(" 雪 \\@\"notes.txt\"  ", prompt.rawInput());
            equal("café", prompt.properties().get("system_prompt"));
        } finally {
            Files.deleteIfExists(file);
        }
        CLIInputProcessingResult missing = processor().process(file);
        require(!missing.succeeded() && missing.interaction().isEmpty(), "missing file accepted");
        require(missing.diagnostics().get(0).message().contains(file.toString()), "missing file not identified");
    }

    private static void unsupported(Runnable action) {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Mutation of expected evidence was permitted");
    }

    private static void equal(Object expected, Object actual) {
        require(java.util.Objects.equals(expected, actual), "expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Test { void run() throws Exception; }

    private static void run(String name, Test test) throws Exception {
        try { test.run(); } catch (Exception | AssertionError failure) {
            throw new AssertionError("FAILED: " + name, failure);
        }
        passed++;
        System.out.println("PASS: " + name);
    }
}
