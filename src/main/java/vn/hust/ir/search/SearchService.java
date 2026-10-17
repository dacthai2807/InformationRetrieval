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

    public List<LuceneIndexService.SearchResult> search(String query)
            throws Exception {
        return indexService.search(query, 20);
    }
}