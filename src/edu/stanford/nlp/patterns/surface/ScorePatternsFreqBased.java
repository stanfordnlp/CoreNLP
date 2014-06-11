package edu.stanford.nlp.patterns.surface;

import java.util.Properties;
import java.util.Map.Entry;

import edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.logging.Redwood;

public class ScorePatternsFreqBased extends ScorePatterns {

  public ScorePatternsFreqBased(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label,
      TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negandUnLabeledPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords4Label, Properties props) {
    super(constVars, patternScoring, label, patternsandWords4Label,
        negPatternsandWords4Label, unLabeledPatternsandWords4Label,
        negandUnLabeledPatternsandWords4Label, allPatternsandWords4Label, props);
  }

  @Override
  public void setUp(Properties props){}
  
  @Override
  Counter<SurfacePattern> score() {

    Counter<SurfacePattern> currentPatternWeights4Label = new ClassicCounter<SurfacePattern>();

    Counter<SurfacePattern> pos_i = new ClassicCounter<SurfacePattern>();
    Counter<SurfacePattern> all_i = new ClassicCounter<SurfacePattern>();
    Counter<SurfacePattern> neg_i = new ClassicCounter<SurfacePattern>();
    Counter<SurfacePattern> unlab_i = new ClassicCounter<SurfacePattern>();

    for (Entry<SurfacePattern, ClassicCounter<String>> en : negPatternsandWords4Label
        .entrySet()) {
      neg_i.setCount(en.getKey(), en.getValue().size());
    }

    for (Entry<SurfacePattern, ClassicCounter<String>> en : unLabeledPatternsandWords4Label
        .entrySet()) {
      unlab_i.setCount(en.getKey(), en.getValue().size());
    }

    for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label
        .entrySet()) {
      pos_i.setCount(en.getKey(), en.getValue().size());
    }

    for (Entry<SurfacePattern, ClassicCounter<String>> en : allPatternsandWords4Label
        .entrySet()) {
      all_i.setCount(en.getKey(), en.getValue().size());
    }

    Counter<SurfacePattern> posneg_i = Counters.add(pos_i, neg_i);
    Counter<SurfacePattern> logFi = new ClassicCounter<SurfacePattern>(pos_i);
    Counters.logInPlace(logFi);

    if (patternScoring.equals(PatternScoring.RlogF)) {
      currentPatternWeights4Label = Counters.product(
          Counters.division(pos_i, all_i), logFi);
    } else if (patternScoring.equals(PatternScoring.RlogFPosNeg)) {
      Redwood.log("extremePatDebug", "computing rlogfposneg");

      currentPatternWeights4Label = Counters.product(
          Counters.division(pos_i, posneg_i), logFi);

    } else if (patternScoring.equals(PatternScoring.RlogFUnlabNeg)) {
      Redwood.log("extremePatDebug", "computing rlogfunlabeg");

      currentPatternWeights4Label = Counters.product(
          Counters.division(pos_i, Counters.add(neg_i, unlab_i)), logFi);
    } else if (patternScoring.equals(PatternScoring.RlogFNeg)) {
      Redwood.log("extremePatDebug", "computing rlogfneg");

      currentPatternWeights4Label = Counters.product(
          Counters.division(pos_i, neg_i), logFi);
    } else if (patternScoring.equals(PatternScoring.YanGarber02)) {

      Counter<SurfacePattern> acc = Counters.division(pos_i,
          Counters.add(pos_i, neg_i));
      double thetaPrecision = 0.8;
      Counters.retainAbove(acc, thetaPrecision);
      Counter<SurfacePattern> conf = Counters.product(
          Counters.division(pos_i, all_i), logFi);
      for (SurfacePattern p : acc.keySet()) {
        currentPatternWeights4Label.setCount(p, conf.getCount(p));
      }
    } else if (patternScoring.equals(PatternScoring.LinICML03)) {

      Counter<SurfacePattern> acc = Counters.division(pos_i,
          Counters.add(pos_i, neg_i));
      double thetaPrecision = 0.8;
      Counters.retainAbove(acc, thetaPrecision);
      Counter<SurfacePattern> conf = Counters.product(Counters.division(
          Counters.add(pos_i, Counters.scale(neg_i, -1)), all_i), logFi);
      for (SurfacePattern p : acc.keySet()) {
        currentPatternWeights4Label.setCount(p, conf.getCount(p));
      }
    } else {
      throw new RuntimeException("not implemented");
    }
    return currentPatternWeights4Label;
  }
}
