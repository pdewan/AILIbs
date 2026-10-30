package eduAI.trace.processors.mutations;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import eduAI.trace.TraceLineParser;
import eduAI.trace.lib.LibTraceKeys;
import eduAI.trace.processors.CLITraceInformation;
import eduAI.trace.cli.CLITraceActions;
import eduAI.trace.processors.cli.CLICommandHandlerChecks;
import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

/** Reuses the demo mutation catalog for one chronological CLI trace and its supporting files. */
public final class CLITraceFixturesGenerator {
    public record Result(TraceFixtureFilter.FilterSummary filter, List<Path> mutations) { }

    public Result generate(Path source, Path correct, Path broken, List<Path> supportingFiles,
            eduAI.trace.processors.cli.CLIMixedIO interactions,
            eduAI.trace.processors.cli.CLIPropertyExpectations propertyPolicy) throws IOException {
        source = source.toAbsolutePath().normalize();
        correct = correct.toAbsolutePath().normalize();
        broken = broken.toAbsolutePath().normalize();
        if (source.equals(correct) || source.startsWith(broken) || correct.startsWith(broken)
                || broken.startsWith(correct)) throw new IllegalArgumentException("Source, correct, and broken datasets must be separate.");
        Path manifest = broken.resolve("datasets.txt");
        List<String> previousDatasets = Files.exists(manifest) ? Files.readAllLines(manifest) : List.of();
        for (Path file : supportingFiles) {
            if (file.isAbsolute() || !source.resolve(file).normalize().startsWith(source))
                throw new IllegalArgumentException("Supporting file must be inside the source directory: " + file);
            if (!Files.isRegularFile(source.resolve(file))) throw new IOException("Missing supporting file: " + source.resolve(file));
        }
        List<String> traces = List.of(CLITraceInformation.TRACE_FILE_NAME);
        var filter = new TraceFixtureFilter(traces).filterDataset(source, correct);
        copySupportingFiles(source, correct, supportingFiles);
        var mutator = new TraceFixtureMutator(traces);
        List<Path> generated = new ArrayList<>(new EssentialBrokenTraceFixturesGenerator().generate(correct, broken, mutator));
        List<String> correctLines = Files.readAllLines(correct.resolve(CLITraceInformation.TRACE_FILE_NAME));
        for (var collision : MetadataKeyCollisionMutations.create(correctLines).entrySet()) {
            Path directory = broken.resolve(collision.getKey());
            Files.createDirectories(directory);
            Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME), collision.getValue());
            generated.add(directory);
        }
        boolean sixCalls = correctLines.stream().map(TraceLineParser::parse).anyMatch(
            p -> eduAI.trace.cli.MinimalCLITrace.Event.COMMAND_HANDLED.eventName().equals(p.getEventName()));
        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<Integer>> commandReturns = new LinkedHashMap<>();
        for (String line : Files.readAllLines(correct.resolve(CLITraceInformation.TRACE_FILE_NAME))) {
            var parsed = TraceLineParser.parse(line);
            counts.merge(parsed.getEventName(), 1, Integer::sum);
            if (CLITraceActions.Event.COMMAND_HANDLER_RESULTS.eventName().equals(parsed.getEventName())) {
                String command = parsed.getAuxiliaryData().get(CLITraceActions.COMMAND_NAME);
                if (CLICommandHandlerChecks.COMMAND_TAGS.containsKey(command == null ? "" : command))
                    commandReturns.computeIfAbsent(command, ignored -> new ArrayList<>()).add(counts.get(parsed.getEventName()));
            }
        }
        // Every request is independently required, including both sides of model/mode switches.
        for (int occurrence = 1; occurrence <= counts.getOrDefault("request_sent", 0); occurrence++) {
            generated.add(mutator.createBrokenDataset(correct, broken,
                    List.of(Mutation.removeEvent("request_sent").atOccurrence(occurrence))).directory());
            for (String field : List.of(LibTraceKeys.REQUEST_MODEL, "providerIndependentContextWindow", "providerDependentContextWindow")) {
                generated.add(mutator.createBrokenDataset(correct, broken,
                        List.of(Mutation.wrongFieldValue("request_sent", field).atOccurrence(occurrence))).directory());
            }
        }
        // First/last defects exercise behavior checks without removing all evidence of an event.
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("parameter_translated", List.of("propertyName", "propertyValue", "providerDependentTargetState"));
        fields.put("metadata_translated", List.of("propertyValue"));
        fields.put("response_translated", List.of("providerIndependentResponse", "providerDependentResponse"));
        fields.put("streaming_chunk_accumulated", List.of("accumulatedMessage"));
        fields.put("partial_response_callback_invoked", List.of("callbackArgument"));
        fields.put("complete_response_callback_invoked", List.of("callbackArgument"));
        for (var entry : fields.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), 0);
            if (count == 0) throw new IOException("Required event is absent: " + entry.getKey());
            Set<Integer> occurrences = new LinkedHashSet<>(List.of(1, count));
            for (String field : entry.getValue()) for (int occurrence : occurrences) {
                generated.add(mutator.createBrokenDataset(correct, broken,
                        List.of(Mutation.wrongFieldValue(entry.getKey(), field).atOccurrence(occurrence))).directory());
            }
        }
        // Each registered command must retain its own return evidence, including later occurrences.
        String resultEvent = CLITraceActions.Event.COMMAND_HANDLER_RESULTS.eventName();
        for (var commandEntry : commandReturns.entrySet()) {
            String command = commandEntry.getKey();
            var occurrences = commandEntry.getValue();
            Path missing = mutator.createBrokenDataset(correct, broken,
                    List.of(Mutation.removeEvent(resultEvent).atOccurrence(occurrences.get(0)))).directory();
            writeCommandExpectation(missing, command, "Command-result count");
            generated.add(missing);
            for (int occurrence : new LinkedHashSet<>(List.of(occurrences.get(0), occurrences.get(occurrences.size() - 1)))) {
                for (String field : List.of(CLITraceActions.COMMAND_RESULT, CLITraceActions.HANDLER_IDENTITY)) {
                    Path directory = mutator.createBrokenDataset(correct, broken,
                            List.of(Mutation.wrongFieldValue(resultEvent, field).atOccurrence(occurrence))).directory();
                    boolean identity = field.equals(CLITraceActions.HANDLER_IDENTITY);
                    writeCommandExpectation(directory, identity ? CLICommandResultMutations.HANDLER_INSTANCES_CHECK : command,
                            identity ? "Returning handler does not match preceding lookup" : "input [");
                    generated.add(directory);
                }
            }
        }
        for (var mutation : (sixCalls ? Map.<String,List<String>>of() : CLIPropertySetMutations.create(
                correctLines)).entrySet()) {
            Path directory = broken.resolve(mutation.getKey());
            Files.createDirectories(directory);
            Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME), mutation.getValue());
            generated.add(directory);
        }
        for (var entry : CLICommandResultMutations.create(
                Files.readAllLines(correct.resolve(CLITraceInformation.TRACE_FILE_NAME)), interactions, correct).entrySet()) {
            Path directory = broken.resolve(entry.getKey());
            Files.createDirectories(directory);
            var mutation = entry.getValue();
            Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME), mutation.lines());
            writeCommandExpectation(directory, mutation.command(), mutation.diagnostic());
            generated.add(directory);
        }
        List<String> architectureCoverage = new ArrayList<>(List.of("dataset\tcheck\trequired_diagnostic"));
        for (var entry : (sixCalls ? Map.<String,CLIArchitectureMutations.Mutation>of()
                : CLIArchitectureMutations.create(correctLines)).entrySet()) {
            Path directory = broken.resolve(entry.getKey());
            Files.createDirectories(directory);
            var mutation = entry.getValue();
            Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME), mutation.lines());
            String expectation = mutation.check().name() + "\t" + mutation.diagnostic();
            Files.writeString(directory.resolve(CLIArchitectureMutations.EXPECTATION_FILE), expectation + "\n");
            architectureCoverage.add(entry.getKey() + "\t" + expectation);
            generated.add(directory);
        }
        Files.write(broken.resolve("architecture-coverage.tsv"), architectureCoverage);
        if (sixCalls) {
            List<String> coverage=new ArrayList<>(List.of("dataset\tcheck"));
            for(var entry:CLIPropertyConsistencyMutations.create(correctLines).entrySet()) {
                Path directory=broken.resolve(entry.getKey());
                Files.createDirectories(directory);
                Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME),entry.getValue().lines());
                Files.writeString(directory.resolve(CLIPropertyConsistencyMutations.EXPECTATION_FILE),entry.getValue().check().name());
                coverage.add(entry.getKey()+"\t"+entry.getValue().check().name());
                generated.add(directory);
            }
            Files.write(broken.resolve("property-consistency-coverage.tsv"),coverage);
            List<String> choicesCoverage=new ArrayList<>(List.of("dataset\tcheck"));
            for(var entry:CLIPropertyChoicesMutations.create(correctLines,propertyPolicy).entrySet()) {
                Path directory=broken.resolve(entry.getKey());
                Files.createDirectories(directory);
                Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME),entry.getValue().lines());
                Files.writeString(directory.resolve(CLIPropertyChoicesMutations.EXPECTATION_FILE),entry.getValue().check().name());
                choicesCoverage.add(entry.getKey()+"\t"+entry.getValue().check().name());
                generated.add(directory);
            }
            Files.write(broken.resolve("property-choices-coverage.tsv"),choicesCoverage);
            List<String> commandCoverage=new ArrayList<>(List.of("dataset\tcheck"));
            for(var entry:CLICommandConsistencyMutations.create(correctLines).entrySet()) {
                Path directory=broken.resolve(entry.getKey());
                Files.createDirectories(directory);
                Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME),entry.getValue().lines());
                Files.writeString(directory.resolve(CLICommandConsistencyMutations.EXPECTATION_FILE),entry.getValue().check().name());
                commandCoverage.add(entry.getKey()+"\t"+entry.getValue().check().name());
                generated.add(directory);
            }
            Files.write(broken.resolve("command-consistency-coverage.tsv"),commandCoverage);
        }
        if (sixCalls) {
            List<String> coverage = new ArrayList<>(List.of("dataset\trequired_diagnostic"));
            for (var entry : CLISixCallMutations.create(correctLines).entrySet()) {
                Path directory = broken.resolve(entry.getKey());
                Files.createDirectories(directory);
                Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME), entry.getValue().lines());
                Files.writeString(directory.resolve(CLISixCallMutations.EXPECTATION_FILE), entry.getValue().diagnostic());
                coverage.add(entry.getKey()+"\t"+entry.getValue().diagnostic());
                generated.add(directory);
            }
            Files.write(broken.resolve("six-call-coverage.tsv"), coverage);
        }
        List<String> requestCoverage = new ArrayList<>(List.of("dataset\tcheck\trequired_diagnostic"));
        for (var entry : CLIRequestInputMutations.create(
                Files.readAllLines(correct.resolve(CLITraceInformation.TRACE_FILE_NAME)), interactions, correct).entrySet()) {
            Path directory = broken.resolve(entry.getKey());
            Files.createDirectories(directory);
            var mutation = entry.getValue();
            Files.write(directory.resolve(CLITraceInformation.TRACE_FILE_NAME), mutation.lines());
            String expectation = mutation.check().name() + "\t" + mutation.diagnostic();
            Files.writeString(directory.resolve(CLIRequestInputMutations.EXPECTATION_FILE), expectation + "\n");
            requestCoverage.add(entry.getKey() + "\t" + expectation);
            generated.add(directory);
        }
        Files.write(broken.resolve("request-input-coverage.tsv"), requestCoverage);
        List<String> commandCoverage = new ArrayList<>(List.of("dataset\tcommand\trequired_diagnostic"));
        for (Path directory : generated) {
            Path expectation = directory.resolve(CLICommandResultMutations.EXPECTATION_FILE);
            if (Files.exists(expectation)) commandCoverage.add(directory.getFileName() + "\t" + Files.readString(expectation).strip());
        }
        Files.write(broken.resolve("command-result-coverage.tsv"), commandCoverage);
        for (Path directory : generated) copySupportingFiles(correct, directory, supportingFiles);
        List<String> names = generated.stream().map(path -> path.getFileName().toString()).toList();
        for (String previous : previousDatasets) {
            if (!names.contains(previous)) removeObsoleteDataset(broken, previous);
        }
        Files.write(manifest, names);
        return new Result(filter, List.copyOf(generated));
    }

    private static void writeCommandExpectation(Path directory, String command, String diagnostic) throws IOException {
        Files.writeString(directory.resolve(CLICommandResultMutations.EXPECTATION_FILE), command + "\t" + diagnostic + "\n");
    }

    /** Prune only datasets recorded by the preceding generation, after the new generation succeeds. */
    private static void removeObsoleteDataset(Path root, String name) throws IOException {
        Path directory = root.resolve(name).normalize();
        if (!directory.getParent().equals(root) || Files.isSymbolicLink(directory))
            throw new IOException("Unsafe obsolete dataset path: " + directory);
        if (!Files.exists(directory)) return;
        if (!directory.toRealPath().startsWith(root.toRealPath()))
            throw new IOException("Obsolete dataset lies outside mutation root: " + directory);
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static void copySupportingFiles(Path source, Path target, List<Path> files) throws IOException {
        for (Path file : files) {
            Path destination = target.resolve(file).normalize();
            Files.createDirectories(destination.getParent());
            Files.copy(source.resolve(file), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
