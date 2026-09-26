package eduAI.trace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceLine {
	private final String layerName;
	private final String eventName;
	private final String threadPatternName;
	private final String threadName;
	private final String designPatternName;
	private final String sourceClassName;
	private final Map<String, String> auxiliaryData;
	private final String rawLine;

	public TraceLine(
			String aLayerName,
			String anEventName,
			String aThreadPatternName,
			String aThreadName,
			String aDesignPatternName,
			String aSourceClassName,
			Map<String, String> anAuxiliaryData,
			String aRawLine) {
		layerName = aLayerName == null ? "" : aLayerName;
		eventName = anEventName == null ? "" : anEventName;
		threadPatternName =
				aThreadPatternName == null ? "" : aThreadPatternName;
		threadName = aThreadName == null ? "" : aThreadName;
		designPatternName =
				aDesignPatternName == null ? "" : aDesignPatternName;
		sourceClassName =
				aSourceClassName == null ? "" : aSourceClassName;
		auxiliaryData = anAuxiliaryData == null
				? Map.of()
				: Collections.unmodifiableMap(
						new LinkedHashMap<>(anAuxiliaryData));
		rawLine = aRawLine == null ? "" : aRawLine;
	}

	public String getLayerName() {
		return layerName;
	}

	public String getEventName() {
		return eventName;
	}

	public String getThreadPatternName() {
		return threadPatternName;
	}

	public String getThreadName() {
		return threadName;
	}

	public String getDesignPatternName() {
		return designPatternName;
	}

	public String getSourceClassName() {
		return sourceClassName;
	}

	public Map<String, String> getAuxiliaryData() {
		return auxiliaryData;
	}

	public String getRawLine() {
		return rawLine;
	}
}
