package vn.hust.ir.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.hust.ir.indexing.LuceneIndexService;

import java.io.IOException;

@RestController
@RequestMapping("/api/index")
public class IndexingController {

    private final LuceneIndexService indexService;

    public IndexingController(LuceneIndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
    public IndexResponse indexDocument(@RequestBody IndexRequest request)
            throws IOException {
        if (request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "URL is required."
            );
        }

        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Document content is required."
            );
        }

        indexService.indexDocument(
                request.title(),
                request.url(),
                request.content()
        );

        return new IndexResponse("indexed", request.url());
    }

    public record IndexRequest(String title, String url, String content) {
    }

    public record IndexResponse(String status, String url) {
    }
}