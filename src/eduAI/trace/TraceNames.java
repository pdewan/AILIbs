package eduAI.trace;

import java.util.Locale;

public final class TraceNames {
	private TraceNames() {
	}

	public static String token(Enum<?> aTraceName) {
		if (aTraceName == null) {
			throw new IllegalArgumentException(
					"Trace name must not be null");
		}
		return aTraceName.name().toLowerCase(Locale.ROOT);
	}
}
