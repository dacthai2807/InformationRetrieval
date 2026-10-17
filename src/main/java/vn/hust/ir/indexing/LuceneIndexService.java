package vn.hust.ir.indexing;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.hust.ir.text.VietnameseTextAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LuceneIndexService {

    private final Analyzer analyzer = new VietnameseTextAnalyzer();

    @Value("${ir.storage.index-dir:index}")
    private String indexPath;

    private Directory directory;

    @PostConstruct
    public void initialize() throws IOException {
        Path path = Path.of(indexPath).toAbsolutePath();
        Files.createDirectories(path);
        directory = FSDirectory.open(path);
    }

    /**
     * Adds a document to the index or updates the existing document with the same URL.
     */
    public void indexDocument(String title, String url, String content)
            throws IOException {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Document URL must not be empty.");
        }

        Document document = new Document();
        document.add(new StringField("url", url, Field.Store.YES));
        document.add(new TextField(
                "title",
                title == null ? "" : title,
                Field.Store.YES
        ));
        document.add(new TextField(
                "content",
                content == null ? "" : content,
                Field.Store.YES
        ));

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setSimilarity(new ClassicSimilarity());

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            writer.updateDocument(new Term("url", url), document);
            writer.commit();
        }
    }

    public List<SearchResult> search(String queryText, int limit)
            throws Exception {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        if (!DirectoryReader.indexExists(directory)) {
            return List.of();
        }

        Map<String, Float> boosts = Map.of(
                "title", 2.0f,
                "content", 1.0f
        );

        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{"title", "content"},
                analyzer,
                boosts
        );
        parser.setDefaultOperator(
                MultiFieldQueryParser.Operator.OR
        );

        Query query = parser.parse(
                MultiFieldQueryParser.escape(queryText)
        );

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new ClassicSimilarity());

            TopDocs topDocs = searcher.search(query, Math.max(1, limit));
            List<SearchResult> results = new ArrayList<>();

            for (ScoreDoc hit : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(hit.doc);

                String content = document.get("content");
                String snippet = makeSnippet(content, queryText);

                results.add(new SearchResult(
                        document.get("title"),
                        document.get("url"),
                        snippet,
                        hit.score
                ));
            }

            return results;
        }
    }

    private String makeSnippet(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = content.replaceAll("\\s+", " ").trim();
        String normalizedContent = normalizeForSnippet(cleaned);
        String normalizedQuery = normalizeForSnippet(query);

        int position = normalizedContent.indexOf(normalizedQuery);
        if (position < 0) {
            return cleaned.substring(0, Math.min(240, cleaned.length()))
                    + (cleaned.length() > 240 ? "..." : "");
        }

        int start = Math.max(0, position - 80);
        int end = Math.min(cleaned.length(), position + 160);
        return (start > 0 ? "..." : "")
                + cleaned.substring(start, end)
                + (end < cleaned.length() ? "..." : "");
    }

    private String normalizeForSnippet(String value) {
        return java.text.Normalizer.normalize(
                        value.toLowerCase(java.util.Locale.ROOT)
                                .replace('đ', 'd'),
                        java.text.Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "");
    }

    @PreDestroy
    public void close() throws IOException {
        if (directory != null) {
            directory.close();
        }
        analyzer.close();
    }

    public record SearchResult(
            String title,
            String url,
            String snippet,
            float score
    ) {
    }
}