# Phân tích & biểu đồ cho slide

- `Analyze.java` — mở chỉ mục Lucene (`index/`) và chạy lại đúng logic truy vấn của `LuceneIndexService` (parser `title^2/content^1`, OR, FILTER `documentType`, dedup chunk theo URL) cho 15 truy vấn, ở 2 chế độ × 2 mô hình (TF-IDF, BM25). Xuất `out/docs.tsv`, `out/terms.tsv`, `out/queries.tsv`.
- `charts.py` — đọc `out/*.tsv`, `url-crawled.txt`, `data/links.tsv` và vẽ `../charts/c01…c10`.

```bash
# Lucene 9.12.2 jars: core, queryparser, queries, sandbox, analysis-common (Maven Central)
CP=$(ls lib/*.jar | tr '\n' ':')
javac -cp "$CP" -d cls src/main/java/vn/hust/ir/text/VietnameseTextAnalyzer.java docs/slide/analysis/Analyze.java
java -cp "cls:$CP" Analyze index docs/slide/analysis/out      # copy index/ trước nếu muốn giữ nguyên bản gốc
pip install matplotlib && python docs/slide/analysis/charts.py
```
Lưu ý: số liệu phản ánh chỉ mục mẫu trong repo (159 trang / 405 chunk), không phải toàn bộ phiên crawl trực tiếp.
