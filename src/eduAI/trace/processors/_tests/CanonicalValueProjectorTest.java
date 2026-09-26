package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.util.LinkedHashMap;
import java.util.List;

import eduAI.trace.processors.GenericTracesFileProcessor.TraceMessage;
import eduAI.trace.processors.GenericTracesFileProcessor.TracePart;

public class CanonicalValueProjectorTest {
	public static void main(String[] args) {
		new CanonicalValueProjectorTest()
				.testProjectionPreservesDataAndFiltersStructuralValues();
		System.out.println("Canonical value projector checks passed");
	}

	public void testProjectionPreservesDataAndFiltersStructuralValues() {
		LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
		metadata.put("inputTokens", "1185");
		CanonicalTraceResponse response = new CanonicalTraceResponse(
				List.of(new TraceMessage(
						"ASSISTANT",
						List.of(
								new TracePart("text", "Blue"),
								new TracePart("text", "Blue"),
								new TracePart("text", "ASSISTANT"),
								new TracePart("imageBytes", "4884")))),
				metadata);
		CanonicalValueProjector projector = new CanonicalValueProjector();
		List<CanonicalValueOccurrence> raw =
				projector.projectResponseContent(response);
		assertCount(raw, CanonicalValueKind.ROLE, "ASSISTANT", 1);
		assertCount(raw, CanonicalValueKind.PART_KIND, "text", 3);
		assertCount(raw, CanonicalValueKind.DATA, "Blue", 2);
		assertCount(raw, CanonicalValueKind.DATA, "ASSISTANT", 1);
		assertCount(raw, CanonicalValueKind.IMAGE_DATA, "4884", 1);

		ProviderIndependentValueIgnoreRegistry ignored =
				new ProviderIndependentValueIgnoreRegistry();
		ignored.register(CanonicalValueKind.ROLE, "ASSISTANT");
		ignored.register(CanonicalValueKind.PART_KIND, "text");
		ignored.register(CanonicalValueKind.PART_KIND, "imageBytes");
		List<CanonicalValueOccurrence> filtered = projector.withoutIgnoredValues(
				raw, ignored::isIgnored);
		assertCount(filtered, CanonicalValueKind.ROLE, "ASSISTANT", 0);
		assertCount(filtered, CanonicalValueKind.PART_KIND, "text", 0);
		assertCount(filtered, CanonicalValueKind.DATA, "ASSISTANT", 1);
		assertCount(filtered, CanonicalValueKind.DATA, "Blue", 2);
		assertCount(filtered, CanonicalValueKind.IMAGE_DATA, "4884", 1);

		List<CanonicalValueOccurrence> projectedMetadata =
				projector.projectMetadata(response);
		assertCount(
				projectedMetadata,
				CanonicalValueKind.PROPERTY_NAME,
				"inputTokens",
				1);
		assertCount(
				projectedMetadata,
				CanonicalValueKind.PROPERTY_VALUE,
				"1185",
				1);
	}

	private void assertCount(
			List<CanonicalValueOccurrence> someValues,
			CanonicalValueKind aKind,
			String aValue,
			long anExpectedCount) {
		long count = someValues.stream()
				.map(CanonicalValueOccurrence::canonicalValue)
				.filter(value -> value.kind() == aKind)
				.filter(value -> aValue.equals(value.value()))
				.count();
		if (count != anExpectedCount) {
			throw new AssertionError(
					"Expected " + anExpectedCount + " occurrences of "
							+ aKind + ":" + aValue + " but found " + count
							+ " in " + someValues);
		}
	}
}
