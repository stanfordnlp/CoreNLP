package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

/**
 * TODO(gabor) JavaDoc
 *
 * @author Gabor Angeli
 */
public class NaturalLogicWeights {

  private TwoDimensionalCounter<String, String> ppAffinity = new TwoDimensionalCounter<>();
  private Counter<String> dobjAffinity = new ClassicCounter<>();

  public NaturalLogicWeights() {

  }

  public NaturalLogicWeights(String PP_AFFINITY, String DOBJ_AFFINITY) {
    // Preposition affinities
    for (String line : IOUtils.readLines(PP_AFFINITY, "utf8")) {
      String[] fields = line.split("\t");
      if (fields.length != 3) {
        throw new IllegalArgumentException("Invalid format for the pp_affinity data");
      }
      ppAffinity.setCount(fields[0], fields[1], Double.parseDouble(fields[2]));
    }
    for (String verb : ppAffinity.firstKeySet()) {
      // Normalize counts to be between 0 and 1
      Counter<String> preps = ppAffinity.getCounter(verb);
      Counters.multiplyInPlace(preps, -1.0);
      Counters.addInPlace(preps, 1.0);
      double min = Counters.min(preps);
      double max = Counters.max(preps);
      Counters.addInPlace(preps, -min);
      if (max == min) {
        Counters.addInPlace(preps, 0.5);
      } else {
        Counters.divideInPlace(preps, max - min);
      }
      Counters.multiplyInPlace(preps, -1.0);
      Counters.addInPlace(preps, 1.0);
    }
    // Object affinities
    for (String line : IOUtils.readLines(DOBJ_AFFINITY, "utf8")) {
      String[] fields = line.split("\t");
      if (fields.length != 2) {
        throw new IllegalArgumentException("Invalid format for the dobj_affinity data");
      }
      dobjAffinity.setCount(fields[0], Double.parseDouble(fields[1]));
    }
  }

  public double deletionProbability(String edgeType) {
    // TODO(gabor)
    return 1.0;
  }

  public double deletionProbability(SemanticGraphEdge edge, Iterable<SemanticGraphEdge> neighbors) {
    // TODO(gabor)
    return 1.0;
  }

  /*
  private double backoffEdgeProbability(String edgeRel) {
    return 1.0;  // TODO(gabor) should probably learn these...
  }

  public double deletionProbability(String parent, String edgeRel) {
    return deletionProbability(parent, edgeRel, false);
  }

  public double deletionProbability(String parent, String edgeRel, boolean isSecondaryEdgeOfType) {
    if (edgeRel.startsWith("prep")) {
      double affinity = ppAffinity.getCount(parent, edgeRel);
      if (affinity != 0.0 && !isSecondaryEdgeOfType) {
        return Math.sqrt(1.0 - Math.min(1.0, affinity));
      } else {
        return backoffEdgeProbability(edgeRel);
      }
    } else if (edgeRel.startsWith("dobj")) {
      double affinity = dobjAffinity.getCount(parent);
      if (affinity != 0.0 && !isSecondaryEdgeOfType) {
        return Math.sqrt(1.0 - Math.min(1.0, affinity));
      } else {
        return backoffEdgeProbability(edgeRel);
      }
    } else {
      return backoffEdgeProbability(edgeRel);
    }
  }
  */

  public static NaturalLogicWeights fromString(String str) {
    return new NaturalLogicWeights(null, null);  // TODO(gabor)
  }
}
