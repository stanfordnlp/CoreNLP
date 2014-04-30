package edu.stanford.nlp.sentiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

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
    PENNTREES, VECTORS, ROOT, PROBABILITIES
  }

  static enum Input {
    TEXT, TREES
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
  static int outputTreeVectors(PrintStream out, Tree tree, int index) {
    if (tree.isLeaf()) {
      return index;
    }

    out.print("  " + index + ":");
    SimpleMatrix vector = RNNCoreAnnotations.getNodeVector(tree);
    for (int i = 0; i < vector.getNumElements(); ++i) {
      out.print("  " + NF.format(vector.get(i)));
    }
    out.println();
    index++;
    for (Tree child : tree.children()) {
      index = outputTreeVectors(out, child, index);
    }
    return index;
  }

  /**
   * Outputs the scores from the tree.  Counts the tree nodes the
   * same as setIndexLabels.
   */
  static int outputTreeScores(PrintStream out, Tree tree, int index) {
    if (tree.isLeaf()) {
      return index;
    }

    out.print("  " + index + ":");
    SimpleMatrix vector = RNNCoreAnnotations.getPredictions(tree);
    for (int i = 0; i < vector.getNumElements(); ++i) {
      out.print("  " + NF.format(vector.get(i)));
    }
    out.println();
    index++;
    for (Tree child : tree.children()) {
      index = outputTreeScores(out, child, index);
    }
    return index;
  }

  /**
   * Outputs a tree using the output style requested
   */
  static void outputTree(PrintStream out, CoreMap sentence, List<Output> outputFormats) {
    Tree tree = sentence.get(SentimentCoreAnnotations.AnnotatedTree.class);
    for (Output output : outputFormats) {
      switch (output) {
      case PENNTREES: {
        Tree copy = tree.deepCopy();
        setSentimentLabels(copy);
        out.println(copy);
        break;
      }
      case VECTORS: {
        Tree copy = tree.deepCopy();
        setIndexLabels(copy, 0);
        out.println(copy);
        outputTreeVectors(out, tree, 0);
        break;
      }
      case ROOT: {
        out.println("  " + sentence.get(SentimentCoreAnnotations.ClassName.class));
        break;
      }
      case PROBABILITIES: {
        Tree copy = tree.deepCopy();
        setIndexLabels(copy, 0);
        out.println(copy);
        outputTreeScores(out, tree, 0);
        break;
      }
      default:
        throw new IllegalArgumentException("Unknown output format " + output);
      }
    }
  }

  public static void help() {
    System.err.println("Known command line arguments:");
    System.err.println("  -sentimentModel <model>: Which model to use");
    System.err.println("  -parserModel <model>: Which parser to use");
    System.err.println("  -file <filename>: Which file to process");
    System.err.println("  -fileList <file>,<file>,...: Comma separated list of files to process.  Output goes to file.out");
    System.err.println("  -stdin: Process stdin instead of a file");
    System.err.println("  -input <format>: Which format to input, TEXT or TREES.  Will not process stdin as trees.  Trees need to be binarized");
    System.err.println("  -output <format>: Which format to output, PENNTREES, VECTORS, PROBABILITIES, or ROOT.  Multiple formats can be specified as a comma separated list.");
    System.err.println("  -filterUnknown: remove neutral and unknown trees from the input.  Only applies to TREES input");
  }

  /**
   * Reads an annotation from the given filename using the requested input.
   */
  public static Annotation getAnnotation(Input inputFormat, String filename, boolean filterUnknown) {
    switch (inputFormat) {
    case TEXT: {
      String text = IOUtils.slurpFileNoExceptions(filename);
      Annotation annotation = new Annotation(text);
      return annotation;
    }
    case TREES: {
      List<Tree> trees = SentimentUtils.readTreesWithGoldLabels(filename);
      if (filterUnknown) {
        trees = SentimentUtils.filterUnknownRoots(trees);
      }
      List<CoreMap> sentences = Generics.newArrayList();
      
      for (Tree tree : trees) {
        CoreMap sentence = new Annotation(Sentence.listToString(tree.yield()));
        sentence.set(TreeCoreAnnotations.BinarizedTreeAnnotation.class, tree);
        sentences.add(sentence);
      }
      Annotation annotation = new Annotation("");
      annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
      return annotation;
    }
    default:
      throw new IllegalArgumentException("Unknown format " + inputFormat);
    }
  }

  public static void main(String[] args) throws IOException {
    String parserModel = null;
    String sentimentModel = null;

    String filename = null;
    String fileList = null;
    boolean stdin = false;

    boolean filterUnknown = false;

    List<Output> outputFormats = Arrays.asList(new Output[] { Output.ROOT });
    Input inputFormat = Input.TEXT;

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
      } else if (args[argIndex].equalsIgnoreCase("-fileList")) {
        fileList = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-stdin")) {
        stdin = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-input")) {
        inputFormat = Input.valueOf(args[argIndex + 1].toUpperCase());
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        String[] formats = args[argIndex + 1].split(",");
        outputFormats = new ArrayList<Output>();
        for (String format : formats) {
          outputFormats.add(Output.valueOf(format.toUpperCase()));
        }
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-filterUnknown")) {
        filterUnknown = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-help")) {
        help();
        System.exit(0);
      } else {
        System.err.println("Unknown argument " + args[argIndex + 1]);
        throw new IllegalArgumentException("Unknown argument " + args[argIndex + 1]);
      }
    }

    Properties props = new Properties();
    if (sentimentModel != null) {
      props.setProperty("sentiment.model", sentimentModel);
    }
    if (parserModel != null) {
      props.setProperty("parse.model", parserModel);
    }
    if (stdin) {
      props.setProperty("ssplit.eolonly", "true");
    }
    if (inputFormat == Input.TREES) {
      props.setProperty("annotators", "sentiment");
      props.setProperty("enforceRequirements", "false");
    } else {
      props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
    }

    int count = 0;
    if (filename != null) count++;
    if (fileList != null) count++;
    if (stdin) count++;
    if (count > 1) {
      throw new IllegalArgumentException("Please only specify one of -file, -fileList or -stdin");
    }
    if (count == 0) {
      throw new IllegalArgumentException("Please specify either -file, -fileList or -stdin");
    }

    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    if (filename != null) {
      // Process a file.  The pipeline will do tokenization, which
      // means it will split it into sentences as best as possible
      // with the tokenizer.
      Annotation annotation = getAnnotation(inputFormat, filename, filterUnknown);
      pipeline.annotate(annotation);

      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        System.out.println(sentence);
        outputTree(System.out, sentence, outputFormats);
      }
    } else if (fileList != null) {
      // Process multiple files.  The pipeline will do tokenization,
      // which means it will split it into sentences as best as
      // possible with the tokenizer.  Output will go to filename.out
      // for each file.
      for (String file : fileList.split(",")) {
        Annotation annotation = getAnnotation(inputFormat, file, filterUnknown);
        pipeline.annotate(annotation);

        FileOutputStream fout = new FileOutputStream(file + ".out");
        PrintStream pout = new PrintStream(fout);
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          pout.println(sentence);
          outputTree(pout, sentence, outputFormats);
        }
        pout.flush();
        fout.close();
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
            outputTree(System.out, sentence, outputFormats);
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
