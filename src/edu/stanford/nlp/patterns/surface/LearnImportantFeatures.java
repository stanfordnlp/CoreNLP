package edu.stanford.nlp.patterns.surface;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.patterns.CandidatePhrase;
import edu.stanford.nlp.patterns.DataInstance;
import edu.stanford.nlp.patterns.PatternsAnnotations;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.ArgumentParser.Option;
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
  public Class answerClass = CoreAnnotations.AnswerAnnotation.class;

  @Option(name = "answerLabel")
  public String answerLabel = "WORD";

  @Option(name = "wordClassClusterFile")
  String wordClassClusterFile = null;

  @Option(name = "thresholdWeight")
  Double thresholdWeight = null;

  Map<String, Integer> clusterIds = new HashMap<>();
  CollectionValuedMap<Integer, String> clusters = new CollectionValuedMap<>();

  @Option(name = "negativeWordsFiles")
  String negativeWordsFiles = null;
  HashSet<String> negativeWords = new HashSet<>();

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

  private int sample(Map<String, DataInstance> sents, Random r, Random rneg, double perSelectNeg, double perSelectRand, int numrand, List<Pair<String, Integer>> chosen, RVFDataset<String, String> dataset){
    for (Entry<String, DataInstance> en : sents.entrySet()) {
      CoreLabel[] sent = en.getValue().getTokens().toArray(new CoreLabel[0]);

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
    return numrand;
  }

  public Counter<String> getTopFeatures(Iterator<Pair<Map<String, DataInstance>, File>> sentsf,
      double perSelectRand, double perSelectNeg, String externalFeatureWeightsFileLabel) throws IOException, ClassNotFoundException {
    Counter<String> features = new ClassicCounter<>();
    RVFDataset<String, String> dataset = new RVFDataset<>();
    Random r = new Random(10);
    Random rneg = new Random(10);
    int numrand = 0;
    List<Pair<String, Integer>> chosen = new ArrayList<>();
    while(sentsf.hasNext()){
      Pair<Map<String, DataInstance>, File> sents = sentsf.next();
      numrand = this.sample(sents.first(), r, rneg, perSelectNeg, perSelectRand, numrand, chosen, dataset);
    }
    /*if(batchProcessSents){
      for(File f: sentFiles){
        Map<String, List<CoreLabel>> sentsf = IOUtils.readObjectFromFile(f);
        numrand = this.sample(sentsf, r, rneg, perSelectNeg, perSelectRand, numrand, chosen, dataset);
      }
    }else
      numrand = this.sample(sents, r, rneg, perSelectNeg, perSelectRand, numrand, chosen, dataset);
  */
    System.out.println("num random chosen: " + numrand);
    System.out.println("Number of datums per label: "
        + dataset.numDatumsPerLabel());

    LogisticClassifierFactory<String, String> logfactory = new LogisticClassifierFactory<>();
    LogisticClassifier<String, String> classifier = logfactory
        .trainClassifier(dataset);
    Counter<String> weights = classifier.weightsAsCounter();
    if (!classifier.getLabelForInternalPositiveClass().equals(answerLabel))
      weights = Counters.scale(weights, -1);
    if (thresholdWeight != null) {
      HashSet<String> removeKeys = new HashSet<>();
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
    Counter<String> feat = new ClassicCounter<>();
    CoreLabel l = sent[i];

    String label;
    if (l.get(answerClass).toString().equals(answerLabel))
      label = answerLabel;
    else
      label = "O";

      CollectionValuedMap<String, CandidatePhrase> matchedPhrases = l
          .get(PatternsAnnotations.MatchedPhrases.class);
      if (matchedPhrases == null) {
        matchedPhrases = new CollectionValuedMap<>();
        matchedPhrases.add(label, CandidatePhrase.createOrGet(l.word()));
      }

      for (CandidatePhrase w : matchedPhrases.allValues()) {
        Integer num = this.clusterIds.get(w.getPhrase());
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


    // System.out.println("adding " + l.word() + " as " + label);
    return new RVFDatum<>(feat, label);
  }

  public static void main(String[] args) {
    try {

      LearnImportantFeatures lmf = new LearnImportantFeatures();
      Properties props = StringUtils.argsToPropertiesWithResolve(args);
      ArgumentParser.fillOptions(lmf, props);
      lmf.setUp();
      String sentsFile = props.getProperty("sentsFile");
      Map<String, DataInstance> sents = IOUtils
          .readObjectFromFile(sentsFile);
      System.out.println("Read the sents file: " + sentsFile);
      double perSelectRand = Double.parseDouble(props
          .getProperty("perSelectRand"));
      double perSelectNeg = Double.parseDouble(props
          .getProperty("perSelectNeg"));
      // String wekaOptions = props.getProperty("wekaOptions");
      //lmf.getTopFeatures(false, , perSelectRand, perSelectNeg, props.getProperty("externalFeatureWeightsFile"));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
