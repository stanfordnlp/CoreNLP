package edu.stanford.nlp.process;


import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.GeneralizedCounter;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This class is designed to be used with {@link edu.stanford.nlp.process.WordShapeClassifier} to
 * evaluate word shapes.  It has the following flags:
 * <table>
 * <tr><th>flag<th>description<th>default
 * <tr><td>-f<td>file name<td>required
 * <tr><td>-delim<td>delimiter used in file to seperate columns<td>" "
 * <tr><td>-wordCol<td>the column in the file containing the word<td>0
 * <tr><td>-classCol<td>the column in the file containing the class<td>2
 * <tr><td>-s<td>specifies which classifiers to use (ex. -s 0-2,5,6)<td>0-6
 * <tr><td>-counts<td>0 or 1 to specify whether to display the counts<td>0
 * <tr><td>-classEntropy<td>0 or 1 to specify whether to display info for all word shapes<td>0
 * <tr><td>-avgEntropy<td>0 or 1 to specify whether to display the weighted entropy of the classifer<td>1
 * <tr><td>-one<td>0 or 1 to specify whether to display how many word shapes have only one member<td>1
 * </table>
 * <p/>
 * <br>
 * <br>
 * example:<br>
 * <code>java edu.stanford.nlp.process.EvalWordShapes -f /u/nlp/data/biocreative/task1a/train/train.wsj -classEntropy 1 -d " "</code>
 *
 * @author Jenny Finkel
 */

public class EvalWordShapes {

  /**
   * Print examples of this shape of String.
   */
  private static String printWordShape;


  /**
   * Reads in a file and converts into an array of {@link List}s where each inner
   * {@link List} contains the {@link String} representations of the word
   * and (interned) class.  The delimiter for splitting each line into columns is
   * specified, as are the column numbers for the columns contianing the word and
   * the class.  Lines with too few columns are ignored.
   *
   * @param filename The path to the file being read in.
   * @param wordCol  The column in the file where the word is located.
   * @param classCol The column in the file where the class is located.
   * @param delim    The delimiter on which to split a line into columns.
   * @return An array of {@link List}s where each inner {@link List} contains the
   *         word and (interned) class.
   */
  public static String[][] getWords(String filename, int wordCol, int classCol, String delim) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      List<String[]> words = new ArrayList<String[]>();
      String[] lineArr;
      String[] word;
      while ((line = in.readLine()) != null) {
        lineArr = line.split(delim);
        if (lineArr.length >= Math.max(wordCol, classCol)) {
          word = new String[2];
          word[0] = lineArr[wordCol].intern();
          word[1] = lineArr[classCol].intern();
          words.add(word);
        }
      }
      String[][] w = new String[1][];
      return words.toArray(w);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Puts wordshape/class pairs into a {@link GeneralizedCounter}.  Takes a {@link List} of
   * {@link List}s where each of the inner {@link List}s contains only two items, interned
   * {@link String}s representing the word shape and the class.
   *
   * @param words {@link List} of {@link List}s where each of the inner {@link List}s contains
   *              only two items, interned {@link String}s representing the word shape
   *              and the class.
   * @return A {@link GeneralizedCounter} which contains counts for pairs of word shapes
   *         and classes.
   */
  public static GeneralizedCounter<Object> train(String[][] words, int classifier) {
    GeneralizedCounter<Object> gc = new GeneralizedCounter<Object>(2);
    for (int i = 0; i < words.length; i++) {
      List<Object> wordshape = new ArrayList<Object>(2);
      wordshape.add((edu.stanford.nlp.process.WordShapeClassifier.wordShape(words[i][0], classifier)).intern());
      wordshape.add(words[i][1]);
      gc.incrementCount(wordshape);
      if (printWordShape != null) {
        String shape = edu.stanford.nlp.process.WordShapeClassifier.wordShape(words[i][0], classifier);
        if (shape.equals(printWordShape)) {
          System.out.println(StringUtils.pad(words[i][0], 30) + words[i][1]);
        }
      }
    }
    return gc;
  }

