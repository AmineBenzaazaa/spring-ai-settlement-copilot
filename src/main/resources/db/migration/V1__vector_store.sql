-- RAG chunks for Spring AI's PgVectorStore. Flyway owns the schema; Spring AI's own
-- initialize-schema is off. 384 dimensions = HashingEmbeddingModel.DEFAULT_DIMENSIONS.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        uuid PRIMARY KEY,
    content   text NOT NULL,
    metadata  json,
    embedding vector(384) NOT NULL
);

-- Approximate nearest neighbour search with cosine distance (the <=> operator).
CREATE INDEX vector_store_embedding_hnsw ON vector_store USING hnsw (embedding vector_cosine_ops);
