
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
import vn.hust.ir.crawler.ContentExtractor.ExtractedPage;
import vn.hust.ir.crawler.ContentExtractor.LinkReference;
import vn.hust.ir.indexing.LuceneIndexService;

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

@Service
public class WebCrawlerService {

    private static final Logger log =
            LoggerFactory.getLogger(WebCrawlerService.class);

    private final LuceneIndexService indexService;
    private final ContentExtractor contentExtractor;
    private final ThreadPoolTaskExecutor executor;
    private final ObjectMapper objectMapper;

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

    private final Path pagesFile =
            Path.of("data", "pages.jsonl");

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile int processed;
    private volatile int indexed;
    private volatile int errors;
    private volatile String message = "Crawler chưa chạy.";

    public WebCrawlerService(
            LuceneIndexService indexService,
            ContentExtractor contentExtractor,
            @Qualifier("crawlExecutor")
            ThreadPoolTaskExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.indexService = indexService;
        this.contentExtractor = contentExtractor;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public synchronized boolean start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[CRAWLER] Yêu cầu khởi chạy bị bỏ qua: crawler đang chạy.");
            return false;
        }

        processed = 0;
        indexed = 0;
        errors = 0;
        message = "Đang khởi tạo crawler...";

        log.info("[CRAWLER] Starting. Domain={}, maxPages={}, historyFile={}",
                allowedDomain, maxPages, historyFile);

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

