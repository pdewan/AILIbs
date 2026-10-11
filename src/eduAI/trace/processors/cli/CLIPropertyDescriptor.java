package eduAI.trace.processors.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Input-language metadata, independent of student property-store implementations. */
public record CLIPropertyDescriptor(ValueType type, List<String> alternatives) {
    public enum ValueType { BOOLEAN, INTEGER, DOUBLE, STRING, STRING_LIST }

    public CLIPropertyDescriptor {
        Objects.requireNonNull(type, "type");
        alternatives = List.copyOf(alternatives);
    }

    public Object parse(String text) {
        Object value;
        try {
            value = switch (type) {
                case BOOLEAN -> {
                    if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException("expected true or false");
                    }
                    yield Boolean.valueOf(text);
                }
                case INTEGER -> Integer.valueOf(text);
                case DOUBLE -> {
                    double number = Double.parseDouble(text);
                    if (!Double.isFinite(number)) {
                        throw new IllegalArgumentException("expected a finite number");
                    }
                    yield number;
                }
                case STRING -> text;
                case STRING_LIST -> {
                    List<String> items = Arrays.stream(text.split(",", -1))
                            .map(String::trim).toList();
                    if (items.stream().anyMatch(String::isEmpty)) {
                        throw new IllegalArgumentException("list items must not be empty");
                    }
                    yield items;
                }
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected " + type.name().toLowerCase(), e);
        }
        List<String> candidates = value instanceof List<?> items
                ? items.stream().map(String::valueOf).toList()
                : List.of(String.valueOf(value));
        if (!alternatives.isEmpty() && !alternatives.containsAll(candidates)) {
            throw new IllegalArgumentException("allowed values: " + alternatives);
        }
        return value;
    }
}
