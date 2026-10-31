import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.document.Document;
import vn.hust.ir.text.VietnameseTextAnalyzer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Analyze {
  static Analyzer an = new VietnameseTextAnalyzer();
  public static void main(String[] a) throws Exception {
    Path idx = Path.of(a[0]); Path out = Path.of(a[1]);
    try (var dir = FSDirectory.open(idx); var r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setSimilarity(new ClassicSimilarity());
      // 1) per-doc stats
      try (var w = new PrintWriter(out.resolve("docs.tsv").toFile())) {
        w.println("type\turl\twords\tchunkIndex");
        var sf = s.storedFields();
        for (int i = 0; i < r.maxDoc(); i++) {
          Document d = sf.document(i);
          String t = d.get("documentType");
          String url = "full".equals(t) ? d.get("url") : d.get("chunkUrl");
          String c = d.get("content"); int words = c == null ? 0 : c.split("\\s+").length;
          w.println(t + "\t" + url + "\t" + words + "\t" + (d.getField("chunkIndex")==null?-1:d.getField("chunkIndex").numericValue()));
        }
      }
      // 2) vocabulary
      try (var w = new PrintWriter(out.resolve("terms.tsv").toFile())) {
        w.println("term\tdf");
        Terms ts = MultiTerms.getTerms(r, "content");
        TermsEnum te = ts.iterator(); BytesRef b;
        while ((b = te.next()) != null) w.println(b.utf8ToString() + "\t" + te.docFreq());
      }
      // 3) queries
      String[] qs = {"đại học bách khoa","trí tuệ nhân tạo","tuyển sinh đại học","nghiên cứu khoa học",
        "dai hoc bach khoa","tuyen sinh","học bổng","kỷ niệm 70 năm","đào tạo sau đại học","hợp tác quốc tế",
        "thư viện","sinh viên khởi nghiệp","công nghệ thông tin","kỹ thuật điện","lịch công tác"};
      try (var w = new PrintWriter(out.resolve("queries.tsv").toFile())) {
        w.println("query\tmode\tsim\thits\tms\ttop_scores\ttop_urls");
        for (String q : qs) for (String sim : new String[]{"tfidf","bm25"}) for (String mode : new String[]{"full","chunk"}) {
          s.setSimilarity(sim.equals("tfidf") ? new ClassicSimilarity() : new BM25Similarity());
          var p = new MultiFieldQueryParser(new String[]{"title","content"}, an, Map.of("title",2f,"content",1f));
          p.setDefaultOperator(MultiFieldQueryParser.Operator.OR);
          Query tq = p.parse(MultiFieldQueryParser.escape(q));
          Query query = new BooleanQuery.Builder().add(tq, BooleanClause.Occur.MUST)
              .add(new TermQuery(new Term("documentType", mode)), BooleanClause.Occur.FILTER).build();
          int hits = s.count(query);
          boolean dedup = mode.equals("chunk");
          long best = Long.MAX_VALUE; TopDocs td = null;
          for (int k = 0; k < 30; k++) { long t0 = System.nanoTime(); td = s.search(query, dedup ? 200 : 20); best = Math.min(best, System.nanoTime()-t0); }
          LinkedHashMap<String,Float> u = new LinkedHashMap<>();
          for (ScoreDoc sd : td.scoreDocs) { Document d = s.storedFields().document(sd.doc);
            String url = dedup ? d.get("chunkUrl") : d.get("url"); u.putIfAbsent(url, sd.score); if (u.size()>=20) break; }
          StringBuilder sc = new StringBuilder(), ur = new StringBuilder();
          for (var e : u.entrySet()) { sc.append(String.format(Locale.ROOT,"%.4f,", e.getValue())); ur.append(e.getKey()).append(' '); }
          w.println(q+"\t"+mode+"\t"+sim+"\t"+hits+"\t"+String.format(Locale.ROOT,"%.3f",best/1e6)+"\t"+sc+"\t"+ur.toString().trim());
        }
      }
    }
  }
}
