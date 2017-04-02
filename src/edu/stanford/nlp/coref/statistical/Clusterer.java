package edu.stanford.nlp.coref.statistical;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.stanford.nlp.coref.statistical.ClustererDataLoader.ClustererDoc;
import edu.stanford.nlp.coref.statistical.EvalUtils.B3Evaluator;
import edu.stanford.nlp.coref.statistical.EvalUtils.Evaluator;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * System for building up coreference clusters incrementally, merging a pair of clusters each step.
 * Trained with a variant of the SEARN imitation learning algorithm.
 * @author Kevin Clark
 */
public class Clusterer {
  private static final boolean USE_CLASSIFICATION = true;
  private static final boolean USE_RANKING = true;
  private static final boolean LEFT_TO_RIGHT = false;
  private static final boolean EXACT_LOSS = false;
  private static final double MUC_WEIGHT = 0.25;
  private static final double EXPERT_DECAY = 0.0;
  private static final double LEARNING_RATE = 0.05;
  private static final int BUFFER_SIZE_MULTIPLIER = 20;
  private static final int MAX_DOCS = 1000;
  private static final int RETRAIN_ITERATIONS = 100;
  private static final int NUM_EPOCHS = 15;
  private static final int EVAL_FREQUENCY = 1;

  private static final int MIN_PAIRS = 10;
  private static final double MIN_PAIRWISE_SCORE = 0.15;
  private static final int EARLY_STOP_THRESHOLD = 1000;
  private static final double EARLY_STOP_VAL = 1500 / 0.2;

  public static int currentDocId = 0;
  public static int isTraining = 1;

  private final ClustererClassifier classifier;
  private final Random random;

  public Clusterer() {
    random = new Random(0);
    classifier = new ClustererClassifier(LEARNING_RATE);
  }

  public Clusterer(String modelPath) {
    random = new Random(0);
    classifier = new ClustererClassifier(modelPath, LEARNING_RATE);
  }

  public List<Pair<Integer, Integer>> getClusterMerges(ClustererDoc doc) {
    List<Pair<Integer, Integer>> merges = new ArrayList<>();
    State currentState = new State(doc);
    while (!currentState.isComplete()) {
      Pair<Integer, Integer> currentPair =
          currentState.mentionPairs.get(currentState.currentIndex);
      if (currentState.doBestAction(classifier)) {
        merges.add(currentPair);
      }
    }
    return merges;
  }

