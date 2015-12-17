package edu.stanford.nlp.dcoref;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;

/**
 * Similar to {@link edu.stanford.nlp.ling.CoreAnnotations},
 * but this class contains
 * annotations made specifically for storing Coref data.  This is kept
 * separate from CoreAnnotations so that systems which only need
 * CoreAnnotations do not depend on Coref classes.
 */
public class CorefCoreAnnotations {

  /**
   * The standard key for the coref label.
   * Not used by the new dcoref system.
   */
  public static class CorefAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Destination of the coreference link for this word (if any).
   * It contains the index of the sentence and the index of the word that
   * are the end of this coref link. Both indices start at 1. The
   * sentence index is IntTuple.get(0); the token index in the
   * sentence is IntTuple.get(1).
   */
  public static class CorefDestAnnotation implements CoreAnnotation<IntTuple> {
    public Class<IntTuple> getType() {
      return IntTuple.class;
    }
  }

  /**
   * This stores the entire set of coreference links for one
   * document. Each link is stored as a pair of pointers (source and
   * destination), where each pointer stores a sentence offset and a
   * token offset. All offsets start at 0.
   */
  @Deprecated
  public static class CorefGraphAnnotation implements CoreAnnotation<List<Pair<IntTuple, IntTuple>>> {
    public Class<List<Pair<IntTuple, IntTuple>>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * An integer representing a document-level unique cluster of
   * coreferent entities. In other words, if two entities have the
   * same CorefClusterIdAnnotation, they are coreferent. This
   * annotation is typically attached to tokens (CoreLabel).
   */
  public static class CorefClusterIdAnnotation implements CoreAnnotation<Integer> {
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Set of all the CoreLabel objects which are coreferent with a
   * CoreLabel.  Note that the list includes the CoreLabel that was
   * annotated which creates a cycle.
   *
   * @deprecated This was an original dcoref annotation. You should now use CorefChainAnnotation
   */
  @Deprecated
  public static class CorefClusterAnnotation implements CoreAnnotation<Set<CoreLabel>> {
    public Class<Set<CoreLabel>> getType() {
      return ErasureUtils.uncheckedCast(Set.class);
    }
  }

  /**
   * CorefChainID - CorefChain map.
   */
  public static class CorefChainAnnotation implements CoreAnnotation<Map<Integer, CorefChain>> {
    public Class<Map<Integer, CorefChain>> getType() {
      return ErasureUtils.uncheckedCast(Map.class);
    }
  }

}