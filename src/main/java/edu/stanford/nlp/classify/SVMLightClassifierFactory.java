package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.GoldenSectionLineSearch;
import edu.stanford.nlp.optimization.LineSearcher;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;


/**
 * This class is meant for training SVMs ({@link SVMLightClassifier}s).  It actually calls SVM Light, or
 * SVM Struct for multiclass SVMs, or SVM perf is the option is enabled, on the command line, reads in the produced
 * model file and creates a Linear Classifier.  A Platt model is also trained
 * (unless otherwise specified) on top of the SVM so that probabilities can
 * be produced. For multiclass classifier, you have to set C using setC otherwise the code will not run (by sonalg).
 *
 * @author Jenny Finkel
 * @author Aria Haghighi
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (templatization)
 */
public class SVMLightClassifierFactory<L, F> implements ClassifierFactory<L, F, SVMLightClassifier<L,F>>{ //extends AbstractLinearClassifierFactory {

  private static final long serialVersionUID = 1L;

  /**
   * C can be tuned using held-out set or cross-validation.
   * For binary SVM, if C=0, svmlight uses default of 1/(avg x*x).
   */
  protected double C = -1.0;
  private boolean useSigmoid = false;
  protected boolean verbose = true;
  private String svmLightLearn = "/u/nlp/packages/svm_light/svm_learn";
  private String svmStructLearn = "/u/nlp/packages/svm_multiclass/svm_multiclass_learn";
  private String svmPerfLearn = "/u/nlp/packages/svm_perf/svm_perf_learn";
  private String svmLightClassify = "/u/nlp/packages/svm_light/svm_classify";
  private String svmStructClassify = "/u/nlp/packages/svm_multiclass/svm_multiclass_classify";
  private String svmPerfClassify = "/u/nlp/packages/svm_perf/svm_perf_classify";

  private boolean useAlphaFile = false;
  protected File alphaFile;
  private boolean deleteTempFilesOnExit = true;
  private int svmLightVerbosity = 0;  // not verbose
  private boolean doEval = false;
  private boolean useSVMPerf = false;

  final static Redwood.RedwoodChannels logger = Redwood.channels(SVMLightClassifierFactory.class);

  /** @param svmLightLearn is the fullPathname of the training program of svmLight with default value "/u/nlp/packages/svm_light/svm_learn"
   * @param svmStructLearn is the fullPathname of the training program of svmMultiClass with default value "/u/nlp/packages/svm_multiclass/svm_multiclass_learn"
   * @param svmPerfLearn is the fullPathname of the training program of svmMultiClass with default value "/u/nlp/packages/svm_perf/svm_perf_learn"
   */
  public SVMLightClassifierFactory(String svmLightLearn, String svmStructLearn, String svmPerfLearn){
    this.svmLightLearn = svmLightLearn;
    this.svmStructLearn = svmStructLearn;
    this.svmPerfLearn = svmPerfLearn;
  }

  public SVMLightClassifierFactory(){
  }

  public SVMLightClassifierFactory(boolean useSVMPerf){
    this.useSVMPerf = useSVMPerf;
  }

  /**
   * Set the C parameter (for the slack variables) for training the SVM.
   */
  public void setC(double C) {
    this.C = C;
  }

  /**
   * Get the C parameter (for the slack variables) for training the SVM.
   */

  public double getC() {
    return C;
  }

  /**
   * Specify whether or not to train an overlying platt (sigmoid)
   * model for producing meaningful probabilities.
   */
  public void setUseSigmoid(boolean useSigmoid) {
    this.useSigmoid = useSigmoid;
  }

  /**
   * Get whether or not to train an overlying platt (sigmoid)
   * model for producing meaningful probabilities.
   */
  public boolean getUseSigma() {
    return useSigmoid;
  }


  public boolean getDeleteTempFilesOnExitFlag() {
    return deleteTempFilesOnExit;
  }

