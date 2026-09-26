package eduAI.trace.lib;

import java.util.List;

import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatToolCalls;
import eduAI.trace.ImageByteSummary;

public final class OllamaNativeMessageFormatter {
	private OllamaNativeMessageFormatter() {
	}

	public static String formatContextWindow(
			List<OllamaChatMessage> aMessages) {
		if (aMessages == null || aMessages.isEmpty()) {
			return "No Ollama chat messages.";
		}
		StringBuilder builder = new StringBuilder();
		String lineSeparator = System.lineSeparator();
		for (int i = 0; i < aMessages.size(); i++) {
			if (i > 0) {
				builder.append(lineSeparator);
			}
			builder.append("[")
					.append(i + 1)
					.append("] ")
					.append(formatMessage(aMessages.get(i)));
		}
		return builder.toString();
	}

	public static String formatMessage(OllamaChatMessage aMessage) {
		if (aMessage == null) {
			return OllamaChatMessage.class.getName() + "(null)";
		}
		return OllamaChatMessage.class.getName() + "(role="
				+ aMessage.getRole()
				+ ", thinking="
				+ quoted(aMessage.getThinking())
				+ ", response="
				+ quoted(aMessage.getResponse())
				+ ", images="
				+ imageCount(aMessage)
				+ ", imageBytes="
				+ imageByteCount(aMessage)
				+ ", imageSummary="
				+ firstImageSummary(aMessage)
				+ ", toolCalls="
				+ formatToolCalls(aMessage.getToolCalls())
				+ ")";
	}

	private static String firstImageSummary(OllamaChatMessage aMessage) {
		if (aMessage.getImages() == null || aMessage.getImages().isEmpty()) {
			return ImageByteSummary.from(null).formatted();
		}
		return ImageByteSummary.from(aMessage.getImages().get(0)).formatted();
	}

	private static int imageCount(OllamaChatMessage aMessage) {
		return aMessage.getImages() == null
				? 0
				: aMessage.getImages().size();
	}

	private static int imageByteCount(OllamaChatMessage aMessage) {
		if (aMessage.getImages() == null) {
			return 0;
		}
		int result = 0;
		for (byte[] image : aMessage.getImages()) {
			result += image == null ? 0 : image.length;
		}
		return result;
	}

	private static String formatToolCalls(
			List<OllamaChatToolCalls> aToolCalls) {
		if (aToolCalls == null || aToolCalls.isEmpty()) {
			return "[]";
		}
		StringBuilder builder = new StringBuilder("[");
		for (int i = 0; i < aToolCalls.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(formatToolCall(aToolCalls.get(i)));
		}
		builder.append("]");
		return builder.toString();
	}

	private static String formatToolCall(
			OllamaChatToolCalls aToolCall) {
		if (aToolCall == null || aToolCall.getFunction() == null) {
			return "toolCall(null)";
		}
		return "toolCall(name="
				+ quoted(aToolCall.getFunction().getName())
				+ ", arguments="
				+ aToolCall.getFunction().getArguments()
				+ ")";
	}

	private static String quoted(String aText) {
		if (aText == null) {
			return "null";
		}
		return "\"" + aText
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				+ "\"";
	}
}
