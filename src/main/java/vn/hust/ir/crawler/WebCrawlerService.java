
package vn.hust.ir.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import vn.hust.ir.crawler.ContentExtractor.LinkReference;
import vn.hust.ir.indexing.LuceneIndexService;
import vn.hust.ir.indexing.ContentChunker;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import vn.hust.ir.storage.PageDocument;
import vn.hust.ir.storage.PageRepository;

import java.time.Instant;

@Service
public class WebCrawlerService {

    private static final Logger log =
            LoggerFactory.getLogger(WebCrawlerService.class);

    private final LuceneIndexService indexService;
    private final ResourceProcessingService resourceProcessor;
    private final ThreadPoolTaskExecutor executor;
    // private final ObjectMapper objectMapper;
    private final ContentChunker contentChunker;
    private final PageRepository pageRepository;

    @Value("${ir.crawler.request-delay-ms:3000}")
    private long requestDelayMs;

    @Value("${ir.crawler.max-retries:3}")
    private int maxRetries;

    @Value("${ir.crawler.retry-backoff-ms:5000}")
    private long retryBackoffMs;

    private volatile long lastRequestTime = 0;

    @Value("${ir.crawler.allowed-domain:hust.edu.vn}")
    private String allowedDomain;

    @Value("${ir.crawler.max-pages-per-run:1000}")
    private int maxPages;

    @Value("${ir.crawler.legacy-url-file:url-crawled.txt}")
    private String historyFile;

    @Value("${ir.crawler.content-extraction-strategy:READABILITY}")
    private String extractionStrategyName;

    private final Path discoveredFile =
            Path.of("data", "url-discovered.txt");

    private final Path linksFile =
            Path.of("data", "links.tsv");

    // private final Path pagesFile =
    //         Path.of("data", "pages.jsonl");

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile int processed;
    private volatile int indexed;
    private volatile int errors;
    private volatile String message = "Crawler chưa chạy.";

    public WebCrawlerService(
            LuceneIndexService indexService,
            ResourceProcessingService resourceProcessor,
            ContentChunker contentChunker,
            @Qualifier("crawlExecutor")
            ThreadPoolTaskExecutor executor,
            PageRepository pageRepository
            // ObjectMapper objectMapper
    ) {
        this.indexService = indexService;
        this.resourceProcessor = resourceProcessor;
        this.contentChunker = contentChunker;
        this.executor = executor;
        // this.objectMapper = objectMapper;
        this.pageRepository = pageRepository;
    }

