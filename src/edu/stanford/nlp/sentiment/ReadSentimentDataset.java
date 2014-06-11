package edu.stanford.nlp.sentiment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.PTBEscapingProcessor;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Generics;

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

  // TODO: add the ability to split the training, dev and test data
  public static void main(String[] args) {
    String dictionaryFilename = null;
    String sentimentFilename = null;
    String tokensFilename = null;
    String parseFilename = null;


    // TODO: document arguments
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
      } else {
        System.err.println("Unknown argument " + args[argIndex]);
        System.exit(2);
      }
    }

    List<List<String>> sentences = Generics.newArrayList();
    for (String line : IOUtils.readLines(tokensFilename, "utf-8")) {
      String[] sentence = line.split("\\|");
      sentences.add(Arrays.asList(sentence));
    }

    Map<List<String>, Integer> phraseIds = Generics.newHashMap();
    for (String line : IOUtils.readLines(dictionaryFilename)) {
      String[] pieces = line.split("\\|");
      String[] sentence = pieces[0].split(" ");
      Integer id = Integer.valueOf(pieces[1]);
      phraseIds.put(Arrays.asList(sentence), id);
    }


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

    int index = 0;
    PTBEscapingProcessor escaper = new PTBEscapingProcessor();
    for (String line : IOUtils.readLines(parseFilename)) {
      String[] pieces = line.split("\\|");
      List<Integer> parentPointers = CollectionUtils.transformAsList(Arrays.asList(pieces), new Function<String, Integer>() { 
          public Integer apply(String arg) { return Integer.valueOf(arg) - 1; }
        });
      Tree tree = convertTree(parentPointers, sentences.get(index), phraseIds, sentimentScores, escaper);
      ++index;

      System.out.println(tree);
    }
  }
}
