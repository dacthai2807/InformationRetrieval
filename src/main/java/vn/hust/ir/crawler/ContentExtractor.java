
package vn.hust.ir.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Extracts article content and metadata from HTML pages.
 *
 * Two strategies are supported:
 * - DOM_HEURISTIC: scores DOM blocks using text, paragraphs and link density.
 * - READABILITY: uses article-oriented structural and semantic scoring.
 */
@Component
public class ContentExtractor {

    public enum Strategy {
        DOM_HEURISTIC,
        READABILITY
    }

    public record LinkReference(
            String destinationUrl,
            String anchorText
    ) {}

    public record ExtractedPage(
            String url,
            String title,
            String publishDate,
            String author,
            String content,
            List<LinkReference> links
    ) {}

    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+");

    private static final String[] DATE_SELECTORS = {
            "meta[property=article:published_time]",
            "meta[property=og:published_time]",
            "meta[name=date]",
            "meta[name=pubdate]",
            "meta[name=publishdate]",
            "meta[name=datePublished]",
            "time[datetime]"
    };

    private static final String[] AUTHOR_SELECTORS = {
            "meta[name=author]",
            "meta[property=article:author]",
            "meta[name=byl]",
            "[rel=author]",
            "[class*=author]",
            "[class*=writer]"
    };

    /**
     * Extracts metadata, main text and outgoing links from a page.
     */
    public ExtractedPage extract(
            String url,
            String html,
            Strategy strategy
    ) {
        Document document = Jsoup.parse(html, url);

        String title = extractTitle(document);
        String publishDate = extractMetadata(
                document, DATE_SELECTORS, "content", "datetime");
        String author = extractMetadata(
                document, AUTHOR_SELECTORS, "content", null);

        List<LinkReference> links = extractLinks(document);

        // Remove elements that should not contribute to article text.
        Document contentDocument = document.clone();
        contentDocument.select(
                "script, style, noscript, svg, canvas, iframe, "
                + "nav, footer, aside, form, button"
        ).remove();

        String content = switch (strategy) {
            case DOM_HEURISTIC -> extractByDomHeuristic(contentDocument);
            case READABILITY -> extractByReadability(contentDocument);
        };

        // Fallback for pages without a clear article container.
        if (content.isBlank()) {
            Element body = contentDocument.body();
            content = body == null ? "" : normalize(body.text());
        }

        return new ExtractedPage(
                url,
                title,
                publishDate,
                author,
                content,
                List.copyOf(links)
        );
    }

    private String extractTitle(Document document) {
        for (String selector : new String[]{
                "meta[property=og:title]",
                "meta[name=twitter:title]"
        }) {
            Element element = document.selectFirst(selector);
            if (element != null
                    && !element.attr("content").isBlank()) {
                return normalize(element.attr("content"));
            }
        }

        Element title = document.selectFirst("h1");
        if (title != null && !title.text().isBlank()) {
            return normalize(title.text());
        }

        return document.title().trim();
    }

    private String extractMetadata(
            Document document,
            String[] selectors,
            String primaryAttribute,
            String fallbackAttribute
    ) {
        for (String selector : selectors) {
            Elements elements = document.select(selector);

            for (Element element : elements) {
                String value = element.attr(primaryAttribute);

                if (value.isBlank() && fallbackAttribute != null) {
                    value = element.attr(fallbackAttribute);
                }

                if (value.isBlank()) {
                    value = element.text();
                }

                value = normalize(value);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }

        return "";
    }

    private List<LinkReference> extractLinks(Document document) {
        List<LinkReference> result = new ArrayList<>();

        for (Element anchor : document.select("a[href]")) {
            String destination = anchor.absUrl("href").trim();
            String text = normalize(anchor.text());

            if (destination.isBlank()) {
                continue;
            }

            String lower = destination.toLowerCase(Locale.ROOT);
            if (lower.startsWith("javascript:")
                    || lower.startsWith("mailto:")
                    || lower.startsWith("tel:")) {
                continue;
            }

            result.add(new LinkReference(destination, text));
        }

        return result;
    }

    /**
     * Scores content blocks using text volume, paragraph structure,
     * headings and link density.
     */
    private String extractByDomHeuristic(Document document) {
        Element root = bestCandidate(document, false);
        return root == null ? "" : normalize(root.text());
    }

    /**
     * Readability-like extraction based on article semantics and
     * positive/negative class and ID signals.
     */
    private String extractByReadability(Document document) {
        Element root = bestCandidate(document, true);
        return root == null ? "" : normalize(root.text());
    }

    private Element bestCandidate(
            Document document,
            boolean readability
    ) {
        List<Element> candidates = new ArrayList<>();

        candidates.addAll(document.select(
                "article, main, [role=main], "
                + ".post-content, .entry-content, .article-content, "
                + "#content, #main, #article"
        ));

        candidates.addAll(document.select("section, div, body"));

        return candidates.stream()
                .distinct()
                .filter(element -> normalize(element.text()).length() >= 80)
                .max(Comparator.comparingDouble(
                        element -> score(element, readability)))
                .orElse(null);
    }

    private double score(Element element, boolean readability) {
        String text = normalize(element.text());
        int textLength = text.length();

        int paragraphCount = element.select("p").size();
        int headingCount = element.select("h1, h2, h3").size();
        int linkTextLength = 0;

        for (Element link : element.select("a")) {
            linkTextLength += normalize(link.text()).length();
        }

        double linkDensity = textLength == 0
                ? 1.0
                : (double) linkTextLength / textLength;

        String identity = (
                element.id() + " " + element.className()
        ).toLowerCase(Locale.ROOT);

        double score = Math.log1p(textLength)
                + paragraphCount * 2.0
                + headingCount * 1.5
                - linkDensity * 12.0;

        if (readability) {
            if (element.tagName().equals("article")) {
                score += 15;
            }
            if (element.tagName().equals("main")
                    || "main".equals(element.attr("role"))) {
                score += 12;
            }

            if (identity.matches(".*(article|content|post|entry|story).*")) {
                score += 8;
            }
            if (identity.matches(".*(nav|menu|footer|sidebar|comment|related|breadcrumb|widget).*")) {
                score -= 15;
            }
        } else {
            if (paragraphCount >= 3) {
                score += 5;
            }
            if (linkDensity > 0.5) {
                score -= 10;
            }
        }

        return score;
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }
}
