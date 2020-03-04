package edu.stanford.nlp.trees;

import junit.framework.TestCase;

import java.io.*;

/**
 * Start testing various output mechanisms for TreePrint.
 *
 * @author John Bauer
 */
public class TreePrintTest extends TestCase {
  private String printTree(Tree tree, String mode) {
    TreePrint treePrint = new TreePrint(mode);
    StringWriter writer = new StringWriter();
    PrintWriter wrapped = new PrintWriter(writer);
    treePrint.printTree(tree, wrapped);
    wrapped.close();
    String out = writer.toString();
    return out;
  }

  public void testConll2007() {
    Tree test = Tree.valueOf("((S (NP (PRP It)) (VP (VBZ is) (RB not) (ADJP (JJ normal)) (SBAR (IN for) (S (NP (NNS dogs)) (VP (TO to) (VP (VB be) (VP (VBG vomiting)))))))))");
    String[] words = { "It", "is", "not", "normal", "for", "dogs", "to", "be", "vomiting" };
    String[] tags = { "PRP", "VBZ", "RB", "JJ", "IN", "NNS", "TO", "VB", "VBG" };

    String out = printTree(test, "conll2007");

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

  public void testPenn() {
    Tree test = Tree.valueOf("((S (NP (PRP It)) (VP (VBZ is) (RB not) (ADJP (JJ normal)) (SBAR (IN for) (S (NP (NNS dogs)) (VP (TO to) (VP (VB be) (VP (VBG vomiting)))))))))");
    String expected = "(ROOT\n" +
      "  (S\n" +
      "    (NP (PRP It))\n" +
      "    (VP (VBZ is) (RB not)\n" +
      "      (ADJP (JJ normal))\n" +
      "      (SBAR (IN for)\n" +
      "        (S\n" +
      "          (NP (NNS dogs))\n" +
      "          (VP (TO to)\n" +
      "            (VP (VB be)\n" +
      "              (VP (VBG vomiting)))))))))\n\n";
    expected = expected.replace("\n", System.lineSeparator());
    String out = printTree(test, "penn");
    assertEquals(expected, out);

    test = Tree.valueOf("(-LRB- -RRB-)");
    expected = "(-LRB- -RRB-)\n\n";
    expected = expected.replace("\n", System.lineSeparator());
    out = printTree(test, "penn");
    assertEquals(expected, out);
  }
}
