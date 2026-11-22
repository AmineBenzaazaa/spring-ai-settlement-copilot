package com.msetransport.copilot.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Offline, deterministic embeddings ("feature hashing"): each normalized term and term bigram is
 * hashed into one of {@code dimensions} buckets with a +1/-1 sign, then the vector is L2-normalized
 * so pgvector's cosine distance works. Same text, same vector, on every machine, with no model
 * download and no API key: ideal for the demo and for repeatable tests.
 *
 * <p>Trade-off: it matches words, not meaning ("owed" and "outstanding" are unrelated to it). A
 * neural embedding model is a drop-in replacement: it is just another {@link EmbeddingModel} bean.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    public static final int DEFAULT_DIMENSIONS = 384;

    private final int dimensions;

    public HashingEmbeddingModel(int dimensions) {
        if (dimensions < 16) {
            throw new IllegalArgumentException("dimensions must be >= 16");
        }
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embedText(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    public float[] embedText(String text) {
        float[] vector = new float[dimensions];
        List<String> terms = TextNormalizer.terms(text);
        for (int i = 0; i < terms.size(); i++) {
            add(vector, terms.get(i), 1.0f);
            if (i + 1 < terms.size()) {
                add(vector, terms.get(i) + " " + terms.get(i + 1), 0.5f);
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            vector[0] = 1f; // pgvector rejects zero vectors for cosine distance
            return vector;
        }
        float inv = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= inv;
        }
        return vector;
    }

    private void add(float[] vector, String feature, float weight) {
        int h = fnv1a(feature);
        int bucket = Math.floorMod(h, dimensions);
        float sign = ((h >>> 31) == 0) ? 1f : -1f;
        vector[bucket] += sign * weight;
    }

    /** FNV-1a: tiny, stable across JVMs (unlike {@code String.hashCode} it spreads short strings well). */
    static int fnv1a(String s) {
        int hash = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }
}
