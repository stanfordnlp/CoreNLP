package edu.stanford.nlp.sentiment;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * @author John Bauer
 * @author Richard Socher
 */
public class BuildBinarizedDataset  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(BuildBinarizedDataset.class);

  private BuildBinarizedDataset() {} // static methods only

  /**
   * Sets all of the labels on a tree to the given default value.
   */
  public static void setUnknownLabels(Tree tree, Integer defaultLabel) {
    if (tree.isLeaf()) {
      return;
    }

    for (Tree child : tree.children()) {
      setUnknownLabels(child, defaultLabel);
    }

    tree.label().setValue(defaultLabel.toString());
  }

  public static void setPredictedLabels(Tree tree) {
    if (tree.isLeaf()) {
      return;
    }

    for (Tree child : tree.children()) {
      setPredictedLabels(child);
    }

    tree.label().setValue(Integer.toString(RNNCoreAnnotations.getPredictedClass(tree)));
  }

  public static void extractLabels(Map<Pair<Integer, Integer>, String> spanToLabels, List<HasWord> tokens, String line) {
    String[] pieces = line.trim().split("\\s+");
    if (pieces.length == 0) {
      return;
    }
    if (pieces.length == 1) {
      String error = "Found line with label " + line + " but no tokens to associate with that line";
      throw new RuntimeException(error);
    }

    //TODO: BUG: The pieces are tokenized differently than the splitting, e.g., on possessive markers as in "actors' expenses"
    for (int i = 0; i < tokens.size() - pieces.length + 2; ++i) {
      boolean found = true;
      for (int j = 1; j < pieces.length; ++j) {
        if (!tokens.get(i + j - 1).word().equals(pieces[j])) {
          found = false;
          break;
        }
      }
      if (found) {
        spanToLabels.put(new Pair<>(i, i + pieces.length - 1), pieces[0]);
      }
    }
  }

  public static boolean setSpanLabel(Tree tree, Pair<Integer, Integer> span, String value) {
    if (!(tree.label() instanceof CoreLabel)) {
      throw new AssertionError("Expected CoreLabels");
    }
    CoreLabel label = (CoreLabel) tree.label();
    if (label.get(CoreAnnotations.BeginIndexAnnotation.class).equals(span.first) &&
        label.get(CoreAnnotations.EndIndexAnnotation.class).equals(span.second)) {
      label.setValue(value);
      return true;
    }
    if (label.get(CoreAnnotations.BeginIndexAnnotation.class) > span.first &&
        label.get(CoreAnnotations.EndIndexAnnotation.class) < span.second) {
      return false;
    }
    for (Tree child : tree.children()) {
      if (setSpanLabel(child, span, value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Turns a text file into trees for use in a RNTN classifier such as
   * the treebank used in the Sentiment project.
   * <br>
   * The expected input file is one sentence per line, with sentences
   * separated by blank lines. The first line has the main label of the sentence together with the full sentence.
   * Lines after the first sentence line but before
   * the blank line will be treated as labeled sub-phrases.  The
   * labels should start with the label and then contain a list of
   * tokens the label applies to. All phrases that do not have their own label will take on the main sentence label!
   *  For example:
   * <br>
   * <code>
   * 1 Today is not a good day.<br>
   * 3 good<br>
   * 3 good day <br>
   * 3 a good day <br>
   * <br>
   * (next block starts here) <br>
   * </code>
   * <br>
   * If you have an example sentence you want to label, you will need
   * to manually label the subtrees from there.  For example, to build
   * a 5 class dataset which matches the existing datasets, you would
   * label the very negative phrases with 0, neutral phrases with 2,
   * very positive phrases with 4.  The binary label dataset uses 0
   * for negative, 1 for positive, and -1 for unlabeled (which can
   * mean neutral, although the binary model will not predict
   * neutral).
   * <br>
   * In order to determine which sub-phrases would need labeling, you
   * can run the sentences through the same parser used to turn the
   * sentences into trees.  For example, in the case of using the
   * englishPCFG model, you can look at the main class of
   * edu.stanford.nlp.parser.lexparser.LexicalizedParser .  You will
   * definitely want to provide a label for the entire sentence.  Any
   * subphrases which have a significantly different sentiment should
   * be labeled, such as the previous example of "not a good day" vs
   * "a good day".
   * <br>
   * Although it would be excessive to do so, a list of ALL of the
   * subphrases contained in a parsed tree can be produced by first
   * running the parser, then using the tool
   * edu.stanford.nlp.trees.OutputSubtrees
   * <br>
   * By default the englishPCFG parser is used.  This can be changed
   * with the {@code -parserModel} flag.  Specify an input file
   * with {@code -input}.
   * <br>
   * If a sentiment model is provided with -sentimentModel, that model
   * will be used to prelabel the sentences.  Any spans with given
   * labels will then be used to adjust those labels.
   */
  public static void main(String[] args) {
    CollapseUnaryTransformer transformer = new CollapseUnaryTransformer();

    String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";

    String inputPath = null;

    String sentimentModelPath = null;
    SentimentModel sentimentModel = null;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-input")) {
        inputPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parserModel")) {
        parserModel = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-sentimentModel")) {
        sentimentModelPath = args[argIndex + 1];
        argIndex += 2;
      } else {
        log.info("Unknown argument " + args[argIndex]);
        System.exit(2);
      }
    }

    if (inputPath == null) {
      throw new IllegalArgumentException("Must specify input file with -input");
    }

    LexicalizedParser parser = LexicalizedParser.loadModel(parserModel);
    TreeBinarizer binarizer = TreeBinarizer.simpleTreeBinarizer(parser.getTLPParams().headFinder(), parser.treebankLanguagePack());

    if (sentimentModelPath != null) {
      sentimentModel = SentimentModel.loadSerialized(sentimentModelPath);
    }

    String text = IOUtils.slurpFileNoExceptions(inputPath);
    String[] chunks = text.split("\\n\\s*\\n+"); // need blank line to make a new chunk

    for (String chunk : chunks) {
      if (chunk.trim().isEmpty()) {
        continue;
      }
      // The expected format is that line 0 will be the text of the
      // sentence, and each subsequence line, if any, will be a value
      // followed by the sequence of tokens that get that value.

      // Here we take the first line and tokenize it as one sentence.
      String[] lines = chunk.trim().split("\\n");
      String sentence = lines[0];
      StringReader sin = new StringReader(sentence);
      DocumentPreprocessor document = new DocumentPreprocessor(sin);
      document.setSentenceFinalPuncWords(new String[] {"\n"});
      List<HasWord> tokens = document.iterator().next();
      Integer mainLabel = Integer.valueOf(tokens.get(0).word());
      //System.out.print("Main Sentence Label: " + mainLabel.toString() + "; ");
      tokens = tokens.subList(1, tokens.size());
      //log.info(tokens);

      Map<Pair<Integer, Integer>, String> spanToLabels = Generics.newHashMap();
      for (int i = 1; i < lines.length; ++i) {
        extractLabels(spanToLabels, tokens, lines[i]);
      }

      // TODO: add an option which treats the spans as constraints when parsing

      Tree tree = parser.apply(tokens);
      Tree binarized = binarizer.transformTree(tree);
      Tree collapsedUnary = transformer.transformTree(binarized);

      // if there is a sentiment model for use in prelabeling, we
      // label here and then use the user given labels to adjust
      if (sentimentModel != null) {
        Trees.convertToCoreLabels(collapsedUnary);
        SentimentCostAndGradient scorer = new SentimentCostAndGradient(sentimentModel, null);
        scorer.forwardPropagateTree(collapsedUnary);
        setPredictedLabels(collapsedUnary);
      } else {
        setUnknownLabels(collapsedUnary, mainLabel);
      }

      Trees.convertToCoreLabels(collapsedUnary);
      collapsedUnary.indexSpans();

      for (Map.Entry<Pair<Integer, Integer>, String> pairStringEntry : spanToLabels.entrySet()) {
        setSpanLabel(collapsedUnary, pairStringEntry.getKey(), pairStringEntry.getValue());
      }

      System.out.println(collapsedUnary);
      //System.out.println();
    }
  } // end main

}
