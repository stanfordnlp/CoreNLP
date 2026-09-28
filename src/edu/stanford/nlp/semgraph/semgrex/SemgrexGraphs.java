package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * The graphs a pattern is being matched against, and how they are joined.
 *<br>
 * One sentence may have more than one graph of it -- a basic and an
 * enhanced dependency graph, say -- and a relation can name which of them
 * it wants with {@code >nsubj@enhanced}.  Those live in one of these,
 * keyed by name.
 *<br>
 * The alignment relation, {@code @}, crosses to a different sentence
 * entirely, which has graphs of its own.  So the other side of an
 * alignment is another one of these, and crossing is
 * {@link #crossAlignment}.  The two sides refer to each other, so
 * crossing twice comes back to where it started and costs nothing.
 *<br>
 * All of this is fixed for the whole of a match and is passed down by
 * reference.  Which graph is currently being searched is not kept here,
 * because it changes as the match descends.
 *
 * @author John Bauer
 */
public class SemgrexGraphs implements Serializable {

  private static final long serialVersionUID = 1L;

  /** The graphs of this sentence, by name */
  private final Map<SemgrexGraphName, SemanticGraph> graphs;

  /** The graphs on the other side of the alignment, or null when there is none */
  private SemgrexGraphs aligned;

  /** How the two sides are joined, or null when there is no alignment */
  public final Alignment alignment;

  /**
   * Whether this side is the one the alignment maps from
   *<br>
   * In the original hyp/txt alignment paradigm, this is the "hyp" side.
   * In the no alignment situation, this is always true.
   **/
  private final boolean mapsFrom;

  /**
   * One graph, with no alignment
   *
   * @param graph the graph, stored as the basic graph; if null, there are no graphs
   */
  public SemgrexGraphs(SemanticGraph graph) {
    this(singleGraph(graph), null, true);
  }

  /**
   * The named graphs of one sentence, with no alignment
   *
   * @param graphs the graphs by name; the map is kept as is, not copied
   */
  public SemgrexGraphs(Map<SemgrexGraphName, SemanticGraph> graphs) {
    this(graphs, null, true);
  }

  private SemgrexGraphs(Map<SemgrexGraphName, SemanticGraph> graphs, Alignment alignment, boolean mapsFrom) {
    this.graphs = graphs;
    this.alignment = alignment;
    this.mapsFrom = mapsFrom;
  }

  private static Map<SemgrexGraphName, SemanticGraph> singleGraph(SemanticGraph graph) {
    Map<SemgrexGraphName, SemanticGraph> map = new EnumMap<>(SemgrexGraphName.class);
    if (graph != null) {
      map.put(SemgrexGraphName.BASIC, graph);
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * The basic and enhanced graphs of one sentence.
   *<br>
   * Either may be null, for a sentence which was not given that graph.  A
   * pattern which then asks for the missing one says so when it is run,
   * rather than the caller having to decide in advance which graphs a
   * pattern is going to want.
   *
   * @param basic the basic dependencies graph, or null
   * @param enhanced the enhanced dependencies graph, or null
   * @return the graphs of the sentence, with no alignment
   */
  public static SemgrexGraphs of(SemanticGraph basic, SemanticGraph enhanced) {
    Map<SemgrexGraphName, SemanticGraph> map = new EnumMap<>(SemgrexGraphName.class);
    if (basic != null) {
      map.put(SemgrexGraphName.BASIC, basic);
    }
    if (enhanced != null) {
      map.put(SemgrexGraphName.ENHANCED, enhanced);
    }
    return new SemgrexGraphs(Collections.unmodifiableMap(map));
  }

  /**
   * Two sentences joined by an alignment, each with the one graph.
   *<br>
   * The two sides are linked to each other here rather than built on
   * demand, so that crossing the alignment is a field read and crossing
   * back arrives at the very same object.
   *
   * @param hypGraph the graph on the side the alignment maps from
   * @param alignment the alignment joining the two graphs
   * @param txtGraph the graph on the side the alignment maps to
   * @return the hyp side; its {@link #crossAlignment} is the txt side
   */
  public static SemgrexGraphs aligned(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph) {
    SemgrexGraphs hyp = new SemgrexGraphs(singleGraph(hypGraph), alignment, true);
    SemgrexGraphs txt = new SemgrexGraphs(singleGraph(txtGraph), alignment, false);
    hyp.aligned = txt;
    txt.aligned = hyp;
    return hyp;
  }

  /**
   * The graph of this sentence with the given name, or null if there is none
   *
   * @param name which graph to return
   * @return the named graph, or null
   */
  public SemanticGraph get(SemgrexGraphName name) {
    return graphs.get(name);
  }

  /**
   * The graph a pattern searches when it has not asked for one by name
   *
   * @return the basic graph, or null if there is none
   */
  public SemanticGraph getDefault() {
    return graphs.get(SemgrexGraphName.BASIC);
  }

  /**
   * The other side of the alignment, or null when there is no alignment
   *
   * @return the graphs of the aligned sentence, or null
   */
  public SemgrexGraphs crossAlignment() {
    return aligned;
  }

  /**
   * Whether this side is the one the alignment maps from, which is the
   * direction the ALIGNMENT relation walks the map in.
   *
   * @return true for the hyp side or when there is no alignment, false for the txt side
   */
  public boolean mapsFrom() {
    return mapsFrom;
  }

  @Override
  public String toString() {
    if (aligned == null) {
      return "SemgrexGraphs" + graphs.keySet();
    }
    return "SemgrexGraphs" + graphs.keySet() + " aligned to " + aligned.graphs.keySet();
  }
}