  /**
   * Prints the entropy of all individual wordshapes, along with the counts
   * for each class in each word shape.
   *
   * @param gc the {@link GeneralizedCounter} containing counts for
   *           wordshape/class pairs whose entropies are to be displayed.
   */
  public static void printAllEntropies(GeneralizedCounter<Object> gc) {
    Iterator<Object> it = gc.topLevelKeySet().iterator();
    List<Object> toPrint = new ArrayList<Object>();
    String[] line;
    int[] maxLen = {0, 0};
    while (it.hasNext()) {
      line = new String[3];

      line[0] = (String) it.next();
      maxLen[0] = Math.max(maxLen[0], line[0].length());

      List<Object> l = new ArrayList<Object>(1);
      l.add(line[0]);
      GeneralizedCounter<Object> c = gc.conditionalize(l);

      line[1] = c.toString();
      maxLen[1] = Math.max(maxLen[1], line[1].length());

      line[2] = "" + Counters.entropy(c.counterView());

      toPrint.add(line);
    }

    it = toPrint.iterator();
    StringBuffer tmp;
    while (it.hasNext()) {
      line = (String[]) it.next();
      tmp = new StringBuffer(line[0]);
      while (tmp.length() < maxLen[0]) {
        tmp.append(" ");
      }
      System.out.print(tmp + " \t");

      tmp = new StringBuffer(line[1]);
      while (tmp.length() < maxLen[1]) {
        tmp.append(" ");
      }
      System.out.print(tmp + " \t");

      System.out.println(line[2]);
    }
    System.out.println();
  }

  /**
   * Prints the entropy of all individual wordshapes, along with the counts
   * for each class in each word shape.
   *
   * @param gc the {@link GeneralizedCounter} containing counts for
   *           wordshape/class pairs whose entropies are to be displayed.
   */
  public static void prettyPrintCounts(GeneralizedCounter<Object> gc) {
    Iterator<Object> it = gc.topLevelKeySet().iterator();
    Object correct, guessed;
    while (it.hasNext()) {
      correct = it.next();
      List<Object> l = new ArrayList<Object>(1);
      l.add(correct);
      GeneralizedCounter<Object> c = gc.conditionalize(l);
      System.out.println("---\nCorrect Class: " + correct + " (" + c.totalCount() + ")\n  guessed counts:");
      Iterator<Object> it1 = c.topLevelKeySet().iterator();
      while (it1.hasNext()) {
        guessed = it1.next();
        System.out.println("  " + guessed + ": " + c.getCount(guessed));
      }
    }
    System.out.println();
  }

  /**
   * Creates a {@link GeneralizedCounter} which maps Actual Class to
   * Guessed Class (picks the most likely class based on counts) to
   * count from a {@link GeneralizedCounter} which maps
   * wordshape to actual class to count.
   *
   * @param gc the {@link GeneralizedCounter} containing counts for
   *           wordshape/class pairs.
   */
  public static GeneralizedCounter<Object> getCounts(GeneralizedCounter<Object> gc) {
    GeneralizedCounter<Object> counts = new GeneralizedCounter<Object>(2);
    Iterator<Object> it = gc.topLevelKeySet().iterator();
    String wordshape;
    while (it.hasNext()) {
      wordshape = (String) it.next();
      List<Object> l = new ArrayList<Object>(1);
      l.add(wordshape);
      GeneralizedCounter<Object> c = gc.conditionalize(l);
      Object o = Counters.argmax(c.counterView());
      Iterator<Object> it1 = c.topLevelKeySet().iterator();
      while (it1.hasNext()) {
        List<Object> l1 = new ArrayList<Object>(2);
        Object o1 = it1.next();
        l1.add(o1);
        l1.add(o);
        counts.incrementCount(l1, c.getCount(o1));
      }
    }
    return counts;
  }

  /**
   * Calculates the weighted entropy for the wordshape/class pairs.
   */
  public static double weightedEntropy(GeneralizedCounter<Object> gc) {

    double entropy = 0.0;
    double total = gc.totalCount();
    double weight;
    Set<Object> wordshapes = gc.topLevelKeySet();
    Iterator<Object> it = wordshapes.iterator();
    String wordshape;
    while (it.hasNext()) {
      wordshape = (String) it.next();
      List<Object> l = new ArrayList<Object>(1);
      l.add(wordshape);
      GeneralizedCounter<Object> c = gc.conditionalize(l);
      weight = c.totalCount() / total;
      weight *= Counters.entropy(c.counterView());
      entropy += weight;
    }
    return entropy;

  }

