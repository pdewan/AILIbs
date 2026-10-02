package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import eduAI.trace.processors.mutations.TraceFixtureMutator.Mutation;

public class EssentialBrokenTraceFixturesGenerator {
	public static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	public static final Path DEFAULT_TARGET = Path.of("broken_filtered");

	private record EventField(String eventName, String fieldName) {
	}

	private static final List<EventField> IMPORTANT_FIELDS = List.of(
			field("server_handle_factory_fetched", "provider"),
			field("server_handle_factory_fetched", "serverHandleFactoryRegistryClass"),
			field("server_handle_factory_fetched", "serverHandleFactoryClass"),
			field("server_handle_factory_fetched", "serverHandleFactoryIdentity"),
			field("server_handle_fetched", "serverHandleFactoryIdentity"),
			field("server_handle_fetched", "serverHandleClass"),
			field("server_handle_fetched", "serverHandleIdentity"),
			field("request_sent", "mode"),
			field("request_sent", "providerIndependentParameterStore"),
			field("request_sent", "providerIndependentContextWindow"),
			field("request_sent", "providerDependentConfiguration"),
			field("request_sent", "providerDependentContextWindow"),
			field("message_translated", "providerDependentMessageClass"),
			field("message_translated", "providerIndependentMessage"),
			field("message_translated", "providerDependentMessage"),
			field("response_translated", "providerDependentResponse"),
			field("response_translated", "providerIndependentResponse"),
			field("parameter_translated", "parameterHandlerClass"),
			field("parameter_translated", "parameterHandler"),
			field("parameter_translated", "propertyName"),
			field("parameter_translated", "propertyValue"),
			field("parameter_translated", "providerDependentTarget"),
			field("parameter_translated", "providerDependentTargetState"),
			field("metadata_translated", "metadataHandlerClass"),
			field("metadata_translated", "metadataHandler"),
			field("metadata_translated", "propertyValue"),
			field("message_merger_factory_fetched", "streamChunkMergerFactoryRegistryClass"),
			field("message_merger_factory_fetched", "streamChunkMergerFactory"),
			field("message_merger_factory_fetched", "streamChunkMergerFactoryClass"),
			field("streaming_chunk_accumulated", "messageMergerClass"),
			field("streaming_chunk_accumulated", "messageMerger"),
			field("streaming_chunk_accumulated", "streamingChunk"),
			field("streaming_chunk_accumulated", "accumulatedMessage"),
			field("streaming_chunks_merged", "messageMergerClass"),
			field("streaming_chunks_merged", "messageMerger"),
			field("streaming_chunks_merged", "mergedMessage"),
			field("partial_response_callback_invoked", "streamingCallbackClass"),
			field("partial_response_callback_invoked", "streamingCallback"),
			field("partial_response_callback_invoked", "callbackArgument"),
			field("complete_response_callback_invoked", "streamingCallbackClass"),
			field("complete_response_callback_invoked", "streamingCallback"),
			field("complete_response_callback_invoked", "callbackArgument"));

	private static final List<String> SOURCE_CLASS_EVENTS = List.of(
			"message_translated",
			"response_translated",
			"partial_response_callback_invoked",
			"complete_response_callback_invoked");

	public List<Path> generate(String aSource, String aTarget)
			throws IOException {
		return generate(Path.of(aSource), Path.of(aTarget));
	}

	public List<Path> generate(Path aSource, Path aTarget)
			throws IOException {
		TraceFixtureMutator mutator = new TraceFixtureMutator();
		List<Path> generated = new ArrayList<>();

		for (String eventName : EssentialTraceEvents.all().stream().sorted().toList()) {
			generated.add(mutator.createBrokenDataset(
					aSource,
					aTarget,
					List.of(Mutation.removeEvent(eventName))).directory());
		}
		for (EventField eventField : IMPORTANT_FIELDS) {
			generated.add(mutator.createBrokenDataset(
					aSource,
					aTarget,
					List.of(Mutation.wrongFieldValue(
							eventField.eventName(),
							eventField.fieldName()))).directory());
		}
		for (String eventName : SOURCE_CLASS_EVENTS) {
			generated.add(mutator.createBrokenDataset(
					aSource,
					aTarget,
					List.of(Mutation.removeSourceClass(eventName))).directory());
		}

		generated.add(combination(
				mutator, aSource, aTarget,
				Mutation.wrongFieldValue("parameter_translated", "parameterHandlerClass"),
				Mutation.wrongFieldValue("parameter_translated", "parameterHandler")));
		generated.add(combination(
				mutator, aSource, aTarget,
				Mutation.wrongFieldValue("metadata_translated", "metadataHandlerClass"),
				Mutation.wrongFieldValue("metadata_translated", "metadataHandler")));
		generated.add(combination(
				mutator, aSource, aTarget,
				Mutation.wrongFieldValue("streaming_chunk_accumulated", "messageMergerClass"),
				Mutation.wrongFieldValue("streaming_chunk_accumulated", "messageMerger")));
		generated.add(combination(
				mutator, aSource, aTarget,
				Mutation.wrongFieldValue("request_sent", "providerIndependentContextWindow"),
				Mutation.wrongFieldValue("request_sent", "providerDependentContextWindow")));
		return List.copyOf(generated);
	}

	private Path combination(
			TraceFixtureMutator aMutator,
			Path aSource,
			Path aTarget,
			Mutation... someMutations) throws IOException {
		return aMutator.createBrokenDataset(
				aSource,
				aTarget,
				List.of(someMutations)).directory();
	}

	private static EventField field(String anEventName, String aFieldName) {
		return new EventField(anEventName, aFieldName);
	}

	public static void main(String[] args) throws IOException {
		Path source = args.length > 0 ? Path.of(args[0]) : DEFAULT_SOURCE;
		Path target = args.length > 1 ? Path.of(args[1]) : DEFAULT_TARGET;
		if (args.length > 2) {
			throw new IllegalArgumentException(
					"Expected optional arguments: <sourceDirectory> <targetRoot>");
		}
		List<Path> generated =
				new EssentialBrokenTraceFixturesGenerator().generate(source, target);
		System.out.println(
				"Generated "
						+ generated.size()
						+ " broken trace datasets under "
						+ target);
	}
}
