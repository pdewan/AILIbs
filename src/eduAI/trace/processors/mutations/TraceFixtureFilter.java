package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;

public class TraceFixtureFilter {
	public static final Path DEFAULT_SOURCE = Path.of(".");
	public static final Path DEFAULT_TARGET =
			Path.of("correct_filtered");

	public static final List<String> TRACE_FILE_NAMES = List.of(
			"TraceGeminiBridgeNonStreamingDemo.txt",
			"TraceGeminiBridgeStreamingDemo.txt",
			"TraceOllamaNonStreamingBridgeDemo.txt",
			"TraceOllamaStreamingBridgeDemo.txt");

	public record FilterSummary(
			int datasets,
			int sourceTraceLines,
			int retainedTraceLines) {
		public int removedTraceLines() {
			return sourceTraceLines - retainedTraceLines;
		}
	}

	public FilterSummary filterDefaultFixtures() throws IOException {
		return filterDataset(DEFAULT_SOURCE, DEFAULT_TARGET);
	}

	public FilterSummary filterDataset(
			String aSourceDirectory,
			String aTargetDirectory) throws IOException {
		return filterDataset(
				Path.of(aSourceDirectory),
				Path.of(aTargetDirectory));
	}

	public FilterSummary filterDataset(
			Path aSourceDirectory,
			Path aTargetDirectory) throws IOException {
		MutableSummary summary = new MutableSummary();
		Set<String> essentialEvents = EssentialTraceEvents.all();
		filterDataset(
				aSourceDirectory,
				aTargetDirectory,
				essentialEvents,
				summary);
		validateFilteredDirectory(aTargetDirectory, essentialEvents);
		return summary.toImmutable();
	}

	private void filterDataset(
			Path aSourceDirectory,
			Path aTargetDirectory,
			Set<String> essentialEvents,
			MutableSummary summary) throws IOException {
		Files.createDirectories(aTargetDirectory);
		for (String fileName : TRACE_FILE_NAMES) {
			Path source = aSourceDirectory.resolve(fileName);
			if (!Files.isRegularFile(source)) {
				throw new IOException("Required trace file is missing: " + source);
			}
			List<String> retainedLines = new ArrayList<>();
			for (String line : Files.readAllLines(
					source,
					StandardCharsets.UTF_8)) {
				TraceLine traceLine = TraceLineParser.parse(line);
				if (traceLine == null) {
					continue;
				}
				summary.sourceTraceLines++;
				if (essentialEvents.contains(traceLine.getEventName())) {
					retainedLines.add(line);
					summary.retainedTraceLines++;
				}
			}
			Files.write(
					aTargetDirectory.resolve(fileName),
					retainedLines,
					StandardCharsets.UTF_8);
		}
		summary.datasets++;
	}

	private void validateFilteredDirectory(
			Path aRoot,
			Set<String> essentialEvents) throws IOException {
		if (!Files.exists(aRoot)) {
			throw new IOException("Filtered trace directory was not created: " + aRoot);
		}
		for (String fileName : TRACE_FILE_NAMES) {
			Path path = aRoot.resolve(fileName);
			if (!Files.isRegularFile(path)) {
				throw new IOException("Filtered trace file is missing: " + path);
			}
				for (String line : Files.readAllLines(
						path,
						StandardCharsets.UTF_8)) {
					TraceLine traceLine = TraceLineParser.parse(line);
					if (traceLine == null
							|| !essentialEvents.contains(
									traceLine.getEventName())) {
						throw new IOException(
								"Nonessential or malformed trace line in "
										+ path
										+ ": "
										+ line);
					}
			}
		}
	}

	public static void main(String[] args) throws IOException {
		TraceFixtureFilter filter = new TraceFixtureFilter();
		if (args.length > 2) {
			throw new IllegalArgumentException(
					"Expected optional arguments: "
							+ "[sourceDirectory] [targetDirectory]");
		}
		Path source = args.length > 0
				? Path.of(args[0])
				: DEFAULT_SOURCE;
		Path target = args.length > 1
				? Path.of(args[1])
				: DEFAULT_TARGET;
		FilterSummary summary = filter.filterDataset(source, target);
		System.out.println(
				"Filtered trace fixtures: datasets="
						+ summary.datasets()
						+ " retained="
						+ summary.retainedTraceLines()
						+ " removed="
						+ summary.removedTraceLines());
	}

	private static class MutableSummary {
		private int datasets;
		private int sourceTraceLines;
		private int retainedTraceLines;

		private FilterSummary toImmutable() {
			return new FilterSummary(
					datasets,
					sourceTraceLines,
					retainedTraceLines);
		}
	}
}
