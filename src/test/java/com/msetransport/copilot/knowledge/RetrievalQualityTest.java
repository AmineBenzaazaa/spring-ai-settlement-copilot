package com.benzaazaa.copilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;

/**
 * Retrieval eval without a database: the real documentation chunks, the real embeddings, the same
 * cosine pgvector computes, then the hybrid re-rank. The expected section must come first.
 */
class RetrievalQualityTest {

    private static final HashingEmbeddingModel MODEL = new HashingEmbeddingModel(HashingEmbeddingModel.DEFAULT_DIMENSIONS);
    private static final List<Document> CHUNKS = KnowledgeIngestor.loadChunks();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "How is the balance owed computed?|Courier balance formulas",
            "Comment le solde dû est-il calculé ?|Courier balance formulas",
            "What does AMOUNT_MISMATCH mean?|How each line is classified",
            "Que signifie MISSING_FROM_STATEMENT ?|How each line is classified",
            "What is a dispute?|Dispute / litige",
            "how does dispute resolution work|Dispute lifecycle",
            "why is a statement flagged|Why a statement is flagged",
            "what happens if I upload the same file twice|Processing pipeline"})
    void theRightSectionComesFirst(String question, String expectedSection) {
        float[] q = MODEL.embedText(question);
        List<Document> candidates = CHUNKS.stream()
                .map(d -> d.mutate().score(cosine(q, MODEL.embedText(d.getText()))).build())
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(KnowledgeTools.CANDIDATES)
                .toList();
        assertThat(KnowledgeTools.rerank(question, candidates).getFirst().title()).endsWith(expectedSection);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
