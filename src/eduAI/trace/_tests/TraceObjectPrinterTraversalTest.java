package eduAI.trace._tests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.TraceTextSummary;

/** Regression coverage for complete grading evidence through wrapped object graphs. */
public final class TraceObjectPrinterTraversalTest {
	private static final class Node {
		Object value;
		Node(Object value) { this.value = value; }
	}
	private static final class Pair {
		Object first;
		Object second;
		Pair(Object value) { first = value; second = value; }
	}
	public static void main(String[] args) {
		Object nested = "deep-leaf";
		require(TraceObjectPrinter.format(java.nio.file.Path.of("a", "b")).contains("value=<opaque>"),
				"runtime Path recursively expanded as a collection");
		for (int i = 0; i < 20; i++) nested = new Node(List.of(nested));
		String dump = TraceObjectPrinter.format("context", nested);
		require(dump.contains("value=\"deep-leaf\""), "nested leaf omitted");
		require(!dump.contains("<max-depth>"), "depth cutoff remains");
		List<Node> messages = new ArrayList<>();
		Map<String, Node> properties = new LinkedHashMap<>();
		for (int i = 0; i < 24; i++) {
			messages.add(new Node("message-" + i));
			properties.put("key-" + i, new Node("property-" + i));
		}
		for (Object collection : List.of(messages, messages.toArray(), properties)) {
			String all = TraceObjectPrinter.format("collection", collection);
			require(all.contains("value=\"" + (collection == properties ? "property-23" : "message-23") + "\""), "later element omitted");
			require(!all.contains(", ..."), "element cutoff remains");
		}
		Node cyclic = new Node(null);
		cyclic.value = cyclic;
		require(TraceObjectPrinter.format(cyclic).contains("value=<cycle>"), "cycle not detected");
		String shared = TraceObjectPrinter.format(new Pair(new Node("shared-leaf")));
		require(shared.split("value=\"shared-leaf\"", -1).length == 3, "shared reference treated as cycle");
		TraceTextSummary a = TraceTextSummary.from("abcdefghij-middle-klmnopqrst");
		TraceTextSummary b = TraceTextSummary.from("abcdefghij-MIDDLE-klmnopqrst");
		require(a.prefix().equals("abcdefghij") && a.suffix().equals("klmnopqrst"), "wrong edges");
		require(a.length() == b.length() && !a.sha256().equals(b.sha256()), "middle not hashed");
		require(a.equals(TraceTextSummary.from("abcdefghij-middle-klmnopqrst")), "unstable hash");
		require(TraceTextSummary.from("abc").sha256().equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "not SHA-256 UTF-8");
		TraceTextSummary unicode = TraceTextSummary.from("😀".repeat(25));
		require(unicode.length() == 25 && unicode.prefix().codePointCount(0, unicode.prefix().length()) == 10,
				"Unicode character split");
		String longText = "start-" + "complete-content-".repeat(50) + "end";
		String container = TraceObjectPrinter.format(List.of(longText));
		require(container.contains("toStringSummary=textSummary("), "missing compact description");
		require(container.contains("value=\"" + TraceTextSummary.from(longText).token() + "\""), "text summary lost");
		System.out.println("TraceObjectPrinterTraversalTest passed");
	}
	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
