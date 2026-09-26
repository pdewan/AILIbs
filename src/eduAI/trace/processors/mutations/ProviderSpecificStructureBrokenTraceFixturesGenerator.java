package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

public class ProviderSpecificStructureBrokenTraceFixturesGenerator {
	public static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	public static final Path DEFAULT_TARGET =
			Path.of("provider_structure_broken");

	public List<Path> generate(Path aSource, Path aTarget)
			throws IOException {
		TraceFixtureMutator mutator = new TraceFixtureMutator();
		List<Path> result = new ArrayList<>();
		for (String provider : List.of("Gemini", "Ollama")) {
			String fileName = "Gemini".equals(provider)
					? "TraceGeminiBridgeNonStreamingDemo.txt"
					: "TraceOllamaNonStreamingBridgeDemo.txt";
			result.add(generate(
					mutator,
					aSource,
					aTarget,
					copy(
							"request_sent",
							"providerDependentContextWindow",
							"providerIndependentContextWindow",
							fileName)));
			result.add(generate(
					mutator,
					aSource,
					aTarget,
					copy(
							"request_sent",
							"providerDependentConfiguration",
							"providerIndependentParameterStore",
							fileName)));
			result.add(generate(
					mutator,
					aSource,
					aTarget,
					copy(
							"response_translated",
							"providerDependentResponse",
							"providerIndependentResponse",
							fileName)));
		}
		return List.copyOf(result);
	}

	private Mutation copy(
			String anEvent,
			String aSourceField,
			String aTargetField,
			String aFileName) {
		return Mutation.copyFieldValue(
				anEvent,
				aSourceField,
				aTargetField)
				.inFile(aFileName)
				.atOccurrence(1);
	}

	private Path generate(
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
		for (Path directory :
				new ProviderSpecificStructureBrokenTraceFixturesGenerator()
						.generate(source, target)) {
			System.out.println(directory);
		}
	}
}
