package edu.stanford.nlp.tagger.util;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

import java.io.Reader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class makes a table of tag bigram counts and percentages in data.
 * It counts the bigrams (including sentence ends) prints out both a count
 * matrix
 * and percentages.  The only clever part was learning how to format floating
 * point numbers in Java.
 *
 * @author <a href="mailto:manning@cs.stanford.edu">Christopher Manning</a>
 * @version 1.0
 */
public final class CountTagSequences {

  private static final int MAXTAGS = 50;
  private static final int FIELDLENG = 7;

  private static Index<Label> numb = new HashIndex<Label>();

  private static int[][] counts = new int[MAXTAGS][MAXTAGS];
  private static int[] rowcounts = new int[MAXTAGS];


  /**
   * Not meant to be instantiated
   */
  private CountTagSequences() {
  }

  private static void addBigrams(List ts) {
    Iterator it = ts.iterator();
    if (!it.hasNext()) {
      System.err.println("Bung!");
      System.exit(0);
    }
    Label t1 = (Label) it.next();
    while (it.hasNext()) {
      Label t2 = (Label) it.next();
      int i = numb.indexOf(t1, true);
      int j = numb.indexOf(t2, true);
      counts[i][j]++;
      rowcounts[i]++;
      t1 = t2;
    }
  }


  private static void printBigrams() {
    int max = numb.size();

    System.out.println("Tag bigram counts (t1\\t2)");
    System.out.println("==========================");

    System.out.print(StringUtils.pad("", FIELDLENG));
    for (int j = 0; j < max; j++) {
      System.out.print(StringUtils.padLeft(numb.get(j), FIELDLENG));
    }
    System.out.println(StringUtils.padLeft("Total", FIELDLENG));
    for (int i = 0; i < max; i++) {
      System.out.print(StringUtils.pad(numb.get(i), FIELDLENG));
      for (int j = 0; j < max; j++) {
        System.out.print(StringUtils.padLeft(Integer.toString(counts[i][j]), FIELDLENG));
      }
      System.out.println(StringUtils.padLeft(Integer.toString(rowcounts[i]), FIELDLENG));
    }

    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(1);
    nf.setMinimumFractionDigits(1);
    System.out.println();
    System.out.println("Tag bigram percentages (stochastic matrix)");
    System.out.println("==========================================");

    System.out.print(StringUtils.pad("", FIELDLENG));
    for (int j = 0; j < max; j++) {
      System.out.print(StringUtils.padLeft(numb.get(j), FIELDLENG));
    }
    System.out.println();
    for (int i = 0; i < max; i++) {
      System.out.print(StringUtils.pad(numb.get(i), FIELDLENG));
      for (int j = 0; j < max; j++) {
        if (rowcounts[i] == 0) {
          System.out.print(StringUtils.padLeft("-", FIELDLENG));
        } else {
          double percent = (counts[i][j] * 100.0) / rowcounts[i];
          System.out.print(StringUtils.padLeft(nf.format(percent), FIELDLENG));
        }
      }
      System.out.println();
    }


  }

  /**
   * Describe <code>main</code> method here.
   *
   * @param args a <code>String[]</code> value
   */
  public static void main(String[] args) {
    Label boundary = new Tag("<s>");
    // delete empties from the tree!
    Treebank treebank = new DiskTreebank(new TreeReaderFactory() {
      @Override
      public TreeReader newTreeReader(Reader in) {
        return new PennTreeReader(in, new LabeledScoredTreeFactory(), new BobChrisTreeNormalizer());
      }
    });
    treebank.loadPath(args[0]);
    for (Tree t : treebank) {
      // System.out.println(t);
      List<Label> ts = new ArrayList<Label>();
      ts.add(boundary);
      t.preTerminalYield(ts);
      ts.add(boundary);
      // System.out.println(ts);
      addBigrams(ts);
    }
    printBigrams();
  }

}
