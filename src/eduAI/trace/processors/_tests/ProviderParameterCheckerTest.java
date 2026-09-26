package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import eduAI.trace.lib.StreamingMode;

public class ProviderParameterCheckerTest {
	public static void main(String[] args) throws IOException {
		assertCase("300000", "sample.NativeTarget", "timeout=Optional[300000]", true, true);
		assertCase("1000", "sample.NativeTarget", "timeout=Optional[300000]", false, true);
		assertCase("300000", "sample.NativeTarget", "timeout=Optional.empty", true, false);
		assertCase("300000", "sample.WrongTarget", "timeout=Optional[300000]", true, false);
		System.out.println("Provider parameter checks passed");
	}

	private static void assertCase(
			String aStoredValue,
			String aTarget,
			String aTargetState,
			boolean anExpectedGenericMatch,
			boolean anExpectedProviderAssignment) throws IOException {
		Path trace = Files.createTempFile("parameter-check", ".txt");
		Files.writeString(
				trace,
				"## lib {parameter_translated} [none: main] "
						+ "(single_model_request_processing: sample.ParameterProcessor) "
						+ "<parameterHandlerClass=\"sample.TimeoutAdapter\" "
						+ "providerDependentTarget=\""
						+ aTarget
						+ "\" providerDependentTargetState=\""
						+ aTargetState
						+ "\" "
						+ "propertyName=\"timeout_milliseconds\" "
						+ "propertyValue=\"300000\"> ##\n"
						+ "## lib {request_sent} [none: main] "
						+ "(single_model_request_processing: sample.Client) "
						+ "<provider=\"Provider\" "
						+ "providerIndependentParameterStore=\"fields={entry: sample.Property fields={name: java.lang.String value=\\\"timeout_milliseconds\\\", value: java.lang.Integer value="
						+ aStoredValue
						+ "}}\" providerIndependentContextWindow=\"elements=[]\" "
						+ "providerDependentConfiguration=\""
						+ aTargetState
						+ "\" providerDependentContextWindow=\"[]\"> ##\n",
				StandardCharsets.UTF_8);
		GenericTracesFileProcessor processor =
				new GenericTracesFileProcessor();
		processor.registerProviderParameterChecker(
				new TextProviderParameterChecker(
						"Provider",
						"timeout_milliseconds",
						"sample.NativeTarget",
						"timeout=Optional[300000]"));
		processor.processTraceFile(
				"Provider",
				StreamingMode.NON_STREAMING,
				trace);
		processor.checkProviderGenericParameterMatches(
				"Provider",
				StreamingMode.NON_STREAMING);
		assertMessage(
				processor,
				"generic_parameter_name_value_match",
				"match=" + anExpectedGenericMatch);
		assertMessage(
				processor,
				"provider_parameter_assigned",
				"assigned=" + anExpectedProviderAssignment);
		Files.deleteIfExists(trace);
	}

	private static void assertMessage(
			GenericTracesFileProcessor aProcessor,
			String aName,
			String anExpectedText) {
		for (String message : aProcessor.getCheckerMessages()) {
			if (message.contains(aName)
					&& message.contains(anExpectedText)) {
				return;
			}
		}
		throw new AssertionError(
				"Missing " + aName + " " + anExpectedText + " in "
						+ aProcessor.getCheckerMessages());
	}
}
