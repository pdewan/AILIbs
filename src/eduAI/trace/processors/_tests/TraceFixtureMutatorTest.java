package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

public class TraceFixtureMutatorTest {
	public static void main(String[] args) throws IOException {
		new TraceFixtureMutatorTest().testParameterizedMutations();
		System.out.println("Trace fixture mutator test passed");
	}

	public void testParameterizedMutations() throws IOException {
		Path root = Files.createTempDirectory("trace-fixture-mutator-");
		try {
			Path source = root.resolve("source");
			Path target = root.resolve("broken");
			Files.createDirectories(source);
			for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
				Files.write(
						source.resolve(fileName),
						List.of(
								"## lib {request_sent} [none: main] (single_model_request_processing: test.Client) <provider=\"Gemini\" context=\"one two\" model=\"test\"> ##",
								"## lib {response_translated} [none: main] (single_model_request_processing: test.Client) <provider=\"Gemini\" response=\"ok\"> ##"),
						StandardCharsets.UTF_8);
			}

			Mutation wrongContext = Mutation.wrongFieldValue(
					"request_sent",
					"context").inFile(
						TraceFixtureFilter.TRACE_FILE_NAMES.get(0));
			Mutation removeResponse = Mutation.removeEvent(
					"response_translated").inFile(
						TraceFixtureFilter.TRACE_FILE_NAMES.get(0));
			Mutation removeSource = Mutation.removeSourceClass(
					"request_sent").inFile(
						TraceFixtureFilter.TRACE_FILE_NAMES.get(0));
			Mutation copyModel = Mutation.copyFieldValue(
					"request_sent",
					"context",
					"model").inFile(
						TraceFixtureFilter.TRACE_FILE_NAMES.get(1));
			TraceFixtureMutator.MutationResult result =
					new TraceFixtureMutator().createBrokenDataset(
							source,
							target,
							List.of(wrongContext, removeResponse, removeSource));

			String expectedDirectory =
					new TraceFixtureMutator().directoryName(
							List.of(wrongContext, removeResponse, removeSource));
			if (!expectedDirectory.equals(
					result.directory().getFileName().toString())) {
				throw new AssertionError(
						"Unexpected mutation directory: " + result.directory());
			}
			List<String> changed = Files.readAllLines(
					result.directory().resolve(
							TraceFixtureFilter.TRACE_FILE_NAMES.get(0)),
					StandardCharsets.UTF_8);
			if (changed.size() != 1
					|| !changed.get(0).contains("context=\"incorrect\"")
					|| changed.get(0).contains(": test.Client)")
					|| !changed.get(0).contains("model=\"test\"")) {
				throw new AssertionError("Unexpected changed trace: " + changed);
			}
			if (result.changedLineCounts().get(wrongContext) != 1
					|| result.changedLineCounts().get(removeResponse) != 1) {
				throw new AssertionError(
						"Unexpected mutation counts: "
								+ result.changedLineCounts());
			}
			TraceFixtureMutator.MutationResult copyResult =
					new TraceFixtureMutator().createBrokenDataset(
							source,
							root.resolve("copied"),
							List.of(copyModel));
			List<String> copied = Files.readAllLines(
					copyResult.directory().resolve(
							TraceFixtureFilter.TRACE_FILE_NAMES.get(1)),
					StandardCharsets.UTF_8);
			if (!copied.get(0).contains(
					"context=\"one two\" model=\"one two\"")) {
				throw new AssertionError(
						"Field value was not copied: " + copied.get(0));
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
