package eduAI.trace.processors;

import java.util.List;
import java.util.function.Predicate;

/** Compares native canonical values with an arbitrary generic object dump. */
public interface ProviderIndependentValueComparator {
	CanonicalValueComparison compare(
			String aProviderIndependentObjectDump,
			List<CanonicalValueOccurrence> someProviderCanonicalValues,
			Predicate<CanonicalValue> anIgnorePredicate);
}
