package eduAI.trace;

import java.util.regex.Pattern;

/** Reads enum and String role fields from the same reflective message format. */
public final class TraceMessageRole {
	private static final Pattern ROLE = Pattern.compile(
			"\\brole:\\s+[^,}]+?\\s+value=(?:\"([A-Za-z_]+)\"|([A-Za-z_]+)(?=[,}\\s]|$))");

	private TraceMessageRole() { }

	public static String fromDump(String dump) {
		if (dump == null) return "";
		var matcher = ROLE.matcher(dump);
		if (!matcher.find()) return "";
		return matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
	}
}
