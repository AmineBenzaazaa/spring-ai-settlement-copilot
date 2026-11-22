package com.msetransport.copilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.msetransport.copilot.security.Caller;
import com.msetransport.copilot.tools.ToolOutputs.DocSnippet;
import com.msetransport.copilot.tools.ToolOutputs.DocSnippets;
import com.msetransport.copilot.tools.TurnContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class KnowledgeToolsTest {

    private static Document doc(String title, String text, double score) {
        return Document.builder().text(text).metadata(Map.of("source", "x.md", "title", title)).score(score).build();
    }

    @Test
    void rerankPrefersTheSectionThatDefinesTheCode() {
        List<DocSnippet> ranked = KnowledgeTools.rerank("What does AMOUNT_MISMATCH mean?", List.of(
                doc("Glossary", "Discrepancy\nAny non-matched line: amount mismatch, unknown tracking", 0.40),
                doc("Classification", "How each line is classified\n- AMOUNT_MISMATCH: collected differs from expected", 0.30)));
        assertThat(ranked.getFirst().title()).isEqualTo("Classification");
    }

    @Test
    void searchReturnsTopSnippetsAndRecordsDocSources() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                doc("A", "Fees\nwhat the courier keeps", 0.5), doc("B", "Balance\nexpected minus reported", 0.6),
                doc("C", "C\nc", 0.1), doc("D", "D\nd", 0.1)));
        TurnContext turn = new TurnContext(new Caller("atlas", "MERCHANT", 1L, "t"), t -> { });
        DocSnippets out = new KnowledgeTools(store).searchSettlementDocs("balance", new ToolContext(Map.of(TurnContext.KEY, turn)));
        assertThat(out.snippets()).hasSize(3);
        assertThat(out.snippets().getFirst().title()).isEqualTo("B");
        assertThat(turn.sources()).hasSize(3).allSatisfy(s -> assertThat(s.kind()).isEqualTo("doc"));
    }

    @Test
    void blankQueryDoesNotHitTheStore() {
        VectorStore store = mock(VectorStore.class);
        TurnContext turn = new TurnContext(new Caller("atlas", "MERCHANT", 1L, "t"), t -> { });
        DocSnippets out = new KnowledgeTools(store).searchSettlementDocs(" ", new ToolContext(Map.of(TurnContext.KEY, turn)));
        assertThat(out.snippets()).isEmpty();
        verifyNoInteractions(store);
    }
}
