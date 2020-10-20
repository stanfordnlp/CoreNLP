package edu.stanford.nlp.international.arabic.process;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.WordSegmenter;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Arabic word segmentation model based on conditional random fields (CRF).
 * This is a re-implementation (with extensions) of the model described in
 * (Green and DeNero, 2012).
 *
 * This package includes a JFlex-based orthographic normalization package
 * that runs on the input prior to processing by the CRF-based segmentation
 * model. The normalization options are configurable, but must be consistent for
 * both training and test data.
 *
 * @author Spence Green
 */
public class ArabicSegmenter implements WordSegmenter, ThreadsafeProcessor<String,String> /* Serializable */  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ArabicSegmenter.class);

  private static final long serialVersionUID = -4791848633597417788L;

  // SEGMENTER OPTIONS (can be set in the Properties object
  // passed to the constructor).

  // The input already been tokenized. Do not run the Arabic tokenizer.
  private static final String optTokenized = "tokenized";

  // Tokenizer options
  private static final String optTokenizer = "orthoOptions";

  // Mark segmented prefixes with this String
  private static final String optPrefix = "prefixMarker";

  // Mark segmented suffixes with this String
  private static final String optSuffix = "suffixMarker";

  // Number of decoding threads
  private static final String optThreads = "nthreads";

  // Write TedEval files
  private static final String optTedEval = "tedEval";

  // Use a custom feature factory
  private static final String optFeatureFactory = "featureFactory";
  private static final String defaultFeatureFactory =
      "edu.stanford.nlp.international.arabic.process.StartAndEndArabicSegmenterFeatureFactory";
  private static final String localOnlyFeatureFactory =
      "edu.stanford.nlp.international.arabic.process.ArabicSegmenterFeatureFactory";

  // Training and evaluation files have domain labels
  private static final String optWithDomains = "withDomains";

  // Training and evaluation text are all in the same domain (default:atb)
  private static final String optDomain = "domain";

  // Ignore rewrites (training only, produces a model that then can be used to do
  // no-rewrite segmentation)
  private static final String optNoRewrites = "noRewrites";

  // Use the original feature set which doesn't contain start-and-end "wrapper" features
  private static final String optLocalFeaturesOnly = "localFeaturesOnly";

  private transient CRFClassifier<CoreLabel> classifier;
  private final SeqClassifierFlags flags;
  private final TokenizerFactory<CoreLabel> tf;
  private final String prefixMarker;
  private final String suffixMarker;
  private final boolean isTokenized;
  private final String tokenizerOptions;
  private final String tedEvalPrefix;
  private final boolean hasDomainLabels;
  private final String domain;
  private final boolean noRewrites;

  /**
   * Make an Arabic Segmenter.
   *
   *  @param props Options for how to tokenize. See the main method of {@link ArabicTokenizer} for details
   */
  public ArabicSegmenter(Properties props) {
    isTokenized = props.containsKey(optTokenized);
    tokenizerOptions = props.getProperty(optTokenizer, null);
    tedEvalPrefix = props.getProperty(optTedEval, null);
    hasDomainLabels = props.containsKey(optWithDomains);
    domain = props.getProperty(optDomain, "atb");
    noRewrites = props.containsKey(optNoRewrites);
    tf = getTokenizerFactory();

    prefixMarker = props.getProperty(optPrefix, "");
    suffixMarker = props.getProperty(optSuffix, "");

    if (props.containsKey(optLocalFeaturesOnly)) {
      if (props.containsKey(optFeatureFactory))
        throw new RuntimeException("Cannot use custom feature factory with localFeaturesOnly flag--" +
            "have your custom feature factory extend ArabicSegmenterFeatureFactory instead of " +
            "StartAndEndArabicSegmenterFeatureFactory and remove the localFeaturesOnly flag.");

      props.setProperty(optFeatureFactory, localOnlyFeatureFactory);
    }
    if (!props.containsKey(optFeatureFactory))
      props.setProperty(optFeatureFactory, defaultFeatureFactory);

    // Remove all command-line properties that are specific to ArabicSegmenter
    props.remove(optTokenizer);
    props.remove(optTokenized);
    props.remove(optPrefix);
    props.remove(optSuffix);
    props.remove(optThreads);
    props.remove(optTedEval);
    props.remove(optWithDomains);
    props.remove(optDomain);
    props.remove(optNoRewrites);
    props.remove(optLocalFeaturesOnly);

    flags = new SeqClassifierFlags(props);
    classifier = new CRFClassifier<>(flags);
  }

  /**
   * Copy constructor.
   *
   * @param other
   */
  public ArabicSegmenter(ArabicSegmenter other) {
    isTokenized = other.isTokenized;
    tokenizerOptions = other.tokenizerOptions;
    prefixMarker = other.prefixMarker;
    suffixMarker = other.suffixMarker;
    tedEvalPrefix = other.tedEvalPrefix;
    hasDomainLabels = other.hasDomainLabels;
    domain = other.domain;
    noRewrites = other.noRewrites;
    flags = other.flags;

    // ArabicTokenizerFactory is *not* threadsafe. Make a new copy.
    tf = getTokenizerFactory();

    // CRFClassifier is threadsafe, so return a reference.
    classifier = other.classifier;
  }

  /**
   * Creates an ArabicTokenizer. The default tokenizer
   * is ArabicTokenizer.atbFactory(), which produces the
   * same orthographic normalization as Green and Manning (2010).
   *
   * @return A TokenizerFactory that produces each Arabic token as a CoreLabel
   */
  private TokenizerFactory<CoreLabel> getTokenizerFactory() {
    TokenizerFactory<CoreLabel> tokFactory = null;
    if ( ! isTokenized) {
      if (tokenizerOptions == null) {
        tokFactory = ArabicTokenizer.atbFactory();
        String atbVocOptions = "removeProMarker,removeMorphMarker,removeLengthening";
        tokFactory.setOptions(atbVocOptions);
      } else {
        if (tokenizerOptions.contains("removeSegMarker")) {
          throw new RuntimeException("Option 'removeSegMarker' cannot be used with ArabicSegmenter");
        }
        tokFactory = ArabicTokenizer.factory();
        tokFactory.setOptions(tokenizerOptions);
      }
      log.info("Loaded ArabicTokenizer with options: " + tokenizerOptions);
    }
    return tokFactory;
  }

  @Override
  public void initializeTraining(double numTrees) {
    throw new UnsupportedOperationException("Training is not supported!");
  }

  @Override
  public void train(Collection<Tree> trees) {
    throw new UnsupportedOperationException("Training is not supported!");
  }

  @Override
  public void train(Tree tree) {
    throw new UnsupportedOperationException("Training is not supported!");
  }

  @Override
  public void train(List<TaggedWord> sentence) {
    throw new UnsupportedOperationException("Training is not supported!");
  }

  @Override
  public void finishTraining() {
    throw new UnsupportedOperationException("Training is not supported!");
  }

  @Override
  public String process(String nextInput) {
    return segmentString(nextInput);
  }

  @Override
  public ThreadsafeProcessor<String, String> newInstance() {
    return new ArabicSegmenter(this);
  }

  @Override
  public List<HasWord> segment(String line) {
    String segmentedString = segmentString(line);
    return SentenceUtils.toWordList(segmentedString.split("\\s+"));
  }

  private List<CoreLabel> segmentStringToIOB(String line) {
    List<CoreLabel> tokenList;
    if (tf == null) {
      // Whitespace tokenization.
      tokenList = IOBUtils.StringToIOB(line);
    } else {
      List<CoreLabel> tokens = tf.getTokenizer(new StringReader(line)).tokenize();
      tokenList = IOBUtils.StringToIOB(tokens, null, false, tf, line);
    }
    IOBUtils.labelDomain(tokenList, domain);
    tokenList = classifier.classify(tokenList);
    return tokenList;
  }

  public List<CoreLabel> segmentStringToTokenList(String line) {
    List<CoreLabel> tokenList = CollectionUtils.makeList();
    List<CoreLabel> labeledSequence = segmentStringToIOB(line);
    for (IntPair span : IOBUtils.TokenSpansForIOB(labeledSequence)) {
      CoreLabel token = new CoreLabel();
      String text = IOBUtils.IOBToString(labeledSequence, prefixMarker, suffixMarker,
          span.getSource(), span.getTarget());
      token.setWord(text);
      token.setValue(text);
      token.set(CoreAnnotations.TextAnnotation.class, text);
      token.set(CoreAnnotations.ArabicSegAnnotation.class, "1");
      int start = labeledSequence.get(span.getSource()).beginPosition();
      int end = labeledSequence.get(span.getTarget() - 1).endPosition();
      token.setOriginalText(line.substring(start, end));
      token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, start);
      token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
      tokenList.add(token);
    }
    return tokenList;
  }

  public String segmentString(String line) {
    List<CoreLabel> labeledSequence = segmentStringToIOB(line);
    String segmentedString = IOBUtils.IOBToString(labeledSequence, prefixMarker, suffixMarker);
    return segmentedString;
  }

  /**
   * Segment all strings from an input.
   *
   * @param br -- input stream to segment
   * @param pwOut -- output stream to write the segmenter text
   * @return number of input characters segmented
   */
  public long segment(BufferedReader br, PrintWriter pwOut) {
    long nSegmented = 0;
    try {
      for (String line; (line = br.readLine()) != null;) {
        nSegmented += line.length(); // Measure this quantity since it is quick to compute
        String segmentedLine = segmentString(line);
        pwOut.println(segmentedLine);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return nSegmented;
  }

  /**
   * Train a segmenter from raw text. Gold segmentation markers are required.
   */
  public void train() {
    boolean hasSegmentationMarkers = true;
    boolean hasTags = true;
    DocumentReaderAndWriter<CoreLabel> docReader = new ArabicDocumentReaderAndWriter(hasSegmentationMarkers,
                                                                                     hasTags,
                                                                                     hasDomainLabels,
                                                                                     domain,
                                                                                     noRewrites,
                                                                                     tf);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.trainFile, docReader);

    classifier.train(lines, docReader);
    log.info("Finished training.");
  }

  /**
   * Evaluate accuracy when the input is gold segmented text *with* segmentation
   * markers and morphological analyses. In other words, the evaluation file has the
   * same format as the training data.
   *
   * @param pwOut
   */
  private void evaluate(PrintWriter pwOut) {
    log.info("Starting evaluation...");
    boolean hasSegmentationMarkers = true;
    boolean hasTags = true;
    DocumentReaderAndWriter<CoreLabel> docReader = new ArabicDocumentReaderAndWriter(hasSegmentationMarkers,
                                                                                     hasTags,
                                                                                     hasDomainLabels,
                                                                                     domain,
                                                                                     tf);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.testFile, docReader);

    PrintWriter tedEvalGoldTree = null, tedEvalParseTree = null;
    PrintWriter tedEvalGoldSeg = null, tedEvalParseSeg = null;
    if (tedEvalPrefix != null) {
      try {
        tedEvalGoldTree = new PrintWriter(tedEvalPrefix + "_gold.ftree");
        tedEvalGoldSeg = new PrintWriter(tedEvalPrefix + "_gold.segmentation");
        tedEvalParseTree = new PrintWriter(tedEvalPrefix + "_parse.ftree");
        tedEvalParseSeg = new PrintWriter(tedEvalPrefix + "_parse.segmentation");
      } catch (FileNotFoundException e) {
        System.err.printf("%s: %s%n", ArabicSegmenter.class.getName(), e.getMessage());
      }
    }

    Counter<String> labelTotal = new ClassicCounter<>();
    Counter<String> labelCorrect = new ClassicCounter<>();
    int total = 0;
    int correct = 0;
    for (List<CoreLabel> line : lines) {
      final String[] inputTokens = tedEvalSanitize(IOBUtils.IOBToString(line).replaceAll(":", "#pm#")).split(" ");
      final String[] goldTokens = tedEvalSanitize(IOBUtils.IOBToString(line, ":")).split(" ");
      line = classifier.classify(line);
      final String[] parseTokens = tedEvalSanitize(IOBUtils.IOBToString(line, ":")).split(" ");
      for (CoreLabel label : line) {
        // Do not evaluate labeling of whitespace
        String observation = label.get(CoreAnnotations.CharAnnotation.class);
        if ( ! observation.equals(IOBUtils.getBoundaryCharacter())) {
          total++;
          String hypothesis = label.get(CoreAnnotations.AnswerAnnotation.class);
          String reference = label.get(CoreAnnotations.GoldAnswerAnnotation.class);
          labelTotal.incrementCount(reference);
          if (hypothesis.equals(reference)) {
            correct++;
            labelCorrect.incrementCount(reference);
          }
        }
      }
      if (tedEvalParseSeg != null) {
        tedEvalGoldTree.printf("(root");
        tedEvalParseTree.printf("(root");
        int safeLength = inputTokens.length;
        if (inputTokens.length != goldTokens.length) {
          log.info("In generating TEDEval files: Input and gold do not have the same number of tokens");
          log.info("    (ignoring any extras)");
          log.info("  input: " + Arrays.toString(inputTokens));
          log.info("  gold: " + Arrays.toString(goldTokens));
          safeLength = Math.min(inputTokens.length, goldTokens.length);
        }
        if (inputTokens.length != parseTokens.length) {
          log.info("In generating TEDEval files: Input and parse do not have the same number of tokens");
          log.info("    (ignoring any extras)");
          log.info("  input: " + Arrays.toString(inputTokens));
          log.info("  parse: " + Arrays.toString(parseTokens));
          safeLength = Math.min(inputTokens.length, parseTokens.length);
        }
        for (int i = 0; i < safeLength; i++) {
          for (String segment : goldTokens[i].split(":"))
            tedEvalGoldTree.printf(" (seg %s)", segment);
          tedEvalGoldSeg.printf("%s\t%s%n", inputTokens[i], goldTokens[i]);
          for (String segment : parseTokens[i].split(":"))
            tedEvalParseTree.printf(" (seg %s)", segment);
          tedEvalParseSeg.printf("%s\t%s%n", inputTokens[i], parseTokens[i]);
        }
        tedEvalGoldTree.printf(")%n");
        tedEvalGoldSeg.println();
        tedEvalParseTree.printf(")%n");
        tedEvalParseSeg.println();
      }
    }

    double accuracy = ((double) correct) / ((double) total);
    accuracy *= 100.0;

    pwOut.println("EVALUATION RESULTS");
    pwOut.printf("#datums:\t%d%n", total);
    pwOut.printf("#correct:\t%d%n", correct);
    pwOut.printf("accuracy:\t%.2f%n", accuracy);
    pwOut.println("==================");

    // Output the per label accuracies
    pwOut.println("PER LABEL ACCURACIES");
    for (String refLabel : labelTotal.keySet()) {
      double nTotal = labelTotal.getCount(refLabel);
      double nCorrect = labelCorrect.getCount(refLabel);
      double acc = (nCorrect / nTotal) * 100.0;
      pwOut.printf(" %s\t%.2f%n", refLabel, acc);
    }

    if (tedEvalParseSeg != null) {
      tedEvalGoldTree.close();
      tedEvalGoldSeg.close();
      tedEvalParseTree.close();
      tedEvalParseSeg.close();
    }
  }

  private static String tedEvalSanitize(String str) {
    return str.replaceAll("\\(", "#lp#").replaceAll("\\)", "#rp#");
  }

  /**
   * Evaluate P/R/F1 when the input is raw text.
   */
  private static void evaluateRawText(PrintWriter pwOut) {
    // TODO(spenceg): Evaluate raw input w.r.t. a reference that might have different numbers
    // of characters per sentence. Need to implement a monotonic sequence alignment algorithm
    // to align the two character strings.
    //    String gold = flags.answerFile;
    //    String rawFile = flags.testFile;
    throw new RuntimeException("Not yet implemented!");
  }

  public void serializeSegmenter(String filename) {
    classifier.serializeClassifier(filename);
  }

  public void loadSegmenter(String filename, Properties p) {
    try {
      classifier = CRFClassifier.getClassifier(filename, p);
    } catch (ClassCastException | IOException | ClassNotFoundException e) {
      throw new RuntimeIOException("Failed to load segmenter " + filename, e);
    }
  }

  @Override
  public void loadSegmenter(String filename) {
    loadSegmenter(filename, new Properties());
  }


  private static String usage() {
    String nl = System.lineSeparator();
    StringBuilder sb = new StringBuilder();
    sb.append("Usage: java ").append(ArabicSegmenter.class.getName()).append(" OPTS < file_to_segment").append(nl);
    sb.append(nl).append(" Options:").append(nl);
    sb.append("  -help                : Print this message.").append(nl);
    sb.append("  -orthoOptions str    : Comma-separated list of orthographic normalization options to pass to ArabicTokenizer.").append(nl);
    sb.append("  -tokenized           : Text is already tokenized. Do not run internal tokenizer.").append(nl);
    sb.append("  -trainFile file      : Gold segmented IOB training file.").append(nl);
    sb.append("  -testFile  file      : Gold segmented IOB evaluation file.").append(nl);
    sb.append("  -textFile  file      : Raw input file to be segmented.").append(nl);
    sb.append("  -loadClassifier file : Load serialized classifier from file.").append(nl);
    sb.append("  -prefixMarker char   : Mark segmented prefixes with specified character.").append(nl);
    sb.append("  -suffixMarker char   : Mark segmented suffixes with specified character.").append(nl);
    sb.append("  -nthreads num        : Number of threads  (default: 1)").append(nl);
    sb.append("  -tedEval prefix      : Output TedEval-compliant gold and parse files.").append(nl);
    sb.append("  -featureFactory cls  : Name of feature factory class  (default: ").append(defaultFeatureFactory);
    sb.append(")").append(nl);
    sb.append("  -withDomains         : Train file (if given) and eval file have domain labels.").append(nl);
    sb.append("  -domain dom          : Assume one domain for all data (default: 123)").append(nl);
    sb.append(nl).append(" Otherwise, all flags correspond to those present in SeqClassifierFlags.java.").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("help", 0);
    optionArgDefs.put("orthoOptions", 1);
    optionArgDefs.put("tokenized", 0);
    optionArgDefs.put("trainFile", 1);
    optionArgDefs.put("testFile", 1);
    optionArgDefs.put("textFile", 1);
    optionArgDefs.put("loadClassifier", 1);
    optionArgDefs.put("prefixMarker", 1);
    optionArgDefs.put("suffixMarker", 1);
    optionArgDefs.put("nthreads", 1);
    optionArgDefs.put("tedEval", 1);
    optionArgDefs.put("featureFactory", 1);
    optionArgDefs.put("withDomains", 0);
    optionArgDefs.put("domain", 1);
    return optionArgDefs;
  }

  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    // Strips off hyphens
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    if (options.containsKey("help") || args.length == 0) {
      log.info(usage());
      System.exit(-1);
    }

    int nThreads = PropertiesUtils.getInt(options, "nthreads", 1);
    ArabicSegmenter segmenter = getSegmenter(options);

    // Decode either an evaluation file or raw text
    try {
      PrintWriter pwOut;
      if (segmenter.flags.outputEncoding != null) {
        OutputStreamWriter out = new OutputStreamWriter(System.out, segmenter.flags.outputEncoding);
        pwOut = new PrintWriter(out, true);
      } else if (segmenter.flags.inputEncoding != null) {
        OutputStreamWriter out = new OutputStreamWriter(System.out, segmenter.flags.inputEncoding);
        pwOut = new PrintWriter(out, true);
      } else {
        pwOut = new PrintWriter(System.out, true);
      }
      if (segmenter.flags.testFile != null) {
        if (segmenter.flags.answerFile == null) {
          segmenter.evaluate(pwOut);
        } else {
          segmenter.evaluateRawText(pwOut);
        }

      } else {
        BufferedReader br = (segmenter.flags.textFile == null) ?
            IOUtils.readerFromStdin() :
                IOUtils.readerFromString(segmenter.flags.textFile, segmenter.flags.inputEncoding);

        double charsPerSec = decode(segmenter, br, pwOut, nThreads);
        IOUtils.closeIgnoringExceptions(br);
        System.err.printf("Done! Processed input text at %.2f input characters/second%n", charsPerSec);
      }

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (IOException e) {
      System.err.printf("%s: Could not open %s%n", ArabicSegmenter.class.getName(), segmenter.flags.textFile);
    }
  }

  /**
   * Segment input and write to output stream.
   *
   * @param segmenter
   * @param br
   * @param pwOut
   * @param nThreads
   * @return input characters processed per second
   */
  private static double decode(ArabicSegmenter segmenter, BufferedReader br,
                               PrintWriter pwOut, int nThreads) {
    assert nThreads > 0;
    long nChars = 0;
    final long startTime = System.nanoTime();
    if (nThreads > 1) {
      MulticoreWrapper<String,String> wrapper = new MulticoreWrapper<>(nThreads, segmenter);
      try {
        for (String line; (line = br.readLine()) != null;) {
          nChars += line.length();
          wrapper.put(line);
          while (wrapper.peek()) {
            pwOut.println(wrapper.poll());
          }
        }

        wrapper.join();
        while (wrapper.peek()) {
          pwOut.println(wrapper.poll());
        }

      } catch (IOException e) {
        log.warn(e);
      }

    } else {
      nChars = segmenter.segment(br, pwOut);
    }
    long duration = System.nanoTime() - startTime;
    double charsPerSec = (double) nChars / (duration / 1000000000.0);
    return charsPerSec;
  }

  /**
   * Train a new segmenter or load an trained model from file.  First
   * checks to see if there is a "model" or "loadClassifier" flag to
   * load from, and if not tries to run training using the given
   * options.
   *
   * @param options Properties to specify segmenter behavior
   * @return the trained or loaded model
   */
  public static ArabicSegmenter getSegmenter(Properties options) {
    ArabicSegmenter segmenter = new ArabicSegmenter(options);
    if (segmenter.flags.inputEncoding == null) {
      segmenter.flags.inputEncoding = System.getProperty("file.encoding");
    }

    // Load or train the classifier
    if (segmenter.flags.loadClassifier != null) {
      segmenter.loadSegmenter(segmenter.flags.loadClassifier, options);
    } else if (segmenter.flags.trainFile != null){
      segmenter.train();

      if(segmenter.flags.serializeTo != null) {
        segmenter.serializeSegmenter(segmenter.flags.serializeTo);
        log.info("Serialized segmenter to: " + segmenter.flags.serializeTo);
      }
    } else {
      log.info("No training file or trained model specified!");
      log.info(usage());
      System.exit(-1);
    }
    return segmenter;
  }

}
