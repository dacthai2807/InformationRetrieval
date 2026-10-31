
package vn.hust.ir.crawler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import vn.hust.ir.crawler.ContentExtractor.ExtractedPage;
import vn.hust.ir.crawler.ContentExtractor.LinkReference;

@Service
public class ResourceProcessingService {

    private static final Logger log =
            LoggerFactory.getLogger(ResourceProcessingService.class);

    private final ContentExtractor contentExtractor;
    private final OcrService ocrService;

    public ResourceProcessingService(
        ContentExtractor contentExtractor,
        OcrService ocrService
    ) {
        this.contentExtractor = contentExtractor;
        this.ocrService = ocrService;
    }

    public enum ResourceType {
        HTML,
        PDF,
        DOCUMENT,
        IMAGE,
        UNKNOWN
    }

    public record ProcessedResource(
            String url,
            String title,
            String publishDate,
            String author,
            String content,
            String contentType,
            ResourceType resourceType,
            List<LinkReference> links,
            Map<String, String> metadata
    ) {
    }

    public ResourceType detectType(String url, String contentType) {
        String mime = contentType == null
                ? ""
                : contentType.toLowerCase(Locale.ROOT);

        String path = "";

        try {
            path = URI.create(url).getPath();
        } catch (Exception ignored) {
            // Fall back to MIME type.
        }

        String lowerPath = path.toLowerCase(Locale.ROOT);

        if (mime.contains("text/html")
                || lowerPath.endsWith(".html")
                || lowerPath.endsWith(".htm")) {
            return ResourceType.HTML;
        }

        if (mime.contains("application/pdf")
                || lowerPath.endsWith(".pdf")) {
            return ResourceType.PDF;
        }

        if (mime.contains("wordprocessingml")
                || mime.contains("application/msword")
                || mime.contains("application/rtf")
                || mime.contains("application/vnd.oasis.opendocument.text")
                || lowerPath.endsWith(".docx")
                || lowerPath.endsWith(".doc")
                || lowerPath.endsWith(".rtf")
                || lowerPath.endsWith(".odt")) {
            return ResourceType.DOCUMENT;
        }

        if (mime.startsWith("image/")
                || hasImageExtension(lowerPath)) {
            return ResourceType.IMAGE;
        }

        return ResourceType.UNKNOWN;
    }

    public ProcessedResource process(
            String url,
            String contentType,
            String htmlBody,
            byte[] bytes,
            ContentExtractor.Strategy strategy
    ) throws Exception {

        ResourceType type = detectType(url, contentType);

        return switch (type) {
            case HTML -> processHtml(url, contentType, htmlBody, strategy);
            case PDF, DOCUMENT -> processDocument(
                    url, contentType, bytes, type);
            case IMAGE -> processImage(url, contentType, bytes);
            case UNKNOWN -> new ProcessedResource(
                    url,
                    fileNameFromUrl(url),
                    "",
                    "",
                    "",
                    contentType,
                    ResourceType.UNKNOWN,
                    List.of(),
                    Map.of()
            );
        };
    }

    private ProcessedResource processHtml(
            String url,
            String contentType,
            String html,
            ContentExtractor.Strategy strategy
    ) throws Exception {

        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("HTML rỗng: " + url);
        }

        ExtractedPage page = contentExtractor.extract(url, html, strategy);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", "html");

        List<LinkReference> links = new ArrayList<>();

        if (page.links() != null) {
            links.addAll(page.links());
        }

        // Bổ sung URL ảnh và media được nhúng trong trang.
        Document document = Jsoup.parse(html, url);

        for (Element element : document.select(
                "img[src], source[src], video[src], audio[src], "
                        + "embed[src], object[data]")) {

            String attribute = element.hasAttr("src") ? "src" : "data";
            String destination = element.absUrl(attribute);

            if (destination == null || destination.isBlank()) {
                continue;
            }

            String anchor = element.attr("alt");

            if (anchor.isBlank()) {
                anchor = element.attr("title");
            }

            if (anchor.isBlank()) {
                anchor = element.parent() != null
                        ? element.parent().text()
                        : "";
            }

            links.add(new LinkReference(destination, anchor));
        }

        // Loại các cạnh trùng hoàn toàn theo URL đích.
        Map<String, LinkReference> uniqueLinks = new LinkedHashMap<>();

