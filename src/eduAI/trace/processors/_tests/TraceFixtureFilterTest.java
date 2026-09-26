package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;

public class TraceFixtureFilterTest {
	public static void main(String[] args) throws IOException {
		new TraceFixtureFilterTest().testFiltersDataset();
		System.out.println("Trace fixture filter test passed");
	}

	public void testFiltersDataset() throws IOException {
		Path root = Files.createTempDirectory("trace-fixture-filter-");
		try {
			Path source = root.resolve("source");
			Path target = root.resolve("target");
			Files.createDirectories(source);
			for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
				Files.write(
						source.resolve(fileName),
						List.of(
								"## lib {request_sent} [none: main] (single_model_request_processing: test.Client) <provider=\"Test\"> ##",
								"## lib {server_handle_created} [none: main] (single_model_request_processing: test.Client) <provider=\"Test\"> ##"),
						StandardCharsets.UTF_8);
			}

			TraceFixtureFilter.FilterSummary summary =
					new TraceFixtureFilter().filterDataset(
							source.toString(),
							target.toString());
			if (summary.datasets() != 1
					|| summary.retainedTraceLines() != 4
					|| summary.removedTraceLines() != 4) {
				throw new AssertionError("Unexpected summary: " + summary);
			}
			for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
				List<String> lines = Files.readAllLines(
						target.resolve(fileName),
						StandardCharsets.UTF_8);
				if (lines.size() != 1) {
					throw new AssertionError(
							"Expected one retained line in " + fileName);
				}
				TraceLine traceLine = TraceLineParser.parse(lines.get(0));
				if (traceLine == null
						|| !"request_sent".equals(traceLine.getEventName())) {
					throw new AssertionError(
							"Unexpected retained line: " + lines.get(0));
				}
			}
		} finally {
			deleteDirectory(root);
		}
	}

	private void deleteDirectory(Path aDirectory) throws IOException {
		try (var paths = Files.walk(aDirectory)) {
			for (Path path : paths
					.sorted((first, second) -> second.compareTo(first))
					.toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
