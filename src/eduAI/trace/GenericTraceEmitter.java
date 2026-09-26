package eduAI.trace;

import java.util.Map;

public class GenericTraceEmitter implements TraceEmitter {
	@Override
	public void traceThreadPattern(
			Enum<?> aThreadPatternName,
			Class<?> aSourceClass,
			Enum<?> anEventName,
			Map<String, String> anAuxiliaryData) {
		GenericTrace.traceThreadPattern(
				aThreadPatternName,
				aSourceClass,
				anEventName,
				anAuxiliaryData);
	}

	@Override
	public void traceDesignPattern(
			Enum<?> aDesignPatternName,
			Class<?> aSourceClass,
			Enum<?> anEventName,
			Map<String, String> anAuxiliaryData) {
		GenericTrace.traceDesignPattern(
				aDesignPatternName,
				aSourceClass,
				anEventName,
				anAuxiliaryData);
	}

	@Override
	public void tracePattern(
			Enum<?> aThreadPatternName,
			Enum<?> aDesignPatternName,
			Class<?> aSourceClass,
			Enum<?> anEventName,
			Map<String, String> anAuxiliaryData) {
		GenericTrace.tracePattern(
				aThreadPatternName,
				aDesignPatternName,
				aSourceClass,
				anEventName,
				anAuxiliaryData);
	}
}
