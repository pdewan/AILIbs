package eduAI.trace.processors._tests;

import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.lib.OllamaNativeMessageFormatter;
import eduAI.trace.processors.*;
import java.util.*;

public final class StudentReportCompatibilityTest {
	public static void main(String[] args) {
		var ollama = new OllamaTraceInterpreter();
		String nativeMessage = "OllamaChatMessage(role=user, thinking=null, response=\"hello\", images=0, imageBytes=0, imageSummary=imageBytes(count=0,prefix=,suffix=))";
		require(ollama.providerContextWindowMessages(nativeMessage).size() == 1, "null thinking rejected");
		require(ollama.providerContextWindowMessages(nativeMessage.replace("thinking=null", "thinking=invalid")).isEmpty(), "invalid thinking accepted");
		var gemini = new GeminiTraceInterpreter();
		String config = "GenerateContentConfig{systemInstruction=Optional[Content{parts=Optional[[Part{text=Optional[first instruction], thought=Optional.empty}, Part{text=Optional[second instruction], thought=Optional.empty}]], role=Optional[system]}], temperature=Optional[0.4]}";
		var messages = gemini.providerConfigurationMessages(config);
		require(messages.size() == 2 && messages.get(1).parts().get(0).value().equals("second instruction"), "Gemini lost a system part");
		var actualConfig = com.google.genai.types.GenerateContentConfig.builder().systemInstruction(
				com.google.genai.types.Content.builder().role("system").parts(List.of(
						com.google.genai.types.Part.fromText("first instruction"),
						com.google.genai.types.Part.fromText("second instruction with additional words"))).build()).build();
		var actualMessages = gemini.providerConfigurationMessages(TraceObjectPrinter.format("configuration", actualConfig));
		require(actualMessages.size() == 2 && eduAI.trace.TraceTextSummary.matches(
				actualMessages.get(1).parts().get(0).value(), "second instruction with additional words"),
				"actual Gemini configuration lost its second system part");
		String source = "GenerateContentResponse{usageMetadata=Optional[GenerateContentResponseUsageMetadata{promptTokenCount=Optional[12], candidatesTokenCount=Optional[3]}]}";
		var registry = gemini.nativeMetadataRegistry(source);
		require(registry.difference(source, "PROMPT_TOKEN_COUNT", "12") == null, "metadata alias rejected");
		require(registry.difference(source, "PROMPT_TOKEN_COUNT", "99") != null, "bad aliased metadata accepted");
		require(registry.difference(source, "arbitrary student name", "12") == null, "arbitrary metadata name rejected");
		require(registry.difference(source, "arbitrary student name", "99999") != null, "arbitrary name bypassed value checking");
		var checker = new TextProviderParameterChecker("Ollama", "temperature", "Options", "temperature=");
		String target = TraceObjectPrinter.format("target", new LinkedHashMap<>(Map.of("temperature", 0.4)));
		var evidence = new ProviderParameterChecker.ParameterEvidence("temperature", "0.4", "sample.Adapter", target, target);
		require(checker.isExpectedTarget(evidence) && checker.isAssigned(evidence), "options map rejected");
		var wrong = new ProviderParameterChecker.ParameterEvidence("temperature", "0.9", "sample.Adapter", target, target);
		require(!checker.isAssigned(wrong), "wrong options map value accepted");
		for (String name : List.of("GEMINI", "gemini", "GeMiNi")) require(TraceProviderNames.canonical(name).equals("Gemini"), "case not normalized");
		System.out.println("StudentReportCompatibilityTest passed");
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
