
package vn.hust.ir.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import vn.hust.ir.crawler.ResourceProcessingService;
import vn.hust.ir.crawler.ContentExtractor;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/test-extraction")
public class ExtractionTestController {

    private final ResourceProcessingService resourceProcessingService;

    public ExtractionTestController(
            ResourceProcessingService resourceProcessingService) {
        this.resourceProcessingService = resourceProcessingService;
    }

    /**
     * Test bằng cách upload file trực tiếp.
     * POST /api/test-extraction/upload
     */
    @PostMapping(
        value = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        var result = resourceProcessingService.process(
                file.getOriginalFilename(),
                file.getContentType(),
                null,
                file.getBytes(),
                ContentExtractor.Strategy.READABILITY
        );

        return toResponse(result);
    }

    /**
     * Test bằng URL tài nguyên.
     * GET /api/test-extraction/url?url=...
     */
    @GetMapping("/url")
    public Map<String, Object> testUrl(
            @RequestParam("url") String url
    ) throws Exception {

        URI uri = URI.create(url);

        // Chỉ chấp nhận HTTP/HTTPS.
        String scheme = uri.getScheme();
        if (scheme == null ||
                !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "Chỉ hỗ trợ URL HTTP/HTTPS");
        }

        var connection = uri.toURL().openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        byte[] bytes;
        String contentType;

        try (var input = connection.getInputStream()) {
            bytes = input.readAllBytes();
            contentType = connection.getContentType();
        }

        var result = resourceProcessingService.process(
                url,
                contentType,
                null,
                bytes,
                ContentExtractor.Strategy.READABILITY
        );

        return toResponse(result);
    }

    /**
     * Chuẩn hóa kết quả trả về cho cả hai endpoint.
     */
    private Map<String, Object> toResponse(
            ResourceProcessingService.ProcessedResource result) {

        return Map.of(
                "url", safe(result.url()),
                "title", safe(result.title()),
                "author", safe(result.author()),
                "publishDate", safe(result.publishDate()),
                "resourceType", result.resourceType(),
                "contentType", safe(result.contentType()),
                "content", safe(result.content()),
                "contentLength", safe(result.content()).length(),
                "metadata", result.metadata()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
