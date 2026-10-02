package eduAI.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Incremental SHA-256 of concatenated UTF-8 text, without retaining the full text. */
public final class RunningTextHash {
	private final MessageDigest digest;
	private final StringBuilder prefix = new StringBuilder();
	private final StringBuilder suffix = new StringBuilder();
	private int length;
	private char pendingHigh;

	public RunningTextHash() {
		try { digest = MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	public void append(String text) {
		java.util.Objects.requireNonNull(text, "text");
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			// Preserve a surrogate pair even when the callback splits it in two.
			if (pendingHigh != 0) {
				if (Character.isLowSurrogate(c)) {
					accept(new String(new char[] {pendingHigh, c}));
					pendingHigh = 0;
					continue;
				}
				accept(String.valueOf(pendingHigh));
				pendingHigh = 0;
			}
			if (Character.isHighSurrogate(c)) pendingHigh = c;
			else accept(String.valueOf(c));
		}
	}

	private void accept(String character) {
		digest.update(character.getBytes(StandardCharsets.UTF_8));
		length = Math.addExact(length, 1);
		if (prefix.codePointCount(0, prefix.length()) < 10) prefix.append(character);
		suffix.append(character);
		if (suffix.codePointCount(0, suffix.length()) > 10)
			suffix.delete(0, suffix.offsetByCodePoints(0, 1));
	}

	/** Non-destructive snapshot; subsequent append operations continue this stream. */
	public TraceTextSummary snapshot() {
		try {
			MessageDigest copy = (MessageDigest) digest.clone();
			String head = prefix.toString();
			String tail = suffix.toString();
			int count = length;
			if (pendingHigh != 0) {
				String pending = String.valueOf(pendingHigh);
				copy.update(pending.getBytes(StandardCharsets.UTF_8));
				count = Math.addExact(count, 1);
				if (head.codePointCount(0, head.length()) < 10) head += pending;
				tail += pending;
				if (tail.codePointCount(0, tail.length()) > 10)
					tail = tail.substring(tail.offsetByCodePoints(0, 1));
			}
			return new TraceTextSummary(count, head, tail, HexFormat.of().formatHex(copy.digest()));
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException("SHA-256 provider must support independent snapshots", e);
		}
	}
}
