package edu.stanford.nlp.patterns;

import java.util.Properties;
import java.util.Map.Entry;
import java.util.Set;

import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.logging.Redwood;

public class ScorePatternsFreqBased<E> extends ScorePatterns<E> {

  public ScorePatternsFreqBased(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label, Set<CandidatePhrase> allCandidatePhrases,
      TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label,
      Properties props) {
    super(constVars, patternScoring, label, allCandidatePhrases, patternsandWords4Label,
        negPatternsandWords4Label, unLabeledPatternsandWords4Label,  props);
  }

  @Override
  public void setUp(Properties props){}
  
  @Override
  public Counter<E> score() {

    Counter<E> currentPatternWeights4Label = new ClassicCounter<>();

    Counter<E> pos_i = new ClassicCounter<>();
    Counter<E> neg_i = new ClassicCounter<>();
    Counter<E> unlab_i = new ClassicCounter<>();

    for (Entry<E, ClassicCounter<CandidatePhrase>> en : negPatternsandWords4Label
        .entrySet()) {
      neg_i.setCount(en.getKey(), en.getValue().size());
    }

    for (Entry<E, ClassicCounter<CandidatePhrase>> en : unLabeledPatternsandWords4Label
        .entrySet()) {
      unlab_i.setCount(en.getKey(), en.getValue().size());
    }

    for (Entry<E, ClassicCounter<CandidatePhrase>> en : patternsandWords4Label
        .entrySet()) {
      pos_i.setCount(en.getKey(), en.getValue().size());
    }

    Counter<E> all_i = Counters.add(pos_i, neg_i);
    all_i.addAll(unlab_i);
//    for (Entry<Integer, ClassicCounter<String>> en : allPatternsandWords4Label
//        .entrySet()) {
//      all_i.setCount(en.getKey(), en.getValue().size());
//    }

    Counter<E> posneg_i = Counters.add(pos_i, neg_i);
    Counter<E> logFi = new ClassicCounter<>(pos_i);
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

      Counter<E> acc = Counters.division(pos_i,
          Counters.add(pos_i, neg_i));
      double thetaPrecision = 0.8;
      Counters.retainAbove(acc, thetaPrecision);
      Counter<E> conf = Counters.product(
          Counters.division(pos_i, all_i), logFi);
      for (E p : acc.keySet()) {
        currentPatternWeights4Label.setCount(p, conf.getCount(p));
      }
    } else if (patternScoring.equals(PatternScoring.LinICML03)) {

      Counter<E> acc = Counters.division(pos_i,
          Counters.add(pos_i, neg_i));
      double thetaPrecision = 0.8;
      Counters.retainAbove(acc, thetaPrecision);
      Counter<E> conf = Counters.product(Counters.division(
          Counters.add(pos_i, Counters.scale(neg_i, -1)), all_i), logFi);
      for (E p : acc.keySet()) {
        currentPatternWeights4Label.setCount(p, conf.getCount(p));
      }
    } else {
      throw new RuntimeException("not implemented " + patternScoring + " . check spelling!");
    }
    return currentPatternWeights4Label;
  }
}
