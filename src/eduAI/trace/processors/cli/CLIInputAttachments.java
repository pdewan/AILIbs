package eduAI.trace.processors.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import eduAI.trace.ImageByteSummary;

/** Shared input-side attachment expansion for request and command-result expectations. */
public final class CLIInputAttachments {
    public record Expanded(List<String> texts, List<String> images) {
        public Expanded { texts = List.copyOf(texts); images = List.copyOf(images); }
    }
    private CLIInputAttachments() { }
    public static Expanded expand(CLIMixedIO.Entry entry, Path directory) throws IOException {
        List<String> texts = new ArrayList<>(), images = new ArrayList<>();
        String prompt = entry.input().argument();
        int from = 0;
        for (var attachment : entry.attachments()) {
            addText(texts, prompt.substring(from, attachment.start()));
            byte[] bytes = Files.readAllBytes(directory.resolve(attachment.path()).normalize());
            // The agreed Image.png can contain JPEG bytes; inspect content rather than extension.
            if (javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)) != null) {
                String summary = ImageByteSummary.from(bytes).formatted();
                images.add(summary.substring("imageBytes(".length(), summary.length() - 1));
            } else addText(texts, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            from = attachment.end();
        }
        addText(texts, prompt.substring(from));
        return new Expanded(texts, images);
    }
    /** Reads only the selected content kind; verifies the bytes agree with policy. */
    public static Expanded expand(CLIMixedIO.Entry entry,Path directory,CLIAttachmentKinds kinds,
            CLIFileExpansionContentsChecks.Kind selected) throws IOException {
        var texts=new ArrayList<String>();var images=new ArrayList<String>();
        String prompt=entry.input().argument();int from=0;
        for(var attachment:entry.attachments()) {
            if(selected==CLIFileExpansionContentsChecks.Kind.TEXT) addText(texts,prompt.substring(from,attachment.start()));
            if(kinds.kind(attachment.path())==selected) {
                var loaded=CLIFileExpansionContentsChecks.readAttachment(directory,attachment.path());
                if(loaded.image()!=(selected==CLIFileExpansionContentsChecks.Kind.IMAGE))
                    throw new IllegalArgumentException("Attachment kind differs from configured kind: "+attachment.path());
                if(loaded.image()) {
                    String summary=loaded.content();
                    images.add(summary.substring("imageBytes(".length(),summary.length()-1));
                } else addText(texts,loaded.content());
            }
            from=attachment.end();
        }
        if(selected==CLIFileExpansionContentsChecks.Kind.TEXT) addText(texts,prompt.substring(from));
        return new Expanded(texts,images);
    }
    private static void addText(List<String> texts, String text) { if (!text.isBlank()) texts.add(text); }
}
