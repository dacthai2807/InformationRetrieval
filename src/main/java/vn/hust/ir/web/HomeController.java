
package vn.hust.ir.web;

import vn.hust.ir.indexing.LuceneIndexService;
import vn.hust.ir.search.SearchService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;

@Controller
public class HomeController {

    private final SearchService searchService;

    public HomeController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "q", required = false) String q,
            Model model) {

        // Initialize every attribute used by the Thymeleaf template.
        List<LuceneIndexService.SearchResult> results =
                Collections.emptyList();

        boolean searched = q != null && !q.isBlank();
        boolean searchErrorPresent = false;
        String searchError = null;

        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("searched", searched);
        model.addAttribute("results", results);
        model.addAttribute("resultCount", 0);
        model.addAttribute("searchErrorPresent", false);
        model.addAttribute("searchError", null);

        if (searched) {
            try {
                results = searchService.search(q.trim());

                if (results == null) {
                    results = Collections.emptyList();
                }

            } catch (Exception e) {
                searchErrorPresent = true;
                searchError = "Không thể thực hiện tìm kiếm. "
                        + "Vui lòng kiểm tra log của ứng dụng.";

                // Log the actual exception for debugging.
                org.slf4j.LoggerFactory
                        .getLogger(HomeController.class)
                        .error("Search failed for query: {}", q, e);

                results = Collections.emptyList();
            }
        }

        // Update the model with the final search state.
        model.addAttribute("results", results);
        model.addAttribute("resultCount", results.size());
        model.addAttribute("searchErrorPresent", searchErrorPresent);
        model.addAttribute("searchError", searchError);

        return "index";
    }
}
