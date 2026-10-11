package eduAI.trace.processors.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import eduAI.trace.processors.cli.CLIInputProcessingResult.Diagnostic;
import eduAI.trace.processors.cli.CLIInteraction.Command;
import eduAI.trace.processors.cli.CLIInteraction.Kind;

/**
 * Reconstructs expectations from a UTF-8, one-command-per-line script.
 * Blank lines are ignored. Property values are unquoted text after the first '='.
 * Command-specific view flags, attachments, and assignment sequences are left to
 * later processors. No student implementation or AI provider is invoked.
 */
public final class BasicCLIInputProcessor implements CLIInputProcessor {
    private final Map<String, CLIPropertyDescriptor> descriptors;
    private final Set<String> commands;
    private final Set<String> requiredPromptProperties;

    public BasicCLIInputProcessor(Map<String, CLIPropertyDescriptor> descriptors,
            Set<String> commands, Set<String> requiredPromptProperties) {
        this.descriptors = Map.copyOf(descriptors);
        this.commands = Set.copyOf(commands);
        this.requiredPromptProperties = Set.copyOf(requiredPromptProperties);
        if (!descriptors.keySet().containsAll(requiredPromptProperties)) {
            throw new IllegalArgumentException("Required properties need descriptors");
        }
    }

    @Override
    public CLIInputProcessingResult process(Path inputFile) {
        try {
            return processLines(Files.readAllLines(inputFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return failure(List.of(new Diagnostic(0,
                    "Cannot read input file " + inputFile + ": " + e.getMessage())));
        }
    }

    @Override
    public CLIInputProcessingResult processLines(List<String> lines) {
        List<Command> records = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        int ordinal = 0;
        boolean terminated = false;
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            String input = raw.strip();
            if (input.isEmpty()) continue;
            ordinal++;
            try {
                if (terminated) throw new IllegalArgumentException("Input after /bye is not consumed");
                String name = "prompt";
                String argument = raw;
                Kind kind = Kind.PROMPT;
                String propertyName = null;
                if (input.startsWith("/")) {
                    int end = 1;
                    while (end < input.length() && !Character.isWhitespace(input.charAt(end))) end++;
                    name = input.substring(1, end);
                    argument = input.substring(end).strip();
                    kind = Kind.COMMAND;
                    if (name.equals("prop")) {
                        int equals = argument.indexOf('=');
                        propertyName = (equals < 0 ? argument : argument.substring(0, equals)).strip();
                        CLIPropertyDescriptor descriptor = descriptors.get(propertyName);
                        if (descriptor == null) {
                            throw new IllegalArgumentException("Unknown property: " + propertyName);
                        }
                        if (equals < 0) {
                            kind = Kind.PROPERTY_GET;
                        } else {
                            kind = Kind.PROPERTY_SET;
                            String value = argument.substring(equals + 1).strip();
                            try {
                                Object parsed = descriptor.parse(value);
                                properties.put(propertyName, parsed);
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException(propertyName + ": " + e.getMessage(), e);
                            }
                        }
                    } else if (!commands.contains(name)) {
                        throw new IllegalArgumentException("Unknown command: /" + name);
                    }
                    if (name.equals("bye")) {
                        if (!argument.isEmpty()) throw new IllegalArgumentException("/bye takes no arguments");
                        terminated = true;
                    }
                }
                if (kind == Kind.PROMPT) {
                    List<String> missing = requiredPromptProperties.stream()
                            .filter(key -> !properties.containsKey(key)).sorted().toList();
                    if (!missing.isEmpty()) {
                        throw new IllegalArgumentException("Missing prompt properties: " + missing);
                    }
                }
                records.add(new Command(ordinal, index + 1, raw, kind, name,
                        argument, propertyName, properties));
            } catch (IllegalArgumentException e) {
                diagnostics.add(new Diagnostic(index + 1, e.getMessage()));
            }
        }
        if (ordinal == 0) diagnostics.add(new Diagnostic(0, "Input contains no commands"));
        return diagnostics.isEmpty()
                ? new CLIInputProcessingResult(Optional.of(new CLIInteraction(records)), List.of())
                : failure(diagnostics);
    }

    private CLIInputProcessingResult failure(List<Diagnostic> diagnostics) {
        return new CLIInputProcessingResult(Optional.empty(), diagnostics);
    }
}
