package edu.stanford.nlp.sentiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Properties;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

/**
 * A wrapper class which creates a suitable pipeline for the sentiment
 * model and processes raw text.
 *<br>
 * The main program has the following options: <br>
 * <code>-parserModel</code> Which parser model to use, defaults to englishPCFG.ser.gz <br>
 * <code>-sentimentModel</code> Which sentiment model to use, defaults to sentiment.ser.gz <br>
 * <code>-file</code> Which file to process. <br>
 * <code>-stdin</code> Read one line at a time from stdin. <br>
 * <code>-output</code> pennTrees: Output trees with scores at each binarized node.  vectors: Number tree nodes and print out the vectors.  Defaults to printing just the root. <br>
 *
 * @author John Bauer
 */
public class SentimentPipeline {
  private static final NumberFormat NF = new DecimalFormat("0.0000");

  static enum Output {
    PENNTREES, VECTORS, ROOT
  }

  /**
   * Sets the labels on the tree (except the leaves) to be the integer
   * value of the sentiment prediction.  Makes it easy to print out
   * with Tree.toString()
   */
  static void setSentimentLabels(Tree tree) {
    if (tree.isLeaf()) {
      return;
    }

    for (Tree child : tree.children()) {
      setSentimentLabels(child);
    }

    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("Required a tree with CoreLabels");
    }
    CoreLabel cl = (CoreLabel) label;
    cl.setValue(Integer.toString(RNNCoreAnnotations.getPredictedClass(tree)));
  }

  /**
   * Sets the labels on the tree to be the indices of the nodes.
   * Starts counting at the root and does a postorder traversal.
   */
  static int setIndexLabels(Tree tree, int index) {
    if (tree.isLeaf()) {
      return index;
    }

    tree.label().setValue(Integer.toString(index));
    index++;
    for (Tree child : tree.children()) {
      index = setIndexLabels(child, index);
    }
    return index;
  }

  /**
   * Outputs the vectors from the tree.  Counts the tree nodes the
   * same as setIndexLabels.
   */
  static int outputTreeVectors(Tree tree, int index) {
    if (tree.isLeaf()) {
      return index;
    }

    System.out.print("  " + index + ":");
    SimpleMatrix vector = RNNCoreAnnotations.getNodeVector(tree);
    for (int i = 0; i < vector.getNumElements(); ++i) {
      System.out.print("  " + NF.format(vector.get(i)));
    }
    System.out.println();
    index++;
    for (Tree child : tree.children()) {
      index = outputTreeVectors(child, index);
    }
    return index;
  }

  /**
   * Outputs a tree using the output style requested
   */
  static void outputTree(Tree tree, Output output) {
    switch (output) {
    case PENNTREES: {
      Tree copy = tree.deepCopy();
      setSentimentLabels(copy);
      System.out.println(copy);
      break;
    }
    case VECTORS: {
      Tree copy = tree.deepCopy();
      setIndexLabels(copy, 0);
      System.out.println(copy);
      outputTreeVectors(tree, 0);
      break;
    }
    case ROOT:
      int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
      System.out.println("  " + SentimentUtils.sentimentString(sentiment));
      break;
    default:
      throw new IllegalArgumentException("Unknown output format " + output);
    }
  }

  public static void help() {
    System.err.println("Known command line arguments:");
    System.err.println("  -sentimentModel <model>: Which model to use");
    System.err.println("  -parserModel <model>: Which parser to use");
    System.err.println("  -file <filename>: Which file to process");
    System.err.println("  -stdin: Process stdin instead of a file");
    System.err.println("  -output <format>: Which format to output, PENNTREES, VECTOR, or ROOT ");
  }

  public static void main(String[] args) throws IOException {
    String parserModel = null;
    String sentimentModel = null;

    String filename = null;
    boolean stdin = false;

    Output output = Output.ROOT;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-sentimentModel")) {
        sentimentModel = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parserModel")) {
        parserModel = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-file")) {
        filename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-stdin")) {
        stdin = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        String format = args[argIndex + 1];
        output = Output.valueOf(format.toUpperCase());
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-help")) {
        help();
        System.exit(0);
      } else {
        System.err.println("Unknown argument " + args[argIndex + 1]);
        throw new IllegalArgumentException("Unknown argument " + args[argIndex + 1]);
      }
    }

    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
    if (sentimentModel != null) {
      props.setProperty("sentiment.model", sentimentModel);
    }
    if (parserModel != null) {
      props.setProperty("parse.model", parserModel);
    }

    if (filename != null && stdin) {
      throw new IllegalArgumentException("Please only specify one of -file or -stdin");
    }
    if (filename == null && !stdin) {
      throw new IllegalArgumentException("Please specify either -file or -stdin");
    }

    if (stdin) {
      props.setProperty("ssplit.eolonly", "true");
    }
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    if (filename != null) {
      // Process a file.  The pipeline will do tokenization, which
      // means it will split it into sentences as best as possible
      // with the tokenizer.
      String text = IOUtils.slurpFileNoExceptions(filename);
      Annotation annotation = new Annotation(text);
      pipeline.annotate(annotation);

      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        Tree tree = sentence.get(SentimentCoreAnnotations.AnnotatedTree.class);
        System.out.println(sentence);
        outputTree(tree, output);
      }
    } else {
      // Process stdin.  Each line will be treated as a single sentence.
      System.err.println("Reading in text from stdin.");
      System.err.println("Please enter one sentence per line.");
      System.err.println("Processing will end when EOF is reached.");
      BufferedReader reader = new BufferedReader(IOUtils.encodedInputStreamReader(System.in, "utf-8"));
      while (true) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        line = line.trim();
        if (line.length() > 0) {
          Annotation annotation = pipeline.process(line);
          for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            Tree tree = sentence.get(SentimentCoreAnnotations.AnnotatedTree.class);
            outputTree(tree, output);
          }
        } else {
          // Output blank lines for blank lines so the tool can be
          // used for line-by-line text processing
          System.out.println("");
        }
      }
      
    }
  }
}
