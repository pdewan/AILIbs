package eduAI.trace._tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import eduAI.lib._tests.TestTrace;
import eduAI.trace.GenericTrace;
import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;
import eduAI.trace.TraceObjectPrinter;
import eduAI.trace._tests.fake.FakeDesignPattern;
import eduAI.trace._tests.fake.FakeEvent;
import eduAI.trace._tests.fake.FakeThreadPattern;
import eduAI.trace._tests.other.OtherLayerEvent;

public class CoreGenericTraceTest {
	private static final boolean TRACE = true;

	public static void main(String[] args) {
		CoreGenericTraceTest test = new CoreGenericTraceTest();
		TestTrace.programGoal(
				TRACE,
				test,
				"test generic trace formatting and parsing without layer-specific vocabularies");
		TestTrace.run(
				TRACE,
				test,
				"testGenericTraceDerivesLayerFromEnumPackage",
				"generic trace derives the layer from enum package and emits enum-name tokens",
				() -> test.testGenericTraceDerivesLayerFromEnumPackage());
		TestTrace.run(
				TRACE,
				test,
				"testGenericTraceRejectsMixedEnumLayers",
				"generic trace refuses event, thread-pattern, and design-pattern enums from different packages",
				() -> test.testGenericTraceRejectsMixedEnumLayers());
		TestTrace.run(
				TRACE,
				test,
				"testGenericTraceCanBeGloballyDisabled",
				"generic trace has an API-level master switch for clients without framework properties",
				() -> test.testGenericTraceCanBeGloballyDisabled());
		TestTrace.run(
				TRACE,
				test,
				"testGenericTraceCanWriteOnlyToFile",
				"generic trace can route traces to a file without writing to output",
				() -> test.testGenericTraceCanWriteOnlyToFile());
		TestTrace.run(
				TRACE,
				test,
				"testGenericTraceCanWriteToFileAndOutput",
				"generic trace can route traces to both file and output",
				() -> test.testGenericTraceCanWriteToFileAndOutput());
		TestTrace.run(
				TRACE,
				test,
				"testGenericTraceStartsFreshTraceFileOnFirstWrite",
				"generic trace starts a fresh trace file on the first write and appends later writes",
				() -> test
						.testGenericTraceStartsFreshTraceFileOnFirstWrite());
		TestTrace.run(
				TRACE,
				test,
				"testGeneratedTraceEndsWithExplicitTerminator",
				"generic trace emits an explicit record terminator",
				() -> test
						.testGeneratedTraceEndsWithExplicitTerminator());
		TestTrace.run(
				TRACE,
				test,
				"testParserExtractsEmbeddedTerminatedTrace",
				"generic trace parser extracts terminated trace records embedded in streaming output",
				() -> test
						.testParserExtractsEmbeddedTerminatedTrace());
		TestTrace.run(
				TRACE,
				test,
				"testTraceObjectPrinterLabelsObjectsAndDetectsCycles",
				"trace object printer labels variables, prints immediate interfaces, recurses through fields, and detects cycles",
				() -> test
						.testTraceObjectPrinterLabelsObjectsAndDetectsCycles());
		TestTrace.run(
				TRACE,
				test,
				"testTraceObjectPrinterPreservesLongStrings",
				"trace object printer preserves complete string values",
				() -> test.testTraceObjectPrinterPreservesLongStrings());
		TestTrace.run(
				TRACE,
				test,
				"testTraceObjectPrinterUsesRegisteredExternalFormatter",
				"trace object printer uses table-driven external formatter registrations",
				() -> test
						.testTraceObjectPrinterUsesRegisteredExternalFormatter());
	}

