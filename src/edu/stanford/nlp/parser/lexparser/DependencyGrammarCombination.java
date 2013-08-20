package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.stats.ClassicCounter;
import java.text.NumberFormat;

/**
 * @author Kristina Toutanova
 *         Date: 2004-2-19
 *         Time: 17:29:48
 */
public class DependencyGrammarCombination extends MLEDependencyGrammar {
  /**
   * 
   */
  private static final long serialVersionUID = 1421410021676721958L;
  MLEDependencyGrammar grammar1;
  MLEDependencyGrammar grammar2;
  double wt2 = 2;

  public DependencyGrammarCombination(MLEDependencyGrammar grammar1, MLEDependencyGrammar grammar2, double wt, Options op) {
    // TODO: it seems weird to put all of these objects at null.  Test
    // that.  Is it possible just make this not subclass
    // MLEDependencyGrammar?
    super(null, null, false, false, false, op, null, null);
    this.grammar1 = grammar1;
    this.grammar2 = grammar2;
    wt2 = wt;
  }


  @Override
  public double scoreTB(IntDependency dependency) {
    double score1 = grammar1.scoreTB(dependency);
    double score2 = grammar2.scoreTB(dependency);
    score1 /= op.testOptions.depWeight;
    score2 /= op.testOptions.depWeight;
    double count = grammar1.countHistory(dependency);
    double alpha = (count + 1) / (count + wt2);
    double score = Math.log(alpha * Math.exp(score1) + (1 - alpha) * Math.exp(score2));
    if (Double.isNaN(score)) {
      score = Double.NEGATIVE_INFINITY;
    }

    //if (op.testOptions.rightBonus && ! dependency.leftHeaded)
    //  score -= 0.2;

    if (score < -100) {
      score = Double.NEGATIVE_INFINITY;
    }

    return op.testOptions.depWeight * score;

  }

  @Override
  public int tagBin(int tag) {
    return grammar1.tagBin(tag);
  }

  @Override
  public int numTagBins() {
    return grammar1.numTagBins();
  }

}

class DependencyGrammarCombinationParts extends DependencyGrammarCombination {

  /**
   * 
   */
  private static final long serialVersionUID = 3169272395868792597L;
  boolean fixed = false;

  public DependencyGrammarCombinationParts(MLEDependencyGrammar grammar1, MLEDependencyGrammar grammar2, double wt, Options op) {
    super(grammar1, grammar2, wt, op);
  }

