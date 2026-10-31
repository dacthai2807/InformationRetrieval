
package vn.hust.ir.indexing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContentChunker {

    private static final int CHUNK_SIZE = 400;
    private static final int CHUNK_OVERLAP = 50;

    public List<String> split(String content) {
        List<String> chunks = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return chunks;
        }

        String normalized = content
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = normalized.split(" ");

        if (words.length <= CHUNK_SIZE) {
            chunks.add(normalized);
            return chunks;
        }

        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + CHUNK_SIZE, words.length);

            String chunk = String.join(
                    " ",
                    java.util.Arrays.copyOfRange(words, start, end)
            );

            chunks.add(chunk);

            if (end == words.length) {
                break;
            }

            start = end - CHUNK_OVERLAP;
        }

        return chunks;
    }
}
