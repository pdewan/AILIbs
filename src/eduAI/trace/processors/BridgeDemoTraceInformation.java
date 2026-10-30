package eduAI.trace.processors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A1 bridge-demo file locations and expected inputs; contains no checking logic. */
public final class BridgeDemoTraceInformation {
	private final Path traceDirectory;

	public BridgeDemoTraceInformation() {
		this(Path.of("."));
	}

	public BridgeDemoTraceInformation(Path aTraceDirectory) {
		traceDirectory = aTraceDirectory == null ? Path.of(".") : aTraceDirectory;
	}

	public Map<String, GenericTracesFileProcessor.TraceFiles> traceFiles() {
		System.out.println(GenericTracesFileProcessor.INFO_MESSAGE_PREFIX
				+ " Searching for trace files; current directory is: " + Path.of(".").toAbsolutePath().normalize());
		Map<String, GenericTracesFileProcessor.TraceFiles> result = new LinkedHashMap<>();
		result.put(TraceProviderNames.GEMINI, new GenericTracesFileProcessor.TraceFiles(
				traceFile("TraceGeminiBridgeStreamingDemo.txt"),
				traceFile("TraceGeminiBridgeNonStreamingDemo.txt")));
		result.put(TraceProviderNames.OLLAMA, new GenericTracesFileProcessor.TraceFiles(
				traceFile("TraceOllamaStreamingBridgeDemo.txt"),
				traceFile("TraceOllamaNonStreamingBridgeDemo.txt")));
		return result;
	}

	/** Preserve legacy filenames, compressed traces, and submission-directory lookup. */
	public Path traceFile(String aFileName) {
		String preferred = aFileName.replace("OllamaStreamingBridge", "OllamaBridgeStreaming")
				.replace("OllamaNonStreamingBridge", "OllamaBridgeNonStreaming");
		Path directory = TraceDirectorySearch.find(traceDirectory,
				List.of(preferred, preferred + ".gz", aFileName, aFileName + ".gz"));
		for (String name : List.of(preferred, aFileName)) {
			Path plain = directory.resolve(name);
			if (Files.isRegularFile(plain)) return plain;
			Path compressed = directory.resolve(name + ".gz");
			if (Files.isRegularFile(compressed)) return compressed;
		}
		return directory.resolve(preferred);
	}

	public GenericTracesFileProcessor.ExpectedInputs expectedInputs() {
		return new GenericTracesFileProcessor.ExpectedInputs(
				List.of(BridgeDemoTraceInputs.SYSTEM_PROMPT_1, BridgeDemoTraceInputs.SYSTEM_PROMPT_2),
				List.of(BridgeDemoTraceInputs.STARTING_PROMPT, "Galahad?",
						BridgeDemoTraceInputs.SUMMARY_PROMPT, "Arthur?"),
				List.of(BridgeDemoTraceInputs.SUMMARY_PROMPT),
				imageBytes());
	}

	private byte[] imageBytes() {
		System.out.println(GenericTracesFileProcessor.INFO_MESSAGE_PREFIX
				+ " Searching for reference image; current directory is: " + Path.of(".").toAbsolutePath().normalize());
		String name = BridgeDemoTraceInputs.IMAGE_FILE_NAME;
		Path image = TraceDirectorySearch.find(Path.of("."), List.of(name)).resolve(name);
		try {
			return Files.readAllBytes(image);
		} catch (IOException e) {
			System.err.println(GenericTracesFileProcessor.ERROR_MESSAGE_PREFIX
					+ " Cannot read expected reference image " + image.toAbsolutePath().normalize()
					+ "; expected-image checks cannot pass. " + e.getMessage());
			return new byte[0];
		}
	}
}
