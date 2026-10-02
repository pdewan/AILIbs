package eduAI.trace._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.GenericTrace;
import eduAI.trace.lib.LibTraceActions;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.GenericTracesFileProcessor;

/** Exercises production emission, disk serialization, parsing, and the merge checker. */
public final class CompactStreamingEndToEndTest {
	private record TextPart(String text) { }
	private enum Role { ASSISTANT, SYSTEM }
	private record Message(Role role, List<TextPart> parts) { }
	private static Message message(String text) { return new Message(Role.ASSISTANT, List.of(new TextPart(text))); }
	public static void main(String[] args) throws Exception {
		Path directory = Path.of("build/trace-printer-review/compact-streaming-e2e");
		Files.createDirectories(directory);
		GenericTrace.setOutputEnabled(false);
		String[] chunks = {"abcdefghij first middle klmnopqrst", "uvwxyzABCD second middle EFGHIJKLMN", "0123456789 third middle 9876543210"};
		for (String provider : List.of("Gemini", "Ollama")) {
			for (String fault : List.of("correct", "dropped", "duplicated", "reordered", "altered", "bad-final")) {
				Path file = directory.resolve(provider + "-" + fault + ".txt");
				GenericTrace.setTraceFile(file);
				LibTraceActions.traceServerHandleFactoryFetched(CompactStreamingEndToEndTest.class, provider, new Object(), new Object());
				LibTraceActions.traceStreamingChunksMerged(CompactStreamingEndToEndTest.class, new Object(),
						new Message(Role.SYSTEM, List.of(new TextPart("System instruction with more than twenty characters"))));
				Object merger = new Object();
				String accumulated = "";
				for (int i = 0; i < chunks.length; i++) {
					accumulated += chunks[i];
					String actual = accumulated;
					if (i == 1) actual = switch (fault) {
						case "dropped" -> chunks[0];
						case "duplicated" -> accumulated + chunks[1];
						case "reordered" -> chunks[1] + chunks[0];
						case "altered" -> accumulated.replace("second", "SECOND");
						default -> accumulated;
					};
					LibTraceActions.traceStreamingChunkAccumulated(CompactStreamingEndToEndTest.class,
							merger, message(chunks[i]), message(actual));
				}
				LibTraceActions.traceStreamingChunkAccumulated(CompactStreamingEndToEndTest.class, merger,
						new Message(Role.ASSISTANT, java.util.Arrays.asList((TextPart) null)), message(accumulated));
				LibTraceActions.traceStreamingChunksMerged(CompactStreamingEndToEndTest.class,
						merger, message(fault.equals("bad-final") ? accumulated.replace("second", "SECOND") : accumulated));
				GenericTrace.clearTraceFile();
				String serialized = Files.readString(file);
				if (!serialized.contains("@text:") || !serialized.contains("expectedMergedTextSummary"))
					throw new AssertionError("Missing compact/hash evidence");
				verify(provider, file, fault.equals("correct"));
				if (fault.equals("correct")) {
					List<String> lines = Files.readAllLines(file);
					for (String mutation : List.of("deleted-record", "duplicated-record", "reordered-record")) {
						List<String> changed = new ArrayList<>(lines);
						if (mutation.equals("deleted-record")) changed.remove(3);
						if (mutation.equals("duplicated-record")) changed.add(3, changed.get(3));
						if (mutation.equals("reordered-record")) Collections.swap(changed, 2, 3);
						Path mutated = directory.resolve(provider + "-" + mutation + ".txt");
						Files.write(mutated, changed);
						verify(provider, mutated, false);
					}
				}
			}
		}
		System.out.println("CompactStreamingEndToEndTest passed: 2 correct streams and 16 corrupted streams");
	}
	private static void verify(String provider, Path file, boolean expected) {
		GenericTracesFileProcessor processor = new GenericTracesFileProcessor();
		processor.initializeTraceProcessing();
		processor.processTraceFile(provider, StreamingMode.STREAMING, file);
		var result = processor.checkStreamingChunksAccumulatedCorrectly(provider);
		if (result.passed() != expected) throw new AssertionError(file + " expected pass=" + expected + " actual=" + result);
		if (expected && result.level3Messages().stream().noneMatch(s -> s.contains("final=true match=true")))
			throw new AssertionError("No final hash comparison executed: " + result);
	}
}
