package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.DataCollection;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Pair;
import libsvm.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * A wrapper class for the libSVM SVM implementation. Right now supports only
 * the simplest functionality, but can easily be expanded. UNTESTED
 * 
 * @author Galen Andrew (galand@cs.stanford.edu) Date: May 3, 2004
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (templatization)
 * @author Ramesh Nallapati (nmramesh@cs.stanford.edu) Included GeneralDataset
 *         as an additional input datastructure by overloading the methods. This
 *         will allow us to toggle between SVMClassifier and LogisticClassifier
 *         using the same GeneralDataset. Additionally, the SVMClassifier no
 *         longer has to build its own index but uses that of GeneralDataset.
 *         Also, fixed a bug in classOf() method which crashes because of an
 *         incorrect typecasting. The removed line is retained in the comments.
 *         Date July 21, 2008.
 * @author sonalg Corrected some bugs because index has be >=1 and the indices
 *         have to be ordered (current implementation might take more time since
 *         it orders the nodes according to their index). Also, added
 *         functionality so that you can set the value equal to the value of the
 *         RVFDatum feature. Date 10/01/2011
 */
public class LibSVMClassifier<L, F> implements Classifier<L, F> {

  /**
   *
   */
  private static final long serialVersionUID = 1L;
  Index<L> labelIndex;
  Index<F> featureIndex;
  svm_model model;

  public Collection<L> labels() {
    return labelIndex.objectsList();
  }

  static <L, F> double getFeatureScore(Datum<L, F> example, F feature) {
    if (example instanceof RVFDatum<?, ?>)
      return ((RVFDatum<L, F>) example).getFeatureCount(feature);
    else
      return 1.0;
  }

  static <L, F> Pair<Integer, Double> getIndexAndValue(Index<F> featureIndex, F feature, Datum<L, F> example) {
    // to make sure the index starts from 1; 0 is considered to be kernel
    // similarity
    int featInt = featureIndex.indexOf(feature) + 1;
    return new Pair<Integer, Double>(featInt, getFeatureScore(example, feature));
  }

  Pair<Integer, Double> getIndexAndValue(F feature, Datum<L, F> example) {
    // to make sure the index starts from 1; 0 is considered to be kernel
    // similarity
    int featInt = featureIndex.indexOf(feature) + 1;
    return new Pair<Integer, Double>(featInt, getFeatureScore(example, feature));
  }

  // the indices have to be in an ascending order
  public static class NodeComparator implements Comparator<svm_node> {

    public int compare(svm_node arg0, svm_node arg1) {
      if (arg0.index > arg1.index)
        return 1;
      else if (arg0.index < arg1.index)
        return -1;
      else
        return 0;
    }

  }

  /**
   * cannot use this for one-class svm. use oneClassScoreOf.
   */
  public L classOf(Datum<L, F> example) {
    Collection<F> features = example.asFeatures();
    ArrayList<svm_node> nodes = new ArrayList<svm_node>();
    for (F feature : features) {
      Pair<Integer, Double> indexValue = getIndexAndValue(feature, example);
      nodes.add(newSVMnode(indexValue.first(), indexValue.second()));
    }
    Collections.sort(nodes, new NodeComparator());
    svm_node[] nodeArray = new svm_node[nodes.size()];
    nodes.toArray(nodeArray);
    double predict = svm.svm_predict(model, nodeArray);
    int prediction = (int) Math.round(predict);

    if (model.param.svm_type == svm_parameter.ONE_CLASS) {
      if (prediction == -1)
        return null;
      else
        return labelIndex.get(0);
    }

    return labelIndex.get(prediction);
  }

