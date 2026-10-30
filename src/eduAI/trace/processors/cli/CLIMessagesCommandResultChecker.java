package eduAI.trace.processors.cli;

import java.util.*;
import eduAI.properties.AIPropertyNames;

/** Prompt/response history with optional system instructions; no setter/getter outputs expected. */
public final class CLIMessagesCommandResultChecker implements CLICommandResultChecker {
    private final CLIMessageView view;
    @Override public List<String> checkSession(CLIMixedIO session) { return CLISemanticEvidence.messages(session,view); }
    private final Map<Integer, List<String>> expandedPrompts=new HashMap<>();
    private final Optional<java.nio.file.Path> directory;
    private final CLIAttachmentKinds attachmentKinds;
    public CLIMessagesCommandResultChecker() { directory=Optional.empty(); view=CLIMessageView.ALL; attachmentKinds=new CLIAttachmentKinds(Map.of()); }
    public CLIMessagesCommandResultChecker(CLIMixedIO io, java.nio.file.Path directory,CLIAttachmentKinds kinds) throws java.io.IOException {
        this(io,directory,CLIMessageView.ALL,kinds);
    }
    public CLIMessagesCommandResultChecker(CLIMixedIO io, java.nio.file.Path directory,CLIMessageView view,CLIAttachmentKinds kinds) throws java.io.IOException {
        this.view=Objects.requireNonNull(view);
        this.directory=Optional.of(Objects.requireNonNull(directory));
        this.attachmentKinds=Objects.requireNonNull(kinds);
    }
    @Override public List<String> check(CLICommandResultContext context) {
        String options = context.current().input().argument().strip();
        if (!options.isEmpty() && !options.equals(CLICommandNames.SYSTEM_OPTION))
            return List.of("Messages result expectations unavailable for these options");
        List<String> errors = new ArrayList<>();
        Set<String> systems = new LinkedHashSet<>();
        errors.addAll(CLISemanticEvidence.historyExchanges(context,true));
        for (var entry : context.history()) {
            if (entry.input().kind() != CLIInteraction.Kind.PROMPT) continue;
            var user = context.result().turn(entry.number(), "user");
            // Raw references and expanded text are accepted representations. A raw
            // match needs no file contents. Expansion reads configured TEXT files
            // only: image bytes belong to independent image-content checks.
            boolean promptMatches=user.containsHistoryText(entry.input().argument().strip());
            if(!promptMatches && !entry.attachments().isEmpty()) {
                try {
                    if(!expandedPrompts.containsKey(entry.input().inputOrdinal()))
                        expandedPrompts.put(entry.input().inputOrdinal(),CLIInputAttachments.expand(entry,
                            directory.orElseThrow(()->new IllegalArgumentException("History text expansion requires a directory")),
                            attachmentKinds,CLIFileExpansionContentsChecks.Kind.TEXT).texts());
                    var expanded=expandedPrompts.get(entry.input().inputOrdinal());
                    promptMatches=user.containsHistoryText(String.join("",expanded).strip())
                        || user.containsHistoryText(String.join(" ",expanded).strip());
                } catch(java.io.IOException | IllegalArgumentException error) {
                    errors.add("Cannot read history text attachment at input ["+entry.number()+"]: "+error.getMessage());
                }
            }
            if (!promptMatches)
                errors.add("Messages lack earlier prompt [" + entry.number() + "]");
            for (String name : List.of(AIPropertyNames.STREAM, AIPropertyNames.STREAMING_ALIAS,
                    AIPropertyNames.TIMEOUT, AIPropertyNames.TEMPERATURE, AIPropertyNames.NUM_CTX, AIPropertyNames.TOP_K)) {
                Object value = entry.input().properties().get(name);
                if (value == null) continue;
                String displayed = name.equals(AIPropertyNames.STREAMING_ALIAS) ? AIPropertyNames.STREAM : name;
                if (!user.contains(name) && !user.contains(displayed)) errors.add("Messages lack parameter " + name + " for input [" + entry.number() + "]");
                errors.addAll(user.require("parameter " + name + " at input [" + entry.number() + "]", value));
            }
            var responses = context.priorResponses().get(entry.input().inputOrdinal());
            if (responses == null || responses.isEmpty()) errors.add("Response history unavailable for input [" + entry.number() + "]");
            else for (String response : responses) if (!context.result().turn(entry.number(), "assistant").containsHistoryText(response))
                errors.add("Messages lack earlier response [" + entry.number() + "]");
            Object system = entry.input().properties().get(AIPropertyNames.SYSTEM_PROMPT);
            if (system != null) systems.add(String.valueOf(system));
        }
        if (options.equals(CLICommandNames.SYSTEM_OPTION)) {
            for (String system : systems) if (!context.result().containsHistoryText(system))
                errors.add("Messages -system lacks a previously used system instruction");
        } else if (java.util.regex.Pattern.compile("\\[\\d+\\.system\\]").matcher(context.result().text()).find())
            errors.add("Ordinary messages result includes system-message entries");
        return errors;
    }
}
