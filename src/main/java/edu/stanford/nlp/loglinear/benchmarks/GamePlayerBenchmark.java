package edu.stanford.nlp.loglinear.benchmarks; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.loglinear.inference.CliqueTree;
import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.ConcatVectorNamespace;
import edu.stanford.nlp.loglinear.model.GraphicalModel;

import java.io.IOException;
import java.util.*;

/**
 * Created on 9/11/15.
 * @author keenon
 * <p>
 * This simulates game-player-like activity, with a few CoNLL CliqueTrees playing host to lots and lots of manipulations
 * by adding and removing human "observations". In real life, this kind of behavior occurs during sampling lookahead for
 * LENSE-like systems.
 * <p>
 * In order to measure only the realistic parts of behavior, and not the random generation of numbers, we pre-cache a
 * few hundred ConcatVectors representing human obs features, then our feature function is just indexing into that cache.
 * The cache is designed to require a bit of L1 cache eviction to page through, so that we don't see artificial speed
 * gains during dot products b/c we already have both features and weights in L1 cache.
 */
public class GamePlayerBenchmark  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(GamePlayerBenchmark.class);
  static final String DATA_PATH = "/u/nlp/data/ner/conll/";

  public static void main(String[] args) throws IOException, ClassNotFoundException {

    //////////////////////////////////////////////////////////////
    // Generate the CoNLL CliqueTrees to use during gameplay
    //////////////////////////////////////////////////////////////

    CoNLLBenchmark coNLL = new CoNLLBenchmark();

    List<CoNLLBenchmark.CoNLLSentence> train = coNLL.getSentences(DATA_PATH + "conll.iob.4class.train");
    List<CoNLLBenchmark.CoNLLSentence> testA = coNLL.getSentences(DATA_PATH + "conll.iob.4class.testa");
    List<CoNLLBenchmark.CoNLLSentence> testB = coNLL.getSentences(DATA_PATH + "conll.iob.4class.testb");

    List<CoNLLBenchmark.CoNLLSentence> allData = new ArrayList<>();
    allData.addAll(train);
    allData.addAll(testA);
    allData.addAll(testB);

    Set<String> tagsSet = new HashSet<>();
    for (CoNLLBenchmark.CoNLLSentence sentence : allData) for (String nerTag : sentence.ner) tagsSet.add(nerTag);
    List<String> tags = new ArrayList<>();
    tags.addAll(tagsSet);

    coNLL.embeddings = coNLL.getEmbeddings(DATA_PATH + "google-300-trimmed.ser.gz", allData);

    log.info("Making the training set...");

    ConcatVectorNamespace namespace = new ConcatVectorNamespace();

    int trainSize = train.size();
    GraphicalModel[] trainingSet = new GraphicalModel[trainSize];
    for (int i = 0; i < trainSize; i++) {
      if (i % 10 == 0) {
        log.info(i + "/" + trainSize);
      }
      trainingSet[i] = coNLL.generateSentenceModel(namespace, train.get(i), tags);
    }

    //////////////////////////////////////////////////////////////
    // Generate the random human observation feature vectors that we'll use
    //////////////////////////////////////////////////////////////

    Random r = new Random(10);
    int numFeatures = 5;
    int featureLength = 30;
    ConcatVector[] humanFeatureVectors = new ConcatVector[1000];
    for (int i = 0; i < humanFeatureVectors.length; i++) {
      humanFeatureVectors[i] = new ConcatVector(numFeatures);
      for (int j = 0; j < numFeatures; j++) {
        if (r.nextBoolean()) {
          humanFeatureVectors[i].setSparseComponent(j, r.nextInt(featureLength), r.nextDouble());
        } else {
          double[] dense = new double[featureLength];
          for (int k = 0; k < dense.length; k++) {
            dense[k] = r.nextDouble();
          }
          humanFeatureVectors[i].setDenseComponent(j, dense);
        }
      }
    }

    ConcatVector weights = new ConcatVector(numFeatures);
    for (int i = 0; i < numFeatures; i++) {
      double[] dense = new double[featureLength];
      for (int j = 0; j < dense.length; j++) dense[j] = r.nextDouble();
      weights.setDenseComponent(i, dense);
    }

    //////////////////////////////////////////////////////////////
    // Actually perform gameplay-like random mutations
    //////////////////////////////////////////////////////////////

    log.info("Warming up the JIT...");

    for (int i = 0; i < 10; i++) {
      log.info(i);
      gameplay(r, trainingSet[i], weights, humanFeatureVectors);
    }

    log.info("Timing actual run...");

    long start = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      log.info(i);
      gameplay(r, trainingSet[i], weights, humanFeatureVectors);
    }
    long duration = System.currentTimeMillis() - start;

    log.info("Duration: " + duration);
  }

  //////////////////////////////////////////////////////////////
  // This is an implementation of something like MCTS, trying to take advantage of the general speed gains due to fast
  // CliqueTree caching of dot products. It doesn't actually do any clever selection, preferring to select observations
  // at random.
  //////////////////////////////////////////////////////////////

  private static void gameplay(Random r, GraphicalModel model, ConcatVector weights, ConcatVector[] humanFeatureVectors) {
    List<Integer> variablesList = new ArrayList<>();
    List<Integer> variableSizesList = new ArrayList<>();
    for (GraphicalModel.Factor f : model.factors) {
      for (int i = 0; i < f.neigborIndices.length; i++) {
        int j = f.neigborIndices[i];
        if (!variablesList.contains(j)) {
          variablesList.add(j);
          variableSizesList.add(f.featuresTable.getDimensions()[i]);
        }
      }
    }

    int[] variables = variablesList.stream().mapToInt(i -> i).toArray();
    int[] variableSizes = variableSizesList.stream().mapToInt(i -> i).toArray();

    List<SampleState> childrenOfRoot = new ArrayList<>();

    CliqueTree tree = new CliqueTree(model, weights);

    int initialFactors = model.factors.size();

    // Run some "samples"
    long start = System.currentTimeMillis();
    long marginalsTime = 0;
    for (int i = 0; i < 1000; i++) {
      log.info("\tTaking sample " + i);
      Stack<SampleState> stack = new Stack<>();
      SampleState state = selectOrCreateChildAtRandom(r, model, variables, variableSizes, childrenOfRoot, humanFeatureVectors);
      long localMarginalsTime = 0;
      // Each "sample" is 10 moves deep
      for (int j = 0; j < 10; j++) {
        // log.info("\t\tFrame "+j);
        state.push(model);
        assert (model.factors.size() == initialFactors + j + 1);

        ///////////////////////////////////////////////////////////
        // This is the thing we're really benchmarking
        ///////////////////////////////////////////////////////////
        if (state.cachedMarginal == null) {
          long s = System.currentTimeMillis();
          state.cachedMarginal = tree.calculateMarginalsJustSingletons();
          localMarginalsTime += System.currentTimeMillis() - s;
        }

        stack.push(state);
        state = selectOrCreateChildAtRandom(r, model, variables, variableSizes, state.children, humanFeatureVectors);
      }
      log.info("\t\t" + localMarginalsTime + " ms");
      marginalsTime += localMarginalsTime;

      while (!stack.empty()) {
        stack.pop().pop(model);
      }
      assert (model.factors.size() == initialFactors);
    }

    log.info("Marginals time: " + marginalsTime + " ms");
    log.info("Avg time per marginal: " + (marginalsTime / 200) + " ms");
    log.info("Total time: " + (System.currentTimeMillis() - start));
  }

  private static SampleState selectOrCreateChildAtRandom(Random r,
                                                         GraphicalModel model,
                                                         int[] variables,
                                                         int[] variableSizes,
                                                         List<SampleState> children,
                                                         ConcatVector[] humanFeatureVectors) {
    int i = r.nextInt(variables.length);
    int variable = variables[i];
    int observation = r.nextInt(variableSizes[i]);

    for (SampleState s : children) {
      if (s.variable == variable && s.observation == observation) return s;
    }

    int humanObservationVariable = 0;
    for (GraphicalModel.Factor f : model.factors) {
      for (int j : f.neigborIndices) {
        if (j >= humanObservationVariable) humanObservationVariable = j + 1;
      }
    }

    GraphicalModel.Factor f = model.addFactor(new int[]{variable, humanObservationVariable}, new int[]{variableSizes[i], variableSizes[i]}, (assn) -> {
      int j = (assn[0] * variableSizes[i]) + assn[1];
      return humanFeatureVectors[j];
    });
    model.factors.remove(f);

    SampleState newState = new SampleState(f, variable, observation);
    children.add(newState);
    return newState;
  }

  public static class SampleState {
    public GraphicalModel.Factor addedFactor;
    public int variable;
    public int observation;
    public List<SampleState> children = new ArrayList<>();

    public double[][] cachedMarginal = null;

    public SampleState(GraphicalModel.Factor addedFactor, int variable, int observation) {
      this.addedFactor = addedFactor;
      this.variable = variable;
      this.observation = observation;
    }

    /**
     * This applies this SampleState to the model. The name comes from an analogy to a stack. If we take a sample
     * path, involving a number of steps through the model, we push() each SampleState onto the model one at a time,
     * then when we return from the sample we can pop() each SampleState off the model, and be left with our
     * original model state.
     *
     * @param model the model to push this SampleState onto
     */
    public void push(GraphicalModel model) {
      assert (!model.factors.contains(addedFactor));
      model.factors.add(addedFactor);
      model.getVariableMetaDataByReference(variable).put(CliqueTree.VARIABLE_OBSERVED_VALUE, "" + observation);
    }

    /**
     * See push() for an explanation.
     *
     * @param model the model to pop this SampleState from
     */
    public void pop(GraphicalModel model) {
      assert (model.factors.contains(addedFactor));
      model.factors.remove(addedFactor);
      model.getVariableMetaDataByReference(variable).remove(CliqueTree.VARIABLE_OBSERVED_VALUE);
    }
  }
}