    public synchronized boolean start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[CRAWLER] Crawler đang chạy.");
            return false;
        }

        processed = 0;
        indexed = 0;
        errors = 0;
        message = "Đang khởi tạo crawler...";

        log.info(
                "[CRAWLER] Starting. Domain={}, maxPages={}, historyFile={}",
                allowedDomain, maxPages, historyFile
        );

        try {
            executor.execute(this::crawl);
            return true;
        } catch (RuntimeException e) {
            running.set(false);
            message = "Không thể khởi chạy crawler: " + e.getMessage();
            log.error("[CRAWLER] Không thể gửi tác vụ vào executor.", e);
            return false;
        }
    }

    public CrawlStatus status() {
        return new CrawlStatus(
                running.get(),
                processed,
                indexed,
                errors,
                maxPages,
                message
        );
    }

    private void waitBeforeRequest() throws InterruptedException {
        synchronized (this) {
            long elapsed = System.currentTimeMillis() - lastRequestTime;
            long remaining = requestDelayMs - elapsed;

            if (remaining > 0) {
                Thread.sleep(remaining);
            }

            lastRequestTime = System.currentTimeMillis();
        }
    }

    private void waitBeforeRetry(
            int retryAttempt,
            Connection.Response response
    ) throws InterruptedException {

        long delay = retryBackoffMs * (1L << (retryAttempt - 1));

        String retryAfter = response.headers()
                .get("Retry-After");

        if (retryAfter != null) {
            try {
                delay = Math.max(delay, Long.parseLong(retryAfter) * 1000L);
            } catch (NumberFormatException ignored) {
                // Retry-After không phải số giây.
            }
        }

        log.warn("[RETRY WAIT] attempt={} | delay={}ms",
                retryAttempt, delay);

        Thread.sleep(delay);
    }

    private void crawl() {
        try {
            Files.createDirectories(discoveredFile.getParent());

            ensureFile(discoveredFile);
            ensureFile(Path.of(historyFile));
            ensureFile(linksFile);
            // ensureFile(pagesFile);

            log.info("[CRAWLER] Working directory: {}",
                    Path.of("").toAbsolutePath());

            Set<String> crawled = readUrls(Path.of(historyFile));
            Set<String> discovered = readUrls(discoveredFile);

            log.info("[CRAWLER] URLs loaded: history={}, discovered={}",
                    crawled.size(), discovered.size());

            String seed = normalizeUrl("https://" + allowedDomain + "/");

            if (seed == null) {
                throw new IllegalStateException(
                        "Không thể chuẩn hóa seed URL: " + allowedDomain);
            }

            if (discovered.add(seed)) {
                appendLine(discoveredFile, seed);
                log.info("[CRAWLER] Added seed: {}", seed);
            }

            ArrayDeque<String> queue = new ArrayDeque<>(discovered);
            Set<String> attemptedThisRun = new LinkedHashSet<>();

            log.info("[CRAWLER] Initial queue size: {}", queue.size());

            while (!queue.isEmpty() && processed < maxPages) {
                String url = queue.removeFirst();

                if (!attemptedThisRun.add(url)) {
                    log.debug("[SKIP: DUPLICATE THIS RUN] {}", url);
                    continue;
                }

                if (crawled.contains(url) && !url.equals(seed)) {
                    log.debug("[SKIP: HISTORY] {}", url);
                    continue;
                }

                if (!isAllowedDomain(url)) {
                    log.debug("[SKIP: DISALLOWED DOMAIN] {}", url);
                    continue;
                }

                message = "Đang xử lý: " + url;

                try {
                    Connection.Response response = null;

                    for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {

                        waitBeforeRequest();

                        response = Jsoup.connect(url)
                                        .userAgent(
                                                "HUSTInformationRetrievalBot/1.0 "
                                                        + "(academic research crawler)"
                                        )
                                        .timeout(20000)
                                        .maxBodySize(100 * 1024 * 1024)
                                        .followRedirects(true)
                                        .ignoreHttpErrors(true)
                                        .ignoreContentType(true)
                                        .execute();

                        int statusCode = response.statusCode();

                        if (statusCode == 429 || statusCode >= 500) {

                            if (attempt <= maxRetries) {
                                log.warn(
                                        "[HTTP RETRY] URL={} | status={} | attempt={}/{}",
                                        url, statusCode, attempt, maxRetries
                                );

                                waitBeforeRetry(attempt, response);
                                continue;
                            }
                        }

                        break;
                    }

                    String finalUrl = normalizeUrl(
                            response.url().toExternalForm());

                    int statusCode = response.statusCode();

                    String contentType = response.contentType() == null
                            ? ""
                            : response.contentType().toLowerCase(Locale.ROOT);

                    byte[] bytes = response.bodyAsBytes();

                    log.info(
                            "[HTTP] requested={} | final={} | status={} "
                                    + "| contentType={} | bodyBytes={}",
                            url, finalUrl, statusCode, contentType, bytes.length
                    );

                    if (finalUrl == null || !isAllowedDomain(finalUrl)) {
                        errors++;
                        log.warn("[SKIP: REDIRECT OUTSIDE DOMAIN] {} -> {}",
                                url, finalUrl);
                        continue;
                    }

                    if (statusCode < 200 || statusCode >= 300) {
                        errors++;
                        log.warn("[HTTP ERROR] URL={} status={}",
                                finalUrl, statusCode);
                        continue;
                    }

                    ResourceProcessingService.ResourceType detectedType =
                            resourceProcessor.detectType(finalUrl, contentType);

                    if (detectedType
                            == ResourceProcessingService.ResourceType.UNKNOWN) {
                        log.info("[SKIP: UNSUPPORTED TYPE] {} | {}",
                                finalUrl, contentType);
                        continue;
                    }

                    if (crawled.contains(finalUrl) && !finalUrl.equals(seed)) {
                        if (crawled.add(url)) {
                            appendLine(Path.of(historyFile), url);
                        }

                        log.info("[SKIP: FINAL URL ALREADY CRAWLED] {}", finalUrl);
                        continue;
                    }

                    String htmlBody =
                            detectedType == ResourceProcessingService.ResourceType.HTML
                                    ? response.body()
                                    : null;

                    ResourceProcessingService.ProcessedResource resource =
                            resourceProcessor.process(
                                    finalUrl,
                                    contentType,
                                    htmlBody,
                                    bytes,
                                    resolveStrategy(extractionStrategyName)
                            );

                    processed++;

                    int contentLength = resource.content() == null
                            ? 0
                            : resource.content().length();

                    int linkCount = resource.links() == null
                            ? 0
                            : resource.links().size();

                    log.info(
                            "[RESOURCE] url={} | type={} | title={} "
                                    + "| contentLength={} | links={}",
                            finalUrl,
                            resource.resourceType(),
                            resource.title(),
                            contentLength,
                            linkCount
                    );

                    saveResourceMetadata(resource);

                    if (resource.content() != null
                            && !resource.content().isBlank()) {

                        // Index toàn bộ tài liệu.
                        indexService.indexDocument(
                                resource.title(),
                                finalUrl,
                                resource.content()
                        );

                        // Chia nội dung thành các chunk.
                        var chunks = contentChunker.split(
                                resource.content()
                        );

                        // Index các chunk.
                        indexService.indexChunks(
                                resource.title(),
                                finalUrl,
                                chunks
                        );

                        indexed++;

                        log.info(
                                "[INDEXED] {} | type={} | chunks={} | count={}",
                                finalUrl,
                                resource.resourceType(),
                                chunks.size(),
                                indexed
                        );

                    } else {
                        log.info("[NO TEXT TO INDEX] {} | type={}",
                                finalUrl, resource.resourceType());
                    }

                    if (crawled.add(finalUrl)) {
                        appendLine(Path.of(historyFile), finalUrl);
                    }

                    int discoveredBefore = discovered.size();
                    int queuedFromPage = 0;
                    int externalLinks = 0;
                    int invalidLinks = 0;
                    int duplicateLinks = 0;

                    if (resource.links() != null) {
                        for (LinkReference link : resource.links()) {
                            String destination =
                                    normalizeUrl(link.destinationUrl());

                            if (destination == null) {
                                invalidLinks++;
                                continue;
                            }

                            appendLink(
                                    finalUrl,
                                    destination,
                                    link.anchorText()
                            );

                            if (!isAllowedDomain(destination)) {
                                externalLinks++;
                                continue;
                            }

                            if (discovered.add(destination)) {
                                appendLine(discoveredFile, destination);

                                if (!crawled.contains(destination)) {
                                    queue.addLast(destination);
                                    queuedFromPage++;
                                }
                            } else {
                                duplicateLinks++;
                            }
                        }
                    }

                    log.info(
                            "[LINK SUMMARY] page={} | extracted={} "
                                    + "| newDiscovered={} | queued={} "
                                    + "| external={} | invalid={} "
                                    + "| duplicates={} | queueSize={}",
                            finalUrl,
                            linkCount,
                            discovered.size() - discoveredBefore,
                            queuedFromPage,
                            externalLinks,
                            invalidLinks,
                            duplicateLinks,
                            queue.size()
                    );

                    Thread.sleep(300);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message = "Crawler bị gián đoạn.";
                    log.warn("[CRAWLER] Interrupted at {}", url);
                    break;

                } catch (Exception e) {
                    errors++;
                    log.error("[CRAWL ERROR] URL={} | error={}",
                            url, e.getMessage(), e);
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                message = "Crawler bị gián đoạn.";
            } else if (processed >= maxPages && !queue.isEmpty()) {
                message = "Đã đạt giới hạn URL của lượt crawl.";
            } else {
                message = "Đã xử lý hết các URL hiện có trong hàng đợi.";
            }

            log.info(
                    "[CRAWLER SUMMARY] processed={} | indexed={} "
                            + "| errors={} | discovered={} | history={} "
                            + "| queueRemaining={} | message={}",
                    processed,
                    indexed,
                    errors,
                    discovered.size(),
                    crawled.size(),
                    queue.size(),
                    message
            );

        } catch (Exception e) {
            message = "Crawler gặp lỗi khởi tạo hoặc lưu dữ liệu: "
                    + e.getMessage();

            log.error("[CRAWLER FATAL]", e);

        } finally {
            running.set(false);
            log.info("[CRAWLER] Stopped.");
        }
    }

    private ContentExtractor.Strategy resolveStrategy(String name) {
        try {
            return ContentExtractor.Strategy.valueOf(
                    name.trim().toUpperCase(Locale.ROOT)
            );
        } catch (Exception e) {
            log.warn("[CRAWLER] Strategy '{}' không hợp lệ; dùng READABILITY.",
                    name);
            return ContentExtractor.Strategy.READABILITY;
        }
    }

    private boolean isAllowedDomain(String url) {
        try {
            String host = URI.create(url).getHost();

            if (host == null) {
                return false;
            }

            host = host.toLowerCase(Locale.ROOT);
            String domain = allowedDomain.toLowerCase(Locale.ROOT);

            return host.equals(domain) || host.endsWith("." + domain);

        } catch (Exception e) {
            return false;
        }
    }

    // private void saveResourceMetadata(
    //         ResourceProcessingService.ProcessedResource resource
    // ) throws IOException {

    //     Map<String, Object> record = new LinkedHashMap<>();

    //     record.put("url", resource.url());
    //     record.put("title", resource.title());
    //     record.put("publishDate", resource.publishDate());
    //     record.put("author", resource.author());
    //     record.put("content", resource.content());
    //     record.put("contentType", resource.contentType());
    //     record.put("resourceType", resource.resourceType().name());
    //     record.put("metadata", resource.metadata());
    //     record.put("links", resource.links());

    //     appendLine(
    //             pagesFile,
    //             objectMapper.writeValueAsString(record)
    //     );
    // }

    private void saveResourceMetadata(
        ResourceProcessingService.ProcessedResource resource
    ) {
        PageDocument document = new PageDocument(
                resource.url(), // Dùng URL làm MongoDB _id
                resource.url(),
                resource.title(),
                resource.publishDate(),
                resource.author(),
                resource.content(),
                resource.contentType(),
                resource.resourceType().name(),
                resource.links(),
                resource.metadata(),
                Instant.now()
        );

        pageRepository.save(document);

        log.info("[MONGODB SAVED] url={} | title={} | type={}",
                resource.url(),
                resource.title(),
                resource.resourceType());
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(rawUrl.trim()).normalize();

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                return null;
            }

            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);

            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }

            if (uri.getUserInfo() != null) {
                return null;
            }

            int port = uri.getPort();

            if ((scheme.equals("http") && port == 80)
                    || (scheme.equals("https") && port == 443)) {
                port = -1;
            }

            String path = uri.getRawPath();

            if (path == null || path.isBlank()) {
                path = "/";
            }

            String query = uri.getRawQuery();

            if (query != null) {
                query = java.util.Arrays.stream(query.split("&"))
                        .filter(part -> {
                            String key = part.split("=", 2)[0]
                                    .toLowerCase(Locale.ROOT);

                            return !key.startsWith("utm_")
                                    && !key.equals("fbclid")
                                    && !key.equals("gclid");
                        })
                        .collect(Collectors.joining("&"));

                if (query.isBlank()) {
                    query = null;
                }
            }

            return new URI(
                    scheme, null, host, port, path, query, null
            ).toASCIIString();

        } catch (Exception e) {
            log.debug("[URL NORMALIZE ERROR] raw={} | error={}",
                    rawUrl, e.getMessage());
            return null;
        }
    }

    private Set<String> readUrls(Path path) throws IOException {
        Set<String> urls = new LinkedHashSet<>();

        if (!Files.exists(path)) {
            return urls;
        }

        for (String line : Files.readAllLines(
                path, StandardCharsets.UTF_8)) {
            String normalized = normalizeUrl(line);

            if (normalized != null) {
                urls.add(normalized);
            } else if (!line.isBlank()) {
                log.warn("[FILE] Ignored invalid URL in {}: {}",
                        path, line);
            }
        }

        return urls;
    }

    private void ensureFile(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.notExists(path)) {
            Files.createFile(path);
            log.info("[FILE CREATED] {}", absolutePath);
        }
    }

    private void appendLine(Path path, String value) throws IOException {
        Files.writeString(
                path,
                value + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private void appendLink(
            String source,
            String destination,
            String anchorText
    ) throws IOException {

        String safeAnchor = anchorText == null
                ? ""
                : anchorText.replace("\t", " ")
                        .replace("\r", " ")
                        .replace("\n", " ");

        appendLine(
                linksFile,
                source + "\t" + destination + "\t" + safeAnchor
        );
    }

    public record CrawlStatus(
            boolean running,
            int processed,
            int indexed,
            int errors,
            int limit,
            String message
    ) {
    }
}

