package eduAI.trace.lib._tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;

import eduAI.lib._tests.TestTrace;
import eduAI.trace.lib.NativePayloadFormatter;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;

public class NativePayloadFormatterTest {
	private static final boolean TRACE = true;

	public static void main(String[] args) {
		NativePayloadFormatterTest test =
				new NativePayloadFormatterTest();
		TestTrace.programGoal(
				TRACE,
				test,
				"test provider-native payload formatting");
		TestTrace.run(
				TRACE,
				test,
				"testFormatsGeminiNativeContextWindow",
				"Gemini native Content lists show roles and part details",
				() -> test.testFormatsGeminiNativeContextWindow());
		TestTrace.run(
				TRACE,
				test,
				"testFormatsOllamaNativeContextWindow",
				"Ollama native chat-message lists show role, text, images, and tool counts",
				() -> test.testFormatsOllamaNativeContextWindow());
		TestTrace.run(
				TRACE,
				test,
				"testFormatsRegisteredProviderConfiguration",
				"provider configuration formatting is table-driven",
				() -> test.testFormatsRegisteredProviderConfiguration());
	}

	@Test
	public void testFormatsGeminiNativeContextWindow() {
		Content content =
				Content
						.builder()
						.role("user")
						.parts(
								List.of(
										Part.fromText("hello"),
										Part
												.builder()
												.inlineData(
														Blob
																.builder()
																.mimeType("image/png")
																.data(new byte[] {
																		1,
																		2,
																		3 })
																.build())
												.build(),
										Part
												.builder()
												.functionCall(
														FunctionCall
																.builder()
																.name("lookup")
																.args(Map.of(
																		"term",
																		"adapter"))
																.build())
												.build()))
						.build();

		String formatted =
				NativePayloadFormatter.formatContextWindow(
						List.of(content));

		trace("Gemini native context: " + formatted);
		assertTrue(formatted.contains(
				"com.google.genai.types.Content(role=\"user\""));
		assertTrue(formatted.contains("text(\"hello\""));
		assertTrue(formatted.contains(
				"inlineData(mimeType=\"image/png\", "
						+ "imageBytes(count=3,prefix=010203,suffix=010203))"));
		assertTrue(formatted.contains("functionCall(name=\"lookup\""));
	}

	@Test
	public void testFormatsOllamaNativeContextWindow() {
		OllamaChatMessage message = new OllamaChatMessage();
		message.setRole(OllamaChatMessageRole.USER);
		message.setResponse("hello");
		message.setThinking("think");
		message.setImages(List.of(new byte[] { 1, 2, 3 }));

		String formatted =
				NativePayloadFormatter.formatContextWindow(
						List.of(message));

		trace("Ollama native context: " + formatted);
		assertTrue(formatted.contains(
				"io.github.ollama4j.models.chat.OllamaChatMessage(role=user"));
		assertTrue(formatted.contains("thinking=\"think\""));
		assertTrue(formatted.contains("response=\"hello\""));
		assertTrue(formatted.contains("images=1"));
		assertTrue(formatted.contains("imageBytes=3"));
	}

	@Test
	public void testFormatsRegisteredProviderConfiguration() {
		try {
			NativePayloadFormatter.registerConfigurationFormatter(
					FakeConfiguration.class,
					configuration -> "fakeConfiguration("
							+ ((FakeConfiguration) configuration).value
							+ ")");
			String formatted =
					NativePayloadFormatter.formatConfiguration(
							new FakeConfiguration("configured"));
			trace("provider configuration: " + formatted);
			assertTrue(
					formatted.contains(
							"fakeConfiguration(configured)"));
		} finally {
			NativePayloadFormatter.clearConfigurationFormatters();
		}
	}

	private void trace(String aMessage) {
		TestTrace.trace(TRACE, this, aMessage);
	}

	private static class FakeConfiguration {
		private final String value;

		private FakeConfiguration(String aValue) {
			value = aValue;
		}
	}
}
