package eduAI.trace;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ImageByteSummary(
		int count,
		String prefix,
		String suffix) {
	private static final int EDGE_BYTES = 8;
	private static final Pattern PATTERN = Pattern.compile(
			"imageBytes\\(count=(\\d+),prefix=([0-9a-f]*),suffix=([0-9a-f]*)\\)");

	public static ImageByteSummary from(byte[] someBytes) {
		byte[] bytes = someBytes == null ? new byte[0] : someBytes;
		int edge = Math.min(EDGE_BYTES, bytes.length);
		return new ImageByteSummary(
				bytes.length,
				hex(Arrays.copyOfRange(bytes, 0, edge)),
				hex(Arrays.copyOfRange(bytes, bytes.length - edge, bytes.length)));
	}

	public static ImageByteSummary parseFirst(String aText) {
		if (aText == null) {
			return null;
		}
		Matcher matcher = PATTERN.matcher(aText);
		return matcher.find()
				? new ImageByteSummary(
						Integer.parseInt(matcher.group(1)),
						matcher.group(2),
						matcher.group(3))
				: null;
	}

	public static ImageByteSummary parseFirstNonempty(String aText) {
		if (aText == null) {
			return null;
		}
		Matcher matcher = PATTERN.matcher(aText);
		while (matcher.find()) {
			ImageByteSummary summary = new ImageByteSummary(
					Integer.parseInt(matcher.group(1)),
					matcher.group(2),
					matcher.group(3));
			if (summary.count() > 0) {
				return summary;
			}
		}
		return null;
	}

	public String formatted() {
		return "imageBytes(count=" + count
				+ ",prefix=" + prefix
				+ ",suffix=" + suffix + ")";
	}

	private static String hex(byte[] someBytes) {
		StringBuilder result = new StringBuilder(someBytes.length * 2);
		for (byte value : someBytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}
}
