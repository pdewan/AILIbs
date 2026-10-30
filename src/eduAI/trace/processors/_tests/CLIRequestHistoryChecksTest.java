package eduAI.trace.processors._tests;

import java.nio.file.*;
import java.util.*;
import eduAI.trace.*;
import eduAI.trace.lib.StreamingMode;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
import eduAI.trace.processors.GenericTracesFileProcessor.TraceMessage;
import eduAI.trace.processors.GenericTracesFileProcessor.TracePart;

/** Input-derived values and attachment retention across provider and streaming changes. */
public final class CLIRequestHistoryChecksTest {
    private static final String ATTACHMENT = "An ambiguity needs a clarifying question.\nKeep this exact text.";
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("cli-request-history-");
        try {
            Files.writeString(directory.resolve("Commentary.txt"), ATTACHMENT);
            var descriptors = Map.of(
                    "current_models", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.STRING_LIST, List.of()),
                    "system_prompt", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.STRING, List.of()),
                    "timeout", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.INTEGER, List.of()),
                    "temperature", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.DOUBLE, List.of()),
                    "stream", new CLIPropertyDescriptor(CLIPropertyDescriptor.ValueType.BOOLEAN, List.of()));
            var parsed = CLIProcessors.input(descriptors, Set.of("messages"), Set.of()).processLines(List.of(
                    "/prop current_models = local-model", "/prop system_prompt = Be concise.",
                    "/prop timeout = 71", "/prop temperature = 0.3", "/prop stream = false", "Initial question",
                    "/prop current_models = remote-model", "/prop timeout = 127", "/prop temperature = 0.6",
                    "/prop stream = true", "Read \\@\"Commentary.txt\" please",
                    "/prop current_models = local-model", "Explain that commentary", "/prop stream = false", "Summarize"));
            if (!parsed.succeeded()) throw new AssertionError(parsed.diagnostics());
            var io = new CLIMixedIO(parsed.interaction().orElseThrow().commands().stream().map(c ->
                    new CLIMixedIO.Entry(c.inputOrdinal(), c, CLIAttachment.extract(c.argument()), Optional.empty(), List.of())).toList());
            var expected = CLITraceExpectations.from(io, directory);
            for (var check : CLITraceExpectations.Check.values())
                require(expected.check(records(0), null, check).passed(), "Correct requests failed " + check);
            for (int mutation = 1; mutation <= 8; mutation++) {
                var check = mutation <= 2 ? CLITraceExpectations.Check.SETTINGS : CLITraceExpectations.Check.PROMPTS;
                var result = expected.check(records(mutation), null, check);
                require(!result.passed(), "Surviving mutation " + mutation);
                String diagnostic = mutation <= 2 ? "CLI property timeout" : mutation <= 4 ? "CLI prompts" : "CLI earlier prompt/text attachment history";
                require(result.level3ErrorMessages().stream().anyMatch(e -> e.contains(diagnostic)), "Wrong rejecting rule " + result);
            }
            // History comparison requires two completed exchanges. Include the
            // initial prompt as well as the attachment turn; one exchange alone
            // cannot be the passing control for these content mutations.
            var initial = io.entries().stream().filter(e -> e.input().kind()==CLIInteraction.Kind.PROMPT).findFirst().orElseThrow();
            var attached = io.entries().stream().filter(e -> !e.attachments().isEmpty()).findFirst().orElseThrow();
            var view = new CLIInteraction.Command(100, 100, "/messages", CLIInteraction.Kind.COMMAND,
                    "messages", "", null, Map.of());
            var current = new CLIMixedIO.Entry(100, view, List.of(), Optional.empty(), List.of());
            var messagesChecker = new CLIMessagesCommandResultChecker(io, directory,
                new CLIAttachmentKinds(Map.of("Commentary.txt",CLIFileExpansionContentsChecks.Kind.TEXT)));
            for (int mutation = 0; mutation <= 4; mutation++) {
                String shown = mutation == 0 ? ATTACHMENT : "incorrect attachment";
                if (mutation == 2) shown = ATTACHMENT.substring(0, ATTACHMENT.length() - 1);
                if (mutation == 3) shown = "";
                if (mutation == 4) shown = "\\@\"Commentary.txt\"";
                String text = "[" + initial.number() + ".user] prompt = Initial question, parameters = timeout=71 temperature=0.3 stream=false\n["
                        + initial.number() + ".assistant] response = Initial answer\n[" + attached.number() + ".user] prompt = Read " + shown + " please, parameters = timeout=127 temperature=0.6 stream=true\n["
                        + attached.number() + ".assistant] response = Recorded answer";
                var context = new CLICommandResultContext(current, List.of(initial,attached),
                        new CLIResultEvidence(TraceObjectPrinter.formatFullEvidence("result", text)), List.of(),
                        Map.of(initial.input().inputOrdinal(),List.of("Initial answer"),attached.input().inputOrdinal(), List.of("Recorded answer")), Map.of(), List.of());
                var errors = messagesChecker.check(context);
                // A raw-reference history needs no attachment bytes. Expanded
                // text does; image-only history text never consumes image bytes.
                var absent=directory.resolve("absent-attachments");
                var textOnly=new CLIMessagesCommandResultChecker(io,absent,
                    new CLIAttachmentKinds(Map.of("Commentary.txt",CLIFileExpansionContentsChecks.Kind.TEXT)));
                if(mutation==4) require(textOnly.check(context).isEmpty(),"Raw reference unnecessarily reads missing text");
                if(mutation==0) require(!textOnly.check(context).isEmpty(),"Missing required expanded text passed");
                if(mutation==3) {
                    var imageOnly=new CLIMessagesCommandResultChecker(io,absent,
                        new CLIAttachmentKinds(Map.of("Commentary.txt",CLIFileExpansionContentsChecks.Kind.IMAGE)));
                    require(imageOnly.check(context).isEmpty(),"Image bytes unnecessarily gate text history");
                }
                require(mutation == 0 || mutation == 4 ? errors.isEmpty() : errors.stream().anyMatch(e -> e.contains("Messages lack earlier prompt")),
                        "Expanded message history mutation=" + mutation + " errors=" + errors);
            }
            System.out.println("CLIRequestHistoryChecksTest: correct requests and original/expanded message history passed; 11 targeted timeout/text mutations rejected");
        } finally {
            Files.deleteIfExists(directory.resolve("Commentary.txt"));
            Files.delete(directory);
        }
    }
    private static List<TraceRecord> records(int mutation) {
        List<TraceRecord> records = new ArrayList<>();
        List<List<String>> texts = List.of(List.of("Initial question"), List.of("Read ", ATTACHMENT, " please"),
                List.of("Explain that commentary"), List.of("Summarize"));
        for (int i = 0; i < texts.size(); i++) {
            var mode = i == 1 || i == 2 ? StreamingMode.STREAMING : StreamingMode.NON_STREAMING;
            var record = new TraceRecord(new TraceRecordId(i == 1 ? "gemini" : "ollama", mode, i),
                    new TraceLine("cli", "request_sent", "none", "test", "request", "StudentSender", Map.of(), ""), "CLITrace.txt", i + 1);
            for (boolean nativeRequest : List.of(false, true)) {
                List<TraceMessage> context = new ArrayList<>();
                context.add(new TraceMessage("SYSTEM", List.of(new TracePart("text", "Be concise."))));
                for (int j = 0; j <= i; j++) {
                    List<String> parts = texts.get(j);
                    boolean wrongCurrent = i == 1 && j == 1 && mutation == (nativeRequest ? 4 : 3);
                    boolean wrongHistory = i == 2 && j == 1 && mutation == (nativeRequest ? 6 : 5);
                    boolean missingHistory = i == 3 && j == 1 && mutation == (nativeRequest ? 8 : 7);
                    if (missingHistory) continue;
                    if (wrongCurrent || wrongHistory) parts = List.of("Read ", "incorrect attachment", " please");
                    // Both separate generic parts and a provider's joined text are accepted.
                    if (nativeRequest) parts = List.of(String.join("", parts));
                    context.add(new TraceMessage("USER", parts.stream().map(p -> new TracePart("text", p)).toList()));
                }
                int timeout = i == 0 ? 71 : 127;
                if (mutation == 1 && i == 1) timeout = 71;
                if (mutation == 2 && i == 0) timeout = 127;
                var request = new CanonicalTraceRequest(i == 1 ? "remote-model" : "local-model",
                        Map.of("timeout_milliseconds", String.valueOf(timeout * 1000), "temperature", i == 0 ? "0.3" : "0.6"), context);
                record.putExtractedData(nativeRequest ? "providerDependentCanonicalRequest" : "providerIndependentCanonicalRequest", request);
            }
            records.add(record);
        }
        return records;
    }
    private static void require(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
}
