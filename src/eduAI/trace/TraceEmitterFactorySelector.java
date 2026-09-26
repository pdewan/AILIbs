package eduAI.trace;

public final class TraceEmitterFactorySelector {
	private static TraceEmitterFactory traceEmitterFactory =
			new GenericTraceEmitterFactory();

	private TraceEmitterFactorySelector() {
	}

	public static TraceEmitter getTraceEmitter() {
		return traceEmitterFactory.getTraceEmitter();
	}

	public static void setTraceEmitterFactory(
			TraceEmitterFactory aTraceEmitterFactory) {
		if (aTraceEmitterFactory == null) {
			throw new IllegalArgumentException(
					"Trace emitter factory must not be null");
		}
		traceEmitterFactory = aTraceEmitterFactory;
	}
}
