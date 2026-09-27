package eduAI.trace.processors._tests;

import eduAI.trace.processors.GeminiTraceInterpreter;
import eduAI.trace.processors.OllamaTraceInterpreter;

public class ProviderTextDelimiterTest {
    public static void main(String[] args) {
        String text = "Keep ]) inside text, including \"quotes\", and continue.";
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        var messages = new GeminiTraceInterpreter().providerContextWindowMessages(
                "com.google.genai.types.Content(role=\"user\", parts=[0:text(\""
                + escaped + "\", thought=false)])");
        if (messages.size() != 1 || messages.get(0).parts().size() != 1
                || !text.equals(messages.get(0).parts().get(0).value())) {
            throw new AssertionError("Gemini delimiter text truncated: " + messages);
        }
        var response = new OllamaTraceInterpreter().providerResponseMessages(
                "OllamaChatResponseModel(message={\"role\":\"assistant\","
                + "\"content\":\"line\\nnext\\t\\u263a and literal \\\\n\",\"tool_calls\":null})");
        String expected = "line\nnext\t\u263a and literal \\n";
        if (response.size() != 1 || !expected.equals(response.get(0).parts().get(0).value())) {
            throw new AssertionError("Ollama JSON escapes incorrectly decoded: " + response);
        }
        String bracketText = "Read [the first item] and then ] the rest.";
        String part = "Part{text=Optional[" + bracketText + "], thought=Optional.empty}";
        var gemini = new GeminiTraceInterpreter();
        var bracketResponse = gemini.providerResponseMessages("GenerateContentResponse{" + part + "}");
        var system = gemini.providerConfigurationMessages(
                "GenerateContentConfig{systemInstruction=Optional[Content{parts=Optional[[" + part + "]]}]}");
        if (!bracketText.equals(bracketResponse.get(0).parts().get(0).value())
                || !bracketText.equals(system.get(0).parts().get(0).value())) {
            throw new AssertionError("Gemini Optional text was truncated at a data bracket");
        }
        System.out.println("Provider text delimiter checks passed");
    }
}
