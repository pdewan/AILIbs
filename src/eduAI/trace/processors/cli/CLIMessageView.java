package eduAI.trace.processors.cli;
/** Explicit selection for independent grading of the two message views. */
public enum CLIMessageView {
    ALL, ORDINARY, SYSTEM;
    public boolean includes(CLIMixedIO.Entry entry) {
        String option=entry.input().argument().strip();
        return this==ALL || (this==ORDINARY ? option.isEmpty() : option.equals(CLICommandNames.SYSTEM_OPTION));
    }
}
