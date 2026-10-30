package eduAI.trace.processors.cli;

import java.util.List;

/** One reusable property rule; diagnostics are contextualized by the registry. */
public interface CLIPropertyChecker {
    List<String> checkMetadata(CLIPropertyDescriptor expected, String returnedType, String alternatives);
    List<String> checkValue(CLIPropertyDescriptor expected, Object value, String returnedValue);
}
