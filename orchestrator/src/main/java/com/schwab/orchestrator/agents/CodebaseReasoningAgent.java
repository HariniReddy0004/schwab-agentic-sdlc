package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Genuine (not LLM-simulated) codebase reasoning for brownfield runs: extracts keywords from the
 * requirement text, walks the target repository's Java sources, and reports which
 * files/classes/packages textually relate to those keywords, ranked by how many keywords they
 * match. This is intentionally a real static scan rather than an LLM guess, because "identify
 * impacted modules/services/APIs/data flows" should be checkable against the actual repo, not
 * hallucinated.
 */
public final class CodebaseReasoningAgent implements Agent {
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "should", "must", "will", "into", "from",
            "when", "where", "which", "have", "has", "add", "make", "allow", "users", "need", "needs");

    @Override
    public StageAgentResult execute(StageId stage, ExecutionContext context, RunRequest request) {
        String repoPath = request.repoContextPath();
        if (repoPath == null || repoPath.isBlank()) {
            return new StageAgentResult(
                    "No repoContextPath supplied for this brownfield run; skipped static impact analysis.",
                    Map.of(), List.of(),
                    List.of("Codebase reasoning ran without repository access; impacted-module list is incomplete"),
                    true, false, List.of(), 0.2);
        }

        Path root = Path.of(repoPath);
        if (!Files.isDirectory(root)) {
            return new StageAgentResult(
                    "Configured repoContextPath '" + repoPath + "' does not exist or is not a directory.",
                    Map.of(), List.of(),
                    List.of("repoContextPath is invalid; impacted-module list is incomplete"),
                    true, false, List.of(), 0.2);
        }

        Set<String> keywords = extractKeywords(request.requirementText());
        List<Match> matches = scan(root, keywords);
        matches.sort((a, b) -> Integer.compare(b.matchedKeywords.size(), a.matchedKeywords.size()));
        List<Match> top = matches.stream().limit(12).toList();

        StringBuilder impacted = new StringBuilder();
        for (Match m : top) {
            impacted.append("- ").append(m.relativePath).append("  (matched: ").append(m.matchedKeywords).append(")\n");
        }
        if (top.isEmpty()) {
            impacted.append("(no files matched the extracted keywords: ").append(keywords).append(")\n");
        }

        Map<String, String> artifacts = new LinkedHashMap<>();
        artifacts.put("impactedModules", impacted.toString());
        artifacts.put("extractedKeywords", String.join(", ", keywords));

        List<StageAgentResult.Decision> decisions = List.of(new StageAgentResult.Decision(
                "Rank impacted files by keyword-match count rather than a single file guess",
                "A brownfield change usually touches multiple layers (controller/service/repository); ranking surfaces all of them for the design stage to consider"));

        boolean lowConfidence = top.isEmpty();
        return new StageAgentResult(
                "Scanned " + repoPath + " and identified " + top.size() + " candidate impacted file(s) based on requirement keywords.",
                artifacts, decisions,
                lowConfidence ? List.of("No files matched requirement keywords; architecture stage should reassess scope") : List.of(),
                lowConfidence, false, List.of(), lowConfidence ? 0.3 : 0.75);
    }

    private Set<String> extractKeywords(String requirementText) {
        if (requirementText == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String token : requirementText.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 3 && !STOPWORDS.contains(token)) {
                out.add(token);
            }
        }
        return out;
    }

    private record Match(String relativePath, List<String> matchedKeywords) {
    }

    private List<Match> scan(Path root, Set<String> keywords) {
        List<Match> results = new ArrayList<>();
        if (keywords.isEmpty()) return results;
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                try {
                    String content = Files.readString(file).toLowerCase(Locale.ROOT);
                    String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    List<String> matched = new ArrayList<>();
                    for (String kw : keywords) {
                        if (content.contains(kw) || fileName.contains(kw)) {
                            matched.add(kw);
                        }
                    }
                    if (!matched.isEmpty()) {
                        results.add(new Match(root.relativize(file).toString(), matched));
                    }
                } catch (IOException ignoredPerFile) {
                    // unreadable file (binary, permissions, etc.) - skip, don't fail the whole scan
                }
            }
        } catch (IOException e) {
            // repo walk itself failed; return whatever we found before the failure (possibly empty)
        }
        return results;
    }
}