    private void crawl() {
        try {
            Files.createDirectories(discoveredFile.getParent());

            ensureFile(discoveredFile);
            ensureFile(Path.of(historyFile));
            ensureFile(linksFile);
            ensureFile(pagesFile);

            log.info("[CRAWLER] Working directory: {}",
                    Path.of("").toAbsolutePath());
            log.info("[CRAWLER] Discovered file: {}",
                    discoveredFile.toAbsolutePath());
            log.info("[CRAWLER] History file: {}",
                    Path.of(historyFile).toAbsolutePath());

            Set<String> crawled = readUrls(Path.of(historyFile));
            Set<String> discovered = readUrls(discoveredFile);

            log.info("[CRAWLER] URLs loaded: history={}, discovered={}",
                    crawled.size(), discovered.size());

            String seed = normalizeUrl("https://" + allowedDomain + "/");

            if (seed == null) {
                throw new IllegalStateException(
                        "Không thể chuẩn hóa seed URL cho domain: "
                                + allowedDomain);
            }

            if (discovered.add(seed)) {
                appendLine(discoveredFile, seed);
                log.info("[CRAWLER] Added seed: {}", seed);
            } else {
                log.info("[CRAWLER] Seed đã tồn tại trong discovered: {}",
                        seed);
            }

            ArrayDeque<String> queue = new ArrayDeque<>(discovered);
            Set<String> attemptedThisRun = new LinkedHashSet<>();

            log.info("[CRAWLER] Initial queue size: {}", queue.size());

            while (!queue.isEmpty() && processed < maxPages) {
                String url = queue.removeFirst();

                log.info("[CRAWLER] Dequeued URL: {} | remaining={}",
                        url, queue.size());

                if (crawled.contains(url)) {
                    log.info("[SKIP: HISTORY] {}", url);

                    // Cho phép crawl lại trang chủ để khôi phục hàng đợi
                    // khi discovered file chỉ chứa seed URL.
                    if (!url.equals(seed)) {
                        continue;
                    }

                    log.info("[CRAWLER] Seed URL sẽ được tải lại để khám phá link.");
                }

                if (!attemptedThisRun.add(url)) {
                    log.info("[SKIP: DUPLICATE THIS RUN] {}", url);
                    continue;
                }

                if (!isAllowedDomain(url)) {
                    log.info("[SKIP: DISALLOWED DOMAIN] {}", url);
                    continue;
                }

                message = "Đang xử lý: " + url;

                try {
                    Connection.Response response = Jsoup.connect(url)
                            .userAgent(
                                    "HUSTInformationRetrievalBot/1.0 "
                                            + "(academic research crawler)"
                            )
                            .timeout(20_000)
                            .maxBodySize(10 * 1024 * 1024)
                            .followRedirects(true)
                            .ignoreHttpErrors(true)
                            .execute();

                    String finalUrl = normalizeUrl(
                            response.url().toExternalForm());

                    int statusCode = response.statusCode();

                    String contentType = response.contentType() == null
                            ? ""
                            : response.contentType()
                                    .toLowerCase(Locale.ROOT);

                    log.info(
                            "[HTTP] requested={} | final={} | status={} "
                                    + "| contentType={} | bodyBytes={}",
                            url,
                            finalUrl,
                            statusCode,
                            contentType,
                            response.bodyAsBytes().length
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

                    // This stage currently processes HTML pages only.
                    // PDF/DOCX and other document types can be handled by Tika.
                    if (!contentType.contains("text/html")) {
                        log.info("[SKIP: NON-HTML] {} | {}",
                                finalUrl, contentType);
                        continue;
                    }

                    processed++;

                    if (crawled.contains(finalUrl) && !finalUrl.equals(seed)) {
                        if (!crawled.contains(url)) {
                            crawled.add(url);
                            appendLine(Path.of(historyFile), url);
                        }

                        log.info("[SKIP: FINAL URL ALREADY CRAWLED] {}", finalUrl);
                        continue;
                    }

                    ContentExtractor.Strategy strategy =
                            resolveStrategy(extractionStrategyName);

                    ExtractedPage page = contentExtractor.extract(
                            finalUrl,
                            response.body(),
                            strategy
                    );

                    int contentLength = page.content() == null
                            ? 0
                            : page.content().length();

                    int linkCount = page.links() == null
                            ? 0
                            : page.links().size();

                    log.info(
                            "[EXTRACT] url={} | title={} | contentLength={} "
                                    + "| extractedLinks={}",
                            finalUrl,
                            page.title(),
                            contentLength,
                            linkCount
                    );

                    // Show up to 10 links to help diagnose extraction.
                    if (page.links() != null) {
                        page.links().stream()
                                .limit(10)
                                .forEach(link ->
                                        log.info(
                                                "[EXTRACT SAMPLE LINK] {} | anchor={}",
                                                link.destinationUrl(),
                                                link.anchorText()
                                        ));
                    }

                    savePageMetadata(page, contentType);

                    if (page.content() != null
                            && !page.content().isBlank()) {
                        indexService.indexDocument(
                                page.title(),
                                finalUrl,
                                page.content()
                        );
                        indexed++;

                        log.info("[INDEXED] {} | indexedCount={}",
                                finalUrl, indexed);
                    } else {
                        log.warn("[EMPTY CONTENT] {}",
                                finalUrl);
                    }

                    if (crawled.add(finalUrl)) {
                        appendLine(Path.of(historyFile), finalUrl);
                        log.info("[HISTORY SAVED] {}", finalUrl);
                    }

                    int discoveredBefore = discovered.size();
                    int queuedFromPage = 0;
                    int externalLinks = 0;
                    int invalidLinks = 0;
                    int duplicateLinks = 0;

                    if (page.links() != null) {
                        for (LinkReference link : page.links()) {
                            String rawUrl = link.destinationUrl();
                            String destination = normalizeUrl(rawUrl);

                            if (destination == null) {
                                invalidLinks++;
                                log.debug("[SKIP: INVALID URL] raw={}",
                                        rawUrl);
                                continue;
                            }

                            boolean allowed = isAllowedDomain(destination);

                            appendLink(
                                    finalUrl,
                                    destination,
                                    link.anchorText()
                            );

                            if (!allowed) {
                                externalLinks++;
                                log.debug("[EXTERNAL LINK] {} -> {}",
                                        finalUrl, destination);
                                continue;
                            }

                            if (discovered.add(destination)) {
                                appendLine(discoveredFile, destination);

                                log.info("[DISCOVERED] {}",
                                        destination);

                                if (!crawled.contains(destination)) {
                                    queue.addLast(destination);
                                    queuedFromPage++;

                                    log.info("[QUEUED] {} | queueSize={}",
                                            destination, queue.size());
                                } else {
                                    log.debug(
                                            "[NOT QUEUED: ALREADY CRAWLED] {}",
                                            destination);
                                }
                            } else {
                                duplicateLinks++;
                                log.debug("[DUPLICATE DISCOVERED] {}",
                                        destination);
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

                    // Be polite to the target server.
                    Thread.sleep(300);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message = "Crawler bị gián đoạn.";
                    log.warn("[CRAWLER] Interrupted while processing {}",
                            url);
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

            log.error("[CRAWLER FATAL] Initialization or persistence failed",
                    e);

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

    private void savePageMetadata(
            ExtractedPage page,
            String contentType
    ) throws IOException {
        Map<String, Object> record = new LinkedHashMap<>();

        record.put("url", page.url());
        record.put("title", page.title());
        record.put("publishDate", page.publishDate());
        record.put("author", page.author());
        record.put("content", page.content());
        record.put("contentType", contentType);
        record.put("links", page.links());

        appendLine(pagesFile, objectMapper.writeValueAsString(record));
    }

    private boolean isAllowedDomain(String url) {
        try {
            String host = URI.create(url).getHost();

            if (host == null) {
                return false;
            }

            host = host.toLowerCase(Locale.ROOT);
            String domain = allowedDomain.toLowerCase(Locale.ROOT);

            return host.equals(domain)
                    || host.endsWith("." + domain);

        } catch (Exception e) {
            return false;
        }
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
                    scheme,
                    null,
                    host,
                    port,
                    path,
                    query,
                    null
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
