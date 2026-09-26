package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

public class ParameterBehaviorBrokenTraceFixturesGenerator {
	public static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	public static final Path DEFAULT_TARGET =
			Path.of("parameter_behavior_broken");

	private static final List<String> PARAMETER_FIELDS = List.of(
			"propertyName",
			"propertyValue",
			"providerDependentTarget",
			"providerDependentTargetState");

	public List<Path> generate(Path aSource, Path aTarget)
			throws IOException {
		TraceFixtureMutator mutator = new TraceFixtureMutator();
		ArrayList<Path> result = new ArrayList<>();
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			result.add(create(
					mutator,
					aSource,
					aTarget,
					Mutation.removeEvent("parameter_translated")
							.inFile(fileName)
							.atOccurrence(1)));
			for (String fieldName : PARAMETER_FIELDS) {
				result.add(create(
						mutator,
						aSource,
						aTarget,
						Mutation.wrongFieldValue(
								"parameter_translated",
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
				new ParameterBehaviorBrokenTraceFixturesGenerator()
						.generate(source, target);
		System.out.println(
				"Generated "
						+ generated.size()
						+ " parameter behavior datasets under "
						+ target);
	}
}
