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
		String first = "First system instruction with a long middle and ending.";
		String second = "Second instruction with a different middle and ending.";
		String firstToken = eduAI.trace.TraceTextSummary.fromEdges(first).token();
		String secondToken = eduAI.trace.TraceTextSummary.fromEdges(second).token();
		String dump = "window: arbitrary.Window fields={one: java.lang.String value=\"" + firstToken
				+ "\", two: java.lang.String value=\"" + secondToken + "\"}";
		var compact = List.of(occurrence(CanonicalValueKind.DATA,
				eduAI.trace.TraceTextSummary.fromEdges(first + second).token(), "system.value"));
		require(comparator.compare(dump, compact, value -> false).matches(), "Combined system summaries rejected");
		require(!comparator.compare(dump.replace(secondToken, firstToken), compact, value -> false).matches(), "Duplicated system prompt accepted");
		require(!comparator.compare(dump.replace(firstToken, secondToken), compact, value -> false).matches(), "Changed system prefix accepted");
		require(!comparator.compare(dump.replace(secondToken, ""), compact, value -> false).matches(), "Missing system prompt accepted");
		var reversed = List.of(occurrence(CanonicalValueKind.DATA,
				eduAI.trace.TraceTextSummary.fromEdges(second + first).token(), "system.value"));
		require(!comparator.compare(dump, reversed, value -> false).matches(), "Reordered system prompts accepted");
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