  public void doTraining(String modelName) {
    classifier.setWeight("bias", -0.3);
    classifier.setWeight("anaphorSeen", -1);
    classifier.setWeight("max-ranking", 1);
    classifier.setWeight("bias-single", -0.3);
    classifier.setWeight("anaphorSeen-single", -1);
    classifier.setWeight("max-ranking-single", 1);

    String outputPath = StatisticalCorefTrainer.clusteringModelsPath +
        modelName + "/";
    File outDir = new File(outputPath);
    if (!outDir.exists()) {
      outDir.mkdir();
    }

    PrintWriter progressWriter;
    List<ClustererDoc> trainDocs;
    try {
      PrintWriter configWriter = new PrintWriter(outputPath + "config", "UTF-8");
      configWriter.print(StatisticalCorefTrainer.fieldValues(this));
      configWriter.close();
      progressWriter = new PrintWriter(outputPath + "progress", "UTF-8");

      Redwood.log("scoref.train", "Loading training data");
      StatisticalCorefTrainer.setDataPath("dev");
      trainDocs = ClustererDataLoader.loadDocuments(MAX_DOCS);
    } catch (Exception e) {
      throw new RuntimeException("Error setting up training", e);
    }

    double bestTrainScore = 0;
    List<List<Pair<CandidateAction, CandidateAction>>> examples = new ArrayList<>();
    for (int iteration = 0; iteration < RETRAIN_ITERATIONS; iteration++) {
      Redwood.log("scoref.train", "ITERATION " + iteration);
      classifier.printWeightVector(null);
      Redwood.log("scoref.train", "");
      try {
        classifier.writeWeights(outputPath + "model");
        classifier.printWeightVector(IOUtils.getPrintWriter(outputPath + "weights"));
      } catch (Exception e) {
        throw new RuntimeException();
      }

      long start = System.currentTimeMillis();
      Collections.shuffle(trainDocs, random);

      examples = examples.subList(Math.max(0, examples.size()
          - BUFFER_SIZE_MULTIPLIER * trainDocs.size()), examples.size());
      trainPolicy(examples);

      if (iteration % EVAL_FREQUENCY == 0) {
        double trainScore = evaluatePolicy(trainDocs, true);
        if (trainScore > bestTrainScore) {
          bestTrainScore = trainScore;
          writeModel("best", outputPath);
        }

        if (iteration % 10 == 0) {
          writeModel("iter_" + iteration, outputPath);
        }
        writeModel("last", outputPath);

        double timeElapsed = (System.currentTimeMillis() - start) / 1000.0;
        double ffhr = State.ffHits / (double) (State.ffHits + State.ffMisses);
        double shr = State.sHits / (double) (State.sHits + State.sMisses);
        double fhr = featuresCacheHits /
            (double) (featuresCacheHits + featuresCacheMisses);
        Redwood.log("scoref.train", modelName);
        Redwood.log("scoref.train", String.format("Best train: %.4f", bestTrainScore));
        Redwood.log("scoref.train", String.format("Time elapsed: %.2f", timeElapsed));
        Redwood.log("scoref.train", String.format("Cost hit rate: %.4f", ffhr));
        Redwood.log("scoref.train", String.format("Score hit rate: %.4f", shr));
        Redwood.log("scoref.train", String.format("Features hit rate: %.4f", fhr));
        Redwood.log("scoref.train", "");

        progressWriter.write(iteration + " " + trainScore + " "
            + " " + timeElapsed + " " + ffhr + " " + shr
            + " " + fhr + "\n");
        progressWriter.flush();
      }

      for (ClustererDoc trainDoc : trainDocs) {
        examples.add(runPolicy(trainDoc, Math.pow(EXPERT_DECAY,
                (iteration + 1))));
      }
    }

    progressWriter.close();
  }

  private void writeModel(String name, String modelPath) {
    try {
      classifier.writeWeights(modelPath + name + "_model.ser");
      classifier.printWeightVector(
          IOUtils.getPrintWriter(modelPath + name + "_weights"));
    } catch (Exception e) {
      throw new RuntimeException();
    }
  }

  private void trainPolicy(List<List<Pair<CandidateAction, CandidateAction>>> examples) {
    List<Pair<CandidateAction, CandidateAction>> flattenedExamples = new ArrayList<>();
    examples.stream().forEach(flattenedExamples::addAll);

    for (int epoch = 0; epoch < NUM_EPOCHS; epoch++) {
      Collections.shuffle(flattenedExamples, random);
      flattenedExamples.forEach(classifier::learn);
    }

    double totalCost = flattenedExamples.stream()
        .mapToDouble(e -> classifier.bestAction(e).cost).sum();
    Redwood.log("scoref.train",
        String.format("Training cost: %.4f", 100 * totalCost / flattenedExamples.size()));
  }

  private double evaluatePolicy(List<ClustererDoc> docs, boolean training) {
    isTraining = 0;
    B3Evaluator evaluator = new B3Evaluator();
    for (ClustererDoc doc : docs) {
      State currentState = new State(doc);
      while (!currentState.isComplete()) {
        currentState.doBestAction(classifier);
      }
      currentState.updateEvaluator(evaluator);
    }
    isTraining = 1;

    double score = evaluator.getF1();
    Redwood.log("scoref.train", String.format("B3 F1 score on %s: %.4f",
        training ? "train" : "validate", score));
    return score;
  }

  private List<Pair<CandidateAction, CandidateAction>> runPolicy(ClustererDoc doc, double beta) {
    List<Pair<CandidateAction, CandidateAction>> examples = new ArrayList<>();
    State currentState = new State(doc);
    while (!currentState.isComplete()) {
      Pair<CandidateAction, CandidateAction> actions = currentState.getActions(classifier);
      if (actions == null) {
        continue;
      }
      examples.add(actions);

      boolean useExpert = random.nextDouble() < beta;
      double action1Score = useExpert ? -actions.first.cost :
        classifier.weightFeatureProduct(actions.first.features);
      double action2Score = useExpert ? -actions.second.cost :
        classifier.weightFeatureProduct(actions.second.features);
      currentState.doAction(action1Score >= action2Score);
    }

    return examples;
  }

