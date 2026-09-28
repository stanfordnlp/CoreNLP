package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Generics;

import java.util.*;

/**
 * Represents an alignment between a text and a hypothesis as a map from
 * hypothesis words to text words, along with a real-valued score and
 * (optionally) a justification string.
 *
 * @author Bill MacCartney
 */
public class Alignment {

  private Map<IndexedWord, IndexedWord> map;
  private final double score;
  private final String justification;

  /**
   * Creates an alignment which uses the given map directly (it is not copied).
   *
   * @param map map from hypothesis words to text words
   * @param score the score of this alignment
   * @param justification a justification string, possibly null
   */
  public Alignment(Map<IndexedWord, IndexedWord> map,
                   double score,
                   String justification) {
    this.map = map;
    this.score = score;
    this.justification = justification;
  }

  /**
   * Copy constructor.  The map is copied, but the words in it are shared.
   *
   * @param other the alignment to copy
   */
  public Alignment(Alignment other) {
    // note that we aren't copying the words
    map = new HashMap<>(other.map);
    score = other.score;
    justification = other.justification;
  }

  /**
   * Returns the score for this <code>Alignment</code>.
   *
   * @return the score
   */
  public double getScore() { return score; }

  /**
   * Returns the map from hypothesis words to text words for this
   * <code>Alignment</code>.  This is the internal map, not a copy.
   *
   * @return the map from hypothesis words to text words
   */
  public Map<IndexedWord, IndexedWord> getMap() {
    return map;
  }

  /**
   * Returns the justification for this <code>Alignment</code>.
   *
   * @return the justification, possibly null
   */
  public String getJustification() { return justification; }

  /**
   * Returns this alignment in the "readable" format.
   *
   * @return a multi-line description of the alignment and its score
   */
  @Override
  public String toString() {
    return toString("readable");
  }

  /**
   * Returns this alignment as a String in the given format.
   * "readable" and "readable-tag-index" list the alignments sorted by
   * hypothesis word followed by the score, "readable-old" lists them
   * unsorted, and any other format returns the map's toString.
   * The format is compared with {@code ==}, so it must be one of those
   * String literals (or an interned String) to be recognized.
   *
   * @param format the name of the output format
   * @return the alignment in the requested format
   */
  public String toString(String format) {
    StringBuilder sb = new StringBuilder();
    if (format == "readable") {
      // sb.append("Alignment map:\n");
      List<IndexedWord> keys = new ArrayList<>(map.keySet());
      Collections.sort(keys);
      for (IndexedWord key : keys) {
        sb.append(String.format("%-20s ==> %s%n",
                                iwToString(key),
                                iwToString(map.get(key))));
      }
      sb.append(String.format("%s %6.3f%n", "Alignment score:", score));
    }  else if (format == "readable-tag-index") {
      List<IndexedWord> keys = new ArrayList<>(map.keySet());
      Collections.sort(keys);
      for (IndexedWord key : keys) {
        sb.append(String.format("%-20s ==> %s%n",
                                iwToString(key),
                                iwToString(map.get(key))));
      }
      sb.append(String.format("%s %6.3f%n", "Alignment score:", score));
    } else if (format == "readable-old") {
      // sb.append("Alignment map:\n");
      for (Map.Entry<IndexedWord, IndexedWord> entry : map.entrySet()) {
        sb.append(String.format("%-20s ==> %s%n",
                                iwToString(entry.getKey()),
                                iwToString(entry.getValue())));
      }
      sb.append("Alignment score: ");
      sb.append(String.format("%6.3f", score));
      sb.append("\n");
    } else {                            // default
      sb.append(map.toString());
    }
    return sb.toString();
  }

  private static String iwToString(IndexedWord iw) {
    if (iw == null || iw.equals(IndexedWord.NO_WORD))
      return "_";
    return iw.toString(CoreLabel.OutputFormat.VALUE);
  }

  /**
   * Defined on map only.
   */
  @Override
  public boolean equals(Object o) {
    if (! (o instanceof Alignment)) return false;
    Alignment other = (Alignment) o;
    return map.equals(other.map);
  }

  /**
   * Defined on map only.
   */
  @Override
  public int hashCode() {
    return map.hashCode();
  }

