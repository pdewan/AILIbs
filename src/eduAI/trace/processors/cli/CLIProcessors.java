package eduAI.trace.processors.cli;

import java.util.Map;
import java.util.Set;

/** Construction boundary for independent grading processors. */
public final class CLIProcessors {
    private CLIProcessors() { }
    public static CLIInputProcessor input(Map<String, CLIPropertyDescriptor> properties,
            Set<String> commands, Set<String> requiredPromptProperties) {
        return new BasicCLIInputProcessor(properties, commands, requiredPromptProperties);
    }
    public static CLIMixedIOProcessor mixedIO(CLIInputProcessor inputProcessor) {
        return new BasicCLIMixedIOProcessor(inputProcessor);
    }
}
