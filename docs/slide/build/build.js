// Build HUST Search deck: node build.js  (NODE_PATH must contain pptxgenjs)
const pptxgen = require("pptxgenjs");
const fs = require("fs");
const path = require("path");
const ROOT = path.resolve(__dirname, "..");
const A = (p) => path.join(__dirname, "assets", p);
const D = (p) => path.join(ROOT, "diagrams", p);
const C = (p) => path.join(ROOT, "charts", p);
const UI = (p) => path.resolve(ROOT, "..", "images", p);

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.title = "HUST Search — Báo cáo bài tập lớn Tìm kiếm thông tin";
pres.author = "Nhóm HUST Search";
pres.theme = { headFontFace: "Arial", bodyFontFace: "Arial" };

const L = require("./lib")(pres);
const { COL, FONT } = L;

// ---------- speaker notes from slides.md (PHẦN C) ----------
const md = fs.readFileSync(path.join(ROOT, "slides.md"), "utf8");
const partC = md.slice(md.indexOf("# PHẦN C"));
const NOTES = {};
partC.split(/\n(?=### Slide )/).forEach((blk) => {
  const m = blk.match(/^### Slide (\d+) — ([^\n]*)\n([\s\S]*)$/);
  if (!m) return;
  let body = m[3].split(/\n---/)[0].trim();
  body = body.replace(/\*\*/g, "").replace(/`/g, "").replace(/^\*\((.*)\)\*$/gm, "($1)");
  const head = m[2].replace(/\*/g, "").replace(/`/g, "");
  NOTES[m[1]] = `[${head.trim()}]\n\n${body}`;
});

// ---------- masters ----------
pres.defineSlideMaster({
  title: "CONTENT", background: { color: "FFFFFF" },
  objects: [{ image: { x: 0, y: 0, w: 13.333, h: 7.5, path: A("bg_content.png"), altText: "HUST background" } }],
  slideNumber: { x: 12.1, y: 7.1, w: 0.8, h: 0.25, fontFace: FONT, fontSize: 10, color: COL.grey, align: "right" },
});
pres.defineSlideMaster({
  title: "COVER", background: { color: "FFFFFF" },
  objects: [{ image: { x: 0, y: 0, w: 13.333, h: 7.5, path: A("bg_cover.png"), altText: "HUST cover background" } }],
});

let n = 0;
function slide(title, notesKey) {
  n += 1;
  const s = pres.addSlide({ masterName: "CONTENT" });
  s.addText(title, { x: 0.5, y: 0.07, w: 12.3, h: 0.58, fontFace: FONT, fontSize: 24, bold: true, color: COL.white, margin: 0, valign: "middle", isTextBox: true, objectName: "Title" });
  const note = NOTES[notesKey === undefined ? n : notesKey];
  if (note) s.addNotes(note);
  return s;
}
const T = (x) => x; // placeholder for readability
const X0 = 0.5, W = 12.33, Y0 = 1.02;

// =====================================================================
// 1. COVER
// =====================================================================
{
  n += 1;
  const s = pres.addSlide({ masterName: "COVER" });
  s.addImage({ path: A("logo.png"), x: 0.5, y: 0.3, w: 3.2, h: 0.96, altText: "Logo ĐH Bách khoa Hà Nội" });
  s.addText("HUST Search", { x: 0.5, y: 2.0, w: 7.2, h: 0.9, fontFace: FONT, fontSize: 48, bold: true, color: COL.red, margin: 0, isTextBox: true, objectName: "Title" });
  s.addText("Thu thập, lập chỉ mục và tìm kiếm thông tin trên website hust.edu.vn", { x: 0.5, y: 2.95, w: 6.6, h: 0.95, fontFace: FONT, fontSize: 22, bold: true, color: COL.navy, margin: 0, isTextBox: true, valign: "top" });
  s.addText("Báo cáo bài tập lớn · Học phần Tìm kiếm thông tin", { x: 0.5, y: 4.05, w: 6.6, h: 0.3, fontFace: FONT, fontSize: 14, color: COL.dark, margin: 0, isTextBox: true });
  s.addText(L.paras(["Giảng viên: **[Tên thầy]**", "Nhóm **[số nhóm]** — **[Họ tên – MSSV các thành viên]**"], { size: 14, color: COL.red, bullet: false, gap: 3 }),
    { x: 0.5, y: 4.5, w: 6.6, h: 0.75, margin: 0, isTextBox: true, valign: "top" });
  const chips = ["Java 21", "Spring Boot 3.5", "Lucene 9.12", "MongoDB", "Jsoup", "Tika 3.2", "Tesseract OCR"];
  let cx = 0.5;
  chips.forEach((c) => {
    const w = 0.22 + c.length * 0.095;
    L.node(s, cx, 5.55, w, 0.32, c, { size: 10.5, fill: COL.white, line: COL.navy, bold: true });
    cx += w + 0.1;
  });
  s.addNotes(NOTES[1]);
}

// =====================================================================
// 2. BÀI TOÁN & MỤC TIÊU
// =====================================================================
{
  const s = slide("Bài toán và mục tiêu");
  const sw = 2.95, sg = 0.177;
  [["118", "host *.hust.edu.vn trong danh sách URL"], ["~101,8k", "URL trong url-crawled.txt"], ["4", "loại tài nguyên: HTML · PDF · Word · ảnh"], ["159", "trang trong chỉ mục mẫu (405 chunk)"]]
    .forEach(([a, b], i) => L.stat(s, X0 + i * (sw + sg), Y0, sw, 0.95, a, b, { nsize: 26 }));
  let p = L.panel(s, X0, 2.1, 4.0, 4.45, "Bài toán");
  L.list(s, [
    "`hust.edu.vn` và các **tên miền con** có lượng nội dung lớn, phân tán: tin tức, thông báo, văn bản, thư viện số…",
    "Nội dung **không chỉ là HTML**: còn PDF, Word và **ảnh chứa chữ** (logo, thông báo scan).",
    "Người dùng tìm bằng **từ khóa tiếng Việt**, có thể **gõ không dấu**.",
    "Cần hệ thống **đơn giản, chạy đơn máy**, đủ để thực hành toàn bộ chuỗi IR: thu thập → xử lý → chỉ mục → xếp hạng → giao diện.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 14.5, gap: 14 });
  p = L.panel(s, 4.67, 2.1, 4.9, 4.45, "Bốn mục tiêu của nhóm");
  const goals = [
    ["Crawler có kiểm soát", "phạm vi `*.hust.edu.vn`, lịch sự (3 s/request), retry + backoff, dừng/chạy tiếp được."],
    ["Trích xuất nội dung sạch", "HTML (chấm điểm DOM), PDF/Word (Tika), ảnh (Tesseract `vie+eng`)."],
    ["Chỉ mục ngược bằng Lucene", "analyzer tiếng Việt **tự viết**: bỏ dấu + unigram + bigram."],
    ["So sánh 2 chiến lược", "**Full-document** vs **Chunk-based** trên cùng giao diện."],
  ];
  goals.forEach(([h, d], i) => {
    const y = p.y + i * 1.0;
    L.badge(s, p.x, y + 0.05, i + 1, 0.34);
    L.text(s, `**${h}**`, { x: p.x + 0.48, y, w: p.w - 0.5, h: 0.28, size: 13.5 });
    L.text(s, d, { x: p.x + 0.48, y: y + 0.3, w: p.w - 0.5, h: 0.7, size: 12.5, color: COL.dark });
  });
  p = L.panel(s, 9.74, 2.1, 3.09, 4.45, "Phạm vi & đóng góp");
  L.list(s, [
    "Hệ thống **end-to-end** trong **một ứng dụng Spring Boot** (cổng 8080).",
    "**~3.500 dòng** mã, 18 file Java + 1 template.",
    "Giao diện web **HUST Search** + REST API.",
    "Lưu **bản gốc** (MongoDB) tách khỏi **chỉ mục** (Lucene) để index lại bất cứ lúc nào.",
    "Mục đích **học tập/nghiên cứu**.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 13.5, gap: 11 });
}

// =====================================================================
// 3. KIẾN TRÚC
// =====================================================================
{
  const s = slide("Kiến trúc tổng quan");
  // external source
  L.node(s, X0, 1.2, 1.55, 1.5, "**hust.edu.vn**\n+ subdomain\n(nguồn dữ liệu)", { fill: COL.light, size: 11 });
  L.node(s, 11.28, 1.2, 1.55, 1.5, "**Người dùng**\ntrình duyệt\nGET /?q=&mode=", { fill: COL.light, size: 11 });
  const lx = [2.3, 5.25, 8.2], lw = 2.8;
  const layers = [
    ["① Thu thập", ["**WebCrawlerService**\nBFS · 3 s/request · retry", "**CrawlController**\nPOST /api/crawl/start", "**UrlFileIndexingService**\nnạp N URL có sẵn"]],
    ["② Xử lý & lưu trữ", ["**ResourceProcessingService**\nphân loại MIME / đuôi file", "**ContentExtractor** (Jsoup)\nchấm điểm khối DOM", "**Tika** · **OcrService**\nPDF/Word · ảnh (vie+eng)"]],
    ["③ Chỉ mục & tìm kiếm", ["**ContentChunker**\n400 từ · overlap 50", "**LuceneIndexService**\nfull + chunk · ClassicSimilarity", "**VietnameseTextAnalyzer**\n**SearchService** · **HomeController**"]],
  ];
  layers.forEach(([t, items], i) => {
    const p = L.panel(s, lx[i], 1.0, lw, 3.45, t);
    items.forEach((it, k) => L.node(s, p.x, p.y + k * 1.0, p.w, 0.9, it, { size: 10.5, fill: COL.blueL }));
  });
  L.arrow(s, 2.05, 1.95, 2.3, 1.95); L.arrow(s, 5.1, 2.7, 5.25, 2.7); L.arrow(s, 8.05, 2.7, 8.2, 2.7); L.arrow(s, 11.0, 1.95, 11.28, 1.95);
  // stores
  const st = [["MongoDB — collection `pages`", "bản gốc: title, content, links, metadata", 2.3, COL.greenL, COL.green], ["data/links.tsv · url-discovered · url-crawled", "đồ thị liên kết, frontier, lịch sử", 5.25, COL.light, COL.grey], ["Lucene index (FSDirectory ./index)", "2 loại doc: `full` và `chunk`", 8.2, COL.greenL, COL.green]];
  st.forEach(([a, b, x, f, l]) => {
    L.node(s, x, 4.75, 2.8, 0.85, `**${a}**\n${b}`, { size: 10, fill: f, line: l });
  });
  L.arrow(s, 3.7, 4.45, 3.7, 4.75); L.arrow(s, 6.65, 4.45, 6.65, 4.75); L.arrow(s, 9.6, 4.45, 9.6, 4.75);
  L.callout(s, X0, 5.85, W, 0.72, "**Điểm nhấn:** 3 lớp tách bạch — *Thu thập → Xử lý/Lưu trữ → Lập chỉ mục/Tìm kiếm*; một ứng dụng Spring Boot. Bản gốc (Mongo) **tách khỏi** chỉ mục (Lucene) nên index lại không cần crawl lại. Sơ đồ UML đầy đủ: Phụ lục A1–A2.", { kind: "blue", size: 12 });
  L.text(s, "Crawl/UrlFileIndexing → Processing → Mongo + Lucene;  Người dùng → HomeController → SearchService → Lucene", { x: 2.3, y: 5.62, w: 8.7, h: 0.2, size: 9.5, color: COL.grey, align: "center" });
}

// =====================================================================
// 4. CÔNG NGHỆ & MÃ NGUỒN
// =====================================================================
{
  const s = slide("Công nghệ và cấu trúc mã nguồn");
  let p = L.panel(s, X0, Y0, 7.3, 3.7, "Ngăn xếp công nghệ");
  L.table(s, ["Thành phần", "Công nghệ", "Vai trò"], [
    ["Web / MVC", "Spring Boot 3.5.6 · Thymeleaf", "REST API + giao diện"],
    ["Crawl HTTP, parse HTML", "Jsoup 1.21.2", "Tải trang, trích link, duyệt DOM"],
    ["PDF / Word / metadata", "Apache Tika 3.2.2", "Trích văn bản + metadata"],
    ["OCR ảnh", "Tess4J 5.16 + Tesseract (`vie`, `eng`)", "Đọc chữ trong ảnh"],
    ["Lưu trữ gốc", "MongoDB (Spring Data)", "Collection `pages`, _id = URL"],
    ["Chỉ mục & truy vấn", "Apache Lucene 9.12.2", "Inverted index, TF-IDF"],
  ], p.x, p.y, p.w, [1.95, 2.55, 2.55], { size: 12.5, boldFirst: true, rowH: 0.46 });
  p = L.panel(s, 7.97, Y0, 4.86, 3.7, "Cấu trúc package `vn.hust.ir`");
  L.list(s, [
    "`crawler/` — WebCrawlerService (723 dòng), ContentExtractor, ResourceProcessingService, OcrService, CrawlExecutorConfig",
    "`indexing/` — LuceneIndexService, ContentChunker, UrlFileIndexingService",
    "`text/` — VietnameseTextAnalyzer",
    "`search/` — SearchService",
    "`storage/` — PageDocument, PageRepository",
    "`web/` — 5 controller: Home, Crawl, Indexing, UrlIndexing, ExtractionTest",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12.5, gap: 8 });
  const cw = 2.95, cg = 0.177;
  [["3.482", "dòng (Java + template)"], ["18 + 1", "file Java + 1 template HTML"], ["6", "package, mỗi cái một trách nhiệm"], ["5", "controller / 9 endpoint"]]
    .forEach(([a, b], i) => L.stat(s, X0 + i * (cw + cg), 4.9, cw, 0.9, a, b, { nsize: 24 }));
  L.callout(s, X0, 5.95, W, 0.6, "**Thiết kế:** mỗi lớp một việc (crawl · trích xuất · chunk · lập chỉ mục · tìm kiếm) → thay thế từng phần dễ — ví dụ đổi `Similarity` sang BM25 chỉ một dòng trong `LuceneIndexService`.", { size: 12 });
}

// =====================================================================
// 5. CRAWLER (1): BFS
// =====================================================================
{
  const s = slide("Crawler (1): duyệt BFS có kiểm soát");
  let p = L.panel(s, X0, Y0, W, 2.05, "Vòng lặp crawl — WebCrawlerService.crawl()");
  const steps = [["Hàng đợi", "`ArrayDeque`\nremoveFirst / addLast"], ["Lọc URL", "trùng lượt · đã crawl · ngoài domain"], ["GET", "throttle 3 s · retry 429/5xx"], ["Xử lý", "HTML · Tika · OCR"], ["Lưu + Index", "MongoDB · Lucene full + chunk"], ["Link mới", "cùng miền → discovered + queue"]];
  const bw = 1.8, bg = 0.226;
  steps.forEach(([a, b], i) => {
    const x = p.x + i * (bw + bg);
    L.node(s, x, p.y + 0.0, bw, 0.95, `**${i + 1}. ${a}**\n${b}`, { size: 10.5, fill: i === 0 || i === 5 ? COL.pink : COL.blueL, line: i === 0 || i === 5 ? COL.red : COL.navy });
    if (i < 5) L.arrow(s, x + bw, p.y + 0.475, x + bw + bg, p.y + 0.475);
  });
  // loop back
  L.arrow(s, p.x + 5 * (bw + bg) + bw / 2, p.y + 0.95, p.x + 5 * (bw + bg) + bw / 2, p.y + 1.2, { noHead: true, color: COL.red });
  L.arrow(s, p.x + 5 * (bw + bg) + bw / 2, p.y + 1.2, p.x + bw / 2, p.y + 1.2, { noHead: true, color: COL.red });
  L.arrow(s, p.x + bw / 2, p.y + 1.2, p.x + bw / 2, p.y + 0.95, { color: COL.red });
  L.text(s, "lặp đến khi hết hàng đợi hoặc `processed ≥ max-pages-per-run = 1000`", { x: p.x + 2.4, y: p.y + 1.24, w: 6.5, h: 0.2, size: 10, color: COL.red, align: "center" });
  p = L.panel(s, X0, 3.25, 4.0, 3.3, "Thuật toán");
  L.list(s, [
    "Nạp `url-crawled.txt` (đã crawl) + `data/url-discovered.txt` (đã phát hiện); thêm **seed** `https://hust.edu.vn/`.",
    "Mỗi URL: bỏ qua nếu **trùng lượt chạy**, **đã crawl**, hoặc **ngoài miền**.",
    "Sau khi tải: kiểm tra miền **lần nữa trên URL cuối** (redirect có thể ra ngoài).",
    "Loại UNKNOWN bị bỏ qua; mã ∉ 2xx tính vào `errors`.",
    "**Dừng/tiếp tục:** trạng thái ghi ra file → chạy lại không crawl lặp.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12, numbered: true, gap: 6 });
  p = L.panel(s, 4.65, 3.25, 4.1, 3.3, "Giới hạn phạm vi");
  L.list(s, [
    "`isAllowedDomain`: host `== hust.edu.vn` **hoặc** kết thúc bằng `.hust.edu.vn`.",
    "Kiểm tra cả URL yêu cầu lẫn **URL sau redirect**.",
    "Seed duy nhất; chỉ link **nội miền** mới vào hàng đợi (link ngoài vẫn ghi vào `links.tsv`).",
    "Số tài nguyên mỗi lượt giới hạn **1.000** (cấu hình).",
    "Quan sát thực tế: danh sách URL trải trên **118 host**, 3 host đầu chiếm 53%.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12, gap: 7 });
  p = L.panel(s, 8.9, 3.25, 3.93, 3.3, "Chuẩn hóa URL — normalizeUrl()");
  L.list(s, [
    "chữ thường scheme / host",
    "bỏ **cổng mặc định** 80 / 443",
    "bỏ **fragment** `#…`",
    "loại tham số tracking `utm_*`, `fbclid`, `gclid`",
    "chỉ nhận `http` / `https`; từ chối URL có userinfo",
  ], { x: p.x, y: p.y, w: p.w, h: 1.85, size: 12, gap: 4 });
  L.callout(s, p.x, p.y + 1.95, p.w, 0.98, "**Vì sao quan trọng?** Chuẩn hóa trước mọi so sánh ⇒ không crawl trùng cùng một trang với các biến thể URL khác nhau.", { size: 11 });
}

// =====================================================================
// 6. CRAWLER (2): LỊCH SỰ & BỀN BỈ
// =====================================================================
{
  const s = slide("Crawler (2): lịch sự với máy chủ và bền bỉ khi lỗi");
  let p = L.panel(s, X0, Y0, 4.0, 3.2, "Lịch sự (politeness)");
  L.list(s, [
    "**≥ 3 giây** giữa hai request (`request-delay-ms`, khóa `synchronized` + `lastRequestTime`).",
    "User-Agent tự khai báo: `HUSTInformationRetrievalBot/1.0 (academic research crawler)`.",
    "Executor **1 luồng** (`core = max = 1`, queue = 1) → không crawl dồn dập.",
    "Nghỉ thêm 300 ms sau mỗi trang.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12.5, gap: 8 });
  p = L.panel(s, 4.65, Y0, 4.1, 3.2, "Retry + exponential backoff");
  s.addChart(pres.charts.BAR, [{ name: "Chờ (giây)", labels: ["Lần thử 1", "Lần thử 2", "Lần thử 3"], values: [5, 10, 20] }], {
    x: p.x - 0.05, y: p.y - 0.02, w: p.w + 0.1, h: 1.85, barDir: "col", chartColors: [COL.navy], showTitle: false, showLegend: false,
    showValue: true, dataLabelPosition: "outEnd", dataLabelColor: COL.dark, dataLabelFontSize: 11, dataLabelFontFace: FONT, dataLabelFormatCode: '0" s"',
    catAxisLabelColor: COL.dark, catAxisLabelFontSize: 10, catAxisLabelFontFace: FONT, valAxisHidden: true, valGridLine: { style: "none" }, catGridLine: { style: "none" }, valAxisMaxVal: 25,
  });
  L.text(s, "`delay = 5000 ms × 2^(n−1)`; tôn trọng `Retry-After` nếu lớn hơn. Áp dụng khi **429 hoặc ≥ 500**, tối đa `max-retries = 3`.", { x: p.x, y: p.y + 1.88, w: p.w, h: 0.9, size: 11 });
  p = L.panel(s, 8.9, Y0, 3.93, 3.2, "Xử lý lỗi & timeout");
  L.list(s, [
    "Timeout **20 s**, `maxBodySize` 100 MB, theo dõi redirect.",
    "`ignoreHttpErrors` → tự xử lý mã trạng thái.",
    "Lỗi một URL **không làm dừng** cả phiên: `errors++`, tiếp tục.",
    "Redirect ra ngoài miền → bỏ qua.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12.5, gap: 8 });
  p = L.panel(s, X0, 4.4, 6.0, 2.15, "Giám sát tiến độ (REST)");
  s.addShape(pres.ShapeType.rect, { x: p.x, y: p.y, w: p.w, h: 1.2, fill: { color: COL.code }, line: { color: COL.code } });
  s.addText([
    { text: "POST /api/crawl/start   GET /api/crawl/status\n", options: { color: "9CDCFE", fontFace: L.MONO, fontSize: 11 } },
    { text: '{ "running": true, "processed": 165, "indexed": 151,\n  "errors": 9, "limit": 1000, "message": "Đang xử lý: …" }', options: { color: "E6EDF7", fontFace: L.MONO, fontSize: 11 } },
  ], { x: p.x + 0.1, y: p.y, w: p.w - 0.2, h: 1.2, margin: 0, valign: "middle", isTextBox: true });
  L.text(s, "UI poll `/status` → thanh tiến độ (đã xử lý · đã lập chỉ mục · lỗi). Mẫu thực tế: 165 / 151 / 9 (≈ 5,5% lỗi).", { x: p.x, y: p.y + 1.3, w: p.w, h: 0.5, size: 11 });
  L.callout(s, 6.65, 4.4, 6.18, 2.15, [
    "**Hạn chế nói thẳng:** crawler **chưa đọc robots.txt** (đã kiểm tra: không có xử lý trong `src`).",
    "Bù đắp: rate-limit 3 s, đơn luồng, UA định danh, giới hạn miền, chỉ chạy cho mục đích học tập.",
    "Hướng phát triển: parse `robots.txt` + `Crawl-delay`, hàng đợi ưu tiên theo host, re-crawl theo độ tươi.",
  ], { size: 12.5 });
}

// =====================================================================
// 7. ĐA ĐỊNH DẠNG
// =====================================================================
{
  const s = slide("Xử lý đa định dạng: HTML · PDF · Word · ảnh (OCR)");
  let p = L.panel(s, X0, Y0, W, 2.55, "ResourceProcessingService.detectType() — phân loại theo MIME, rồi theo đuôi file");
  L.table(s, ["Loại", "Nhận diện", "Cách xử lý", "Kết quả"], [
    ["**HTML**", "`text/html`, `.html/.htm`", "`ContentExtractor` (Jsoup)", "title, ngày, tác giả, nội dung chính, link + ảnh/media nhúng"],
    ["**PDF**", "`application/pdf`, `.pdf`", "Tika `AutoDetectParser`", "văn bản + metadata (tác giả, ngày tạo…)"],
    ["**DOCUMENT**", "docx / doc / rtf / odt", "Tika", "như PDF"],
    ["**IMAGE**", "`image/*`, jpg/png/gif/webp…", "Tika metadata + **Tesseract OCR** `vie+eng`", "chữ trong ảnh; `ocrStatus` = SUCCESS / NO_TEXT / FAILED"],
    ["UNKNOWN", "còn lại", "bỏ qua (`SKIP: UNSUPPORTED TYPE`)", "—"],
  ], p.x, p.y, p.w, [1.3, 2.8, 3.5, 4.37], { size: 11.5 });
  const ex = [
    ["Ảnh → OCR: logo-2-1.png", "db_schema/image.png", "OCR ra đúng *\"ĐẠI HỌC BÁCH KHOA HÀ NỘI / LANOI UNIVERSITY OF SCIENCE AND TECHNOLOGY\"*; metadata 31 trường."],
    ["HTML: bài tin tức", "db_schema/image(1).png", "title, `publishDate = 2026-10-11T07:12:05+07:00`, author, **170 liên kết** ra."],
    ["Word: file Erasmus (.docx)", "db_schema/image(2).png", "title *\"SOCRATES / ERASMUS\"*, author, ngày tạo lấy từ metadata Tika (25 trường)."],
  ];
  ex.forEach(([t, img, d], i) => {
    const x = X0 + i * 4.18, w = 3.97;
    const q = L.panel(s, x, 3.7, w, 2.85, t);
    L.img(s, UI(img), q.x, q.y, q.w, 1.45, { border: true });
    L.text(s, d, { x: q.x, y: q.y + 1.52, w: q.w, h: 0.95, size: 11.5 });
  });
}

// =====================================================================
// 8. TRÍCH XUẤT HTML
// =====================================================================
{
  const s = slide("Trích xuất nội dung chính từ HTML (ContentExtractor)");
  let p = L.panel(s, X0, Y0, W, 1.82, "Quy trình");
  const steps = [["Metadata", "title: og:title → twitter:title → h1 → `<title>`; ngày, tác giả từ meta / `<time>`"], ["Liên kết", "`a[href]` → URL tuyệt đối + anchor; bỏ `javascript:` `mailto:` `tel:`"], ["Lọc nhiễu", "clone DOM, xóa script, style, nav, footer, aside, form, button…"], ["Chấm điểm", "mỗi khối article / main / section / div / body (≥ 80 ký tự)"], ["Chọn + fallback", "khối điểm cao nhất → text; rỗng thì lấy `body`"]];
  const bw = 2.3, bg = 0.2075;
  steps.forEach(([a, b], i) => {
    const x = p.x + i * (bw + bg);
    L.node(s, x, p.y, bw, 1.28, `**${i + 1}. ${a}**\n${b}`, { size: 10.5, fill: i === 3 ? COL.pink : COL.blueL, line: i === 3 ? COL.red : COL.navy });
    if (i < 4) L.arrow(s, x + bw, p.y + 0.65, x + bw + bg, p.y + 0.65);
  });
  p = L.panel(s, X0, 2.97, 4.0, 3.58, "Hàm chấm điểm");
  s.addShape(pres.ShapeType.rect, { x: p.x, y: p.y, w: p.w, h: 1.3, fill: { color: COL.code }, line: { color: COL.code } });
  s.addText("score = ln(1 + độ_dài)\n      + 2   · #<p>\n      + 1,5 · #<h1–h3>\n      − 12  · link_density", { x: p.x + 0.12, y: p.y, w: p.w - 0.2, h: 1.3, fontFace: L.MONO, fontSize: 12, color: "E6EDF7", margin: 0, valign: "middle", isTextBox: true });
  L.list(s, [
    "`link_density` = độ dài text trong thẻ `<a>` / tổng text.",
    "**Trực giác:** nội dung chính = *nhiều chữ, nhiều đoạn văn, ít link*; menu thì toàn link.",
  ], { x: p.x, y: p.y + 1.45, w: p.w, h: 1.9, size: 12.5, gap: 8 });
  p = L.panel(s, 4.65, 2.97, 8.18, 3.58, "Hai chiến lược (cấu hình `content-extraction-strategy`)");
  L.table(s, ["", "DOM_HEURISTIC", "READABILITY (mặc định)"], [
    ["Cộng điểm", "+5 nếu ≥ 3 đoạn `<p>`", "+15 `<article>` · +12 `<main>` / `role=main` · +8 id/class chứa *article, content, post, entry, story*"],
    ["Trừ điểm", "−10 nếu link_density > 0,5", "**−15** nếu id/class chứa *nav, menu, footer, sidebar, comment, related, breadcrumb, widget*"],
    ["Ứng viên", "article, main, [role=main], .post-content, .entry-content, #content, section, div, body", "như DOM_HEURISTIC"],
    ["Fallback", "text rỗng → `body.text()`", "như DOM_HEURISTIC"],
  ], p.x, p.y, p.w, [1.1, 2.9, 3.82], { size: 11, boldFirst: true });
  L.callout(s, p.x, p.y + 2.5, p.w, 0.78, "**Vì sao cần?** Nếu không lọc, các từ như *trang chủ*, *liên hệ* xuất hiện ở mọi trang → làm nhiễu IDF và kết quả tìm kiếm. UML: Phụ lục A6.", { size: 11.5 });
}

// =====================================================================
// 9. LƯU TRỮ
// =====================================================================
{
  const s = slide("Lưu trữ dữ liệu: MongoDB + tệp phẳng");
  let p = L.panel(s, X0, Y0, 4.4, 3.75, "MongoDB — collection `pages` (PageDocument)");
  L.table(s, ["Trường", "Ý nghĩa"], [
    ["`_id`, `url`", "URL chuẩn hóa → ghi lại là **upsert**"],
    ["`title` `author` `publishDate`", "metadata"],
    ["`content`", "văn bản đã trích"],
    ["`contentType` `resourceType`", "MIME gốc · HTML / PDF / DOCUMENT / IMAGE"],
    ["`links[]`", "`{destinationUrl, anchorText}`, khử trùng theo đích"],
    ["`metadata{}`", "metadata Tika / OCR / kích thước"],
    ["`crawledAt`", "thời điểm crawl"],
  ], p.x, p.y, p.w, [1.75, 2.41], { size: 11, rowH: 0.4 });
  p = L.panel(s, 5.05, Y0, 3.7, 3.75, "Bản ghi thật (MongoDB Compass)");
  L.img(s, UI("db_schema/image(1).png"), p.x, p.y, p.w, 1.15, { border: true });
  L.img(s, UI("db_schema/image(3).png"), p.x, p.y + 1.25, p.w, 1.9, { border: true });
  L.text(s, "Trên: bản ghi HTML (170 links). Dưới: `links.tsv` — nguồn ⇥ đích ⇥ anchor.", { x: p.x, y: p.y + 3.2, w: p.w, h: 0.35, size: 10, color: COL.grey });
  p = L.panel(s, 8.9, Y0, 3.93, 3.75, "Tệp phẳng hỗ trợ");
  L.table(s, ["File", "Nội dung", "Quy mô"], [
    ["`links.tsv`", "đồ thị liên kết", "21.760 cạnh · 752 đích"],
    ["`url-discovered.txt`", "frontier", "760 URL"],
    ["`url-crawled.txt`", "lịch sử / nạp sẵn", "~101,8k URL · 118 host"],
  ], p.x, p.y, p.w, [1.35, 1.1, 1.24], { size: 10 });
  L.list(s, ["`links.tsv` mới chỉ **lưu**, chưa dùng để xếp hạng (PageRank là hướng phát triển).", "Ghi **append** từng dòng → an toàn khi dừng giữa chừng."], { x: p.x, y: p.y + 1.6, w: p.w, h: 1.7, size: 12, gap: 7 });
  L.callout(s, X0, 4.95, W, 0.75, "**Vì sao tách MongoDB và Lucene?** MongoDB giữ **bản gốc đầy đủ** (+ đồ thị liên kết) để **lập chỉ mục lại** bất cứ lúc nào; Lucene chỉ giữ cấu trúc phục vụ **tìm kiếm**.", { size: 12.5 });
  const cards = [["Mongo → Lucene", "cùng khóa là URL"], ["Idempotent", "lưu lại không nhân bản"], ["Mô hình dữ liệu", "xem Phụ lục A3"]];
  cards.forEach(([a, b], i) => L.stat(s, X0 + i * 4.18, 5.8, 3.97, 0.75, a, b, { nsize: 14, color: COL.navy, lsize: 10.5 }));
}

// =====================================================================
// 10. ANALYZER
// =====================================================================
{
  const s = slide("Bộ phân tích tiếng Việt tự viết (VietnameseTextAnalyzer)");
  let p = L.panel(s, X0, Y0, W, 2.1, "Pipeline — dùng chung cho cả lúc index và lúc query");
  const st = [["① Chuẩn hóa", "chữ thường, `đ → d`", "\"Đại học Bách khoa\""], ["② Bỏ dấu", "Unicode NFD + xóa `\\p{M}`", "\"dai hoc bach khoa\""], ["③ Tách từ", "regex `[\\p{L}\\p{N}]+`", "dai | hoc | bach | khoa"], ["④ Bigram", "nối từ kề nhau bằng `_`", "dai_hoc · hoc_bach · bach_khoa"]];
  const bw = 2.8, bg = 0.377;
  st.forEach(([a, b, c], i) => {
    const x = p.x + i * (bw + bg);
    L.node(s, x, p.y, bw, 1.0, `**${a}**\n${b}`, { size: 11 });
    L.node(s, x, p.y + 1.1, bw, 0.34, c, { size: 10.5, fill: COL.white, line: COL.line, bold: true });
    if (i < 3) L.arrow(s, x + bw, p.y + 0.5, x + bw + bg, p.y + 0.5);
  });
  L.text(s, "**Token stream:** `dai, dai_hoc, hoc, hoc_bach, bach, bach_khoa, khoa`", { x: p.x, y: p.y + 1.5, w: p.w, h: 0.22, size: 11 });
  p = L.panel(s, X0, 3.3, 4.0, 3.25, "Lý do thiết kế");
  L.list(s, [
    "**Bỏ dấu:** gõ *dai hoc bach khoa* vẫn khớp; chịu được lỗi dấu / khác dạng Unicode.",
    "**Bigram thay cho tách từ:** tiếng Việt viết cách chữ; bigram giữ cụm 2 âm tiết (*bách khoa, tuyển sinh*) **không cần VnCoreNLP**.",
    "**Một analyzer** cho index và query → token luôn khớp.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12.5, gap: 9 });
  p = L.panel(s, 4.65, 3.3, 3.9, 3.25, "Đánh đổi");
  L.list(s, [
    "Mất phân biệt từ **đồng âm khác dấu** (*ma / má / mà*).",
    "Chỉ mục lớn: **bigram = 83%** từ vựng (67.032 / 80.311 term).",
    "Cụm ≥ 3 âm tiết chỉ khớp qua các bigram liền kề.",
    "Chưa có stop-word, chưa stemming.",
  ], { x: p.x, y: p.y, w: p.w, h: p.h, size: 12.5, gap: 9 });
  p = L.panel(s, 8.7, 3.3, 4.13, 3.25, "Đã kiểm chứng (chỉ mục mẫu 159 trang)");
  L.stat(s, p.x, p.y, 1.95, 0.95, "100%", "trùng top-20 giữa có dấu / không dấu", { nsize: 22, lsize: 9.5 });
  L.stat(s, p.x + 2.05, p.y, 1.89, 0.95, "80.311", "term khác nhau", { nsize: 22, lsize: 9.5 });
  L.callout(s, p.x, p.y + 1.05, p.w, 1.75, "`đại học bách khoa` và `dai hoc bach khoa` cho **danh sách URL và điểm số giống hệt nhau**, ở cả hai chế độ (full / chunk). Biểu đồ Zipf: Phụ lục A9.", { kind: "green", size: 11.5 });
}

module.exports = { pres, L, slide, NOTES, X0, W, Y0, A, D, C, UI, getN: () => n, setN: (v) => { n = v; } };
