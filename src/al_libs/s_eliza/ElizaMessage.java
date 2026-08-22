package al_libs.s_eliza;

public class ElizaMessage {
	private final ElizaRole role;
	private final String text;

	public ElizaMessage(
			ElizaRole aRole,
			String aText) {
		role = aRole == null ? ElizaRole.USER : aRole;
		text = aText == null ? "" : aText;
	}

	public ElizaRole role() {
		return role;
	}

	public String text() {
		return text;
	}
}
