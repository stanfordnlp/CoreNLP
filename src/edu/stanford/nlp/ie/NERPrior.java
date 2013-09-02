package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.List;


/**
 * @author Jenny Finkel
 */

public class NERPrior extends EntityCachingAbstractSequencePrior<CoreLabel> {

  public NERPrior(String backgroundSymbol, Index<String> classIndex, List<CoreLabel> doc) {
    super(backgroundSymbol, classIndex, doc);
  }

  public double scoreOf(int[] sequence) {
    TwoDimensionalCounter<List<String>,String> gc = new TwoDimensionalCounter<List<String>, String>();
    for (int i = 0; i < entities.length; i++) {
      if (entities[i] != null && (i == 0 || entities[i] != entities[i-1])) {
        gc.incrementCount(entities[i].words, classIndex.get(entities[i].type));
      }
    }

    int penalty = 0;

    for (List<String> entity : gc.firstKeySet()) {
      boolean first = true;
      for (String label : classIndex) {
        double c = gc.getCount(entity, label);
        if (c > 0.9 && c < 1.1) {
          if (!first) { penalty++; }
          first = false;
        }
      }
        //GeneralizedCounter labels = gc.conditionalizeOnce(entity);
        //penalty += (labels.keySet().size() - 1);
    }

    return penalty * -2.0;
    // return Math.log(Math.pow(0.9, penalty));
  }

}
