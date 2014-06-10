package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.io.Serializable;


class RandomWalk implements Serializable {

  private static final long serialVersionUID = -5284941866796561664L;

  private final Map<Object,Counter> model = Generics.newHashMap();

  private final Map<Object,Counter> hiddenToSeen = Generics.newHashMap();
  private final Map<Object,Counter> seenToHidden = Generics.newHashMap();

  private static final double LAMBDA = 0.01;

  /**
   * Uses the initialized values
   */
  public double score(Object hidden, Object seen) {
    return model.get(hidden).getCount(seen) / model.get(hidden).totalCount();
  }

  /* score with flexible number of steps */
  public double score(Object hidden, Object seen, int steps) {
    double total = 0;
    for (int i = 0; i <= steps; i++) {
      total += Math.pow(LAMBDA, steps) * step(hidden, seen, steps);
    }
    return total;
  }

  /* returns probability of hidden -> seen with <code>steps</code>
   * random walk steps */
  public double step(Object hidden, Object seen, int steps) {
    if (steps < 1) {
      return hiddenToSeen.get(hidden).getCount(seen) / hiddenToSeen.get(hidden).totalCount();
    } else {
      double total = 0;
      for (Object seen1 : seenToHidden.keySet()) {
        for (Object hidden1 : hiddenToSeen.keySet()) {
          double subtotal = hiddenToSeen.get(hidden).getCount(seen1) / hiddenToSeen.get(hidden).totalCount() * (seenToHidden.get(seen1).getCount(hidden1) / seenToHidden.get(seen1).totalCount());
          subtotal += score(hidden1, seen, steps - 1);
          total += subtotal;
        }
      }
      return total;
    }
  }


  public void train(Collection<Pair<?,?>> data) {
    for (Pair p : data) {
      Object seen = p.first();
      Object hidden = p.second();
      if (!hiddenToSeen.keySet().contains(hidden)) {
        hiddenToSeen.put(hidden, new ClassicCounter());
      }
      hiddenToSeen.get(hidden).incrementCount(seen);

      if (!seenToHidden.keySet().contains(seen)) {
        seenToHidden.put(seen, new ClassicCounter());
      }
      seenToHidden.get(seen).incrementCount(hidden);
    }
  }

  /**
   * builds a random walk model with n steps.
   *
   * @param data A collection of seen/hidden event <code>Pair</code>s
   */
  public RandomWalk(Collection<Pair<?,?>> data, int steps) {
    train(data);
    for (Iterator i = seenToHidden.keySet().iterator(); i.hasNext();) {
      Object seen = i.next();
      if (!model.containsKey(seen)) {
        model.put(seen, new ClassicCounter());
      }
      for (Iterator j = hiddenToSeen.keySet().iterator(); j.hasNext();) {
        Object hidden = j.next();
        model.get(seen).setCount(hidden, score(seen, hidden, steps));
      }
    }
  }

}