	public void testGenericTraceDerivesLayerFromEnumPackage() {
		String oldName = Thread.currentThread().getName();
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			Thread.currentThread().setName("fake worker");
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			GenericTrace.traceDesignPattern(
				FakeDesignPattern.WORKFLOW_STEP,
				CoreGenericTraceTest.class,
				FakeEvent.THING_STARTED,
				Map.of(
							"note",
							"value with spaces",
							"markup",
							"<quoted> \"text\""));
		} finally {
			System.setOut(oldOut);
			Thread.currentThread().setName(oldName);
		}
		String line = firstLine(bytes.toString(StandardCharsets.UTF_8));
		trace("generic trace line: " + line);
		TraceLine parsed = TraceLineParser.parse(line);
		assertEquals("fake", parsed.getLayerName());
		assertEquals("thing_started", parsed.getEventName());
		assertEquals("none", parsed.getThreadPatternName());
		assertEquals("fake worker", parsed.getThreadName());
		assertEquals("workflow_step", parsed.getDesignPatternName());
		assertEquals(
				"eduAI.trace._tests.CoreGenericTraceTest",
				parsed.getSourceClassName());
		assertEquals(
				"value with spaces",
				parsed.getAuxiliaryData().get("note"));
		assertEquals(
				"<quoted> \"text\"",
				parsed.getAuxiliaryData().get("markup"));
	}

	public void testGeneratedTraceEndsWithExplicitTerminator() {
		String oldName = Thread.currentThread().getName();
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			Thread.currentThread().setName("terminator worker");
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			GenericTrace.traceDesignPattern(
				FakeDesignPattern.WORKFLOW_STEP,
				CoreGenericTraceTest.class,
				FakeEvent.THING_STARTED,
				Map.of("hashes", "before ## after"));
		} finally {
			System.setOut(oldOut);
			Thread.currentThread().setName(oldName);
		}
		String line = firstLine(bytes.toString(StandardCharsets.UTF_8));
		trace("terminated generic trace line: " + line);
		assertEquals(true, line.endsWith(" ##"));
		assertEquals(true, line.contains("\\x23\\x23"));
		TraceLine parsed = TraceLineParser.parse(line);
		assertEquals(
				"before ## after",
				parsed.getAuxiliaryData().get("hashes"));
	}

	public void testParserExtractsEmbeddedTerminatedTrace() {
		String text =
				"Yes"
				+ "## lib {response_translated} [none: main] "
				+ "(single_model_request_processing: Sender) "
				+ "<chunk=\"hello\"> ##"
				+ "more streamed text";
		TraceLine parsed = TraceLineParser.parse(text);
		assertEquals("lib", parsed.getLayerName());
		assertEquals("response_translated", parsed.getEventName());
		assertEquals(
				"single_model_request_processing",
				parsed.getDesignPatternName());
		assertEquals("Sender", parsed.getSourceClassName());
		assertEquals("hello", parsed.getAuxiliaryData().get("chunk"));
		assertEquals(
				"## lib {response_translated} [none: main] "
						+ "(single_model_request_processing: Sender) "
						+ "<chunk=\"hello\"> ##",
				parsed.getRawLine());
	}

	public void testGenericTraceRejectsMixedEnumLayers() {
		try {
			GenericTrace.traceDesignPattern(
				FakeDesignPattern.WORKFLOW_STEP,
				CoreGenericTraceTest.class,
				OtherLayerEvent.THING_FINISHED,
				Map.of());
			throw new AssertionError(
					"mixed enum layers should be rejected");
		} catch (IllegalArgumentException expected) {
			trace("mixed layer rejection: " + expected.getMessage());
		}
	}

	public void testGenericTraceCanBeGloballyDisabled() {
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			GenericTrace.resetTraceDestinations();
			GenericTrace.setEnabled(false);
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			GenericTrace.traceThreadPattern(
					FakeThreadPattern.WORKER_THREADS,
					CoreGenericTraceTest.class,
					FakeEvent.THING_STARTED,
					Map.of());
		} finally {
			System.setOut(oldOut);
			GenericTrace.setEnabled(true);
		}
		assertEquals("", bytes.toString(StandardCharsets.UTF_8));
	}

	public void testGenericTraceCanWriteOnlyToFile() {
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Path traceFile = createTempTraceFile();
		try {
			GenericTrace.resetTraceDestinations();
			GenericTrace.setTraceFile(traceFile);
			GenericTrace.setOutputEnabled(false);
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			GenericTrace.traceThreadPattern(
					FakeThreadPattern.WORKER_THREADS,
					CoreGenericTraceTest.class,
					FakeEvent.THING_STARTED,
					Map.of("route", "file"));
		} finally {
			System.setOut(oldOut);
			GenericTrace.resetTraceDestinations();
		}
		assertEquals("", bytes.toString(StandardCharsets.UTF_8));
		String fileOutput = readString(traceFile);
		trace("file-only generic trace output: " + fileOutput.trim());
		TraceLine parsed = TraceLineParser.parse(firstLine(fileOutput));
		assertEquals("file", parsed.getAuxiliaryData().get("route"));
		deleteTempTraceFile(traceFile);
	}

	public void testGenericTraceCanWriteToFileAndOutput() {
		PrintStream oldOut = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Path traceFile = createTempTraceFile();
		try {
			GenericTrace.resetTraceDestinations();
			GenericTrace.setTraceFile(traceFile);
			System.setOut(
					new PrintStream(
							bytes,
							true,
							StandardCharsets.UTF_8));
			GenericTrace.traceThreadPattern(
					FakeThreadPattern.WORKER_THREADS,
					CoreGenericTraceTest.class,
					FakeEvent.THING_STARTED,
					Map.of("route", "both"));
		} finally {
			System.setOut(oldOut);
			GenericTrace.resetTraceDestinations();
		}
		String output = bytes.toString(StandardCharsets.UTF_8);
		String fileOutput = readString(traceFile);
		trace("output generic trace line: " + output.trim());
		trace("file generic trace line: " + fileOutput.trim());
		assertEquals(firstLine(output), firstLine(fileOutput));
		TraceLine parsed = TraceLineParser.parse(firstLine(output));
		assertEquals("both", parsed.getAuxiliaryData().get("route"));
		deleteTempTraceFile(traceFile);
	}

	public void testGenericTraceStartsFreshTraceFileOnFirstWrite() {
		Path traceFile = createTempTraceFile();
		try {
			writeString(
					traceFile,
					"old trace without terminator"
							+ System.lineSeparator());
			GenericTrace.resetTraceDestinations();
			GenericTrace.setTraceFile(traceFile);
			GenericTrace.setOutputEnabled(false);
			GenericTrace.traceThreadPattern(
					FakeThreadPattern.WORKER_THREADS,
					CoreGenericTraceTest.class,
					FakeEvent.THING_STARTED,
					Map.of("route", "fresh"));
			GenericTrace.traceThreadPattern(
					FakeThreadPattern.WORKER_THREADS,
					CoreGenericTraceTest.class,
					FakeEvent.THING_STARTED,
					Map.of("route", "appended"));
			String fileOutput = readString(traceFile);
			trace("fresh trace file output: " + fileOutput.trim());
			assertEquals(false, fileOutput.contains("old trace"));
			assertEquals(true, firstLine(fileOutput).endsWith(" ##"));
			assertEquals(2, fileOutput.split("\\R").length);
		} finally {
			GenericTrace.resetTraceDestinations();
			deleteTempTraceFile(traceFile);
		}
	}

	public void testTraceObjectPrinterLabelsObjectsAndDetectsCycles() {
		ThrowingToStringObject object = new ThrowingToStringObject();
		object.number = 7;
		object.text = "hello";
		object.nested = new NestedObject("child");
		object.pattern = Pattern.compile("a.*");
		object.values.add("first");
		object.self = object;
		String formatted = TraceObjectPrinter.format("studentObject", object);
		trace("reflective object trace: " + formatted);
		assertContains(
				"studentObject: eduAI.trace._tests.CoreGenericTraceTest$ThrowingToStringObject",
				formatted);
		assertContains(
				"interfaces=[eduAI.trace._tests.CoreGenericTraceTest$SampleInterface]",
				formatted);
		assertContains(
				"toString=<toString-threw:java.lang.AssertionError>",
				formatted);
		assertContains("number: java.lang.Integer", formatted);
		assertContains("value=7", formatted);
		assertContains("text: java.lang.String", formatted);
		assertContains("value=\"hello\"", formatted);
		assertContains(
				"nested: eduAI.trace._tests.CoreGenericTraceTest$NestedObject",
				formatted);
		assertContains("name: java.lang.String", formatted);
		assertContains("pattern: java.util.regex.Pattern", formatted);
		assertContains("toString=\"a.*\"", formatted);
		assertContains("values: java.util.ArrayList", formatted);
		assertContains(
				"self: eduAI.trace._tests.CoreGenericTraceTest$ThrowingToStringObject",
				formatted);
		assertContains("value=<cycle>", formatted);
	}

	public void testTraceObjectPrinterUsesRegisteredExternalFormatter() {
		try {
			TraceObjectPrinter.registerExternalFormatter(
					NestedObject.class,
					object -> "nested:"
							+ ((NestedObject) object).name);
			String formatted =
					TraceObjectPrinter.format(
							"externalLikeObject",
							new NestedObject("table"));
			trace("registered formatter trace: " + formatted);
			assertContains(
					"externalLikeObject: eduAI.trace._tests.CoreGenericTraceTest$NestedObject",
					formatted);
			assertContains("formatted=\"nested:table\"", formatted);
			assertEquals(
					true,
					TraceObjectPrinter.externalPackagePrefixes()
							.contains("eduAI.eliza."));
		} finally {
			TraceObjectPrinter.clearExternalFormatters();
		}
	}

	public void testTraceObjectPrinterPreservesLongStrings() {
		String value = "0123456789".repeat(40);
		String formatted = TraceObjectPrinter.format("longText", value);
		assertContains("value=\"" + eduAI.trace.TraceTextSummary.from(value).token() + "\"", formatted);
		assertContains("value=\"" + value + "\"", TraceObjectPrinter.formatFullEvidence("longText", value));
	}

	private Path createTempTraceFile() {
		try {
			Path result = Files.createTempFile(
					"educopilot-generic-trace-",
					".txt");
			Files.deleteIfExists(result);
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String readString(Path aPath) {
		try {
			return Files.readString(aPath, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void writeString(Path aPath, String aText) {
		try {
			Files.writeString(
					aPath,
					aText,
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void deleteTempTraceFile(Path aPath) {
		try {
			Files.deleteIfExists(aPath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String firstLine(String anOutput) {
		String[] lines = anOutput.split("\\R");
		for (String line : lines) {
			if (line.startsWith(GenericTrace.TRACE_PREFIX)) {
				return line;
			}
		}
		return "";
	}

	private void trace(String aMessage) {
		TestTrace.trace(TRACE, this, aMessage);
	}

	private void assertEquals(Object anExpected, Object anActual) {
		if (anExpected == null
				? anActual != null
				: !anExpected.equals(anActual)) {
			throw new AssertionError(
					"Expected <" + anExpected + "> but got <"
							+ anActual + ">");
		}
	}

	private void assertContains(String anExpected, String anActual) {
		if (anActual == null || !anActual.contains(anExpected)) {
			throw new AssertionError(
					"Expected <" + anActual + "> to contain <"
							+ anExpected + ">");
		}
	}

	private interface SampleInterface {
	}

	private static class ThrowingToStringObject
			implements SampleInterface {
		private int number;
		private String text;
		private NestedObject nested;
		private Pattern pattern;
		private final List<String> values = new ArrayList<>();
		private ThrowingToStringObject self;

		@Override
		public String toString() {
			throw new AssertionError("toString should not be called");
		}
	}

	private static class NestedObject {
		private final String name;

		private NestedObject(String aName) {
			name = aName;
		}
	}
}
