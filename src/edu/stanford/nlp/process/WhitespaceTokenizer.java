package edu.stanford.nlp.process;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedHashMap;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.Maps;
import edu.stanford.nlp.util.StringUtils;

/**
 * A WhitespaceTokenizer is a tokenizer that splits on and discards only
 * whitespace characters.
 * This implementation can return Word, CoreLabel or other LexedToken objects. It has a parameter
 * for whether to make EOL a token or whether to treat EOL characters as whitespace.
 * If an EOL is a token, the class returns it as a Word with String value "\n".
 *
 * <i>Implementation note:</i> This was rewritten in Apr 2006 to discard the old StreamTokenizer-based
 * implementation and to replace it with a Unicode compliant JFlex-based version.
 * This tokenizer treats as Whitespace almost exactly the same characters deemed Whitespace by the
 * Java function {@link java.lang.Character#isWhitespace(int) isWhitespace}. That is, a whitespace
 * is a Unicode SPACE_SEPARATOR, LINE_SEPARATOR or PARAGRAPH_SEPARATOR, or one of the control characters
 * U+0009-U+000D or U+001C-U+001F <i>except</i> the non-breaking space characters. The one addition is
 * to also allow U+0085 as a line ending character, for compatibility with certain IBM systems.
 * For including "spaces" in tokens, it is recommended that you represent them as the non-break space
 * character U+00A0.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Roger Levy
 * @author Christopher Manning
 */
public class WhitespaceTokenizer<T extends HasWord> extends AbstractTokenizer<T> {

  private WhitespaceLexer lexer;
  private final boolean eolIsSignificant;

  /**
   * A factory which vends WhitespaceTokenizers.
   *
   * @author Christopher Manning
   */
  public static class WhitespaceTokenizerFactory<T extends HasWord> implements TokenizerFactory<T> {

    private static final long serialVersionUID = -5438594683910349897L;

    private boolean tokenizeNLs;
    @SuppressWarnings("serial")
    private final LexedTokenFactory<T> factory;

    /**
     * Constructs a new TokenizerFactory that returns Word objects and
     * treats carriage returns as normal whitespace.
     * THIS METHOD IS INVOKED BY REFLECTION BY SOME OF THE JAVANLP
     * CODE TO LOAD A TOKENIZER FACTORY.  IT SHOULD BE PRESENT IN A
     * TokenizerFactory.
     *
     * @return A TokenizerFactory that returns Word objects
     */
    public static TokenizerFactory<Word> newTokenizerFactory() {
      return new WhitespaceTokenizerFactory<>(new WordTokenFactory(),
              false);
    }

    public WhitespaceTokenizerFactory(LexedTokenFactory<T> factory) {
      this(factory, false);
    }

    public WhitespaceTokenizerFactory(LexedTokenFactory<T> factory,
                                      String options) {
      this.factory = factory;
      LinkedHashMap<String, String> prop = StringUtils.stringToPropertiesMap(options);
      this.tokenizeNLs = Maps.getBool(prop, "tokenizeNLs", false);
    }

    public WhitespaceTokenizerFactory(LexedTokenFactory<T> factory,
                                      boolean tokenizeNLs) {
      this.factory = factory;
      this.tokenizeNLs = tokenizeNLs;
    }

    @Override
    public Iterator<T> getIterator(Reader r) {
      return getTokenizer(r);
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r) {
      return new WhitespaceTokenizer<>(factory, r, tokenizeNLs);
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r, String extraOptions) {
      LinkedHashMap<String, String> prop = StringUtils.stringToPropertiesMap(extraOptions);
      boolean tokenizeNewlines = Maps.getBool(prop, "tokenizeNLs", this.tokenizeNLs);

      return new WhitespaceTokenizer<>(factory, r, tokenizeNewlines);
    }

    @Override
    public void setOptions(String options) {
      LinkedHashMap<String, String> prop = StringUtils.stringToPropertiesMap(options);
      tokenizeNLs = Maps.getBool(prop, "tokenizeNLs", tokenizeNLs);
    }

  } // end class WhitespaceTokenizerFactory


