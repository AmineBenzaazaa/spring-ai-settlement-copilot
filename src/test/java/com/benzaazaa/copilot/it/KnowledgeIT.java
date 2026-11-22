package com.benzaazaa.copilot.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.benzaazaa.copilot.chat.ChatAnswer;
import com.benzaazaa.copilot.knowledge.KnowledgeIngestor;
import com.benzaazaa.copilot.knowledge.KnowledgeToolsAccess;
import com.benzaazaa.copilot.support.AbstractIntegrationTest;
import com.benzaazaa.copilot.support.TestTokens;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** RAG against a real pgvector: Flyway schema, HNSW index, cosine search, idempotent ingestion. */
class KnowledgeIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private KnowledgeIngestor ingestor;

    @Test
    void ingestionIsIdempotent() {
        int expected = ingestor.chunkCount();
        assertThat(expected).isGreaterThan(15);
        ingestor.ingest();
        assertThat(jdbc.queryForObject("select count(*) from vector_store", Integer.class)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'vector_store_embedding_hnsw'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void cosineSearchPutsTheRightSectionInTheCandidates() {
        // pure vector search: the right chunk is in the neighbourhood, the keyword re-rank then picks it
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder().query("courier balance formulas")
                .topK(KnowledgeToolsAccess.CANDIDATES).build());
        assertThat(docs).hasSize(KnowledgeToolsAccess.CANDIDATES);
        assertThat(docs).anySatisfy(d -> assertThat(d.getMetadata().get("title").toString()).contains("Courier balance formulas"));
        assertThat(docs.getFirst().getScore()).isBetween(0.0, 1.0);
    }

    @Test
    void docsQuestionsAreAnsweredFromTheKnowledgeBase() {
        ChatAnswer en = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("What does AMOUNT_MISMATCH mean?")),
                ChatAnswer.class);
        assertThat(en.answer()).startsWith("**AMOUNT_MISMATCH**").contains("collected differs from expected");
        assertThat(en.sources()).first().satisfies(s -> assertThat(s.kind()).isEqualTo("doc"));

        ChatAnswer fr = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("Comment le solde dû est-il calculé ?")),
                ChatAnswer.class);
        assertThat(fr.language()).isEqualTo("fr");
        assertThat(fr.answer()).contains("balanceOwed = expectedCollections - reportedCollections");
    }
}
