package edu.stanford.nlp.sequences;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.DomainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PossibleAnswersAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.optimization.*;

import java.util.*;
import java.io.*;

/**
 * This class directly deals with training sequence classifiers and
 * vending sequence models.
 *
 * @author Jenny Finkel
 **/

public class QueriableSequenceModelFactory implements Serializable {

  protected static final long serialVersionUID = 2218714006594152967l;

  public interface QueriableSequenceModelFactoryDataProvider {
    public ObjectiveFunctionInterface getObjectiveFunction(MultiDocumentCliqueDataset trainingData) ;
    public QueriableSequenceModel getModel(CliqueDataset dataset) ;
  }

  private QueriableSequenceModelFactoryDataProvider provider;
  private DatasetMetaInfo metaInfo = null;
  private SeqClassifierFlags flags;
  private double[] weights = null;
  private Map<String,double[]> hierarchicalWeights;
//  private Type2FeatureFactory featureFactory = null;
  private Map<String,String> answersByDomain = new HashMap();

  public DatasetMetaInfo metaInfo() { return metaInfo; }

  public QueriableSequenceModelFactory(QueriableSequenceModelFactoryDataProvider provider, SeqClassifierFlags flags, Type2FeatureFactory featureFactory, Map<String,ObjectBank<List<CoreLabel>>> trainingData, boolean baseline, boolean doFE) {
    this.provider = provider;
    this.flags = flags;
    this.metaInfo = new DatasetMetaInfo(featureFactory, flags.backgroundSymbol);
    if (doFE) {
      trainFrustratinglyEasy(trainingData);
    } else if (baseline) {
      trainHierarchicalBaseline(trainingData);
    } else {
      trainHierarchical(trainingData);
    }
  }

  public QueriableSequenceModelFactory(QueriableSequenceModelFactoryDataProvider provider, SeqClassifierFlags flags, Type2FeatureFactory featureFactory, ObjectBank<List<CoreLabel>> trainingData) {
      this(provider, flags, featureFactory, trainingData, new DatasetMetaInfo(featureFactory, flags.backgroundSymbol));
    }

  /**
   * This constructor takes the training data and the testing data, and then only trains the
   * overlapping features.
   */
  public QueriableSequenceModelFactory(QueriableSequenceModelFactoryDataProvider provider, SeqClassifierFlags flags, Type2FeatureFactory featureFactory, ObjectBank<List<CoreLabel>> trainingData, ObjectBank<List<CoreLabel>> testingData) {
    this.provider = provider;
    this.flags = flags;
    this.metaInfo = new DatasetMetaInfo(featureFactory, flags.backgroundSymbol);
    if (flags.combo) {
      ((ComboFeatureFactory)featureFactory).setMetaInfo(metaInfo);
    }
//    this.featureFactory = featureFactory;
    if (flags.evaluateIters > 0) {
      train(trainingData, testingData, metaInfo, false);
    } else {
      train(trainingData, testingData, false);
    }
  }

  private void trainHierarchical(Map<String,ObjectBank<List<CoreLabel>>> trainingData) {

    Map<String,ObjectiveFunctionInterface> objFuncs = new HashMap();
    Map<String,MultiDocumentCliqueDataset> datasets = new HashMap();

    Map<String,Double> adaptSigmas = new HashMap();
    if (flags.transferSigmas != null) {
      for (String bit : flags.transferSigmas.split(",")) {
        String[] bits = bit.split("=");
        adaptSigmas.put(bits[0], Double.parseDouble(bits[1]));
      }
    }

    Counter<String> domainSizes = new ClassicCounter();

    for (Map.Entry<String,ObjectBank<List<CoreLabel>>> entry : trainingData.entrySet()) {
      String domainName = entry.getKey();
      ObjectBank<List<CoreLabel>> data = entry.getValue();

      Set<String> possibleAnswers = new HashSet();
      for (List<CoreLabel> doc : data) {
        for (CoreLabel word : doc) {
          possibleAnswers.add(word.get(AnswerAnnotation.class));
          domainSizes.incrementCount(domainName);
        }
      }

      if (flags.restrictLabels) {
        String pa = StringUtils.join(possibleAnswers,",");
        System.err.println("======> "+pa);
        answersByDomain.put(domainName, pa);

        for (List<CoreLabel> doc : data) {
          for (CoreLabel word : doc) {
            word.set(PossibleAnswersAnnotation.class, pa);
          }
        }
      }

      MultiDocumentCliqueDataset dataset = new MultiDocumentCliqueDataset(metaInfo, flags, data);
      datasets.put(domainName, dataset);
    }

    for (Map.Entry<String,ObjectBank<List<CoreLabel>>> entry : trainingData.entrySet()) {
      String domainName = entry.getKey();
      if (!adaptSigmas.containsKey(domainName)) {
        double mult = domainSizes.totalCount() / domainSizes.getCount(domainName);
        adaptSigmas.put(domainName, mult * flags.adaptSigma);
      }
    }

    System.err.println(adaptSigmas);

    PrintWriter pw = new PrintWriter(System.err, true);
    for (Map.Entry<String,MultiDocumentCliqueDataset> entry : datasets.entrySet()) {
      String domainName = entry.getKey();
      MultiDocumentCliqueDataset dataset = entry.getValue();
      ObjectiveFunctionInterface objFunc = provider.getObjectiveFunction(dataset);
      objFuncs.put(domainName, objFunc);

      System.err.println("==========================================================");
      System.err.println("domain = "+domainName);
      System.err.println("==========================================================");
      dataset.printStats(pw);

    }

    Interner.getGlobal().clear();
    metaInfo.fm.makeFaster();

    HierarchicalObjectiveFunction func = new HierarchicalObjectiveFunction(objFuncs, metaInfo, flags.sigma, adaptSigmas);

    Minimizer minimizer;
    if (flags.useQN) {
      minimizer = new QNMinimizer(flags.QNsize,flags.useRobustQN);
    } else if (flags.useSGD ) {
      if(flags.tuneSGD){
        StochasticMinimizer sgd = new SGDMinimizer(flags.gainSGD,flags.stochasticBatchSize,flags.SGDPasses);
        Pair<Integer,Double> ret = sgd.tune(func, func.initial(), 5*60*1000);
        flags.stochasticBatchSize = ret.first();
        flags.gainSGD = ret.second();
        sgd = null;
      }
      minimizer = new SGDMinimizer(flags.gainSGD,flags.stochasticBatchSize, flags.SGDPasses, flags.outputIterationsToFile);
    } else {
      minimizer = new CGMinimizer();
    }

    hierarchicalWeights = func.convertWeights(minimizer.minimize(func, flags.tolerance, func.initial(), flags.maxIterations));

    if (flags.saveFeatureIndexToDisk) {
      metaInfo.readFromDisk();
    }
  }



