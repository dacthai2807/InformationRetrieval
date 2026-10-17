package vn.hust.ir.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.hust.ir.indexing.UrlFileIndexingService;

@RestController
@RequestMapping("/api/indexing")
public class UrlIndexingController {

    private final UrlFileIndexingService service;

    public UrlIndexingController(UrlFileIndexingService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public UrlFileIndexingService.Status start(
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            if (!service.start(limit)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Quá trình nạp URL đang chạy."
                );
            }

            return service.status();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage()
            );
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage()
            );
        }
    }

    @GetMapping("/status")
    public UrlFileIndexingService.Status status() {
        return service.status();
    }
}