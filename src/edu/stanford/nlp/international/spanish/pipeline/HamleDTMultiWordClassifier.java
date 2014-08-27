package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * The AnCora Spanish corpus contains thousands of multi-word
 * expressions. In CoreNLP we don't deal with this MWEs and instead
 * preprocess our training data so that they are split into their
 * constituent token.
 *
 * The remaining problem is to determine how to amend the
 * dependency-parsed sentences containing these MWEs. This class
 * is a classifier which determines how the non-head tokens of a
 * multi-word expression should be labeled in a corrected dependency
 * treebank.
 *
 * @author Jon Gauthier
 */
public class HamleDTMultiWordClassifier {

  private static final NumberFormat nf = new DecimalFormat("0.000");

  // Path to FreeLing dictionary
  private static final String DICTIONARY_PATH =
    "data/edu/stanford/nlp/international/spanish/dict.data";
  private static final Map<String, String> dictionary = new HashMap<String, String>();
  static {
    for (String line : IOUtils.readLines(DICTIONARY_PATH)) {
      String[] fields = line.split("\\s");

      String word = fields[0], pos = fields[2];
      dictionary.put(word, pos);
    }
  }

  private static final List<Function<String, List<String>>> featureFunctions = new
    ArrayList<Function<String, List<String>>>() {{
      add(new LeadingVerbFeatureFunction());
      add(new CharacterNGramFeatureFunction(2, 4));
    }};

  private Classifier<String, String> classifier;
  private Classifier<String, String> makeClassifier(GeneralDataset<String, String> trainDataset) {
//    double sigma = 0.0; // TODO
//
//    return new NBLinearClassifierFactory<String, String>(sigma)
//      .trainClassifier(trainDataset);

    LinearClassifierFactory<String, String> lcf = new LinearClassifierFactory<String, String>();
    return lcf.trainClassifier(trainDataset);
  }

  public void trainClassifier(String datasetPath) {
    // Build dataset and output basic information
    GeneralDataset<String, String> dataset = readDataset(datasetPath);
    dataset.summaryStatistics();

    classifier = makeClassifier(dataset);
    // TODO serialize
  }

  private void checkClassifier() {
    if (classifier == null)
      throw new IllegalStateException("No classifier has been trained or loaded");
  }

  public void outputHighWeights(int numWeights) {
    checkClassifier();

    String cString;
    if (classifier instanceof LinearClassifier<?, ?>) {
      cString = ((LinearClassifier<?, ?>) classifier).toString("HighWeight", numWeights);
    } else {
      cString = classifier.toString();
    }

    System.err.println("Highest classifier weights:");
    System.err.println(cString);
  }

  public void testClassifier(String datasetPath) {
    checkClassifier();

    GeneralDataset<String, String> test = readDataset(datasetPath);

    // Count TPs, TNs, FPs, FNs
    IntCounter<String> contingency = new IntCounter<String>();

    for (Datum<String, String> datum : test) {
      Counter<String> logScores = classifier.scoresOf(datum);

      String guess = classifier.classOf(datum);
      String gold = datum.label();

      // Update stats
      for (String next : classifier.labels()) {
        if (next.equals(gold)) {
          if (next.equals(guess))
            contingency.incrementCount(next + "|TP");
          else
            contingency.incrementCount(next + "|FN");
        } else {
          if (next.equals(guess))
            contingency.incrementCount(next + "|FP");
          else
            contingency.incrementCount(next + "|FN");
        }
      }

      Distribution<String> scoreDist = Distribution.distributionFromLogisticCounter(logScores);
      outputTestExample(datum, guess, logScores, scoreDist);
    }

    outputTestSummary(classifier.labels(), test, contingency);
  }

  private void outputTestExample(Datum<String, String> datum, String answer,
                                 Counter<String> logScores,
                                 Distribution<String> scoreDist) {
    List<String> classes = Counters.toSortedList(logScores);

    // TODO output per-example information
  }

