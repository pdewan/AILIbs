package ai_libs.s_eliza;

import java.util.Iterator;
import java.util.List;


public interface ElizaClient {
	public static String DEFAULT_MODEL_NAME = "s-eliza";

	default String generate(String aPrompt) {
		return generate(DEFAULT_MODEL_NAME, aPrompt).text();
	}

	default Iterator<String> generateStream(String aPrompt) {
		return generateStream(DEFAULT_MODEL_NAME, aPrompt);
	}

	ElizaResponse generate(
			String aModelName,
			String aPrompt);

	ElizaResponse generate(
			String aModelName,
			List<ElizaMessage> aMessages);

	Iterator<String> generateStream(
			String aModelName,
			String aPrompt);

	Iterator<String> generateStream(
			String aModelName,
			List<ElizaMessage> aMessages);
}
