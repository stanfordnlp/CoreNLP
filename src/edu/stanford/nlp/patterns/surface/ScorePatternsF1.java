package edu.stanford.nlp.patterns.surface;

import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.CollectionUtils;

/**
 * Used if patternScoring flag is set to F1 with the seed pattern. See {@link PatternScoring} enum.
 * 
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */

public class ScorePatternsF1 extends ScorePatterns {

  Counter<String> p0Set = null;
  Integer p0;
  public ScorePatternsF1(ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label, Set<String> allCandidatePhrases,
      TwoDimensionalCounter<Integer, String> patternsandWords4Label,
      TwoDimensionalCounter<Integer, String> negPatternsandWords4Label,
      TwoDimensionalCounter<Integer, String> unLabeledPatternsandWords4Label,
      Properties props, Counter<String> p0Set, Integer p0){
    super(constVars,
        patternScoring, label, allCandidatePhrases, patternsandWords4Label,
        negPatternsandWords4Label, unLabeledPatternsandWords4Label,
        props);
    this.p0 = p0;
    this.p0Set =p0Set; 
  }
      
  @Override
  public void setUp(Properties props){}
  
  @Override
  Counter<Integer> score() {
    Counter<Integer> specificity = new ClassicCounter<Integer>();
    Counter<Integer> sensitivity = new ClassicCounter<Integer>();

    if (p0Set.keySet().size() == 0)
      throw new RuntimeException("how come p0set size is empty for " + p0
          + "?");

    for (Entry<Integer, ClassicCounter<String>> en : patternsandWords4Label
        .entrySet()) {

      int common = CollectionUtils.intersection(en.getValue().keySet(),
          p0Set.keySet()).size();
      if (common == 0)
        continue;
      if (en.getValue().keySet().size() == 0)
        throw new RuntimeException("how come counter for " + en.getKey()
            + " is empty?");

      specificity.setCount(en.getKey(), common
          / (double) en.getValue().keySet().size());
      sensitivity.setCount(en.getKey(), common / (double) p0Set.size());
    }
    Counters.retainNonZeros(specificity);
    Counters.retainNonZeros(sensitivity);
    Counter<Integer> add = Counters.add(sensitivity, specificity);
    Counter<Integer> product = Counters.product(sensitivity,
        specificity);
    Counters.retainNonZeros(product);
    Counters.retainKeys(product, add.keySet());
    Counter<Integer> finalPat = Counters.scale(
        Counters.division(product, add), 2);
    
    return finalPat;
  }

}