  private static class GlobalFeatures {
    public boolean anaphorSeen;
    public int currentIndex;
    public int size;
    public double docSize;
  }

  private static class State {
    private static int sHits;
    private static int sMisses;
    private static int ffHits;
    private static int ffMisses;

    private final Map<MergeKey, Boolean> hashedScores;
    private final Map<Long, Double> hashedCosts;

    private final ClustererDoc doc;
    private final List<Cluster> clusters;
    private final Map<Integer, Cluster> mentionToCluster;
    private final List<Pair<Integer, Integer>> mentionPairs;
    private final List<GlobalFeatures> globalFeatures;

    private int currentIndex;
    private Cluster c1;
    private Cluster c2;
    private long hash;

    public State(ClustererDoc doc) {
      currentDocId = doc.id;
      this.doc = doc;
      this.hashedScores = new HashMap<>();
      this.hashedCosts = new HashMap<>();
      this.clusters = new ArrayList<>();
      this.hash = 0;

      mentionToCluster = new HashMap<>();
      for (int m : doc.mentions) {
        Cluster c = new Cluster(m);
        clusters.add(c);
        mentionToCluster.put(m, c);
        hash ^= c.hash * 7;
      }

      List<Pair<Integer, Integer>> allPairs = new ArrayList<>(doc.classificationScores.keySet());

      Counter<Pair<Integer, Integer>> scores =
          USE_RANKING ? doc.rankingScores : doc.classificationScores;
      Collections.sort(allPairs, (p1, p2) -> {
        double diff = scores.getCount(p2) - scores.getCount(p1);
        return diff == 0 ? 0 : (int) Math.signum(diff);
      });

      int i = 0;
      for (i = 0; i < allPairs.size(); i++) {
        double score = scores.getCount(allPairs.get(i));
        if (score < MIN_PAIRWISE_SCORE && i > MIN_PAIRS) {
          break;
        }
        if (i >= EARLY_STOP_THRESHOLD && i / score > EARLY_STOP_VAL) {
          break;
        }
      }
      mentionPairs = allPairs.subList(0, i);
      if (LEFT_TO_RIGHT) {
        Collections.sort(mentionPairs, (p1, p2) -> {
          if (p1.second.equals(p2.second)) {
            double diff = scores.getCount(p2) - scores.getCount(p1);
            return diff == 0 ? 0 : (int) Math.signum(diff);
          }
          return doc.mentionIndices.get(p1.second)
              < doc.mentionIndices.get(p2.second) ? -1 : 1;
        });
        for (int j = 0; j < mentionPairs.size(); j++) {
          Pair<Integer, Integer> p1 = mentionPairs.get(j);
          for (int k = j + 1; k < mentionPairs.size(); k++) {
            Pair<Integer, Integer> p2 = mentionPairs.get(k);
            assert(doc.mentionIndices.get(p1.second)
                <= doc.mentionIndices.get(p2.second));
          }
        }
      }

      Counter<Integer> seenAnaphors = new ClassicCounter<>();
      Counter<Integer> seenAntecedents = new ClassicCounter<>();
      globalFeatures = new ArrayList<>();
      for (int j = 0; j < allPairs.size(); j++) {
        Pair<Integer, Integer> mentionPair = allPairs.get(j);
        GlobalFeatures gf = new GlobalFeatures();
        gf.currentIndex = j;
        gf.anaphorSeen = seenAnaphors.containsKey(mentionPair.second);
        gf.size = mentionPairs.size();
        gf.docSize = doc.mentions.size() / 300.0;
        globalFeatures.add(gf);

        seenAnaphors.incrementCount(mentionPair.second);
        seenAntecedents.incrementCount(mentionPair.first);
      }

      currentIndex = 0;
      setClusters();
    }

