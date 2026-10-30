package eduAI.trace.processors.cli;

import java.util.*;

/** Session-local registrations: one property contract can have multiple reusable rules. */
public final class CLIPropertyCheckerRegistry {
    public record Registration(String canonicalName, CLIPropertyDescriptor descriptor, List<CLIPropertyChecker> checkers) {
        public Registration {
            Objects.requireNonNull(canonicalName);
            Objects.requireNonNull(descriptor);
            checkers = List.copyOf(checkers);
            if (checkers.isEmpty()) throw new IllegalArgumentException("At least one property checker is required");
        }
    }
    private final Map<String, Registration> registrations = new LinkedHashMap<>();

    public static CLIPropertyCheckerRegistry fromDescriptors(Map<String, CLIPropertyDescriptor> descriptors) {
        var registry = new CLIPropertyCheckerRegistry();
        var shared = new FlattenedCLIPropertyChecker();
        descriptors.forEach((name, descriptor) -> registry.register(name, descriptor, shared));
        return registry;
    }
    public void register(String name, CLIPropertyDescriptor descriptor, CLIPropertyChecker... checkers) {
        registrations.put(Objects.requireNonNull(name), new Registration(name, descriptor, List.of(checkers)));
    }
    public void registerAlias(String alias, String property) {
        registrations.put(Objects.requireNonNull(alias), require(property));
    }
    public void addChecker(String property, CLIPropertyChecker checker) {
        Registration old = require(property);
        var rules = new ArrayList<>(old.checkers());
        rules.add(Objects.requireNonNull(checker));
        Registration updated = new Registration(old.canonicalName(), old.descriptor(), rules);
        registrations.replaceAll((name, registration) -> registration == old ? updated : registration);
    }
    public void setDescriptor(String property, CLIPropertyDescriptor descriptor) {
        Registration old = require(property);
        Registration updated = new Registration(old.canonicalName(), descriptor, old.checkers());
        registrations.replaceAll((name, registration) -> registration == old ? updated : registration);
    }
    public Registration get(String property) { return registrations.get(property); }
    private Registration require(String property) {
        Registration registration = get(property);
        if (registration == null) throw new IllegalArgumentException("No checker registered for property=" + property);
        return registration;
    }
    public List<String> checkMetadata(String property, String type, String alternatives) {
        Registration registration = get(property);
        if (registration == null) return List.of("No checker registered for property=" + property);
        return registration.checkers().stream().flatMap(checker -> checker.checkMetadata(
                registration.descriptor(), type, alternatives).stream()).toList();
    }
    public List<String> checkValue(String property, Object value, String returned) {
        Registration registration = get(property);
        if (registration == null) return List.of("No checker registered for property=" + property);
        return registration.checkers().stream().flatMap(checker -> checker.checkValue(
                registration.descriptor(), value, returned).stream()).toList();
    }
}
