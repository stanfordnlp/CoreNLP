
package edu.stanford.nlp.parser.nndep;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.CoreMap;
import java.util.*;
import java.io.*;


/**
 *
 *  Some utility functions
 *
 *  @author Danqi Chen
 *  @author Jon Gauthier
 */

public class Util  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Util.class);

  private Util() {} // static methods

  private static Random random;

  /**
   * Normalize word embeddings by setting mean = rMean, std = rStd
   */
  public static double[][] scaling(double[][] A, double rMean, double rStd) {
    int count = 0;
    double mean = 0.0;
    double std = 0.0;
    for (double[] aA : A)
      for (int j = 0; j < aA.length; ++j) {
        count += 1;
        mean += aA[j];
        std += aA[j] * aA[j];
      }
    mean = mean / count;
    std = Math.sqrt(std / count - mean * mean);

    System.err.printf("Scaling word embeddings:");
    System.err.printf("(mean = %.2f, std = %.2f) -> (mean = %.2f, std = %.2f)", mean, std, rMean, rStd);

    double[][] rA = new double[A.length][A[0].length];
    for (int i = 0; i < rA.length; ++ i)
      for (int j = 0; j < rA[i].length; ++ j)
        rA[i][j] = (A[i][j] - mean) * rStd / std + rMean;
    return rA;
  }

  /**
   *  Normalize word embeddings by setting mean = 0, std = 1
   */
  public static double[][] scaling(double[][] A) {
    return scaling(A, 0.0, 1.0);
  }

  // return strings sorted by frequency, and filter out those with freq. less than cutOff.

  /**
   * Build a dictionary of words collected from a corpus.
   *
   * <p>Filters out words with a frequency below the given {@code cutOff}.
   *
   * @return Words sorted by decreasing frequency, filtered to remove any words with a frequency
   *     below {@code cutOff}
   */
  public static List<String> generateDict(List<String> str, int cutOff) {
    Counter<String> freq = new IntCounter<>();
    for (String aStr : str)
      freq.incrementCount(aStr);

    List<String> keys = Counters.toSortedList(freq, false);
    List<String> dict = new ArrayList<>();
    keys.stream()
        .filter(word -> freq.getCount(word) >= cutOff)
        .forEach(
            word -> {
              dict.add(word);
            });
    return dict;
  }

  public static List<String> generateDict(List<String> str) {
    return generateDict(str, 1);
  }

  /**
   * @return Shared random generator used in this package
   */
  static Random getRandom() {
    if (random != null)
      return random;
    else
      return getRandom(System.currentTimeMillis());
  }

  /**
   * Set up shared random generator to use the given seed.
   *
   * @return Shared random generator object
   */
  static Random getRandom(long seed) {
    random = new Random(seed);
    System.err.printf("Random generator initialized with seed %d%n", seed);

    return random;
  }

  public static <T> List<T> getRandomSubList(List<T> input, int subsetSize)
  {
    int inputSize = input.size();
    if (subsetSize > inputSize)
      subsetSize = inputSize;

    Random random = getRandom();
    for (int i = 0; i < subsetSize; i++)
    {
      int indexToSwap = i + random.nextInt(inputSize - i);
      T temp = input.get(i);
      input.set(i, input.get(indexToSwap));
      input.set(indexToSwap, temp);
    }
    return input.subList(0, subsetSize);
  }

  // TODO replace with GrammaticalStructure#readCoNLLGrammaticalStructureCollection
  public static void loadConllFile(String inFile, List<CoreMap> sents, List<DependencyTree> trees, boolean unlabeled, boolean cPOS)
  {
    CoreLabelTokenFactory tf = new CoreLabelTokenFactory(false);

    BufferedReader reader = null;
    try {
      reader = IOUtils.readerFromString(inFile);

      List<CoreLabel> sentenceTokens = new ArrayList<>();
      DependencyTree tree = new DependencyTree();

      for (String line : IOUtils.getLineIterable(reader, false)) {
        String[] splits = line.split("\t");
        if (splits.length < 10) {
          if (sentenceTokens.size() > 0) {
            trees.add(tree);
            CoreMap sentence = new CoreLabel();
            sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
            sents.add(sentence);
            tree = new DependencyTree();
            sentenceTokens = new ArrayList<>();
          }
        } else {
          String word = splits[1],
                  pos = cPOS ? splits[3] : splits[4],
                  depType = splits[7];

          int head = -1;
          try {
            head = Integer.parseInt(splits[6]);
          }
          catch (NumberFormatException e) {
            continue;
          }

          CoreLabel token = tf.makeToken(word, 0, 0);
          token.setTag(pos);
          token.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, head);
          token.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, depType);
          sentenceTokens.add(token);

          if (!unlabeled)
            tree.add(head, depType);
          else
            tree.add(head, Config.UNKNOWN);
        }
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(reader);
    }
  }

  public static void loadConllFile(String inFile, List<CoreMap> sents, List<DependencyTree> trees)
  {
    loadConllFile(inFile, sents, trees, false, false);
  }

  public static void writeConllFile(String outFile, List<CoreMap> sentences, List<DependencyTree> trees)
  {
    try
    {
      PrintWriter output = IOUtils.getPrintWriter(outFile);

      for (int i = 0; i < sentences.size(); i++)
      {
        CoreMap sentence = sentences.get(i);
        DependencyTree tree = trees.get(i);

        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

        for (int j = 1, size = tokens.size(); j <= size; ++j)
        {
          CoreLabel token = tokens.get(j - 1);
          output.printf("%d\t%s\t_\t%s\t%s\t_\t%d\t%s\t_\t_%n",
              j, token.word(), token.tag(), token.tag(),
              tree.getHead(j), tree.getLabel(j));
        }
        output.println();
      }
      output.close();
    }
    catch (Exception e) {
      throw new RuntimeIOException(e);
    }
  }

  public static void printTreeStats(String str, List<DependencyTree> trees)
  {
    log.info(Config.SEPARATOR + " " + str);
    int nTrees = trees.size();
    int nonTree = 0;
    int multiRoot = 0;
    int nonProjective = 0;
    for (DependencyTree tree : trees) {
      if (!tree.isTree())
        ++nonTree;
      else
      {
        if (!tree.isProjective())
          ++nonProjective;
        if (!tree.isSingleRoot())
          ++multiRoot;
      }
    }
    System.err.printf("#Trees: %d%n", nTrees);
    System.err.printf("%d tree(s) are illegal (%.2f%%).%n", nonTree, nonTree * 100.0 / nTrees);
    System.err.printf("%d tree(s) are legal but have multiple roots (%.2f%%).%n", multiRoot, multiRoot * 100.0 / nTrees);
    System.err.printf("%d tree(s) are legal but not projective (%.2f%%).%n", nonProjective, nonProjective * 100.0 / nTrees);
  }

  public static void printTreeStats(List<DependencyTree> trees)
  {
    printTreeStats("", trees);
  }
}
