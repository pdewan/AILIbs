package eduAI.trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GenericTrace {
	public static final String TRACE_PREFIX = "##";
	public static final String TRACE_SUFFIX = "##";
	private static final Object PRINT_LOCK = new Object();
	private static volatile boolean enabled = true;
	private static volatile boolean outputEnabled = true;
	private static volatile boolean fileEnabled = false;
	private static volatile Path traceFile;
	private static volatile boolean traceFileNeedsFreshStart = false;

	private GenericTrace() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean isEnabled) {
		enabled = isEnabled;
	}

	public static boolean isOutputEnabled() {
		return outputEnabled;
	}

	public static void setOutputEnabled(boolean isEnabled) {
		outputEnabled = isEnabled;
	}

	public static boolean isFileEnabled() {
		return fileEnabled;
	}

	public static void setFileEnabled(boolean isEnabled) {
		fileEnabled = isEnabled;
	}

	public static Path getTraceFile() {
		return traceFile;
	}

	public static void setTraceFile(String aTraceFile) {
		if (aTraceFile == null || aTraceFile.isBlank()) {
			clearTraceFile();
			return;
		}
		setTraceFile(Path.of(aTraceFile));
	}

	public static void setTraceFile(Path aTraceFile) {
		traceFile = aTraceFile;
		fileEnabled = aTraceFile != null;
		traceFileNeedsFreshStart = aTraceFile != null;
	}

	public static void clearTraceFile() {
		traceFile = null;
		fileEnabled = false;
		traceFileNeedsFreshStart = false;
	}

	public static void deleteTraceFile() {
		if (traceFile == null) {
			return;
		}
		try {
			Files.deleteIfExists(traceFile);
		} catch (IOException e) {
			throw new UncheckedIOException(
					"Could not delete trace file: " + traceFile,
					e);
		}
	}

	public static void resetTraceDestinations() {
		outputEnabled = true;
		clearTraceFile();
	}

	public static void traceThreadPattern(Enum<?> aThreadPatternName, Class<?> aSourceClass, Enum<?> anEventName,
			Map<String, String> anAuxiliaryData) {
		require(aThreadPatternName, "thread pattern");
		tracePattern(aThreadPatternName, null, aSourceClass, anEventName, anAuxiliaryData);
	}

	public static void traceDesignPattern(Enum<?> aDesignPatternName, Class<?> aSourceClass, Enum<?> anEventName,
			Map<String, String> anAuxiliaryData) {
		require(aDesignPatternName, "design pattern");
		tracePattern(null, aDesignPatternName, aSourceClass, anEventName, anAuxiliaryData);
	}

	public static void tracePattern(Enum<?> aThreadPatternName, Enum<?> aDesignPatternName, Class<?> aSourceClass,
			Enum<?> anEventName, Map<String, String> anAuxiliaryData) {
		require(aSourceClass, "source class");
		require(anEventName, "event name");
		if (!enabled) {
			return;
		}
		String line = formatLine(aThreadPatternName, aDesignPatternName, aSourceClass, anEventName, anAuxiliaryData);
		synchronized (PRINT_LOCK) {
			writeLine(line);
		}
	}

	private static void writeLine(String aLine) {
		if (outputEnabled) {
			System.out.println();
			System.out.println(aLine);
			System.out.flush();
		}
		if (fileEnabled && traceFile != null) {
			writeFileLine(aLine);
		}
	}

	private static void writeFileLine(String aLine) {
		try {
			Path parent = traceFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			if (traceFileNeedsFreshStart) {
				Files.write(
						traceFile,
						(aLine + System.lineSeparator())
								.getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE);
				traceFileNeedsFreshStart = false;
			} else {
				Files.write(
						traceFile,
						(aLine + System.lineSeparator())
								.getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE,
						StandardOpenOption.APPEND);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(
					"Could not write trace file: " + traceFile,
					e);
		}
	}

	private static String formatLine(Enum<?> aThreadPatternName, Enum<?> aDesignPatternName, Class<?> aSourceClass,
			Enum<?> anEventName, Map<String, String> anAuxiliaryData) {
		String layerName = layerName(anEventName, aThreadPatternName, aDesignPatternName);
		String eventName = TraceNames.token(anEventName);
		String threadPatternName = optionalName(aThreadPatternName);
		String designPatternName = optionalName(aDesignPatternName);
		String threadName = Thread.currentThread().getName();
		String sourceClassName = sourceClassName(aSourceClass);
		Map<String, String> data = cleanAuxiliaryData(anAuxiliaryData);
		StringBuilder builder = new StringBuilder();
		builder.append(TRACE_PREFIX).append(" ").append(layerName).append(" {").append(eventName).append("}");
		builder.append(" [").append(threadPatternName).append(": ").append(threadName).append("]");
		builder.append(" (").append(designPatternName).append(": ").append(sourceClassName).append(")");
		if (!data.isEmpty()) {
			builder.append(" <").append(formatAuxiliaryData(data)).append(">");
		}
		builder.append(" ").append(TRACE_SUFFIX);
		return builder.toString();
	}

	private static String sourceClassName(Class<?> aSourceClass) {
		if (aSourceClass == null) {
			return "";
		}
		String canonicalName = aSourceClass.getCanonicalName();
		return canonicalName == null
				? aSourceClass.getName()
				: canonicalName;
	}

	private static Map<String, String> cleanAuxiliaryData(Map<String, String> anAuxiliaryData) {
		Map<String, String> result = new LinkedHashMap<>();
		if (anAuxiliaryData != null) {
			for (Map.Entry<String, String> entry : anAuxiliaryData.entrySet()) {
				String key = key(entry.getKey());
				if (key.isEmpty()) {
					continue;
				}
				result.put(key, entry.getValue() == null ? "" : entry.getValue());
			}
		}
		return result;
	}

	private static String layerName(Enum<?>... aTraceEnums) {
		String result = "";
		if (aTraceEnums != null) {
			for (Enum<?> traceEnum : aTraceEnums) {
				String current = layerName(traceEnum);
				if (current.isEmpty()) {
					continue;
				}
				if (result.isEmpty()) {
					result = current;
				} else if (!result.equals(current)) {
					throw new IllegalArgumentException(
							"Trace enums must come from one layer: " + result + ", " + current);
				}
			}
		}
		return result;
	}

	private static void require(Object aValue, String aName) {
		if (aValue == null) {
			throw new IllegalArgumentException("Trace " + aName + " must not be null");
		}
	}

	private static String layerName(Enum<?> aTraceEnum) {
		if (aTraceEnum == null) {
			return "";
		}
		Package enumPackage = aTraceEnum.getClass().getPackage();
		String packageName = enumPackage == null ? "" : enumPackage.getName();
		int lastDot = packageName.lastIndexOf('.');
		String name = lastDot < 0 ? packageName : packageName.substring(lastDot + 1);
		return cleanToken(name);
	}

	private static String optionalName(Enum<?> aTraceName) {
		return aTraceName == null ? "none" : TraceNames.token(aTraceName);
	}

	private static String key(String aKey) {
		String key = aKey == null ? "" : aKey.trim();
		if (key.isEmpty()) {
			return "";
		}
		return cleanToken(key);
	}

	private static String cleanToken(String aToken) {
		String token = aToken == null ? "" : aToken.trim();
		if (token.isEmpty()) {
			throw new IllegalArgumentException("Trace token must not be empty");
		}
		for (int index = 0; index < token.length(); index++) {
			char ch = token.charAt(index);
			if (Character.isWhitespace(ch) || "{}[]()<>=,\"'".indexOf(ch) >= 0) {
				throw new IllegalArgumentException(
						"Trace token must contain no whitespace " + "or trace delimiters: " + token);
			}
		}
		return token;
	}

	static String formatAuxiliaryData(Map<String, String> anAuxiliaryData) {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> entry : anAuxiliaryData.entrySet()) {
			if (builder.length() > 0) {
				builder.append(" ");
			}
			builder.append(entry.getKey()).append("=\"").append(escapeValue(entry.getValue())).append("\"");
		}
		return builder.toString();
	}

	private static String escapeValue(String aValue) {
		String value = aValue == null ? "" : aValue;
		return value.replace("\\", "\\\\").replace("##", "\\x23\\x23").replace("\"", "\\\"").replace("<", "\\x3c").replace(">", "\\x3e")
				.replace("\r", "\\r").replace("\n", "\\n");
	}
}