  private void trainHierarchicalBaseline(Map<String,ObjectBank<List<CoreLabel>>> trainingData) {

    Collection<List<CoreLabel>> allData = new ArrayList();

    for (Map.Entry<String,ObjectBank<List<CoreLabel>>> entry : trainingData.entrySet()) {
      String domainName = entry.getKey();
      ObjectBank<List<CoreLabel>> data = entry.getValue();

      if (flags.restrictLabels) {
        Set<String> possibleAnswers = new HashSet();
        for (List<CoreLabel> doc : data) {
          for (CoreLabel word : doc) {
            possibleAnswers.add(word.get(AnswerAnnotation.class));
          }
        }
        String pa = StringUtils.join(possibleAnswers,",");
        System.err.println("======> "+pa);
        answersByDomain.put(domainName, pa);

        for (List<CoreLabel> doc : data) {
          for (CoreLabel word : doc) {
            word.set(PossibleAnswersAnnotation.class, pa);
          }
        }
      }

      allData.addAll(data);
    }

    MultiDocumentCliqueDataset dataset = new MultiDocumentCliqueDataset(metaInfo, flags, allData);

    Interner.getGlobal().clear();
    dataset.metaInfo().fm.makeFaster();

    PrintWriter pw = new PrintWriter(System.err, true);
    dataset.printStats(pw);
    train(dataset, false);
  }

  private void trainFrustratinglyEasy(Map<String,ObjectBank<List<CoreLabel>>> trainingData) {

    Collection<List<CoreLabel>> allData = new ArrayList();

    for (Map.Entry<String,ObjectBank<List<CoreLabel>>> entry : trainingData.entrySet()) {
      String domainName = entry.getKey();
      ObjectBank<List<CoreLabel>> data = entry.getValue();

      if (flags.restrictLabels) {
        Set<String> possibleAnswers = new HashSet();
        for (List<CoreLabel> doc : data) {
          for (CoreLabel word : doc) {
            possibleAnswers.add(word.get(AnswerAnnotation.class));
          }
        }
        String pa = StringUtils.join(possibleAnswers,",");
        System.err.println("======> "+pa);
        answersByDomain.put(domainName, pa);

        for (List<CoreLabel> doc : data) {
          for (CoreLabel word : doc) {
            word.set(PossibleAnswersAnnotation.class, pa);
          }
        }
      }

      for (List<CoreLabel> doc : data) {
        doc.get(0).set(DomainAnnotation.class, domainName);
      }

      allData.addAll(data);
    }

    MultiDocumentCliqueDataset dataset = new MultiDocumentCliqueDataset(metaInfo, flags, allData);

    Interner.getGlobal().clear();
    dataset.metaInfo().fm.makeFaster();

    PrintWriter pw = new PrintWriter(System.err, true);
    dataset.printStats(pw);
    train(dataset, false);
  }


