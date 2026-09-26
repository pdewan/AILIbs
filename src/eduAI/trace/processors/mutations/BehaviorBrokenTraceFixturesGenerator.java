package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

public class BehaviorBrokenTraceFixturesGenerator {
	public static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	public static final Path DEFAULT_TARGET = Path.of("behavior_broken");

	public List<Path> generate(Path aSource, Path aTarget)
			throws IOException {
		TraceFixtureMutator mutator = new TraceFixtureMutator();
		List<Path> generated = new ArrayList<>();
		for (String provider : List.of("Gemini", "Ollama")) {
			String traceFile = "Gemini".equals(provider)
					? "TraceGeminiBridgeStreamingDemo.txt"
					: "TraceOllamaStreamingBridgeDemo.txt";
			String nonStreamingTraceFile = "Gemini".equals(provider)
					? "TraceGeminiBridgeNonStreamingDemo.txt"
					: "TraceOllamaNonStreamingBridgeDemo.txt";
			generated.add(generate(
					mutator,
					aSource,
					aTarget,
					Mutation.wrongFieldValue(
							"partial_response_callback_invoked",
							"callbackArgument")
							.inFile(traceFile)));
			generated.add(generate(
					mutator,
					aSource,
					aTarget,
					Mutation.wrongFieldValue(
							"complete_response_callback_invoked",
							"callbackArgument")
							.inFile(traceFile)));
			generated.add(generate(
					mutator,
					aSource,
					aTarget,
					Mutation.wrongFieldValue(
							"request_sent",
							"providerDependentContextWindow")
							.inFile(nonStreamingTraceFile)
							.atOccurrence(1)));
			generated.add(generate(
					mutator,
					aSource,
					aTarget,
					Mutation.wrongFieldValue(
							"streaming_chunk_accumulated",
							"streamingChunk")
							.inFile(traceFile)));
		}
		generated.add(generate(
				mutator,
				aSource,
				aTarget,
				Mutation.wrongFieldValue(
						"request_sent",
						"providerIndependentParameterStore")
						.inFile("TraceGeminiBridgeNonStreamingDemo.txt")
						.atOccurrence(1)));
		generated.add(generate(
				mutator,
				aSource,
				aTarget,
				Mutation.wrongFieldValue(
						"response_translated",
						"providerIndependentResponse")
						.inFile("TraceGeminiBridgeNonStreamingDemo.txt")
						.atOccurrence(1)));
		return List.copyOf(generated);
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
		List<Path> generated =
				new BehaviorBrokenTraceFixturesGenerator()
						.generate(source, target);
		for (Path directory : generated) {
			System.out.println(directory);
		}
	}
}
