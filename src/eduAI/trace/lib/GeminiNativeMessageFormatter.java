package eduAI.trace.lib;

import java.util.List;
import java.util.Optional;

import com.google.genai.types.Blob;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import eduAI.trace.ImageByteSummary;

public final class GeminiNativeMessageFormatter {
	private GeminiNativeMessageFormatter() {
	}

	public static String formatContextWindow(List<Content> aMessages) {
		if (aMessages == null || aMessages.isEmpty()) {
			return "No Gemini content messages.";
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
					.append(formatContent(aMessages.get(i)));
		}
		return builder.toString();
	}

	public static String formatContent(Content aContent) {
		if (aContent == null) {
			return Content.class.getName() + "(null)";
		}
		StringBuilder builder = new StringBuilder();
		builder.append(Content.class.getName())
				.append("(role=")
				.append(quoted(value(aContent.role())))
				.append(", parts=[");
		List<Part> parts = aContent.parts().orElse(List.of());
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(i)
					.append(":")
					.append(formatPart(parts.get(i)));
		}
		builder.append("])");
		return builder.toString();
	}

	public static String formatPart(Part aPart) {
		if (aPart == null) {
			return "null";
		}
		if (aPart.text().isPresent()) {
			return "text("
					+ quoted(aPart.text().get())
					+ ", thought="
					+ aPart.thought().orElse(false)
					+ ")";
		}
		if (aPart.inlineData().isPresent()) {
			return formatInlineData(aPart.inlineData().get());
		}
		if (aPart.fileData().isPresent()) {
			return formatFileData(aPart.fileData().get());
		}
		if (aPart.functionCall().isPresent()) {
			return formatFunctionCall(aPart.functionCall().get());
		}
		if (aPart.codeExecutionResult().isPresent()) {
			return formatCodeExecutionResult(
					aPart.codeExecutionResult().get());
		}
		return "part(" + aPart + ")";
	}

	private static String formatInlineData(Blob aBlob) {
		byte[] data = aBlob == null
				? null
				: aBlob.data().orElse(null);
		return "inlineData(mimeType="
				+ quoted(aBlob == null
						? null
						: aBlob.mimeType().orElse(null))
				+ ", "
				+ ImageByteSummary.from(data).formatted()
				+ ")";
	}

	private static String formatFileData(FileData aFileData) {
		return "fileData(uri="
				+ quoted(aFileData == null
						? null
						: value(aFileData.fileUri()))
				+ ")";
	}

	private static String formatFunctionCall(FunctionCall aFunctionCall) {
		return "functionCall(name="
				+ quoted(aFunctionCall == null
						? null
						: value(aFunctionCall.name()))
				+ ", args="
				+ (aFunctionCall == null
						? "null"
						: String.valueOf(aFunctionCall.args()))
				+ ")";
	}

	private static String formatCodeExecutionResult(
			CodeExecutionResult aResult) {
		return "codeExecutionResult(output="
				+ quoted(aResult == null
						? null
						: value(aResult.output()))
				+ ")";
	}

	private static String value(Optional<String> anOptional) {
		return anOptional == null ? null : anOptional.orElse(null);
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
