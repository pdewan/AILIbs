package eduAI.trace._tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import eduAI.trace.GenericTrace;
import eduAI.trace.lib.LibTraceActions;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.GenericTracesFileProcessor;

/** Emission-to-checker regression for String roles and invalid-evidence diagnostics. */
public final class StringRoleTraceTest {
	private enum Role { SYSTEM, USER, ASSISTANT }
	private record TextPart(String text) { }
	private record Message(Object role, List<TextPart> parts) { }
	private record NoRole(List<TextPart> parts) { }

	public static void main(String[] args) throws Exception {
		Path file = Files.createTempFile("role-trace-", ".txt");
		try {
			GenericTrace.setOutputEnabled(false);
			for (Object role : List.of("system", "user", "assistant", "SYSTEM", Role.SYSTEM, Role.USER, Role.ASSISTANT)) {
				check(file, new Message(role, List.of(new TextPart("hello"))), true);
			}
			check(file, new NoRole(List.of(new TextPart("hello"))), false);
			check(file, new Message(null, List.of(new TextPart("hello"))), false);
			System.out.println("StringRoleTraceTest: 7 valid roles and 2 unreadable roles checked");
		} finally {
			GenericTrace.resetTraceDestinations();
			Files.deleteIfExists(file);
		}
	}

	private static void check(Path file, Object message, boolean expected) throws Exception {
		GenericTrace.setTraceFile(file);
		LibTraceActions.traceServerHandleFactoryFetched(StringRoleTraceTest.class, "Ollama", new Object(), new Object());
		LibTraceActions.traceMessageTranslated(StringRoleTraceTest.class, message, new Object());
		GenericTrace.clearTraceFile();
		GenericTracesFileProcessor processor = new GenericTracesFileProcessor();
		processor.processTraceFile("Ollama", StreamingMode.NON_STREAMING, file);
		var result = processor.checkTraceEventExists("message_translated",
				Map.of("Ollama", List.of(StreamingMode.NON_STREAMING)));
		if (result.passed() != expected) throw new AssertionError(result);
		if (!expected && result.level3ErrorMessages().stream().noneMatch(s -> s.contains("role not recognized"))) {
			throw new AssertionError("Missing invalid-role diagnostic: " + result);
		}
	}
}
