package eduAI.trace.processors._tests;

import eduAI.trace.processors.OllamaTraceInterpreter;

public class OllamaResponseQuotedTextTest {
    public static void main(String[] args) {
        assertResponse(" In the movie \"The Holy Grail\", Arthur is king. ");
        assertResponse("He said \"hello\", then \"goodbye\", and left.");
        assertResponse("A backslash \\ followed by a quoted word \"word\", then more.");
        assertResponse("Plain text, with commas.");
        System.out.println("Ollama quoted response text checks passed");
    }

    private static void assertResponse(String expected) {
        String escaped = expected.replace("\\", "\\\\").replace("\"", "\\\"");
        String dump = "OllamaChatResponseModel(message={\"role\":\"assistant\","
                + "\"content\":\"" + escaped + "\",\"tool_calls\":null})";
        var messages = new OllamaTraceInterpreter().providerResponseMessages(dump);
        if (messages.size() != 1 || messages.get(0).parts().size() != 1
                || !expected.equals(messages.get(0).parts().get(0).value())) {
            throw new AssertionError("Expected complete response <" + expected
                    + "> but parsed " + messages);
        }
    }
}