  /**
   * using only the overlapping features of trainingData and testingData to do training
   */
  private void train(MultiDocumentCliqueDataset trainingData, MultiDocumentCliqueDataset testingData, boolean adapt) {
    if (flags.useObservedFeaturesOnly) {
      boolean[] seen = observedFeatures(trainingData);
      keepFeatures(trainingData, seen);
    }

    if (flags.featureCountThreshold > 0) {
      boolean[] keepers = featuresWithCountsGreaterThan(trainingData, flags.featureCountThreshold);
      keepFeatures(trainingData, keepers);
    }

    // restricting the feature set only to those also appear in the testing set
    if (testingData != null) {
      boolean[] seenFeat = seenFeatureOnly(trainingData.metaInfo(), testingData.metaInfo());
      keepFeatures(trainingData, seenFeat);
    }

    if (flags.randomizedRatio < 1.0) {
      boolean[] randomized = randomizedFeatures(trainingData, flags.randomizedRatio);
      System.err.println("Selecting each feature with prob="+flags.randomizedRatio+"....");
      keepFeatures(trainingData, randomized);
    }

    metaInfo = trainingData.metaInfo();
    //System.out.print(metaInfo.toString());
    //metaInfo.printAllFeaturesToFile("allNERFeatures.txt");

    if (flags.saveFeatureIndexToDisk) {
      metaInfo.writeToDisk();
    }

    LogPrior.LogPriorType type = LogPrior.getType(flags.priorType);
    LogPrior prior = new LogPrior(type, flags.sigma, flags.epsilon);

    ObjectiveFunctionInterface func = null;
    if (adapt) {
      System.err.println("(DEBUG): adapt");
      int length = trainingData.metaInfo().numFeatures();
      double[] newWeights = new double[length];
      Arrays.fill(newWeights, 0);
      System.arraycopy(weights, 0, newWeights, 0, weights.length);
      System.err.println("(DEBUG): old weights.length="+weights.length+", new="+newWeights.length);
      prior = LogPrior.getAdaptationPrior(newWeights, prior);
    } else {
      System.err.println("(DEBUG): NOT adapt");
    }

    func = provider.getObjectiveFunction(trainingData);
    func.setPrior(prior);

    Minimizer minimizer;
    if (flags.useQN) {
      minimizer = new QNMinimizer(flags.QNsize,flags.useRobustQN);
    } else if( flags.useInPlaceSGD){
      StochasticInPlaceMinimizer sgdMinimizer = new StochasticInPlaceMinimizer(flags.sigma, flags.SGDPasses, flags.tuneSampleSize);
      if (flags.useSGDtoQN) {
          QNMinimizer qnMinimizer = new QNMinimizer(flags.QNsize,flags.useRobustQN);
          minimizer = new HybridMinimizer(sgdMinimizer, qnMinimizer, flags.SGDPasses);
      } else {
        minimizer = sgdMinimizer;
      }
    } else if (flags.useSGD ) {
      if(flags.tuneSGD){
        StochasticMinimizer sgd = new SGDMinimizer(flags.gainSGD,flags.stochasticBatchSize,flags.SGDPasses);
        Pair<Integer,Double> ret = sgd.tune(func, func.initial(), 5*60*1000);
        flags.stochasticBatchSize = ret.first();
        flags.gainSGD = ret.second();
        sgd = null;
      }
      minimizer = new SGDMinimizer(flags.gainSGD,flags.stochasticBatchSize, flags.SGDPasses, flags.outputIterationsToFile);

    } else if (flags.useSMD ) {
      if(flags.tuneSGD){
        StochasticMinimizer smd = new SMDMinimizer(flags.gainSGD,flags.stochasticBatchSize,StochasticCalculateMethods.GradientOnly,flags.SGDPasses);
        Pair<Integer,Double> ret = smd.tune(func, func.initial(), 5*60*1000);
        flags.stochasticBatchSize = ret.first();
        flags.gainSGD = ret.second();
        smd = null;
      }

      minimizer = new SMDMinimizer(flags.gainSGD,flags.stochasticBatchSize, flags.stochasticMethod,flags.SGDPasses,flags.outputIterationsToFile);

    } else if (flags.useScaledSGD){

      if(flags.tuneSGD){
        StochasticMinimizer scaled = new ScaledSGDMinimizer(flags.gainSGD,flags.stochasticBatchSize,flags.SGDPasses,flags.scaledSGDMethod);
        Pair<Integer,Double> ret = scaled.tune(func, func.initial(), 5*60*1000);
        flags.stochasticBatchSize = ret.first();
        flags.gainSGD = ret.second();
        scaled = null;
      }
      minimizer = new ScaledSGDMinimizer(flags.gainSGD,flags.stochasticBatchSize,flags.SGDPasses,flags.scaledSGDMethod,flags.outputIterationsToFile);
    } else if (flags.useSGDtoQN){
      minimizer = new SGDToQNMinimizer(flags.gainSGD,flags.stochasticBatchSize,flags.SGDPasses,flags.QNPasses,1,flags.QNsize);
    } else if (flags.useStochasticQN){
      minimizer = new SQNMinimizer(flags.QNsize,0.9,flags.stochasticBatchSize,flags.outputIterationsToFile);
    } else {
      minimizer = new CGMinimizer();
    }

    Evaluator[] evaluators = null;
    if (flags.evaluateIters > 0) {
      List<Evaluator> evaluatorList = new ArrayList<Evaluator>();
      evaluatorList.add(new MemoryEvaluator());
      if (flags.evaluateTrain) {
        SequenceClassifierEvaluator evaluator = new SequenceClassifierEvaluator("Train set", this);
        evaluator.setTestData(trainingData);
        evaluator.setEvalCmd(flags.evalCmd);
        evaluatorList.add(evaluator);
      }
      if (testingData != null) {
        SequenceClassifierEvaluator evaluator = new SequenceClassifierEvaluator("Test set", this);
        evaluator.setTestData(testingData);
        evaluator.setEvalCmd(flags.evalCmd);
        evaluatorList.add(evaluator);
      }
      evaluators = new Evaluator[evaluatorList.size()];
      evaluatorList.toArray(evaluators);
    }
    if (minimizer instanceof HasEvaluators) {
      ((HasEvaluators) minimizer).setEvaluators(flags.evaluateIters, evaluators);
    }

    // this is to clear memory because i'm desperate
    if (!true) {
      ((FeatureFactoryWrapper)(metaInfo.featureFactory)).clearSubstrings();
    }

    weights = minimizer.minimize(func, flags.tolerance, func.initial(), flags.maxIterations);

    // this is to clear memory because i'm desperate
    if (!true) {
      minimizer = null;
      for (CliqueDataset ds : trainingData.datasets) {
        ds.features = null;
        ds.labels = null;
        ds.possibleLabels = null;
        ds.maxCliqueLabels = null;
      }
    }

    if (flags.saveFeatureIndexToDisk) {
      metaInfo.readFromDisk();
    }

//     for (int i = 0; i < weights.length; i++) {
//       System.err.println(metaInfo.getFeature(i)+"\t"+weights[i]);
//     }

    if (flags.featureWeightThreshold > 0.0) {
      boolean[] keepers = featuresWithWeightsGreaterThan(trainingData, flags.featureWeightThreshold);
      keepFeatures(trainingData, keepers);

      // retrain

      if (flags.saveFeatureIndexToDisk) {
        metaInfo.writeToDisk();
      }

      func = provider.getObjectiveFunction(trainingData);
      func.setPrior(prior);

      if (minimizer instanceof QNMinimizer) {
        ((QNMinimizer)minimizer).setM(flags.QNsize2);
      }

      weights = minimizer.minimize(func, flags.tolerance, func.initial(), flags.maxIterations);

      if (flags.saveFeatureIndexToDisk) {
        metaInfo.readFromDisk();
      }
    }

    if (flags.removeTopN > 0) {
      for (int i = 0; i < flags.numTimesRemoveTopN; i++) {
        boolean[] keepers = removeTopNWeights(trainingData, flags.removeTopN);
        keepFeatures(trainingData, keepers);

        //         retrain
        if (flags.saveFeatureIndexToDisk) {
          metaInfo.writeToDisk();
        }

        func = provider.getObjectiveFunction(trainingData);
        func.setPrior(prior);
        weights = minimizer.minimize(func, flags.tolerance, func.initial(), flags.maxIterations);

        if (flags.saveFeatureIndexToDisk) {
          metaInfo.readFromDisk();
        }
      }
    }

    if (flags.removeTopNPercent > 0) {
      boolean[] keepers = removeTopNPercentWeights(trainingData, flags.removeTopNPercent);
      keepFeatures(trainingData, keepers);

      //         retrain
      if (flags.saveFeatureIndexToDisk) {
        metaInfo.writeToDisk();
      }

      func = provider.getObjectiveFunction(trainingData);
      func.setPrior(prior);
      weights = minimizer.minimize(func, flags.tolerance, func.initial(), flags.maxIterations);

      if (flags.saveFeatureIndexToDisk) {
        metaInfo.readFromDisk();
      }
    }
  }

