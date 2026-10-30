package eduAI.trace.processors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** File location for the chronological CLI session, independent of demo inputs. */
public final class CLITraceInformation {
	public static final String TRACE_FILE_NAME = "CLITrace.txt";
	public static final String INTERACTIONS_FILE_NAME = "CLIInputOutput.txt";
	private final Path directory;

	public CLITraceInformation(Path directory) {
		this.directory = directory == null ? Path.of(".") : directory;
	}

	public Path traceFile() {
		String compressedName = TRACE_FILE_NAME + ".gz";
		Path found = directory();
		Path plain = found.resolve(TRACE_FILE_NAME);
		Path compressed = found.resolve(compressedName);
		return !Files.isRegularFile(plain) && Files.isRegularFile(compressed) ? compressed : plain;
	}

	/** Resolve the session as one set; never combine files from separate directories. */
	public Path directory() {
		return TraceDirectorySearch.find(directory,
				List.of(TRACE_FILE_NAME, TRACE_FILE_NAME + ".gz", INTERACTIONS_FILE_NAME));
	}
}
