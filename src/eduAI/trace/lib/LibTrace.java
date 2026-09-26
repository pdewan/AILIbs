package eduAI.trace.lib;

import java.util.Map;
import java.util.Objects;

import eduAI.trace.TraceEmitterFactorySelector;

public final class LibTrace {
	private LibTrace() {
	}

	public static void traceThreadPattern(
			LibThreadPattern aThreadPattern,
			Class<?> aSourceClass,
			LibTraceEvent anEvent,
			Map<String, String> anAuxiliaryData) {
		validate(
				Objects.requireNonNull(aThreadPattern, "threadPattern"),
				aSourceClass,
				anEvent,
				anAuxiliaryData);
		TraceEmitterFactorySelector.getTraceEmitter().traceThreadPattern(
				aThreadPattern,
				aSourceClass,
				anEvent,
				anAuxiliaryData);
	}

	public static void traceDesignPattern(
			LibDesignPattern aDesignPattern,
			Class<?> aSourceClass,
			LibTraceEvent anEvent,
			Map<String, String> anAuxiliaryData) {
		validate(
				Objects.requireNonNull(aDesignPattern, "designPattern"),
				aSourceClass,
				anEvent,
				anAuxiliaryData);
		TraceEmitterFactorySelector.getTraceEmitter().traceDesignPattern(
				aDesignPattern,
				aSourceClass,
				anEvent,
				anAuxiliaryData);
	}

	public static void tracePattern(
			LibThreadPattern aThreadPattern,
			LibDesignPattern aDesignPattern,
			Class<?> aSourceClass,
			LibTraceEvent anEvent,
			Map<String, String> anAuxiliaryData) {
		Objects.requireNonNull(aThreadPattern, "threadPattern");
		validate(
				Objects.requireNonNull(aDesignPattern, "designPattern"),
				aSourceClass,
				anEvent,
				anAuxiliaryData);
		TraceEmitterFactorySelector.getTraceEmitter().tracePattern(
				aThreadPattern,
				aDesignPattern,
				aSourceClass,
				anEvent,
				anAuxiliaryData);
	}

	private static void validate(
			Object aPattern,
			Class<?> aSourceClass,
			LibTraceEvent anEvent,
			Map<String, String> anAuxiliaryData) {
		Objects.requireNonNull(aPattern, "pattern");
		Objects.requireNonNull(aSourceClass, "sourceClass");
		Objects.requireNonNull(anEvent, "event");
		Objects.requireNonNull(anAuxiliaryData, "auxiliaryData");
		for (Map.Entry<String, String> entry : anAuxiliaryData.entrySet()) {
			Objects.requireNonNull(entry.getKey(), "auxiliaryData key");
			Objects.requireNonNull(
					entry.getValue(),
					"auxiliaryData[" + entry.getKey() + "]");
		}
	}
}
