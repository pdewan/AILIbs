package eduAI.trace.processors.cli;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import eduAI.trace.ImageByteSummary;
import eduAI.trace.processors.*;
import static eduAI.trace.cli.MinimalCLITrace.*;
import eduAI.trace.processors.cli.MinimalCLIContractChecks.Attachment;

/** Independent attachment-kind checks sharing the contract's name/content comparator. */
public final class CLIFileExpansionContentsChecks {
    public enum Kind { TEXT, IMAGE }
    private CLIFileExpansionContentsChecks() { }

    public static Attachment readAttachment(Path directory, String fileName) throws java.io.IOException {
        byte[] bytes=Files.readAllBytes(directory.resolve(fileName));
        boolean image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes))!=null;
        return new Attachment(fileName,image ? ImageByteSummary.from(bytes).formatted()
            : new String(bytes,StandardCharsets.UTF_8),image);
    }

    public static TraceCheckResult check(Kind kind, Collection<TraceRecord> records, CLIMixedIO io, Path directory,CLIAttachmentKinds kinds) {
        var errors=new ArrayList<String>();
        var expected=new LinkedHashMap<String,List<Attachment>>();
        try {
            for(var entry:io.entries()) for(var reference:entry.attachments()) {
                if(kinds.kind(reference.path())!=kind) continue;
                var attachment=readAttachment(directory,reference.path());
                if(attachment.image()!=(kind==Kind.IMAGE)) errors.add("Attachment kind differs from configured kind: "+reference.path());
                expected.computeIfAbsent(attachment.fileName(),key->new ArrayList<>()).add(attachment);
            }
        } catch(java.io.IOException | IllegalArgumentException e) {
            return new TraceCheckResult(List.of(),List.of(),List.of("Cannot read attachment: "+e.getMessage()));
        }
        if(expected.isEmpty()) errors.add("Insufficient evidence: no "+kind+" file attachments");
        for(var entry:expected.entrySet()) {
            var key=entry.getKey();
            var actual=records.stream().filter(r->CLITraceEvidence.is(r,Event.FILE_EXPANDED))
                .map(r->r.getTraceLine().getAuxiliaryData())
                .filter(d->key.equals(d.get(INPUT_FILE_NAME))).toList();
            if(actual.size()!=entry.getValue().size())
                errors.add("Expansion count differs for "+key);
            for(var fields:actual) if(!MinimalCLIContractChecks.attachmentMatches(entry.getValue().get(0),fields))
                errors.add("Expanded contents differ for "+key);
        }
        // The aggregate contract also rejects expansions of unrequested file names.
        // Such an event cannot be assigned a kind from trusted input evidence.
        return new TraceCheckResult(List.of(),List.of(),errors);
    }
}
