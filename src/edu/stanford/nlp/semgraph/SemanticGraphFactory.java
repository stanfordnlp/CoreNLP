package edu.stanford.nlp.semgraph;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.*;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

/**
 * Refactoring of static makers of SemanticGraphs in order to simplify
 * the SemanticGraph class.
 *
 * @author rafferty
 */
public class SemanticGraphFactory  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemanticGraphFactory.class);

  private SemanticGraphFactory() {} // just static factory methods

  private static final boolean INCLUDE_PUNCTUATION_DEPENDENCIES = false;

  public enum Mode {
    COLLAPSED_TREE,
    /** collapse: Whether to do "collapsing" of pairs of dependencies into
     *  single dependencies, e.g., for prepositions and conjunctions.
     */
    COLLAPSED,
    /** ccProcess: Whether to do processing of CC complements resulting from
     *  collapsing.  This argument is ignored unless {@code collapse} is
     * {@code true}.
     */
    CCPROCESSED,
    BASIC,
    ENHANCED,
    ENHANCED_PLUS_PLUS
  }

  /**
   * Produces an Uncollapsed (basic) SemanticGraph.
   */
  public static SemanticGraph generateUncollapsedDependencies(Tree tree) {
    return makeFromTree(tree, Mode.BASIC, GrammaticalStructure.Extras.NONE);
  }

  /**
   * Produces a Collapsed SemanticGraph.
   *
   * @deprecated Use {@link #generateEnhancedDependencies(Tree)} or
   * {@link #generateEnhancedPlusPlusDependencies(Tree)} instead.
   */
  @Deprecated
  public static SemanticGraph generateCollapsedDependencies(Tree tree) {
    return makeFromTree(tree, Mode.COLLAPSED, GrammaticalStructure.Extras.NONE);
  }

  /**
   * Produces a CCProcessed SemanticGraph.
   *
   * @deprecated Use {@link #generateEnhancedDependencies(Tree)} or
   * {@link #generateEnhancedPlusPlusDependencies(Tree)} instead.
   *
   */
  @Deprecated
  public static SemanticGraph generateCCProcessedDependencies(Tree tree) {
    return makeFromTree(tree, Mode.CCPROCESSED, GrammaticalStructure.Extras.NONE);
  }

  /**
   *
   * Produces an enhanced dependencies SemanticGraph.
   */
  public static SemanticGraph generateEnhancedDependencies(Tree tree){
    return makeFromTree(tree, Mode.ENHANCED, GrammaticalStructure.Extras.NONE);
  }

  /**
   *
   * Produces an enhanced++ dependencies SemanticGraph.
   */
  public static SemanticGraph generateEnhancedPlusPlusDependencies(Tree tree){
    return makeFromTree(tree, Mode.ENHANCED_PLUS_PLUS, GrammaticalStructure.Extras.NONE);
  }

  /**
   * Produces an Uncollapsed (basic) SemanticGraph.
   */
  public static SemanticGraph generateUncollapsedDependencies(GrammaticalStructure gs) {
    return makeFromTree(gs, Mode.BASIC, GrammaticalStructure.Extras.NONE, null);
  }

  /**
   * Produces a Collapsed SemanticGraph.
   *
   * @deprecated Use {@link #generateEnhancedDependencies(GrammaticalStructure)} or
   * {@link #generateEnhancedPlusPlusDependencies(GrammaticalStructure)} instead.
   */
  @Deprecated
  public static SemanticGraph generateCollapsedDependencies(GrammaticalStructure gs) {
    return makeFromTree(gs, Mode.COLLAPSED, GrammaticalStructure.Extras.NONE, null);
  }

  /**
   * Produces a CCProcessed SemanticGraph.
   *
   * @deprecated Use {@link #generateEnhancedDependencies(GrammaticalStructure)} or
   * {@link #generateEnhancedPlusPlusDependencies(GrammaticalStructure)} instead.
   */
  @Deprecated
  public static SemanticGraph generateCCProcessedDependencies(GrammaticalStructure gs) {
    return makeFromTree(gs, Mode.CCPROCESSED, GrammaticalStructure.Extras.NONE, null);
  }

  /**
   * Produces an enhanced dependencies SemanticGraph.
   */
  public static SemanticGraph generateEnhancedDependencies(GrammaticalStructure gs) {
    return makeFromTree(gs, Mode.ENHANCED, GrammaticalStructure.Extras.NONE, null);
  }

  /**
   * Produces an enhanced++ dependencies SemanticGraph.
   */
  public static SemanticGraph generateEnhancedPlusPlusDependencies(GrammaticalStructure gs) {
    return makeFromTree(gs, Mode.ENHANCED_PLUS_PLUS, GrammaticalStructure.Extras.NONE, null);
  }

  /**
   * Produces an Uncollapsed (basic) SemanticGraph.
   *
   * The extras parameter has no effect if gs is an instance of {@link UniversalEnglishGrammaticalStructure}.
   *
   * @deprecated Use {@link #generateUncollapsedDependencies(GrammaticalStructure)} instead.
   */
  @Deprecated
  public static SemanticGraph generateUncollapsedDependencies(GrammaticalStructure gs, GrammaticalStructure.Extras extras) {
    return makeFromTree(gs, Mode.BASIC, extras, null);
  }

  /**
   * Produces a Collapsed SemanticGraph with optional extras.
   *
   * @deprecated Use {@link #generateEnhancedDependencies(GrammaticalStructure)} or
   * {@link #generateEnhancedPlusPlusDependencies(GrammaticalStructure)} instead.
   */
  @Deprecated
  public static SemanticGraph generateCollapsedDependencies(GrammaticalStructure gs, GrammaticalStructure.Extras extras) {
    return makeFromTree(gs, Mode.COLLAPSED, extras, null);
  }

  /**
   * Produces a CCProcessed SemanticGraph with optional extras.
   *
   * @deprecated Use {@link #generateEnhancedDependencies(GrammaticalStructure)} or
   * {@link #generateEnhancedPlusPlusDependencies(GrammaticalStructure)} instead.
   */
  @Deprecated
  public static SemanticGraph generateCCProcessedDependencies(GrammaticalStructure gs, GrammaticalStructure.Extras extras) {
    return makeFromTree(gs, Mode.CCPROCESSED, extras, null);
  }

  /**
   * @see #makeFromTree(Tree, Mode, GrammaticalStructure.Extras, Predicate)
   */
  public static SemanticGraph makeFromTree(Tree tree,
                                           Mode mode,
                                           GrammaticalStructure.Extras includeExtras,
                                           Predicate<TypedDependency> filter,
                                           boolean originalDependencies) {
    return makeFromTree(tree, mode, includeExtras, filter,
        originalDependencies, INCLUDE_PUNCTUATION_DEPENDENCIES);
  }

  /**
   * Returns a new {@code SemanticGraph} constructed from a given {@link
   * Tree} with given options.
   *
   * This factory method is intended to replace a profusion of highly similar
   * factory methods, such as
   * {@code typedDependencies()},
   * {@code typedDependenciesCollapsed()},
   * {@code allTypedDependencies()},
   * {@code allTypedDependenciesCollapsed()}, etc.
   *
   * For a fuller explanation of the meaning of the boolean arguments, see
   * {@link GrammaticalStructure}.
   *
   * @param tree A tree representing a phrase structure parse
   * @param includeExtras Whether to include extra dependencies, which may
   * result in a non-tree
   * @param filter A filter to exclude certain dependencies; ignored if null
   * @param originalDependencies generate original Stanford dependencies instead of new
   * Universal Dependencies
   * @return A SemanticGraph
   */
  public static SemanticGraph makeFromTree(Tree tree,
                                           Mode mode,
                                           GrammaticalStructure.Extras includeExtras,
                                           Predicate<TypedDependency> filter,
                                           boolean originalDependencies,
                                           boolean includePunctuationDependencies) {
    GrammaticalStructure gs;
    if (originalDependencies) {
      Predicate<String> wordFilt;
      if (includePunctuationDependencies) {
        wordFilt = Filters.acceptFilter();
      } else {
        wordFilt = new PennTreebankLanguagePack().punctuationWordRejectFilter();
      }
      gs = new EnglishGrammaticalStructure(tree,
          wordFilt,
          new SemanticHeadFinder(true));

    } else {
      Predicate<String> tagFilt;
      if (includePunctuationDependencies) {
        tagFilt = Filters.acceptFilter();
      } else {
        tagFilt = new PennTreebankLanguagePack().punctuationTagRejectFilter();
      }
      gs = new UniversalEnglishGrammaticalStructure(tree,
          tagFilt,
          new UniversalSemanticHeadFinder(true)
      );

    }
    return makeFromTree(gs, mode, includeExtras,
            filter);
  }



  // TODO: these booleans would be more readable as enums similar to Mode.
  // Then the arguments would make more sense
  public static SemanticGraph makeFromTree(GrammaticalStructure gs,
                                           Mode mode,
                                           GrammaticalStructure.Extras includeExtras,
                                           Predicate<TypedDependency> filter) {
    Collection<TypedDependency> deps;
    switch(mode) {
      case ENHANCED:
        deps = gs.typedDependenciesEnhanced();
        break;
      case ENHANCED_PLUS_PLUS:
        deps = gs.typedDependenciesEnhancedPlusPlus();
        break;
      case COLLAPSED_TREE:
        deps = gs.typedDependenciesCollapsedTree();
        break;
      case COLLAPSED:
        deps = gs.typedDependenciesCollapsed(includeExtras);
        break;
      case CCPROCESSED:
        deps = gs.typedDependenciesCCprocessed(includeExtras);
        break;
      case BASIC:
        deps = gs.typedDependencies(includeExtras);
        break;
      default:
        throw new IllegalArgumentException("Unknown mode " + mode);
    }

    if (filter != null) {
      List<TypedDependency> depsFiltered = Generics.newArrayList();
      for (TypedDependency td : deps) {
        if (filter.test(td)) {
          depsFiltered.add(td);
        }
      }
      deps = depsFiltered;
    }

    // there used to be an if clause that filtered out the case of empty
    // dependencies. However, I could not understand (or replicate) the error
    // it alluded to, and it led to empty dependency graphs for very short fragments,
    // which meant they were ignored by the RTE system. Changed. (pado)
    // See also the SemanticGraph constructor.

    //log.info(deps.toString());
    return new SemanticGraph(deps);
  }


  /**
   * @see #makeFromTree(GrammaticalStructure, Mode, GrammaticalStructure.Extras, Predicate)
   */
  @Deprecated
  public static SemanticGraph makeFromTree(GrammaticalStructure tree,
                                           Mode mode,
                                           boolean includeExtras,
                                           Predicate<TypedDependency> filter) {
    return makeFromTree(tree, mode, includeExtras ? GrammaticalStructure.Extras.MAXIMAL : GrammaticalStructure.Extras.NONE, filter);
  }


  /**
   * @see #makeFromTree(GrammaticalStructure, Mode, GrammaticalStructure.Extras, Predicate)
   */
  public static SemanticGraph makeFromTree(GrammaticalStructure structure) {
    return makeFromTree(structure, Mode.BASIC, GrammaticalStructure.Extras.NONE, null);
  }


  /**
   * @see #makeFromTree(Tree, Mode, GrammaticalStructure.Extras, Predicate)
   */
  public static SemanticGraph makeFromTree(Tree tree,
                                           Mode mode,
                                           GrammaticalStructure.Extras includeExtras,
                                           Predicate<TypedDependency> filter) {
    return makeFromTree(tree, mode, includeExtras, filter, false);
  }

  /**
   * @see #makeFromTree(Tree, Mode, GrammaticalStructure.Extras, Predicate)
   */
  @Deprecated
  public static SemanticGraph makeFromTree(Tree tree,
                                           Mode mode,
                                           boolean includeExtras,
                                           Predicate<TypedDependency> filter) {
    return makeFromTree(tree, mode, includeExtras ? GrammaticalStructure.Extras.MAXIMAL : GrammaticalStructure.Extras.NONE, filter, false);
  }

  /**
   * @see #makeFromTree(Tree, Mode, GrammaticalStructure.Extras, Predicate)
   */
  public static SemanticGraph makeFromTree(Tree tree,
                                           Mode mode,
                                           GrammaticalStructure.Extras includeExtras) {
    return makeFromTree(tree, mode, includeExtras, null, false);
  }

  /**
   * @see #makeFromTree(Tree, Mode, GrammaticalStructure.Extras)
   */
  @Deprecated
  public static SemanticGraph makeFromTree(Tree tree,
                                           Mode mode,
                                           boolean includeExtras) {
    return makeFromTree(tree, mode, includeExtras ? GrammaticalStructure.Extras.MAXIMAL : GrammaticalStructure.Extras.NONE);
  }

  /**
   * Given a list of edges, attempts to create and return a rooted SemanticGraph.
   *
   * TODO: throw Exceptions, or flag warnings on conditions for concern (no root, etc)
   */
  public static SemanticGraph makeFromEdges(Iterable<SemanticGraphEdge> edges) {
    // Identify the root(s) of this graph
    SemanticGraph sg = new SemanticGraph();
    Collection<IndexedWord> vertices = getVerticesFromEdgeSet(edges);
    for (IndexedWord vertex : vertices) {
      sg.addVertex(vertex);
    }
    for (SemanticGraphEdge edge : edges) {
      sg.addEdge(edge.getSource(),edge.getTarget(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }

    sg.resetRoots();
    return sg;
  }

  /**
   * Given a list of edges, attempts to create and return a rooted SemanticGraph.
   *
   * TODO: throw Exceptions, or flag warnings on conditions for concern (no root, etc)
   */
  public static SemanticGraph makeFromEdges(Iterable<SemanticGraphEdge> edges, Collection<IndexedWord> roots) {
    // Identify the root(s) of this graph
    SemanticGraph sg = new SemanticGraph();
    Collection<IndexedWord> vertices = getVerticesFromEdgeSet(edges);
    for (IndexedWord vertex : vertices) {
      sg.addVertex(vertex);
    }
    for (IndexedWord vertex : roots) {
      sg.addVertex(vertex);
    }
    for (SemanticGraphEdge edge : edges) {
      sg.addEdge(edge.getSource(),edge.getTarget(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }

    sg.setRoots(roots);
    return sg;
  }

  /**
   * Given an iterable set of edges, returns the set of  vertices covered by these edges.
   *
   * Note: CDM changed the return of this from a List to a Set in 2011. This seemed more
   * sensible.  Hopefully it doesn't break anything....
   */
  private static Set<IndexedWord> getVerticesFromEdgeSet(Iterable<SemanticGraphEdge> edges) {
    Set<IndexedWord> retSet = Generics.newHashSet();
    for (SemanticGraphEdge edge : edges) {
      retSet.add(edge.getGovernor());
      retSet.add(edge.getDependent());
    }
    return retSet;
  }


  /**
   * Given a set of vertices, and the source graph they are drawn from, create a path composed
   * of the minimum paths between the vertices.  i.e. this is a simple brain-dead attempt at getting
   * something approximating a minimum spanning graph.
   *
   * NOTE: the hope is the vertices will already be contiguous, but facilities are added just in case for
   * adding additional nodes.
   */
  public static SemanticGraph makeFromVertices(SemanticGraph sg, Collection<IndexedWord> nodes) {
    List<SemanticGraphEdge> edgesToAdd = new ArrayList<>();
    List<IndexedWord> nodesToAdd = new ArrayList<>(nodes);
    for (IndexedWord nodeA :nodes) {
      for (IndexedWord nodeB : nodes) {
        if (nodeA != nodeB) {
          List<SemanticGraphEdge> edges = sg.getShortestDirectedPathEdges(nodeA, nodeB);
          if (edges != null) {
            edgesToAdd.addAll(edges);
            for (SemanticGraphEdge edge : edges) {
              IndexedWord gov = edge.getGovernor();
              IndexedWord dep = edge.getDependent();
              if (gov != null && !nodesToAdd.contains(gov)) {
                nodesToAdd.add(gov);
              }
              if (dep != null && !nodesToAdd.contains(dep)) {
                nodesToAdd.add(dep);
              }
            }
          }
        }
      }
    }

    SemanticGraph retSg = new SemanticGraph();
    for (IndexedWord node : nodesToAdd) {
      retSg.addVertex(node);
    }
    for (SemanticGraphEdge edge : edgesToAdd) {
      retSg.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }

    retSg.resetRoots();
    return retSg;
  }

  /**
   * This creates a new graph based off the given, but uses the existing nodes objects.
   */
  public static SemanticGraph duplicateKeepNodes(SemanticGraph sg) {
    SemanticGraph retSg = new SemanticGraph();
    for (IndexedWord node : sg.vertexSet()) {
      retSg.addVertex(node);
    }
    retSg.setRoots(sg.getRoots());
    for (SemanticGraphEdge edge : sg.edgeIterable()) {
      retSg.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
    return retSg;
  }

  /**
   * Given a list of graphs, constructs a new graph combined from the
   * collection of graphs.  Original vertices are used, edges are
   * copied.  Graphs are ordered by the sentence index and index of
   * the original vertices.  Intent is to create a "mega graph"
   * similar to the graphs used in the RTE problem.
   * <br>
   * This method only works if the indexed words have different
   * sentence ids, as otherwise the maps used will confuse several of
   * the IndexedWords.
   */
  public static SemanticGraph makeFromGraphs(Collection<SemanticGraph> sgList) {
    SemanticGraph sg = new SemanticGraph();
    Collection<IndexedWord> newRoots = Generics.newHashSet();
    for (SemanticGraph currSg : sgList) {
      newRoots.addAll(currSg.getRoots());
      for (IndexedWord currVertex : currSg.vertexSet())
        sg.addVertex(currVertex);
      for (SemanticGraphEdge currEdge : currSg.edgeIterable())
        sg.addEdge(currEdge.getGovernor(), currEdge.getDependent(),
            currEdge.getRelation(), currEdge.getWeight(), currEdge.isExtra());
    }
    sg.setRoots(newRoots);
    return sg;
  }

  /**
   * Like makeFromGraphs, but it makes a deep copy of the graphs and
   * renumbers the index words.
   * <br>
   * {@code lengths} must be a vector containing the number of
   * tokens in each sentence.  This is used to reindex the tokens.
   */
  public static SemanticGraph deepCopyFromGraphs(List<SemanticGraph> graphs,
                                                 List<Integer> lengths) {
    SemanticGraph newGraph = new SemanticGraph();
    Map<Integer, IndexedWord> newWords = Generics.newHashMap();
    List<IndexedWord> newRoots = new ArrayList<>();
    int vertexOffset = 0;
    for (int i = 0; i < graphs.size(); ++i) {
      SemanticGraph graph = graphs.get(i);
      for (IndexedWord vertex : graph.vertexSet()) {
        IndexedWord newVertex = new IndexedWord(vertex);
        newVertex.setIndex(vertex.index() + vertexOffset);
        newGraph.addVertex(newVertex);
        newWords.put(newVertex.index(), newVertex);
      }
      for (SemanticGraphEdge edge : graph.edgeIterable()) {
        IndexedWord gov = newWords.get(edge.getGovernor().index() +
            vertexOffset);
        IndexedWord dep = newWords.get(edge.getDependent().index() +
            vertexOffset);
        if (gov == null || dep == null) {
          throw new AssertionError("Counting problem (or broken edge)");
        }
        newGraph.addEdge(gov, dep, edge.getRelation(), edge.getWeight(), edge.isExtra());
      }
      for (IndexedWord root : graph.getRoots()) {
        newRoots.add(newWords.get(root.index() + vertexOffset));
      }
      vertexOffset += lengths.get(i);
    }
    newGraph.setRoots(newRoots);
    return newGraph;
  }

}
