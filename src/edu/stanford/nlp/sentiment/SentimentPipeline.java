package edu.stanford.nlp.sentiment; 

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * A wrapper class which creates a suitable pipeline for the sentiment
 * model and processes raw text.
 * <p>
 * The main program has the following options: <br>
 * {@code -parserModel} Which parser model to use, defaults to englishPCFG.ser.gz <br>
 * {@code -sentimentModel} Which sentiment model to use, defaults to sentiment.ser.gz <br>
 * {@code -file} Which file to process. <br>
 * {@code -fileList} A comma separated list of files to process. <br>
 * {@code -stdin} Read one line at a time from stdin. <br>
 * {@code -output} pennTrees: Output trees with scores at each binarized node.  vectors: Number tree nodes and print out the vectors.  probabilities: Output the scores for different labels for each node. Defaults to printing just the root. <br>
 * {@code -filterUnknown} Remove unknown trees from the input.  Only applies to TREES input, in which case the trees must be binarized with sentiment labels <br>
 * {@code -help} Print out help <br>
 *
 * @author John Bauer
 */
public class SentimentPipeline  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SentimentPipeline.class);

  private static final NumberFormat NF = new DecimalFormat("0.0000");

  enum Output {
    PENNTREES, VECTORS, ROOT, PROBABILITIES
  }

  enum Input {
    TEXT, TREES
  }

  private SentimentPipeline() {} // static methods

  /**
   * Sets the labels on the tree (except the leaves) to be the integer
   * value of the sentiment prediction.  Makes it easy to print out
   * with Tree.toString()
   */
  private static void setSentimentLabels(Tree tree) {
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
  private static int setIndexLabels(Tree tree, int index) {
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
  private static int outputTreeVectors(PrintStream out, Tree tree, int index) {
    if (tree.isLeaf()) {
      return index;
    }

    out.print("  " + index + ':');
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
  private static int outputTreeScores(PrintStream out, Tree tree, int index) {
    if (tree.isLeaf()) {
      return index;
    }

    out.print("  " + index + ':');
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
   * Outputs a tree using the output style requested.
   */
  private static void outputTree(PrintStream out, CoreMap sentence, List<Output> outputFormats) {
    Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
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
        out.println("  " + sentence.get(SentimentCoreAnnotations.SentimentClass.class));
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

  private static final String DEFAULT_TLPP_CLASS = "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams";

  private static void help() {
    log.info("Known command line arguments:");
    log.info("  -sentimentModel <model>: Which model to use");
    log.info("  -parserModel <model>: Which parser to use");
    log.info("  -file <filename>: Which file to process");
    log.info("  -fileList <file>,<file>,...: Comma separated list of files to process.  Output goes to file.out");
    log.info("  -stdin: Process stdin instead of a file");
    log.info("  -input <format>: Which format to input, TEXT or TREES.  Will not process stdin as trees.  If trees are not already binarized, they will be binarized with -tlppClass's headfinder, which means they must have labels in that treebank's tagset.");
    log.info("  -output <format>: Which format to output, PENNTREES, VECTORS, PROBABILITIES, or ROOT.  Multiple formats can be specified as a comma separated list.");
    log.info("  -filterUnknown: remove unknown trees from the input.  Only applies to TREES input, in which case the trees must be binarized with sentiment labels");
    log.info("  -tlppClass: a class to use for building the binarizer if using non-binarized TREES as input.  Defaults to " + DEFAULT_TLPP_CLASS);
  }

  /**
   * Reads an annotation from the given filename using the requested input.
   */
  public static List<Annotation> getAnnotations(StanfordCoreNLP tokenizer, Input inputFormat, String filename, boolean filterUnknown) {
    switch (inputFormat) {
    case TEXT: {
      String text = IOUtils.slurpFileNoExceptions(filename);
      Annotation annotation = new Annotation(text);
      tokenizer.annotate(annotation);
      List<Annotation> annotations = Generics.newArrayList();
      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        Annotation nextAnnotation = new Annotation(sentence.get(CoreAnnotations.TextAnnotation.class));
        nextAnnotation.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
        annotations.add(nextAnnotation);
      }
      return annotations;
    }
    case TREES: {
      List<Tree> trees;
      if (filterUnknown) {
        trees = SentimentUtils.readTreesWithGoldLabels(filename);
        trees = SentimentUtils.filterUnknownRoots(trees);
      } else {
        MemoryTreebank treebank = new MemoryTreebank("utf-8");
        treebank.loadPath(filename, null);
        trees = new ArrayList<>(treebank);
      }

      List<Annotation> annotations = Generics.newArrayList();
      for (Tree tree : trees) {
        CoreMap sentence = new Annotation(SentenceUtils.listToString(tree.yield()));
        sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);
        List<CoreMap> sentences = Collections.singletonList(sentence);
        Annotation annotation = new Annotation("");
        annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
        annotations.add(annotation);
      }
      return annotations;
    }
    default:
      throw new IllegalArgumentException("Unknown format " + inputFormat);
    }
  }

  /** Runs the tree-based sentiment model on some text. */
  public static void main(String[] args) throws IOException {
    String parserModel = null;
    String sentimentModel = null;

    String filename = null;
    String fileList = null;
    boolean stdin = false;

    boolean filterUnknown = false;

    List<Output> outputFormats = Collections.singletonList(Output.ROOT);
    Input inputFormat = Input.TEXT;

    String tlppClass = DEFAULT_TLPP_CLASS;

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
        inputFormat = Input.valueOf(args[argIndex + 1].toUpperCase(Locale.ROOT));
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        String[] formats = args[argIndex + 1].split(",");
        outputFormats = new ArrayList<>();
        for (String format : formats) {
          outputFormats.add(Output.valueOf(format.toUpperCase(Locale.ROOT)));
        }
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-filterUnknown")) {
        filterUnknown = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-tlppClass")) {
        tlppClass = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-help")) {
        help();
        System.exit(0);
      } else {
        log.info("Unknown argument " + args[argIndex + 1]);
        help();
        throw new IllegalArgumentException("Unknown argument " + args[argIndex + 1]);
      }
    }

    // We construct two pipelines.  One handles tokenization, if
    // necessary.  The other takes tokenized sentences and converts
    // them to sentiment trees.
    Properties pipelineProps = new Properties();
    Properties tokenizerProps = null;
    if (sentimentModel != null) {
      pipelineProps.setProperty("sentiment.model", sentimentModel);
    }
    if (parserModel != null) {
      pipelineProps.setProperty("parse.model", parserModel);
    }
    if (inputFormat == Input.TREES) {
      pipelineProps.setProperty("annotators", "binarizer, sentiment");
      pipelineProps.setProperty("customAnnotatorClass.binarizer", "edu.stanford.nlp.pipeline.BinarizerAnnotator");
      pipelineProps.setProperty("binarizer.tlppClass", tlppClass);
      pipelineProps.setProperty("enforceRequirements", "false");
    } else {
      pipelineProps.setProperty("annotators", "parse, sentiment");
      pipelineProps.setProperty("parse.binaryTrees", "true");
      pipelineProps.setProperty("parse.buildgraphs", "false");
      pipelineProps.setProperty("enforceRequirements", "false");
      tokenizerProps = new Properties();
      tokenizerProps.setProperty("annotators", "tokenize, ssplit");
    }

    if (stdin && tokenizerProps != null) {
      tokenizerProps.setProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "true");
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

    StanfordCoreNLP tokenizer = (tokenizerProps == null) ? null : new StanfordCoreNLP(tokenizerProps);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(pipelineProps);

    if (filename != null) {
      // Process a file.  The pipeline will do tokenization, which
      // means it will split it into sentences as best as possible
      // with the tokenizer.
      List<Annotation> annotations = getAnnotations(tokenizer, inputFormat, filename, filterUnknown);
      for (Annotation annotation : annotations) {
        pipeline.annotate(annotation);

        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          System.out.println(sentence);
          outputTree(System.out, sentence, outputFormats);
        }
      }
    } else if (fileList != null) {
      // Process multiple files.  The pipeline will do tokenization,
      // which means it will split it into sentences as best as
      // possible with the tokenizer.  Output will go to filename.out
      // for each file.
      for (String file : fileList.split(",")) {
        List<Annotation> annotations = getAnnotations(tokenizer, inputFormat, file, filterUnknown);
        FileOutputStream fout = new FileOutputStream(file + ".out");
        PrintStream pout = new PrintStream(fout);
        for (Annotation annotation : annotations) {
          pipeline.annotate(annotation);

          for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            pout.println(sentence);
            outputTree(pout, sentence, outputFormats);
          }
        }
        pout.flush();
        fout.close();
      }
    } else {
      // Process stdin.  Each line will be treated as a single sentence.
      log.info("Reading in text from stdin.");
      log.info("Please enter one sentence per line.");
      log.info("Processing will end when EOF is reached.");
      BufferedReader reader = IOUtils.readerFromStdin("utf-8");

      for (String line; (line = reader.readLine()) != null; ) {
        line = line.trim();
        if ( ! line.isEmpty()) {
          Annotation annotation = tokenizer.process(line);
          pipeline.annotate(annotation);
          for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            outputTree(System.out, sentence, outputFormats);
          }
        } else {
          // Output blank lines for blank lines so the tool can be
          // used for line-by-line text processing
          System.out.println();
        }
      }

    }
  }

}
