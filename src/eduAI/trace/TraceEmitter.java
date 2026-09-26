package eduAI.trace;

import java.util.Map;

public interface TraceEmitter {
	void traceThreadPattern(
			Enum<?> aThreadPatternName,
			Class<?> aSourceClass,
			Enum<?> anEventName,
			Map<String, String> anAuxiliaryData);

	void traceDesignPattern(
			Enum<?> aDesignPatternName,
			Class<?> aSourceClass,
			Enum<?> anEventName,
			Map<String, String> anAuxiliaryData);

	void tracePattern(
			Enum<?> aThreadPatternName,
			Enum<?> aDesignPatternName,
			Class<?> aSourceClass,
			Enum<?> anEventName,
			Map<String, String> anAuxiliaryData);
}
