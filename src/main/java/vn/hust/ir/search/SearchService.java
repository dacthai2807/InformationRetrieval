
package vn.hust.ir.search;

import org.springframework.stereotype.Service;
import vn.hust.ir.indexing.LuceneIndexService;

import java.util.List;

@Service
public class SearchService {

    private final LuceneIndexService indexService;

    public SearchService(LuceneIndexService indexService) {
        this.indexService = indexService;
    }

    public List<LuceneIndexService.SearchResult> search(
            String query,
            String mode
    ) throws Exception {

        if ("full".equalsIgnoreCase(mode)) {
            return indexService.search(query, 20);
        }

        return indexService.searchChunks(query, 20);
    }

    // Giữ lại phương thức cũ nếu các thành phần khác đang sử dụng.
    public List<LuceneIndexService.SearchResult> search(String query)
            throws Exception {
        return indexService.searchChunks(query, 20);
    }
}
