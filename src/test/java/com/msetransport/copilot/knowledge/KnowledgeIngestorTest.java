package com.msetransport.copilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class KnowledgeIngestorTest {

    private static final String MD = """
            # Disputes

            Intro text without a section is ignored.

            ## Opening a dispute
            A merchant can open a dispute
            on any result.

            - first bullet
              continues here
            - second bullet

            ## Empty section

            ## Lifecycle
            OPEN, then RESOLVED or REJECTED.
            """;

    @Test
    void splitsOneChunkPerSectionWithStableIdsAndTitles() {
        List<Document> chunks = KnowledgeIngestor.split("disputes.md", MD);
        assertThat(chunks).hasSize(2);
        Document first = chunks.getFirst();
        assertThat(first.getMetadata()).containsEntry("source", "disputes.md").containsEntry("title", "Disputes › Opening a dispute");
        assertThat(first.getText()).startsWith("Opening a dispute\nA merchant can open a dispute on any result.");
        assertThat(first.getText()).contains("- first bullet continues here\n- second bullet");
        assertThat(KnowledgeIngestor.split("disputes.md", MD).getFirst().getId()).isEqualTo(first.getId());
        assertThat(KnowledgeIngestor.split("other.md", MD).getFirst().getId()).isNotEqualTo(first.getId());
    }

    @Test
    void loadsTheBundledDocumentation() {
        List<Document> chunks = KnowledgeIngestor.loadChunks();
        assertThat(chunks).hasSizeGreaterThan(15);
        assertThat(chunks).anySatisfy(d -> assertThat(d.getText()).contains("AMOUNT_MISMATCH:"));
    }

    @Test
    void ingestReplacesChunksById() {
        VectorStore store = mock(VectorStore.class);
        KnowledgeIngestor ingestor = new KnowledgeIngestor(store);
        ingestor.ingest();
        InOrder order = inOrder(store);
        order.verify(store).delete(anyList());
        order.verify(store).add(anyList());
        assertThat(ingestor.chunkCount()).isEqualTo(KnowledgeIngestor.loadChunks().size());
    }
}
