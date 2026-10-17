package vn.hust.ir.text;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes Vietnamese text and emits unigram and adjacent bigram tokens.
 */
public class VietnameseTextAnalyzer extends Analyzer {

    private static final Pattern WORD_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new VietnameseTokenizer();
        return new TokenStreamComponents(tokenizer);
    }

    private static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT)
                .replace('đ', 'd');

        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private static class VietnameseTokenizer extends Tokenizer {

        private final CharTermAttribute term =
                addAttribute(CharTermAttribute.class);
        private final OffsetAttribute offsets =
                addAttribute(OffsetAttribute.class);

        private List<Token> tokens = List.of();
        private int cursor;
        private int finalOffset;

        @Override
        public void reset() throws IOException {
            super.reset();

            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int count;

            while ((count = input.read(buffer)) != -1) {
                text.append(buffer, 0, count);
            }

            String normalized = normalize(text.toString());
            Matcher matcher = WORD_PATTERN.matcher(normalized);

            List<Token> words = new ArrayList<>();
            while (matcher.find()) {
                words.add(new Token(
                        matcher.group(),
                        matcher.start(),
                        matcher.end()
                ));
            }

            List<Token> expanded = new ArrayList<>();
            for (int i = 0; i < words.size(); i++) {
                Token current = words.get(i);
                expanded.add(current);

                if (i + 1 < words.size()) {
                    Token next = words.get(i + 1);
                    expanded.add(new Token(
                            current.text() + "_" + next.text(),
                            current.start(),
                            next.end()
                    ));
                }
            }

            tokens = expanded;
            cursor = 0;
            finalOffset = normalized.length();
        }

        @Override
        public boolean incrementToken() {
            if (cursor >= tokens.size()) {
                return false;
            }

            clearAttributes();

            Token token = tokens.get(cursor++);
            term.append(token.text());
            offsets.setOffset(token.start(), token.end());

            return true;
        }

        @Override
        public void end() throws IOException {
            super.end();
            offsets.setOffset(finalOffset, finalOffset);
        }

        private record Token(String text, int start, int end) {
        }
    }
}