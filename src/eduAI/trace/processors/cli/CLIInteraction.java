package eduAI.trace.processors.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Expected input-side evidence; does not contain claims derived from stdout or traces. */
public record CLIInteraction(List<Command> commands) {
    public CLIInteraction {
        commands = List.copyOf(commands);
    }

    public enum Kind { PROPERTY_SET, PROPERTY_GET, PROMPT, COMMAND }

    /**
     * inputOrdinal counts nonblank inputs, not observed CLI command numbers.
     * properties contains the expected settings after this input. Prompt argument
     * and rawInput retain attachment syntax; file resolution is a later stage.
     */
    public record Command(int inputOrdinal, int lineNumber, String rawInput,
            Kind kind, String commandName, String argument, String propertyName,
            Map<String, Object> properties) {
        public Command {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            properties.forEach((name, value) -> snapshot.put(name,
                    value instanceof List<?> list ? List.copyOf(list) : value));
            properties = Collections.unmodifiableMap(snapshot);
        }
    }
}
