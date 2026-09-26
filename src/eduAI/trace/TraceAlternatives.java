package eduAI.trace;

import java.util.ArrayList;
import java.util.List;

public final class TraceAlternatives {
	public static final String ALL = "all";

	private TraceAlternatives() {
	}

	@SafeVarargs
	public static List<String> fromEnums(
			Class<? extends Enum<?>>... anEnumClasses) {
		List<String> result = new ArrayList<>();
		if (anEnumClasses == null) {
			return result;
		}
		for (Class<? extends Enum<?>> enumClass : anEnumClasses) {
			addEnumConstants(result, enumClass);
		}
		return result;
	}

	@SafeVarargs
	public static List<String> fromEnumsWithAll(
			Class<? extends Enum<?>>... anEnumClasses) {
		List<String> result = new ArrayList<>();
		result.add(ALL);
		result.addAll(fromEnums(anEnumClasses));
		return result;
	}

	public static List<String> fromValues(
			Enum<?>... aValues) {
		List<String> result = new ArrayList<>();
		if (aValues == null) {
			return result;
		}
		for (Enum<?> value : aValues) {
			result.add(TraceNames.token(value));
		}
		return result;
	}

	private static void addEnumConstants(
			List<String> aResult,
			Class<? extends Enum<?>> anEnumClass) {
		if (anEnumClass == null) {
			return;
		}
		for (Enum<?> value : anEnumClass.getEnumConstants()) {
			aResult.add(TraceNames.token(value));
		}
	}
}
