package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Quadruple;
import edu.stanford.nlp.util.Triple;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An encapsulation of the natural logic weights to use during forward inference.
 *
 * @see edu.stanford.nlp.naturalli.ForwardEntailer
 *
 * @author Gabor Angeli
 */
public class NaturalLogicWeights {

  private final Map<Pair<String, String>, Double> verbPPAffinity = new HashMap<>();
  private final Map<Triple<String, String, String>, Double> verbSubjPPAffinity = new HashMap<>();
  private final Map<Quadruple<String, String, String, String>, Double> verbSubjObjPPAffinity = new HashMap<>();
  private final Map<Quadruple<String, String, String, String>, Double> verbSubjPPPPAffinity = new HashMap<>();
  private final Map<Quadruple<String, String, String, String>, Double> verbSubjPPObjAffinity = new HashMap<>();
  private final Map<String, Double> verbObjAffinity = new HashMap<>();
  private final double upperProbabilityCap;


  public NaturalLogicWeights() {
    this.upperProbabilityCap = 1.0;
  }

  public NaturalLogicWeights(double upperProbabilityCap) {
    this.upperProbabilityCap = upperProbabilityCap;
  }

  public NaturalLogicWeights(String affinityModels, double upperProbabilityCap) throws IOException {
    this.upperProbabilityCap = upperProbabilityCap;

    String line;
    // Simple PP attachments
    try (BufferedReader ppReader = IOUtils.readerFromString(affinityModels + "/pp.tab.gz", "utf8")) {
      while ((line = ppReader.readLine()) != null) {
        String[] fields = line.split("\t");
        Pair<String, String> key = Pair.makePair(fields[0].intern(), fields[1].intern());
        verbPPAffinity.put(key, Double.parseDouble(fields[2]));
      }
    }

    // Subj PP attachments
    try (BufferedReader subjPPReader = IOUtils.readerFromString(affinityModels + "/subj_pp.tab.gz", "utf8")) {
      while ((line = subjPPReader.readLine()) != null) {
        String[] fields = line.split("\t");
        Triple<String, String, String> key = Triple.makeTriple(fields[0].intern(), fields[1].intern(), fields[2].intern());
        verbSubjPPAffinity.put(key, Double.parseDouble(fields[3]));
      }
    }

    // Subj Obj PP attachments
    try (BufferedReader subjObjPPReader = IOUtils.readerFromString(affinityModels + "/subj_obj_pp.tab.gz", "utf8")) {
      while ((line = subjObjPPReader.readLine()) != null) {
        String[] fields = line.split("\t");
        Quadruple<String, String, String, String> key = Quadruple.makeQuadruple(fields[0].intern(), fields[1].intern(), fields[2].intern(), fields[3].intern());
        verbSubjObjPPAffinity.put(key, Double.parseDouble(fields[4]));
      }
    }

    // Subj PP PP attachments
    try (BufferedReader subjPPPPReader = IOUtils.readerFromString(affinityModels + "/subj_pp_pp.tab.gz", "utf8")) {
      while ((line = subjPPPPReader.readLine()) != null) {
        String[] fields = line.split("\t");
        Quadruple<String, String, String, String> key = Quadruple.makeQuadruple(fields[0].intern(), fields[1].intern(), fields[2].intern(), fields[3].intern());
        verbSubjPPPPAffinity.put(key, Double.parseDouble(fields[4]));
      }
    }

    // Subj PP PP attachments
    try (BufferedReader subjPPObjReader = IOUtils.readerFromString(affinityModels + "/subj_pp_obj.tab.gz", "utf8")) {
      while ((line = subjPPObjReader.readLine()) != null) {
        String[] fields = line.split("\t");
        Quadruple<String, String, String, String> key = Quadruple.makeQuadruple(fields[0].intern(), fields[1].intern(), fields[2].intern(), fields[3].intern());
        verbSubjPPObjAffinity.put(key, Double.parseDouble(fields[4]));
      }
    }

    // Subj PP PP attachments
    try (BufferedReader objReader = IOUtils.readerFromString(affinityModels + "/obj.tab.gz", "utf8")) {
      while ((line = objReader.readLine()) != null) {
        String[] fields = line.split("\t");
        verbObjAffinity.put(fields[0], Double.parseDouble(fields[1]));
      }
    }
  }

