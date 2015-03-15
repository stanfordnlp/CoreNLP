package edu.stanford.nlp.sentiment;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.PTBEscapingProcessor;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Generics;

/**
 * Reads the sentiment dataset and writes it to the appropriate files.
 *
 * @author John Bauer
 */
public class ReadSentimentDataset {
  public static Tree convertTree(List<Integer> parentPointers, List<String> sentence, Map<List<String>, Integer> phraseIds, Map<Integer, Double> sentimentScores, PTBEscapingProcessor escaper) {
    int maxNode = 0;
    for (Integer parent : parentPointers) {
      maxNode = Math.max(maxNode, parent);
    }

    Tree[] subtrees = new Tree[maxNode + 1];
    for (int i = 0; i < sentence.size(); ++i) {
      CoreLabel word = new CoreLabel();
      word.setValue(sentence.get(i));
      Tree leaf = new LabeledScoredTreeNode(word);
      subtrees[i] = new LabeledScoredTreeNode(new CoreLabel());
      subtrees[i].addChild(leaf);
    }

    for (int i = sentence.size(); i <= maxNode; ++i) {
      subtrees[i] = new LabeledScoredTreeNode(new CoreLabel());
    }

    boolean[] connected = new boolean[maxNode + 1];
    Tree root = null;
    for (int index = 0; index < parentPointers.size(); ++index) {
      if (parentPointers.get(index) == -1) {
        if (root != null) {
          throw new RuntimeException("Found two roots for sentence " + sentence);
        }
        root = subtrees[index];
      } else {
        // Walk up the tree structure to make sure that leftmost
        // phrases are added first.  Otherwise, if the numbers are
        // inverted, we might get the right phrase added to a parent
        // first, resulting in "case zero in this", for example,
        // instead of "in this case zero"
        // Note that because we keep track of which ones are already
        // connected, we process this at most once per parent, so the
        // overall construction time is still efficient.
        connect(parentPointers, subtrees, connected, index);
      }
    }

    for (int i = 0; i <= maxNode; ++i) {
      List<Tree> leaves = subtrees[i].getLeaves();
      List<String> words = CollectionUtils.transformAsList(leaves, new Function<Tree, String>() { 
          public String apply(Tree tree) { return tree.label().value(); }
        });
      Integer phraseId = phraseIds.get(words);
      if (phraseId == null) {
        throw new RuntimeException("Could not find phrase id for phrase " + sentence);
      }
      // TODO: should we make this an option?  Perhaps we want cases
      // where the trees have the phrase id and not their class
      Double score = sentimentScores.get(phraseId);
      if (score == null) {
        throw new RuntimeException("Could not find sentiment score for phrase id " + phraseId);
      }
      // TODO: make this a numClasses option
      int classLabel = Math.round((float) Math.floor(score * 5.0));
      if (classLabel > 4) {
        classLabel = 4;
      }
      subtrees[i].label().setValue(Integer.toString(classLabel));
    }

    for (int i = 0; i < sentence.size(); ++i) {
      Tree leaf = subtrees[i].children()[0];
      leaf.label().setValue(escaper.escapeString(leaf.label().value()));
    }

    return root;
  }

  private static void connect(List<Integer> parentPointers, Tree[] subtrees, boolean[] connected, int index) {
    if (connected[index]) {
      return;
    }
    if (parentPointers.get(index) < 0) {
      return;
    }
    subtrees[parentPointers.get(index)].addChild(subtrees[index]);
    connected[index] = true;
    connect(parentPointers, subtrees, connected, parentPointers.get(index));
  }