  public static WhitespaceTokenizerFactory<CoreLabel> newCoreLabelTokenizerFactory(String options) {
    return new WhitespaceTokenizerFactory<>(new CoreLabelTokenFactory(), options);
  }

  public static WhitespaceTokenizerFactory<CoreLabel> newCoreLabelTokenizerFactory() {
    return new WhitespaceTokenizerFactory<>(new CoreLabelTokenFactory());
  }

  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  @SuppressWarnings("unchecked")
  @Override
  protected T getNext() {
    if (lexer == null) {
      return null;
    }
    try {
      T token = (T) lexer.next();
      while (token != null && token.word().equals(WhitespaceLexer.NEWLINE)) {
        if (eolIsSignificant) {
          return token;
        } else {
          token = (T) lexer.next();
        }
      }
      return token;
    } catch (IOException e) {
      return null;
    }

  }

  /**
   * Constructs a new WhitespaceTokenizer.
   *
   * @param r The Reader that is its source.
   * @param eolIsSignificant Whether eol tokens should be returned.
   */
  public WhitespaceTokenizer(LexedTokenFactory factory,
                             Reader r, boolean eolIsSignificant) {
    this.eolIsSignificant = eolIsSignificant;
    // The conditional below is perhaps currently needed in LexicalizedParser, since
    // it passes in a null arg while doing type-checking for sentence escaping
    // but StreamTokenizer barfs on that.  But maybe shouldn't be here.
    if (r != null) {
      lexer = new WhitespaceLexer(r, factory);
    }
  }

  public static WhitespaceTokenizer<CoreLabel> newCoreLabelWhitespaceTokenizer(Reader r) {
    return new WhitespaceTokenizer<>(new CoreLabelTokenFactory(), r, false);
  }

  public static WhitespaceTokenizer<CoreLabel> newCoreLabelWhitespaceTokenizer(Reader r, boolean tokenizeNLs) {
    return new WhitespaceTokenizer<>(new CoreLabelTokenFactory(), r, tokenizeNLs);
  }

  public static WhitespaceTokenizer<Word>
    newWordWhitespaceTokenizer(Reader r)
  {
    return newWordWhitespaceTokenizer(r, false);
  }

  public static WhitespaceTokenizer<Word>
    newWordWhitespaceTokenizer(Reader r, boolean eolIsSignificant)
  {
    return new WhitespaceTokenizer<>(new WordTokenFactory(), r,
            eolIsSignificant);
  }

  /* ----
   * Sets the source of this Tokenizer to be the Reader r.

  private void setSource(Reader r) {
    lexer = new WhitespaceLexer(r);
  }
  ---- */

  public static TokenizerFactory<Word> factory() {
    return new WhitespaceTokenizerFactory<>(new WordTokenFactory(),
            false);
  }

  public static TokenizerFactory<Word> factory(boolean eolIsSignificant) {
    return new WhitespaceTokenizerFactory<>(new WordTokenFactory(),
            eolIsSignificant);
  }

  /**
   * Reads a file from the argument and prints its tokens one per line.
   * This is mainly as a testing aid, but it can also be quite useful
   * standalone to turn a corpus into a one token per line file of tokens.
   *
   * Usage: {@code java edu.stanford.nlp.process.WhitespaceTokenizer filename }
   *
   * @param args Command line arguments
   * @throws IOException If can't open files, etc.
   */
  public static void main(String[] args) throws IOException {

    boolean eolIsSignificant = (args.length > 0 && args[0].equals("-cr"));
    Reader reader = ((args.length > 0 &&
                      !args[args.length - 1].equals("-cr")) ?
                     new InputStreamReader(new FileInputStream
                                           (args[args.length - 1]), "UTF-8") :
                     new InputStreamReader(System.in, "UTF-8"));
    WhitespaceTokenizer<Word> tokenizer =
            new WhitespaceTokenizer<>(new WordTokenFactory(), reader,
                    eolIsSignificant);
    PrintWriter pw =
      new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"), true);
    while (tokenizer.hasNext()) {
      Word w = tokenizer.next();
      if (w.value().equals(WhitespaceLexer.NEWLINE)) {
        pw.println("***CR***");
      } else {
        pw.println(w);
      }
    }
  }

} // end class WhitespaceTokenizer

