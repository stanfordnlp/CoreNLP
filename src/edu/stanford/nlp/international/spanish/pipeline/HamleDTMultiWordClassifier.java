package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.NBLinearClassifierFactory;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.Function;
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

  private static final List<Function<String, List<String>>> featureFunctions = new
    ArrayList<Function<String, List<String>>>() {{
      add(new LeadingVerbFeatureFunction());
      add(new NGramFeatureFunction(2, 4));
    }};

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

  private Classifier<String, String> classifier;
  private Classifier<String, String> makeClassifier(GeneralDataset<String, String> trainDataset) {
    double sigma = 0.0; // TODO

    return new NBLinearClassifierFactory<String, String>(sigma)
      .trainClassifier(trainDataset);
  }

  public void trainClassifier(String datasetPath) {
    // Build dataset and output basic information
    GeneralDataset<String, String> dataset = readDataset(datasetPath);
    dataset.summaryStatistics();

    classifier = makeClassifier(dataset);
    // TODO serialize
  }

  public void testClassifier(String datasetPath) {
    if (classifier == null)
      throw new IllegalStateException("No classifier has been trained or loaded");

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
    // TODO output test summary (see writeResultsSummary)
  }

  private void outputTestExample(Datum<String, String> datum, String answer,
                                 Counter<String> logScores,
                                 Distribution<String> scoreDist) {
    List<String> classes = Counters.toSortedList(logScores);

    // TODO output per-example information
  }

  private void outputTestSummary(Collection<String> labels, GeneralDataset<String, String> test,
                                 IntCounter<String> contingency) {
    System.err.printf("%n%i examples in test set", test.size());

    for (String label : labels) {
      int tp = contingency.getIntCount(label + "|TP");
      int tn = contingency.getIntCount(label + "|TN");
      int fp = contingency.getIntCount(label + "|FP");
      int fn = contingency.getIntCount(label + "|FN");

      double precision = (tp + fp == 0) ? 1.0 : ((double) tp) / (tp + fp);
      double recall = (tp + fn == 0) ? 1.0 : ((double) tp) / (tp + fn);
    }
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

  // -- Feature function definitions -- //

  private static class NGramFeatureFunction implements Function<String, List<String>> {

    private static final String featurePrefix = "#";

    private final int MIN_SIZE;
    private final int MAX_SIZE;

    public NGramFeatureFunction(int MIN_SIZE, int MAX_SIZE) {
      this.MIN_SIZE = MIN_SIZE;
      this.MAX_SIZE = MAX_SIZE;
    }

    @Override
    public List<String> apply(String mwe) {
      List<String> words = Arrays.asList(mwe.split("_"));
      Collection<String> ngrams = StringUtils.getNgrams(words, MIN_SIZE, MAX_SIZE);
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