  private static void writeTrees(String filename, List<Tree> trees, List<Integer> treeIds) {
    try {
      FileOutputStream fos = new FileOutputStream(filename);
      BufferedWriter bout = new BufferedWriter(new OutputStreamWriter(fos));
      
      for (Integer id : treeIds) {
        bout.write(trees.get(id).toString());
        bout.write("\n");
      }
      bout.flush();
      fos.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * This program converts the format of the Sentiment data set
   * prepared by Richard, Jean, etc. into trees readable with the
   * normal TreeReaders.
   * <br>
   * An example command line is
   * <br>
   * <code>java edu.stanford.nlp.sentiment.ReadSentimentDataset -dictionary stanfordSentimentTreebank/dictionary.txt -sentiment stanfordSentimentTreebank/sentiment_labels.txt -tokens stanfordSentimentTreebank/SOStr.txt -parse stanfordSentimentTreebank/STree.txt  -split stanfordSentimentTreebank/datasetSplit.txt  -train train.txt -dev dev.txt -test test.txt</code>
   * <br>
   * The arguments are as follows: <br>
   * <code>-dictionary</code>, <code>-sentiment</code>,
   * <code>-tokens</code>, <code>-parse</code>, <code>-split</code>
   * Path to the corresponding files from the dataset <br>
   * <code>-train</code>, <code>-dev</code>, <code>-test</code>
   * Paths for saving the corresponding output files <br>
   * Each of these arguments is required.
   */
  public static void main(String[] args) {
    String dictionaryFilename = null;
    String sentimentFilename = null;
    String tokensFilename = null;
    String parseFilename = null;
    String splitFilename = null;

    String trainFilename = null;
    String devFilename = null;
    String testFilename = null;

    int argIndex = 0;
    while (argIndex < args.length) {
      if (args[argIndex].equalsIgnoreCase("-dictionary")) {
        dictionaryFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-sentiment")) {
        sentimentFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tokens")) {
        tokensFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parse")) {
        parseFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-split")) {
        splitFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-train")) {
        trainFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-dev")) {
        devFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-test")) {
        testFilename = args[argIndex + 1];
        argIndex += 2;
      } else {
        System.err.println("Unknown argument " + args[argIndex]);
        System.exit(2);
      }
    }

    // Sentence file is formatted
    //   w1|w2|w3...
    List<List<String>> sentences = Generics.newArrayList();
    for (String line : IOUtils.readLines(tokensFilename, "utf-8")) {
      String[] sentence = line.split("\\|");
      sentences.add(Arrays.asList(sentence));
    }

    // Split and read the phrase ids file.  This file is in the format
    //   w1 w2 w3 ... | id
    Map<List<String>, Integer> phraseIds = Generics.newHashMap();
    for (String line : IOUtils.readLines(dictionaryFilename)) {
      String[] pieces = line.split("\\|");
      String[] sentence = pieces[0].split(" ");
      Integer id = Integer.valueOf(pieces[1]);
      phraseIds.put(Arrays.asList(sentence), id);
    }

    // Split and read the sentiment scores file.  Each line of this
    // file is of the format:
    //   phrasenum | score
    Map<Integer, Double> sentimentScores = Generics.newHashMap();
    for (String line : IOUtils.readLines(sentimentFilename)) {
      if (line.startsWith("phrase")) {
        continue;
      }
      String[] pieces = line.split("\\|");
      Integer id = Integer.valueOf(pieces[0]);
      Double score = Double.valueOf(pieces[1]);
      sentimentScores.put(id, score);
    }

    // Read lines from the tree structure file.  This is a file of parent pointers for each tree.
    int index = 0;
    PTBEscapingProcessor escaper = new PTBEscapingProcessor();
    List<Tree> trees = Generics.newArrayList();
    for (String line : IOUtils.readLines(parseFilename)) {
      String[] pieces = line.split("\\|");
      List<Integer> parentPointers = CollectionUtils.transformAsList(Arrays.asList(pieces), new Function<String, Integer>() { 
          public Integer apply(String arg) { return Integer.valueOf(arg) - 1; }
        });
      Tree tree = convertTree(parentPointers, sentences.get(index), phraseIds, sentimentScores, escaper);
      ++index;
      trees.add(tree);
    }

    Map<Integer, List<Integer>> splits = Generics.newHashMap();
    splits.put(1, Generics.<Integer>newArrayList());
    splits.put(2, Generics.<Integer>newArrayList());
    splits.put(3, Generics.<Integer>newArrayList());
    for (String line : IOUtils.readLines(splitFilename)) {
      if (line.startsWith("sentence_index")) {
        continue;
      }
      String[] pieces = line.split(",");
      Integer treeId = Integer.valueOf(pieces[0]) - 1;
      Integer fileId = Integer.valueOf(pieces[1]);
      splits.get(fileId).add(treeId);
    }

    writeTrees(trainFilename, trees, splits.get(1));
    writeTrees(devFilename, trees, splits.get(2));
    writeTrees(testFilename, trees, splits.get(3));
  }
}
