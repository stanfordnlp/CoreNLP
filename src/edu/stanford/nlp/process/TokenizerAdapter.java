package edu.stanford.nlp.process;


import java.io.IOException;
import java.io.StreamTokenizer;


/**
 * This class adapts between a <code>java.io.StreamTokenizer</code>
 * and a <code>edu.stanford.nlp.process.Tokenizer</code>.
 *
 * @author Christopher Manning
 * @version 2004/04/01
 */
public class TokenizerAdapter extends AbstractTokenizer<String> {

  protected final StreamTokenizer st;

  protected String eolString = "<EOL>";


  /**
   * Create a new <code>TokenizerAdaptor</code>.  In general, it is
   * recommended that the passed in <code>StreamTokenizer</code> should
   * have had <code>resetSyntax()</code> done to it, so that numbers are
   * returned as entered as tokens of type <code>String</code>, though this
   * code will cope as best it can.
   *
   * @param st The internal <code>java.io.StreamTokenizer</code>
   */
  public TokenizerAdapter(StreamTokenizer st) {
    this.st = st;
  }


  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  @Override
  public String getNext() {
    try {
      int nextTok = st.nextToken();
      switch (nextTok) {
        case java.io.StreamTokenizer.TT_EOL:
          return eolString;
        case java.io.StreamTokenizer.TT_EOF:
          return null;
        case java.io.StreamTokenizer.TT_WORD:
          return st.sval;
        case java.io.StreamTokenizer.TT_NUMBER:
          return Double.toString(st.nval);
        default:
          char[] t = { (char) nextTok };    // (array initialization)
          return new String(t);
      }
    } catch (IOException ioe) {
      // do nothing, return null
      return null;
    }
  }


  /**
   * Set the <code>String</code> returned when the inner tokenizer
   * returns an end-of-line token.  This will only happen if the
   * inner tokenizer has been set to <code>eolIsSignificant(true)</code>.
   *
   * @param eolString The String used to represent eol.  It is not allowed
   *                  to be <code>null</code> (which would confuse line ends and file end)
   */
  public void setEolString(String eolString) {
    if (eolString == null) {
      throw new IllegalArgumentException("eolString cannot be null");
    }
    this.eolString = eolString;
  }


  /**
   * Say whether the <code>String</code> is the end-of-line token for
   * this tokenizer.
   *
   * @param str The String being tested
   * @return Whether it is the end-of-line token
   */
  public boolean isEol(String str) {
    return eolString.equals(str);
  }

}