  private boolean[] removeTopNPercentWeights(MultiDocumentCliqueDataset data, double ratio) {
    System.err.println("Remove Top N% Weights");
    int n = (int)(ratio * data.metaInfo().numFeatures());
    System.err.println("data size="+data.metaInfo().numFeatures()+" ratio="+ratio+" n="+n);
    return removeTopNWeights(data, n);
  }


  /**
   * This constructor takes the training data and trains to set the weights.  It uses the provided metaInfo and you can disallow additional features
   * by passing a DatasetMetaInfo which has been locked.
   */
  public QueriableSequenceModelFactory(QueriableSequenceModelFactoryDataProvider provider, SeqClassifierFlags flags, Type2FeatureFactory featureFactory, ObjectBank<List<CoreLabel>> trainingData, DatasetMetaInfo metaInfo) {
    this.provider = provider;
    this.flags = flags;
    this.metaInfo = metaInfo;
    if (flags.combo) {
      ((ComboFeatureFactory)featureFactory).setMetaInfo(metaInfo);
    }
//    this.featureFactory = featureFactory;
    train(trainingData, metaInfo, false);
  }

  private boolean[] seenFeatureOnly(DatasetMetaInfo trainMetaInfo, DatasetMetaInfo testMetaInfo) {
    boolean[] features = new boolean[trainMetaInfo.numFeatures()];

    Set trainFeatureSet = new HashSet();
    Set testFeatureSet = new HashSet();
    for (Object o : trainMetaInfo.getFeatures().objectsList()) {
      trainFeatureSet.add(((Pair)o).first);
    }
    for (Object o : testMetaInfo.getFeatures().objectsList()) {
      testFeatureSet.add(((Pair)o).first);
    }

    System.err.println("# of Feature Types in Training:"+trainFeatureSet.size());
    System.err.println("# of Feature Types in  Testing:"+testFeatureSet.size());
    trainFeatureSet.retainAll(testFeatureSet);
    System.err.println("# of Feature Types in Intersection:"+trainFeatureSet.size());
    //for (Object o : trainFeatureSet) {
    //System.err.println(o);
    //}


//    Index newFeatureIndex = new HashIndex();

    for (int i = 0; i < features.length; i++) {
      Object feat = trainMetaInfo.getFeature(i);
      if (trainFeatureSet.contains(((Pair)feat).first())) {
//        System.err.println("(DEBUG): adding "+((Pair)feat).first());
        features[i] = true;
      } else {
//        System.err.println("(DEBUG) not adding "+((Pair)feat).first());
        features[i] = false;
      }
    }

    return features;
  }



