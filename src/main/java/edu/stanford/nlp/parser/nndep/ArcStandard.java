package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an arc-standard transition-based dependency parsing system
 * (Nivre, 2004).
 *
 * @author Danqi Chen
 */
public class ArcStandard extends ParsingSystem {

  private boolean singleRoot = true;

  public ArcStandard(TreebankLanguagePack tlp, List<String> labels, boolean verbose) {
    super(tlp, labels, makeTransitions(labels), verbose);
  }

  @Override
  public boolean isTerminal(Configuration c) {
    return (c.getStackSize() == 1 && c.getBufferSize() == 0);
  }

  /**
   * Generate all possible transitions which this parsing system can
   * take for any given configuration.
   *
   * @return A List of the transitions
   */
  private static List<String> makeTransitions(List<String> labels) {
    List<String> moves = new ArrayList<>();

    // TODO store these as objects!
    for (String label : labels) {
      moves.add("L(" + label + ')');
    }
    for (String label : labels) {
      moves.add("R(" + label + ')');
    }
    moves.add("S");
    return moves;
  }

  @Override
  public Configuration initialConfiguration(CoreMap s) {
    Configuration c = new Configuration(s);
    int length = s.get(CoreAnnotations.TokensAnnotation.class).size();

    // For each token, add dummy elements to the configuration's tree
    // and add the words onto the buffer
    for (int i = 1; i <= length; ++i) {
      c.tree.add(Config.NONEXIST, Config.UNKNOWN);
      c.buffer.add(i);
    }

    // Put the ROOT node on the stack
    c.stack.add(0);

    return c;
  }

  @Override
  public boolean canApply(Configuration c, String t) {
    if (t.startsWith("L") || t.startsWith("R")) {
      String label = t.substring(2, t.length() - 1);
      int h = t.startsWith("L") ? c.getStack(0) : c.getStack(1);
      if (h < 0) return false;
      if (h == 0 && !label.equals(rootLabel)) return false;
      if (h > 0 && label.equals(rootLabel)) return false;
    }

    int nStack = c.getStackSize();
    int nBuffer = c.getBufferSize();

    if (t.startsWith("L"))
      return nStack > 2;
    else if (t.startsWith("R")) {
      if (singleRoot)
        return (nStack > 2) || (nStack == 2 && nBuffer == 0);
      else
        return nStack >= 2;
    } else
      return nBuffer > 0;
  }

  @Override
  public void apply(Configuration c, String t) {
    int w1 = c.getStack(1);
    int w2 = c.getStack(0);
    if (t.startsWith("L")) {
      c.addArc(w2, w1, t.substring(2, t.length() - 1));
      c.removeSecondTopStack();
    } else if (t.startsWith("R")) {
      c.addArc(w1, w2, t.substring(2, t.length() - 1));
      c.removeTopStack();
    } else c.shift();
  }

  // O(n) implementation
  @Override
  public String getOracle(Configuration c, DependencyTree dTree) {
    int w1 = c.getStack(1);
    int w2 = c.getStack(0);
    if (w1 > 0 && dTree.getHead(w1) == w2) {
      return "L(" + dTree.getLabel(w1) + ')';
    } else if (w1 >= 0 && dTree.getHead(w2) == w1 && !c.hasOtherChild(w2, dTree)) {
      return "R(" + dTree.getLabel(w2) + ')';
    } else {
      return "S";
    }
  }

  // NOTE: need to check the correctness again.
  private static boolean canReach(Configuration c, DependencyTree dTree) {
    int n = c.getSentenceSize();
    for (int i = 1; i <= n; ++i)
      if (c.getHead(i) != Config.NONEXIST && c.getHead(i) != dTree.getHead(i))
        return false;

    boolean[] inBuffer = new boolean[n + 1];
    boolean[] depInList = new boolean[n + 1];

    int[] leftL = new int[n + 2];
    int[] rightL = new int[n + 2];

    for (int i = 0; i < c.getBufferSize(); ++i)
      inBuffer[c.buffer.get(i)] = true;

    int nLeft = c.getStackSize();
    for (int i = 0; i < nLeft; ++i) {
      int x = c.stack.get(i);
      leftL[nLeft - i] = x;
      if (x > 0) depInList[dTree.getHead(x)] = true;
    }

    int nRight = 1;
    rightL[nRight] = leftL[1];
    for (int i = 0; i < c.getBufferSize(); ++i) {
      // boolean inList = false;
      int x = c.buffer.get(i);
      if (!inBuffer[dTree.getHead(x)] || depInList[x]) {
        rightL[++nRight] = x;
        depInList[dTree.getHead(x)] = true;
      }
    }

    int[][] g = new int[nLeft + 1][nRight + 1];
    for (int i = 1; i <= nLeft; ++i)
      for (int j = 1; j <= nRight; ++j)
        g[i][j] = -1;

    g[1][1] = leftL[1];
    for (int i = 1; i <= nLeft; ++i)
      for (int j = 1; j <= nRight; ++j)
        if (g[i][j] != -1) {
          int x = g[i][j];
          if (j < nRight && dTree.getHead(rightL[j + 1]) == x) g[i][j + 1] = x;
          if (j < nRight && dTree.getHead(x) == rightL[j + 1]) g[i][j + 1] = rightL[j + 1];
          if (i < nLeft && dTree.getHead(leftL[i + 1]) == x) g[i + 1][j] = x;
          if (i < nLeft && dTree.getHead(x) == leftL[i + 1]) g[i + 1][j] = leftL[i + 1];
        }
    return g[nLeft][nRight] != -1;
  }

  @Override
  public boolean isOracle(Configuration c, String t, DependencyTree dTree) {
    if (!canApply(c, t))
      return false;

    if (t.startsWith("L") && !dTree.getLabel(c.getStack(1)).equals(t.substring(2, t.length() - 1)))
      return false;

    if (t.startsWith("R") && !dTree.getLabel(c.getStack(0)).equals(t.substring(2, t.length() - 1)))
      return false;

    Configuration ct = new Configuration(c);
    apply(ct, t);
    return canReach(ct, dTree);
  }

}
