package eduAI.trace.processors.cli;

/** Names in the assignment input and trace contract, independent of student classes. */
public interface CLICommandNames {
    /** Internal grading role, not a required student registry key or typed command. */
    static String role(String traceName) {
        return traceName != null && traceName.isBlank() ? PROMPT : traceName;
    }
    String PROMPT = "prompt";
    String PROPERTY = "prop";
    String MESSAGES = "messages";
    String TRANSCRIPT = "transcript";
    String NEW = "new";
    String EXIT = "bye";
    String SYSTEM_OPTION = "-system";
}
