
package vn.hust.ir.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.hust.ir.indexing.LuceneIndexService;
import vn.hust.ir.search.SearchService;

import java.util.List;

@Controller
public class HomeController {

    private final SearchService searchService;

    public HomeController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "chunk") String mode,
            Model model
    ) {

        // Chỉ chấp nhận hai chế độ tìm kiếm.
        mode = "full".equalsIgnoreCase(mode) ? "full" : "chunk";

        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("mode", mode);

        boolean searched = q != null && !q.isBlank();

        model.addAttribute("searched", searched);
        model.addAttribute("searchErrorPresent", false);

        List<LuceneIndexService.SearchResult> results = List.of();

        if (searched) {
            try {
                results = searchService.search(q.trim(), mode);

            } catch (Exception e) {
                model.addAttribute("searchErrorPresent", true);
                model.addAttribute("searchError", e.getMessage());
            }
        }

        model.addAttribute("results", results);
        model.addAttribute("resultCount", results.size());

        return "index";
    }
}
