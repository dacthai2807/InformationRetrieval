package vn.hust.ir.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class UrlFileIndexingService {

    private final LuceneIndexService lucene;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${ir.crawler.legacy-url-file:url-crawled.txt}")
    private String urlFile;

    private volatile int total;
    private volatile int processed;
    private volatile int indexed;
    private volatile int errors;
    private volatile String message = "Chưa bắt đầu nạp URL.";

    public UrlFileIndexingService(
            LuceneIndexService lucene,
            @Qualifier("crawlExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.lucene = lucene;
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
            executor.execute(() -> processUrls(urls));
            return true;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không thể khởi tạo nạp URL: " + e.getMessage(), e
            );
        }
    }

    public Status status() {
        return new Status(
                running.get(), total, processed, indexed, errors, message
        );
    }

    private void processUrls(List<String> urls) {
        try {
            for (String url : urls) {
                message = "Đang xử lý: " + url;

                try {
                    Connection.Response response = Jsoup.connect(url)
                            .userAgent("HUSTInformationRetrievalBot/1.0")
                            .timeout(15000)
                            .maxBodySize(10 * 1024 * 1024)
                            .followRedirects(true)
                            .ignoreHttpErrors(true)
                            .execute();

                    if (response.statusCode() < 200
                            || response.statusCode() >= 300) {
                        errors++;
                        continue;
                    }

                    String contentType = response.contentType() == null
                            ? ""
                            : response.contentType().toLowerCase();

                    // PDF/DOCX extraction will be added separately.
                    if (!contentType.contains("text/html")) {
                        errors++;
                        continue;
                    }

                    String finalUrl = response.url().toExternalForm();

                    Document page = response.parse();
                    page.select(
                            "script, style, noscript, nav, footer, "
                            + "header, aside, form, iframe, svg"
                    ).remove();

                    Element main = page.selectFirst(
                            "main, article, [role=main], "
                            + ".post-content, .entry-content, .content-area"
                    );

                    String content = main != null
                            ? main.text()
                            : page.body().text();

                    content = content.replaceAll("\\s+", " ").trim();

                    if (content.isBlank()) {
                        errors++;
                        continue;
                    }

                    lucene.indexDocument(page.title(), finalUrl, content);
                    indexed++;

                    // Be polite to the website.
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