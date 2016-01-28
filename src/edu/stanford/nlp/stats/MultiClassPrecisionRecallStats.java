package edu.stanford.nlp.stats;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.ProbabilisticClassifier;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Triple;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jenny Finkel
 */
public class MultiClassPrecisionRecallStats<L> implements Scorer<L> {
  /**
   * Count of true positives.
   */
  protected int[] tpCount;
  
  /**
   * Count of false positives.
   */
  protected int[] fpCount;

  /**
   * Count of false negatives.
   */
  protected int[] fnCount;

  protected Index<L> labelIndex;
  protected L negLabel;
  protected int negIndex = -1;

  public <F> MultiClassPrecisionRecallStats(Classifier<L,F> classifier, GeneralDataset<L,F> data, L negLabel) 
  {
    this.negLabel = negLabel;
    score(classifier, data);
  }

  public MultiClassPrecisionRecallStats(L negLabel) 
  {
    this.negLabel = negLabel;
  }

  public L getNegLabel() {
      return negLabel;
  }

  public <F> double score(ProbabilisticClassifier<L,F> classifier, GeneralDataset<L,F> data) {
    return score((Classifier<L,F>)classifier, data);
  }

  public <F> double score(Classifier<L,F> classifier, GeneralDataset<L,F> data) {

    List<L> guesses = new ArrayList<L>();
    List<L> labels = new ArrayList<L>();

    for (int i = 0; i < data.size(); i++) {
      Datum<L, F> d = data.getRVFDatum(i);
      L guess = classifier.classOf(d);
      guesses.add(guess);
    }      

    int[] labelsArr = data.getLabelsArray();
    labelIndex = data.labelIndex;
    for (int i = 0; i < data.size(); i++) {
      labels.add(labelIndex.get(labelsArr[i]));
    }

    labelIndex = new HashIndex<L>();
    labelIndex.addAll(data.labelIndex().objectsList());
    labelIndex.addAll(classifier.labels());

    int numClasses = labelIndex.size();
    tpCount = new int[numClasses];
    fpCount = new int[numClasses];
    fnCount = new int[numClasses];

    negIndex = labelIndex.indexOf(negLabel);

    for (int i=0; i < guesses.size(); ++i)
    {
      L guess = guesses.get(i);
      int guessIndex = labelIndex.indexOf(guess);
      L label = labels.get(i);
      int trueIndex = labelIndex.indexOf(label);
      
      if (guessIndex == trueIndex) {
        if (guessIndex != negIndex) {
          tpCount[guessIndex]++;
        }
      } else {
        if (guessIndex != negIndex) {
          fpCount[guessIndex]++;
        }
        if (trueIndex != negIndex) {
          fnCount[trueIndex]++;
        }
      }
    }
    
    return getFMeasure();
  }

  /**
   * Returns the current precision: <tt>tp/(tp+fp)</tt>.
   * Returns 1.0 if tp and fp are both 0.
   */
  public Triple<Double, Integer, Integer> getPrecisionInfo(L label) {
    int i = labelIndex.indexOf(label);
    if (tpCount[i] == 0 && fpCount[i] == 0) {
      return new Triple<Double, Integer, Integer>(1.0, tpCount[i], fpCount[i]);
    }
    return new Triple<Double, Integer, Integer>((((double) tpCount[i]) / (tpCount[i] + fpCount[i])), tpCount[i], fpCount[i]);
  }

  public double getPrecision(L label) {
    return getPrecisionInfo(label).first();
  }

  public Triple<Double, Integer, Integer> getPrecisionInfo() {
    int tp = 0, fp = 0;
    for (int i = 0; i < labelIndex.size(); i++) {
      if (i == negIndex) { continue; }
      tp += tpCount[i];
      fp += fpCount[i];
    }
    return new Triple<Double, Integer, Integer>((((double) tp) / (tp + fp)), tp, fp);
  }

  public double getPrecision() {
    return getPrecisionInfo().first();
  }

  /**
   * Returns a String summarizing precision that will print nicely.
   */
  public String getPrecisionDescription(int numDigits) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);
    Triple<Double, Integer, Integer> prec = getPrecisionInfo();
    return nf.format(prec.first()) + "  (" + prec.second() + "/" + (prec.second() + prec.third()) + ")";
  }

  public String getPrecisionDescription(int numDigits, L label) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);
    Triple<Double, Integer, Integer> prec = getPrecisionInfo(label);
    return nf.format(prec.first()) + "  (" + prec.second() + "/" + (prec.second() + prec.third()) + ")";
  }

  public Triple<Double, Integer, Integer> getRecallInfo(L label) {
    int i = labelIndex.indexOf(label);
    if (tpCount[i] == 0 && fnCount[i] == 0) {
      return new Triple<Double, Integer, Integer>(1.0, tpCount[i], fnCount[i]);
    }
    return new Triple<Double, Integer, Integer>((((double) tpCount[i]) / (tpCount[i] + fnCount[i])), tpCount[i], fnCount[i]);
  }

  public double getRecall(L label) {
    return getRecallInfo(label).first();
  }

  public Triple<Double, Integer, Integer> getRecallInfo() {
    int tp = 0, fn = 0;
    for (int i = 0; i < labelIndex.size(); i++) {
      if (i == negIndex) { continue; }
      tp += tpCount[i];
      fn += fnCount[i];
    }
    return new Triple<Double, Integer, Integer>((((double) tp) / (tp + fn)), tp, fn);
  }

  public double getRecall() {
    return getRecallInfo().first();
  }

  /**
   * Returns a String summarizing precision that will print nicely.
   */
  public String getRecallDescription(int numDigits) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);
    Triple<Double, Integer, Integer> recall = getRecallInfo();
    return nf.format(recall.first()) + "  (" + recall.second() + "/" + (recall.second() + recall.third()) + ")";
  }

  public String getRecallDescription(int numDigits, L label) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);
    Triple<Double, Integer, Integer> recall = getRecallInfo(label);
    return nf.format(recall.first()) + "  (" + recall.second() + "/" + (recall.second() + recall.third()) + ")";
  }

  public double getFMeasure(L label) {
    double p = getPrecision(label);
    double r = getRecall(label);
    double f = (2 * p * r) / (p + r);
    return f;
  }

  public double getFMeasure() {
    double p = getPrecision();
    double r = getRecall();
    double f = (2 * p * r) / (p + r);
    return f;
  }

  /**
   * Returns a String summarizing F1 that will print nicely.
   */
  public String getF1Description(int numDigits) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);
    return nf.format(getFMeasure());
  }

  public String getF1Description(int numDigits, L label) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);
    return nf.format(getFMeasure(label));
  }

  /**
   * Returns a String summarizing F1 that will print nicely.
   */
  public String getDescription(int numDigits) {
    StringBuffer sb = new StringBuffer();
    sb.append("--- PR Stats ---").append("\n");
    for (L label : labelIndex) {
      if (label == null || label.equals(negLabel)) { continue; }
      sb.append("** ").append(label.toString()).append(" **\n");
      sb.append("\tPrec:   ").append(getPrecisionDescription(numDigits, label)).append("\n");
      sb.append("\tRecall: ").append(getRecallDescription(numDigits, label)).append("\n");
      sb.append("\tF1:     ").append(getF1Description(numDigits, label)).append("\n");
    }
    sb.append("** Overall **\n");
    sb.append("\tPrec:   ").append(getPrecisionDescription(numDigits)).append("\n");
    sb.append("\tRecall: ").append(getRecallDescription(numDigits)).append("\n");
    sb.append("\tF1:     ").append(getF1Description(numDigits));
    return sb.toString();
  }
}
