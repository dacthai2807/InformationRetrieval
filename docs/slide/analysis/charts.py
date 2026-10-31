"""Sinh biểu đồ phân tích từ dữ liệu repo + kết quả Analyze.java (out/*.tsv)."""
import collections, csv, math, re, sys
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent / "out"
CH = Path(__file__).resolve().parents[1] / "charts"
CH.mkdir(exist_ok=True)
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11, "axes.spines.top": False,
                     "axes.spines.right": False, "figure.dpi": 150, "savefig.bbox": "tight"})
C1, C2, C3 = "#1565C0", "#EF6C00", "#2E7D32"
stats = {}

def tsv(p):
    with open(p, encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))

def save(name): plt.savefig(CH / name); plt.close()

# 1. host distribution of url-crawled.txt
hosts = collections.Counter(re.sub(r"https?://([^/]+).*", r"\1", l.strip())
                            for l in open(ROOT / "url-crawled.txt", encoding="utf-8") if l.strip())
top = hosts.most_common(10)
fig, ax = plt.subplots(figsize=(8, 4.2))
ax.barh([h for h, _ in top][::-1], [c for _, c in top][::-1], color=C1)
for i, (_, c) in enumerate(top[::-1]): ax.text(c, i, f" {c:,}", va="center", fontsize=9)
ax.set_title(f"Top 10 host trong url-crawled.txt ({sum(hosts.values()):,} URL, {len(hosts)} host)")
ax.set_xlabel("Số URL"); save("c01_host_distribution.png")
stats["hosts"] = len(hosts); stats["urls"] = sum(hosts.values())
stats["top3_share"] = sum(c for _, c in top[:3]) / sum(hosts.values())

# 2. index composition + chunks per page
docs = tsv(OUT / "docs.tsv")
full = [d for d in docs if d["type"] == "full"]; chunk = [d for d in docs if d["type"] == "chunk"]
cpp = collections.Counter(d["url"] for d in chunk)
fig, (a, b) = plt.subplots(1, 2, figsize=(10, 4), gridspec_kw={"width_ratios": [1, 2]})
a.bar(["full", "chunk"], [len(full), len(chunk)], color=[C1, C2])
for i, v in enumerate([len(full), len(chunk)]): a.text(i, v, str(v), ha="center", va="bottom")
a.set_title("Số tài liệu Lucene theo loại"); a.set_ylabel("Số document")
dist = collections.Counter(cpp.values()); ks = sorted(dist)
b.bar([str(k) for k in ks], [dist[k] for k in ks], color=C3)
b.set_title("Số chunk trên mỗi trang"); b.set_xlabel("Số chunk"); b.set_ylabel("Số trang")
save("c02_index_composition.png")
stats.update(full=len(full), chunk=len(chunk), pages_1chunk=dist[1], pages_multi=sum(v for k, v in dist.items() if k > 1),
             max_chunks=max(cpp.values()), avg_chunks=len(chunk) / len(cpp))