  /**
   * returns a new alignment with the guarantee that:
   *
   *   (i)   every node in hypGraph has a corresponding alignment
   *   (ii)  no alignment exists that doesn't have a node in hypGraph
   *   (iii) the only alignment that exists that doesn't have a node in
   *         txtGraph is an alignment to NO_WORD
   *
   * TODO[wcmac]: What is this for?  Looks like nothing is using this?
   */
  Alignment patchedAlignment(SemanticGraph hypGraph, SemanticGraph txtGraph) {
    Map<IndexedWord, IndexedWord> patchedMap = Generics.newHashMap();
    Set<IndexedWord> txtVertexSet = txtGraph.vertexSet();
    for (Object o : hypGraph.vertexSet())  {
      IndexedWord vertex = (IndexedWord)o;
      if (map.containsKey(vertex) && txtVertexSet.contains(map.get(vertex))) {
        patchedMap.put(vertex, map.get(vertex));
      }
      else patchedMap.put(vertex, IndexedWord.NO_WORD);
    }
    return new Alignment(patchedMap, score, justification);
  }


  /**
   * Constructs and returns a new Alignment from the given hypothesis
   * {@code SemanticGraph} to the given text (passage) SemanticGraph, using
   * the given array of indexes.  The i'th node of the array should contain the
   * index of the node in the text (passage) SemanticGraph to which the i'th
   * node in the hypothesis SemanticGraph is aligned, or -1 if it is aligned to
   * NO_WORD.
   * Nodes are looked up with {@link SemanticGraph#getNodeByIndex}, so i is
   * used as a word index in hypGraph.
   *
   * @param txtGraph the text graph
   * @param hypGraph the hypothesis graph
   * @param indexes for each hypothesis node, the index of the aligned text node, or -1
   * @param score the score of the alignment
   * @param justification a justification string, possibly null
   * @return the new alignment
   * @throws IllegalArgumentException if either graph is null or empty,
   *   indexes is null or not the same length as hypGraph, or an index is
   *   not found in its graph
   */
  public static Alignment makeFromIndexArray(SemanticGraph txtGraph,
                                             SemanticGraph hypGraph,
                                             int[] indexes,
                                             double score,
                                             String justification) {
    if (txtGraph == null || txtGraph.isEmpty())
      throw new IllegalArgumentException("Invalid txtGraph " + txtGraph);
    if (hypGraph == null || hypGraph.isEmpty())
      throw new IllegalArgumentException("Invalid hypGraph " + hypGraph);
    if (indexes == null)
      throw new IllegalArgumentException("Null index array");
    if (indexes.length != hypGraph.size())
      throw new IllegalArgumentException("Index array length " + indexes.length +
                                         " does not match hypGraph size " + hypGraph.size());
    Map<IndexedWord, IndexedWord> map =
      Generics.newHashMap();
    for (int i = 0; i < indexes.length; i++) {
      IndexedWord hypNode = hypGraph.getNodeByIndex(i);
      IndexedWord txtNode = IndexedWord.NO_WORD;
      if (indexes[i] >= 0)
        txtNode = txtGraph.getNodeByIndex(indexes[i]);
      map.put(hypNode, txtNode);
    }
    return new Alignment(map, score, justification);
  }

  /**
   * Same as {@link #makeFromIndexArray(SemanticGraph, SemanticGraph, int[], double, String)}
   * with a score of 0 and no justification.
   *
   * @param txtGraph the text graph
   * @param hypGraph the hypothesis graph
   * @param indexes for each hypothesis node, the index of the aligned text node, or -1
   * @return the new alignment
   */
  public static Alignment makeFromIndexArray(SemanticGraph txtGraph,
                                             SemanticGraph hypGraph,
                                             int[] indexes) {
    return makeFromIndexArray(txtGraph, hypGraph, indexes, 0.0, null);
  }

  /**
   * Same as {@link #makeFromIndexArray(SemanticGraph, SemanticGraph, int[], double, String)}
   * with no justification.
   *
   * @param txtGraph the text graph
   * @param hypGraph the hypothesis graph
   * @param indexes for each hypothesis node, the index of the aligned text node, or -1
   * @param score the score of the alignment
   * @return the new alignment
   */
  public static Alignment makeFromIndexArray(SemanticGraph txtGraph,
                                             SemanticGraph hypGraph,
                                             int[] indexes,
                                             double score) {
    return makeFromIndexArray(txtGraph, hypGraph, indexes, score, null);
  }

}


