package eduAI.trace.processors.cli;

import java.util.*;

/** Registration controls dispatch; adding a command or rule requires no switch statement. */
public final class CLICommandResultCheckerRegistry {
    private final Map<String, List<CLICommandResultChecker>> registrations = new LinkedHashMap<>();
    public void register(String command, CLICommandResultChecker... checkers) {
        if (command == null || command.isBlank() || checkers.length == 0)
            throw new IllegalArgumentException("Command name and at least one result checker are required");
        registrations.put(command, List.of(checkers));
    }
    public void addChecker(String command, CLICommandResultChecker checker) {
        var rules = new ArrayList<>(registrations.getOrDefault(command, List.of()));
        rules.add(Objects.requireNonNull(checker));
        registrations.put(command, List.copyOf(rules));
    }
    public boolean contains(String command) { return registrations.containsKey(command); }
    public List<String> checkSession(String command,CLIMixedIO session) {
        var rules=registrations.get(command);
        if(rules==null) throw new IllegalArgumentException("No command-result checker registered for command="+command);
        return rules.stream().flatMap(rule->rule.checkSession(session).stream()).toList();
    }
    public List<String> check(String command, CLICommandResultContext context) {
        var rules = registrations.get(command);
        if (rules == null) return List.of("No command-result checker registered for command=" + command);
        return rules.stream().flatMap(rule -> rule.check(context).stream()).toList();
    }
}
