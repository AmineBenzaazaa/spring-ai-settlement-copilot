package com.msetransport.copilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel(384);

    @Test
    void isDeterministicAndUnitLength() {
        float[] a = model.embedText("Which courier owes the most?");
        float[] b = model.embedText("Which courier owes the most?");
        assertThat(a).containsExactly(b);
        assertThat(a).hasSize(384);
        assertThat(norm(a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(model.dimensions()).isEqualTo(384);
    }

    @Test
    void relatedTextIsCloserThanUnrelatedText() {
        float[] query = model.embedText("what is an amount mismatch");
        float[] related = model.embedText("AMOUNT_MISMATCH: collected amount differs from the expected amount");
        float[] unrelated = model.embedText("CSV export escapes formula injection");
        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
    }

    @Test
    void frenchQuestionsMatchEnglishDocs() {
        float[] fr = model.embedText("litige ouvert sur un écart");
        float[] en = model.embedText("open dispute on a discrepancy");
        float[] other = model.embedText("upload the remittance file");
        assertThat(cosine(fr, en)).isGreaterThan(cosine(fr, other));
    }

    @Test
    void emptyTextStillGivesAValidVector() {
        float[] v = model.embedText("the of and");
        assertThat(norm(v)).isEqualTo(1.0);
    }

    @Test
    void embedsBatchesAndDocuments() {
        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("a courier", "a dispute"), null));
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
        assertThat(model.embed(new Document("a courier"))).containsExactly(model.embedText("a courier"));
    }

    @Test
    void rejectsTinyDimensions() {
        assertThatThrownBy(() -> new HashingEmbeddingModel(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashIsStable() {
        assertThat(HashingEmbeddingModel.fnv1a("courier")).isEqualTo(HashingEmbeddingModel.fnv1a("courier"));
        assertThat(HashingEmbeddingModel.fnv1a("courier")).isNotEqualTo(HashingEmbeddingModel.fnv1a("couriers"));
    }

    static double norm(float[] v) {
        double s = 0;
        for (float x : v) {
            s += x * x;
        }
        return Math.sqrt(s);
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot / (norm(a) * norm(b));
    }
}
