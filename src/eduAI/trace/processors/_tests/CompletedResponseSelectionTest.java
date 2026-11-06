package eduAI.trace.processors._tests;

import eduAI.trace.TraceTextSummary;
import eduAI.trace.processors.*;
import eduAI.trace.processors.cli.*;
import java.nio.file.*;
import java.util.*;

/** Actual-file regressions, not injected checker outcomes. A correct session with
 * per-chunk translations must pass. For each provider, a wrong merged answer,
 * missing completion trace, or corrupted answer in the next request must fail
 * its consumer. Corrupting retention must not fail prompt-result comparison or
 * the other provider's retention. Writes reviewable mutated trace files. */
public final class CompletedResponseSelectionTest {
    private static final String WRONG_ANSWER = "A different completed response was mistakenly stored.";
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("trace file and mutation output directory required");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        var lines = Files.readAllLines(input);
        var baseline = read(input);
        require(prompt(baseline).passed(), "correct prompt results: " + prompt(baseline));
        for (String provider : List.of(TraceProviderNames.GEMINI, TraceProviderNames.OLLAMA)) {
            require(baseline.checkPreviousCompletedResponsesRetained(provider).passed(), "correct retention " + provider);
            var records = new ArrayList<>(baseline.getTraceRecords().values());
            var merge = records.stream().filter(r -> provider.equals(r.getId().provider())
                && r.getTraceLine().getEventName().equals("streaming_chunks_merged")).findFirst().orElseThrow();
            var next = records.stream().filter(r -> r.getId().sequenceNumber() > merge.getId().sequenceNumber()
                && r.getTraceLine().getEventName().equals("request_sent")).findFirst().orElseThrow();
            var summary = TraceTextSummary.parse(merge.getTraceLine().getAuxiliaryData().get("mergedTextSummary"));
            Objects.requireNonNull(summary, "fixture must contain compact merged text evidence");
            var wrong = TraceTextSummary.from(WRONG_ANSWER);
            var changed = new ArrayList<>(lines);
            int mergeIndex = merge.getTraceFileLineNumber() - 1;
            changed.set(mergeIndex, wrongMergedAnswer(changed.get(mergeIndex), summary, wrong));
            var badMerge = mutation(output, provider + "-wrong-merged-answer", changed);
            require(!prompt(badMerge).passed(), "wrong merge must fail prompt " + provider);
            require(!badMerge.checkPreviousCompletedResponsesRetained(next.getId().provider()).passed(), "wrong merge must fail retention");

            changed = new ArrayList<>(lines); changed.remove(mergeIndex);
            var missing = mutation(output, provider + "-missing-completion", changed);
            require(!prompt(missing).passed(), "missing completion must fail prompt " + provider);
            require(!missing.checkPreviousCompletedResponsesRetained(next.getId().provider()).passed(), "missing completion must fail retention");

            changed = new ArrayList<>(lines);
            int requestIndex = next.getTraceFileLineNumber() - 1;
            changed.set(requestIndex, replaceSummary(changed.get(requestIndex), summary, wrong));
            var badHistory = mutation(output, provider + "-wrong-retained-answer", changed);
            require(!badHistory.checkPreviousCompletedResponsesRetained(next.getId().provider()).passed(), "wrong retained answer must fail");
            require(prompt(badHistory).passed(), "retention failure must not block prompt credit");
            String other = next.getId().provider().equals(TraceProviderNames.GEMINI) ? TraceProviderNames.OLLAMA : TraceProviderNames.GEMINI;
            require(badHistory.checkPreviousCompletedResponsesRetained(other).passed(), "other provider remains independent");
            changed = new ArrayList<>(lines);
            changed.set(requestIndex, replaceSummary(changed.get(requestIndex), summary, TraceTextSummary.from("")));
            var lostHistory = mutation(output, provider + "-empty-retained-answer", changed);
            require(!lostHistory.checkPreviousCompletedResponsesRetained(next.getId().provider()).passed(), "empty retained answer must fail");
            require(prompt(lostHistory).passed(), "lost history must not block prompt credit");
            require(lostHistory.checkPreviousCompletedResponsesRetained(other).passed(), "lost history remains provider scoped");
            System.out.println(provider + ": four realistic mutations rejected; unrelated checks pass");
        }
        System.out.println("Completed response selection regression passed");
    }
    private static String wrongMergedAnswer(String line, TraceTextSummary from, TraceTextSummary to) {
        var fields = new LinkedHashMap<>(eduAI.trace.TraceLineParser.parse(line).getAuxiliaryData());
        fields.put("mergedMessage", replaceSummary(fields.get("mergedMessage"), from, to));
        fields.put("mergedTextSummary", to.formatted());
        // The independently accumulated expected summary must remain unchanged.
        int start = line.indexOf(" <"), end = line.lastIndexOf("> ##");
        require(start >= 0 && end > start, "trace envelope required");
        return line.substring(0, start + 2) + eduAI.trace.GenericTrace.formatAuxiliaryData(fields) + line.substring(end);
    }
    private static String replaceSummary(String line, TraceTextSummary from, TraceTextSummary to) {
        String changed = line.replace(from.formatted(), to.formatted()).replace(from.token(), to.token());
        require(!changed.equals(line), "mutation must change actual trace text");
        return changed;
    }
    private static NativeTraceFilesProcessor mutation(Path directory, String name, List<String> lines) throws Exception {
        Path file = directory.resolve(name + ".txt"); Files.write(file, lines); return read(file);
    }
    private static NativeTraceFilesProcessor read(Path file) {
        var processor = new NativeTraceFilesProcessor(file); processor.processTraceFiles(); return processor;
    }
    private static TraceCheckResult prompt(NativeTraceFilesProcessor processor) {
        var registry = new CLICommandResultCheckerRegistry();
        registry.register(CLICommandNames.PROMPT, new CLIPromptCommandResultChecker());
        return CLICommandResultChecks.checkTraces(processor.getTraceRecords().values(), registry, CLICommandNames.PROMPT);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
