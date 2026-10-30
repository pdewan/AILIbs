package eduAI.trace.processors._tests;

import java.nio.file.Files;
import java.nio.file.Path;
import eduAI.trace.processors.*;

public final class TraceDirectorySearchTest {
	private static final String GEMINI = "TraceGeminiBridgeStreamingDemo.txt";
	private static final String OLLAMA = "TraceOllamaStreamingBridgeDemo.txt";
	public static void main(String[] args) throws Exception {
		if (args.length > 0 && args[0].equals("--image-cwd")) {
			var information = new BridgeDemoTraceInformation();
			var processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
                processor.setExpectedContentChecks(new eduAI.trace.processors.assignment1.BridgeDemoContentChecks((GenericTracesFileProcessor) processor));
			require(java.util.Arrays.equals(information.expectedInputs().expectedImageBytes(),
					Files.readAllBytes(Path.of(args[1]))), "reference image bytes differ");
			processor.processTraceFiles();
			boolean expectedPass = args.length < 3 || !args[2].equals("--expect-failure");
			require(processor.checkExpectedGenericImagesMatch("Gemini").passed() == expectedPass, "unexpected Gemini image result");
			require(processor.checkExpectedGenericImagesMatch("Ollama").passed() == expectedPass, "unexpected Ollama image result");
			System.out.println("Reference image lookup and comparison passed: " + Path.of(".").toAbsolutePath().normalize());
			return;
		}
		if (args.length > 0 && args[0].equals("--cwd")) {
			var information = new BridgeDemoTraceInformation();
			var processor = new NativeTraceFilesProcessor(information.traceFiles(), information.expectedInputs());
                processor.setExpectedContentChecks(new eduAI.trace.processors.assignment1.BridgeDemoContentChecks((GenericTracesFileProcessor) processor));
			require(processor.checkTraceFilesExist().passed(), "default current-directory lookup failed");
			System.out.println("Current-directory lookup passed: " + information.traceFiles());
			return;
		}
		Path root = Files.createTempDirectory("trace-directory-search-");
		Path local = root.resolve("local");
		write(local.resolve(GEMINI));
		write(local.resolve("educopliot/" + GEMINI));
		write(local.resolve("educopliot/" + OLLAMA));
		var processor = new BridgeDemoTraceInformation(local);
		require(processor.traceFile(GEMINI).equals(local.resolve(GEMINI)), "local file must win");
		require(processor.traceFile(OLLAMA).equals(local.resolve("educopliot/" + OLLAMA)), "missing local file must search descendants");

		Path nested = root.resolve("nested");
		write(nested.resolve("educopliot/src/" + GEMINI));
		write(nested.resolve("educopliot/bin/" + GEMINI));
		write(nested.resolve("src/project/" + GEMINI));
		write(nested.resolve("BIN/project/" + GEMINI));
		write(nested.resolve("outer/educopliot/logs/" + GEMINI));
		processor = new BridgeDemoTraceInformation(nested);
		require(processor.traceFile(GEMINI).equals(nested.resolve("outer/educopliot/logs/" + GEMINI)), "recursive search must prune src and bin");
		write(nested.resolve("outer/educopliot/logs/TraceOllamaBridgeStreamingDemo.txt.gz"));
		require(processor.traceFile(OLLAMA).equals(nested.resolve("outer/educopliot/logs/TraceOllamaBridgeStreamingDemo.txt.gz")), "preferred compressed alias must work recursively");

		Path excluded = root.resolve("excluded");
		write(excluded.resolve("src/" + GEMINI));
		write(excluded.resolve("bin/child/" + GEMINI));
		processor = new BridgeDemoTraceInformation(excluded);
		require(processor.traceFile(GEMINI).equals(excluded.resolve(GEMINI)), "excluded files must remain missing");
		require(!new NativeTraceFilesProcessor(processor.traceFiles(), processor.expectedInputs()).checkTraceFilesExist().passed(), "missing files must still fail");

		Path project = root.resolve("project");
		Path bin = project.resolve("BiN");
		write(bin.resolve(GEMINI));
		write(project.resolve(GEMINI));
		write(project.resolve(OLLAMA));
		processor = new BridgeDemoTraceInformation(bin);
		require(processor.traceFile(GEMINI).equals(bin.resolve(GEMINI)), "bin-local file must win over parent");
		require(processor.traceFile(OLLAMA).equals(project.resolve(OLLAMA)), "missing bin-local file must search parent");
		String nonStreaming = "TraceGeminiBridgeNonStreamingDemo.txt";
		write(project.resolve("submission/" + nonStreaming));
		write(project.resolve("src/" + nonStreaming));
		write(bin.resolve("nested/" + nonStreaming));
		require(processor.traceFile(nonStreaming).equals(project.resolve("submission/" + nonStreaming)), "bin fallback must recurse from parent and prune src/bin");

		Path ambiguous = root.resolve("ambiguous");
		write(ambiguous.resolve("one/" + GEMINI));
		write(ambiguous.resolve("two/" + GEMINI));
		try { new BridgeDemoTraceInformation(ambiguous).traceFiles(); throw new AssertionError("ambiguous matches accepted"); }
		catch (IllegalArgumentException expected) {
			require(expected.getMessage().contains("Multiple trace directories"), "ambiguity should be explained");
		}
		System.out.println("TraceDirectorySearchTest passed; fixtures: " + root);
	}
	private static void write(Path file) throws Exception { Files.createDirectories(file.getParent()); Files.writeString(file, "fixture"); }
	private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
