package eduAI.trace.processors.cli;

import java.util.List;

/** An independently registered rule for one command's returned data. */
@FunctionalInterface
public interface CLICommandResultChecker {
    List<String> check(CLICommandResultContext context);
    /** Additional whole-session exercise requirements; ordinary rules use per-return evidence. */
    default List<String> checkSession(CLIMixedIO session) { return List.of(); }
}