  /**
   * This constructor takes the training data and trains to set the weights.
   */
  public QueriableSequenceModelFactory(QueriableSequenceModelFactoryDataProvider provider, SeqClassifierFlags flags, Type2FeatureFactory featureFactory, DatasetMetaInfo metaInfo, double[] parameters) {
    this.provider = provider;
    this.flags = flags;
//    this.featureFactory = featureFactory;
    this.weights = parameters;
    this.metaInfo = metaInfo;
    if (flags.combo) {
      ((ComboFeatureFactory)featureFactory).setMetaInfo(metaInfo);
    }
    if (metaInfo.numFeatures() != parameters.length) {
      throw new RuntimeException("Number of parameters in the parameters array must match the number of features in the DatasetMetaInfo!");
    }
  }

  private void train(ObjectBank<List<CoreLabel>> data, DatasetMetaInfo metaInfo, boolean adapt) {
    train(data, null, metaInfo, adapt);
  }

  private void train(ObjectBank<List<CoreLabel>> train, ObjectBank<List<CoreLabel>> test, DatasetMetaInfo metaInfo, boolean adapt) {
    if (flags.useObservedSequencesOnly) {
      Collection<LabeledClique> observedSequences = observedSequences(train.iterator(), metaInfo);
      metaInfo.setAllowedSequences(observedSequences);
    }

    MultiDocumentCliqueDataset dataset = new MultiDocumentCliqueDataset(metaInfo, flags, train);
    MultiDocumentCliqueDataset testset = (test != null)? new MultiDocumentCliqueDataset(metaInfo, flags, test): null;

    Interner.getGlobal().clear();
    dataset.metaInfo().fm.makeFaster();

    PrintWriter pw = new PrintWriter(System.err, true);
    dataset.printStats(pw);
    train(dataset, testset, adapt);
  }

  private void train(ObjectBank<List<CoreLabel>> train, ObjectBank<List<CoreLabel>> test, boolean adapt) {
    DatasetMetaInfo metaInfo = new DatasetMetaInfo(metaInfo().featureFactory(), flags.backgroundSymbol);
    DatasetMetaInfo testMetaInfo = new DatasetMetaInfo(metaInfo().featureFactory(), flags.backgroundSymbol);

    if (flags.useObservedSequencesOnly) {
      Collection<LabeledClique> observedSequences = observedSequences(train.iterator(), metaInfo);
      metaInfo.setAllowedSequences(observedSequences);
      Collection<LabeledClique> testObservedSequences = observedSequences(test.iterator(), testMetaInfo);
      testMetaInfo.setAllowedSequences(testObservedSequences);
    }


    MultiDocumentCliqueDataset dataset = new MultiDocumentCliqueDataset(metaInfo, flags, train);
    MultiDocumentCliqueDataset testset = new MultiDocumentCliqueDataset(testMetaInfo, flags, test);

    Interner.getGlobal().clear();

    PrintWriter pw = new PrintWriter(System.err, true);
    dataset.printStats(pw);
    train(dataset, testset, adapt);
  }


  public void adapt(ObjectBank<List<CoreLabel>> adaptData) {
    train(adaptData, metaInfo, true);
  }

