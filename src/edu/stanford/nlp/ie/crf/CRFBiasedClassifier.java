package edu.stanford.nlp.ie.crf; 
import java.util.*;
import java.util.function.DoubleUnaryOperator;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.optimization.GoldenSectionLineSearch;
import edu.stanford.nlp.optimization.LineSearcher;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * CRFBiasedClassifier is used to adjust the precision-recall tradeoff
 * of any CRF model implemented using CRFClassifier. This adjustment is
 * performed after CRF training.  The method is described in (Minkov,
 * Wang, Tomasic, and Cohen, 2006): "NER Systems that Suit User's
 * Preferences: Adjusting the Recall-Precision Trade-off for Entity
 * Extraction".  CRFBiasedClassifier can import any model serialized
 * with {@link CRFClassifier} and supports most command-line parameters
 * available in {@link CRFClassifier}.  In addition to this,
 * CRFBiasedClassifier also interprets the parameter -classBias, as in:
 * <p><code>
 * java -server -mx500m edu.stanford.nlp.ie.crf.CRFBiasedClassifier -loadClassifier model.gz -testFile test.txt -classBias A:0.5,B:1.5
 * </code>
 * <p>
 * The command above sets a bias of 0.5 towards class A and a bias of
 * 1.5 towards class B. These biases (which internally are treated as
 * feature weights in the log-linear model underpinning the CRF
 * classifier) can take any real value. As the weight of A tends to plus
 * infinity, the classifier will only predict A labels, and as it tends
 * towards minus infinity, it will never predict A labels.
 *
 * @author Michel Galley
 * @author Sonal Gupta (made the class generic)
 */

public class CRFBiasedClassifier<IN extends CoreMap> extends CRFClassifier<IN>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CRFBiasedClassifier.class);

  private static final String BIAS = "@@@DECODING_CLASS_BIAS@@@";
  private boolean testTime = false;


  public CRFBiasedClassifier(Properties props) {
    super(props);
  }

  public CRFBiasedClassifier(SeqClassifierFlags flags) {super(flags); }

  @Override
  public CRFDatum<List<String>, CRFLabel> makeDatum(List<IN> info, int loc, List<FeatureFactory<IN>> featureFactories) {

    pad.set(CoreAnnotations.AnswerAnnotation.class, flags.backgroundSymbol);
    PaddedList<IN> pInfo = new PaddedList<>(info, pad);

    List<List<String>> features = new ArrayList<>();
    Collection<Clique> done = Generics.newHashSet();
    for (int i = 0; i < windowSize; i++) {
      List<String> featuresC = new ArrayList<>();
      List<Clique> windowCliques = FeatureFactory.getCliques(i, 0);
      windowCliques.removeAll(done);
      done.addAll(windowCliques);
      for (Clique c : windowCliques) {
        for (FeatureFactory<IN> featureFactory : featureFactories) {
          featuresC.addAll(featureFactory.getCliqueFeatures(pInfo, loc, c));
        }
        if(testTime && i==0)
          // this feature is only present at test time and only appears
          // in cliques of size 1 (i.e., cliques with window=0)
          featuresC.add(BIAS);
      }
      features.add(featuresC);
    }

    int[] labels = new int[windowSize];
    for (int i = 0; i < windowSize; i++) {
      String answer = pInfo.get(loc + i - windowSize + 1).get(CoreAnnotations.AnswerAnnotation.class);
      labels[i] = classIndex.indexOf(answer);
    }

    return new CRFDatum<>(features, new CRFLabel(labels), null);
  }

  void addBiasFeature() {
    if(!featureIndex.contains(BIAS)) {
      featureIndex.add(BIAS);
      double[][] newWeights = new double[weights.length+1][];
      System.arraycopy (weights,0,newWeights,0,weights.length);
      newWeights[weights.length] = new double[classIndex.size()];
      weights = newWeights;
    }
  }

  public void setBiasWeight(String cname, double weight) {
    int ci = classIndex.indexOf(cname);
    setBiasWeight(ci, weight);
  }

  public void setBiasWeight(int cindex, double weight) {
    addBiasFeature();
    int fi = featureIndex.indexOf(BIAS);
    weights[fi][cindex] = weight;
  }

  @Override
  public List<IN> classify(List<IN> document) {
    testTime = true;
    List<IN> l = super.classify(document);
    testTime = false;
    return l;
  }

  class CRFBiasedClassifierOptimizer implements DoubleUnaryOperator  {
    CRFBiasedClassifier<IN> crf;
    DoubleUnaryOperator evalFunction;

    CRFBiasedClassifierOptimizer(CRFBiasedClassifier<IN> c, DoubleUnaryOperator e) {
      crf = c;
      evalFunction = e;
    }

    @Override
    public double applyAsDouble(double w) {
      crf.setBiasWeight(0,w);
      return evalFunction.applyAsDouble(w);
    }
  }

  /**
   * Adjust the bias parameter to optimize some objective function.
   * Note that this function only tunes the bias parameter of one class
   * (class of index 0), and is thus only useful for binary classification
   * problems.
   */
  public void adjustBias(List<List<IN>> develData, DoubleUnaryOperator evalFunction, double low, double high) {
    LineSearcher ls = new GoldenSectionLineSearch(true,1e-2,low,high);
    CRFBiasedClassifierOptimizer optimizer = new CRFBiasedClassifierOptimizer(this, evalFunction);
    double optVal = ls.minimize(optimizer);
    int bi = featureIndex.indexOf(BIAS);
    log.info("Class bias of "+weights[bi][0]+" reaches optimal value "+optVal);
  }

  /** The main method, which is essentially the same as in CRFClassifier. See the class documentation. */
  public static void main(String[] args) throws Exception {
    log.info("CRFBiasedClassifier invoked at " + new Date()
            + " with arguments:");
    for (String arg : args) {
      log.info(" " + arg);
    }
    log.info();

    Properties props = StringUtils.argsToProperties(args);
    CRFBiasedClassifier<CoreLabel> crf = new CRFBiasedClassifier<>(props);
    String testFile = crf.flags.testFile;
    String loadPath = crf.flags.loadClassifier;

    if (loadPath != null) {
      crf.loadClassifierNoExceptions(loadPath, props);
    } else if (crf.flags.loadJarClassifier != null) {
      crf.loadJarClassifier(crf.flags.loadJarClassifier, props);
    } else {
      crf.loadDefaultClassifier();
    }
    if(crf.flags.classBias != null) {
      StringTokenizer biases = new java.util.StringTokenizer(crf.flags.classBias,",");
      while (biases.hasMoreTokens()) {
        StringTokenizer bias = new java.util.StringTokenizer(biases.nextToken(),":");
        String cname = bias.nextToken();
        double w = Double.parseDouble(bias.nextToken());
        crf.setBiasWeight(cname,w);
        log.info("Setting bias for class "+cname+" to "+w);
      }
    }

    if (testFile != null) {
      DocumentReaderAndWriter<CoreLabel> readerAndWriter = crf.makeReaderAndWriter();
      if (crf.flags.printFirstOrderProbs) {
        crf.printFirstOrderProbs(testFile, readerAndWriter);
      } else if (crf.flags.printProbs) {
        crf.printProbs(testFile, readerAndWriter);
      } else if (crf.flags.useKBest) {
        int k = crf.flags.kBest;
        crf.classifyAndWriteAnswersKBest(testFile, k, readerAndWriter);
      } else {
        crf.classifyAndWriteAnswers(testFile, readerAndWriter, true);
      }
    }
  } // end main

}
