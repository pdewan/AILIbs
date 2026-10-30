package eduAI.trace.processors.cli;
import java.util.Objects;
/** A deliberate exact-name or prefix requirement; there is no alternate match mode on failure. */
public record CLIModelChoiceRequirement(Match match, String name) {
    public enum Match { EXACT, PREFIX }
    public CLIModelChoiceRequirement {
        Objects.requireNonNull(match);
        if(name==null || name.isBlank()) throw new IllegalArgumentException("Required model name/prefix must not be blank");
    }
    public boolean matches(String choice) {
        return switch(match) { case EXACT -> choice.equals(name); case PREFIX -> choice.startsWith(name); };
    }
}
