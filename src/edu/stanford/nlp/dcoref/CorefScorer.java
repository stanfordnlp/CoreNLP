package edu.stanford.nlp.dcoref;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Logger;

/**
 * Wrapper for a coreference resolution score: MUC, B cubed, Pairwise.
 */
public abstract class CorefScorer {

  enum SubScoreType {Recall, Precision, F1}
  enum ScoreType { MUC, BCubed, Pairwise}

  double precisionNumSum;
  double precisionDenSum;
  double recallNumSum;
  double recallDenSum;
  ScoreType scoreType;

  CorefScorer() {
    precisionNumSum = 0.0;
    precisionDenSum = 0.0;
    recallNumSum = 0.0;
    recallDenSum = 0.0;
  }

  public double getScore(SubScoreType subScoreType) {
    switch (subScoreType) {
      case Precision:
        return getPrecision();
      case Recall:
        return getRecall();
      case F1:
        return getF1();
      default:
        throw new IllegalArgumentException("Unsupported subScoreType: " + subScoreType);
    }
  }

  public double getPrecision(){
    return precisionNumSum/precisionDenSum;
  }
  public double getRecall(){
    return recallNumSum/recallDenSum;
  }
  public double getF1(){
    double p = getPrecision();
    double r = getRecall();
    return 2.0 * p * r / (p + r);
  }

  public void calculateScore(Document doc){
    calculatePrecision(doc);
    calculateRecall(doc);
  }
  protected abstract void calculatePrecision(Document doc);
  protected abstract void calculateRecall(Document doc);

  public void printF1(Logger logger, boolean printF1First) {
    NumberFormat nf = new DecimalFormat("0.000000");

    double r = getRecall();
    double p = getPrecision();
    double f1 = getF1();

    String R = nf.format(r);
    String P = nf.format(p);
    String F1 = nf.format(f1);

    NumberFormat nf2 = new DecimalFormat("00.0");

    String RR = nf2.format(r*100);
    String PP = nf2.format(p*100);
    String F1F1 = nf2.format(f1*100);

    if(printF1First) {
      String str = "F1 = "+F1+", P = "+P+" ("+(int) precisionNumSum+"/"+(int) precisionDenSum+"), R = "+R+" ("+(int) recallNumSum+"/"+(int) recallDenSum+")";
      if(scoreType == ScoreType.Pairwise){
        logger.fine("Pairwise "+str);
      } else if(scoreType == ScoreType.BCubed){
        logger.fine("B cube "+str);
      } else {
        logger.fine("MUC "+str);
      }
    } else {
      logger.fine("& "+PP+" & "+RR + " & "+F1F1);
    }
  }
  public void printF1(Logger logger) {
    printF1(logger, true);
  }

}