    public State(State state) {
      this.hashedScores = state.hashedScores;
      this.hashedCosts = state.hashedCosts;

      this.doc = state.doc;
      this.hash = state.hash;
      this.mentionPairs = state.mentionPairs;
      this.currentIndex = state.currentIndex;
      this.globalFeatures = state.globalFeatures;

      this.clusters = new ArrayList<>();
      this.mentionToCluster = new HashMap<>();
      for (Cluster c : state.clusters) {
        Cluster copy = new Cluster(c);
        clusters.add(copy);
        for (int m : copy.mentions) {
          mentionToCluster.put(m, copy);
        }
      }

      setClusters();
    }

    public void setClusters() {
      Pair<Integer, Integer> currentPair = mentionPairs.get(currentIndex);
      c1 = mentionToCluster.get(currentPair.first);
      c2 = mentionToCluster.get(currentPair.second);
    }

    public void doAction(boolean isMerge) {
      if (isMerge) {
        if (c2.size() > c1.size()) {
          Cluster tmp = c1;
          c1 = c2;
          c2 = tmp;
        }

        hash ^= 7 * c1.hash;
        hash ^= 7 * c2.hash;

        c1.merge(c2);
        for (int m : c2.mentions) {
          mentionToCluster.put(m, c1);
        }
        clusters.remove(c2);

        hash ^= 7 * c1.hash;
      }
      currentIndex++;
      if (!isComplete()) {
        setClusters();
      }
      while (c1 == c2) {
        currentIndex++;
        if (isComplete()) {
          break;
        }
        setClusters();
      }
    }

    public boolean doBestAction(ClustererClassifier classifier) {
      Boolean doMerge = hashedScores.get(new MergeKey(c1, c2, currentIndex));
      if (doMerge == null) {
        Counter<String> features = getFeatures(doc, c1, c2,
            globalFeatures.get(currentIndex));
        doMerge = classifier.weightFeatureProduct(features) > 0;
        hashedScores.put(new MergeKey(c1, c2, currentIndex), doMerge);
        sMisses += isTraining;
      } else {
        sHits += isTraining;
      }

      doAction(doMerge);
      return doMerge;
    }

    public boolean isComplete() {
      return currentIndex >= mentionPairs.size();
    }

    public double getFinalCost(ClustererClassifier classifier) {
      while(EXACT_LOSS && !isComplete()) {
        if (hashedCosts.containsKey(hash)) {
          ffHits += isTraining;;
          return hashedCosts.get(hash);
        }
        doBestAction(classifier);
      }
      ffMisses += isTraining;

      double cost = EvalUtils.getCombinedF1(MUC_WEIGHT, doc.goldClusters, clusters,
          doc.mentionToGold, mentionToCluster);
      hashedCosts.put(hash, cost);
      return cost;
    }

    public void updateEvaluator(Evaluator evaluator) {
      evaluator.update(doc.goldClusters, clusters, doc.mentionToGold, mentionToCluster);
    }

    public Pair<CandidateAction, CandidateAction> getActions(ClustererClassifier classifier) {
      Counter<String> mergeFeatures = getFeatures(doc, c1, c2,
          globalFeatures.get(currentIndex));
      double mergeScore = Math.exp(classifier.weightFeatureProduct(mergeFeatures));
      hashedScores.put(new MergeKey(c1, c2, currentIndex), mergeScore > 0.5);

      State merge = new State(this);
      merge.doAction(true);
      double mergeB3 = merge.getFinalCost(classifier);

      State noMerge = new State(this);
      noMerge.doAction(false);
      double noMergeB3 = noMerge.getFinalCost(classifier);

      double weight = doc.mentions.size() / 100.0;
      double maxB3 = Math.max(mergeB3, noMergeB3);
      return new Pair<>(
              new CandidateAction(mergeFeatures, weight * (maxB3 - mergeB3)),
              new CandidateAction(new ClassicCounter<>(), weight * (maxB3 - noMergeB3)));
    }
  }

  private static class MergeKey {
    private final int hash;

    public MergeKey(Cluster c1, Cluster c2, int ind) {
      hash = (int)(c1.hash ^ c2.hash) + (2003 * ind) + currentDocId;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      return ((MergeKey) o).hash == hash;
    }
  }