  private static Collection<LabeledClique> observedSequences(Iterator<List<CoreLabel>> trainingData, DatasetMetaInfo metaInfo) {
    Clique maxClique = metaInfo.getMaxClique();
    int rightWindow = maxClique.maxRight();
    int leftWindow = maxClique.maxLeft();
    int[] labels = new int[maxClique.size()];

    Collection<LabeledClique> seen = new HashSet<LabeledClique>();

    while (trainingData.hasNext()) {
      Arrays.fill(labels, metaInfo.backgroundIndex());
      List<CoreLabel> doc = trainingData.next();
      for (int i = 0; i < doc.size(); i++) {
        metaInfo.addLabel(doc.get(i).get(AnswerAnnotation.class));
      }

      for (int i = 0; i < doc.size()+rightWindow; i++) {
        System.arraycopy(labels, 1, labels, 0, labels.length-1);
        if (i < rightWindow) {
          labels[leftWindow+i] = metaInfo.indexOfLabel(doc.get(i).get(AnswerAnnotation.class));
          continue;
        }

        labels[labels.length-1] = metaInfo.indexOfLabel(doc.get(i+rightWindow).get(AnswerAnnotation.class));
        LabeledClique lc = LabeledClique.valueOf(maxClique, labels);
        seen.add(lc);
      }
    }
      return seen;
  }


  private void train(MultiDocumentCliqueDataset trainingData, boolean adapt) {
    train(trainingData, null, adapt);
  }

  private boolean[] observedFeatures(MultiDocumentCliqueDataset data) {
    boolean[] seen = new boolean[data.metaInfo().numFeatures()];

    for (int docNum = 0; docNum < data.numDocuments(); docNum++) {
      CliqueDataset doc = data.getDocument(docNum);
      for (int datumNum = 0; datumNum < doc.numDatums(); datumNum++) {
        int[] features = doc.features[datumNum].get(doc.maxCliqueLabels[datumNum]).features;
        for (int featureNum = 0; featureNum < features.length; featureNum++) {
          seen[features[featureNum]] = true;
        }
      }
    }

    return seen;
  }

  private  boolean[] featuresWithCountsGreaterThan(MultiDocumentCliqueDataset data, int threshhold) {
    int[] counts = new int[data.metaInfo().numFeatures()];

    for (int docNum = 0; docNum < data.numDocuments(); docNum++) {
      CliqueDataset doc = data.getDocument(docNum);
      for (int datumNum = 0; datumNum < doc.numDatums(); datumNum++) {
        int[] features = doc.features[datumNum].get(doc.maxCliqueLabels[datumNum]).features;
        for (int featureNum = 0; featureNum < features.length; featureNum++) {
          counts[features[featureNum]]++;
        }
      }
    }

    boolean[] features = new boolean[counts.length];
    for (int i = 0; i < features.length; i++) {
      features[i] = (counts[i] > threshhold);
    }

    return features;
  }

  private boolean[] featuresWithWeightsGreaterThan(MultiDocumentCliqueDataset data, double threshhold) {

    boolean[] features = new boolean[data.metaInfo().numFeatures()];

    for (int i = 0; i < features.length; i++) {
      features[i] = (Math.abs(weights[i]) > threshhold);
    }

    return features;
  }

  private boolean[] randomizedFeatures(MultiDocumentCliqueDataset data, double ratio) {
    boolean[] features = new boolean[data.metaInfo().numFeatures()];

    Random rand = new Random();

    for (int i = 0; i < features.length; i++) {
      features[i] = (rand.nextDouble() < ratio);
    }

    return features;
  }

  private boolean[] removeTopNWeights(MultiDocumentCliqueDataset data, int n) {

    int[] topNFeatures = new int[n];
    double[] topNWeights = new double[n];

    Arrays.fill(topNWeights, Double.NEGATIVE_INFINITY);

    double thresh = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < data.metaInfo().numFeatures(); i++) {
      double w = Math.abs(weights[i]);
      if (w > thresh) {
        for (int j = 0; j < n; j++) {
          if (topNWeights[j] == thresh) {
            topNFeatures[j] = i;
            topNWeights[j] = w;
            break;
          }
        }
        thresh = Double.POSITIVE_INFINITY;
        for (int j = 0; j < n; j++) {
          if (topNWeights[j] < thresh) {
            thresh = topNWeights[j];
          }
        }
      }
    }

    boolean[] features = new boolean[data.metaInfo().numFeatures()];
    Arrays.fill(features, true);
    for (int i = 0; i < n; i++) {
      features[topNFeatures[i]] = false;
    }

