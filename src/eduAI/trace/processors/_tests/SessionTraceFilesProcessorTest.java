package eduAI.trace.processors._tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import eduAI.trace.TraceLineParser;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.*;

/** Uses existing demo fixtures to exercise chronological loading without model calls. */
public final class SessionTraceFilesProcessorTest {
	private record Expected(String provider, StreamingMode mode, int line) { }

	public static void main(String[] args) throws Exception {
		Path fixtures = Path.of(args[0]);
		Path directory = Files.createTempDirectory("cli-session-trace-");
		List<String> lines = new ArrayList<>();
		List<Expected> expected = new ArrayList<>();
		append(fixtures, "TraceGeminiBridgeNonStreamingDemo.txt", "Gemini", StreamingMode.NON_STREAMING, false, lines, expected);
		append(fixtures, "TraceOllamaStreamingBridgeDemo.txt", "Ollama", StreamingMode.STREAMING, false, lines, expected);
		append(fixtures, "TraceGeminiBridgeStreamingDemo.txt", "Gemini", StreamingMode.STREAMING, false, lines, expected);
		append(fixtures, "TraceOllamaNonStreamingBridgeDemo.txt", "Ollama", StreamingMode.NON_STREAMING, false, lines, expected);
		append(fixtures, "TraceGeminiBridgeNonStreamingDemo.txt", "Gemini", StreamingMode.NON_STREAMING, true, lines, expected);
		Path trace = directory.resolve("CLITrace.txt");
		Files.write(trace, lines);
		verify(trace, expected);
		Path compressed = directory.resolve("CLITrace.txt.gz");
		try (var output = new GZIPOutputStream(Files.newOutputStream(compressed))) {
			Files.copy(trace, output);
		}
		verify(compressed, expected);
		String factory = lines.stream().filter(line -> line.contains("{server_handle_factory_fetched}"))
				.findFirst().orElseThrow();
		List<String> conflicting = new ArrayList<>(lines);
		conflicting.add(factory.replace("provider=\"gemini\"", "provider=\"ollama\""));
		Path conflict = directory.resolve("conflicting-provider.txt");
		Files.write(conflict, conflicting);
		if (new NativeTraceFilesProcessor(conflict).checkProviderNamesConsistent().passed()) {
			throw new AssertionError("One factory identity accepted for conflicting providers");
		}
		TraceFilesProcessor absent = new NativeTraceFilesProcessor(directory.resolve("absent.txt"));
		if (absent.checkTraceFilesExist().passed()) throw new AssertionError("Missing session accepted");
		System.out.println("SessionTraceFilesProcessorTest passed: " + expected.size() + " ordered events, plain and gzip");
	}

	private static void append(Path fixtures, String name, String provider, StreamingMode mode,
			boolean cachedFactory, List<String> lines, List<Expected> expected) throws Exception {
		for (String line : Files.readAllLines(fixtures.resolve(name))) {
			var parsed = TraceLineParser.parse(line);
			// Revisit a previously established handle without repeating its factory lookup.
			if (cachedFactory && parsed != null && "server_handle_factory_fetched".equals(parsed.getEventName())) {
				continue;
			}
			lines.add(line);
			if (parsed != null) expected.add(new Expected(provider, mode, lines.size()));
		}
	}

	private static void verify(Path trace, List<Expected> expected) {
		TraceFilesProcessor processor = new NativeTraceFilesProcessor(trace);
		if (!processor.checkTraceFilesExist().passed()) throw new AssertionError("Session missing");
		// Dependent checks must trigger session loading just as they do for demo files.
		if (!processor.checkProviderNamesConsistent().passed()) throw new AssertionError("Valid provider switch rejected");
		List<TraceRecord> records = processor.getTraceRecords().values().stream()
				.sorted(Comparator.comparingInt(record -> record.getId().sequenceNumber())).toList();
		if (records.size() != expected.size()) throw new AssertionError("Events lost or duplicated");
		for (int index = 0; index < records.size(); index++) {
			var record = records.get(index);
			var want = expected.get(index);
			if (!want.provider().equals(record.getId().provider()) || want.mode() != record.getId().mode()
					|| record.getId().sequenceNumber() != index) {
				throw new AssertionError("Incorrect session context at " + want.line() + ": " + record.getId());
			}
		}
        try {
            processor.checkExpectedPrompts();
            throw new AssertionError("Expected prompts ran without an explicitly configured checker");
        } catch (IllegalStateException expectedFailure) {
            if (!expectedFailure.getMessage().contains("configured explicitly")) throw expectedFailure;
        }
	}
}
