package eduAI.trace._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.NativeTraceFilesProcessor;

/** Runs merge checks on real demo traces, then corrupts their chunk record sequence. */
public final class FreshStreamingMergeCheck {
	public static void main(String[] args) throws Exception {
		Path directory = Path.of(args[0]);
		for (String provider : args.length > 1 ? List.of(args[1]) : List.of("Gemini", "Ollama")) {
			Path file = directory.resolve(provider.equals("Gemini")
					? "TraceGeminiBridgeStreamingDemo.txt" : "TraceOllamaStreamingBridgeDemo.txt");
			verify(provider, file, true);
			List<String> lines = Files.readAllLines(file);
			List<Integer> chunks = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains("{streaming_chunk_accumulated}")) chunks.add(i);
			if (chunks.size() < 3) throw new AssertionError("Too few real chunks");
			for (String mutation : List.of("dropped", "duplicated", "reordered", "altered-final-hash")) {
				List<String> changed = new ArrayList<>(lines);
				int index = chunks.get(1);
				switch (mutation) {
					case "dropped" -> changed.remove(index);
					case "duplicated" -> changed.add(index, changed.get(index));
					case "reordered" -> Collections.swap(changed, chunks.get(0), index);
					case "altered-final-hash" -> {
						boolean altered = false;
						for (int i = 0; i < changed.size(); i++) {
							String line = changed.get(i);
							if (!line.contains("{streaming_chunks_merged}") || !line.contains("streamHashId=")) continue;
							int field = line.indexOf("mergedTextSummary=");
							int hash = line.indexOf("sha256=", field) + 7;
							if (field < 0 || hash < 7) throw new AssertionError("Missing final hash");
							changed.set(i, line.substring(0, hash) + (line.charAt(hash) == '0' ? '1' : '0') + line.substring(hash + 1));
							altered = true; break;
						}
						if (!altered) throw new AssertionError("No final merge");
					}
				}
				Path target = directory.resolve(provider + "-" + mutation + ".txt");
				Files.write(target, changed);
				verify(provider, target, false);
			}
		}
	}
	private static void verify(String provider, Path file, boolean expected) throws Exception {
		NativeTraceFilesProcessor processor = new NativeTraceFilesProcessor();
		processor.initializeTraceProcessing();
		processor.processTraceFile(provider, StreamingMode.STREAMING, file);
		var result = processor.checkStreamingChunksAccumulatedCorrectly(provider);
		Files.write(file.resolveSibling(file.getFileName() + ".merge-results.txt"), result.level3Messages());
		if (result.passed() != expected) throw new AssertionError(file + " expected=" + expected
				+ " errors=" + result.level3ErrorMessages().stream().limit(3).toList());
		long finals = result.level3Messages().stream().filter(s -> s.contains("final=true match=true")).count();
		if (expected && finals < 4) throw new AssertionError("Fewer than four final merge checks: " + finals);
		System.out.println(file.getFileName() + " expected=" + expected + " actual=" + result.passed() + " finalMerges=" + finals);
	}
}
