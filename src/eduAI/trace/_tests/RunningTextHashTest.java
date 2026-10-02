package eduAI.trace._tests;

import eduAI.trace.RunningTextHash;
import eduAI.trace.TraceTextSummary;

public final class RunningTextHashTest {
	public static void main(String[] args) {
		String text = "first line\nHello 😀 world\t \"quoted\" \\ ending";
		for (int split = 0; split <= text.length(); split++) {
			RunningTextHash hash = new RunningTextHash();
			hash.append(text.substring(0, split));
			require(hash.snapshot().equals(TraceTextSummary.from(text.substring(0, split))), "prefix " + split);
			hash.append(text.substring(split));
			require(hash.snapshot().equals(TraceTextSummary.from(text)), "split " + split);
		}
		RunningTextHash hash = new RunningTextHash();
		for (char c : text.toCharArray()) hash.append(String.valueOf(c));
		require(hash.snapshot().equals(TraceTextSummary.from(text)), "single UTF-16 units");
		require(hash.snapshot().equals(hash.snapshot()), "snapshot consumes state");
		RunningTextHash other = new RunningTextHash();
		other.append("different");
		require(!other.snapshot().sha256().equals(hash.snapshot().sha256()), "stream isolation");
		String malformed = "x\uD800y\uDC00z\uD800";
		RunningTextHash invalid = new RunningTextHash();
		for (char c : malformed.toCharArray()) invalid.append(String.valueOf(c));
		require(invalid.snapshot().equals(TraceTextSummary.from(malformed)), "malformed UTF-16");
		require(new RunningTextHash().snapshot().equals(TraceTextSummary.from("")), "empty stream");
		System.out.println("RunningTextHashTest passed");
	}
	private static void require(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
