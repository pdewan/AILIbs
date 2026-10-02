package eduAI.trace._tests;

import java.util.List;
import eduAI.trace.CompactNativeText;
import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.TraceTextSummary;

/** System instructions retain edges and length; ordinary message text retains a hash. */
public final class SystemPromptSummaryTest {
	private record Part(String text) { }
	private record Message(String role, List<Part> parts) { }
	public static void main(String[] args) {
		String prompt = "abcdefghij" + "middle".repeat(10000) + "klmnopqrst";
		TraceTextSummary edges = TraceTextSummary.fromToken(TraceTextSummary.compact(prompt, true));
		require(edges != null && edges.sha256().isEmpty(), "system prompt contains a hash");
		require(edges.length() == prompt.length() && edges.prefix().equals("abcdefghij")
				&& edges.suffix().equals("klmnopqrst"), "incorrect edges or length");
		String system = TraceObjectPrinter.format(new Message("SYSTEM", List.of(new Part(prompt))));
		require(system.contains(edges.token()), "reflective system prompt not summarized");
		require(!system.contains(prompt) && !system.contains("sha256="), "system prompt retains full text or hash");
		var collector = new eduAI.trace.processors.ReflectiveValueCollector();
		require(collector.matchingValueOffset(system, prompt, java.util.Set.of()) >= 0,
				"checker does not recognize summarized system prompt");
		require(collector.matchingValueOffset(system, prompt + "x", java.util.Set.of()) < 0,
				"checker accepts wrong system prompt length");
		String user = TraceObjectPrinter.format(new Message("USER", List.of(new Part(prompt))));
		require(user.contains(TraceTextSummary.from(prompt).token()), "ordinary message lost hash");
		String nativeDump = CompactNativeText.format("text=Optional[" + prompt + "], thought=Optional.empty", true);
		require(nativeDump.contains(edges.token()) && !nativeDump.contains(prompt), "native system prompt not summarized");
		String changed = prompt.replace("middle", "MIDDLE");
		require(TraceTextSummary.matches(edges.token(), changed), "edges-only contract unexpectedly compares the middle");
		require(!TraceTextSummary.matches(edges.token(), prompt + "x"), "changed length accepted");
		require(!TraceTextSummary.matches(TraceTextSummary.from(prompt).token(), changed), "ordinary message middle change accepted");
		String combined = TraceTextSummary.concatenateEdges(List.of(
				TraceTextSummary.compact(prompt, true), TraceTextSummary.compact(prompt, true)));
		require(TraceTextSummary.matches(combined, prompt + prompt), "split system prompt concatenation differs");
		System.out.println("SystemPromptSummaryTest passed");
	}
	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