        for (LinkReference link : links) {
            if (link == null
                    || link.destinationUrl() == null
                    || link.destinationUrl().isBlank()) {
                continue;
            }

            uniqueLinks.putIfAbsent(link.destinationUrl(), link);
        }

        return new ProcessedResource(
                page.url(),
                page.title(),
                page.publishDate(),
                page.author(),
                page.content(),
                contentType,
                ResourceType.HTML,
                new ArrayList<>(uniqueLinks.values()),
                metadata
        );
    }

    private ProcessedResource processDocument(
            String url,
            String contentType,
            byte[] bytes,
            ResourceType type
    ) throws Exception {

        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException(
                    "Nội dung tài liệu rỗng: " + url);
        }

        Metadata tikaMetadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(bytes)) {
            parser.parse(input, handler, tikaMetadata, new ParseContext());
        }

        Map<String, String> metadata = extractMetadata(tikaMetadata);

        String title = firstNonBlank(
                tikaMetadata.get(TikaCoreProperties.TITLE),
                fileNameFromUrl(url)
        );

        String author = firstNonBlank(
                tikaMetadata.get(TikaCoreProperties.CREATOR),
                tikaMetadata.get("Author"),
                ""
        );

        String publishDate = firstNonBlank(
                tikaMetadata.get(TikaCoreProperties.CREATED),
                tikaMetadata.get(TikaCoreProperties.MODIFIED),
                ""
        );

        String extractedContent = handler.toString().trim();

        metadata.put("sourceType", type.name().toLowerCase(Locale.ROOT));
        metadata.put("detectedContentType",
                firstNonBlank(tikaMetadata.get(Metadata.CONTENT_TYPE), contentType));

        return new ProcessedResource(
                url,
                title,
                publishDate,
                author,
                extractedContent,
                contentType,
                type,
                List.of(),
                metadata
        );
    }

    private ProcessedResource processImage(
        String url,
        String contentType,
        byte[] bytes
    ) {

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", "image");

        if (bytes != null) {
            metadata.put("sizeBytes", String.valueOf(bytes.length));
        }

        String content = "";

        // Trích xuất metadata ảnh qua Tika.
        if (bytes != null && bytes.length > 0) {
            try {
                Metadata tikaMetadata = new Metadata();
                ContentHandler handler = new BodyContentHandler(-1);
                AutoDetectParser parser = new AutoDetectParser();

                try (ByteArrayInputStream input =
                            new ByteArrayInputStream(bytes)) {
                    parser.parse(
                            input,
                            handler,
                            tikaMetadata,
                            new ParseContext()
                    );
                }

                metadata.putAll(extractMetadata(tikaMetadata));

            } catch (Exception e) {
                log.debug(
                        "[IMAGE METADATA] Không trích xuất được metadata: {}",
                        url,
                        e
                );
            }

            // OCR ảnh.
            try {
                content = ocrService.extractText(bytes);

                if (content == null) {
                    content = "";
                }

                metadata.put("ocrStatus",
                        content.isBlank() ? "NO_TEXT" : "SUCCESS");

                log.info(
                        "[IMAGE OCR] url={} | extractedCharacters={}",
                        url,
                        content.length()
                );

            } catch (Exception e) {
                metadata.put("ocrStatus", "FAILED");

                log.warn(
                        "[IMAGE OCR] Không trích xuất được văn bản: {}",
                        url,
                        e
                );
            }
        }

        return new ProcessedResource(
                url,
                fileNameFromUrl(url),
                "",
                "",
                content,
                contentType,
                ResourceType.IMAGE,
                List.of(),
                metadata
        );
    }

    private Map<String, String> extractMetadata(Metadata metadata) {
        Map<String, String> result = new LinkedHashMap<>();

        for (String name : metadata.names()) {
            String value = metadata.get(name);

            if (value != null && !value.isBlank()) {
                result.put(name, value);
            }
        }

        return result;
    }

    private boolean hasImageExtension(String path) {
        return path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".png")
                || path.endsWith(".gif")
                || path.endsWith(".webp")
                || path.endsWith(".bmp")
                || path.endsWith(".tif")
                || path.endsWith(".tiff")
                || path.endsWith(".svg")
                || path.endsWith(".ico");
    }

    private String fileNameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            String name = Paths.get(path).getFileName() == null
                    ? ""
                    : Paths.get(path).getFileName().toString();

            return name.isBlank() ? url : name;
        } catch (Exception e) {
            return url;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}

