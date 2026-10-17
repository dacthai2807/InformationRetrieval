package vn.hust.ir.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.hust.ir.crawler.WebCrawlerService;

@RestController
@RequestMapping("/api/crawl")
public class CrawlController {

    private final WebCrawlerService crawler;

    public CrawlController(WebCrawlerService crawler) {
        this.crawler = crawler;
    }

    @PostMapping("/start")
    public WebCrawlerService.CrawlStatus start() {
        if (!crawler.start()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Crawler đang chạy."
            );
        }

        return crawler.status();
    }

    @GetMapping("/status")
    public WebCrawlerService.CrawlStatus status() {
        return crawler.status();
    }
}