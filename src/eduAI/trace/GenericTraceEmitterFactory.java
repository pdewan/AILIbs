package eduAI.trace;

public class GenericTraceEmitterFactory implements TraceEmitterFactory {
	private final TraceEmitter traceEmitter = new GenericTraceEmitter();

	@Override
	public TraceEmitter getTraceEmitter() {
		return traceEmitter;
	}
}
