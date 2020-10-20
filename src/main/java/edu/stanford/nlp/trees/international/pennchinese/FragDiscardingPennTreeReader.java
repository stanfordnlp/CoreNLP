package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;

import java.io.*;

/**
 * @author Galen Andrew
 */
public class FragDiscardingPennTreeReader extends PennTreeReader {
  public FragDiscardingPennTreeReader(Reader in, TreeFactory tf, TreeNormalizer tn, Tokenizer<String> tk) {
    super(in, tf, tn, tk);
  }

//  private static PrintWriter pw;
//
//  static {
//    try {
//      if (false) {
//        pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream("discardedFRAGs.chi"), "GB18030"), true);
//      }
//    } catch (Exception e) {
//      throw new RuntimeException("");
//    }
//  }

  @Override
  public Tree readTree() throws IOException {
    Tree tr = super.readTree();
    while (tr != null && tr.firstChild().value().equals("FRAG")) {
//      if (pw != null) {
//        pw.println("Discarding Tree:");
//        tr.pennPrint(pw);
//      }
      tr = super.readTree();
    }
    return tr;
  }
}
