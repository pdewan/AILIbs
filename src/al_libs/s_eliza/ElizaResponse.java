package al_libs.s_eliza;

public class ElizaResponse {
	private final String modelName;
	private final String text;
	private final String prompt;

	public ElizaResponse(
			String aModelName,
			String aText,
			String aPrompt) {
		modelName = aModelName == null ? "" : aModelName;
		text = aText == null ? "" : aText;
		prompt = aPrompt == null ? "" : aPrompt;
	}

	public String modelName() {
		return modelName;
	}

	public String text() {
		return text;
	}

	public String prompt() {
		return prompt;
	}
}
