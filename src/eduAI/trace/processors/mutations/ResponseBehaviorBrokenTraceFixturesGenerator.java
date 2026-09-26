package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

public class ResponseBehaviorBrokenTraceFixturesGenerator {
	public static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	public static final Path DEFAULT_TARGET =
			Path.of("response_behavior_broken");

	public List<Path> generate(Path aSource, Path aTarget)
			throws IOException {
		TraceFixtureMutator mutator = new TraceFixtureMutator();
		ArrayList<Path> result = new ArrayList<>();
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			String responseField = fileName.contains("NonStreaming")
					? "providerDependentResponse"
					: "providerIndependentResponse";
			result.add(create(
					mutator,
					aSource,
					aTarget,
					Mutation.wrongFieldValue(
							"response_translated",
							responseField)
							.inFile(fileName)
							.atOccurrence(1)));
			for (String fieldName : List.of("propertyName", "propertyValue")) {
				result.add(create(
						mutator,
						aSource,
						aTarget,
						Mutation.wrongFieldValue(
								"metadata_translated",
								fieldName)
								.inFile(fileName)
								.atOccurrence(1)));
			}
		}
		return List.copyOf(result);
	}

	private Path create(
			TraceFixtureMutator aMutator,
			Path aSource,
			Path aTarget,
			Mutation aMutation) throws IOException {
		return aMutator.createBrokenDataset(
				aSource,
				aTarget,
				List.of(aMutation)).directory();
	}

	public static void main(String[] args) throws IOException {
		Path source = args.length > 0 ? Path.of(args[0]) : DEFAULT_SOURCE;
		Path target = args.length > 1 ? Path.of(args[1]) : DEFAULT_TARGET;
		if (args.length > 2) {
			throw new IllegalArgumentException(
					"Expected optional arguments: <sourceDirectory> <targetRoot>");
		}
		List<Path> generated =
				new ResponseBehaviorBrokenTraceFixturesGenerator()
						.generate(source, target);
		System.out.println(
				"Generated "
						+ generated.size()
						+ " response behavior datasets under "
						+ target);
	}
}
