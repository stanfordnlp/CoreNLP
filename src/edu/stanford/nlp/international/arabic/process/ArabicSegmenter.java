package edu.stanford.nlp.international.arabic.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
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
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Arabic word segmentation model based on conditional random fields (CRF).
 * This is a re-implementation (with extensions) of the model described in
 * (Green and DeNero, 2012).
 * <p>
 * This package includes a JFlex-based orthographic normalization package
 * that runs on the input prior to processing by the CRF-based segmentation
 * model. The normalization options are configurable, but must be consistent for
 * both training and test data.
 *
 * @author Spence Green
 */
public class ArabicSegmenter implements WordSegmenter, Serializable, ThreadsafeProcessor<String,String> {

  private static final long serialVersionUID = -4791848633597417788L;

  // SEGMENTER OPTIONS (can be set in the Properties object
  // passed to the constructor).

  // The input already been tokenized. Do not run the Arabic tokenizer.
  private final String optTokenized = "tokenized";

  // Tokenizer options
  private final String optTokenizer = "orthoOptions";

  // Mark segmented prefixes with this String
  private final String optPrefix = "prefixMarker";

  // Mark segmented suffixes with this String
  private final String optSuffix = "suffixMarker";

  // Number of decoding threads
  private final String optThreads = "nthreads";

  private transient CRFClassifier<CoreLabel> classifier;
  private final SeqClassifierFlags flags;
  private final TokenizerFactory<CoreLabel> tf;
  private final String prefixMarker;
  private final String suffixMarker;
  private final boolean isTokenized;
  private final String tokenizerOptions;

  public ArabicSegmenter(Properties props) {
    isTokenized = props.containsKey(optTokenized);
    tokenizerOptions = props.getProperty(optTokenizer, null);
    tf = getTokenizerFactory();

    prefixMarker = props.getProperty(optPrefix, "");
    suffixMarker = props.getProperty(optSuffix, "");

    // Remove all command-line properties that are specific to ArabicSegmenter
    props.remove(optTokenizer);
    props.remove(optTokenized);
    props.remove(optPrefix);
    props.remove(optSuffix);
    props.remove(optThreads);

    // Currently, this class only supports one featureFactory.
    props.put("featureFactory", "edu.stanford.nlp.international.arabic.process.ArabicSegmenterFeatureFactory");

    flags = new SeqClassifierFlags(props);
    classifier = new CRFClassifier<CoreLabel>(flags);
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
    flags = other.flags;

    // ArabicTokenizerFactory is *not* threadsafe. Make a new copy.
    tf = getTokenizerFactory();

    // CRFClassifier is threadsafe, so return a reference.
    classifier = other.classifier;
  }

  /**
   * Creates an ArabicTokenizer from the user-specified options. The
   * default is ArabicTokenizer.atbFactory(), which produces the
   * same orthographic normalization as Green and Manning (2010).
   *
   * @param props
   * @return
   */
  private TokenizerFactory<CoreLabel> getTokenizerFactory() {
    TokenizerFactory<CoreLabel> tokFactory = null;
    if ( ! isTokenized) {
      if (tokenizerOptions == null) {
        tokFactory = ArabicTokenizer.atbFactory();
        String atbVocOptions = "removeProMarker,removeMorphMarker";
        tokFactory.setOptions(atbVocOptions);
      } else {
        if (tokenizerOptions.contains("removeSegMarker")) {
          throw new RuntimeException("Option 'removeSegMarker' cannot be used with ArabicSegmenter");
        }
        tokFactory = ArabicTokenizer.factory();
        tokFactory.setOptions(tokenizerOptions);
      }
      System.err.println("Loaded ArabicTokenizer with options: " + tokenizerOptions);
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
    return Sentence.toWordList(segmentedString.split("\\s+"));
  }

  public String segmentString(String line) {
    List<CoreLabel> tokenList;
    if (tf == null) {
      // Whitespace tokenization.
      tokenList = IOBUtils.StringToIOB(line);
    } else {
      List<CoreLabel> tokens = tf.getTokenizer(new StringReader(line)).tokenize();
      tokenList = IOBUtils.StringToIOB(tokens, null, false);
    }
    tokenList = classifier.classify(tokenList);
    String segmentedString = IOBUtils.IOBToString(tokenList, prefixMarker, suffixMarker);
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
                                                                                     hasTags, tf);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.trainFile, docReader);

    classifier.train(lines, docReader);
    System.err.println("Finished training.");
  }

  /**
   * Evaluate accuracy when the input is gold segmented text *with* segmentation
   * markers and morphological analyses. In other words, the evaluation file has the
   * same format as the training data.
   *
   * @param pwOut
   */
  private void evaluate(PrintWriter pwOut) {
    System.err.println("Starting evaluation...");
    boolean hasSegmentationMarkers = true;
    boolean hasTags = true;
    DocumentReaderAndWriter<CoreLabel> docReader = new ArabicDocumentReaderAndWriter(hasSegmentationMarkers,
                                                                                     hasTags, tf);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.testFile, docReader);

    Counter<String> labelTotal = new ClassicCounter<String>();
    Counter<String> labelCorrect = new ClassicCounter<String>();
    int total = 0;
    int correct = 0;
    for (List<CoreLabel> line : lines) {
      line = classifier.classify(line);
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
  }

  /**
   * Evaluate P/R/F1 when the input is raw text
   */
  private void evaluateRawText(PrintWriter pwOut) {
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
    classifier = new CRFClassifier<CoreLabel>(p);
    try {
      classifier.loadClassifier(new File(filename), p);
    } catch (ClassCastException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void loadSegmenter(String filename) {
    loadSegmenter(filename, new Properties());
  }


  private static String usage() {
    String nl = System.getProperty("line.separator");
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
      System.err.println(usage());
      System.exit(-1);
    }

    int nThreads = PropertiesUtils.getInt(options, "nthreads", 1);
    ArabicSegmenter segmenter = getSegmenter(options);

    // Decode either an evaluation file or raw text
    try {
      PrintWriter pwOut = new PrintWriter(System.out, true);
      if (segmenter.flags.testFile != null) {
        if (segmenter.flags.answerFile == null) {
          segmenter.evaluate(pwOut);
        } else {
          segmenter.evaluateRawText(pwOut);
        }

      } else {
        BufferedReader br = (segmenter.flags.textFile == null) ?
            new BufferedReader(new InputStreamReader(System.in)) :
              new BufferedReader(new InputStreamReader(new FileInputStream(segmenter.flags.textFile),
                  segmenter.flags.inputEncoding));

        double charsPerSec = decode(segmenter, br, pwOut, nThreads);
        IOUtils.closeIgnoringExceptions(br);
        System.err.printf("Done! Processed input text at %.2f input characters/second%n", charsPerSec);
      }

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
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
      MulticoreWrapper<String,String> wrapper = new MulticoreWrapper<String,String>(nThreads, segmenter);
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
        e.printStackTrace();
      }

    } else {
      nChars = segmenter.segment(br, pwOut);
    }
    long duration = System.nanoTime() - startTime;
    double charsPerSec = (double) nChars / (duration / 1000000000.0);
    return charsPerSec;
  }

  /**
   * Train a new segmenter or load an trained model from file.
   *
   * @param options
   * @return
   */
  private static ArabicSegmenter getSegmenter(Properties options) {
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
        System.err.println("Serialized segmenter to: " + segmenter.flags.serializeTo);
      }
    } else {
      System.err.println("No training file or trained model specified!");
      System.err.println(usage());
      System.exit(-1);
    }
    return segmenter;
  }
}
