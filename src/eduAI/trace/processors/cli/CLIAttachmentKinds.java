package eduAI.trace.processors.cli;
import java.util.*;
/** Trusted input policy, independent of trace claims and file availability.
 * Unknown references require configuration; extensions and trace contents never
 * silently decide which independently graded attachment check owns a file. */
public record CLIAttachmentKinds(Map<String,CLIFileExpansionContentsChecks.Kind> files) {
    public CLIAttachmentKinds { files=Map.copyOf(files); }
    public CLIFileExpansionContentsChecks.Kind kind(String file) {
        var kind=files.get(file);
        if(kind==null) throw new IllegalArgumentException("No attachment kind configured for " + file);
        return kind;
    }
}
