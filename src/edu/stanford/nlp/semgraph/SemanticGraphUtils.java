package edu.stanford.nlp.semgraph;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.StringWriter;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * Generic utilities for dealing with Dependency graphs and other structures, useful for
 * text simplification and rewriting.
 *
 * TODO: Migrate some of the functions (that make sense) into SemanticGraph proper.
 * BUT BEWARE: This class has methods that use jgraph (as opposed to jgrapht).
 * We don't want our core code to become dependent on jgraph, so methods in
 * SemanticGraph shouldn't call methods in this class, and methods that use
 * jgraph shouldn't be moved into SemanticGraph.
 *
 * @author Eric Yeh
 *
 */
public class SemanticGraphUtils  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemanticGraphUtils.class);

  private SemanticGraphUtils() {}

  /**
   * Given a collection of nodes from srcGraph, generates a new
   * SemanticGraph based off the subset represented by those nodes.
   * This uses the same vertices as in the original graph, which
   * allows for equality and comparisons between the two graphs.
   * With more than one node, only the nodes which have an edge to or from
   * another node in the collection (or themselves) are included.
   *
   * @param nodes the nodes to include
   * @param srcGraph the graph the nodes and edges come from
   * @return a new graph over those nodes and the edges between them,
   *   or null if nodes is empty
   */
  public static SemanticGraph makeGraphFromNodes(Collection<IndexedWord> nodes, SemanticGraph srcGraph) {
    if (nodes.size() == 1) {
      SemanticGraph retSg = new SemanticGraph();
      for (IndexedWord node :nodes)
        retSg.addVertex(node);
      return retSg;
    }

    if (nodes.isEmpty()) {
      return null;
    }

    // TODO: if any nodes are not connected to edges in the original
    // graph, this will leave them out
    List<SemanticGraphEdge> edges = new ArrayList<>();
    for (IndexedWord nodeG : nodes) {
      for (IndexedWord nodeD: nodes) {
        Collection<SemanticGraphEdge> existingEdges =
          srcGraph.getAllEdges(nodeG, nodeD);
        if (existingEdges != null) {
          edges.addAll(existingEdges);
        }
      }
    }
    return SemanticGraphFactory.makeFromEdges(edges);
  }

  //----------------------------------------------------------------------------------------
  //Query routines (obtaining sets of edges/vertices over predicates, etc)
  //----------------------------------------------------------------------------------------

  /**
   * Finds the vertex in the given SemanticGraph that corresponds to the given node.
   * Returns null if cannot find. Uses first match on index, sentIndex, and word values.
   *
   * @param node the node to look for
   * @param sg the graph to search
   * @return the matching vertex of sg, or null
   */
  public static IndexedWord findMatchingNode(IndexedWord node,
                                             SemanticGraph sg) {
    for (IndexedWord tgt : sg.vertexSet()) {
      if ((tgt.index() == node.index()) &&
          (tgt.sentIndex() == node.sentIndex()) &&
          (tgt.word().equals(node.word())) )
        return tgt;
    }
    return null;
  }


  /**
   * Given a starting vertice, grabs the subtree encapsulated by portion of the semantic graph, excluding
   * a given edge.  A tabu list is maintained, in order to deal with cyclical relations (such as between a
   * rcmod (relative clause) and its nsubj).
   *
   * @param vertice the vertex to start from
   * @param sg the graph to search
   * @param excludedEdge an edge which is neither followed nor returned
   * @return the edges reachable from vertice by following outgoing edges
   */
  public static Set<SemanticGraphEdge> getSubTreeEdges(IndexedWord vertice, SemanticGraph sg, SemanticGraphEdge excludedEdge) {
    Set<SemanticGraphEdge> tabu = Generics.newHashSet();
    tabu.add(excludedEdge);
    getSubTreeEdgesHelper(vertice, sg, tabu);
    tabu.remove(excludedEdge); // Do not want this in the returned edges
    return tabu;
  }

  /**
   * Adds to tabuEdges each edge reachable from vertice by following outgoing
   * edges, without following any edge already in tabuEdges.
   *
   * @param vertice the vertex to start from
   * @param sg the graph to search
   * @param tabuEdges the edges already found or excluded; updated in place
   */
  public static void getSubTreeEdgesHelper(IndexedWord vertice, SemanticGraph sg, Set<SemanticGraphEdge> tabuEdges) {
    for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(vertice)) {
      if (!tabuEdges.contains(edge)) {
        IndexedWord dep = edge.getDependent();
        tabuEdges.add(edge);
        getSubTreeEdgesHelper(dep, sg, tabuEdges);
      }
    }
  }


  /**
   * Given a set of nodes from a SemanticGraph, returns the set of
   * edges that are spanned between these nodes.
   * Edges from a node to itself are not included.
   *
   * @param nodes the nodes
   * @param sg the graph containing the nodes
   * @return the edges between two different nodes in the collection
   */
  public static Collection<SemanticGraphEdge> getEdgesSpannedByVertices(Collection<IndexedWord> nodes, SemanticGraph sg) {
    Collection<SemanticGraphEdge> ret = Generics.newHashSet();
    for (IndexedWord n1 : nodes)
      for (IndexedWord n2: nodes) {
        if (n1 != n2) {
          Collection<SemanticGraphEdge> edges = sg.getAllEdges(n1, n2);
          if (edges != null) ret.addAll(edges);
        }
      }
    return ret;
  }

  /**
   * Returns a list of all children bearing a grammatical relation starting with the given string, relnPrefix
   *
   * @param graph the graph containing vertex
   * @param vertex the parent node
   * @param relnPrefix the prefix to look for in the relation names
   * @return the children with a matching relation; an empty list if vertex is NO_WORD
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static List<IndexedWord> getChildrenWithRelnPrefix(SemanticGraph graph, IndexedWord vertex, String relnPrefix) {
    if (vertex.equals(IndexedWord.NO_WORD))
      return new ArrayList<>();
    if (!graph.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    List<IndexedWord> childList = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.outgoingEdgeIterable(vertex)) {
      if (edge.getRelation().toString().startsWith(relnPrefix)) {
        childList.add(edge.getTarget());
      }
    }
    return childList;
  }

  /**
   * Returns a list of all children bearing a grammatical relation starting with the given set of relation prefixes
   *
   * @param graph the graph containing vertex
   * @param vertex the parent node
   * @param relnPrefixes the prefixes to look for in the relation names
   * @return the children with a relation matching any of the prefixes;
   *   an empty list if vertex is NO_WORD
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static List<IndexedWord> getChildrenWithRelnPrefix(SemanticGraph graph, IndexedWord vertex, Collection<String> relnPrefixes) {
    if (vertex.equals(IndexedWord.NO_WORD))
      return new ArrayList<>();
    if (!graph.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    List<IndexedWord> childList = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.outgoingEdgeIterable(vertex)) {
      String edgeString = edge.getRelation().toString();
      for (String relnPrefix : relnPrefixes) {
        if (edgeString.startsWith(relnPrefix)) {
          childList.add(edge.getTarget());
          break;
        }
      }
    }
    return childList;
  }

  /**
   * Since graphs can be have preps collapsed, finds all the immediate children of this node
   * that are linked by a collapsed preposition edge.
   * This is any edge whose relation name starts with "prep".
   *
   * @param sg the graph containing vertex
   * @param vertex the parent node
   * @return the children linked by such an edge
   */
  public static List<IndexedWord> getChildrenWithPrepC(SemanticGraph sg, IndexedWord vertex) {
    List<IndexedWord> ret = new ArrayList<>();
    //  Collection<GrammaticalRelation> prepCs = EnglishGrammaticalRelations.getPrepsC();
    //  for (SemanticGraphEdge edge : sg.outgoingEdgesOf(vertex)) {
    //  if (prepCs.contains(edge.getRelation()))
    for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(vertex)) {
      if (edge.getRelation().toString().startsWith("prep"))
        ret.add(edge.getDependent());
    }
    return ret;
  }

  /**
   * Returns the set of incoming edges for the given node that have the given
   * relation.
   *
   * Because certain edges may remain in string form (prepcs), check for both
   * string and object form of relations.
   *
   * @param node the dependent node
   * @param sg the graph containing node
   * @param reln the relation to look for
   * @return the incoming edges with that relation
   */
  public static List<SemanticGraphEdge> incomingEdgesWithReln(IndexedWord node, SemanticGraph sg, GrammaticalRelation reln) {
    return edgesWithReln(sg.incomingEdgeIterable(node), reln);
  }

  /**
   * Checks for outgoing edges of the node, in the given graph, which contain
   * the given relation.  Relations are matched on if they are GrammaticalRelation
   * objects or strings.
   *
   * @param node the governor node
   * @param sg the graph containing node
   * @param reln the relation to look for
   * @return the outgoing edges with that relation
   */
  public static List<SemanticGraphEdge> outgoingEdgesWithReln(IndexedWord node, SemanticGraph sg, GrammaticalRelation reln) {
    return edgesWithReln(sg.outgoingEdgeIterable(node), reln);
  }

  /**
   * Given a list of edges, returns those which match the given relation (can be string or
   * GrammaticalRelation object).
   *
   * @param edges the edges to filter
   * @param reln the relation to look for
   * @return the edges whose relation equals reln
   */
  public static List<SemanticGraphEdge> edgesWithReln(Iterable<SemanticGraphEdge> edges,
                                                      GrammaticalRelation reln) {
    List<SemanticGraphEdge> found = Generics.newArrayList();
    for (SemanticGraphEdge edge : edges) {
      GrammaticalRelation tgtReln = edge.getRelation();
      if (tgtReln.equals(reln)) {
        found.add(edge);
      }
    }
    return found;
  }

  /**
   * Given a semantic graph, and a relation prefix, returns a list of all relations (edges)
   * that start with the given prefix (e.g., prefix "prep" gives you all the prep relations: prep_by, pref_in,etc.)
   *
   * @param sg the graph to search
   * @param prefix the prefix to look for in the relation names
   * @return the edges whose relation name starts with prefix
   */
  public static List<SemanticGraphEdge> findAllRelnsWithPrefix(SemanticGraph sg, String prefix) {
    ArrayList<SemanticGraphEdge> relns = new ArrayList<>();
    for (SemanticGraphEdge edge : sg.edgeIterable()) {
      GrammaticalRelation edgeRelation = edge.getRelation();
      if (edgeRelation.toString().startsWith(prefix)) {
        relns.add(edge);
      }
    }
    return relns;
  }

  /**
   * Finds the descendents of the given node in graph, avoiding the given set of nodes
   * <br>
   * The result includes vertex itself, unless vertex is in tabu, in which case it is empty.
   * The descendants are not searched past a tabu node.
   *
   * @param sg the graph to search
   * @param vertex the node to start from
   * @param tabu the nodes to avoid
   * @return the descendants found
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static Set<IndexedWord> tabuDescendants(SemanticGraph sg, IndexedWord vertex, Collection<IndexedWord> tabu) {
    if (!sg.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    // Do a depth first search
    Set<IndexedWord> descendantSet = Generics.newHashSet();
    tabuDescendantsHelper(sg, vertex, descendantSet, tabu, null, (Predicate<IndexedWord>) null);
    return descendantSet;
  }

  /**
   * Finds the set of descendants for a node in the graph, avoiding the set of nodes and the
   * set of edge relations.  NOTE: these edges are encountered from the downward cull,
   * from governor to dependent.
   * <br>
   * The result includes vertex itself, unless vertex is in tabu, in which case it is empty.
   *
   * @param sg the graph to search
   * @param vertex the node to start from
   * @param tabu the nodes to avoid
   * @param tabuRelns the relations not to follow
   * @return the descendants found
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static Set<IndexedWord> tabuDescendants(SemanticGraph sg, IndexedWord vertex, Collection<IndexedWord> tabu,
                                                 Collection<GrammaticalRelation> tabuRelns) {
    if (!sg.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    // Do a depth first search
    Set<IndexedWord> descendantSet = Generics.newHashSet();
    tabuDescendantsHelper(sg, vertex, descendantSet, tabu, tabuRelns, (Predicate<IndexedWord>) null);
    return descendantSet;
  }

  /**
   * Finds the descendants of the given node in the graph, not following
   * edges with the given relations.  The result includes vertex itself.
   *
   * @param sg the graph to search
   * @param vertex the node to start from
   * @param tabuRelns the relations not to follow
   * @return the descendants found
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static Set<IndexedWord> descendantsTabuRelns(SemanticGraph sg, IndexedWord vertex,
                                                      Collection<GrammaticalRelation> tabuRelns) {
    if (!sg.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    // Do a depth first search
    Set<IndexedWord> descendantSet = Generics.newHashSet();
    tabuDescendantsHelper(sg, vertex, descendantSet, Generics.newHashSet(), tabuRelns, (Predicate<IndexedWord>) null);
    return descendantSet;
  }

  /**
   * Finds the descendants of the given node in the graph, not following
   * edges with the given relations or edges to a child which passes tabuTest.
   * The result includes vertex itself.
   *
   * @param sg the graph to search
   * @param vertex the node to start from
   * @param tabuRelns the relations not to follow
   * @param tabuTest children for which this is true are not followed
   * @return the descendants found
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static Set<IndexedWord> descendantsTabuTestAndRelns(SemanticGraph sg, IndexedWord vertex,
      Collection<GrammaticalRelation> tabuRelns, Predicate<IndexedWord> tabuTest) {
    if (!sg.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    // Do a depth first search
    Set<IndexedWord> descendantSet = Generics.newHashSet();
    tabuDescendantsHelper(sg, vertex, descendantSet, Generics.newHashSet(), tabuRelns, tabuTest);
    return descendantSet;
  }

  /**
   * Finds the descendants of the given node in the graph, avoiding the tabu
   * nodes and not following edges with the given relations or edges to a
   * child which passes tabuTest.
   * The result includes vertex itself, unless vertex is in tabuNodes, in which case it is empty.
   *
   * @param sg the graph to search
   * @param vertex the node to start from
   * @param tabuNodes the nodes to avoid
   * @param tabuRelns the relations not to follow
   * @param tabuTest children for which this is true are not followed
   * @return the descendants found
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static Set<IndexedWord> descendantsTabuTestAndRelns(SemanticGraph sg, IndexedWord vertex,
      Collection<IndexedWord> tabuNodes, Collection<GrammaticalRelation> tabuRelns, Predicate<IndexedWord> tabuTest) {
    if (!sg.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    // Do a depth first search
    Set<IndexedWord> descendantSet = Generics.newHashSet();
    tabuDescendantsHelper(sg, vertex, descendantSet, tabuNodes, tabuRelns, tabuTest);
    return descendantSet;
  }

  /**
   * Finds the descendants of the given node in the graph, avoiding the tabu
   * nodes and not following edges with the given relations or edges to a
   * child which passes tabuTest.
   * The result includes vertex itself, unless vertex is in tabuNodes, in which case it is empty.
   *
   * @param sg the graph to search
   * @param vertex the node to start from
   * @param tabuNodes the nodes to avoid
   * @param tabuRelns the relations not to follow
   * @param tabuTest children for which this is true, given the child and sg, are not followed
   * @return the descendants found
   * @throws IllegalArgumentException if vertex is not in the graph
   */
  public static Set<IndexedWord> descendantsTabuTestAndRelns(SemanticGraph sg, IndexedWord vertex,
                                                             Collection<IndexedWord> tabuNodes, Collection<GrammaticalRelation> tabuRelns,
                                                             BiPredicate<IndexedWord,SemanticGraph> tabuTest) {
    if ( ! sg.containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    // Do a depth first search
    Set<IndexedWord> descendantSet = Generics.newHashSet();
    tabuDescendantsHelper(sg, vertex, descendantSet, tabuNodes, tabuRelns, tabuTest);
    return descendantSet;
  }



  /**
   * Performs a cull for the descendants of the given node in the
   * graph, subject to the tabu nodes to avoid, relations to avoid
   * crawling over, and child nodes to avoid traversing to based upon
   * a predicate test.
   */
  private static void tabuDescendantsHelper(SemanticGraph sg, IndexedWord curr, Set<IndexedWord> descendantSet, Collection<IndexedWord> tabu,
      Collection<GrammaticalRelation> relnsToAvoid, Predicate<IndexedWord> tabuTest) {
    if (tabu.contains(curr))
      return;
    if (descendantSet.contains(curr)) {
      return;
    }

    descendantSet.add(curr);
    for (IndexedWord child : sg.getChildren(curr)) {
      for (SemanticGraphEdge edge : sg.getAllEdges(curr, child)) {
        if (relnsToAvoid != null && relnsToAvoid.contains(edge.getRelation()))
          continue;
        if (tabuTest != null && tabuTest.test(edge.getDependent()))
          continue;
        tabuDescendantsHelper(sg, child, descendantSet, tabu, relnsToAvoid,
                              tabuTest);
      }
    }
  }

  /**
   * Performs a cull for the descendants of the given node in the
   * graph, subject to the tabu nodes to avoid, relations to avoid
   * crawling over, and child nodes to avoid traversing to based upon
   * a predicate test.
   */
  private static void tabuDescendantsHelper(SemanticGraph sg, IndexedWord curr, Set<IndexedWord> descendantSet, Collection<IndexedWord> tabu,
                                            Collection<GrammaticalRelation> relnsToAvoid, BiPredicate<IndexedWord,SemanticGraph> tabuTest) {
    if (tabu.contains(curr))
      return;
    if (descendantSet.contains(curr)) {
      return;
    }

    descendantSet.add(curr);
    for (IndexedWord child : sg.getChildren(curr)) {
      for (SemanticGraphEdge edge : sg.getAllEdges(curr, child)) {
        if (relnsToAvoid != null && relnsToAvoid.contains(edge.getRelation()))
          continue;
        if (tabuTest != null && tabuTest.test(edge.getDependent(), sg))
          continue;
        tabuDescendantsHelper(sg, child, descendantSet, tabu, relnsToAvoid,
                tabuTest);
      }
    }
  }


  //------------------------------------------------------------------------------------
  //"Constituent" extraction and manipulation
  //------------------------------------------------------------------------------------


  /**
   * Returns the vertice that is "leftmost."  Note this requires that the IndexedFeatureLabels present actually have
   * ordering information.
   * TODO: can be done more efficiently?
   *
   * @param startNode the node whose descendants are searched
   * @param sg the graph containing startNode
   * @return the first of startNode and its descendants in IndexedWord order,
   *   which may be startNode itself
   */
  public static IndexedWord leftMostChildVertice(IndexedWord startNode, SemanticGraph sg) {
    TreeSet<IndexedWord> vertices = new TreeSet<>();
    for (IndexedWord vertex : sg.descendants(startNode)) {
      vertices.add(vertex);
    }
    return vertices.first();
  }

  /**
   * Returns the vertices that are "leftmost, rightmost"  Note this requires that the IndexedFeatureLabels present actually have
   * ordering information.
   * TODO: can be done more efficiently?
   *
   * @param startNode the node whose descendants are searched
   * @param sg the graph containing startNode
   * @return the first and last of startNode and its descendants in IndexedWord order
   */
  public static Pair<IndexedWord, IndexedWord> leftRightMostChildVertices(IndexedWord startNode, SemanticGraph sg) {
    TreeSet<IndexedWord> vertices = new TreeSet<>();
    for (IndexedWord vertex : sg.descendants(startNode)) {
      vertices.add(vertex);
    }
    return Pair.makePair(vertices.first(), vertices.last());
  }

  /**
   * Given a SemanticGraph, and a set of nodes, finds the "blanket" of nodes that are one
   * edge away from the set of nodes passed in.  This is similar to the idea of a Markov
   * Blanket, except in the context of a SemanticGraph.
   * TODO: optimize
   *
   * @param sg the graph containing the nodes
   * @param assertedNodes the nodes whose neighbors are wanted
   * @return the nodes not in assertedNodes which share an edge with one of them
   */
  public static Collection<IndexedWord> getDependencyBlanket(SemanticGraph sg, Collection<IndexedWord> assertedNodes) {
    Set<IndexedWord> retSet = Generics.newHashSet();
    for (IndexedWord curr : sg.vertexSet()) {
      if (!assertedNodes.contains(curr) && !retSet.contains(curr)) {
        for (IndexedWord assertedNode : assertedNodes) {
          if (sg.containsEdge(assertedNode, curr) ||
              sg.containsEdge(curr, assertedNode)) {
            retSet.add(curr);
          }
        }
      }
    }
    return retSet;
  }

  /**
   * Resets the indices for the vertices in the graph, using the current
   * ordering returned by vertexList (presumably in order).  This is to ensure
   * accesses to the InfoFile word table do not fall off after a SemanticGraph has
   * been edited.
   * <br>
   * NOTE: the vertices will be replaced, as JGraphT does not permit
   * in-place modification of the nodes.  (TODO: we no longer use
   * JGraphT, so this should be fixed)
   *
   * @param sg the graph to renumber; it is not changed
   * @return a new graph with copies of the vertices, numbered from 1 in sorted order
   */
  public static SemanticGraph resetVerticeOrdering(SemanticGraph sg) {
    SemanticGraph nsg = new SemanticGraph();
    List<IndexedWord> vertices = sg.vertexListSorted();
    int index = 1;
    Map<IndexedWord, IndexedWord> oldToNewVertices = Generics.newHashMap();
    List<IndexedWord> newVertices = new ArrayList<>();
    for (IndexedWord vertex : vertices) {
      IndexedWord newVertex = new IndexedWord(vertex);
      newVertex.setIndex(index++);
      oldToNewVertices.put(vertex, newVertex);
      ///sg.removeVertex(vertex);
      newVertices.add(newVertex);
    }

    for (IndexedWord nv : newVertices) {
      nsg.addVertex(nv);
    }

    List<IndexedWord> newRoots = new ArrayList<>();
    for (IndexedWord or : sg.getRoots()) {
      newRoots.add(oldToNewVertices.get(or));
    }
    nsg.setRoots(newRoots);

    for (SemanticGraphEdge edge : sg.edgeIterable()) {
      IndexedWord newGov = oldToNewVertices.get(edge.getGovernor());
      IndexedWord newDep = oldToNewVertices.get(edge.getDependent());
      nsg.addEdge(newGov, newDep, edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
    return nsg;
  }


  /**
   * Given a graph, ensures all edges are EnglishGrammaticalRelations.
   * NOTE: this is English specific.
   * NOTE: currently EnglishGrammaticalRelations does not link collapsed prep string forms
   * back to their object forms, for its valueOf relation.  This may need to be repaired if
   * generated edges indeed do have collapsed preps as strings.
   */
  private static void enRepairEdges(SemanticGraph sg, boolean verbose) {
    for (SemanticGraphEdge edge : sg.edgeIterable()) {
      if (edge.getRelation().isFromString()) {
        GrammaticalRelation newReln = EnglishGrammaticalRelations.valueOf(edge.getRelation().toString());
        if (newReln != null) {
          IndexedWord gov = edge.getGovernor();
          IndexedWord dep = edge.getDependent();
          double weight = edge.getWeight();
          boolean isExtra = edge.isExtra();
          sg.removeEdge(edge);
          sg.addEdge(gov, dep, newReln, weight, isExtra);
        } else {
          if (verbose)
            log.info("Warning, could not find matching GrammaticalRelation for reln=" + edge.getRelation());
        }
      }
    }
  }

  /**
   * Replaces each edge whose relation was made from a String with an
   * equivalent edge using the matching EnglishGrammaticalRelation, if there is one.
   * NOTE: this is English specific.
   *
   * @param sg the graph to repair in place
   */
  public static void enRepairEdges(SemanticGraph sg) {
    enRepairEdges(sg, false);
  }

  /**
   * Deletes all nodes that are not rooted (such as dangling vertices after a series of
   * edges have been chopped).
   * A node is kept if it is a root or a descendant of a root.
   *
   * @param sg the graph to edit in place
   */
  public static void killNonRooted(SemanticGraph sg) {
    List<IndexedWord> nodes = new ArrayList<>(sg.vertexSet());

    // Hack: store all of the nodes we know are in the rootset
    Set<IndexedWord> guaranteed = Generics.newHashSet();
    for (IndexedWord root : sg.getRoots()) {
      guaranteed.add(root);
      guaranteed.addAll(sg.descendants(root));
    }

    for (IndexedWord node : nodes) {
      if (!guaranteed.contains(node)) {
        sg.removeVertex(node);
      }
    }
  }

  /**
   * Replaces a node in the given SemanticGraph with the new node,
   * replacing its position in the node edges.
   * If oldNode is not in the graph, this logs a message and does nothing.
   * The roots of the graph are not updated.
   *
   * @param newNode the node to put in the graph
   * @param oldNode the node to remove
   * @param sg the graph to edit in place
   */
  public static void replaceNode(IndexedWord newNode, IndexedWord oldNode, SemanticGraph sg) {
    // Obtain the edges where the old node was the governor and the dependent.
    // Remove the old node, insert the new, and re-insert the edges.
    // Save the edges in a list so that remove operations don't affect
    // the iterator or our ability to find the edges in the first place
    List<SemanticGraphEdge> govEdges = sg.outgoingEdgeList(oldNode);
    List<SemanticGraphEdge> depEdges = sg.incomingEdgeList(oldNode);
    boolean oldNodeRemoved = sg.removeVertex(oldNode);
    if (oldNodeRemoved) {
      // If the new node is not present, be sure to add it in.
      if (!sg.containsVertex(newNode)) {
        sg.addVertex(newNode);
      }
      for (SemanticGraphEdge govEdge : govEdges) {
        sg.removeEdge(govEdge);
        sg.addEdge(newNode, govEdge.getDependent(), govEdge.getRelation(), govEdge.getWeight(), govEdge.isExtra());
      }
      for (SemanticGraphEdge depEdge : depEdges) {
        sg.removeEdge(depEdge);
        sg.addEdge(depEdge.getGovernor(), newNode, depEdge.getRelation(), depEdge.getWeight(), depEdge.isExtra());
      }
    } else {
      log.info("SemanticGraphUtils.replaceNode: previous node does not exist");
    }
  }

  /** The word, original text and lemma given to vertices replaced by {@link #makeReplacedEdges} */
  public static final String WILDCARD_VERTICE_TOKEN = "WILDCARD";
  /** A vertex with word, value and original text all set to {@code *} */
  public static final IndexedWord WILDCARD_VERTICE = new IndexedWord();
  static {
    WILDCARD_VERTICE.setWord("*");
    WILDCARD_VERTICE.setValue("*");
    WILDCARD_VERTICE.setOriginalText("*");
  }

  /**
   * Given an iterable set of distinct vertices, creates a new mapping that maps the
   * original vertices to a set of "generic" versions.  Used for generalizing tokens in discovered rules.
   *
   * @param verts Vertices to anonymize
   * @param prefix Prefix to assign to this anonymization
   * @return a map from each original vertex to a copy with an empty lemma and
   *   with its word, value and original text set to prefix followed by a number,
   *   counting from 1
   */
  public static Map<IndexedWord, IndexedWord> anonymyizeNodes(Iterable<IndexedWord> verts, String prefix) {
    Map<IndexedWord, IndexedWord> retMap = Generics.newHashMap();
    int index = 1;
    for (IndexedWord orig: verts) {
      IndexedWord genericVert = new IndexedWord(orig);
      genericVert.set(CoreAnnotations.LemmaAnnotation.class, "");
      String genericValue = prefix+index;
      genericVert.setValue(genericValue);
      genericVert.setWord(genericValue);
      genericVert.setOriginalText(genericValue);
      index++;
      retMap.put(orig, genericVert);
    }
    return retMap;
  }


  /** Prefix used by {@link #makeGenericVertices} */
  public static final String SHARED_NODE_ANON_PREFIX ="A";
  /** Prefix used by {@link #makeBlanketVertices} */
  public static final String BLANKET_NODE_ANON_PREFIX ="B";

  /**
   * Used to make a mapping that lets you create "anonymous" versions of shared nodes between two
   * graphs (given in the arg) using the shared prefix.
   *
   * @param verts the vertices to anonymize
   * @return a map from each vertex to an anonymized copy, as in {@link #anonymyizeNodes}
   */
  public static Map<IndexedWord, IndexedWord> makeGenericVertices(Iterable<IndexedWord> verts) {
    return anonymyizeNodes(verts, SHARED_NODE_ANON_PREFIX);
  }

  /**
   * Used to assign generic labels to the nodes in the "blanket" for a set of vertices in a graph.  Here, a "blanket" node is
   * similar to nodes in a Markov Blanket, i.e. nodes that are one edge away from a set of asserted vertices in a
   * SemanticGraph.
   *
   * @param verts the vertices to anonymize
   * @return a map from each vertex to an anonymized copy, as in {@link #anonymyizeNodes}
   */
  public static Map<IndexedWord, IndexedWord> makeBlanketVertices(Iterable<IndexedWord> verts) {
    return anonymyizeNodes(verts, BLANKET_NODE_ANON_PREFIX);
  }


  /**
   * Given a set of edges, and a mapping between the replacement and target vertices that comprise the
   * vertices of the edges, returns a new set of edges with the replacement vertices.  If a replacement
   * is not present and useGenericReplacement is set, a copy of the vertex with its word, original
   * text and lemma set to WILDCARD_VERTICE_TOKEN is used in its place (i.e. can be anything);
   * otherwise the original vertex is kept.
   *
   * Currently used to generate "generic" versions of Semantic Graphs, when given a list of generic
   * vertices to replace with, but can conceivably be used for other purposes where vertices must
   * be replaced.
   *
   * @param edges the edges to copy
   * @param vertReplacementMap map from the original vertices to their replacements
   * @param useGenericReplacement whether to use a wildcard copy of a vertex with no replacement
   * @return new edges with the same relations, weights and extra flags as the originals
   */
  public static List<SemanticGraphEdge> makeReplacedEdges(Iterable<SemanticGraphEdge> edges, Map<IndexedWord, IndexedWord> vertReplacementMap,
      boolean useGenericReplacement) {
    List<SemanticGraphEdge> retList = new ArrayList<>();
    for (SemanticGraphEdge edge : edges) {
      IndexedWord gov = edge.getGovernor();
      IndexedWord dep = edge.getDependent();
      IndexedWord newGov = vertReplacementMap.get(gov);
      IndexedWord newDep = vertReplacementMap.get(dep);
      if (useGenericReplacement) {
        if (newGov == null) {
          newGov = new IndexedWord(gov);
          newGov.set(CoreAnnotations.TextAnnotation.class, WILDCARD_VERTICE_TOKEN);
          newGov.set(CoreAnnotations.OriginalTextAnnotation.class, WILDCARD_VERTICE_TOKEN);
          newGov.set(CoreAnnotations.LemmaAnnotation.class, WILDCARD_VERTICE_TOKEN);
        }
        if (newDep == null) {
          newDep = new IndexedWord(dep);
          newDep.set(CoreAnnotations.TextAnnotation.class, WILDCARD_VERTICE_TOKEN);
          newDep.set(CoreAnnotations.OriginalTextAnnotation.class, WILDCARD_VERTICE_TOKEN);
          newDep.set(CoreAnnotations.LemmaAnnotation.class,WILDCARD_VERTICE_TOKEN);
        }
      } else {
        if (newGov == null)
          newGov = edge.getGovernor();
        if (newDep == null)
          newDep = edge.getDependent();
      }
      SemanticGraphEdge newEdge = new SemanticGraphEdge(newGov, newDep, edge.getRelation(), edge.getWeight(), edge.isExtra());
      retList.add(newEdge);
    }
    return retList;
  }

  /**
   * Given a set of vertices from the same graph, returns the set of all edges between these
   * vertices.
   * This includes every edge into or out of any of the vertices, even if
   * the other end of the edge is not one of the vertices.
   *
   * @param vertices the vertices
   * @param sg the graph containing the vertices
   * @return the incoming and outgoing edges of the vertices
   */
  public static Set<SemanticGraphEdge> allEdgesInSet(Iterable<IndexedWord> vertices, SemanticGraph sg) {
    Set<SemanticGraphEdge> edges = Generics.newHashSet();
    for (IndexedWord v1 : vertices) {
      for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(v1)) {
        edges.add(edge);
      }
      for (SemanticGraphEdge edge : sg.incomingEdgeIterable(v1)) {
        edges.add(edge);
      }
    }
    return edges;
  }

  /**
   * Given two iterable sequences of edges, returns a pair containing the set of
   * edges in the first graph not in the second, and edges in the second not in the first.
   * Edge equality is determined using an object that implements ISemanticGraphEdgeEql.
   *
   * @param edges1 the edges of the first graph
   * @param edges2 the edges of the second graph
   * @param sg1 the first graph
   * @param sg2 the second graph
   * @param compareObj the test for whether two edges are equal
   * @return the edges of edges1 which matched an edge of edges2, and the
   *   edges of each which did not match
   */
  public static EdgeDiffResult diffEdges(Collection<SemanticGraphEdge> edges1, Collection<SemanticGraphEdge> edges2,
      SemanticGraph sg1, SemanticGraph sg2,
      ISemanticGraphEdgeEql compareObj) {
    Set<SemanticGraphEdge> remainingEdges1 = Generics.newHashSet();
    Set<SemanticGraphEdge> remainingEdges2 = Generics.newHashSet();
    Set<SemanticGraphEdge> sameEdges = Generics.newHashSet();


    ArrayList<SemanticGraphEdge> edges2Cache = new ArrayList<>(edges2);
    edge1Loop:
      for (SemanticGraphEdge edge1 : edges1) {
        for (SemanticGraphEdge edge2 : edges2Cache) {
          if (compareObj.equals(edge1, edge2, sg1, sg2)) {
            sameEdges.add(edge1);
            edges2Cache.remove(edge2);
            continue edge1Loop;
          }
        }
        remainingEdges1.add(edge1);
      }

    ArrayList<SemanticGraphEdge> edges1Cache = new ArrayList<>(edges1);
    edge2Loop:
      for (SemanticGraphEdge edge2 : edges2) {
        for (SemanticGraphEdge edge1 : edges1) {
          if (compareObj.equals(edge1, edge2, sg1, sg2)) {
            edges1Cache.remove(edge1);
            continue edge2Loop;
          }
        }
        remainingEdges2.add(edge2);
      }

    return new EdgeDiffResult(sameEdges, remainingEdges1, remainingEdges2);
  }

  /**
   * The result of {@link #diffEdges}: the edges which matched, and the edges of
   * each collection which did not.
   */
  public static class EdgeDiffResult {
    Set<SemanticGraphEdge> sameEdges;
    Set<SemanticGraphEdge> remaining1;
    Set<SemanticGraphEdge> remaining2;

    /**
     * Creates a result from the given sets, which are used directly.
     *
     * @param sameEdges the edges of the first collection which matched an edge of the second
     * @param remaining1 the edges of the first collection with no match
     * @param remaining2 the edges of the second collection with no match
     */
    public EdgeDiffResult(Set<SemanticGraphEdge> sameEdges,
        Set<SemanticGraphEdge> remaining1,
        Set<SemanticGraphEdge> remaining2) {
      this.sameEdges = sameEdges;
      this.remaining1 = remaining1;
      this.remaining2 = remaining2;
    }

    /**
     * The edges of the first collection with no match; this is the internal set, not a copy.
     *
     * @return the unmatched edges of the first collection
     */
    public Set<SemanticGraphEdge> getRemaining1() {
      return remaining1;
    }

    /**
     * The edges of the second collection with no match; this is the internal set, not a copy.
     *
     * @return the unmatched edges of the second collection
     */
    public Set<SemanticGraphEdge> getRemaining2() {
      return remaining2;
    }

    /**
     * The edges of the first collection which matched; this is the internal set, not a copy.
     *
     * @return the matched edges of the first collection
     */
    public Set<SemanticGraphEdge> getSameEdges() {
      return sameEdges;
    }
  }


  /**
   * Pretty printers
   * <br>
   * Prints each edge on its own line, indented with a tab, as {@code reln(gov, dep)}.
   *
   * @param edges the edges to print
   * @return the edges, one per line
   */
  public static String printEdges(Iterable<SemanticGraphEdge> edges) {
    StringWriter buf = new StringWriter();
    for (SemanticGraphEdge edge : edges) {
      buf.append("\t");
      buf.append(edge.getRelation().toString());
      buf.append("(");
      buf.append(edge.getGovernor().toString());
      buf.append(", ");
      buf.append(edge.getDependent().toString());
      buf.append(")\n");
    }
    return buf.toString();
  }

  /**
   * Options for {@link #printVertices(SemanticGraph, PrintVerticeParams)}.
   */
  public static class PrintVerticeParams {
    /** Creates the default options: index and word, wrapping at 8. */
    public PrintVerticeParams() { }

    /** Whether to print the word */
    public boolean showWord = true;
    /** Whether to print the index */
    public boolean showIndex = true;
    /** Whether to print the sentence index */
    public boolean showSentIndex = false;
    /** Whether to print the tag */
    public boolean showPOS = false;
    /** A newline and tab are printed before every vertex whose position, counting from 1, is a multiple of this */
    public int wrapAt = 8;
  }

  /**
   * Prints the vertices of the graph in sorted order, using the default {@link PrintVerticeParams}.
   *
   * @param sg the graph to print
   * @return the vertices, as described in {@link #printVertices(SemanticGraph, PrintVerticeParams)}
   */
  public static String printVertices(SemanticGraph sg) {
    return printVertices(sg, new PrintVerticeParams());
  }

  /**
   * Prints the vertices of the graph in sorted order, separated by spaces.
   * Each vertex is printed as {@code index:sSentIndex/tag/word}, with each
   * part included only if requested in params.
   *
   * @param sg the graph to print
   * @param params which parts of each vertex to print, and where to wrap lines
   * @return the vertices as a String
   */
  public static String printVertices(SemanticGraph sg, PrintVerticeParams params) {
    StringWriter buf = new StringWriter();
    int count = 0;
    for (IndexedWord word : sg.vertexListSorted()) {
      count++;
      if (count % params.wrapAt == 0) { buf.write("\n\t"); }
      if (params.showIndex) {
        buf.write(String.valueOf(word.index()));
        buf.write(":");
      }
      if (params.showSentIndex) {
        buf.write("s");
        buf.write(String.valueOf(word.sentIndex()));
        buf.write("/");
      }
      if (params.showPOS) {
        buf.write(word.tag());
        buf.write("/");
      }

      if (params.showWord) {
        buf.write(word.word());
      }

      buf.write(" ");
    }
    return buf.toString();
  }


  /**
   * Given a SemanticGraph, creates a SemgrexPattern string based off of this graph.
   * NOTE: the word() value of the vertice is the name to reference
   * NOTE: currently presumes there is only one root in this graph.
   * TODO: see if Semgrex can allow multiroot patterns
   * @param sg SemanticGraph to base this pattern on.
   * @param matchTag whether the node descriptions include the tag
   * @param matchWord whether the node descriptions include the word
   * @param nodeNameMap the names to give the nodes in the pattern; if null, the sanitized word is used
   * @return the Semgrex pattern
   */
  public static String semgrexFromGraph(SemanticGraph sg, boolean matchTag, boolean matchWord,
      Map<IndexedWord, String> nodeNameMap) {
    return semgrexFromGraph(sg, null, matchTag, matchWord, nodeNameMap);
  }

  /**
   * Given a SemanticGraph, creates a SemgrexPattern string based off of this graph,
   * starting from its first root.
   * If useWord is set, each node is described as {@code {word: /WORD/}}, with the word quoted as a regex.
   * Note that useTag currently replaces that description with {@code tag: TAG}
   * rather than adding to it, which does not produce a valid node description.
   *
   * @param sg SemanticGraph to base this pattern on
   * @param wildcardNodes nodes to describe as {@code {}}, matching any node; may be null
   * @param useTag whether the node descriptions include the tag
   * @param useWord whether the node descriptions include the word
   * @param nodeNameMap the names to give the nodes in the pattern; if null, the sanitized word is used
   * @return the Semgrex pattern
   */
  public static String semgrexFromGraph(SemanticGraph sg, Collection<IndexedWord> wildcardNodes,
                                        boolean useTag, boolean useWord, Map<IndexedWord, String> nodeNameMap) {
    Function<IndexedWord, String> transformNode = o ->{
      String str = "";
      if(useWord)
        str = "{word: /" + Pattern.quote(o.word()) + "/";
      if(useTag){
        if(!str.isEmpty())
          str += "; ";
        str = "tag: " + o.tag();
      }
      if(!str.isEmpty())
        str += "}";
      return str;
    };

      return semgrexFromGraph(sg, wildcardNodes, nodeNameMap, transformNode);
  }

  /**
   * wordTransformation is a function that converts a vertex (IndexedWord) to the value.
   * For an example, see {@code semgrexFromGraph}
   * function implementations (if useWord and useTag is true, the value is "{word: vertex.word; tag: vertex.tag}").
   * The pattern starts from the first root of sg, and the edges are named E1, E2, etc.
   *
   * @param sg SemanticGraph to base this pattern on
   * @param wildcardNodes nodes to describe as {@code {}}, matching any node; may be null
   * @param nodeNameMap the names to give the nodes in the pattern; if null, the sanitized word is used
   * @param wordTransformation converts a node to its description in the pattern
   * @return the Semgrex pattern
   */
  public static String semgrexFromGraph(SemanticGraph sg, Collection<IndexedWord> wildcardNodes,
     Map<IndexedWord, String> nodeNameMap, Function<IndexedWord, String> wordTransformation) {
    IndexedWord patternRoot = sg.getFirstRoot();
    StringWriter buf = new StringWriter();
    Set<IndexedWord> tabu = Generics.newHashSet();
    Set<SemanticGraphEdge> seenEdges = Generics.newHashSet();

    buf.append(semgrexFromGraphHelper(patternRoot, sg, tabu, seenEdges, true, true, wildcardNodes,
      nodeNameMap, false, wordTransformation));

    String patternString = buf.toString();
    return patternString;
  }


  /**
   * Given a set of edges that form a rooted and connected graph, returns a Semgrex pattern
   * corresponding to it.
   *
   * @param edges the edges of the graph
   * @param matchTag whether the node descriptions include the tag
   * @param matchWord whether the node descriptions include the word
   * @param nodeNameMap the names to give the nodes in the pattern; if null, the sanitized word is used
   * @return the Semgrex pattern, as from {@link #semgrexFromGraph(SemanticGraph, boolean, boolean, Map)}
   */
  public static String semgrexFromGraph(Iterable<SemanticGraphEdge> edges, boolean matchTag,
      boolean matchWord, Map<IndexedWord, String> nodeNameMap) {
    SemanticGraph sg = SemanticGraphFactory.makeFromEdges(edges);
    return semgrexFromGraph(sg, matchTag, matchWord, nodeNameMap);
  }

  /**
   * Recursive call to generate the Semgrex pattern based off of this SemanticGraph.
   * nodeValuesTranformation is a function that converts a vertex (IndexedWord) to the value. For an example, see {@code semgrexFromGraph}
   * function implementations.
   * <br>
   * A node which is already in tabu is written as {@code {tag:TAG}}, followed by
   * its unsanitized word as the name if useWordAsLabel is set, and is not expanded.
   *
   * @param vertice the node to describe
   * @param sg the graph containing the node
   * @param tabu the nodes already described; vertice is added to it
   * @param seenEdges the edges already described; the outgoing edges of vertice are added to it
   * @param useWordAsLabel whether to name nodes by their word when nodeNameMap is null
   * @param nameEdges whether to name each edge E followed by the number of edges seen so far
   * @param wildcardNodes nodes to describe as {@code {}}, matching any node; may be null
   * @param nodeNameMap the names to give the nodes in the pattern; may be null
   * @param orderedNodes whether to visit the outgoing edges sorted by relation name
   * @param nodeValuesTransformation converts a node to its description in the pattern
   * @return the pattern for vertice and the part of the graph below it
   */
  protected static String semgrexFromGraphHelper(IndexedWord vertice, SemanticGraph sg,
      Set<IndexedWord> tabu, Set<SemanticGraphEdge> seenEdges, boolean useWordAsLabel, boolean nameEdges, Collection<IndexedWord> wildcardNodes,
      Map<IndexedWord, String> nodeNameMap, boolean orderedNodes, Function<IndexedWord, String> nodeValuesTransformation) {
    StringWriter buf = new StringWriter();

    // If the node is a wildcarded one, treat it as a {}, meaning any match.  Currently these will not
    // be labeled, but this may change later.
    if (wildcardNodes != null && wildcardNodes.contains(vertice)) {
      buf.append("{}");
    } else {

      String vertexStr = nodeValuesTransformation.apply(vertice);
      if(vertexStr != null && !vertexStr.isEmpty()){
        buf.append(vertexStr);
      }
//      buf.append("{");
//      int i = 0;
//      for(String corekey: useNodeCoreAnnotations){
//        AnnotationLookup.KeyLookup lookup = AnnotationLookup.getCoreKey(corekey);
//        assert lookup != null : "Invalid key " + corekey;
//        if(i > 0)
//          buf.append("; ");
//        String value = vertice.containsKey(lookup.coreKey) ? vertice.get(lookup.coreKey).toString() : "null";
//        buf.append(corekey+":"+nodeValuesTransformation.apply(value));
//        i++;
//      }
//      if (useTag) {
//
//        buf.append("tag:"); buf.append(vertice.tag());
//        if (useWord)
//          buf.append(";");
//      }
//      if (useWord) {
//        buf.append("word:"); buf.append(wordTransformation.apply(vertice.word()));
//      }
//      buf.append("}");
    }
    if (nodeNameMap != null) {
      buf.append("=");
      buf.append(nodeNameMap.get(vertice));
      buf.append(" ");
    } else if (useWordAsLabel) {
      buf.append("=");
      buf.append(sanitizeForSemgrexName(vertice.word()));
      buf.append(" ");
    }

    tabu.add(vertice);

    Iterable<SemanticGraphEdge> edgeIter; // = null;
    if(!orderedNodes){
      edgeIter = sg.outgoingEdgeIterable(vertice);
    } else{
      edgeIter = CollectionUtils.sorted(sg.outgoingEdgeList(vertice), (arg0, arg1) ->
        (arg0.getRelation().toString().compareTo(arg1.getRelation().toString())));
    }


    // For each edge, record the edge, but do not traverse to the vertice if it is already in the
    // tabu list.  If it already is, we emit the edge and the target vertice, as
    // we will not be continuing in that vertex, but we wish to record the relation.
    // If we will proceed down that node, add parens if it will continue recursing down.
    for (SemanticGraphEdge edge : edgeIter) {
      seenEdges.add(edge);
      IndexedWord tgtVert = edge.getDependent();
      boolean applyParens =
        sg.outDegree(tgtVert) > 0 && !tabu.contains(tgtVert);
      buf.append(" >");
      buf.append(edge.getRelation().toString());
      if (nameEdges) {
        buf.append("=E");
        buf.write(String.valueOf(seenEdges.size()));
      }
      buf.append(" ");
      if (applyParens)
        buf.append("(");
      if (tabu.contains(tgtVert)) {
        buf.append("{tag:"); buf.append(tgtVert.tag()); buf.append("}");
        if (useWordAsLabel) {
          buf.append("=");
          buf.append(tgtVert.word());
          buf.append(" ");
        }
      } else {
        buf.append(semgrexFromGraphHelper(tgtVert, sg, tabu, seenEdges, useWordAsLabel, nameEdges,
            wildcardNodes, nodeNameMap, orderedNodes, nodeValuesTransformation));
        if (applyParens)
          buf.append(")");
      }
    }
    return buf.toString();
  }

  /** Same as semgrexFromGraph except the node traversal is ordered by sorting
   * the outgoing edges of each node by relation name
   *
   * @param sg SemanticGraph to base this pattern on
   * @param wildcardNodes nodes to describe as {@code {}}, matching any node; may be null
   * @param nodeNameMap the names to give the nodes in the pattern; if null, the sanitized word is used
   * @param wordTransformation converts a node to its description in the pattern
   * @return the Semgrex pattern
   */
  public static String semgrexFromGraphOrderedNodes(SemanticGraph sg, Collection<IndexedWord> wildcardNodes,
            Map<IndexedWord, String> nodeNameMap, Function<IndexedWord, String> wordTransformation) {
    IndexedWord patternRoot = sg.getFirstRoot();
    StringWriter buf = new StringWriter();
    Set<IndexedWord> tabu = Generics.newHashSet();
    Set<SemanticGraphEdge> seenEdges = Generics.newHashSet();

    buf.append(semgrexFromGraphHelper(patternRoot, sg, tabu, seenEdges, true, true, wildcardNodes,
      nodeNameMap, true, wordTransformation));

    String patternString = buf.toString();
    return patternString;
  }



  /**
   * Sanitizes the given string into a Semgrex friendly name
   * by replacing punctuation such as . , / ? with names such as _DOT_
   *
   * @param text the String to sanitize
   * @return the sanitized String
   */
  public static String sanitizeForSemgrexName(String text) {
    text = text.replaceAll("\\.", "_DOT_");
    text = text.replaceAll(",", "_COMMA_");
    text = text.replaceAll("\\\\", "_BSLASH_");
    text = text.replaceAll("/", "_BSLASH_");
    text = text.replaceAll("\\?", "_QUES_");
    text = text.replaceAll("!", "_BANG_");
    text = text.replaceAll("\\$", "_DOL_");
    text = text.replaceAll("&", "_AMP_");
    text = text.replaceAll(":", "_COL_");
    text = text.replaceAll(";", "_SCOL_");
    text = text.replaceAll("#", "_PND_");
    text = text.replaceAll("@", "_AND_");
    text = text.replaceAll("%", "_PER_");
    text = text.replaceAll("\\(","_LRB_");
    text = text.replaceAll("\\)", "_RRB_");
    return text;
  }


  /**
   * Given a {@code SemanticGraph}, sets the lemmas on its label
   * objects based on their word and tag.
   * This uses the English {@link Morphology} lemmatizer.
   *
   * @param sg the graph whose nodes are lemmatized in place
   */
  public static void lemmatize(SemanticGraph sg) {
    for (IndexedWord node : sg.vertexSet()) {
      node.setLemma(Morphology.lemmaStatic(node.word(), node.tag()));
    }
  }

  /**
   * GIven a graph, returns a new graph with the the new sentence index enforced.
   * NOTE: new vertices are inserted.
   * TODO: is this ok?  rewrite this?
   *
   * @param sg the graph to copy; it is not changed
   * @param newSentIndex the sentence index for the new vertices
   * @return a new graph with copies of the vertices, all with the new sentence index
   */
  public static SemanticGraph setSentIndex(SemanticGraph sg, int newSentIndex) {
    SemanticGraph newGraph = new SemanticGraph(sg);
    List<IndexedWord> prevRoots = new ArrayList<>(newGraph.getRoots());
    List<IndexedWord> newRoots = new ArrayList<>();
    // TODO: we are using vertexListSorted here because we're changing
    // vertices while iterating.  Perhaps there is a better way to do it.
    for (IndexedWord node : newGraph.vertexListSorted()) {
      IndexedWord newWord = new IndexedWord(node);
      newWord.setSentIndex(newSentIndex);
      SemanticGraphUtils.replaceNode(newWord, node, newGraph);
      if (prevRoots.contains(node))
        newRoots.add(newWord);
    }
    newGraph.setRoots(newRoots);
    return newGraph;
  }

  //-----------------------------------------------------------------------------------------------
  //   Graph redundancy checks
  //-----------------------------------------------------------------------------------------------


  /**
   * Removes duplicate graphs from the set, using the string form of the graph
   * as the key (obviating issues with object equality).
   *
   * @param graphs the graphs to filter
   * @return one graph for each distinct string form, in no particular order
   */
  public static Collection<SemanticGraph> removeDuplicates(Collection<SemanticGraph> graphs) {
    Map<String, SemanticGraph> map = Generics.newHashMap();
    for (SemanticGraph sg : graphs) {
      String keyVal = sg.toString().intern();
      map.put(keyVal, sg);
    }
    return map.values();
  }

  /**
   * Given the set of graphs to remove duplicates from, also removes those on the tabu graphs
   * (and does not include them in the return set).
   *
   * @param graphs the graphs to filter
   * @param tabuGraphs graphs whose string forms are excluded from the result
   * @return one graph for each distinct string form not in tabuGraphs, in no particular order
   */
  public static Collection<SemanticGraph> removeDuplicates(Collection<SemanticGraph> graphs,
      Collection<SemanticGraph> tabuGraphs) {
    Map<String, SemanticGraph> tabuMap = Generics.newHashMap();
    for (SemanticGraph tabuSg : tabuGraphs) {
      String keyVal = tabuSg.toString().intern();
      tabuMap.put(keyVal, tabuSg);
    }
    Map<String, SemanticGraph> map = Generics.newHashMap();
    for (SemanticGraph sg : graphs) {
      String keyVal = sg.toString().intern();
      if (tabuMap.containsKey(keyVal))
        continue;
      map.put(keyVal, sg);
    }
    return map.values();
  }

  /**
   * Removes duplicate graphs from the set, and also those with the same string form as tabuGraph.
   *
   * @param graphs the graphs to filter
   * @param tabuGraph a graph whose string form is excluded from the result
   * @return one graph for each distinct string form other than that of tabuGraph, in no particular order
   */
  public static Collection<SemanticGraph> removeDuplicates(Collection<SemanticGraph> graphs,
      SemanticGraph tabuGraph) {
    Collection<SemanticGraph> tabuSet = Generics.newHashSet();
    tabuSet.add(tabuGraph);
    return removeDuplicates(graphs, tabuSet);
  }

  // -----------------------------------------------------------------------------------------------
  // Tree matching code
  // -----------------------------------------------------------------------------------------------

  /**
   * Given a CFG Tree parse, and the equivalent SemanticGraph derived from that Tree, generates a mapping
   * from each of the tree terminals to the best-guess SemanticGraph node(s).
   * This is performed using lexical matching, finding the nth match.
   * NOTE: not all tree nodes may match a Semgraph node, esp. for tokens removed in a collapsed Semgraph,
   * such as prepositions.
   * Each non-terminal is mapped to the node closest to the root of sg among the
   * nodes its leaves map to.
   *
   * @param tree the parse tree
   * @param sg the graph derived from the tree
   * @return a map from positions in the tree to the matching graph nodes
   */
  public static Map<PositionedTree, IndexedWord> mapTreeToSg(Tree tree, SemanticGraph sg) {
    // In order to keep track of positions, we store lists, in order encountered, of lex terms.
    // e.g. lexToTreeNode.get("the").get(2) should point to the same word as lexToSemNode.get("the").get(2)
    // Because IndexedWords may be collapsed together "A B" -> "A_B", we check the value of current(), and
    // split on whitespace if present.
    MapList<String, TreeNodeProxy> lexToTreeNode = new MapList<>();
    MapList<String, IndexedWordProxy> lexToSemNode = new MapList<>();

    for (Tree child : tree.getLeaves()) {
      List<TreeNodeProxy> leafProxies = TreeNodeProxy.create(child, tree);
      for (TreeNodeProxy proxy : leafProxies)
        lexToTreeNode.add(proxy.lex, proxy);
    }

    Map<IndexedWord, Integer> depthMap = Generics.newHashMap();
    for (IndexedWord node : sg.vertexSet()) {
      List<IndexedWord> path = sg.getPathToRoot(node);
      if (path != null)
        depthMap.put(node, path.size());
      else
        depthMap.put(node, 99999); // Use an arbitrarily deep depth value, to trick it into never being used.
      List<IndexedWordProxy> nodeProxies = IndexedWordProxy.create(node);
      for (IndexedWordProxy proxy : nodeProxies)
        lexToSemNode.add(proxy.lex, proxy);
    }

    // Now the map-lists (string->position encountered indices) are populated,
    // simply go through, finding matches.
    // NOTE: we use TreeNodeProxy instead of keying off of Tree, as
    // hash codes for Tree nodes do not consider position of the tree
    // within a tree: two subtrees with the same layout and child
    // labels will be equal.
    Map<PositionedTree, IndexedWord> map = Generics.newHashMap();
    for (String lex : lexToTreeNode.keySet()) {
      for (int i=0;i<lexToTreeNode.size(lex) && i<lexToSemNode.size(lex);i++) {
        map.put(new PositionedTree(lexToTreeNode.get(lex, i).treeNode, tree), lexToSemNode.get(lex,i).node);
      }
    }

    // Now that a terminals to terminals map has been generated, account for the
    // tree non-terminals.
    for (Tree nonTerm : tree) {
      if (!nonTerm.isLeaf()) {
        IndexedWord bestNode = null;
        int bestScore = 99999;
        for (Tree curr : nonTerm) {
          IndexedWord equivNode = map.get(new PositionedTree(curr, tree));
          if ((equivNode == null) || !depthMap.containsKey(equivNode)) continue;
          int currScore = depthMap.get(equivNode);
          if (currScore < bestScore) {
            bestScore = currScore;
            bestNode = equivNode;
          }
        }
        if (bestNode != null) {
          map.put(new PositionedTree(nonTerm, tree), bestNode);
        }
      }
    }

    return map;
  }

  /**
   * Private helper class for {@code mapTreeToSg}.   Acts to
   * map between a Tree node and a lexical value.
   * @author Eric Yeh
   *
   */
  private static class TreeNodeProxy {
    Tree treeNode;
    String lex;
    Tree root;

    public String toString() {
      return lex + " -> " + treeNode + ", #=" + treeNode.nodeNumber(root);
    }

    private TreeNodeProxy(Tree intree, String lex, Tree root) {
      this.treeNode = intree;
      this.lex = lex;
      this.root = root;
    }

    public static List<TreeNodeProxy> create(Tree intree, Tree root) {
      List<TreeNodeProxy> ret = new ArrayList<>();
      if (intree.isLeaf()) {
        ret.add(new TreeNodeProxy(intree, intree.label().value(), root));
      } else
        for (LabeledWord lword : intree.labeledYield()) {
          ret.add(new TreeNodeProxy(intree, lword.word(), root));
        }

      return ret;
    }
  }

  /**
   * This is used to uniquely index trees within a
   * Tree, maintaining the position of this subtree
   * within the context of the root.
   * @author Eric Yeh
   *
   */
  public static class PositionedTree {
    Tree tree;
    Tree root;
    int nodeNumber;

    public String toString() {
      return tree+"."+nodeNumber;
    }

    /**
     * Creates a PositionedTree for the given subtree.
     *
     * @param tree the subtree
     * @param root the tree containing it
     */
    public PositionedTree(Tree tree, Tree root) {
      this.tree = tree;
      this.root = root;
      this.nodeNumber = tree.nodeNumber(root);
    }

    public boolean equals(Object obj) {
      if (obj instanceof PositionedTree) {
        PositionedTree tgt = (PositionedTree) obj;
        return tree.equals(tgt.tree) && root.equals(tgt.root) && tgt.nodeNumber == nodeNumber;
      }
      return false;
    }

    /**
     * TODO: verify this is correct
     */
    @Override
    public int hashCode() {
      int hc = tree.hashCode() ^ (root.hashCode() << 8);
      hc ^= (2 ^ nodeNumber);
      return hc;
    }
  }

  /**
   * Private helper class for {@code mapTreeToSg}.  Acts to
   * map between an IndexedWord (in a SemanticGraph) and a lexical value.
   * @author lumberjack
   *
   */
  private static final class IndexedWordProxy {
    IndexedWord node;
    String lex;

    public String toString() {
      return lex+" -> "+node.word()+":"+node.sentIndex()+"."+node.index();
    }

    private IndexedWordProxy(IndexedWord node, String lex) {
      this.node = node; this.lex = lex;
    }

    /**
     * Generates a set of IndexedWordProxy objects.  If the current() field is present, splits the tokens by
     * a space, and for each, creates a new IndexedWordProxy, in order encountered, referencing this current
     * node, but using the lexical value of the current split token.  Otherwise just use the value of word().
     * This is used to retain attribution to the originating node.
     */
    public static List<IndexedWordProxy> create(IndexedWord node) {
      List<IndexedWordProxy> ret = new ArrayList<>();
      if (node.originalText().length() > 0) {
        for (String token : node.originalText().split(" ")) {
          ret.add(new IndexedWordProxy(node, token));
        }
      } else {
        ret.add(new IndexedWordProxy(node, node.word()));
      }
      return ret;
    }
  }


  /**
   *
   * Checks whether a given SemanticGraph is a strict surface syntax tree.
   *
   * @param sg the graph to check
   * @return true if the graph has exactly one root, every node is reachable
   *   from the root, and a breadth first search from the root never finds an
   *   edge to a node it has already dequeued or to a copy node
   */
  public static boolean isTree(SemanticGraph sg) {

    if (sg.getRoots().size() != 1) {
      return false;
    }

    IndexedWord root = sg.getFirstRoot();
    Set<IndexedWord> visitedNodes = Generics.newHashSet();
    Queue<IndexedWord> queue = Generics.newLinkedList();

    queue.add(root);
    while (!queue.isEmpty()) {
      IndexedWord current = queue.remove();
      visitedNodes.add(current);
      for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(current)) {
        IndexedWord dep = edge.getDependent();
        if (visitedNodes.contains(dep)) {
          return false;
        }
        if (dep.copyCount() > 0) {
          return false;
        }
        queue.add(dep);
      }
    }

    return visitedNodes.size() == sg.size();
  }

  /**
   * Returns the largest index of any node in the graph.
   *
   * @param sg the graph
   * @return the largest index, or {@link Integer#MIN_VALUE} if the graph is empty
   */
  public static int maxIndex(SemanticGraph sg) {
    int index = Integer.MIN_VALUE;
    for (IndexedWord node : sg.vertexSet()) {
      if (node.index() > index) {
        index = node.index();
      }
    }
    return index;
  }

  /**
   * Returns the smallest index of any node in the graph.
   *
   * @param sg the graph
   * @return the smallest index, or {@link Integer#MAX_VALUE} if the graph is empty
   */
  public static int minIndex(SemanticGraph sg) {
    int index = Integer.MAX_VALUE;
    for (IndexedWord node : sg.vertexSet()) {
      if (node.index() < index) {
        index = node.index();
      }
    }
    return index;
  }
}
