package ai_libs.demo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

import ai_libs.logging.LogProcessor;
import ai_libs.logging.OllamaLoggingControl;
import ai_libs.s_eliza.BasicElizaClient;
import ai_libs.s_eliza.ElizaClient;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;

/*
 * Export sanity check: this main directly references the five client stacks
 * expected in the library jar and sends the same text prompt to each one.
 * Network/API-key failures are reported per client so one missing service does
 * not hide whether the other client classes are present and loadable.
 */
public class AllClientsDemo {
	private static final String DEFAULT_PROMPT =
			"Answer in one short sentence: what is the meaning of life?";
	private static final String GEMINI_API_KEY_VARIABLE_NAME =
			"GEMINI_API_KEY";
	private static final String DEFAULT_GEMINI_MODEL =
			"gemini-3.1-flash-lite";
	private static final String OPENAI_API_KEY_VARIABLE_NAME =
			"OPENAI_API_KEY";
	private static final String DEFAULT_OPENAI_BASE_URL =
			"http://langchain4j.dev/demo/openai/v1";
	private static final String DEFAULT_OPENAI_API_KEY = "demo";
	private static final String DEFAULT_OPENAI_MODEL =
			"gpt-4o-mini";
	private static final String DEFAULT_OLLAMA_BASE_URL =
			"http://localhost:11434/";
	private static final String DEFAULT_OLLAMA_MODEL =
			"llama3.2:latest";
	private static final int TIMEOUT_SECONDS = 60;

	public static void main(String[] args) {
		LogProcessor.processLogging();

		String prompt = args.length == 0
				? DEFAULT_PROMPT
				: String.join(" ", args);

		System.out.println(
				"Ollama request debug logging enabled: "
						+ OllamaLoggingControl.isOllamaRequestDebugEnabled());
		System.out.println("Prompt: " + prompt);
		System.out.println();

		run("Ollama4j", () -> ollamaAnswer(prompt));
		run("Gemini SDK", () -> geminiAnswer(prompt));
		run("OpenAI via LangChain4j", () -> openAIAnswer(prompt));
		run("LangChain4j Ollama", () -> langChain4jAnswer(prompt));
		run("Eliza", () -> elizaAnswer(prompt));
	}

	private static void run(
			String aClientName,
			Callable<String> anAnswerSupplier) {
		System.out.println("=== " + aClientName + " ===");
		try {
			String answer = anAnswerSupplier.call();
			System.out.println(answer == null || answer.isBlank()
					? "<empty answer>"
					: answer);
		} catch (Throwable t) {
			System.out.println(
					"FAILED: "
							+ t.getClass().getSimpleName()
							+ ": "
							+ t.getMessage());
		}
		System.out.println();
	}

	private static String ollamaAnswer(String aPrompt) throws Exception {
		Ollama ollama = new Ollama(ollamaBaseURL());
		ollama.setRequestTimeoutSeconds(TIMEOUT_SECONDS);
		List<OllamaChatMessage> messages = new ArrayList<>();
		messages.add(
				new OllamaChatMessage(
						OllamaChatMessageRole.USER,
						aPrompt));
		OllamaChatRequest request = OllamaChatRequest
				.builder()
				.withModel(ollamaModel())
				.withMessages(messages)
				.build();
		ollama.chat(request, null);
		return messages.get(messages.size() - 1).getResponse();
	}

	private static String geminiAnswer(String aPrompt) {
		Client client = Client
				.builder()
				.apiKey(System.getenv(GEMINI_API_KEY_VARIABLE_NAME))
				.build();
		Content userTurn = Content
				.builder()
				.role("user")
				.parts(Part.fromText(aPrompt))
				.build();
		GenerateContentResponse response =
				client.models.generateContent(
						geminiModel(),
						List.of(userTurn),
						null);
		return response.text();
	}

	private static String openAIAnswer(String aPrompt) {
		OpenAiChatModel model = OpenAiChatModel
				.builder()
				.baseUrl(openAIBaseURL())
				.apiKey(openAIAPIKey())
				.modelName(openAIModel())
				.timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
				.build();
		ChatResponse response =
				model.chat(List.of(UserMessage.from(aPrompt)));
		return response.aiMessage().text();
	}

	private static String langChain4jAnswer(String aPrompt) {
		ChatModel model = OllamaChatModel
				.builder()
				.baseUrl(ollamaBaseURL())
				.modelName(ollamaModel())
				.timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
				.build();
		ChatRequest request = ChatRequest
				.builder()
				.messages(List.of(UserMessage.from(aPrompt)))
				.build();
		ChatResponse response = model.chat(request);
		return response.aiMessage().text();
	}

	private static String elizaAnswer(String aPrompt) {
		ElizaClient client = new BasicElizaClient();
		return client.generate(aPrompt);
	}

	private static String ollamaBaseURL() {
		String value = System.getenv("OLLAMA_BASE_URL");
		return value == null || value.isBlank()
				? DEFAULT_OLLAMA_BASE_URL
				: value.trim();
	}

	private static String ollamaModel() {
		String value = System.getenv("OLLAMA_MODEL");
		return value == null || value.isBlank()
				? DEFAULT_OLLAMA_MODEL
				: value.trim();
	}

	private static String openAIAPIKey() {
		String value = System.getenv(OPENAI_API_KEY_VARIABLE_NAME);
		return value == null || value.isBlank()
				? DEFAULT_OPENAI_API_KEY
				: value.trim();
	}

	private static String geminiModel() {
		String value = System.getenv("GEMINI_MODEL");
		return value == null || value.isBlank()
				? DEFAULT_GEMINI_MODEL
				: value.trim();
	}

	private static String openAIBaseURL() {
		String value = System.getenv("OPENAI_BASE_URL");
		return value == null || value.isBlank()
				? DEFAULT_OPENAI_BASE_URL
				: value.trim();
	}

	private static String openAIModel() {
		String value = System.getenv("OPENAI_MODEL");
		return value == null || value.isBlank()
				? DEFAULT_OPENAI_MODEL
				: value.trim();
	}
}