# 3. page length distribution (words) of full docs
words = sorted(int(d["words"]) for d in full)
fig, ax = plt.subplots(figsize=(8, 4))
bins = [10 ** (i / 2) for i in range(0, 11)]
ax.hist([max(w, 1) for w in words], bins=bins, color=C1, edgecolor="white")
ax.set_xscale("log"); ax.axvline(400, color=C2, ls="--"); ax.text(420, ax.get_ylim()[1] * 0.9, "CHUNK_SIZE = 400", color=C2)
ax.set_title("Phân bố độ dài trang (số từ, thang log)"); ax.set_xlabel("Số từ"); ax.set_ylabel("Số trang")
save("c03_page_length.png")
stats.update(median_words=words[len(words) // 2], mean_words=sum(words) / len(words), max_words=words[-1],
             over400=sum(w > 400 for w in words), n_pages=len(words))

# 4. resource type by URL extension (full docs)
def rtype(u):
    p = u.split("?")[0].lower()
    if p.endswith(".pdf"): return "PDF"
    if re.search(r"\.(docx?|rtf|odt)$", p): return "DOCUMENT"
    if re.search(r"\.(jpe?g|png|gif|webp|bmp|tiff?|svg|ico)$", p): return "IMAGE (OCR)"
    return "HTML"
rt = collections.Counter(rtype(d["url"]) for d in full)
fig, ax = plt.subplots(figsize=(6, 4))
ks = list(rt); ax.bar(ks, [rt[k] for k in ks], color=[C1, C2, C3, "#6A1B9A"][:len(ks)])
for i, k in enumerate(ks): ax.text(i, rt[k], rt[k], ha="center", va="bottom")
ax.set_title("Loại tài nguyên trong chỉ mục (suy từ URL)"); ax.set_ylabel("Số trang"); save("c04_resource_types.png")
stats["rtypes"] = dict(rt)

# 5. link graph
indeg, outdeg = collections.Counter(), collections.Counter()
with open(ROOT / "data/links.tsv", encoding="utf-8") as f:
    for l in f:
        p = l.rstrip("\n").split("\t")
        if len(p) >= 2: outdeg[p[0]] += 1; indeg[p[1]] += 1
edges = sum(indeg.values())
fig, (a, b) = plt.subplots(1, 2, figsize=(11, 4.2))
t10 = indeg.most_common(10)
a.barh([re.sub(r"https?://", "", u)[:38] for u, _ in t10][::-1], [c for _, c in t10][::-1], color=C1)
a.set_title("10 URL được liên kết tới nhiều nhất (in-degree)"); a.tick_params(axis="y", labelsize=8)
dd = collections.Counter(indeg.values()); xs = sorted(dd)
b.loglog(xs, [dd[x] for x in xs], "o", color=C2, ms=4)
b.set_title("Phân bố in-degree (log-log)"); b.set_xlabel("in-degree"); b.set_ylabel("Số URL")
save("c05_link_graph.png")
stats.update(edges=edges, dests=len(indeg), srcs=len(outdeg), max_in=t10[0][1], avg_out=edges / len(outdeg))

# 6. vocabulary
terms = tsv(OUT / "terms.tsv")
uni = [int(t["df"]) for t in terms if "_" not in t["term"]]; bi = [int(t["df"]) for t in terms if "_" in t["term"]]
fig, (a, b) = plt.subplots(1, 2, figsize=(10, 4))
a.bar(["unigram", "bigram"], [len(uni), len(bi)], color=[C1, C2])
for i, v in enumerate([len(uni), len(bi)]): a.text(i, v, f"{v:,}", ha="center", va="bottom")
a.set_title("Kích thước từ vựng (trường content)"); a.set_ylabel("Số term khác nhau")
for arr, c, n in ((sorted(uni, reverse=True), C1, "unigram"), (sorted(bi, reverse=True), C2, "bigram")):
    b.loglog(range(1, len(arr) + 1), arr, color=c, label=n)
b.set_title("Luật Zipf: df theo hạng"); b.set_xlabel("Hạng term"); b.set_ylabel("Document frequency"); b.legend()
save("c06_vocabulary.png")
stats.update(vocab=len(terms), uni=len(uni), bi=len(bi), df1_share=sum(1 for t in terms if int(t["df"]) == 1) / len(terms))

# 7-9. queries
q = tsv(OUT / "queries.tsv")
def row(query, mode, sim): return next(r for r in q if r["query"] == query and r["mode"] == mode and r["sim"] == sim)
queries = list(dict.fromkeys(r["query"] for r in q))
short = [s if len(s) < 18 else s[:17] + "…" for s in queries]
x = range(len(queries)); w = 0.4
fig, ax = plt.subplots(figsize=(11, 4.4))
ax.bar([i - w / 2 for i in x], [int(row(s, "full", "tfidf")["hits"]) for s in queries], w, label="Full-document", color=C1)
ax.bar([i + w / 2 for i in x], [int(row(s, "chunk", "tfidf")["hits"]) for s in queries], w, label="Chunk-based (trước gộp URL)", color=C2)
ax.set_xticks(list(x)); ax.set_xticklabels(short, rotation=40, ha="right")
ax.set_ylabel("Số tài liệu khớp"); ax.set_title("Số tài liệu khớp theo truy vấn (OR + bigram → độ phủ cao)"); ax.legend(); save("c07_hits.png")

def urls(r): return r["top_urls"].split()
def overlap(a, b, k=10):
    A, B = set(urls(a)[:k]), set(urls(b)[:k]); return len(A & B) / k if A and B else 0
ov_fc = [overlap(row(s, "full", "tfidf"), row(s, "chunk", "tfidf")) for s in queries]
ov_tb_f = [overlap(row(s, "full", "tfidf"), row(s, "full", "bm25")) for s in queries]
ov_tb_c = [overlap(row(s, "chunk", "tfidf"), row(s, "chunk", "bm25")) for s in queries]
fig, ax = plt.subplots(figsize=(11, 4.4)); w = 0.27
ax.bar([i - w for i in x], ov_fc, w, label="Full vs Chunk (TF-IDF)", color=C1)
ax.bar(list(x), ov_tb_f, w, label="TF-IDF vs BM25 (Full)", color=C2)
ax.bar([i + w for i in x], ov_tb_c, w, label="TF-IDF vs BM25 (Chunk)", color=C3)
ax.set_xticks(list(x)); ax.set_xticklabels(short, rotation=40, ha="right"); ax.set_ylim(0, 1.05)
ax.set_ylabel("Overlap@10"); ax.set_title("Mức giao nhau top-10 giữa các cấu hình"); ax.legend(ncol=3, fontsize=9, loc="upper center", bbox_to_anchor=(0.5, 1.0), frameon=False); ax.set_ylim(0, 1.2); save("c08_overlap.png")
stats.update(ov_fc=sum(ov_fc) / len(ov_fc), ov_tb_f=sum(ov_tb_f) / len(ov_tb_f), ov_tb_c=sum(ov_tb_c) / len(ov_tb_c))

lat = {m: [float(row(s, m, "tfidf")["ms"]) for s in queries] for m in ("full", "chunk")}
fig, ax = plt.subplots(figsize=(11, 4)); w = 0.4
ax.bar([i - w / 2 for i in x], lat["full"], w, label="Full", color=C1); ax.bar([i + w / 2 for i in x], lat["chunk"], w, label="Chunk", color=C2)
ax.set_xticks(list(x)); ax.set_xticklabels(short, rotation=40, ha="right"); ax.set_ylabel("ms (tốt nhất / 30 lần)")
ax.set_title("Độ trễ truy vấn (TF-IDF, ~160 trang)"); ax.legend(); save("c09_latency.png")
stats.update(lat_full=sum(lat["full"]) / len(lat["full"]), lat_chunk=sum(lat["chunk"]) / len(lat["chunk"]))

# 10. score distribution of top-20, TF-IDF full vs chunk for 4 demo queries
fig, axs = plt.subplots(1, 4, figsize=(13, 3.6), sharey=False)
for ax, s in zip(axs, queries[:4]):
    for m, c in (("full", C1), ("chunk", C2)):
        sc = [float(v) for v in row(s, m, "tfidf")["top_scores"].split(",") if v]
        ax.plot(range(1, len(sc) + 1), sc, "-o", ms=3, color=c, label=m)
    ax.set_title(s, fontsize=10); ax.set_xlabel("Hạng")
axs[0].set_ylabel("Điểm TF-IDF"); axs[0].legend(); plt.suptitle("Điểm TF-IDF top-20 theo hạng", y=1.03); save("c10_score_curves.png")

for k, v in stats.items(): print(k, v)