  /**
   * gives raw scores from the svm. These are *not* probabilities and you can't
   * use argmax of the returned counter to know the class.  (sonalg)
   */
  public Counter<L> scoresOf(Datum<L, F> example) {
    Collection<F> features = example.asFeatures();
    ArrayList<svm_node> nodes = new ArrayList<svm_node>();
    for (F feature : features) {
      int featInt = featureIndex.indexOf(feature);
      nodes.add(newSVMnode(featInt, getFeatureScore(example, feature)));
    }
    Collections.sort(nodes, new NodeComparator());
    svm_node[] nodeArray = new svm_node[nodes.size()];
    nodes.toArray(nodeArray);
    // (svm_node[]) nodes.toArray();
    // libsvm uses doubles as labels (??)
    double[] scores = new double[labels().size()];
    svm.svm_predict_values(model, nodeArray, scores);
    Counter<L> scoresC = new ClassicCounter<L>();
    for (int i = 0; i < scores.length; i++)
      scoresC.setCount(labelIndex.get(model.label[i]), scores[i]);
    return scoresC;

  }

  /**
   * To use this, you need to have getProabilities option true. Note that argmax
   * of output of this function *might* not equivalent to classOf function
   * without the getProbability option. See LibSVM documentation/FAQs for
   * details.  (sonalg)
   */
  public Counter<L> probabilitiesOf(Datum<L, F> example){
    if ((model.param.svm_type == svm_parameter.C_SVC || model.param.svm_type == svm_parameter.NU_SVC)
        && model.probA != null && model.probB != null) {

      Collection<F> features = example.asFeatures();
      ArrayList<svm_node> nodes = new ArrayList<svm_node>();
      for (F feature : features) {
        int featInt = featureIndex.indexOf(feature);
        nodes.add(newSVMnode(featInt, getFeatureScore(example, feature)));
      }
      Collections.sort(nodes, new NodeComparator());
      svm_node[] nodeArray = new svm_node[nodes.size()];
      nodes.toArray(nodeArray);
      // (svm_node[]) nodes.toArray();
      // libsvm uses doubles as labels (??)
      double[] prob = new double[labels().size()];
      svm.svm_predict_probability(model, nodeArray, prob);
      Counter<L> scoresC = new ClassicCounter<L>();
      for (int i = 0; i < prob.length; i++) {
        scoresC.setCount(labelIndex.get(model.label[i]), prob[i]);
      }
      return scoresC;
    } else
      throw new RuntimeException(
          "you can only use this function for some kinds of SVMs. Also check that the probability flag is true");
  }

  public double oneClassScoreOf(Datum<L, F> example) {
    double[] scores = new double[1];
    Collection<F> features = example.asFeatures();
    ArrayList<svm_node> nodes = new ArrayList<svm_node>();
    for (F feature : features) {
      int featInt = featureIndex.indexOf(feature);
      nodes.add(newSVMnode(featInt, getFeatureScore(example, feature)));
    }
    svm_node[] nodeArray = new svm_node[nodes.size()];
    nodes.toArray(nodeArray);
    svm.svm_predict_values(model, nodeArray, scores);
    return scores[0];
  }

  /* Compute 10-fold cross-validation accuracy on these examples
  * using Radial Basis Function kernel with gamma and penalty term C.
  * (Hint: make a Function using this and optimize!)
  */
  public static <L, F> double CVAccuracyRBF(DataCollection<L, F> examples, double gamma, double C) {
    svm_parameter param = newSVMparam();
    param.C = C;
    param.gamma = gamma;
    return CVAccuracy(examples, param, 10);
  }

  public static <L, F> double CVAccuracyLinear(DataCollection<L, F> examples, double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.LINEAR;
    param.C = C;
    return CVAccuracy(examples, param, 10);
  }

  public static <L, F> double CVAccuracyPoly(DataCollection<L, F> examples, double gamma, double coef0, int degree,
      double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.POLY;
    param.gamma = gamma;
    param.coef0 = coef0;
    param.degree = degree;
    param.C = C;
    return CVAccuracy(examples, param, 10);
  }

