package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.Tokenizer;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

/**
 * A tokenizer for the ICEGB corpus. (still being worked on)
 *
 * @author Pi-Chuan Chang
 */
public class ICEGBTokenizer extends AbstractTokenizer<ICEGBToken> {

  /**
   * A factory which vends ICEGBTokenizer
   *
   * @author Pi-Chuan Chang
   */
  static class ICEGBTokenizerFactory implements TokenizerFactory<ICEGBToken> {
    public Iterator<ICEGBToken> getIterator(Reader r) {
      return getTokenizer(r);
    }

    public Tokenizer<ICEGBToken> getTokenizer(Reader r) {
      return new ICEGBTokenizer(r);
    }

    public Tokenizer<ICEGBToken> getTokenizer(Reader r, String extraOptions) {
      //Silently ignore extra options
      return getTokenizer(r);
    }

    public void setOptions(String options) {
      //Silently ignore
    }

  } // end class WhitespaceTokenizerFactory


  private ICEGBLexer lexer = null;


  /**
   * Constructs a new tokenizer from a Reader.
   *
   * @param r Reader
   */
  public ICEGBTokenizer(Reader r) {
    lexer = new ICEGBLexer(r);
    //super(new ICEGBLexer(r));
  }

  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  @Override
  public ICEGBToken getNext() {
    try {
      int a = 0;
      while ((a = lexer.yylex()) == ICEGBLexer.IGNORE) {
        System.err.println("#ignored: " + lexer.match());
      }
      switch (a) {
        case ICEGBLexer.YYEOF:
          //return new ICEGBToken(a, null);
          return null;
        default:
          //System.err.println("#"+tokenTypeNames[a]+": "+lexer.match());
          return new ICEGBToken(a, lexer.match());
          //      case ICEGBLexer.SEPARATE:
          //        System.err.println("----------------#SEPARATE----------------");
          //        return lexer.match();
          //      case ICEGBLexer.STRING:
          //        System.err.println("#STRING: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.LINEEND:
          //        //System.err.println("#LINEEND: "+lexer.match());
          //        System.err.println("#LINEEND");
          //        return lexer.match();
          //      case ICEGBLexer.WHITESPACE:
          //        System.err.println("#WHITESPACE: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.LPAREN:
          //        System.err.println("#LPAREN: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.RPAREN:
          //        System.err.println("#RPAREN: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.LBR:
          //        System.err.println("#LBR: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.RBR:
          //        System.err.println("#RBR: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.TAG:
          //        System.err.println("#TAG: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.LSB:
          //        System.err.println("#LSB: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.RSB:
          //        System.err.println("#RSB: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.COMMA:
          //        System.err.println("#COMMA: "+lexer.match());
          //        return lexer.match();
          //      case ICEGBLexer.YYEOF:
          //        return null;
      }
    } catch (IOException ioe) {
      // do nothing, return null
    }
    return null;
  }

  protected static final String[] tokenTypeNames = {"IGNORE", "STRING", "LINEEND", "WHITESPACE", "LPAREN", "RPAREN", "LBR", "RBR", "TAG", "LSB", "RSB", "COMMA", "SEPARATE", "OTHER"};

  public static void main(String[] args) throws IOException {
    Reader in = new FileReader(args[0]);
    ICEGBTokenizer st = new ICEGBTokenizer(in);
    //String s;
    while (st.hasNext()) {
      ICEGBToken nextToken = st.next();
      if (nextToken.type == ICEGBLexer.YYEOF) {
        System.err.println("#" + tokenTypeNames[nextToken.type]);
      } else {
        System.err.println("#" + tokenTypeNames[nextToken.type] + ": " + nextToken.text);
      }

      //System.out.println(nextToken.toString());
    }
  }

}

class ICEGBToken {
  int type;
  String text;

  public ICEGBToken(int type, String text) {
    this.type = type;
    this.text = text;
  }

  @Override
  public String toString() {
    String str = null;
    if (this.type != ICEGBLexer.YYEOF) {
      str = "#" + ICEGBTokenizer.tokenTypeNames[this.type] + ": " + this.text;
    }
    return str;
  }
}