  /*
   * Gets a list of all word shapes with a total count of 1.
   */
  public static int singleClasses(GeneralizedCounter<Object> gc) {

    int count = 0;

    Iterator<Object> it = gc.topLevelKeySet().iterator();
    String wordshape;
    while (it.hasNext()) {
      wordshape = (String) it.next();
      List<Object> l = new ArrayList<Object>(1);
      l.add(wordshape);
      GeneralizedCounter<Object> c = gc.conditionalize(l);
      if (c.totalCount() == 1.0) {
        count++;
      }
      ;
    }
    return count;
  }


  public static void main(String[] args) {

    int numClassifiers = 7;
    boolean showCounts = false, avgEntropy = true, classEntropy = false, one = true;
    String trainFile = "";
    String delim = " ";
    int wordCol = 0;
    int classCol = 2;

    int[] classifiers = new int[numClassifiers];
    for (int i = 0; i < classifiers.length; i++) {
      classifiers[i] = i;
    }

    // evaluate flags
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-f")) {
        trainFile = args[++i];
        continue;
      }

      if (args[i].equalsIgnoreCase("-a")) {
        classifiers = new int[Integer.parseInt(args[++i])];
        for (int j = 0; j < classifiers.length; j++) {
          classifiers[j] = j;
        }
        numClassifiers = classifiers.length;
        continue;
      }
      if (args[i].equalsIgnoreCase("-s")) {
        String[] cs = args[++i].split(",");
        numClassifiers = cs.length;
        for (int j = 0; j < cs.length; j++) {
          int index;
          if ((index = cs[j].indexOf("-")) >= 0) {
            int a = Integer.parseInt(cs[j].substring(0, index));
            int b = Integer.parseInt(cs[j].substring(index + 1));
            numClassifiers += (b - a);
          }
        }
        classifiers = new int[numClassifiers];
        int ci = 0;
        for (int j = 0; j < cs.length; j++) {
          int index;
          if ((index = cs[j].indexOf("-")) >= 0) {
            int a = Integer.parseInt(cs[j].substring(0, index));
            int b = Integer.parseInt(cs[j].substring(index + 1));
            for (int k = a; k <= b; k++) {
              classifiers[ci++] = k;
            }
          } else {
            classifiers[ci++] = Integer.parseInt(cs[j]);
          }
        }
        continue;
      }
      if (args[i].equalsIgnoreCase("-counts")) {
        showCounts = (args[++i].equals("1"));
        continue;
      }
      if (args[i].equalsIgnoreCase("-classEntropy")) {
        classEntropy = (args[++i].equals("1"));
        continue;
      }
      if (args[i].equalsIgnoreCase("-avgEntropy")) {
        avgEntropy = (args[++i].equals("1"));
        continue;
      }
      if (args[i].equalsIgnoreCase("-delim")) {
        delim = args[++i];
        continue;
      }
      if (args[i].equalsIgnoreCase("-wordcol")) {
        wordCol = Integer.parseInt(args[++i]);
        continue;
      }
      if (args[i].equalsIgnoreCase("-classcol")) {
        classCol = Integer.parseInt(args[++i]);
        continue;
      }
      if (args[i].equalsIgnoreCase("-printWordShape")) {
        printWordShape = args[++i];
        continue;
      }
      if (args[i].equalsIgnoreCase("-one")) {
        one = (args[++i].equals("1"));
        continue;
      }
      System.out.println("Unknown Flag: " + args[i]);
      System.exit(1);
    }

    for (int i = 0; i < classifiers.length; i++) {
      System.out.println("\nClassifier " + classifiers[i] + "\n");
      String[][] s = getWords(trainFile, wordCol, classCol, delim);
      GeneralizedCounter<Object> gc = train(s, classifiers[i]);
      if (classEntropy) {
        printAllEntropies(gc);
      }
      if (showCounts) {
        prettyPrintCounts(getCounts(gc));
      }
      if (avgEntropy) {
        System.out.println("weightedEntropy: " + weightedEntropy(gc));
      }
      if (one) {
        System.out.println("numSolitaryWordShapes: " + singleClasses(gc));
      }
    }
  }


}
