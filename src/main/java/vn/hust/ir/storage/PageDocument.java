package vn.hust.ir.storage;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import vn.hust.ir.crawler.ContentExtractor.LinkReference;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "pages")
public record PageDocument(
        @Id
        String id,

        String url,
        String title,
        String publishDate,
        String author,
        String content,
        String contentType,
        String resourceType,
        List<LinkReference> links,
        Map<String, String> metadata,
        Instant crawledAt
) {
}