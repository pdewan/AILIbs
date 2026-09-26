package eduAI.trace.processors;

import java.util.Map;

public interface ProviderIndependentKeyValueComparator {
	KeyValueComparison compare(
			String aProviderIndependentStoreDump,
			Map<String, String> someExpectedProperties);
}
