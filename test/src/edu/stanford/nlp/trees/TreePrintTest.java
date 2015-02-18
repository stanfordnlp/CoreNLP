package edu.stanford.nlp.trees;

import junit.framework.TestCase;

import java.io.*;

/**
 * Start testing various output mechanisms for TreePrint.  So far, just one
 *
 * @author John Bauer
 */
public class TreePrintTest extends TestCase {
  public void testConll2007() {
    Tree test = Tree.valueOf("((S (NP (PRP It)) (VP (VBZ is) (RB not) (ADJP (JJ normal)) (SBAR (IN for) (S (NP (NNS dogs)) (VP (TO to) (VP (VB be) (VP (VBG vomiting)))))))))");
    String[] words = { "It", "is", "not", "normal", "for", "dogs", "to", "be", "vomiting" };
    String[] tags = { "PRP", "VBZ", "RB", "JJ", "IN", "NNS", "TO", "VB", "VBG" };

    TreePrint treePrint = new TreePrint("conll2007");
    StringWriter writer = new StringWriter();
    PrintWriter wrapped = new PrintWriter(writer);
    treePrint.printTree(test, wrapped);
    wrapped.close();
    String out = writer.toString();


    String[] lines = out.trim().split("\n");
    for (int i = 0; i < lines.length; ++i) {
      String[] pieces = lines[i].trim().split("\\s+");
      int lineNum = Integer.valueOf(pieces[0]);
      assertEquals((i + 1), lineNum);
      assertEquals(words[i], pieces[1]);
      assertEquals(tags[i], pieces[3]);
      assertEquals(tags[i], pieces[4]);
    }
  }
}