  public static class Cluster {
    private static final Map<Pair<Integer, Integer>, Long> MENTION_HASHES = new HashMap<>();
    private static final Random RANDOM = new Random(0);

    public final List<Integer> mentions;
    public long hash;

    public Cluster(int m) {
      mentions = new ArrayList<>();
      mentions.add(m);
      hash = getMentionHash(m);
    }

    public Cluster(Cluster c) {
      mentions = new ArrayList<>(c.mentions);
      hash = c.hash;
    }

    public void merge(Cluster c) {
      mentions.addAll(c.mentions);
      hash ^= c.hash;
    }

    public int size() {
      return mentions.size();
    }

    public long getHash() {
      return hash;
    }

    private static long getMentionHash(int m) {
      Pair<Integer, Integer> pair = new Pair<>(m, currentDocId);
      Long hash = MENTION_HASHES.get(pair);
      if (hash == null) {
        hash = RANDOM.nextLong();
        MENTION_HASHES.put(pair, hash);
      }
      return hash;
    }
  }

  private static int featuresCacheHits;
  private static int featuresCacheMisses;
  private static Map<MergeKey, CompressedFeatureVector> featuresCache = new HashMap<>();
  private static Compressor<String> compressor = new Compressor<>();

  private static Counter<String> getFeatures(ClustererDoc doc, Pair<Integer, Integer> mentionPair,
      Counter<Pair<Integer, Integer>> scores) {
    Counter<String> features = new ClassicCounter<>();
    if (!scores.containsKey(mentionPair)) {
      mentionPair = new Pair<>(mentionPair.second, mentionPair.first);
    }
    double score = scores.getCount(mentionPair);
    features.incrementCount("max", score);
    return features;
  }

  private static Counter<String> getFeatures(ClustererDoc doc,
      List<Pair<Integer, Integer>> mentionPairs, Counter<Pair<Integer, Integer>> scores) {
    Counter<String> features = new ClassicCounter<>();

    double maxScore = 0;
    double minScore = 1;
    Counter<String> totals = new ClassicCounter<>();
    Counter<String> totalsLog = new ClassicCounter<>();
    Counter<String> counts = new ClassicCounter<>();
    for (Pair<Integer, Integer> mentionPair : mentionPairs) {
      if (!scores.containsKey(mentionPair)) {
        mentionPair = new Pair<>(mentionPair.second, mentionPair.first);
      }
      double score = scores.getCount(mentionPair);
      double logScore = cappedLog(score);

      String mt1 = doc.mentionTypes.get(mentionPair.first);
      String mt2 = doc.mentionTypes.get(mentionPair.second);
      mt1 = mt1.equals("PRONOMINAL") ? "PRONOMINAL" : "NON_PRONOMINAL";
      mt2 = mt2.equals("PRONOMINAL") ? "PRONOMINAL" : "NON_PRONOMINAL";
      String conj = "_" + mt1 + "_" + mt2;

      maxScore = Math.max(maxScore, score);
      minScore = Math.min(minScore, score);

      totals.incrementCount("", score);
      totalsLog.incrementCount("", logScore);
      counts.incrementCount("");

      totals.incrementCount(conj, score);
      totalsLog.incrementCount(conj, logScore);
      counts.incrementCount(conj);
    }

    features.incrementCount("max", maxScore);
    features.incrementCount("min", minScore);
    for (String key : counts.keySet()) {
      features.incrementCount("avg" + key, totals.getCount(key) / mentionPairs.size());
      features.incrementCount("avgLog" + key, totalsLog.getCount(key) / mentionPairs.size());
    }

    return features;
  }

  private static int earliestMention(Cluster c, ClustererDoc doc) {
    int earliest = -1;
    for (int m : c.mentions) {
      int pos = doc.mentionIndices.get(m);
      if (earliest == -1 || pos < doc.mentionIndices.get(earliest)) {
        earliest = m;
      }
    }
    return earliest;
  }

