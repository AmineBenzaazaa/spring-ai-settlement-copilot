package com.benzaazaa.copilot.knowledge;

import com.benzaazaa.copilot.tools.SourceRef;
import com.benzaazaa.copilot.tools.ToolOutputs.DocSnippet;
import com.benzaazaa.copilot.tools.ToolOutputs.DocSnippets;
import com.benzaazaa.copilot.tools.TurnContext;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * RAG as a tool: the model decides when it needs the rules, and the call shows up in the trace.
 *
 * <p>Hybrid retrieval: pgvector returns the {@value #CANDIDATES} nearest chunks (cosine), then a
 * keyword re-rank boosts chunks that literally contain the query's terms, and strongly boosts exact
 * codes such as {@code AMOUNT_MISMATCH}. Vector search finds the neighbourhood, keywords pick the
 * precise section.
 */
@Component
public class KnowledgeTools {

    static final int CANDIDATES = 8;
    static final int TOP_K = 3;
    static final double MIN_SIMILARITY = 0.05;
    private static final Pattern CODE = Pattern.compile("\\b[A-Z]{3,}(?:_[A-Z]+)+\\b");

    private final VectorStore vectorStore;

    public KnowledgeTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(name = "searchSettlementDocs", description = """
            Search the settlement platform's documentation: business rules, how reconciliation classifies
            lines (MATCHED, AMOUNT_MISMATCH, UNKNOWN_TRACKING, MISSING_FROM_STATEMENT), balance formulas,
            dispute lifecycle, CSV format and glossary (English and French terms). Use for 'what does X
            mean', 'how is X computed' and 'how does X work' questions.""")
    public DocSnippets searchSettlementDocs(@ToolParam(description = "What to look up, in the user's words") String query,
                                            ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        String q = query == null ? "" : query.trim();
        List<Document> candidates = q.isEmpty() ? List.of() : vectorStore.similaritySearch(SearchRequest.builder()
                .query(q).topK(CANDIDATES).similarityThreshold(MIN_SIMILARITY).build());
        List<DocSnippet> snippets = rerank(q, candidates).stream().limit(TOP_K).toList();
        DocSnippets out = new DocSnippets(q, snippets);
        turn.recordResult(out, snippets.size(), 0, snippets.stream()
                .map(s -> new SourceRef("doc", s.source() + "#" + Integer.toHexString(s.title().hashCode()), s.title(), null))
                .toList());
        return out;
    }

    static List<DocSnippet> rerank(String query, List<Document> candidates) {
        Set<String> queryTerms = new HashSet<>(TextNormalizer.terms(query));
        Matcher codes = CODE.matcher(query.toUpperCase());
        Set<String> queryCodes = new HashSet<>();
        while (codes.find()) {
            queryCodes.add(codes.group());
        }
        return candidates.stream()
                .map(d -> {
                    double vector = d.getScore() == null ? 0 : d.getScore();
                    Set<String> docTerms = new HashSet<>(TextNormalizer.terms(d.getText()));
                    long hits = queryTerms.stream().filter(docTerms::contains).count();
                    double keyword = queryTerms.isEmpty() ? 0 : (double) hits / queryTerms.size();
                    String heading = d.getText().lines().findFirst().orElse("");
                    double codeBonus = queryCodes.stream().anyMatch(c -> d.getText().contains(c + ":")) ? 0.5
                            : queryCodes.stream().anyMatch(c -> d.getText().contains(c)) ? 0.25 : 0;
                    double headingBonus = queryTerms.stream().anyMatch(t -> TextNormalizer.terms(heading).contains(t)) ? 0.1 : 0;
                    double score = vector + 0.4 * keyword + codeBonus + headingBonus;
                    return new DocSnippet(String.valueOf(d.getMetadata().get("source")),
                            String.valueOf(d.getMetadata().get("title")), d.getText(), Math.round(score * 1000) / 1000.0);
                })
                .sorted(Comparator.comparingDouble(DocSnippet::score).reversed())
                .toList();
    }
}
