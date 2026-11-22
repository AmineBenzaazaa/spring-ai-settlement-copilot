package com.msetransport.copilot.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the settlement documentation ({@code classpath:knowledge/*.md}) into pgvector at startup.
 * Each {@code ## section} becomes one chunk with a stable id ({@code UUID} derived from file +
 * heading), so restarting replaces the chunks instead of duplicating them.
 */
@Component
public class KnowledgeIngestor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestor.class);
    static final String LOCATION = "classpath:knowledge/*.md";

    private final VectorStore vectorStore;
    private volatile int chunkCount;

    public KnowledgeIngestor(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        List<Document> chunks = loadChunks();
        vectorStore.delete(chunks.stream().map(Document::getId).toList());
        vectorStore.add(chunks);
        chunkCount = chunks.size();
        log.info("Knowledge base ready: {} chunks indexed in pgvector", chunks.size());
    }

    public int chunkCount() {
        return chunkCount;
    }

    static List<Document> loadChunks() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            List<Document> chunks = new ArrayList<>();
            for (Resource resource : resources) {
                String file = resource.getFilename();
                chunks.addAll(split(file, resource.getContentAsString(StandardCharsets.UTF_8)));
            }
            return chunks;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** One chunk per "## " section; the "# " title is kept as the document title. */
    static List<Document> split(String file, String markdown) {
        String docTitle = file;
        List<Document> chunks = new ArrayList<>();
        String heading = null;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("# ")) {
                docTitle = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                addChunk(chunks, file, docTitle, heading, body);
                heading = line.substring(3).trim();
                body.setLength(0);
            } else {
                appendReflowed(body, line);
            }
        }
        addChunk(chunks, file, docTitle, heading, body);
        return chunks;
    }

    /**
     * Joins hard-wrapped lines into one line per paragraph or bullet, so answers do not inherit the
     * markdown file's line breaks. Blank lines and new "- " bullets still start a new line.
     */
    static void appendReflowed(StringBuilder body, String line) {
        boolean continuation = !line.isBlank() && !line.startsWith("- ") && !body.isEmpty()
                && body.charAt(body.length() - 1) == '\n'
                && body.length() >= 2 && body.charAt(body.length() - 2) != '\n';
        if (continuation) {
            body.setLength(body.length() - 1);
            body.append(' ').append(line.strip()).append('\n');
        } else {
            body.append(line.isBlank() ? "" : line.stripTrailing()).append('\n');
        }
    }

    private static void addChunk(List<Document> chunks, String file, String docTitle, String heading, StringBuilder body) {
        String text = body.toString().trim();
        if (heading == null || text.isEmpty()) {
            return;
        }
        String id = UUID.nameUUIDFromBytes((file + "#" + heading).getBytes(StandardCharsets.UTF_8)).toString();
        chunks.add(Document.builder()
                .id(id)
                .text(heading + "\n" + text)
                .metadata(Map.of("source", file, "title", docTitle + " › " + heading))
                .build());
    }
}
