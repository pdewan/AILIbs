package eduAI.trace.lib._tests;

import java.util.LinkedHashMap;
import java.util.Map;

import eduAI.trace.lib.LibDesignPattern;
import eduAI.trace.lib.LibTrace;
import eduAI.trace.lib.LibTraceActions;
import eduAI.trace.lib.LibTraceEvent;
import eduAI.trace.lib.StreamingMode;

public class LibTraceNullArgumentTest {
	public static void main(String[] args) {
		LibTraceNullArgumentTest test = new LibTraceNullArgumentTest();
		test.testCommonTraceBoundaryRejectsNulls();
		test.testTraceActionRejectsNullObjectArguments();
		System.out.println("Lib trace null argument test passed");
	}

	public void testCommonTraceBoundaryRejectsNulls() {
		expectNull(() -> LibTrace.traceDesignPattern(
				null,
				getClass(),
				LibTraceEvent.REQUEST_SENT,
				Map.of()));
		expectNull(() -> LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				null,
				LibTraceEvent.REQUEST_SENT,
				Map.of()));
		expectNull(() -> LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				getClass(),
				null,
				Map.of()));
		expectNull(() -> LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				getClass(),
				LibTraceEvent.REQUEST_SENT,
				null));
		Map<String, String> nullValue = new LinkedHashMap<>();
		nullValue.put("required", null);
		expectNull(() -> LibTrace.traceDesignPattern(
				LibDesignPattern.SINGLE_MODEL_REQUEST_PROCESSING,
				getClass(),
				LibTraceEvent.REQUEST_SENT,
				nullValue));
	}

	public void testTraceActionRejectsNullObjectArguments() {
		expectNull(() -> LibTraceActions.traceRequestSent(
				getClass(),
				StreamingMode.NON_STREAMING,
				null,
				new Object(),
				new Object(),
				new Object()));
	}

	private void expectNull(Runnable anAction) {
		try {
			anAction.run();
			throw new AssertionError("Expected NullPointerException");
		} catch (NullPointerException expected) {
			// Expected.
		}
	}
}
