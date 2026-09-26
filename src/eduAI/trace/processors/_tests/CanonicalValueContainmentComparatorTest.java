package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.util.List;

public class CanonicalValueContainmentComparatorTest {
	public static void main(String[] args) {
		CanonicalValueContainmentComparator comparator =
				new CanonicalValueContainmentComparator();
		List<CanonicalValueOccurrence> expected = List.of(
				occurrence(CanonicalValueKind.ROLE, "ASSISTANT", "message.role"),
				occurrence(CanonicalValueKind.PART_KIND, "text", "part.kind"),
				occurrence(CanonicalValueKind.DATA, "Blue", "part[0].value"),
				occurrence(CanonicalValueKind.DATA, "Blue", "part[1].value"));
		ProviderIndependentValueIgnoreRegistry ignored =
				new ProviderIndependentValueIgnoreRegistry();
		ignored.register(CanonicalValueKind.ROLE, "ASSISTANT");
		ignored.register(CanonicalValueKind.PART_KIND, "text");

		CanonicalValueComparison matching = comparator.compare(
				"response: arbitrary.Response fields={second: java.lang.String value=\"Blue\", first: java.lang.String value=\"Blue\"}",
				expected,
				ignored::isIgnored);
		require(matching.matches(), matching.detail());

		CanonicalValueComparison missingDuplicate = comparator.compare(
				"response: arbitrary.Response fields={only: java.lang.String value=\"Blue\"}",
				expected,
				ignored::isIgnored);
		require(!missingDuplicate.matches(), "A missing duplicate was accepted");
		require(missingDuplicate.missingValues().size() == 1,
				"Expected exactly one missing occurrence");
		require(missingDuplicate.detail().contains("part[1].value"),
				"Missing location was not reported");

		CanonicalValueComparison combined = comparator.compare(
				"window: arbitrary.Window fields={first: java.lang.String "
						+ "value=\"alpha\", second: java.lang.String value=\"beta\"}",
				List.of(occurrence(
						CanonicalValueKind.DATA, "alphabeta", "system.value")),
				value -> false);
		require(combined.matches(),
				"A native value composed from generic values was rejected: "
						+ combined.detail());

		System.out.println("Canonical value containment comparator checks passed");
	}

	private static CanonicalValueOccurrence occurrence(
			CanonicalValueKind aKind,
			String aValue,
			String aLocation) {
		return new CanonicalValueOccurrence(
				new CanonicalValue(aKind, aValue), aLocation);
	}

	private static void require(boolean aCondition, String aMessage) {
		if (!aCondition) {
			throw new AssertionError(aMessage);
		}
	}
}