  private void outputTestSummary(Collection<String> labels, GeneralDataset<String, String> test,
                                 IntCounter<String> contingency) {
    System.err.printf("%n%d examples in test set", test.size());

    double microAccuracy = 0.0, macroF1 = 0.0;
    for (String label : labels) {
      int tp = contingency.getIntCount(label + "|TP");
      int tn = contingency.getIntCount(label + "|TN");
      int fp = contingency.getIntCount(label + "|FP");
      int fn = contingency.getIntCount(label + "|FN");

      double precision = (tp + fp == 0) ? 1.0 : ((double) tp) / (tp + fp);
      double recall = (tp + fn == 0) ? 1.0 : ((double) tp) / (tp + fn);
      double f1 = (precision == 0.0 && recall == 0.0)
                  ? 0.0 : 2 * precision * recall / (precision + recall);
      double accuracy = ((double) tp + tn) / test.size();

      macroF1 += f1;
      microAccuracy += tp;

      System.err.println("Cls " + label + ": TP=" + tp + " FN=" + fn + " FP=" + fp + " TN=" + tn +
                           "; Acc " + nf.format(accuracy) + " P " + nf.format(precision) + " R "
                           + nf.format(recall) + " F1 " + nf.format(f1));
    }

    microAccuracy = microAccuracy / test.size();
    macroF1 = macroF1 / labels.size();

    NumberFormat nf2 = new DecimalFormat("0.00000");
    System.err.println("Accuracy/micro-averaged F1: " + nf2.format(microAccuracy));
    System.err.println("Macro-averaged F1: " + nf2.format(macroF1));
  }

  private GeneralDataset<String, String> readDataset(String path) {
    GeneralDataset<String, String> dataset = new Dataset<String, String>();

    for (String line : ObjectBank.getLineIterator(new File(path))) {
      dataset.add(makeDatumFromLine(line));
    }

    return dataset;
  }

  private Datum<String, String> makeDatumFromLine(String line) {
    String[] fields = line.split("\t");
    String gold = fields[0];
    String expression = fields[1];

    List<String> features = new ArrayList<String>();

    for (Function<String, List<String>> featureFunction : featureFunctions)
      features.addAll(featureFunction.apply(expression));

    return new BasicDatum<String, String>(features, gold);
  }

  private static final String USAGE = String.format(
    "Usage: java %s [-train trainFile [-dev devFile] -saveSerialized serializedPath]%n" +
      "               [-loadSerialized serializedPath -eval evalFile]",
    HamleDTMultiWordClassifier.class.getName());
  
  private static Map<String, Integer> argOptionDefs = new HashMap<String, Integer>() {{
    put("train", 1);
    put("dev", 1);
    put("eval", 1);

    put("saveSerialized", 0);
    put("loadSerialized", 0);
    put("printClassifierHighWeights", 0);
  }};

  public static void main(String[] args) {
    Properties options = StringUtils.argsToProperties(args, argOptionDefs);
    if (!(options.containsKey("train") || options.containsKey("eval"))) {
      System.err.println(USAGE);
      System.exit(1);
    }

    // TODO support load serialized
    HamleDTMultiWordClassifier classifier = options.containsKey("loadSerialized")
      ? null : new HamleDTMultiWordClassifier();

    if (options.containsKey("train")) {
      if (options.containsKey("loadSerialized"))
        throw new RuntimeException("Can't re-train a pre-loaded serialized classifier -- exiting");

      classifier.trainClassifier(options.getProperty("train"));

      // TODO save serialized
    }

    boolean printHighWeights = PropertiesUtils.getBool(options, "printClassifierHighWeights",
                                                       false);
    if (printHighWeights)
      classifier.outputHighWeights(50);

    if (options.containsKey("dev")) {
      classifier.testClassifier(options.getProperty("dev"));
    }

    // TODO support eval
  }

  // -- Feature function definitions -- //

  private static class CharacterNGramFeatureFunction implements Function<String, List<String>> {

    private static final String featurePrefix = "#";

    private final int MIN_SIZE;
    private final int MAX_SIZE;

    public CharacterNGramFeatureFunction(int MIN_SIZE, int MAX_SIZE) {
      this.MIN_SIZE = MIN_SIZE;
      this.MAX_SIZE = MAX_SIZE;
    }

    @Override
    public List<String> apply(String mwe) {
      Collection<String> ngrams = StringUtils.getCharacterNgrams(mwe, MIN_SIZE, MAX_SIZE);
      return new ArrayList<String>(ngrams);
    }

  }

  private static class LeadingVerbFeatureFunction implements Function<String, List<String>> {

    private static final Set<String> verbs = new HashSet<String>();
    static {
      for (Map.Entry<String, String> lex : dictionary.entrySet()) {
        // Add only verbs to set
        if (lex.getValue().startsWith("v"))
          verbs.add(lex.getKey());
      }
    }

    private static final List<String> POSITIVE_RET = Arrays.asList("leadingVerb");
    private static final List<String> NEGATIVE_RET = Arrays.asList("noLeadingVerb");

    @Override
    public List<String> apply(String mwe) {
      String[] words = mwe.split("_");

      if (verbs.contains(words[0]))
        return POSITIVE_RET;
      return NEGATIVE_RET;
    }

  }

}
