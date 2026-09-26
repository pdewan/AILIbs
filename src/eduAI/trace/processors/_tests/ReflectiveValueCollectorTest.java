package eduAI.trace.processors._tests;

import eduAI.trace.processors.*;
import eduAI.trace.processors.mutations.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eduAI.trace.TraceObjectPrinter;
import eduAI.trace.processors.ReflectiveValueCollector.ValueKind;
import eduAI.trace.processors.ReflectiveValueCollector.ValueOccurrence;

public class ReflectiveValueCollectorTest {
	private enum SpeakerCode {
		ASSISTANT
	}

	private static class ArbitraryResponse {
		private final String payload = "Blue";
		private final SpeakerCode speakerCode = SpeakerCode.ASSISTANT;
		private final List<String> repeated = List.of("echo", "echo");
		private final Map<String, Object> attributes = attributes();
		private ArbitraryResponse cycle = this;

		private static Map<String, Object> attributes() {
			LinkedHashMap<String, Object> result = new LinkedHashMap<>();
			result.put("count", 3);
			result.put("complete", true);
			return result;
		}
	}

	public static void main(String[] args) {
		new ReflectiveValueCollectorTest().testCollectsValuesOnly();
		System.out.println("Reflective value collector checks passed");
	}

	public void testCollectsValuesOnly() {
		String dump = TraceObjectPrinter.format(
				"genericResponse", new ArbitraryResponse());
		List<ValueOccurrence> values =
				new ReflectiveValueCollector().collect(dump);
		assertContains(values, ValueKind.STRING, "Blue", 1);
		assertContains(values, ValueKind.ENUM_OR_SYMBOL, "ASSISTANT", 1);
		assertContains(values, ValueKind.STRING, "echo", 2);
		assertContains(values, ValueKind.STRING, "count", 1);
		assertContains(values, ValueKind.NUMBER, "3", 1);
		assertContains(values, ValueKind.STRING, "complete", 1);
		assertContains(values, ValueKind.BOOLEAN, "true", 1);
		ValueOccurrence blue = values.stream()
				.filter(value -> "Blue".equals(value.value()))
				.findFirst()
				.orElseThrow();
		if (!blue.typesOnPath().contains(ArbitraryResponse.class.getName())
				|| !blue.typesOnPath().contains(String.class.getName())
				|| !blue.path().contains("payload")) {
			throw new AssertionError(
					"Collector did not retain the typed path: " + blue);
		}

		List<String> collected = new ArrayList<>();
		for (ValueOccurrence value : values) {
			collected.add(value.value());
		}
		for (String forbidden : List.of(
				"payload",
				"speakerCode",
				"repeated",
				"attributes",
				"ArbitraryResponse",
				"<cycle>")) {
			if (collected.contains(forbidden)) {
				throw new AssertionError(
						"Collector included structural text: " + forbidden);
			}
		}
	}

	private void assertContains(
			List<ValueOccurrence> someValues,
			ValueKind aKind,
			String aValue,
			int anExpectedCount) {
		long count = someValues.stream()
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
