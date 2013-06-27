package edu.stanford.nlp.trees.international.negra;

import edu.stanford.nlp.process.LexerTokenizer;
import edu.stanford.nlp.process.Tokenizer;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;


/**
 * Produces a tokenizer for the NEGRA corpus in context-free Penn
 * Treebank format.
 *
 * @author Roger Levy
 */
public class NegraPennTokenizer extends LexerTokenizer {

  public NegraPennTokenizer(Reader r) {
    super(new NegraPennLexer(r));
  }


  public static void main(String[] args) throws IOException {

    Reader in = new FileReader(args[0]);
    Tokenizer st = new NegraPennTokenizer(in);

    while (st.hasNext()) {
      String s = (String) st.next();
      System.out.println(s);
    }
  }

}
