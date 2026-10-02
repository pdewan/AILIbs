package eduAI.trace.processors;

public final class TraceProviderNames {
	public static final String GEMINI = "Gemini";
	public static final String OLLAMA = "Ollama";
	/** Case-insensitive registry identity; existing display names remain compatible. */
	public static String canonical(String name) {
		if (name == null) return "";
		return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "gemini" -> GEMINI;
			case "ollama" -> OLLAMA;
			default -> name.trim();
		};
	}

	private TraceProviderNames() {
	}
}