  public static <L, F> double CVAccuracy(DataCollection<L, F> examples, svm_parameter param, int folds) {
    Index<F> featureIndex = new HashIndex<F>(examples.features());
    Index<L> labelIndex = new HashIndex<L>(examples.labels());
    svm_problem prob = newSVMproblem(examples, featureIndex, labelIndex);
    double[] target = new double[prob.l];
    svm.svm_cross_validation(prob, param, folds, target);
    int numCorrect = 0;
    for (int i = 0; i < target.length; i++) {
      if (prob.y[i] == target[i]) {
        numCorrect++;
      }
    }
    return (double) numCorrect / prob.l;
  }

  public static <L, F> double CVAccuracyRBF(GeneralDataset<L, F> examples, double gamma, double C) {
    svm_parameter param = newSVMparam();
    param.C = C;
    param.gamma = gamma;
    return CVAccuracy(examples, param, 10);
  }

  public static <L, F> double CVAccuracyLinear(GeneralDataset<L, F> examples, double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.LINEAR;
    param.C = C;
    return CVAccuracy(examples, param, 10);
  }

  public static <L, F> double CVAccuracyPoly(GeneralDataset<L, F> examples, double gamma, double coef0, int degree,
      double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.POLY;
    param.gamma = gamma;
    param.coef0 = coef0;
    param.degree = degree;
    param.C = C;
    return CVAccuracy(examples, param, 10);
  }

  public static <L, F> double CVAccuracy(GeneralDataset<L, F> examples, svm_parameter param, int folds) {
    Index<F> featureIndex = examples.featureIndex;
    Index<L> labelIndex = examples.labelIndex;
    svm_problem prob = newSVMproblem(examples, featureIndex, labelIndex);
    double[] target = new double[prob.l];
    svm.svm_cross_validation(prob, param, folds, target);
    int numCorrect = 0;
    for (int i = 0; i < target.length; i++) {
      if (prob.y[i] == target[i]) {
        numCorrect++;
      }
    }
    return (double) numCorrect / prob.l;
  }

