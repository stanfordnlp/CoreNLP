package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;

/**
 * An ensemble classifier that combines the classification decisions of multiple {@link CMMClassifier}s.
 * This class assumes that all of the classifiers in the ensemble were trained on the same
 * training data, and therefore recognize the same set of labels.
 * At test time, make sure the map/testMap property includes all of the columns used by <b>any</b> classifier.
 * <p/>
 * <p/>
 * Properties:<br />
 * trainProps - a semi-colon delimited list of properties files for the individual CMMClassifiers to train.
 * e.g. conll1.prop;conll2.prop <br />
 * classifiers - a semi-colon delimited list of serialized classifiers to load. <br />
 * </p>
 * <p/>
 * The semantics of some of the CMM properties are different:<br />
 * trainFile - the heldOut dataset used to train the interpolation weights <br />
 * </p>
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class CMMEnsembleClassifier extends CMMClassifier {
  private Map<CMMClassifier,Double> classifiers; // a map of the classifiers to their interpolation weights

  public CMMEnsembleClassifier(Properties props) {
    super(props);
    classifiers = new HashMap<CMMClassifier,Double>();
  }

  /**
   * Adds a classifier to the ensemble with a weight of 0.0.
   * (Calls {@link #addClassifier(CMMClassifier, double) addClassifier(c, 0.0)})
   */
  public void addClassifier(CMMClassifier c) {
    addClassifier(c, 0.0);
  }

  /**
   * Adds a classifier to the ensemble with a given weight.
   */
  public void addClassifier(CMMClassifier c, double weight) {
    classifiers.put(c, new Double(weight));
  }

  /**
   * Trains the interpolation weight.
   */
  public void train(List train) {
    System.err.println("Training interpolation weights...");
    double[] cll = new double[classifiers.size()];
    List<CMMClassifier> ordered = new ArrayList<CMMClassifier>(classifiers.keySet());
    for (int i = 0; i < ordered.size(); i++) {
      CMMClassifier c = ordered.get(i);
      cll[i] = c.loglikelihood(train);
    }

    int numClassifiers = classifiers.size();
    DiffFunction[] ineqConstraints = new DiffFunction[numClassifiers];
    for (int i = 0; i < numClassifiers; i++) {
      ineqConstraints[i] = new ComponentMinimumConstraint(numClassifiers, i, 0.0);
    }

    ConstrainedMinimizer minimizer = new PenaltyConstrainedMinimizer(new CGMinimizer());
    double[] weights = minimizer.minimize(new InterpolationDiffFunction(cll), flags.tolerance, new DiffFunction[]{new SumToOneConstraintFunction(numClassifiers)}, flags.tolerance, ineqConstraints, flags.tolerance, new double[classifiers.size()]);
    for (int i = 0; i < ordered.size(); i++) {
      CMMClassifier c = ordered.get(i);
      classifiers.put(c, new Double(weights[i]));
    }
    System.err.print("Interpolation weights: [");
    for (int i = 0; i < weights.length; i++) {
      if (i > 0) {
        System.err.print(" ");
      }
      System.err.print(weights[i]);
    }
    System.err.println("]");
    //return train;
  }

  private static class InterpolationDiffFunction implements DiffFunction {
    private double[] cll; // the conditional log likelihood of the training set for each classifier

    public InterpolationDiffFunction(double[] cll) {
      this.cll = cll;
    }

    public double[] derivativeAt(double[] x) {
      double[] derivative = new double[cll.length];
      for (int i = 0; i < cll.length; i++) {
        derivative[i] = cll[i];
      }
      return derivative;
    }

    public double valueAt(double[] x) {
      double val = 0.0;
      for (int i = 0; i < cll.length; i++) {
        val += x[i] * cll[i];
      }
      return val;
    }

    public int domainDimension() {
      return cll.length;
    }
  }

  private static class SumToOneConstraintFunction implements DiffFunction {
    int dimension;

    SumToOneConstraintFunction(int dimension) {
      this.dimension = dimension;
    }

    public double valueAt(double[] x) {
      double sum = 0.0;
      for (int i = 0; i < x.length; i++) {
        sum += x[i];
      }
      return sum - 1.0;
    }

    public int domainDimension() {
      return dimension;
    }

    public double[] derivativeAt(double[] x) {
      double[] d = new double[x.length];
      Arrays.fill(d, 1.0);
      return d;
    }
  }


  /**
   * Returns the score given by the ensemble at the given position within the document.
   */
  @Override
  public Counter scoresOf(List lineInfos, int pos) {
    Counter scores = new ClassicCounter();
    for (CMMClassifier c : classifiers.keySet()) {
      Double weight = classifiers.get(c);
      Counter s = c.scoresOf(lineInfos, pos);
      Counters.addInPlace(scores, Counters.scale(s, weight.doubleValue()));
    }
    return scores;

  }

  /**
   * Returns the most likely class as determined by the ensemble.
   */
  @Override
  protected String classOf(List lineInfos, int pos) {
    Counter c = scoresOf(lineInfos, pos);
    return (String) Counters.argmax(c);
  }

  /**
   * Writes out the ensemble in a simple format where each line contains a
   * <i>classifier=weight</i> statement.
   */
  @Override
  public void serializeClassifier(String serializePath) {
    System.err.println("Serializing ensemble to " + serializePath);
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(serializePath));
      for (CMMClassifier c : classifiers.keySet()) {
        bw.write(c.flags.serializeTo);
        bw.write('=');
        bw.write(classifiers.get(c).toString());
        bw.write('\n');
      }
      bw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void loadClassifier(String loadPath) {
    try {
      BufferedReader br = new BufferedReader(new FileReader(loadPath));
      String line;
      while ((line = br.readLine()) != null) {
        String[] pair = line.split("=", 2);
        if (pair.length == 2) {
          CMMClassifier c = new CMMClassifier();
          c.loadClassifier(pair[0]);
          addClassifier(c, Double.parseDouble(pair[1]));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    String trainPropsString = (String) props.remove("trainProps");
    String serializedClassifiers = (String) props.remove("classifiers");
    CMMEnsembleClassifier ensemble = new CMMEnsembleClassifier(props);
    List<String> serialized = new ArrayList<String>();


    if (trainPropsString != null) {
      String[] trainProps = trainPropsString.split(";");
      for (int i = 0; i < trainProps.length; i++) {
        Properties p = new Properties();
        try {
          p.load(new BufferedInputStream(new FileInputStream(trainProps[i])));
        } catch (IOException e) {
          System.err.println("Could not load Properties file: " + trainProps[i]);
          continue;
        }
        CMMClassifier c = new CMMClassifier(p);
        c.train();
        c.serializeClassifier(c.flags.serializeTo);
        serialized.add(c.flags.serializeTo);
      }
    }

    if (ensemble.flags.loadClassifier != null) {
      ensemble.loadClassifier(ensemble.flags.loadClassifier);
    } else {


      if (serializedClassifiers != null) {
        serialized.addAll(Arrays.asList(serializedClassifiers.split(";")));
      }

      if (serialized.isEmpty()) {
        System.err.println("Must either train or specify classifiers for the ensemble.");
        System.exit(1);
      }
      for (String aSerialized : serialized) {
        CMMClassifier c = new CMMClassifier();
        String path = aSerialized;
        c.loadClassifierNoExceptions(path);
        c.flags.serializeTo = path;
        // HN: TODO: don't hardcode weights
        ensemble.addClassifier(c, 0.5);
      }
    }
    if (ensemble.flags.trainFile != null) {
      ensemble.train();
      if (ensemble.flags.serializeTo != null) {
        ensemble.serializeClassifier(ensemble.flags.serializeTo);
      }
    }

    // Make sure necessary variables are set for test time
    for (CMMClassifier c : ensemble.classifiers.keySet()) {
      if (ensemble.classIndex == null) {
        ensemble.classIndex = c.classIndex;
      }
      if (ensemble.answerArrays == null) {
        ensemble.answerArrays = c.answerArrays;
      }
      ensemble.flags.maxLeft = Math.max(ensemble.flags.maxLeft, c.flags.maxLeft);

      if (c.flags.useTaggySequences) {
        ensemble.flags.useTaggySequences = true;
      }
      if (c.flags.usePrevSequences) {
        ensemble.flags.usePrevSequences = true;
      }
      if (c.flags.useNextSequences) {
        ensemble.flags.useNextSequences = true;
      }
      if (c.flags.useSequences) {
        ensemble.flags.useSequences = true;
      }
    }

    if (ensemble.flags.testFile != null) {
      ensemble.classifyAndWriteAnswers(ensemble.flags.testFile,
                                       ensemble.makeReaderAndWriter());
    }
  }
}
