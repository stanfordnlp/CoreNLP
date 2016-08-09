package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;

/**
 * A simple tokenizer for tokenizing Penn Chinese Treebank files.  A
 * token is any parenthesis, node label, or terminal.  All SGML
 * content of the files is ignored.
 *
 * @author Roger Levy
 * @version 01/17/2003
 */
public class CHTBTokenizer extends AbstractTokenizer<String>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CHTBTokenizer.class);

  private final CHTBLexer lexer;


  /**
   * Constructs a new tokenizer from a Reader.  Note that getting
   * the bytes going into the Reader into Java-internal Unicode is
   * not the tokenizer's job.  This can be done by converting the
   * file with {@code ConvertEncodingThread}, or by specifying
   * the files encoding explicitly in the Reader with
   * java.io.{@code InputStreamReader}.
   *
   * @param r Reader
   */
  public CHTBTokenizer(Reader r) {
    lexer = new CHTBLexer(r);
  }


  /**
   * Internally fetches the next token.
   *
   * @return The next token in the token stream, or null if none exists.
   */
  @Override
  protected String getNext() {
    try {
      int a;
      while ((a = lexer.yylex()) == CHTBLexer.IGNORE) {
        // log.info("#ignored: " + lexer.match());
      }
      if (a == CHTBLexer.YYEOF) {
        return null;
      } else {
        //log.info("#matched: " + lexer.match());
        return lexer.match();
      }
    } catch (IOException ioe) {
      // do nothing, return null
    }
    return null;
  }


  /**
   * The main() method tokenizes a file in the specified Encoding
   * and prints it to standard output in the specified Encoding.
   * Its arguments are (Infile, Encoding).
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      log.error("Usage: CHTBTokenizer inputFile encoding");
    }
    String encoding = args[1];
    Reader in = IOUtils.readerFromString(args[0], encoding);

    for (Tokenizer<String> st = new CHTBTokenizer(in); st.hasNext(); ) {
      String s = st.next();
      EncodingPrintWriter.out.println(s, encoding);
      // EncodingPrintWriter.out.println("|" + s + "| (" + s.length() + ")",
      //				encoding);
    }
  }

}
