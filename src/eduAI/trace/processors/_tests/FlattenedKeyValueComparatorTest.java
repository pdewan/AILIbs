package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.util.Map;

public class FlattenedKeyValueComparatorTest {
	public static void main(String[] args) {
		FlattenedKeyValueComparator comparator =
				new FlattenedKeyValueComparator();
		String dump = "store: arbitrary.Store fields={names: arbitrary.Values "
				+ "elements=[[0]: java.lang.String value=\"temperature\"], "
				+ "data: arbitrary.Values elements=[[0]: java.lang.Double value=0.4]}";
		KeyValueComparison match = comparator.compare(
				dump, Map.of("temperature", "0.4"));
		require(match.matches(), match.detail());

		KeyValueComparison missingKey = comparator.compare(
				dump, Map.of("timeout", "0.4"));
		require(!missingKey.matches(), "Missing key was accepted");
		require(missingKey.missingKeys().equals(java.util.List.of("timeout")),
				"Missing key was not diagnosed");

		KeyValueComparison missingValue = comparator.compare(
				dump, Map.of("temperature", "0.7"));
		require(!missingValue.matches(), "Wrong value was accepted");
		require(missingValue.missingValues().equals(
				java.util.List.of("temperature=0.7")),
				"Missing value was not diagnosed");
		System.out.println("Flattened key-value comparator checks passed");
	}

	private static void require(boolean aCondition, String aMessage) {
		if (!aCondition) {
			throw new AssertionError(aMessage);
		}
	}
}