  public static <L, F> LibSVMClassifier<L, F> trainLinearSVM(DataCollection<L, F> examples, double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.LINEAR;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  public static <L, F> LibSVMClassifier<L, F> trainLinearOneClassSVM(DataCollection<L, F> examples, double C) {
    svm_parameter param = newSVMparam();
    param.svm_type = svm_parameter.ONE_CLASS;
    param.kernel_type = svm_parameter.LINEAR;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  public static <L, F> LibSVMClassifier<L, F> trainRBFOneClassSVM(DataCollection<L, F> examples, double gamma, double C) {
    svm_parameter param = newSVMparam();
    param.svm_type = svm_parameter.ONE_CLASS;
    param.kernel_type = svm_parameter.RBF;
    param.gamma = gamma;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  public static <L, F> LibSVMClassifier<L, F> trainPolynomialSVM(DataCollection<L, F> examples, double gamma,
      double coef0, int degree, double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.POLY;
    param.gamma = gamma;
    param.coef0 = coef0;
    param.degree = degree;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  public static <L, F> LibSVMClassifier<L, F> trainRBFSVM(DataCollection<L, F> examples, double gamma, double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.RBF;
    param.gamma = gamma;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  private LibSVMClassifier(DataCollection<L, F> examples, svm_parameter param) {
    featureIndex = new HashIndex<F>(examples.features());
    labelIndex = new HashIndex<L>(examples.labels());
    svm_problem prob = newSVMproblem(examples, featureIndex, labelIndex);
    model = libsvm.svm.svm_train(prob, param);
  }

  /**
   * C is the cost for making errors. (default value 1)
   */
  public static <L, F> LibSVMClassifier<L, F> trainLinearSVM(GeneralDataset<L, F> examples, double C) {
    return trainLinearSVM(examples, C, false, true);
  }

  /**
   * C is the cost for making errors. (default value 1)
   */
  public static <L, F> LibSVMClassifier<L, F> trainLinearSVM(GeneralDataset<L, F> examples, double C,
      boolean getProbabilities, boolean shrinking) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.LINEAR;
    param.C = C;
    param.probability = getProbabilities == true ? 1 : 0;
    param.shrinking = shrinking == true ? 1 : 0;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  /**
   * this method uses the dafult value (1) for the cost parameter C)
   */
  public static <L, F> LibSVMClassifier<L, F> trainLinearSVM(GeneralDataset<L, F> examples) {
    return trainLinearSVM(examples, 1.0, false, true);
  }

  public static <L, F> LibSVMClassifier<L, F> trainLinearSVM(GeneralDataset<L, F> examples, svm_parameter param) {
    return new LibSVMClassifier<L, F>(examples, param);
  }

  /**
   * The kernel for the polynomial SVM is given by: (gamma*u'*v + coef0)^degree
   * C is the cost parameter for errors.
   */
  public static <L, F> LibSVMClassifier<L, F> trainPolynomialSVM(GeneralDataset<L, F> examples, double gamma,
      double coef0, int degree, double C) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.POLY;
    param.gamma = gamma;
    param.coef0 = coef0;
    param.degree = degree;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  /**
   * this method uses default parameters for the polynomial kernel gamma = 0
   * results in setting gamma = 1/(num_unique_features) inside libsvm
   */
  public static <L, F> LibSVMClassifier<L, F> trainPolynomialSVM(GeneralDataset<L, F> examples) {
    return trainPolynomialSVM(examples, 0, 0, 3, 1.0);
  }

  /**
   * this method uses default parameters for the polynomial kernel gamma = 0
   * results in setting gamma = 1/(num_unique_features) inside libsvm
   */
  public static <L, F> LibSVMClassifier<L, F> trainPolynomialSVMOfDegree(GeneralDataset<L, F> examples, int degree) {
    return trainPolynomialSVM(examples, 0, 0, degree, 1.0);
  }

  /**
   * this method uses radial basis function as the kernel given by
   * exp(-gamma*|u-v|^2)
   */
  public static <L, F> LibSVMClassifier<L, F> trainRBFSVM(GeneralDataset<L, F> examples, double gamma, double C,
      boolean getProbabilities, boolean shrinking) {
    svm_parameter param = newSVMparam();
    param.kernel_type = svm_parameter.RBF;
    param.gamma = gamma;
    param.C = C;
    param.probability = getProbabilities == true ? 1 : 0;
    param.shrinking = shrinking == true ? 1 : 0;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  /**
   * this method uses the default values for the RBF kernel gamma = 0 results in
   * setting gamma = 1/(num_unique_features) inside libsvm
   */
  public static <L, F> LibSVMClassifier<L, F> trainRBFSVM(GeneralDataset<L, F> examples) {
    return trainRBFSVM(examples, 0, 1.0, false, true);
  }

  /**
   * this method trains using a one class SVM with a linear kernel C is the cost
   * paramter for making errors (defalut value 1).
   */
  public static <L, F> LibSVMClassifier<L, F> trainLinearOneClassSVM(GeneralDataset<L, F> examples, double C) {
    svm_parameter param = newSVMparam();
    param.svm_type = svm_parameter.ONE_CLASS;
    param.kernel_type = svm_parameter.LINEAR;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  /**
   * this method uses a one class SVM with linear kernel with the dafault value
   * for cost paramter (1)
   */
  public static <L, F> LibSVMClassifier<L, F> trainLinearOneClassSVM(GeneralDataset<L, F> examples) {
    return trainLinearOneClassSVM(examples, 1.0);
  }

  public static <L, F> LibSVMClassifier<L, F> trainLinearOneClassSVM(GeneralDataset<L, F> examples, double C,
      double nu, boolean getProbabilities, boolean shrinking) {
    svm_parameter param = newSVMparam();
    param.svm_type = svm_parameter.ONE_CLASS;
    param.kernel_type = svm_parameter.LINEAR;
    param.nu = nu;
    param.C = C;
    param.probability = getProbabilities == true ? 1 : 0;
    param.shrinking = shrinking == true ? 1 : 0;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  public static <L, F> LibSVMClassifier<L, F> trainRBFOneClassSVM(GeneralDataset<L, F> examples, double gamma, double C) {
    svm_parameter param = newSVMparam();
    param.svm_type = svm_parameter.ONE_CLASS;
    param.kernel_type = svm_parameter.RBF;
    param.gamma = gamma;
    param.C = C;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  public static <L, F> LibSVMClassifier<L, F> trainRBFOneClassSVM(GeneralDataset<L, F> examples, double gamma,
      double C, double nu, boolean getProbabilities, boolean shrinking) {
    svm_parameter param = newSVMparam();
    param.svm_type = svm_parameter.ONE_CLASS;
    param.kernel_type = svm_parameter.RBF;
    param.gamma = gamma;
    param.nu = nu;
    param.C = C;
    param.probability = getProbabilities == true ? 1 : 0;
    param.shrinking = shrinking == true ? 1 : 0;
    return new LibSVMClassifier<L, F>(examples, param);
  }

  private LibSVMClassifier(GeneralDataset<L, F> examples, svm_parameter param) {
    featureIndex = examples.featureIndex;
    labelIndex = examples.labelIndex;
    svm_problem prob = newSVMproblem(examples, featureIndex, labelIndex);
    model = libsvm.svm.svm_train(prob, param);
  }

  // uses default values given in svm_toy
  private static svm_parameter newSVMparam() {
    svm_parameter param = new svm_parameter();
    param.svm_type = svm_parameter.C_SVC;
    param.kernel_type = svm_parameter.RBF;
    param.degree = 3;
    param.gamma = 0;
    param.coef0 = 0;
    param.nu = 0.5;
    param.cache_size = 40;
    param.C = 1;
    param.eps = 1e-3;
    param.p = 0.1;
    param.shrinking = 1;
    param.nr_weight = 0;
    param.weight_label = new int[0];
    param.weight = new double[0];
    param.probability = 0;
    return param;
  }

  private static <L, F> svm_problem newSVMproblem(DataCollection<L, F> examples, Index<F> featureIndex,
      Index<L> labelIndex) {
    svm_problem problem = new svm_problem();
    int numTrain = examples.size();
    problem.l = numTrain;
    problem.x = new svm_node[numTrain][];
    problem.y = new double[numTrain];
    for (int i = 0, num = examples.size(); i < num; i++) {
      Datum<L, F> example = examples.getDatum(i);
      Collection<F> features = example.asFeatures();
      ArrayList<svm_node> nodes = new ArrayList<svm_node>();
      for (F feature : features) {
        Pair<Integer, Double> indexValue = getIndexAndValue(featureIndex, feature, example);
        nodes.add(newSVMnode(indexValue.first(), indexValue.second()));
      }
      Collections.sort(nodes, new NodeComparator());
      problem.x[i] = new svm_node[nodes.size()];
      nodes.toArray(problem.x[i]);
      problem.y[i] = labelIndex.indexOf(example.label());
    }

    return problem;
  }

  private static <L, F> svm_problem newSVMproblem(GeneralDataset<L, F> examples, Index<F> featureIndex,
      Index<L> labelIndex) {
    svm_problem problem = new svm_problem();
    int numTrain = examples.size();
    problem.l = numTrain;
    problem.x = new svm_node[numTrain][];
    problem.y = new double[numTrain];

    for (int i = 0, num = examples.size(); i < num; i++) {
      Datum<L, F> example = examples.getDatum(i);
      Collection<F> features = example.asFeatures();
      ArrayList<svm_node> nodes = new ArrayList<svm_node>();
      for (F feature : features) {
        Pair<Integer, Double> indexValue = getIndexAndValue(featureIndex, feature, example);
        nodes.add(newSVMnode(indexValue.first(), indexValue.second()));
      }
      Collections.sort(nodes, new NodeComparator());
      problem.x[i] = new svm_node[nodes.size()];
      nodes.toArray(problem.x[i]);
      problem.y[i] = labelIndex.indexOf(example.label());

    }

    return problem;

  }

  private static svm_node newSVMnode(int index, double value) {
    svm_node node = new svm_node();
    node.index = index;
    node.value = value;
    return node;
  }

}