  public double deletionProbability(String edgeType) {
    // TODO(gabor) this is effectively assuming hard NatLog weights
    if (edgeType.contains("prep")) {
      return 0.9;
    } else if (edgeType.contains("obj")) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  public double subjDeletionProbability(SemanticGraphEdge edge, Iterable<SemanticGraphEdge> neighbors) {
    // Get information about the neighbors
    // (in a totally not-creepy-stalker sort of way)
    for (SemanticGraphEdge neighbor : neighbors) {
      if (neighbor != edge) {
        String neighborRel = neighbor.getRelation().toString();
        if (neighborRel.contains("subj")) {
          return 1.0;
        }
      }
    }
    return 0.0;
  }

  public double objDeletionProbability(SemanticGraphEdge edge, Iterable<SemanticGraphEdge> neighbors) {
    // Get information about the neighbors
    // (in a totally not-creepy-stalker sort of way)
    Optional<String> subj = Optional.empty();
    Optional<String> pp = Optional.empty();
    for (SemanticGraphEdge neighbor : neighbors) {
      if (neighbor != edge) {
        String neighborRel = neighbor.getRelation().toString();
        if (neighborRel.contains("subj")) {
          subj = Optional.of(neighbor.getDependent().originalText().toLowerCase());
        }
        if (neighborRel.contains("prep")) {
          pp = Optional.of(neighborRel);
        }
        if (neighborRel.contains("obj")) {
          return 1.0;  // allow deleting second object
        }
      }
    }
    String obj = edge.getDependent().originalText().toLowerCase();
    String verb = edge.getGovernor().originalText().toLowerCase();

    // Compute the most informative drop probability we can
    Double rawScore = null;
    if (subj.isPresent()) {
      if (pp.isPresent()) {
        // Case: subj+obj
        rawScore = verbSubjPPObjAffinity.get(Quadruple.makeQuadruple(verb, subj.get(), pp.get(), obj));
      }
    }
    if (rawScore == null) {
      rawScore = verbObjAffinity.get(verb);
    }
    if (rawScore == null) {
      return deletionProbability(edge.getRelation().toString());
    } else {
      return 1.0 - Math.min(1.0, rawScore / upperProbabilityCap);
    }
  }

  public double ppDeletionProbability(SemanticGraphEdge edge, Iterable<SemanticGraphEdge> neighbors) {
    // Get information about the neighbors
    // (in a totally not-creepy-stalker sort of way)
    Optional<String> subj = Optional.empty();
    Optional<String> obj = Optional.empty();
    Optional<String> pp = Optional.empty();
    for (SemanticGraphEdge neighbor : neighbors) {
      if (neighbor != edge) {
        String neighborRel = neighbor.getRelation().toString();
        if (neighborRel.contains("subj")) {
          subj = Optional.of(neighbor.getDependent().originalText().toLowerCase());
        }
        if (neighborRel.contains("obj")) {
          obj = Optional.of(neighbor.getDependent().originalText().toLowerCase());
        }
        if (neighborRel.contains("prep")) {
          pp = Optional.of(neighborRel);
        }
      }
    }
    String prep = edge.getRelation().toString();
    String verb = edge.getGovernor().originalText().toLowerCase();

    // Compute the most informative drop probability we can
    Double rawScore = null;
    if (subj.isPresent()) {
      if (obj.isPresent()) {
        // Case: subj+obj
        rawScore = verbSubjObjPPAffinity.get(Quadruple.makeQuadruple(verb, subj.get(), obj.get(), prep));
      }
      if (rawScore == null && pp.isPresent()) {
        // Case: subj+other_pp
        rawScore = verbSubjPPPPAffinity.get(Quadruple.makeQuadruple(verb, subj.get(), pp.get(), prep));
      }
      if (rawScore == null) {
        // Case: subj
        rawScore = verbSubjPPAffinity.get(Triple.makeTriple(verb, subj.get(), prep));
      }
    }
    if (rawScore == null) {
      // Case: just the original pp
      rawScore = verbPPAffinity.get(Pair.makePair(verb, prep));
    }
    if (rawScore == null) {
      return deletionProbability(prep);
    } else {
      return 1.0 - Math.min(1.0, rawScore / upperProbabilityCap);
    }
  }

  public double deletionProbability(SemanticGraphEdge edge, Iterable<SemanticGraphEdge> neighbors) {
    String edgeRel = edge.getRelation().toString();
    if (edgeRel.contains("prep")) {
      return ppDeletionProbability(edge, neighbors);
    } else if (edgeRel.contains("obj")) {
      return objDeletionProbability(edge, neighbors);
    } else if (edgeRel.contains("subj")) {
      return subjDeletionProbability(edge, neighbors);
    } else if (edgeRel.equals("amod")) {
      String word = (edge.getDependent().lemma() != null ? edge.getDependent().lemma() : edge.getDependent().word()).toLowerCase();
      if (Util.PRIVATIVE_ADJECTIVES.contains(word)) {
        return 0.0;
      } else {
        return 1.0;
      }
    } else {
      return deletionProbability(edgeRel);
    }
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
    return new NaturalLogicWeights();  // TODO(gabor)
  }
}