  private static Counter<String> getFeatures(ClustererDoc doc, Cluster c1, Cluster c2, GlobalFeatures gf) {
    MergeKey key = new MergeKey(c1, c2, gf.currentIndex);
    CompressedFeatureVector cfv = featuresCache.get(key);
    Counter<String> features = cfv == null ? null : compressor.uncompress(cfv);
    if (features != null) {
      featuresCacheHits += isTraining;
      return features;
    }
    featuresCacheMisses += isTraining;

    features = new ClassicCounter<>();
    if (gf.anaphorSeen) {
      features.incrementCount("anaphorSeen");
    }
    features.incrementCount("docSize", gf.docSize);
    features.incrementCount("percentComplete", gf.currentIndex / (double) gf.size);
    features.incrementCount("bias", 1.0);

    int earliest1 = earliestMention(c1, doc);
    int earliest2 = earliestMention(c2, doc);
    if (doc.mentionIndices.get(earliest1) > doc.mentionIndices.get(earliest2)) {
      int tmp = earliest1;
      earliest1 = earliest2;
      earliest2 = tmp;
    }
    features.incrementCount("anaphoricity", doc.anaphoricityScores.getCount(earliest2));

    if (c1.mentions.size() == 1 && c2.mentions.size() == 1) {
      Pair<Integer, Integer> mentionPair = new Pair<>(c1.mentions.get(0),
          c2.mentions.get(0));

      if (USE_CLASSIFICATION) {
        features.addAll(addSuffix(getFeatures(doc, mentionPair, doc.classificationScores),
            "-classification"));
      }
      if (USE_RANKING) {
        features.addAll(addSuffix(getFeatures(doc, mentionPair, doc.rankingScores),
            "-ranking"));
      }

      features = addSuffix(features, "-single");
    } else {
      List<Pair<Integer, Integer>> between = new ArrayList<>();
      for (int m1 : c1.mentions) {
        for (int m2 : c2.mentions) {
          between.add(new Pair<>(m1, m2));
        }
      }

      if (USE_CLASSIFICATION) {
        features.addAll(addSuffix(getFeatures(doc, between, doc.classificationScores),
            "-classification"));
      }
      if (USE_RANKING) {
        features.addAll(addSuffix(getFeatures(doc, between, doc.rankingScores),
            "-ranking"));
      }
    }

    featuresCache.put(key, compressor.compress(features));
    return features;
  }

  private static Counter<String> addSuffix(Counter<String> features, String suffix) {
    Counter<String> withSuffix = new ClassicCounter<>();
    for (Map.Entry<String, Double> e : features.entrySet()) {
      withSuffix.incrementCount(e.getKey() + suffix, e.getValue());
    }
    return withSuffix;
  }

  private static double cappedLog(double x) {
    return Math.log(Math.max(x, 1e-8));
  }

  private static class ClustererClassifier extends SimpleLinearClassifier {
    public ClustererClassifier(double learningRate) {
      super(SimpleLinearClassifier.risk(),
          SimpleLinearClassifier.constant(learningRate),
          0);
    }

    public ClustererClassifier(String modelFile, double learningRate) {
      super(SimpleLinearClassifier.risk(),
          SimpleLinearClassifier.constant(learningRate),
          0,
          modelFile);
    }

    public CandidateAction bestAction(Pair<CandidateAction, CandidateAction> actions) {
      return weightFeatureProduct(actions.first.features) >
          weightFeatureProduct(actions.second.features) ? actions.first : actions.second;
    }

    public void learn(Pair<CandidateAction, CandidateAction> actions) {
      CandidateAction goodAction = actions.first;
      CandidateAction badAction = actions.second;
      if (badAction.cost == 0) {
        CandidateAction tmp = goodAction;
        goodAction = badAction;
        badAction = tmp;
      }
      Counter<String> features = new ClassicCounter<>(goodAction.features);
      for (Map.Entry<String, Double> e : badAction.features.entrySet()) {
        features.decrementCount(e.getKey(), e.getValue());
      }
      learn(features, 0, badAction.cost);
    }
  }

  private static class CandidateAction {
    public final Counter<String> features;
    public final double cost;

    public CandidateAction(Counter<String> features, double cost) {
      this.features = features;
      this.cost = cost;
    }
  }
}
