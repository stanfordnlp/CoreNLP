package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * The idea is that you can learn features that are important using ML algorithm
 * and use those features in learning weights for patterns.
 * 
 * @author Sonal Gupta (sonalg@stanford.edu)
 * 
 */
public class LearnImportantFeatures {

  @Option(name = "answerClass")
  public Class answerClass = CoreAnnotations.AnswerAnnotation.class;// edu.stanford.nlp.sentimentaspects.health.HealthAnnotations.DictAnnotationDTorSC.class;

  @Option(name = "answerLabel")
  public String answerLabel = "WORD";

  @Option(name = "wordClassClusterFile")
  String wordClassClusterFile = null;

  @Option(name = "thresholdWeight")
  Double thresholdWeight = null;

  Map<String, Integer> clusterIds = new HashMap<String, Integer>();
  CollectionValuedMap<Integer, String> clusters = new CollectionValuedMap<Integer, String>();

  @Option(name = "negativeWordsFiles")
  String negativeWordsFiles = null;
  HashSet<String> negativeWords = new HashSet<String>();

  public void setUp() {
    assert (wordClassClusterFile != null);

    if (wordClassClusterFile != null) {
      for (String line : IOUtils.readLines(wordClassClusterFile)) {
        String[] t = line.split("\\s+");
        int num = Integer.parseInt(t[1]);
        clusterIds.put(t[0], num);
        clusters.add(num, t[0]);
      }
    }
    if (negativeWordsFiles != null) {
      for (String file : negativeWordsFiles.split("[,;]")) {
        negativeWords.addAll(IOUtils.linesFromFile(file));
      }
      System.out.println("number of negative words from lists "
          + negativeWords.size());
    }
  }

  public static boolean getRandomBoolean(Random random, double p) {
    return random.nextFloat() < p;
  }

  // public void getDecisionTree(Map<String, List<CoreLabel>> sents,
  // List<Pair<String, Integer>> chosen, Counter<String> weights, String
  // wekaOptions) {
  // RVFDataset<String, String> dataset = new RVFDataset<String, String>();
  // for (Pair<String, Integer> d : chosen) {
  // CoreLabel l = sents.get(d.first).get(d.second());
  // String w = l.word();
  // Integer num = this.clusterIds.get(w);
  // if (num == null)
  // num = -1;
  // double wt = weights.getCount("Cluster-" + num);
  // String label;
  // if (l.get(answerClass).toString().equals(answerLabel))
  // label = answerLabel;
  // else
  // label = "O";
  // Counter<String> feat = new ClassicCounter<String>();
  // feat.setCount("DIST", wt);
  // dataset.add(new RVFDatum<String, String>(feat, label));
  // }
  // WekaDatumClassifierFactory wekaFactory = new
  // WekaDatumClassifierFactory("weka.classifiers.trees.J48", wekaOptions);
  // WekaDatumClassifier classifier = wekaFactory.trainClassifier(dataset);
  // Classifier cls = classifier.getClassifier();
  // J48 j48decisiontree = (J48) cls;
  // System.out.println(j48decisiontree.toSummaryString());
  // System.out.println(j48decisiontree.toString());
  //
  // }

