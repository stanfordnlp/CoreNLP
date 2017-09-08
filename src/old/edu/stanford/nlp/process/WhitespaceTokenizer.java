package old.edu.stanford.nlp.process;


import old.edu.stanford.nlp.ling.Word;
import old.edu.stanford.nlp.objectbank.TokenizerFactory;

import java.io.*;
import java.util.Iterator;

/**
 * A WhitespaceTokenizer is a tokenizer that splits on and discards only
 * whitespace characters.
 * This implementation returns Word objects. It has a parameter for whether
 * to make EOL a token or whether to treat EOL characters as whitespace.
 * If an EOL is a token, the class returns it as a Word with String value "\n".
 * <p/>
 * <i>Implementation note:</i> This was rewritten in Apr 2006 to discard the
 * old StreamTokenizer based implementation and to replace it with a
 * Unicode compliant JFlex-based version.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Roger Levy
 * @author Christopher Manning
 */
public class WhitespaceTokenizer extends AbstractTokenizer<Word> {

  /**
   * A factory which vends WhitespaceTokenizers.
   *
   * @author Christopher Manning
   */
  public static class WhitespaceTokenizerFactory implements TokenizerFactory<Word> {

    private final boolean eolIsSignificant;

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
      return new WhitespaceTokenizerFactory();
    }

    public WhitespaceTokenizerFactory() {
      this(false);
    }

    public WhitespaceTokenizerFactory(boolean eolIsSignificant) {
      this.eolIsSignificant = eolIsSignificant;
    }

    public Iterator<Word> getIterator(Reader r) {
      return getTokenizer(r);
    }

    public Tokenizer<Word> getTokenizer(Reader r) {
      return new WhitespaceTokenizer(r, eolIsSignificant);
    }

  } // end class WhitespaceTokenizerFactory


  private WhitespaceLexer lexer;
  private final boolean eolIsSignificant;

  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  @Override
  protected Word getNext() {
    Word token = null;
    if (lexer == null) {
      return token;
    }
    try {
      token = lexer.next();
      while (token == WhitespaceLexer.crValue) {
        if (eolIsSignificant) {
          return token;
        } else {
          token = lexer.next();
        }
      }
    } catch (IOException e) {
      // do nothing, return null
    }
    return token;
  }

  /**
   * Constructs a new WhitespaceTokenizer
   *
   * @param r The Reader r that is its source.
   */
  public WhitespaceTokenizer(Reader r) {
    this(r, false);
  }


  /**
   * Constructs a new WhitespaceTokenizer
   * @param r The Reader that is its source.
   * @param eolIsSignificant Whether eol tokens should be returned.
   */
  public WhitespaceTokenizer(Reader r, boolean eolIsSignificant) {
    this.eolIsSignificant = eolIsSignificant;
    // The conditional below is perhaps currently needed in LexicalizedParser, since
    // it passes in a null arg while doing type-checking for sentence escaping
    // but StreamTokenizer barfs on that.  But maybe shouldn't be here.
    if (r != null) {
      lexer = new WhitespaceLexer(r);
    }
  }

  /* ----
   * Sets the source of this Tokenizer to be the Reader r.

  private void setSource(Reader r) {
    lexer = new WhitespaceLexer(r);
  }
  ---- */

  public static TokenizerFactory<Word> factory() {
    return new WhitespaceTokenizerFactory(false);
  }

  public static TokenizerFactory<Word> factory(boolean eolIsSignificant) {
    return new WhitespaceTokenizerFactory(eolIsSignificant);
  }

  /**
   * Reads a file from the argument and prints its tokens one per line.
   * This is mainly as a testing aid, but it can also be quite useful
   * standalone to turn a corpus into a one token per line file of tokens.
   * <p/>
   * Usage: <code>java edu.stanford.nlp.process.WhitespaceTokenizer filename
   * </code>
   *
   * @param args Command line arguments
   * @throws IOException If can't open files, etc.
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println("usage: java edu.stanford.nlp.process.WhitespaceTokenizer [-cr] filename");
      return;
    }
    WhitespaceTokenizer tokenizer = new WhitespaceTokenizer(new InputStreamReader(new FileInputStream(args[args.length - 1]), "UTF-8"), args[0].equals("-cr"));
    PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"), true);
    while (tokenizer.hasNext()) {
      Word w = tokenizer.next();
      if (w == WhitespaceLexer.crValue) {
        pw.println("***CR***");
      } else {
        pw.println(w);
      }
    }
  }

} // end class WhitespaceTokenizer

