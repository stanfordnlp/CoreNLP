package edu.stanford.nlp.process;

import java.io.Reader;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/** A tokenizer that works by calling a WordSegmenter.
 *  This is used for Chinese and Arabic.
 *
 *  @author Galen Andrew
 *  @author Spence Green
 */
public class WordSegmentingTokenizer extends AbstractTokenizer<HasWord> {

  private Iterator<HasWord> wordIter;
  private Tokenizer<CoreLabel> tok;
  private WordSegmenter wordSegmenter;

  public WordSegmentingTokenizer(WordSegmenter segmenter, Reader r) {
    this(segmenter, WhitespaceTokenizer.newCoreLabelWhitespaceTokenizer(r));
  }

  public WordSegmentingTokenizer(WordSegmenter segmenter, Tokenizer<CoreLabel> tokenizer) {
    wordSegmenter = segmenter;
    tok = tokenizer;
  }

  @Override
  protected HasWord getNext() {
    while (wordIter == null || ! wordIter.hasNext()) {
      if ( ! tok.hasNext()) {
        return null;
      }
      CoreLabel token = tok.next();
      String s = token.word();
      if (s == null) {
        return null;
      }
      if (s.equals(WhitespaceLexer.NEWLINE)) {
        // if newlines were significant, we should make sure to return
        // them when we see them
        List<HasWord> se = Collections.<HasWord>singletonList(token);
        wordIter = se.iterator();
      } else {
        List<HasWord> se = wordSegmenter.segment(s);
        wordIter = se.iterator();
      }
    }
    return wordIter.next();
  }

  public static TokenizerFactory<HasWord> factory(WordSegmenter wordSegmenter) {
    return new WordSegmentingTokenizerFactory(wordSegmenter);
  }

  private static class WordSegmentingTokenizerFactory implements TokenizerFactory<HasWord>, Serializable {
    private static final long serialVersionUID = -4697961121607489828L;

    boolean tokenizeNLs = false;
    private WordSegmenter segmenter;

    public WordSegmentingTokenizerFactory(WordSegmenter wordSegmenter) {
      segmenter = wordSegmenter;
    }

    public Iterator<HasWord> getIterator(Reader r) {
      return getTokenizer(r);
    }

    public Tokenizer<HasWord> getTokenizer(Reader r) {
      return getTokenizer(r, null);
    }

    public Tokenizer<HasWord> getTokenizer(Reader r, String extraOptions) {
      boolean tokenizeNewlines = this.tokenizeNLs;
      if (extraOptions != null) {
        Properties prop = StringUtils.stringToProperties(extraOptions);
        tokenizeNewlines = PropertiesUtils.getBool(prop, "tokenizeNLs", this.tokenizeNLs);
      }

      return new WordSegmentingTokenizer(segmenter, WhitespaceTokenizer.newCoreLabelWhitespaceTokenizer(r, tokenizeNewlines));
    }

    public void setOptions(String options) {
      Properties prop = StringUtils.stringToProperties(options);
      tokenizeNLs = PropertiesUtils.getBool(prop, "tokenizeNLs", tokenizeNLs);
    }
  }
}
