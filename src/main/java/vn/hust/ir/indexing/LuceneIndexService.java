
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
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.hust.ir.text.VietnameseTextAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * Indexes or updates a complete document.
     *
     * Each complete document is marked with documentType = "full".
     */
    public void indexDocument(String title, String url, String content)
            throws IOException {

        validateUrl(url);

        Document document = new Document();

        document.add(new StringField(
                "documentType",
                "full",
                Field.Store.YES
        ));

        document.add(new StringField(
                "url",
                url,
                Field.Store.YES
        ));

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

        try (IndexWriter writer = createWriter()) {
            writer.updateDocument(new Term("url", url), document);
            writer.commit();
        }
    }

    /**
     * Indexes each content chunk as a separate Lucene document.
     *
     * Before indexing, all previous chunks belonging to the URL
     * are deleted to avoid stale or duplicated chunks.
     */
    public void indexChunks(
            String title,
            String url,
            List<String> chunks
    ) throws IOException {

        validateUrl(url);

        try (IndexWriter writer = createWriter()) {

            // Always remove old chunks, even if the new content is empty.
            writer.deleteDocuments(new Term("chunkUrl", url));

            if (chunks == null || chunks.isEmpty()) {
                writer.commit();
                return;
            }

            for (int i = 0; i < chunks.size(); i++) {

                String chunk = chunks.get(i);

                if (chunk == null || chunk.isBlank()) {
                    continue;
                }

                Document document = new Document();

                document.add(new StringField(
                        "documentType",
                        "chunk",
                        Field.Store.YES
                ));

                document.add(new StringField(
                        "chunkId",
                        url + "#chunk-" + i,
                        Field.Store.YES
                ));

                document.add(new StringField(
                        "chunkUrl",
                        url,
                        Field.Store.YES
                ));

                document.add(new TextField(
                        "title",
                        title == null ? "" : title,
                        Field.Store.YES
                ));

                document.add(new TextField(
                        "content",
                        chunk,
                        Field.Store.YES
                ));

                document.add(new org.apache.lucene.document.StoredField(
                        "chunkIndex",
                        i
                ));

                writer.addDocument(document);
            }

            writer.commit();
        }
    }

    /**
     * Searches only content chunks.
     *
     * Multiple matching chunks from the same URL are merged into
     * one search result, keeping the highest-scoring chunk.
     */
    public List<SearchResult> searchChunks(String queryText, int limit)
            throws Exception {

        return searchByType(queryText, limit, "chunk", true);
    }

    /**
     * Searches only complete documents.
     */
    public List<SearchResult> search(String queryText, int limit)
            throws Exception {

        return searchByType(queryText, limit, "full", false);
    }

    /**
     * Shared search implementation for full documents and chunks.
     */
    private List<SearchResult> searchByType(
            String queryText,
            int limit,
            String documentType,
            boolean deduplicateByUrl
    ) throws Exception {

        if (queryText == null || queryText.isBlank() || limit < 1) {
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

        Query textQuery = parser.parse(
                MultiFieldQueryParser.escape(queryText)
        );

        // Restrict results to the requested document type.
        Query typeQuery = new TermQuery(
                new Term("documentType", documentType)
        );

        Query query = new BooleanQuery.Builder()
                .add(textQuery, BooleanClause.Occur.MUST)
                .add(typeQuery, BooleanClause.Occur.FILTER)
                .build();

        try (DirectoryReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new ClassicSimilarity());

            // Retrieve more candidates when deduplication is enabled,
            // because several top-ranked chunks may belong to one URL.
            int candidateLimit = deduplicateByUrl
                    ? Math.min(1000, limit * 10)
                    : limit;

            TopDocs topDocs = searcher.search(
                    query,
                    Math.max(1, candidateLimit)
            );

            if (deduplicateByUrl) {
                return collectUniqueUrlResults(
                        searcher,
                        topDocs,
                        queryText,
                        limit,
                        true
                );
            }

            return collectResults(
                    searcher,
                    topDocs,
                    queryText
            );
        }
    }

    /**
     * Collects search results without URL deduplication.
     */
    private List<SearchResult> collectResults(
            IndexSearcher searcher,
            TopDocs topDocs,
            String queryText
    ) throws IOException {

        List<SearchResult> results = new ArrayList<>();

        for (ScoreDoc hit : topDocs.scoreDocs) {

            Document document = searcher.storedFields().document(hit.doc);

            String content = document.get("content");
            String url = document.get("url");

            String snippet = makeSnippet(content, queryText);

            results.add(new SearchResult(
                    document.get("title"),
                    url,
                    snippet,
                    hit.score,
                    -1
            ));
        }

        return results;
    }

    /**
     * Keeps only the highest-scoring chunk for each URL.
     */
    private List<SearchResult> collectUniqueUrlResults(
            IndexSearcher searcher,
            TopDocs topDocs,
            String queryText,
            int limit,
            boolean chunkResults
    ) throws IOException {

        Map<String, SearchResult> uniqueResults = new LinkedHashMap<>();

        for (ScoreDoc hit : topDocs.scoreDocs) {

            Document document = searcher.storedFields().document(hit.doc);

            String url = chunkResults
                    ? document.get("chunkUrl")
                    : document.get("url");

            if (url == null || uniqueResults.containsKey(url)) {
                continue;
            }

            int chunkIndex = -1;

            if (chunkResults) {
                var chunkIndexField = document.getField("chunkIndex");

                if (chunkIndexField != null
                        && chunkIndexField.numericValue() != null) {
                    chunkIndex = chunkIndexField.numericValue().intValue();
                }
            }

            String content = document.get("content");
            String snippet = makeSnippet(content, queryText);

            uniqueResults.put(url, new SearchResult(
                    document.get("title"),
                    url,
                    snippet,
                    hit.score,
                    chunkIndex
            ));

            if (uniqueResults.size() >= limit) {
                break;
            }
        }

        return new ArrayList<>(uniqueResults.values());
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Document URL must not be empty."
            );
        }
    }

    private IndexWriter createWriter() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        config.setOpenMode(
                IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        );

        config.setSimilarity(new ClassicSimilarity());

        return new IndexWriter(directory, config);
    }

    /**
     * Creates a short text snippet around the query occurrence.
     */
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
            float score,
            int chunkIndex
    ) {
    }
}
