
package vn.hust.ir.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import vn.hust.ir.crawler.ContentExtractor;
import vn.hust.ir.crawler.ResourceProcessingService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class UrlFileIndexingService {

    private final LuceneIndexService lucene;
    private final ResourceProcessingService resourceProcessor;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${ir.crawler.legacy-url-file:url-crawled.txt}")
    private String urlFile;

    private volatile int total;
    private volatile int processed;
    private volatile int indexed;
    private volatile int errors;
    private volatile String message = "Chưa bắt đầu nạp URL.";
    private final ContentChunker contentChunker;

    public UrlFileIndexingService(
            LuceneIndexService lucene,
            ResourceProcessingService resourceProcessor,
            ContentChunker contentChunker,
            @Qualifier("crawlExecutor")
            ThreadPoolTaskExecutor executor
    ) {
        this.lucene = lucene;
        this.resourceProcessor = resourceProcessor;
        this.contentChunker = contentChunker;
        this.executor = executor;
    }

    public synchronized boolean start(int requestedLimit) {
        if (running.get()) {
            return false;
        }

        if (requestedLimit < 1 || requestedLimit > 1000) {
            throw new IllegalArgumentException(
                    "Số URL phải nằm trong khoảng 1 đến 1000."
            );
        }

        Path path = Path.of(urlFile).toAbsolutePath();

        try {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException(
                        "Không tìm thấy file URL: " + path
                );
            }

            List<String> urls = Files.readAllLines(
                    path, StandardCharsets.UTF_8
            ).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .limit(requestedLimit)
                    .toList();

            if (urls.isEmpty()) {
                throw new IllegalStateException(
                        "File URL không có địa chỉ hợp lệ."
                );
            }

            total = urls.size();
            processed = 0;
            indexed = 0;
            errors = 0;
            message = "Đang chờ xử lý " + total + " URL.";

            running.set(true);

            try {
                executor.execute(() -> processUrls(urls));
            } catch (RuntimeException e) {
                running.set(false);
                throw e;
            }

            return true;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không thể khởi tạo nạp URL: " + e.getMessage(),
                    e
            );
        }
    }

    public Status status() {
        return new Status(
                running.get(),
                total,
                processed,
                indexed,
                errors,
                message
        );
    }

    private void processUrls(List<String> urls) {
        try {
            for (String url : urls) {
                if (Thread.currentThread().isInterrupted()) {
                    message = "Quá trình nạp URL đã bị gián đoạn.";
                    break;
                }

                message = "Đang xử lý: " + url;

                try {
                    Connection.Response response = Jsoup.connect(url)
                            .userAgent(
                                    "HUSTInformationRetrievalBot/1.0 "
                                            + "(academic research crawler)"
                            )
                            .timeout(20000)
                            .maxBodySize(10 * 1024 * 1024)
                            .followRedirects(true)
                            .ignoreHttpErrors(true)
                            .execute();

                    int statusCode = response.statusCode();

                    if (statusCode < 200 || statusCode >= 300) {
                        errors++;
                        continue;
                    }

                    String finalUrl =
                            response.url().toExternalForm();

                    String contentType = response.contentType() == null
                            ? ""
                            : response.contentType().toLowerCase(
                                    java.util.Locale.ROOT
                            );

                    byte[] bytes = response.bodyAsBytes();

                    ResourceProcessingService.ResourceType type =
                            resourceProcessor.detectType(
                                    finalUrl, contentType
                            );

                    if (type
                            == ResourceProcessingService.ResourceType.UNKNOWN) {
                        errors++;
                        continue;
                    }

                    String htmlBody =
                            type == ResourceProcessingService.ResourceType.HTML
                                    ? response.body()
                                    : null;

                    ResourceProcessingService.ProcessedResource resource =
                            resourceProcessor.process(
                                    finalUrl,
                                    contentType,
                                    htmlBody,
                                    bytes,
                                    ContentExtractor.Strategy.READABILITY
                            );

                    if (resource.content() != null
                            && !resource.content().isBlank()) {

                        // Index toàn bộ nội dung tài liệu.
                        lucene.indexDocument(
                                resource.title(),
                                resource.url(),
                                resource.content()
                        );

                        // Chia nội dung thành các chunk.
                        List<String> chunks = contentChunker.split(
                                resource.content()
                        );

                        // Index từng chunk riêng biệt.
                        lucene.indexChunks(
                                resource.title(),
                                resource.url(),
                                chunks
                        );

                        indexed++;
                    }

                    // Image resources are processed for metadata only.
                    // This service does not persist that metadata to JSONL.
                    Thread.sleep(300);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message = "Quá trình nạp URL đã bị gián đoạn.";
                    break;

                } catch (Exception e) {
                    errors++;
                } finally {
                    processed++;
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                message = "Đã dừng quá trình nạp URL.";
            } else {
                message = "Hoàn tất nạp URL.";
            }

        } finally {
            running.set(false);
        }
    }

    public record Status(
            boolean running,
            int total,
            int processed,
            int indexed,
            int errors,
            String message
    ) {
    }
}
