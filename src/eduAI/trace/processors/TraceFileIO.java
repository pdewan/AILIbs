package eduAI.trace.processors;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;

/** Lossless trace transport: retains every chunk and every grading field. */
public final class TraceFileIO {
	private TraceFileIO() { }
	public static List<String> readLines(Path file) throws IOException {
		try (InputStream raw = Files.newInputStream(file);
			 InputStream decoded = file.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
			 BufferedReader reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
			return reader.lines().toList();
		}
	}
	public static void main(String[] args) throws IOException {
		if (args.length != 1) throw new IllegalArgumentException("Usage: TraceFileIO <trace-directory>");
		try (var paths = Files.list(Path.of(args[0]))) {
			for (Path file : paths.filter(p -> p.getFileName().toString().startsWith("Trace")
					&& p.getFileName().toString().endsWith("Demo.txt")).toList()) {
				Path target = file.resolveSibling(file.getFileName() + ".gz");
				try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(target))) { Files.copy(file, output); }
				System.out.println(target.getFileName() + " " + Files.size(file) + " -> " + Files.size(target) + " bytes");
			}
		}
	}
}