  public void setDeleteTempFilesOnExitFlag(boolean deleteTempFilesOnExit) {
    this.deleteTempFilesOnExit = deleteTempFilesOnExit;
  }

  /**
   * Reads in a model file in svm light format.  It needs to know if its multiclass or not
   * because it affects the number of header lines.  Maybe there is another way to tell and we
   * can remove this flag?
   */
  private static Pair<Double, ClassicCounter<Integer>> readModel(File modelFile, boolean multiclass) {
    int modelLineCount = 0;
    try {

      int numLinesToSkip = multiclass ? 13 : 10;
      String stopToken   = "#";

      BufferedReader in = new BufferedReader(new FileReader(modelFile));

      for (int i=0; i < numLinesToSkip; i++) {
        in.readLine();
        modelLineCount ++;
      }

      List<Pair<Double, ClassicCounter<Integer>>> supportVectors = new ArrayList<>();
      // Read Threshold
      String thresholdLine = in.readLine();
      modelLineCount ++;
      String[] pieces = thresholdLine.split("\\s+");
      double threshold = Double.parseDouble(pieces[0]);
      // Read Support Vectors
      while (in.ready()) {
        String svLine = in.readLine();
        modelLineCount ++;
        pieces = svLine.split("\\s+");
        // First Element is the alpha_i * y_i
        double  alpha = Double.parseDouble(pieces[0]);
        ClassicCounter<Integer> supportVector  = new ClassicCounter<>();
        for (int i=1; i < pieces.length; ++i) {
          String piece = pieces[i];
          if (piece.equals(stopToken)) break;
          // Each in featureIndex:num class
          String[] indexNum = piece.split(":");
          String featureIndex = indexNum[0];
          // mihai: we may see "qid" as indexNum[0]. just skip this piece. this is the block id useful only for reranking, which we don't do here.
          if(! featureIndex.equals("qid")){
            double count = Double.parseDouble(indexNum[1]);
            supportVector.incrementCount(Integer.valueOf(featureIndex), count);
          }
        }
        supportVectors.add(new Pair<>(alpha, supportVector));
      }

      in.close();

      return new Pair<>(threshold, getWeights(supportVectors));
    }
    catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Error reading SVM model (line " + modelLineCount + " in file " + modelFile.getAbsolutePath() + ")");
    }
  }

  /**
   * Takes all the support vectors, and their corresponding alphas, and computes a weight
   * vector that can be used in a vanilla LinearClassifier.  This only works because
   * we are using a linear kernel.  The Counter is over the feature indices (+1 cos for
   * some reason svm_light is 1-indexed), not features.
   */
  private static ClassicCounter<Integer> getWeights(List<Pair<Double, ClassicCounter<Integer>>> supportVectors) {
    ClassicCounter<Integer> weights = new ClassicCounter<>();
    for (Pair<Double, ClassicCounter<Integer>> sv : supportVectors) {
      ClassicCounter<Integer> c = new ClassicCounter<>(sv.second());
      Counters.multiplyInPlace(c, sv.first());
      Counters.addInPlace(weights, c);
    }
    return weights;
  }

  /**
   * Converts the weight Counter to be from indexed, svm_light format, to a format
   * we can use in our LinearClassifier.
   */
  private ClassicCounter<Pair<F, L>> convertWeights(ClassicCounter<Integer> weights, Index<F> featureIndex, Index<L> labelIndex, boolean multiclass) {
    return multiclass ? convertSVMStructWeights(weights, featureIndex, labelIndex) : convertSVMLightWeights(weights, featureIndex, labelIndex);
  }

  /**
   * Converts the svm_light weight Counter (which uses feature indices) into a weight Counter
   * using the actual features and labels.  Because this is svm_light, and not svm_struct, the
   * weights for the +1 class (which correspond to labelIndex.get(0)) and the -1 class
   * (which correspond to labelIndex.get(1)) are just the negation of one another.
   */
  private ClassicCounter<Pair<F, L>> convertSVMLightWeights(ClassicCounter<Integer> weights, Index<F> featureIndex, Index<L> labelIndex) {
    ClassicCounter<Pair<F, L>> newWeights = new ClassicCounter<>();
    for (int i : weights.keySet()) {
      F f = featureIndex.get(i-1);
      double w = weights.getCount(i);
      // the first guy in the labelIndex was the +1 class and the second guy
      // was the -1 class
      newWeights.incrementCount(new Pair<>(f, labelIndex.get(0)),w);
      newWeights.incrementCount(new Pair<>(f, labelIndex.get(1)),-w);
    }
    return newWeights;
  }

  /**
   * Converts the svm_struct weight Counter (in which the weight for a feature/label pair
   * correspondes to ((labelIndex * numFeatures)+(featureIndex+1))) into a weight Counter
   * using the actual features and labels.
   */
  private ClassicCounter<Pair<F, L>> convertSVMStructWeights(ClassicCounter<Integer> weights, Index<F> featureIndex, Index<L> labelIndex) {
    // int numLabels = labelIndex.size();
    int numFeatures = featureIndex.size();
    ClassicCounter<Pair<F, L>> newWeights = new ClassicCounter<>();
    for (int i : weights.keySet()) {
      L l = labelIndex.get((i-1) / numFeatures); // integer division on purpose
      F f = featureIndex.get((i-1) % numFeatures);
      double w = weights.getCount(i);
      newWeights.incrementCount(new Pair<>(f, l),w);
    }

    return newWeights;
  }

  /**
   * Builds a sigmoid model to turn the classifier outputs into probabilities.
   */
  private LinearClassifier<L, L> fitSigmoid(SVMLightClassifier<L, F> classifier, GeneralDataset<L, F> dataset) {
    RVFDataset<L, L> plattDataset = new RVFDataset<>();
    for (int i = 0; i < dataset.size(); i++) {
      RVFDatum<L, F> d = dataset.getRVFDatum(i);
      Counter<L> scores = classifier.scoresOf((Datum<L,F>)d);
      scores.incrementCount(null);
      plattDataset.add(new RVFDatum<>(scores, d.label()));
    }
    LinearClassifierFactory<L, L> factory = new LinearClassifierFactory<>();
    factory.setPrior(new LogPrior(LogPrior.LogPriorType.NULL));
    return factory.trainClassifier(plattDataset);
  }

  /**
   * This method will cross validate on the given data and number of folds
   * to find the optimal C.  The scorer is how you determine what to
   * optimize for (F-score, accuracy, etc).  The C is then saved, so that
   * if you train a classifier after calling this method, that C will be used.
   */
  public void crossValidateSetC(GeneralDataset<L, F> dataset, int numFolds, final Scorer<L> scorer, LineSearcher minimizer) {
    System.out.println("in Cross Validate");

    useAlphaFile = true;
    boolean oldUseSigmoid = useSigmoid;
    useSigmoid = false;

    final CrossValidator<L, F> crossValidator = new CrossValidator<>(dataset, numFolds);
    final ToDoubleFunction<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,CrossValidator.SavedState>> score =
        fold -> {
          GeneralDataset<L, F> trainSet = fold.first();
          GeneralDataset<L, F> devSet = fold.second();
          alphaFile = (File)fold.third().state;
          //train(trainSet,true,true);
          SVMLightClassifier<L, F> classifier = trainClassifierBasic(trainSet);
          fold.third().state = alphaFile;
          return scorer.score(classifier,devSet);
        };

    DoubleUnaryOperator negativeScorer =
        cToTry -> {
          C = cToTry;
          if (verbose) { System.out.print("C = "+cToTry+" "); }
          Double averageScore = crossValidator.computeAverage(score);
          if (verbose) { System.out.println(" -> average Score: "+averageScore); }
          return -averageScore;
        };

    C = minimizer.minimize(negativeScorer);

    useAlphaFile = false;
    useSigmoid = oldUseSigmoid;
  }

  public void heldOutSetC(GeneralDataset<L, F> train, double percentHeldOut, final Scorer<L> scorer, LineSearcher minimizer) {
    Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> data = train.split(percentHeldOut);
    heldOutSetC(data.first(), data.second(), scorer, minimizer);
  }

  /**
   * This method will cross validate on the given data and number of folds
   * to find the optimal C.  The scorer is how you determine what to
   * optimize for (F-score, accuracy, etc).  The C is then saved, so that
   * if you train a classifier after calling this method, that C will be used.
   */
  public void heldOutSetC(final GeneralDataset<L, F> trainSet, final GeneralDataset<L, F> devSet, final Scorer<L> scorer, LineSearcher minimizer) {

    useAlphaFile = true;
    boolean oldUseSigmoid = useSigmoid;
    useSigmoid = false;

    DoubleUnaryOperator negativeScorer =
        cToTry -> {
          C = cToTry;
          SVMLightClassifier<L, F> classifier = trainClassifierBasic(trainSet);
          double score = scorer.score(classifier,devSet);
          return -score;
        };

    C = minimizer.minimize(negativeScorer);

    useAlphaFile = false;
    useSigmoid = oldUseSigmoid;
  }

  private boolean tuneHeldOut = false;
  private boolean tuneCV = false;
  private Scorer<L> scorer = new MultiClassAccuracyStats<>();
  private LineSearcher tuneMinimizer = new GoldenSectionLineSearch(true);
  private int folds;
  private double heldOutPercent;

  public double getHeldOutPercent() {
    return heldOutPercent;
  }

  public void setHeldOutPercent(double heldOutPercent) {
    this.heldOutPercent = heldOutPercent;
  }

  public int getFolds() {
    return folds;
  }

  public void setFolds(int folds) {
    this.folds = folds;
  }

  public LineSearcher getTuneMinimizer() {
    return tuneMinimizer;
  }

  public void setTuneMinimizer(LineSearcher minimizer) {
    this.tuneMinimizer = minimizer;
  }

  public Scorer getScorer() {
    return scorer;
  }

  public void setScorer(Scorer<L> scorer) {
    this.scorer = scorer;
  }

  public boolean getTuneCV() {
    return tuneCV;
  }

  public void setTuneCV(boolean tuneCV) {
    this.tuneCV = tuneCV;
  }

  public boolean getTuneHeldOut() {
    return tuneHeldOut;
  }

  public void setTuneHeldOut(boolean tuneHeldOut) {
    this.tuneHeldOut = tuneHeldOut;
  }

  public int getSvmLightVerbosity() {
    return svmLightVerbosity;
  }

  public void setSvmLightVerbosity(int svmLightVerbosity) {
    this.svmLightVerbosity = svmLightVerbosity;
  }

  public SVMLightClassifier<L, F> trainClassifier(GeneralDataset<L, F> dataset) {
    if (tuneHeldOut) {
      heldOutSetC(dataset, heldOutPercent, scorer, tuneMinimizer);
    } else if (tuneCV) {
      crossValidateSetC(dataset, folds, scorer, tuneMinimizer);
    }
    return trainClassifierBasic(dataset);
  }

  Pattern whitespacePattern = Pattern.compile("\\s+");

  public SVMLightClassifier<L, F> trainClassifierBasic(GeneralDataset<L, F> dataset) {
    Index<L> labelIndex = dataset.labelIndex();
    Index<F> featureIndex = dataset.featureIndex;
    boolean multiclass = (dataset.numClasses() > 2);
    try {

      // this is the file that the model will be saved to
      File modelFile = File.createTempFile("svm-", ".model");
      if (deleteTempFilesOnExit) {
        modelFile.deleteOnExit();
      }

      // this is the file that the svm light formated dataset
      // will be printed to
      File dataFile = File.createTempFile("svm-", ".data");
      if (deleteTempFilesOnExit) {
        dataFile.deleteOnExit();
      }

      // print the dataset
      PrintWriter pw = new PrintWriter(new FileWriter(dataFile));
      dataset.printSVMLightFormat(pw);
      pw.close();

      // -v 0 makes it not verbose
      // -m 400 gives it a larger cache, for faster training
      String cmd = (multiclass ? svmStructLearn : (useSVMPerf ? svmPerfLearn : svmLightLearn)) + " -v " + svmLightVerbosity + " -m 400 ";

      // set the value of C if we have one specified
      if (C > 0.0) cmd = cmd + " -c " + C + " ";  // C value
      else if(useSVMPerf) cmd = cmd + " -c " + 0.01 + " "; //It's required to specify this parameter for SVM perf

      // Alpha File
      if (useAlphaFile) {
        File newAlphaFile = File.createTempFile("svm-", ".alphas");
        if (deleteTempFilesOnExit) {
          newAlphaFile.deleteOnExit();
        }
        cmd = cmd + " -a " + newAlphaFile.getAbsolutePath();
        if (alphaFile != null) {
          cmd = cmd + " -y " + alphaFile.getAbsolutePath();
        }
        alphaFile = newAlphaFile;
      }

      // File and Model Data
      cmd = cmd + " " + dataFile.getAbsolutePath() + " " + modelFile.getAbsolutePath();

      if (verbose) logger.info("<< "+cmd+" >>");

      /*Process p = Runtime.getRuntime().exec(cmd);

      p.waitFor();

      if (p.exitValue() != 0) throw new RuntimeException("Error Training SVM Light exit value: " + p.exitValue());
      p.destroy();   */
      SystemUtils.run(new ProcessBuilder(whitespacePattern.split(cmd)),
        new PrintWriter(System.err), new PrintWriter(System.err));

      if (doEval) {
        File predictFile = File.createTempFile("svm-", ".pred");
        if (deleteTempFilesOnExit) {
          predictFile.deleteOnExit();
        }
        String evalCmd = (multiclass ? svmStructClassify : (useSVMPerf ? svmPerfClassify : svmLightClassify)) + " "
                + dataFile.getAbsolutePath() + " " + modelFile.getAbsolutePath() + " " + predictFile.getAbsolutePath();
        if (verbose) logger.info("<< " + evalCmd + " >>");
        SystemUtils.run(new ProcessBuilder(whitespacePattern.split(evalCmd)),
                new PrintWriter(System.err), new PrintWriter(System.err));
      }
      // read in the model file
      Pair<Double, ClassicCounter<Integer>> weightsAndThresh = readModel(modelFile, multiclass);
      double threshold = weightsAndThresh.first();
      ClassicCounter<Pair<F, L>> weights = convertWeights(weightsAndThresh.second(), featureIndex, labelIndex, multiclass);
      ClassicCounter<L> thresholds = new ClassicCounter<>();
      if (!multiclass) {
        thresholds.setCount(labelIndex.get(0), -threshold);
        thresholds.setCount(labelIndex.get(1), threshold);
      }
      SVMLightClassifier<L, F> classifier = new SVMLightClassifier<>(weights, thresholds);
      if (doEval) {
        File predictFile = File.createTempFile("svm-", ".pred2");
        if (deleteTempFilesOnExit) {
          predictFile.deleteOnExit();
        }
        PrintWriter pw2 = new PrintWriter(predictFile);
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(5);
        for (Datum<L,F> datum:dataset) {
          Counter<L> scores = classifier.scoresOf(datum);
          pw2.println(Counters.toString(scores, nf));
        }
        pw2.close();
      }

      if (useSigmoid) {
        if (verbose) System.out.print("fitting sigmoid...");
        classifier.setPlatt(fitSigmoid(classifier, dataset));
        if (verbose) System.out.println("done");
      }

      return classifier;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

