package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import eduAI.trace.TraceTextSummary;
import eduAI.trace.processors.*;

/** Changes valid summary tokens in fresh demo traces; never substitutes full text. */
public final class CompactPromptMutationTest {
	private static final List<String> FILES = List.of("TraceGeminiBridgeNonStreamingDemo.txt",
			"TraceGeminiBridgeStreamingDemo.txt", "TraceOllamaNonStreamingBridgeDemo.txt", "TraceOllamaStreamingBridgeDemo.txt");
	private static final Pattern SYSTEM = Pattern.compile("@text:(\\d+):([A-Za-z0-9_-]*):([A-Za-z0-9_-]*):(?=[^0-9a-f]|$)");
	public static void main(String[] args) throws Exception {
		Path source = Path.of(args[0]);
		Path output = Path.of("build/trace-printer-review/compact-prompt-mutations");
		for (String provider : List.of("Gemini", "Ollama")) {
			for (String mutation : List.of("system-history", "expected-system", "compaction")) {
				Path target = output.resolve(provider + "-" + mutation);
				Files.createDirectories(target);
				for (String name : FILES) Files.copy(source.resolve(name), target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
				String name = provider.equals("Gemini") ? FILES.get(0) : FILES.get(2);
				List<String> lines = Files.readAllLines(target.resolve(name));
				int request = 0, changes = 0;
				String summary = TraceTextSummary.from("Summarize our conversation and remember that summary as the new context for future questions.").token();
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i);
					if (!line.contains("{request_sent}")) continue;
					request++;
					if (mutation.equals("system-history") && request != 2) continue;
					String changed;
					if (mutation.equals("compaction")) {
						String invalid = summary.substring(0, summary.length()-1) + (summary.endsWith("0") ? "1" : "0");
						changed = line.replace(summary, invalid);
					} else {
						Matcher matcher = SYSTEM.matcher(line);
						StringBuilder result = new StringBuilder();
						while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement("@text:"
								+ (Integer.parseInt(matcher.group(1)) + 1) + ":" + matcher.group(2) + ":" + matcher.group(3) + ":"));
						matcher.appendTail(result);
						changed = result.toString();
					}
					if (!changed.equals(line)) { lines.set(i, changed); changes++; }
				}
				if (changes == 0) throw new AssertionError("Mutation did not change compact evidence: " + target);
				Files.write(target.resolve(name), lines);
				BridgeDemoTraceFilesProcessor processor = new BridgeDemoTraceFilesProcessor();
				processor.setTraceDirectory(target.toString());
				processor.processTraceFiles();
				TraceCheckResult result = switch (mutation) {
					case "system-history" -> processor.checkPreviousSystemPromptsRetained(provider);
					case "expected-system" -> processor.checkExpectedPrompts();
					default -> processor.checkExpectedContextWindowCompactions(provider);
				};
				Files.write(target.resolve("check-results.txt"), result.level3Messages());
				if (result.passed()) throw new AssertionError("Corrupted compact prompt accepted: " + target);
				System.out.println(provider + " " + mutation + " rejected");
			}
		}
	}
}