  public Counter<String> getTopFeatures(Map<String, List<CoreLabel>> sents,
      double perSelectRand, double perSelectNeg, String externalFeatureWeightsFileLabel) throws IOException {
    Counter<String> features = new ClassicCounter<String>();
    RVFDataset<String, String> dataset = new RVFDataset<String, String>();
    Random r = new Random(10);
    Random rneg = new Random(10);
    int numrand = 0;
    List<Pair<String, Integer>> chosen = new ArrayList<Pair<String, Integer>>();
    for (Entry<String, List<CoreLabel>> en : sents.entrySet()) {
      CoreLabel[] sent = en.getValue().toArray(new CoreLabel[0]);

      for (int i = 0; i < sent.length; i++) {
        CoreLabel l = sent[i];

        boolean chooseThis = false;

        if (l.get(answerClass).equals(answerLabel)){
          chooseThis = true;
          }
        else if ((!l.get(answerClass).equals("O") || negativeWords.contains(l
            .word().toLowerCase())) && getRandomBoolean(r, perSelectNeg)) {
          chooseThis = true;
        } else if (getRandomBoolean(r, perSelectRand)) {
          numrand++;
          chooseThis = true;
        } else
          chooseThis = false;
        if (chooseThis) {
          chosen.add(new Pair(en.getKey(), i));
          RVFDatum<String, String> d = getDatum(sent, i);
          dataset.add(d, en.getKey(), Integer.toString(i));
        }
      }
    }
    System.out.println("num random chosen: " + numrand);
    System.out.println("Number of datums per label: "
        + dataset.numDatumsPerLabel());

    LogisticClassifierFactory<String, String> logfactory = new LogisticClassifierFactory<String, String>();
    LogisticClassifier<String, String> classifier = logfactory
        .trainClassifier(dataset);
    Counter<String> weights = classifier.weightsAsGenericCounter();
    if (!classifier.getLabelForInternalPositiveClass().equals(answerLabel))
      weights = Counters.scale(weights, -1);
    if (thresholdWeight != null) {
      HashSet<String> removeKeys = new HashSet<String>();
      for (Entry<String, Double> en : weights.entrySet()) {
        if (Math.abs(en.getValue()) <= thresholdWeight)
          removeKeys.add(en.getKey());
      }
      Counters.removeKeys(weights, removeKeys);
      System.out.println("Removing " + removeKeys);
    }
    IOUtils.writeStringToFile(
        Counters.toSortedString(weights, weights.size(), "%1$s:%2$f", "\n"),
        externalFeatureWeightsFileLabel, "utf8");
    // getDecisionTree(sents, chosen, weights, wekaOptions);
    return features;
  }

  private RVFDatum<String, String> getDatum(CoreLabel[] sent, int i) {
    Counter<String> feat = new ClassicCounter<String>();
    CoreLabel l = sent[i];

    
      Set<String> matchedPhrases = l
          .get(PatternsAnnotations.MatchedPhrases.class);
      if (matchedPhrases == null) {
        matchedPhrases = new HashSet<String>();
        matchedPhrases.add(l.word());
      }

      for (String w : matchedPhrases) {
        Integer num = this.clusterIds.get(w);
        if (num == null)
          num = -1;
        feat.setCount("Cluster-" + num, 1.0);
      }

    

    // feat.incrementCount("WORD-" + l.word());
    // feat.incrementCount("LEMMA-" + l.lemma());
    // feat.incrementCount("TAG-" + l.tag());
    int window = 0;
    for (int j = Math.max(0, i - window); j < i; j++) {
      CoreLabel lj = sent[j];
      feat.incrementCount("PREV-" + "WORD-" + lj.word());
      feat.incrementCount("PREV-" + "LEMMA-" + lj.lemma());
      feat.incrementCount("PREV-" + "TAG-" + lj.tag());
    }

    for (int j = i + 1; j < sent.length && j <= i + window; j++) {
      CoreLabel lj = sent[j];
      feat.incrementCount("NEXT-" + "WORD-" + lj.word());
      feat.incrementCount("NEXT-" + "LEMMA-" + lj.lemma());
      feat.incrementCount("NEXT-" + "TAG-" + lj.tag());
    }

    String label;
    if (l.get(answerClass).toString().equals(answerLabel))
      label = answerLabel;
    else
      label = "O";
    // System.out.println("adding " + l.word() + " as " + label);
    return new RVFDatum<String, String>(feat, label);
  }

  public static void main(String[] args) {
    try {

      LearnImportantFeatures lmf = new LearnImportantFeatures();
      Properties props = StringUtils.argsToPropertiesWithResolve(args);
      Execution.fillOptions(lmf, props);
      lmf.setUp();
      String sentsFile = props.getProperty("sentsFile");
      Map<String, List<CoreLabel>> sents = IOUtils
          .readObjectFromFile(sentsFile);
      System.out.println("Read the sents file: " + sentsFile);
      double perSelectRand = Double.parseDouble(props
          .getProperty("perSelectRand"));
      double perSelectNeg = Double.parseDouble(props
          .getProperty("perSelectNeg"));
      // String wekaOptions = props.getProperty("wekaOptions");
      lmf.getTopFeatures(sents, perSelectRand, perSelectNeg, props.getProperty("externalFeatureWeightsFile"));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
