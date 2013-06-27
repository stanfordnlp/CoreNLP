package edu.stanford.nlp.trees.international.arabic;

import edu.stanford.nlp.trees.PennTreebankTokenizer;
import edu.stanford.nlp.process.Tokenizer;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;


/**
 * Builds a tokenizer for the Penn Arabic Treebank (ATB) using a 
 * {@link java.io.StreamTokenizer}.
 * <p>
 * This implementation is current as of the following LDC catalog numbers: 
 * LDC2008E61 (ATBp1v4), LDC2008E62 (ATBp2v3), and LDC2008E22 (ATBp3v3.1)
 *
 * @author Christopher Manning
 * @author Spence Green
 */
public class ArabicTreebankTokenizer extends PennTreebankTokenizer {

  public ArabicTreebankTokenizer(Reader r) {
    super(r);

    //Required to support comments that appear in ATB3
    st.eolIsSignificant(true);
  }

  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  @Override
  public String getNext() {
    try {
      while (true) {

        st.nextToken();
        int nextToken = st.ttype;

        switch (nextToken) {
          case java.io.StreamTokenizer.TT_WORD:
            // ";;" are comments in ATB3
            // ":::" are also escaped for backward compatibility with the
            // old Stanford ATB pipeline
            if (st.sval.equals(":::") || st.sval.equals(";;")) {
              do {
                st.nextToken();
                nextToken = st.ttype;
              } while(nextToken != java.io.StreamTokenizer.TT_EOL);
  
              continue;
  
            } else
              return st.sval;

          case java.io.StreamTokenizer.TT_NUMBER:
            return Double.toString(st.nval);
          case java.io.StreamTokenizer.TT_EOL:
            continue;
          case java.io.StreamTokenizer.TT_EOF:
            return null;
          default:
            char[] t = {(char) nextToken};    // (array initialization)
            return new String(t);
        }
      }
    } catch (IOException e) {
      System.err.printf("%s: Unknown exception in input stream\n", this.getClass().getName());
      e.printStackTrace();
    }

    return null;
  }

  public static void main(String[] args) throws IOException {
    Tokenizer<String> att = new ArabicTreebankTokenizer(new FileReader(args[0]));
    while (att.hasNext()) {
      System.out.print(att.next());
    }
  }

}