  @Override
  public double scoreTB(IntDependency dependency) {
    argCounter = grammar1.argCounter;
    stopCounter = grammar1.stopCounter;
    ClassicCounter arg2Counter = grammar2.argCounter;
    //Counter stop2Counter=grammar2.stopCounter;
    boolean verbose = false;

    int aW = dependency.arg.word;
    IntTaggedWord aTW = dependency.arg;
    IntTaggedWord hTW = dependency.head;
    
    if (verbose) {
      System.out.println("Generating " + dependency);
    }

    boolean leftHeaded = dependency.leftHeaded && directional;
    short valenceBinDistance = valenceBin(dependency.distance);
    short distanceBinDistance = distanceBin(dependency.distance);

    IntDependency temp = new IntDependency(dependency.head, dependency.arg, leftHeaded, valenceBinDistance);
    IntTaggedWord unknownHead = new IntTaggedWord(-1, dependency.head.tag);
    IntTaggedWord unknownArg = new IntTaggedWord(-1, dependency.arg.tag);
    double c_aTW_hTWd = argCounter.getCount(temp);
    double c_aTW_hTWd_g2 = arg2Counter.getCount(temp);
    temp = new IntDependency(dependency.head, unknownArg, leftHeaded, valenceBinDistance);
    double c_aT_hTWd = argCounter.getCount(temp);
    double c_aT_hTWd_g2 = arg2Counter.getCount(temp);
    temp = new IntDependency(dependency.head, wildTW, leftHeaded, valenceBinDistance);
    double c_hTWd = argCounter.getCount(temp);
    double c_hTWd_g2 = arg2Counter.getCount(temp);
    temp = new IntDependency(unknownHead, dependency.arg, leftHeaded, valenceBinDistance);
    double c_aTW_hTd = argCounter.getCount(temp);
    double c_aTW_hTd_g2 = arg2Counter.getCount(temp);

    temp = new IntDependency(unknownHead, unknownArg, leftHeaded, valenceBinDistance);
    double c_aT_hTd = argCounter.getCount(temp);
    temp = new IntDependency(unknownHead, unknownArg, leftHeaded, distanceBinDistance);
    double c_aT_hTDist = stopCounter.getCount(temp);
    temp = new IntDependency(unknownHead, wildTW, leftHeaded, valenceBinDistance);
    double c_hTd = argCounter.getCount(temp);
    double c_hTd_g2 = arg2Counter.getCount(temp);
    temp = new IntDependency(dependency.head, stopTW, leftHeaded, distanceBinDistance);
    double c_stop_hTWds = stopCounter.getCount(temp);
    temp = new IntDependency(unknownHead, stopTW, leftHeaded, distanceBinDistance);
    double c_stop_hTds = stopCounter.getCount(temp);
    temp = new IntDependency(dependency.head, wildTW, leftHeaded, distanceBinDistance);
    double c_hTWds = stopCounter.getCount(temp);
    temp = new IntDependency(unknownHead, wildTW, leftHeaded, distanceBinDistance);
    double c_hTds = stopCounter.getCount(temp);

    //make dependency to be with a stop
    double c_hTDist = c_hTds - c_stop_hTds;
    temp = new IntDependency(wildTW, dependency.arg, false, -1);
    double c_aTW = argCounter.getCount(temp);
    temp = new IntDependency(wildTW, unknownArg, false, -1);
    double c_aT = argCounter.getCount(temp);

    // decide whether to generate something

    double p_stop_hTWds = (c_hTWds > 0.0 ? c_stop_hTWds / c_hTWds : 0.0);
    double p_stop_hTds = (c_hTds > 0.0 ? c_stop_hTds / c_hTds : 1.0);

    double pb_stop_hTWds = (c_stop_hTWds + smooth_stop * p_stop_hTds) / (c_hTWds + smooth_stop);

    if (verbose) {
      System.out.println("  c_stop_hTWds: " + c_stop_hTWds + "; c_hTWds: " + c_hTWds + "; c_stop_hTds: " + c_stop_hTds + "; c_hTds: " + c_hTds);
      System.out.println("  Generate STOP prob: " + pb_stop_hTWds);
    }

    if (aW == -2) {
      // did we generate stop?
      if (rootTW(hTW)) {
        return 0.0;
      }
      return op.testOptions.depWeight * Math.log(pb_stop_hTWds);
    }

    double pb_go_hTWds = 1.0 - pb_stop_hTWds;

    if (rootTW(hTW)) {
      pb_go_hTWds = 1.0;
    }

    // generate the argument

    // do the magic
    double p_aTW_hTWd = (c_hTWd > 0.0 ? c_aTW_hTWd / c_hTWd : 0.0);
    double p_aTW_hTd = (c_hTd > 0.0 ? c_aTW_hTd / c_hTd : 0.0);
    double p_aT_hTWd = (c_hTWd > 0.0 ? c_aT_hTWd / c_hTWd : 0.0);
    double p_aT_hTd = (c_hTd > 0.0 ? c_aT_hTd / c_hTd : 0.0);
    double p_aTW_aT = (c_aTW > 0.0 ? c_aTW / c_aT : 1.0);
    //double p_hd_hTW = (c_hTW>0.0 ? c_hTWd/c_hTW : 0.0);
    //double p_hd_hT = (c_hT>0.0 ? c_hTd/c_hT : 0.0);

    //this is the change to p_aT_hTd
    //p_aT_hTd = (c_hTDist > 0.0 ? c_aT_hTDist / c_hTDist : 0.0);

    double pb_aTW_hTWd = (c_aTW_hTWd + smooth_aT_hTWd * p_aTW_hTd) / (c_hTWd + smooth_aT_hTWd);
    double pb_aT_hTWd = (c_aT_hTWd + smooth_aTW_hTWd * p_aT_hTd) / (c_hTWd + smooth_aTW_hTWd);

    //new definition of  pb_aTW_hTWd

    double alpha_0 = c_hTWd / (c_hTWd + smooth_aT_hTWd);

    double alpha_1 = c_hTWd_g2 / (c_hTWd_g2 + smooth_aT_hTWd);

    double alpha_2 = .8;

    double phat_aTW_hTWd_g2 = (c_hTWd_g2 > 0.0 ? c_aTW_hTWd_g2 / c_hTWd_g2 : 0.0);

    double phat_aTW_hTd_g2 = (c_hTd_g2 > 0.0 ? c_aTW_hTd_g2 / c_hTd_g2 : 0.0);

    pb_aTW_hTWd = alpha_0 * p_aTW_hTWd + (1 - alpha_0) * (alpha_1 * phat_aTW_hTWd_g2 + (1 - alpha_1) * (alpha_2 * p_aTW_hTd + (1 - alpha_2) * phat_aTW_hTd_g2));

    alpha_0 = c_hTWd / (c_hTWd + smooth_aTW_hTWd);
    alpha_1 = c_hTWd_g2 / (c_hTWd_g2 + smooth_aTW_hTWd);
    //double nScore = Math.log(p_aT_hTd);
    double p_aT_hTWd_g2 = (c_hTWd_g2 > 0.0 ? c_aT_hTWd_g2 / c_hTWd_g2 : 0.0);

    pb_aT_hTWd = alpha_0 * p_aT_hTWd + (1 - alpha_0) * (alpha_1 * p_aT_hTWd_g2 + (1 - alpha_1) * p_aT_hTd);

    double score = (Math.log(interp * pb_aTW_hTWd + (1.0 - interp) * p_aTW_aT * pb_aT_hTWd) + Math.log(pb_go_hTWds));

    if (verbose) {
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMaximumFractionDigits(2);
      System.out.println("  c_aTW_hTWd: " + c_aTW_hTWd + "; c_aT_hTWd: " + c_aT_hTWd + "; c_hTWd: " + c_hTWd);
      System.out.println("  c_aTW_hTd: " + c_aTW_hTd + "; c_aT_hTd: " + c_aT_hTd + "; c_hTd: " + c_hTd);
      System.out.println("  Generated with pb_go_hTWds: " + nf.format(Math.log(pb_go_hTWds)) + " pb_aTW_hTWd: " + nf.format(Math.log(pb_aTW_hTWd)) + " p_aTW_aT: " + nf.format(Math.log(p_aTW_aT)) + " pb_aT_hTWd: " + nf.format(Math.log(pb_aT_hTWd)));
      System.out.println("  NoDist log score: " + score);
    }

    if (op.testOptions.prunePunc && pruneTW(aTW)) {
      return 0.0;
    }

    if (Double.isNaN(score)) {
      score = Double.NEGATIVE_INFINITY;
    }

    //if (op.testOptions.rightBonus && ! dependency.leftHeaded)
    //  score -= 0.2;

    if (score < -100) {
      score = Double.NEGATIVE_INFINITY;
    }

    return op.testOptions.depWeight * score;
  }
}
