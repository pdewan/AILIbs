package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

public class ProviderIndependentValueIgnoreRegistryTest {
	public static void main(String[] args) {
		new ProviderIndependentValueIgnoreRegistryTest()
				.testTypedIgnoredValues();
		System.out.println(
				"Provider-independent value ignore registry checks passed");
	}

	public void testTypedIgnoredValues() {
		GenericTracesFileProcessor processor =
				new GenericTracesFileProcessor();
		assertIgnored(processor, CanonicalValueKind.ROLE, "ASSISTANT");
		assertIgnored(processor, CanonicalValueKind.PART_KIND, "text");
		assertNotIgnored(processor, CanonicalValueKind.DATA, "ASSISTANT");
		assertNotIgnored(processor, CanonicalValueKind.PROPERTY_NAME, "text");

		processor.registerProviderIndependentIgnoredValue(
				CanonicalValueKind.PROPERTY_VALUE, "framework-default");
		assertIgnored(
				processor,
				CanonicalValueKind.PROPERTY_VALUE,
				"framework-default");
		assertNotIgnored(
				processor,
				CanonicalValueKind.DATA,
				"framework-default");
	}

	private void assertIgnored(
			TraceFilesProcessor aProcessor,
			CanonicalValueKind aKind,
			String aValue) {
		if (!aProcessor.isProviderIndependentValueIgnored(
				new CanonicalValue(aKind, aValue))) {
			throw new AssertionError(
					"Expected ignored canonical value " + aKind + ":" + aValue);
		}
	}

	private void assertNotIgnored(
			TraceFilesProcessor aProcessor,
			CanonicalValueKind aKind,
			String aValue) {
		if (aProcessor.isProviderIndependentValueIgnored(
				new CanonicalValue(aKind, aValue))) {
			throw new AssertionError(
					"Canonical value was ignored in the wrong category "
							+ aKind + ":" + aValue);
		}
	}
}
