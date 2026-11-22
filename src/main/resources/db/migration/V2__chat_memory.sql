-- Spring AI JdbcChatMemoryRepository table (PostgreSQL dialect), with a wider conversation_id:
-- the copilot keys memory by "<username>:<conversationId>" so users never share a conversation.
CREATE TABLE spring_ai_chat_memory (
    conversation_id varchar(255) NOT NULL,
    content         text         NOT NULL,
    type            varchar(10)  NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     timestamp    NOT NULL
);

CREATE INDEX spring_ai_chat_memory_conversation_id_timestamp_idx
    ON spring_ai_chat_memory (conversation_id, "timestamp");
