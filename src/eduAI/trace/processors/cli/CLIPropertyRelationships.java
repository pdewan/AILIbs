package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.trace.processors.ReflectiveValueCollector;

/** Cross-property state is deliberately separate from individual registered rules. */
final class CLIPropertyRelationships {
    private final Map<CLIPropertyDescriptor.ValueType, Set<String>> types = new HashMap<>();
    private final Map<Set<String>, CLIPropertyDescriptor.ValueType> owners = new HashMap<>();
    private final Map<String, String> propertyHandlers = new HashMap<>();
    private final Map<String, String> handlerOwners = new HashMap<>();

    List<String> check(CLIPropertyCheckerRegistry.Registration registration, String identity, String returnedType) {
        List<String> errors = new ArrayList<>();
        if (identity != null && !identity.isBlank()) {
            String first = propertyHandlers.putIfAbsent(registration.canonicalName(), identity);
            if (first != null && !first.equals(identity)) errors.add("Property handler instance changed: " + registration.canonicalName());
            String previous = handlerOwners.putIfAbsent(identity, registration.canonicalName());
            if (previous != null && !previous.equals(registration.canonicalName()))
                errors.add("Distinct properties share a handler instance: " + previous + " and " + registration.canonicalName());
        }
        Set<String> values = new TreeSet<>();
        new ReflectiveValueCollector().collect(returnedType).forEach(value -> values.add(value.value()));
        if (!values.isEmpty()) {
            var group = registration.descriptor().type();
            var previous = types.putIfAbsent(group, values);
            if (previous != null && !previous.equals(values))
                errors.add("Inconsistent type information for " + group + ": expected=" + previous + " actual=" + values);
            var other = owners.putIfAbsent(values, group);
            if (other != null && other != group) errors.add("Different property types share type information: " + other + " and " + group);
        }
        return errors;
    }
}