    return features;
  }


  public void keepFeatures(MultiDocumentCliqueDataset data, boolean[] featuresToKeep) {

    HashIndex newFeatureIndex = new HashIndex();
    for (int i = 0; i < featuresToKeep.length; i++) {
      if (featuresToKeep[i]) {
        newFeatureIndex.add(data.metaInfo().getFeature(i));
      }
    }

    for (int docNum = 0; docNum < data.numDocuments(); docNum++) {
      CliqueDataset doc = data.getDocument(docNum);
      for (int datumNum = 0; datumNum < doc.numDatums(); datumNum++) {
        for (LabeledClique lc : doc.features[datumNum].keySet()) {
          Features featureInfo = doc.features[datumNum].get(lc);
          int[] features = featureInfo.features;
          int num = 0;
          for (int featureNum = 0; featureNum < features.length; featureNum++) {
            if (featuresToKeep[features[featureNum]]) { num++; }
          }
          int[] newFeatures = new int[num];
          float[] newValues = null;
          boolean isBoolean = featureInfo.isBoolean();
          if (!isBoolean) {
            newValues = new float[num];
          }
          int index = 0;
          for (int featureNum = 0; featureNum < features.length; featureNum++) {
            if (featuresToKeep[features[featureNum]]) {
              newFeatures[index] = newFeatureIndex.indexOf(data.metaInfo().getFeature(features[featureNum]));
              if (!isBoolean) {
                newValues[index] = featureInfo.value(featureNum);
              }
              index++;
            }
          }

          featureInfo.features = newFeatures;
          if (!isBoolean) {
            featureInfo.setValues(newValues);
          }
        }
      }
    }

    data.metaInfo().setFeatureIndex(newFeatureIndex);

    data.printStats(new PrintWriter(System.err, true));
  }

  protected void setWeights(double[] weights)
  {
    this.weights = weights;
  }

  public void test(List<CoreLabel> doc, Class<? extends CoreAnnotation<String>> answerField) {
    SequenceModel model = getModel(doc);
    test(model, doc, answerField);
  }

  public void test(List<CoreLabel> doc, String domain, Class<? extends CoreAnnotation<String>> answerField) {
    SequenceModel model = getModel(doc, domain);
    test(model, doc, answerField);
  }

  public void test(SequenceModel model, List<CoreLabel> doc, Class<? extends CoreAnnotation<String>> answerField) {

    BestSequenceFinder tagInference;
    if (flags.inferenceType.equalsIgnoreCase("Viterbi")) {
      tagInference = new ExactBestSequenceFinder();
    } else if (flags.inferenceType.equalsIgnoreCase("Beam")) {
      tagInference = new BeamBestSequenceFinder(flags.beamSize);
    } else {
      throw new RuntimeException("Unknown inference type: "+flags.inferenceType+". Your options are Viterbi|Beam.");
    }

    int[] bestSequence = tagInference.bestSequence(model);

    int pos = model.leftWindow();
    for (CoreLabel fi : doc) {
      String guess = (String) metaInfo.getLabel(bestSequence[pos]);
      fi.remove(AnswerAnnotation.class); // because fake answers will get added during testing
      fi.set(answerField, guess);
      pos++;
    }
  }

  public void test(CliqueDataset dataset)
  {
    QueriableSequenceModel model = provider.getModel(dataset);
//    System.err.println("weights: " + weights.length);
    model.setParameters(weights);
    test(model, dataset.sourceDoc, AnswerAnnotation.class);
  }

  public List<String> getGold(CliqueDataset dataset)
  {
    List<String> list = new ArrayList<String>(dataset.labels.length);
    for (int i = 0; i < dataset.labels.length; i++) {
      String gold = (String) metaInfo.getLabel(dataset.labels[i]);
      list.add(gold);
    }
    return list;
  }

  public List<String> getGuesses(CliqueDataset dataset)
  {
    QueriableSequenceModel model = provider.getModel(dataset);
//    System.err.println("weights: " + weights.length);
    model.setParameters(weights);
    BestSequenceFinder tagInference;
    if (flags.inferenceType.equalsIgnoreCase("Viterbi")) {
      tagInference = new ExactBestSequenceFinder();
    } else if (flags.inferenceType.equalsIgnoreCase("Beam")) {
      tagInference = new BeamBestSequenceFinder(flags.beamSize);
    } else {
      throw new RuntimeException("Unknown inference type: "+flags.inferenceType+". Your options are Viterbi|Beam.");
    }

    int[] bestSequence = tagInference.bestSequence(model);

    List<String> list = new ArrayList<String>(dataset.numDatums());
    int pos = model.leftWindow();
    for (int i = 0; i < dataset.numDatums(); i++) {
      String guess = (String) metaInfo.getLabel(bestSequence[pos]);
      list.add(guess);
      pos++;
    }
    return list;
  }

  public Counter<List<CoreLabel>> testKBest(List<CoreLabel> doc, Class<? extends CoreAnnotation<String>> answerField, int k) {
    SequenceModel model = getModel(doc);

    KBestSequenceFinder tagInference = new KBestSequenceFinder();
    Counter<int[]> bestSequences = tagInference.kBestSequences(model,k);

    Counter<List<CoreLabel>> kBest = new ClassicCounter<List<CoreLabel>>();

    for (int[] seq : bestSequences.keySet()) {
      List<CoreLabel> kth = new ArrayList<CoreLabel>();
      int pos = model.leftWindow();
      for (CoreLabel fi : doc) {
        CoreLabel newFL = new CoreLabel(fi);
        String guess = (String) metaInfo.getLabel(seq[pos]);
        fi.remove(AnswerAnnotation.class); // because fake answers will get added during testing
        newFL.set(answerField, guess);
        kth.add(newFL);
        pos++;
      }
      kBest.setCount(kth, bestSequences.getCount(seq));
    }

    return kBest;
  }

  public Counter<Pair<List<CoreLabel>,Counter<Integer>>> testKBestWithFeatures(List<CoreLabel> doc, Class<? extends CoreAnnotation<String>> answerField, int k) {
    SequenceModel model = getModel(doc);

    KBestSequenceFinder tagInference = new KBestSequenceFinder();
    Counter<int[]> bestSequences = tagInference.kBestSequences(model,k);

    Counter<Pair<List<CoreLabel>,Counter<Integer>>> kBest = new ClassicCounter<Pair<List<CoreLabel>,Counter<Integer>>>();

    for (int[] seq : bestSequences.keySet()) {
      List<CoreLabel> kth = new ArrayList<CoreLabel>();
      int pos = model.leftWindow();
      for (CoreLabel fi : doc) {
        CoreLabel newFL = new CoreLabel(fi);
        String guess = (String) metaInfo.getLabel(seq[pos]);
        fi.remove(AnswerAnnotation.class); // because fake answers will get added during testing
        newFL.set(answerField, guess);
        pos++;
      }
      Counter<Integer> features = ((CRF)model).getFeatures(seq);
      System.err.println("=================================================\n"+features);
      kBest.setCount(new Pair<List<CoreLabel>, Counter<Integer>>(kth, features), bestSequences.getCount(seq));
    }

    return kBest;
  }


  public String getWeightsString() {
    StringBuilder sb = new StringBuilder();
    if (weights == null) {
      throw new RuntimeException("You must train before you can print the weights!");
    }
    for (int i = 0; i < weights.length; i++) {
      Object f = metaInfo.getFeature(i);
      sb.append("\t" + f.toString() + ": " + weights[i] + "\n");
    }

    return sb.toString();
  }

  /**
   * This method takes a document and converts it into a sequence
   * model.  The DatasetMetaInfo from the training data is
   * used for determining features, possible label sequences, etc.
   * The weights which were either learned or passed in the
   * constructor of this class.
   */
  public QueriableSequenceModel getModel(List<CoreLabel> doc) {
    metaInfo.lock();
    CliqueDataset dataset = new CliqueDataset(metaInfo, flags, doc);
    metaInfo.unlock();
    QueriableSequenceModel model =  provider.getModel(dataset);
    model.setParameters(weights);
    return model;
  }

  public void printHierarchicalWeights() {
    if (hierarchicalWeights != null) {
      List<String> domains = new ArrayList();
      domains.add(null);
      for (String d : hierarchicalWeights.keySet()) {
        if (d != null) { domains.add(d); }
      }
      for (int i = 0; i < metaInfo.numFeatures(); i++) {
        System.err.println(metaInfo.getLabels());
        System.err.println(metaInfo.getFeature(i));
        for (String domain : domains) {
          System.err.println("\t"+domain+"\t"+hierarchicalWeights.get(domain)[i]);
        }
      }
    }
  }

  public QueriableSequenceModel getModel(List<CoreLabel> doc, String domainName) {

//     for (int i = 0; i < metaInfo.numFeatures(); i++) {
//       ImmutablePairOfImmutables pair = (ImmutablePairOfImmutables)metaInfo.getFeature(i);
//       String feat = (String) pair.first();
//       LabeledClique lc = (LabeledClique) pair.second();
//       System.err.println(feat+" :: "+lc.toString(metaInfo.getLabels()));
//       for (String dn : hierarchicalWeights.keySet()) {
//         System.err.println("\t"+dn+" ==> "+hierarchicalWeights.get(dn)[i]);
//       }
//     }

    if (flags.doFE) {
//      System.err.println(domainName);
      doc.get(0).set(DomainAnnotation.class, domainName);
    }

    String possibleAnswers = (answersByDomain == null ? null : answersByDomain.get(domainName));
    if (possibleAnswers != null) {
      for (CoreLabel word : doc) {
        word.set(PossibleAnswersAnnotation.class, possibleAnswers);
      }
    }

    metaInfo.lock();
    CliqueDataset dataset = new CliqueDataset(metaInfo, flags, doc);
    metaInfo.unlock();
    QueriableSequenceModel model =  provider.getModel(dataset);
    double[] weights;
    if (hierarchicalWeights == null) {
      weights = this.weights;
    } else {
      if (!hierarchicalWeights.containsKey(domainName)) {
        System.err.println("cannot find weights for domain name: "+domainName+".  Using top level weights instead");
        domainName = null;
      }
      weights = hierarchicalWeights.get(domainName);
    }
    System.err.println(weights.length);
    model.setParameters(weights);
    return model;
  }


}
