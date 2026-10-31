// Slides 11–20 + appendix. Loaded by main.js after build.js.
module.exports = (ctx) => {
  const { pres, L, slide, NOTES, X0, W, Y0, A, D, C, UI } = ctx;
  const { COL, FONT } = L;
  const S = pres.ShapeType;
  const AP = {}; // appendix notes
  const apx = (title, note) => {
    const s = slide(title, "__none__");
    if (note) s.addNotes("[" + title + "]\n\n" + note);
    return s;
  };
  const codeBox = (s, x, y, w, h, lines, size = 10.5) => {
    s.addShape(S.rect, { x, y, w, h, fill: { color: COL.code }, line: { color: COL.code } });
    s.addText(lines.map((l, i) => ({ text: l.t, options: { color: l.c || "E6EDF7", fontFace: L.MONO, fontSize: size, breakLine: i < lines.length - 1 } })),
      { x: x + 0.12, y: y + 0.08, w: w - 0.24, h: h - 0.16, margin: 0, valign: "top", isTextBox: true });
  };

  // =====================================================================
  // 11. HAI CHIẾN LƯỢC LẬP CHỈ MỤC
  // =====================================================================
  {
    const s = slide("Lập chỉ mục Lucene: hai chiến lược trong cùng một chỉ mục");
    let p = L.panel(s, X0, Y0, 7.7, 2.75, "Hai loại document, phân biệt bằng trường `documentType`");
    L.table(s, ["", "Full-document", "Chunk-based"], [
      ["`documentType`", "`full`", "`chunk`"],
      ["Đơn vị lập chỉ mục", "cả trang", "đoạn ~400 từ"],
      ["Khóa", "`url` (StringField)", "`chunkUrl` + `chunkId = url#chunk-i`"],
      ["Trường văn bản", "`title`, `content` (TextField, stored)", "`title`, `content` (= chunk), `chunkIndex`"],
      ["Cập nhật (idempotent)", "`updateDocument(Term url)` → thay thế", "`deleteDocuments(Term chunkUrl)` rồi thêm lại"],
    ], p.x, p.y, p.w, [1.85, 2.65, 2.94], { size: 11, boldFirst: true, rowH: 0.4 });
    p = L.panel(s, 8.35, Y0, 4.48, 2.75, "Số liệu chỉ mục mẫu");
    L.img(s, C("c02_index_composition.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, X0, 3.9, 7.7, 2.65, "ContentChunker: cửa sổ trượt 400 từ, chồng lấn 50 từ");
    const sc = 7.2 / 1100, bx = p.x + 0.1;
    const rows = [[0, 400, "Chunk #0 · từ 0–399"], [350, 750, "Chunk #1 · từ 350–749"], [700, 1100, "Chunk #2 · từ 700–1099"]];
    rows.forEach(([a, b, t], i) => {
      const y = p.y + 0.05 + i * 0.5;
      s.addShape(S.rect, { x: bx + a * sc, y, w: (b - a) * sc, h: 0.38, fill: { color: i === 1 ? COL.orangeL : COL.blueL }, line: { color: i === 1 ? COL.orange : COL.navy, width: 1 } });
      L.text(s, `**${t}**`, { x: bx + a * sc, y, w: (b - a) * sc, h: 0.38, size: 10.5, align: "center", valign: "middle" });
    });
    [[350, 400], [700, 750]].forEach(([a, b]) => s.addShape(S.rect, { x: bx + a * sc, y: p.y + 0.05 + (a === 350 ? 0 : 0.5), w: (b - a) * sc, h: 0.88, fill: { color: COL.red, transparency: 55 }, line: { color: COL.red, width: 0.75 } }));
    L.text(s, "Trang 1.100 từ → 3 chunk · bước **350 từ** · vùng đỏ = overlap **50 từ** (12,5%) · trang ≤ 400 từ → 1 chunk", { x: bx, y: p.y + 1.62, w: 7.3, h: 0.25, size: 11 });
    L.text(s, "`start = end − CHUNK_OVERLAP` → không cắt đứt cụm từ ở ranh giới đoạn.", { x: bx, y: p.y + 1.95, w: 7.3, h: 0.25, size: 11, color: COL.grey });
    p = L.panel(s, 8.35, 3.9, 4.48, 2.65, "Vì sao cần chunk?");
    L.list(s, [
      "TF-IDF **chuẩn hóa theo độ dài trường** → trang rất dài (PDF, văn bản quy định) bị phạt nặng.",
      "Chunk ~400 từ → điểm **công bằng hơn** và trả về **đúng đoạn liên quan**.",
      "Snippet bám đoạn khớp nhất; overlap tránh mất cụm ở ranh giới.",
      "Index lại cùng URL **không sinh bản sao**.",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 11.5, gap: 5 });
  }

  // =====================================================================
  // 12. TRUY VẤN & XẾP HẠNG
  // =====================================================================
  {
    const s = slide("Truy vấn và xếp hạng TF-IDF");
    let p = L.panel(s, X0, Y0, 4.1, 2.85, "Phân tích truy vấn");
    L.list(s, [
      "`MultiFieldQueryParser` trên **title (boost ×2,0)** và **content (×1,0)**.",
      "Toán tử mặc định **OR** → ưu tiên recall; khớp nhiều từ/bigram → điểm cao hơn.",
      "`escape(queryText)` chống ký tự đặc biệt của cú pháp Lucene.",
      "`BooleanQuery`: text **MUST** + `documentType` **FILTER** (không tính điểm).",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12, gap: 6 });
    p = L.panel(s, X0, 3.99, 4.1, 2.56, "Mô hình xếp hạng: ClassicSimilarity");
    codeBox(s, p.x, p.y, p.w, 1.18, [{ t: "score = coord · queryNorm ·" }, { t: "  Σ tf·idf²·boost·norm" }, { t: "tf = √freq" }, { t: "idf = 1 + ln((N+1)/(df+1))" }, { t: "norm = 1/√(số term trong trường)" }], 10.5);
    L.text(s, "**TF-IDF (vector-space)** — chủ động chọn thay BM25 mặc định để bám lý thuyết môn học. UI hiển thị nhãn *TF-IDF* + điểm.", { x: p.x, y: p.y + 1.27, w: p.w, h: 0.85, size: 11 });
    p = L.panel(s, 4.75, Y0, 8.08, 5.53, "Sequence: một lượt tìm kiếm (mode = chunk | full)");
    L.img(s, D("04s_sequence_search_short.png"), p.x, p.y, p.w, 3.85);
    L.callout(s, p.x, p.y + 3.95, p.w, 1.1, [
      "**Chunk mode:** lấy `N = min(1000, 10·limit)` ứng viên → **gộp theo URL, giữ chunk điểm cao nhất** → 20 kết quả.",
      "**Snippet:** tìm cụm truy vấn (đã bỏ dấu) → cửa sổ −80/+160 ký tự; không thấy → 240 ký tự đầu.",
    ], { kind: "blue", size: 11.5, bullet: false });
  }

  // =====================================================================
  // 13. INGESTION
  // =====================================================================
  {
    const s = slide("Luồng nạp dữ liệu (ingestion pipeline)");
    let p = L.panel(s, X0, Y0, 7.7, 5.53, "Sequence: xử lý một URL");
    L.img(s, D("03s_sequence_crawl_short.png"), p.x, p.y, p.w, 4.1);
    L.callout(s, p.x, p.y + 4.18, p.w, 0.88, "Mỗi URL: **① MongoDB → ② Lucene full → ③ chunk + Lucene chunk → ④ lịch sử → ⑤ links.tsv + hàng đợi**. Chỉ index khi `content` không rỗng (ảnh không chữ chỉ lưu Mongo).", { kind: "blue", size: 11.5 });
    p = L.panel(s, 8.35, Y0, 4.48, 2.9, "Hai chế độ nạp");
    L.list(s, [
      "**Crawler website** — khám phá từ seed theo liên kết, tối đa **1.000** tài nguyên/lượt.",
      "**Nạp URL có sẵn** — đọc **N** URL đầu của `url-crawled.txt` (10 → 1.000), dựng nhanh chỉ mục mẫu, không cần BFS.",
      "Cùng dùng `ResourceProcessingService` → `ContentChunker` → `LuceneIndexService`.",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12.5, gap: 8 });
    p = L.panel(s, 8.35, 4.05, 4.48, 2.5, "Log có cấu trúc để gỡ lỗi & đo");
    L.list(s, [
      "`[HTTP]` `[RESOURCE]` `[INDEXED]`",
      "`[LINK SUMMARY]` — extracted / new / queued / external / duplicates",
      "`[CRAWLER SUMMARY]` — processed · indexed · errors · queueRemaining",
      "`[MONGODB SAVED]` `[IMAGE OCR]` `[HTTP RETRY]`",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12, gap: 7 });
  }

  // =====================================================================
  // 14. API & UI
  // =====================================================================
  {
    const s = slide("API và giao diện người dùng");
    let p = L.panel(s, X0, Y0, 6.9, 3.2, "REST API");
    L.table(s, ["Endpoint", "Chức năng"], [
      ["`GET /?q=…&mode=chunk|full`", "Trang tìm kiếm (Thymeleaf)"],
      ["`POST /api/crawl/start` · `GET /api/crawl/status`", "Điều khiển / theo dõi crawler"],
      ["`POST /api/indexing/start?limit=N` · `GET …/status`", "Nạp URL có sẵn (1–1000)"],
      ["`POST /api/index`", "Index thủ công `{title, url, content}`"],
      ["`POST /api/test-extraction/upload` · `GET …/url?url=`", "Thử trích xuất 1 file / URL bất kỳ"],
    ], p.x, p.y, p.w, [3.9, 2.78], { size: 11, rowH: 0.42 });
    p = L.panel(s, X0, 4.35, 6.9, 2.2, "Giao diện \"HUST Search\" — 3 khối");
    L.list(s, [
      "**Nạp URL có sẵn:** chọn số URL (10 → 1.000), thanh tiến độ: đã xử lý · đã lập chỉ mục · lỗi.",
      "**Crawler website:** nút chạy nền + thống kê trực tiếp + URL đang xử lý.",
      "**Tìm kiếm & so sánh phương pháp:** ô tìm kiếm, **công tắc Full ⇄ Chunk**, truy vấn gợi ý.",
      "Mỗi kết quả: tiêu đề · URL · snippet · `Chunk #k` · điểm TF-IDF.",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 11.5, numbered: true, gap: 4 });
    p = L.panel(s, 7.55, Y0, 5.28, 5.53, "Ảnh chụp giao diện");
    L.img(s, UI("ui/image.png"), p.x, p.y, p.w, p.h - 0.05, { border: true });
  }

  // =====================================================================
  // 15. DEMO
  // =====================================================================
  {
    const s = slide("Demo và kết quả thực tế");
    let p = L.panel(s, X0, Y0, 3.9, 2.9, "Crawler đang chạy");
    L.img(s, UI("ui/image(2).png"), p.x, p.y, p.w, 2.05, { border: true });
    L.text(s, "Đã xử lý **165** · đã lập chỉ mục **151** · lỗi **9** (≈ 5,5%).", { x: p.x, y: p.y + 2.12, w: p.w, h: 0.4, size: 11 });
    p = L.panel(s, X0, 4.05, 3.9, 2.5, "Truy vấn \"đại học bách khoa\" · Chunk");
    L.table(s, ["#", "Kết quả", "Chunk", "Điểm"], [
      ["1", "Đại học Bách khoa Hà Nội (`hust.edu.vn/`)", "#4", "11,32"],
      ["2", "Chào mừng kỷ niệm 70 năm thành lập", "#2", "7,39"],
      ["3", "Trung tâm Sáng tạo & Khởi nghiệp SV (`bke.`)", "#1", "7,21"],
    ], p.x, p.y, p.w, [0.3, 2.3, 0.55, 0.51], { size: 10, align: ["center", "left", "center", "center"] });
    L.text(s, "20 kết quả · phiên chạy trực tiếp (chỉ mục lớn hơn mẫu trong repo).", { x: p.x, y: p.y + 1.55, w: p.w, h: 0.4, size: 10, color: COL.grey });
    p = L.panel(s, 4.55, Y0, 4.7, 5.53, "Giao diện kết quả — chế độ Chunk-based");
    L.img(s, UI("ui/image(3).png"), p.x, p.y, p.w, p.h - 0.05, { border: true });
    p = L.panel(s, 9.4, Y0, 3.43, 5.53, "Quan sát");
    L.list(s, [
      "Trang chủ đứng đầu: cụm *Đại học Bách khoa Hà Nội* dày; **chunk #4** chứa đúng đoạn liên quan (snippet cho thấy ngữ cảnh).",
      "Kết quả đến từ **tên miền con** (`bke.hust.edu.vn`) → crawler đi đúng phạm vi.",
      "Tìm được **tin mới nhất** (sự kiện 70 năm, 10/2026).",
      "Gõ **không dấu** cho kết quả **giống hệt** (đã kiểm chứng).",
    ], { x: p.x, y: p.y, w: p.w, h: 3.3, size: 11.5, gap: 6 });
    L.callout(s, p.x, p.y + 3.4, p.w, 1.65, "Trên **chỉ mục mẫu trong repo** (159 trang): top-1 = `hust.edu.vn/` — TF-IDF **10,52** (full) · **10,77** (chunk); top-2 là trang tag \"Chào mừng kỷ niệm 70 năm…\" (7,98) ở cả hai chế độ.", { kind: "blue", size: 11 });
  }

  // =====================================================================
  // 16. PHÂN TÍCH DỮ LIỆU
  // =====================================================================
  {
    const s = slide("Phân tích tập dữ liệu thu thập được");
    let p = L.panel(s, X0, Y0, 5.0, 2.72, "Phạm vi: top 10 host trong url-crawled.txt");
    L.img(s, C("c01_host_distribution.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 5.6, Y0, 3.8, 2.72, "Loại tài nguyên trong chỉ mục");
    L.img(s, C("c04_resource_types.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, X0, 3.84, 3.6, 2.71, "Độ dài trang (số từ)");
    L.img(s, C("c03_page_length.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 4.2, 3.84, 5.2, 2.71, "Đồ thị liên kết (links.tsv)");
    L.img(s, C("c05_link_graph.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 9.55, Y0, 3.28, 5.53, "Số liệu chính");
    L.list(s, [
      "**118 host**, ~101,8k URL; 3 host đầu (`dlib`, `hust`, `clc`) chiếm **53%**.",
      "Chỉ mục mẫu: **159 trang → 564 document** Lucene.",
      "**73/159 (46%) là ảnh được OCR**; HTML 63 · PDF 18 · Word 5 → OCR mở rộng độ phủ.",
      "Độ dài: trung vị **269 từ**, TB **684**, max **9.747**; **43%** trang > 400 từ → bị chunk.",
      "Đồ thị: **21.760 cạnh**, 119 nguồn (TB ~183 link/trang) → **752 đích**; in-degree max **348**, đuôi dài → phù hợp PageRank.",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 11.5, gap: 8 });
  }

  // =====================================================================
  // 17. SO SÁNH FULL vs CHUNK
  // =====================================================================
  {
    const s = slide("So sánh Full-document và Chunk-based");
    let p = L.panel(s, X0, Y0, 6.35, 3.1, "Đối chiếu");
    L.table(s, ["Tiêu chí", "Full-document", "Chunk-based"], [
      ["Đơn vị trả về", "cả trang", "đoạn khớp nhất"],
      ["Số doc / trang", "1", "⌈n/350⌉ (TB 2,55)"],
      ["Chuẩn hóa độ dài", "theo cả trang → trang dài bị phạt", "theo ~400 từ → công bằng hơn"],
      ["Snippet", "có thể không chứa truy vấn", "luôn bám đoạn khớp nhất"],
      ["Từ truy vấn ở xa nhau", "tốt (cả trang)", "có thể trượt nếu khác chunk"],
      ["Chi phí chỉ mục", "thấp", "cao hơn (+overlap 12,5%)"],
    ], p.x, p.y, p.w, [1.55, 2.4, 2.2], { size: 10.5, boldFirst: true });
    const pm = L.panel(s, X0, 4.25, 6.35, 2.3, "Thực nghiệm: 15 truy vấn, chạy lại logic LuceneIndexService");
    p = pm;
    L.table(s, ["Chỉ số", "Kết quả"], [
      ["Document khớp", "Full **72–88** · Chunk **210–274** (trước gộp URL)"],
      ["Overlap@10 Full vs Chunk", "**0,91** — đồng thuận ở top đầu"],
      ["Overlap@10 TF-IDF vs BM25", "**0,72** (full) · **0,69** (chunk)"],
      ["Độ trễ (best/30 lần)", "Full **0,43 ms** · Chunk **0,54 ms**"],
    ], p.x, p.y, p.w, [2.4, 3.75], { size: 10.5, boldFirst: true });
    p = L.panel(s, 6.95, Y0, 5.88, 2.7, "Số tài liệu khớp theo truy vấn");
    L.img(s, C("c07_hits.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 6.95, 3.82, 5.88, 2.73, "Overlap@10 giữa các cấu hình");
    L.img(s, C("c08_overlap.png"), p.x, p.y, p.w, p.h - 0.05);
    L.text(s, "Overlap đo **mức đồng thuận**, không phải độ chính xác — chưa có nhãn nên chưa tính P/R.", { x: pm.x, y: pm.y + 1.55, w: pm.w, h: 0.4, size: 10.5, color: COL.red });
  }

  // =====================================================================
  // 18. HẠN CHẾ & HƯỚNG PHÁT TRIỂN
  // =====================================================================
  {
    const s = slide("Hạn chế và hướng phát triển");
    let p = L.panel(s, X0, Y0, 6.1, 5.53, "Hạn chế hiện tại (nói thẳng)", { head: COL.red, border: COL.red });
    const lim = [
      ["Chưa có đánh giá độ chính xác", "(P/R, MAP, nDCG) — chưa có truy vấn có nhãn; mới có phân tích *proxy*."],
      ["Xếp hạng chỉ dựa TF-IDF", "đã lưu đồ thị liên kết nhưng chưa dùng (PageRank, anchor text)."],
      ["Analyzer bỏ dấu hoàn toàn", "chưa stop-word, chưa tách từ thật (bigram thay thế)."],
      ["Crawler đơn luồng", "chưa đọc `robots.txt`, chưa re-crawl theo độ tươi."],
      ["Mỗi lần index mở/đóng IndexWriter + commit", "chậm khi nạp lớn."],
      ["Giao diện", "chưa phân trang, highlight từ khóa, autocomplete, sửa chính tả."],
      ["Kiểm thử", "chưa có test tự động ngoài smoke test."],
    ];
    lim.forEach(([a, b], i) => {
      const y = p.y + i * 0.7;
      L.badge(s, p.x, y + 0.04, i + 1, 0.3);
      L.text(s, `**${a}** — ${b}`, { x: p.x + 0.42, y, w: p.w - 0.45, h: 0.62, size: 12.5 });
    });
    p = L.panel(s, 6.75, Y0, 6.08, 4.35, "Hướng phát triển");
    const road = [
      ["Bộ đánh giá", "30–50 truy vấn có nhãn → so sánh TF-IDF vs **BM25** (đổi 1 dòng `Similarity`), full vs chunk bằng số liệu."],
      ["Liên kết", "**PageRank** + **anchor text** từ `links.tsv` (21.760 cạnh)."],
      ["Tiếng Việt", "tách từ (VnCoreNLP) hoặc giữ thêm trường **có dấu**."],
      ["Hiệu năng", "`IndexWriter` dùng chung + batch commit; crawl đa luồng theo host."],
      ["Ngữ nghĩa", "**Embedding** trên chunk + tìm kiếm lai (hybrid) với Lucene."],
    ];
    road.forEach(([a, b], i) => {
      const y = p.y + i * 0.75;
      L.badge(s, p.x, y + 0.04, i + 1, 0.3, COL.navy);
      L.text(s, `**${a}:** ${b}`, { x: p.x + 0.42, y, w: p.w - 0.45, h: 0.7, size: 12.5 });
    });
    L.callout(s, 6.75, 5.52, 6.08, 1.03, "**Ưu tiên cao nhất:** xây bộ đánh giá có nhãn — để mọi so sánh (TF-IDF/BM25, full/chunk) có kết luận bằng số liệu.", { size: 12.5 });
  }

  // =====================================================================
  // 19. TỔNG KẾT
  // =====================================================================
  {
    const s = slide("Tổng kết và đóng góp");
    const cw = 2.3, cg = 0.2075;
    [["4", "loại tài nguyên:\nHTML · PDF · Word · ảnh OCR"], ["2", "chiến lược chỉ mục\nđối chiếu trực tiếp"], ["564", "document Lucene\n(159 full + 405 chunk)"], ["21.760", "cạnh liên kết\nđã thu thập"], ["0,91", "overlap@10\nFull vs Chunk"]]
      .forEach(([a, b], i) => L.stat(s, X0 + i * (cw + cg), Y0, cw, 1.05, a, b, { nsize: 26, lsize: 10 }));
    let p = L.panel(s, X0, 2.25, 4.9, 4.3, "Đã hoàn thành");
    L.list(s, [
      "Hệ thống **end-to-end**: crawl → trích xuất → lưu trữ → lập chỉ mục → tìm kiếm → giao diện web.",
      "Xử lý **4 loại tài nguyên** (kể cả ảnh bằng OCR) trên `hust.edu.vn` và tên miền con.",
      "**Analyzer tiếng Việt** tự cài đặt: chuẩn hóa, bỏ dấu, bigram — đã kiểm chứng.",
      "**2 chiến lược lập chỉ mục** (full / chunk) so sánh trực tiếp trên UI và bằng số liệu.",
      "Crawler **lịch sự, bền bỉ**; dừng/chạy tiếp được.",
      "**9 sơ đồ UML** (PlantUML) + bộ phân tích tái lập được.",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 13, gap: 10 });
    p = L.panel(s, 5.55, 2.25, 4.0, 4.3, "Bài học rút ra");
    [["Tiền xử lý quyết định chất lượng", "trích xuất nội dung sạch, chuẩn hóa tiếng Việt ảnh hưởng trực tiếp đến kết quả."], ["Đơn vị lập chỉ mục quan trọng", "full vs chunk thay đổi xếp hạng, snippet, chi phí."], ["Cần đánh giá định lượng", "overlap chưa phải độ chính xác — kết luận chắc cần nhãn."]]
      .forEach(([a, b], i) => {
        const y = p.y + i * 1.3;
        L.badge(s, p.x, y + 0.03, i + 1, 0.3, COL.navy);
        L.text(s, `**${a}**`, { x: p.x + 0.42, y, w: p.w - 0.45, h: 0.3, size: 13.5 });
        L.text(s, b, { x: p.x + 0.42, y: y + 0.32, w: p.w - 0.45, h: 0.85, size: 12.5 });
      });
    p = L.panel(s, 9.7, 2.25, 3.13, 4.3, "Phân công");
    L.table(s, ["Thành viên", "Phần việc"], [
      ["[Tên 1]", "[…]"], ["[Tên 2]", "[…]"], ["[Tên 3]", "[…]"], ["[Tên 4]", "[…]"],
    ], p.x, p.y, p.w, [1.3, 1.59], { size: 11, rowH: 0.45 });
    L.text(s, "Điền trước khi báo cáo.", { x: p.x, y: p.y + 2.5, w: p.w, h: 0.3, size: 10.5, color: COL.grey });
  }

  // =====================================================================
  // 20. Q&A
  // =====================================================================
  {
    const s = pres.addSlide({ masterName: "COVER" });
    ctx.setN(ctx.getN() + 1);
    s.addImage({ path: A("logo.png"), x: 0.5, y: 0.3, w: 3.2, h: 0.96, altText: "Logo ĐH Bách khoa Hà Nội" });
    s.addText("Cảm ơn thầy và các bạn\nđã lắng nghe!", { x: 0.5, y: 1.9, w: 7.2, h: 1.5, fontFace: FONT, fontSize: 40, bold: true, color: COL.red, margin: 0, isTextBox: true, valign: "top", objectName: "Title" });
    s.addText("Hỏi & Đáp", { x: 0.5, y: 3.5, w: 6, h: 0.6, fontFace: FONT, fontSize: 28, bold: true, color: COL.navy, margin: 0, isTextBox: true });
    s.addShape(S.rect, { x: 0.5, y: 4.4, w: 6.8, h: 1.2, fill: { color: COL.code }, line: { color: COL.code } });
    s.addText([
      { text: "mvn clean compile && mvn spring-boot:run\n", options: { color: "9CDCFE", fontFace: L.MONO, fontSize: 12, breakLine: false } },
      { text: "→ http://localhost:8080   (JDK 21 · MongoDB · tessdata/vie+eng)", options: { color: "E6EDF7", fontFace: L.MONO, fontSize: 11 } },
    ], { x: 0.65, y: 4.4, w: 6.5, h: 1.2, margin: 0, valign: "middle", isTextBox: true });
    s.addText("Mã nguồn: [link repo]  ·  Phụ lục A1–A11: UML, mã, cấu hình, FAQ", { x: 0.5, y: 5.75, w: 7, h: 0.3, fontFace: FONT, fontSize: 12, color: COL.dark, margin: 0, isTextBox: true });
    s.addNotes(NOTES[20]);
  }

  // =====================================================================
  // APPENDIX
  // =====================================================================
  {
    const s = pres.addSlide({ masterName: "COVER" });
    ctx.setN(ctx.getN() + 1);
    s.addImage({ path: A("logo.png"), x: 0.5, y: 0.3, w: 3.2, h: 0.96, altText: "Logo" });
    s.addText("Phụ lục", { x: 0.5, y: 2.3, w: 6, h: 1.0, fontFace: FONT, fontSize: 48, bold: true, color: COL.red, margin: 0, isTextBox: true, objectName: "Title" });
    s.addText(L.paras([
      "A1 Kiến trúc (UML component)", "A2 Sơ đồ lớp", "A3 Mô hình dữ liệu", "A4 Use case", "A5 Sequence: crawl & tìm kiếm", "A6 Activity: crawler · trích xuất · analyzer",
      "A7 Trích đoạn mã", "A8 Cấu hình hệ thống", "A9 Từ vựng, Zipf, đường cong điểm", "A10 Thách thức kỹ thuật", "A11 FAQ — câu hỏi dự kiến",
    ], { size: 12.5, bullet: false, gap: 2, color: COL.dark }), { x: 0.5, y: 3.4, w: 6.5, h: 3.3, margin: 0, isTextBox: true, valign: "top" });
    s.addNotes("[Phụ lục]\n\nDự phòng khi thầy hỏi sâu: sơ đồ UML (PlantUML, thư mục docs/slide/diagrams), trích đoạn mã, cấu hình, FAQ.");
  }

  { // A1
    const s = apx("Phụ lục A1 — Kiến trúc (UML component)", "Sơ đồ thành phần đầy đủ: controller, crawler, indexing/search, storage và các hệ ngoài (Tika, Tesseract, MongoDB, Lucene index). File gốc: diagrams/01_architecture.puml.");
    const p = L.panel(s, X0, Y0, W, 5.53, "01_architecture.puml");
    L.img(s, D("01_architecture.png"), p.x, p.y, p.w, p.h - 0.05);
  }
  { // A2
    const s = apx("Phụ lục A2 — Sơ đồ lớp", "Các lớp chính trong 5 package. record = Java record. WebCrawlerService phụ thuộc ResourceProcessingService, ContentChunker, LuceneIndexService và PageRepository. File: diagrams/02_class_diagram.puml.");
    const p = L.panel(s, X0, Y0, W, 5.53, "02_class_diagram.puml");
    L.img(s, D("02_class_diagram.png"), p.x, p.y, p.w, p.h - 0.05);
  }
  { // A3
    const s = apx("Phụ lục A3 — Mô hình dữ liệu", "Quan hệ giữa bản ghi MongoDB, hai loại document Lucene (full/chunk) và các tệp phẳng. Khóa chung là URL. File: diagrams/09_data_model.puml.");
    const p = L.panel(s, X0, Y0, W, 4.0, "09_data_model.puml");
    L.img(s, D("09_data_model.png"), p.x, p.y, p.w, p.h - 0.05);
    L.callout(s, X0, 5.2, W, 1.35, [
      "**Full:** khóa `url`; cập nhật bằng `updateDocument(Term(\"url\"))`.  **Chunk:** `chunkId = url#chunk-i`; xóa bằng `deleteDocuments(Term(\"chunkUrl\"))` trước khi thêm.",
      "**PageDocument** `_id = url` → lưu lại là upsert.  `links[]` khử trùng theo URL đích; `links.tsv` giữ toàn bộ cạnh (kể cả link ngoài miền).",
    ], { kind: "blue", size: 12, bullet: false });
  }
  { // A4
    const s = apx("Phụ lục A4 — Use case", "Hai tác nhân: người dùng tìm kiếm và quản trị/nhóm phát triển. Cả hai luồng nạp dữ liệu đều include việc theo dõi tiến độ.");
    let p = L.panel(s, X0, Y0, 5.0, 5.53, "08_usecase.puml");
    L.img(s, D("08_usecase.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 5.65, Y0, 7.18, 5.53, "Mô tả");
    L.table(s, ["Tác nhân", "Use case", "Điểm vào"], [
      ["Người dùng", "Tìm kiếm từ khóa tiếng Việt", "`GET /?q=`"],
      ["Người dùng", "Chọn chế độ Full / Chunk; xem snippet, điểm TF-IDF", "`mode=full|chunk`"],
      ["Quản trị", "Chạy crawler từ seed `hust.edu.vn`", "`POST /api/crawl/start`"],
      ["Quản trị", "Nạp N URL có sẵn vào Lucene", "`POST /api/indexing/start?limit=N`"],
      ["Quản trị", "Index thủ công 1 tài liệu", "`POST /api/index`"],
      ["Quản trị", "Thử trích xuất file / URL", "`/api/test-extraction/*`"],
      ["Quản trị", "Theo dõi tiến độ (include)", "`GET …/status`"],
    ], p.x, p.y, p.w, [1.1, 3.5, 2.34], { size: 11, rowH: 0.5 });
  }
  { // A5
    const s = apx("Phụ lục A5 — Sequence: crawl và tìm kiếm", "Bản đầy đủ của hai sequence diagram: xử lý một URL trong crawler (19 bước, có retry/backoff) và một lượt tìm kiếm (mode full/chunk).");
    let p = L.panel(s, X0, Y0, 5.6, 5.53, "03_sequence_crawl.puml");
    L.img(s, D("03_sequence_crawl.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 6.25, Y0, 6.58, 5.53, "04_sequence_search.puml");
    L.img(s, D("04_sequence_search.png"), p.x, p.y, p.w, p.h - 0.05);
  }
  { // A6
    const s = apx("Phụ lục A6 — Activity: crawler · trích xuất HTML · analyzer", "Ba activity diagram: vòng lặp BFS của crawler, quy trình trích xuất nội dung chính từ HTML (hai chiến lược chấm điểm), và pipeline của VietnameseTextAnalyzer.");
    let p = L.panel(s, X0, Y0, 3.9, 5.53, "05 — Vòng lặp crawler");
    L.img(s, D("05_activity_crawler.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 4.55, Y0, 5.2, 5.53, "06 — Trích xuất HTML");
    L.img(s, D("06_activity_extraction.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 9.9, Y0, 2.93, 5.53, "07 — Analyzer");
    L.img(s, D("07_activity_analyzer.png"), p.x, p.y, p.w, p.h - 0.05);
  }
  { // A7
    const s = apx("Phụ lục A7 — Trích đoạn mã", "Trái: analyzer — chuẩn hóa, bỏ dấu, unigram + bigram. Phải: truy vấn Lucene — MultiFieldQueryParser với boost title 2.0, toán tử OR, FILTER theo documentType, ClassicSimilarity.");
    let p = L.panel(s, X0, Y0, 6.05, 5.53, "VietnameseTextAnalyzer");
    codeBox(s, p.x, p.y, p.w, 3.35, [
      { t: "private static String normalize(String text) {" },
      { t: "  String lower = text.toLowerCase(Locale.ROOT)" },
      { t: "                     .replace('đ', 'd');" },
      { t: "  return Normalizer.normalize(lower, NFD)" },
      { t: "         .replaceAll(\"\\\\p{M}+\", \"\");  // bỏ dấu", c: "6A9955" },
      { t: "}" },
      { t: "// WORD_PATTERN = [\\p{L}\\p{N}]+", c: "6A9955" },
      { t: "for (int i = 0; i < words.size(); i++) {" },
      { t: "  expanded.add(words.get(i));        // unigram", c: "6A9955" },
      { t: "  if (i + 1 < words.size())" },
      { t: "    expanded.add(new Token(a.text() + \"_\" + b.text()," },
      { t: "                 a.start(), b.end())); // bigram", c: "6A9955" },
      { t: "}" },
    ], 12);
    L.callout(s, p.x, p.y + 3.45, p.w, 1.65, "`Tuyển sinh đại học` →\n`tuyen, tuyen_sinh, sinh, sinh_dai, dai, dai_hoc, hoc`\n\nCùng analyzer cho **index** và **query** → token luôn khớp.", { kind: "blue", size: 11.5 });
    p = L.panel(s, 6.78, Y0, 6.05, 5.53, "LuceneIndexService.searchByType()");
    codeBox(s, p.x, p.y, p.w, 3.6, [
      { t: "MultiFieldQueryParser parser =" },
      { t: "  new MultiFieldQueryParser(" },
      { t: "    new String[]{\"title\",\"content\"}, analyzer," },
      { t: "    Map.of(\"title\", 2.0f, \"content\", 1.0f));" },
      { t: "parser.setDefaultOperator(Operator.OR);" },
      { t: "Query text = parser.parse(escape(queryText));" },
      { t: "Query type = new TermQuery(" },
      { t: "  new Term(\"documentType\", documentType));" },
      { t: "Query q = new BooleanQuery.Builder()" },
      { t: "  .add(text, Occur.MUST)" },
      { t: "  .add(type, Occur.FILTER) // lọc, không tính điểm", c: "6A9955" },
      { t: "  .build();" },
      { t: "searcher.setSimilarity(new ClassicSimilarity());" },
    ], 11.5);
    L.list(s, ["Chunk mode: `candidateLimit = min(1000, limit·10)` rồi gộp theo `chunkUrl`.", "Full mode: `limit` ứng viên, không gộp."], { x: p.x, y: p.y + 3.75, w: p.w, h: 1.3, size: 12, gap: 6 });
  }
  { // A8
    const s = apx("Phụ lục A8 — Cấu hình hệ thống (application.yml)", "Các tham số cấu hình chính của crawler và lưu trữ. Backoff: lần thử 1 chờ 5 giây, lần 2 chờ 10 giây, lần 3 chờ 20 giây.");
    const p = L.panel(s, X0, Y0, W, 4.25, "Cấu hình");
    L.table(s, ["Khóa", "Giá trị", "Ý nghĩa"], [
      ["`ir.crawler.allowed-domain`", "`hust.edu.vn`", "Miền (và subdomain) được phép"],
      ["`ir.crawler.max-pages-per-run`", "1000", "Số tài nguyên xử lý tối đa mỗi lượt"],
      ["`ir.crawler.request-delay-ms`", "3000", "Khoảng cách giữa 2 request"],
      ["`ir.crawler.max-retries`", "3", "Số lần thử lại với 429 / 5xx"],
      ["`ir.crawler.retry-backoff-ms`", "5000", "Gốc của exponential backoff"],
      ["`ir.crawler.content-extraction-strategy`", "`READABILITY`", "hoặc `DOM_HEURISTIC`"],
      ["`ir.crawler.legacy-url-file`", "`url-crawled.txt`", "Lịch sử / nguồn nạp"],
      ["`ir.storage.index-dir` · `data-dir`", "`index` · `data`", "Thư mục chỉ mục / dữ liệu"],
      ["`spring.data.mongodb.uri`", "`${MONGODB_URI:mongodb://localhost:27017/information_retrieval}`", "Kết nối DB"],
    ], p.x, p.y, p.w, [3.8, 4.7, 3.57], { size: 10.5, rowH: 0.36 });
    L.callout(s, X0, 5.4, W, 1.15, [
      "**Chạy:** `mvn clean compile` → `mvn spring-boot:run` (JDK 21).  Cổng **8080**.  Thymeleaf `cache: false`.",
      "**Chunker:** `CHUNK_SIZE = 400`, `CHUNK_OVERLAP = 50` (hằng số trong `ContentChunker`).  **Executor:** 1 luồng, queue 1.",
    ], { kind: "blue", size: 12, bullet: false });
  }
  { // A9
    const s = apx("Phụ lục A9 — Từ vựng, luật Zipf và đường cong điểm", "Bigram chiếm 83% từ vựng. Cả unigram và bigram đều theo luật Zipf. Điểm TF-IDF top-20 giảm nhanh sau hạng 1-2; full và chunk gần như chồng nhau ở các hạng sau. Document frequency của term luôn lớn hơn hoặc bằng 2 vì mỗi trang có cả document full lẫn chunk.");
    let p = L.panel(s, X0, Y0, 6.1, 3.0, "Từ vựng & Zipf");
    L.img(s, C("c06_vocabulary.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 6.75, Y0, 6.08, 3.0, "Độ trễ truy vấn");
    L.img(s, C("c09_latency.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, X0, 4.15, 7.4, 2.4, "Điểm TF-IDF top-20 theo hạng");
    L.img(s, C("c10_score_curves.png"), p.x, p.y, p.w, p.h - 0.05);
    p = L.panel(s, 8.05, 4.15, 4.78, 2.4, "Nhận xét");
    L.list(s, [
      "**Bigram = 83%** từ vựng (67.032/80.311).",
      "Cả hai nhóm term theo **Zipf** → IDF phân biệt tốt.",
      "Điểm giảm nhanh sau hạng 1–2; full ≈ chunk ở các hạng sau.",
      "df ≥ 2 vì mỗi trang có full + chunk.",
    ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 11, gap: 4 });
  }
  { // A10
    const s = apx("Phụ lục A10 — Thách thức kỹ thuật và cách giải quyết", "Tóm tắt các vấn đề gặp phải và giải pháp trong mã nguồn: chuẩn hóa URL, kiểm tra URL cuối sau redirect, backoff, chấm điểm khối DOM, bỏ dấu + bigram, update/delete trước khi thêm, chạy nền.");
    const p = L.panel(s, X0, Y0, W, 5.53, "Thách thức → giải pháp");
    L.table(s, ["Thách thức", "Giải pháp trong code"], [
      ["Trùng URL do tham số tracking, `#`, cổng mặc định", "`normalizeUrl()` chuẩn hóa trước mọi so sánh"],
      ["Redirect ra ngoài miền", "Kiểm tra **URL cuối** `isAllowedDomain(finalUrl)`"],
      ["URL yêu cầu khác URL cuối đã crawl", "Ghi cả hai vào lịch sử, bỏ qua xử lý lại"],
      ["Máy chủ chặn / quá tải (429, 5xx)", "Retry + exponential backoff + `Retry-After`"],
      ["Trang nhiều nhiễu (menu, footer)", "Loại thẻ nhiễu + chấm điểm link density / class-id"],
      ["PDF / Word / ảnh", "Tika + Tesseract OCR"],
      ["Tiếng Việt: dấu và từ ghép", "Bỏ dấu + unigram/bigram tự viết"],
      ["Trang dài làm lệch điểm", "Chunk 400 từ, overlap 50"],
      ["Kết quả trùng URL ở chunk mode", "Gộp theo URL, giữ chunk tốt nhất"],
      ["Index lại bị nhân bản", "`updateDocument` / `deleteDocuments` trước khi thêm"],
      ["Crawl lâu, UI không bị treo", "Chạy nền `ThreadPoolTaskExecutor`, poll `/status`"],
      ["Ký tự đặc biệt trong truy vấn", "`MultiFieldQueryParser.escape()`"],
    ], p.x, p.y, p.w, [5.6, 6.47], { size: 10.5, rowH: 0.36 });
  }
  { // A11
    const s = apx("Phụ lục A11 — FAQ: câu hỏi dự kiến của thầy", "Trả lời nhanh: (1) chọn TF-IDF để bám môn học, đổi BM25 chỉ một dòng; (2) chưa có đánh giá định lượng độ chính xác, đây là bước tiếp theo; (3) 101 nghìn là danh sách URL, không phải số trang đã lập chỉ mục.");
    const p = L.panel(s, X0, Y0, W, 5.53, "Câu hỏi → trả lời ngắn");
    L.table(s, ["Câu hỏi", "Trả lời"], [
      ["Sao không dùng BM25?", "Lucene 9 mặc định BM25; nhóm **chủ động đặt ClassicSimilarity** để bám TF-IDF của môn học. Đổi sang BM25 chỉ 1 dòng `setSimilarity`; top-10 chỉ trùng ~70% nên đáng đánh giá."],
      ["Bỏ dấu có sai nghĩa không?", "Có thể với từ đồng âm khác dấu; đổi lại chịu lỗi gõ và gõ không dấu. Có thể thêm trường phụ giữ dấu."],
      ["Sao bigram mà không tách từ?", "Tránh phụ thuộc thư viện ngoài, vẫn bắt cụm 2 âm tiết. Cụm ≥ 3 âm tiết khớp qua các bigram liền kề."],
      ["Có tôn trọng `robots.txt` không?", "Chưa cài đặt; bù bằng delay 3 s, đơn luồng, UA định danh, giới hạn miền, mục đích học tập."],
      ["Đánh giá chất lượng ra sao?", "Đo proxy trên 15 truy vấn (overlap@10, độ trễ). Chưa có P/R/nDCG vì chưa có nhãn."],
      ["Vì sao chunk 400 từ, overlap 50?", "Tham số kinh nghiệm: đủ dài giữ ngữ cảnh, đủ ngắn để norm không phạt; overlap 12,5%. Chưa tinh chỉnh bằng thực nghiệm."],
      ["Mở `IndexWriter` mỗi lần có chậm?", "Có; chấp nhận được vì crawler bị giới hạn 3 s/request (I/O-bound). Tối ưu: writer dùng chung + batch commit."],
      ["Vì sao lưu cả MongoDB lẫn Lucene?", "Lucene là cấu trúc tìm kiếm; Mongo giữ bản gốc + đồ thị link để re-index / phân tích."],
      ["Đồ thị liên kết dùng làm gì?", "Hiện mới lưu (21.760 cạnh); dự định PageRank và anchor text."],
      ["OCR có chính xác không?", "Tốt với chữ in rõ (logo, thông báo); phụ thuộc chất lượng ảnh; trạng thái SUCCESS / NO_TEXT / FAILED ghi trong metadata."],
    ], p.x, p.y, p.w, [3.2, 8.87], { size: 12, boldFirst: true, rowH: 0.46 });
  }
};
