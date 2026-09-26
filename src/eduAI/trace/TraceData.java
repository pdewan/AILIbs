package eduAI.trace;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TraceData {
	private TraceData() {
	}

	public static Map<String, String> data(String... aKeysAndValues) {
		Map<String, String> result = new LinkedHashMap<>();
		if (aKeysAndValues == null) {
			return result;
		}
		for (int index = 0; index + 1 < aKeysAndValues.length; index += 2) {
			String key = aKeysAndValues[index];
			if (key == null || key.trim().isEmpty()) {
				continue;
			}
			result.put(
					key,
					value(aKeysAndValues[index + 1]));
		}
		return result;
	}

	public static Map<String, String> limitedData(
			Map<String, String> aData,
			int aMaximumValueLength) {
		Map<String, String> result = new LinkedHashMap<>();
		if (aData != null) {
			for (Map.Entry<String, String> entry : aData.entrySet()) {
				result.put(
						entry.getKey(),
						limitedValue(
								entry.getValue(),
								aMaximumValueLength));
			}
		}
		return result;
	}

	public static String limitedValue(
			String aValue,
			int aMaximumValueLength) {
		String value = value(aValue).trim();
		if (aMaximumValueLength < 0
				|| value.length() <= aMaximumValueLength) {
			return value;
		}
		return value.substring(
				0,
				Math.max(0, aMaximumValueLength))
				+ "...";
	}

	private static String value(String aValue) {
		return aValue == null ? "" : aValue;
	}
}
