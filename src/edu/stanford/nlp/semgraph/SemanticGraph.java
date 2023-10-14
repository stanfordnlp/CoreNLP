package edu.stanford.nlp.semgraph;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.graph.DirectedMultiGraph;
import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringParsingTask;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.TwoDimensionalMap;
import edu.stanford.nlp.util.logging.Redwood;

import static edu.stanford.nlp.trees.GrammaticalRelation.ROOT;

// todo [cdm 2013]: The treatment of roots in this class should probably be redone.
// todo [cdm 2013]: Probably we should put fake root node in graph and arc(s) from it.
// todo [cdm 2013]: At any rate, printing methods should print the root

/**
 * Represents a semantic graph of a sentence or document, with IndexedWord
 * objects for nodes.
 *
 * Notes:
 *
 * The root is not at present represented as a vertex in the graph.
 * At present you need to get a root/roots
 * from the separate roots variable and to know about it.
 * This should maybe be changed, because otherwise, doing things like
 * simply getting the set of nodes or edges from the graph doesn't give
 * you root nodes or edges.
 *
 * Given the kinds of representations that we normally use with
 * typedDependenciesCollapsed, there can be (small) cycles in a
 * SemanticGraph, and these cycles may involve the node that is conceptually the
 * root of the graph, so there may be no node without a parent node. You can
 * better get at the root(s) via the variable and methods provided.
 *
 * There is no mechanism for returning all edges at once (e.g., {@code edgeSet()}).
 * This is intentional.  Use {@code edgeIterable()} to iterate over the edges if necessary.
 *
 * @author Christopher Cox
 * @author Teg Grenager
 * @see SemanticGraphEdge
 * @see IndexedWord
 */
public class SemanticGraph implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemanticGraph.class);

  public static final boolean addSRLArcs = false;

  private static final SemanticGraphFormatter formatter = new SemanticGraphFormatter();

  /**
   * The distinguished root vertices, if known.
   */
  private final Set<IndexedWord> roots;

  private final DirectedMultiGraph<IndexedWord, SemanticGraphEdge> graph;

  private static final MapFactory<IndexedWord, Map<IndexedWord, List<SemanticGraphEdge>>> outerMapFactory = MapFactory.linkedHashMapFactory();
  private static final MapFactory<IndexedWord, List<SemanticGraphEdge>> innerMapFactory = MapFactory.linkedHashMapFactory();
  private static final MapFactory<IndexedWord, IndexedWord> wordMapFactory = MapFactory.linkedHashMapFactory();

  private LinkedList<String> comments = new LinkedList<>();

  public int edgeCount() {
    return graph.getNumEdges();
  }

  public int outDegree(IndexedWord vertex) {
    return graph.getOutDegree(vertex);
  }

  public int inDegree(IndexedWord vertex) {
    return graph.getInDegree(vertex);
  }

  public List<SemanticGraphEdge> getAllEdges(IndexedWord gov,
                                             IndexedWord dep) {
    return graph.getEdges(gov, dep);
  }

  // TODO: this is a bad method to use because there can be multiple
  // edges.  All users of this method should be switched to iterating
  // over getAllEdges.  This has already been done for all uses
  // outside RTE.
  public SemanticGraphEdge getEdge(IndexedWord gov, IndexedWord dep) {
    List<SemanticGraphEdge> edges = graph.getEdges(gov, dep);
    if (edges == null || edges.isEmpty())
      return null;
    return edges.get(0);
  }

  public void addVertex(IndexedWord vertex) {
    graph.addVertex(vertex);
  }

  public boolean containsVertex(IndexedWord vertex) {
    return graph.containsVertex(vertex);
  }

  public boolean containsEdge(IndexedWord source, IndexedWord target) {
    return graph.isEdge(source, target);
  }

  public boolean containsEdge(SemanticGraphEdge edge) {
    return containsEdge(edge.getSource(), edge.getTarget());
  }

  public Set<IndexedWord> vertexSet() {
    return graph.getAllVertices();
  }

  public boolean removeEdge(SemanticGraphEdge e) {
    return graph.removeEdge(e.getSource(), e.getTarget(), e);
  }

  public boolean removeVertex(IndexedWord vertex) {
    return graph.removeVertex(vertex);
  }

  public boolean updateEdge(SemanticGraphEdge edge, GrammaticalRelation reln) {
    boolean removed = removeEdge(edge);
    if (removed) {
      SemanticGraphEdge newEdge = new SemanticGraphEdge(edge.getSource(), edge.getTarget(), reln, edge.getWeight(), edge.isExtra());
      addEdge(newEdge);
    }
    return removed;
  }

  /**
   * This returns an ordered list of vertices (based upon their
   * indices in the sentence). This creates and sorts a list, so
   * prefer vertexSet unless you have a good reason to want nodes in
   * index order.
   *
   * @return Ordered list of vertices
   */
  public List<IndexedWord> vertexListSorted() {
    ArrayList<IndexedWord> vList = new ArrayList<>(vertexSet());
    Collections.sort(vList);
    return vList;
  }

  /**
   * Returns an ordered list of edges in the graph.
   * This creates and sorts a list, so prefer edgeIterable().
   *
   * @return A ordered list of edges in the graph.
   */
  public List<SemanticGraphEdge> edgeListSorted() {
    ArrayList<SemanticGraphEdge> edgeList = new ArrayList<>();
    for (SemanticGraphEdge edge : edgeIterable()) {
      edgeList.add(edge);
    }
    edgeList.sort(SemanticGraphEdge.orderByTargetComparator());
    return edgeList;
  }

  public Iterable<SemanticGraphEdge> edgeIterable() {
    return graph.edgeIterable();
  }

  public Iterator<SemanticGraphEdge> outgoingEdgeIterator(IndexedWord v) {
    return graph.outgoingEdgeIterator(v);
  }

  public Iterable<SemanticGraphEdge> outgoingEdgeIterable(IndexedWord v) {
    return graph.outgoingEdgeIterable(v);
  }

  public Iterator<SemanticGraphEdge> incomingEdgeIterator(IndexedWord v) {
    return graph.incomingEdgeIterator(v);
  }

  public Iterable<SemanticGraphEdge> incomingEdgeIterable(IndexedWord v) {
    return graph.incomingEdgeIterable(v);
  }

  public List<SemanticGraphEdge> outgoingEdgeList(IndexedWord v) {
    return CollectionUtils.toList(outgoingEdgeIterable(v));
  }

  public List<SemanticGraphEdge> incomingEdgeList(IndexedWord v) {
    return CollectionUtils.toList(incomingEdgeIterable(v));
  }

  public boolean isEmpty() {
    return graph.isEmpty();
  }

  /**
   * Searches up to 2 levels to determine how far ancestor is from child (i.e.,
   * returns 1 if "ancestor" is a parent, or 2 if ancestor is a grandparent.
   *
   * @param child
   *          candidate child
   * @param ancestor
   *          candidate ancestor
   * @return the number of generations between "child" and "ancestor" (1 is an
   *         immediate parent), or -1 if there is no relationship found.
   */
  public int isAncestor(IndexedWord child, IndexedWord ancestor) {

    Set<IndexedWord> parents = this.getParents(child);
    if (parents.contains(ancestor)) {
      return 1;
    }
    for (IndexedWord parent : parents) {
      Set<IndexedWord> grandparents = this.getParents(parent);
      if (grandparents.contains(ancestor)) {
        return 2;
      }
    }
    return -1;
  }

  /**
   * Return the maximum distance to a least common ancestor. We only search as
   * high as grandparents. We return -1 if no common parent or grandparent is
   * found.
   *
   * @return The maximum distance to a least common ancestor.
   */
  public int commonAncestor(IndexedWord v1, IndexedWord v2) {
    if (v1.equals(v2)) {
      return 0;
    }

    Set<IndexedWord> v1Parents = this.getParents(v1);
    Set<IndexedWord> v2Parents = this.getParents(v2);
    Set<IndexedWord> v1GrandParents = wordMapFactory.newSet();
    Set<IndexedWord> v2GrandParents = wordMapFactory.newSet();

    if (v1Parents.contains(v2) || v2Parents.contains(v1)) {
      return 1;
    }

    // does v1 have any parents that are v2's parents?
    for (IndexedWord v1Parent : v1Parents) {
      if (v2Parents.contains(v1Parent)) {
        return 1;
      }
      v1GrandParents.addAll(this.getParents(v1Parent));
    }
    // build v2 grandparents
    for (IndexedWord v2Parent : v2Parents) {
      v2GrandParents.addAll(this.getParentList(v2Parent));
    }
    if (v1GrandParents.contains(v2) || v2GrandParents.contains(v1)) {
      return 2;
    }
    // Are any of v1's parents a grandparent of v2?
    for (IndexedWord v2GrandParent : v2GrandParents) {
      if (v1Parents.contains(v2GrandParent)) {
        return 2;
      }
    }
    // Are any of v2's parents a grandparent of v1?
    for (IndexedWord v1GrandParent : v1GrandParents) {
      if (v2Parents.contains(v1GrandParent)) {
        return 2;
      }
    }
    for (IndexedWord v2GrandParent : v2GrandParents) {
      if (v1GrandParents.contains(v2GrandParent)) {
        return 2;
      }
    }
    return -1;
  }

  /**
   * Returns the least common ancestor. We only search as high as grandparents.
   * We return null if no common parent or grandparent is found. Any of the
   * input words can also be the answer if one is the parent or grandparent of
   * other, or if the input words are the same.
   *
   * @return The least common ancestor.
   */
  public IndexedWord getCommonAncestor(IndexedWord v1, IndexedWord v2) {
    if (v1.equals(v2)) {
      return v1;
    }

    if (this.isAncestor(v1, v2) >= 1) {
      return v2;
    }

    if (this.isAncestor(v2, v1) >= 1) {
      return v1;
    }

    Set<IndexedWord> v1Parents = this.getParents(v1);
    Set<IndexedWord> v2Parents = this.getParents(v2);
    Set<IndexedWord> v1GrandParents = wordMapFactory.newSet();
    Set<IndexedWord> v2GrandParents = wordMapFactory.newSet();
    // does v1 have any parents that are v2's parents?

    for (IndexedWord v1Parent : v1Parents) {
      if (v2Parents.contains(v1Parent)) {
        return v1Parent;
      }
      v1GrandParents.addAll(this.getParents(v1Parent));
    }
    // does v1 have any grandparents that are v2's parents?
    for (IndexedWord v1GrandParent : v1GrandParents) {
      if (v2Parents.contains(v1GrandParent)) {
        return v1GrandParent;
      }
    }
    // build v2 grandparents
    for (IndexedWord v2Parent : v2Parents) {
      v2GrandParents.addAll(this.getParents(v2Parent));
    }
    // does v1 have any parents or grandparents that are v2's grandparents?
    for (IndexedWord v2GrandParent : v2GrandParents) {
      if (v1Parents.contains(v2GrandParent)) {
        return v2GrandParent;
      }
      if (v1GrandParents.contains(v2GrandParent)) {
        return v2GrandParent;
      }
    }
    return null;
  }

  // todo [cdm 2013]: Completely RTE-specific methods like this one should be used to a static class of helper methods under RTE
  // If "det" is true, the search for a child is restricted to the "determiner"
  // grammatical relation.
  public boolean matchPatternToVertex(String pattern, IndexedWord vertex, boolean det) {
    if (!containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    String pat = pattern.replaceAll("<", ",<");
    pat = pat.replaceAll(">", ",>");
    String[] nodePath = pat.split(",");
    for (String s : nodePath) {
      if (s.isEmpty()) {
        continue;
      }
      String word = s.substring(1);
      char dir = s.charAt(0);
      if (dir == '<') {
        // look for a matching parent
        boolean match = false;
        for (IndexedWord parent : getParents(vertex)) {
          String lemma = parent.get(CoreAnnotations.LemmaAnnotation.class);
          if (lemma.equals(word)) {
            match = true;
            break;
          }
        }
        if (!match) {
          return false;
        }
      } else if (dir == '>') {
        if (det) {
          // look for a matching child with "det" relation
          Set<IndexedWord> children = wordMapFactory.newSet();
          children.addAll(getChildrenWithReln(vertex, EnglishGrammaticalRelations.DETERMINER));
          children.addAll(getChildrenWithReln(vertex, EnglishGrammaticalRelations.PREDETERMINER));
          boolean match = false;
          for (IndexedWord child : children) {
            String lemma = child.get(CoreAnnotations.LemmaAnnotation.class);
            if (lemma.isEmpty()) {
              lemma = child.word().toLowerCase();
            }
            if (lemma.equals(word)) {
              match = true;
              break;
            }
          }
          if (!match) {
            return false;
          }
        } else {// take any relation, except "det"
          List<Pair<GrammaticalRelation, IndexedWord>> children = childPairs(vertex);
          boolean match = false;
          for (Pair<GrammaticalRelation, IndexedWord> pair : children) {
            if (pair.first().toString().equals("det"))
              continue;
            IndexedWord child = pair.second();
            String lemma = child.get(CoreAnnotations.LemmaAnnotation.class);
            if (lemma.isEmpty()) {
              lemma = child.word().toLowerCase();
            }
            if (lemma.equals(word)) {
              match = true;
              break;
            }
          }
          if (!match) {
            return false;
          }
        }
      } else {
        throw new RuntimeException("Warning: bad pattern \"%s\"\n" + pattern);
      }
    }
    return true;
  }

  // todo [cdm 2013]: Completely RTE-specific methods like this one should be used to a static class of helper methods under RTE
  public boolean matchPatternToVertex(String pattern, IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    String pat = pattern.replaceAll("<", ",<");
    pat = pat.replaceAll(">", ",>");
    String[] nodePath = pat.split(",");
    for (String s : nodePath) {
      if (s.isEmpty()) {
        continue;
      }
      String word = s.substring(1);
      char dir = s.charAt(0);
      if (dir == '<') {
        // look for a matching parent
        boolean match = false;
        for (IndexedWord parent : getParents(vertex)) {
          String lemma = parent.get(CoreAnnotations.LemmaAnnotation.class);
          if (lemma.equals(word)) {
            match = true;
            break;
          }
        }
        if (!match) {
          return false;
        }
      } else if (dir == '>') {
        // look for a matching child
        boolean match = false;
        for (IndexedWord child : getChildren(vertex)) {
          String lemma = child.get(CoreAnnotations.LemmaAnnotation.class);
          if (lemma == null || lemma.isEmpty()) {
            lemma = child.word().toLowerCase();
          }
          if (lemma.equals(word)) {
            match = true;
            break;
          }
        }
        if (!match) {
          return false;
        }
      } else {
        throw new RuntimeException("Warning: bad pattern \"%s\"\n" + pattern);
      }
    }
    return true;
  }

  public List<IndexedWord> getChildList(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    List<IndexedWord> result = new ArrayList<>(getChildren(vertex));
    Collections.sort(result);
    return result;
  }

  public Set<IndexedWord> getChildren(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    return graph.getChildren(vertex);
  }

  public boolean hasChildren(IndexedWord vertex) {
    return outgoingEdgeIterator(vertex).hasNext();
  }

  public List<SemanticGraphEdge> getIncomingEdgesSorted(IndexedWord vertex) {
    List<SemanticGraphEdge> edges = incomingEdgeList(vertex);
    Collections.sort(edges);
    return edges;
  }

  public List<SemanticGraphEdge> getOutEdgesSorted(IndexedWord vertex) {
    List<SemanticGraphEdge> edges = outgoingEdgeList(vertex);
    Collections.sort(edges);
    return edges;
  }

  public List<IndexedWord> getParentList(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    List<IndexedWord> result = new ArrayList<>(getParents(vertex));
    Collections.sort(result);
    return result;
  }

  public Set<IndexedWord> getParents(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new IllegalArgumentException();
    }
    return graph.getParents(vertex);
  }

  /**
   * Method for getting the siblings of a particular node. Siblings are the
   * other children of your parent, where parent is determined as the parent
   * returned by getParent
   *
   * @return collection of sibling nodes (does not include vertex)
   *         the collection is empty if your parent is null
   */
  public Collection<IndexedWord> getSiblings(IndexedWord vertex) {
    IndexedWord parent = this.getParent(vertex);
    if (parent != null) {
      Set<IndexedWord> result = wordMapFactory.newSet();
      result.addAll(this.getChildren(parent));
      result.remove(vertex);//remove this vertex - you're not your own sibling
      return result;
    } else {
      return Collections.emptySet();
    }
  }

  /**
   * Helper function for the public function with the same name.
   * <br>
   * Builds up the list backwards.
   */
  private List<IndexedWord> getPathToRoot(IndexedWord vertex, List<IndexedWord> used) {
    used.add(vertex);

    // TODO: Apparently the order of the nodes in the path to the root
    // makes a difference for the RTE system.  Look into this some more
    List<IndexedWord> parents = getParentList(vertex);
    // Set<IndexedWord> parents = wordMapFactory.newSet();
    // parents.addAll(getParents(vertex));
    parents.removeAll(used);

    if (roots.contains(vertex) || (parents.isEmpty())) {
      used.remove(used.size() - 1);
      if (roots.contains(vertex))
        return Generics.newArrayList();
      else
        return null; // no path found
    }

    for (IndexedWord parent : parents) {
      List<IndexedWord> path = getPathToRoot(parent, used);
      if (path != null) {
        path.add(parent);
        used.remove(used.size() - 1);
        return path;
      }
    }

    used.remove(used.size() - 1);
    return null;

  }

  /**
   * Find the path from the given node to a root. The path does not include the
   * given node. Returns an empty list if vertex is a root. Returns null if a
   * root is inaccessible (should never happen).
   */
  public List<IndexedWord> getPathToRoot(IndexedWord vertex) {
    List<IndexedWord> path = getPathToRoot(vertex, Generics.newArrayList());
    if (path != null) Collections.reverse(path);
    return path;
  }

  /**
   * Return the real syntactic parent of vertex.
   */
  public IndexedWord getParent(IndexedWord vertex) {
    List<IndexedWord> path = getPathToRoot(vertex);

    if (path != null && path.size() > 0)
      return path.get(0);
    else
      return null;
  }

  /**
   * Returns the <em>first</em> {@link edu.stanford.nlp.ling.IndexedWord
   * IndexedWord} in this {@code SemanticGraph} having the given integer index,
   * or throws {@code IllegalArgumentException} if no such node is found.
   */
  public IndexedWord getNodeByIndex(int index) throws IllegalArgumentException {
    IndexedWord node = getNodeByIndexSafe(index);
    if (node == null)
      throw new IllegalArgumentException("No SemanticGraph vertex with index " + index);
    else
      return node;
  }

  /**
   * Same as above, but returns {@code null} if the index does not exist
   * (instead of throwing an exception).
   */
  public IndexedWord getNodeByIndexSafe(int index) {
    for (IndexedWord vertex : vertexSet()) {
      if (vertex.index() == index) {
        return vertex;
      }
    }
    return null;
  }

  /**
   * Returns the <em>first</em> {@link edu.stanford.nlp.ling.IndexedWord
   * IndexedWord} in this {@code SemanticGraph} having the given integer index,
   * or throws {@code IllegalArgumentException} if no such node is found.
   */
  public IndexedWord getNodeByIndexAndCopyCount(int index, int copyCount) throws IllegalArgumentException {
    IndexedWord node = getNodeByIndexAndCopyCountSafe(index, copyCount);
    if (node == null)
      throw new IllegalArgumentException("No SemanticGraph vertex with index " + index + " and copyCount " + copyCount);
    else
      return node;
  }

  /**
   * Same as above, but returns {@code null} if the index does not exist
   * (instead of throwing an exception).
   */
  public IndexedWord getNodeByIndexAndCopyCountSafe(int index, int copyCount) {
    for (IndexedWord vertex : vertexSet()) {
      if (vertex.index() == index && vertex.copyCount() == copyCount) {
        return vertex;
      }
    }
    return null;
  }

  /**
   * Returns the <i>first</i> {@link edu.stanford.nlp.ling.IndexedWord
   * IndexedWord} in this {@code SemanticGraph} having the given word or
   * regex, or return null if no such found.
   */
  public IndexedWord getNodeByWordPattern(String pattern) {
    Pattern p = Pattern.compile(pattern);
    for (IndexedWord vertex : vertexSet()) {
      String w = vertex.word();
      if ((w == null && pattern == null) || w != null && p.matcher(w).matches()) {
        return vertex;
      }
    }
    return null;
  }

  /**
   * Returns all nodes of type {@link edu.stanford.nlp.ling.IndexedWord
   * IndexedWord} in this {@code SemanticGraph} having the given word or
   * regex, or returns empty list if no such found.
   */
  public List<IndexedWord> getAllNodesByWordPattern(String pattern) {
    Pattern p = Pattern.compile(pattern);
    List<IndexedWord> nodes = new ArrayList<>();
    for (IndexedWord vertex : vertexSet()) {
      String w = vertex.word();
      if ((w == null && pattern == null) || w != null && p.matcher(w).matches()) {
        nodes.add(vertex);
      }
    }
    return nodes;
  }

  public List<IndexedWord> getAllNodesByPartOfSpeechPattern(String pattern) {
    Pattern p = Pattern.compile(pattern);
    List<IndexedWord> nodes = new ArrayList<>();
    for (IndexedWord vertex : vertexSet()) {
      String pos = vertex.tag();
      if ((pos == null && pattern == null) || pos != null && p.matcher(pos).matches()) {
        nodes.add(vertex);
      }
    }
    return nodes;
  }

  /**
   * Returns the set of descendants governed by this node in the graph.
   *
   */
  public Set<IndexedWord> descendants(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    // Do a depth first search
    Set<IndexedWord> descendantSet = wordMapFactory.newSet();
    descendantsHelper(vertex, descendantSet);
    return descendantSet;
  }

  private void descendantsHelper(IndexedWord curr, Set<IndexedWord> descendantSet) {
    if (descendantSet.contains(curr)) {
      return;
    }
    descendantSet.add(curr);
    for (IndexedWord child : getChildren(curr)) {
      descendantsHelper(child, descendantSet);
    }
  }

  /**
   * Returns a list of pairs of a relation name and the child
   * IndexedFeatureLabel that bears that relation.
   */
  public List<Pair<GrammaticalRelation, IndexedWord>> childPairs(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    List<Pair<GrammaticalRelation, IndexedWord>> childPairs =
      Generics.newArrayList();
    for (SemanticGraphEdge e : outgoingEdgeIterable(vertex)) {
      childPairs.add(new Pair<>(e.getRelation(), e.getTarget()));
    }
    return childPairs;
  }

  /**
   * Returns a list of pairs of a relation name and the parent
   * IndexedFeatureLabel to which we bear that relation.
   */
  public List<Pair<GrammaticalRelation, IndexedWord>> parentPairs(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    List<Pair<GrammaticalRelation, IndexedWord>> parentPairs = Generics.newArrayList();
    for (SemanticGraphEdge e : incomingEdgeIterable(vertex)) {
      parentPairs.add(new Pair<>(e.getRelation(), e.getSource()));
    }
    return parentPairs;
  }

  /**
   * Returns a set of relations which this node has with its parents.
   *
   * @return The set of relations which this node has with its parents.
   */
  public Set<GrammaticalRelation> relns(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    Set<GrammaticalRelation> relns = Generics.newHashSet();
    List<Pair<GrammaticalRelation, IndexedWord>> pairs = parentPairs(vertex);
    for (Pair<GrammaticalRelation, IndexedWord> p : pairs) {
      relns.add(p.first());
    }
    return relns;
  }

  /**
   * Returns the relation that node a has with node b.
   *
   * Note: there may be multiple arcs between {@code a} and
   * {@code b}, and this method only returns one relation.
   */
  public GrammaticalRelation reln(IndexedWord a, IndexedWord b) {
    if (!containsVertex(a)) {
      throw new UnknownVertexException(a, this);
    }

    List<Pair<GrammaticalRelation, IndexedWord>> pairs = childPairs(a);
    for (Pair<GrammaticalRelation, IndexedWord> p : pairs)
      if (p.second().equals(b))
        return p.first();

    return null;
  }

  /**
   * Returns a list of relations which this node has with its children.
   */
  public Set<GrammaticalRelation> childRelns(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    Set<GrammaticalRelation> relns = Generics.newHashSet();
    List<Pair<GrammaticalRelation, IndexedWord>> pairs = childPairs(vertex);
    for (Pair<GrammaticalRelation, IndexedWord> p : pairs) {
      relns.add(p.first());
    }
    return relns;
  }

  public Collection<IndexedWord> getRoots() {
    return roots;
  }

  public boolean isRoot(IndexedWord vertex) {
    return roots.contains(vertex);
  }

  /**
   * Initially looks for nodes which have no incoming arcs. If there are any, it
   * returns a list of them. If not, it looks for nodes from which every other
   * node is reachable. If there are any, it returns a list of them. Otherwise,
   * it returns an empty list.
   *
   * @return A list of root nodes or an empty list.
   */
  private List<IndexedWord> getVerticesWithoutParents() {
    List<IndexedWord> result = new ArrayList<>();
    for (IndexedWord v : vertexSet()) {
      int inDegree = inDegree(v);
      if (inDegree == 0) {
        result.add(v);
      }
    }
    Collections.sort(result);
    return result;
  }

  /** Returns the (first) root of this SemanticGraph. */
  public IndexedWord getFirstRoot() {
    if (roots.isEmpty())
      throw new RuntimeException("No roots in graph:\n" + this
          + "\nFind where this graph was created and make sure you're adding roots.");
    return roots.iterator().next();
  }

  public void addRoot(IndexedWord root) {
    addVertex(root);
    roots.add(root);
  }

  /**
   * This method should not be used if possible. TODO: delete it
   *
   * Recomputes the roots, based of actual candidates. This is done to
   * ensure a rooted tree after a sequence of edits. If the none of the vertices
   * can act as a root (due to a cycle), keep old rootset, retaining only the
   * existing vertices on that list.
   *
   * TODO: this cannot deal with "Hamburg is a city which everyone likes", as
   * the intended root node,'Hamburg, is also the dobj of the relative clause. A
   * possible solution would be to create edgeset routines that allow filtering
   * over a predicate, and specifically filter out dobj relations for choosing
   * next best candidate. This could also be useful for dealing with
   * non-syntactic arcs in the future. TODO: There is also the possibility the
   * roots could be empty at the end, and will need to be resolved. TODO:
   * determine if this is a reasonably correct solution.
   */
  public void resetRoots() {
    Collection<IndexedWord> newRoots = getVerticesWithoutParents();
    if (newRoots.size() > 0) {
      roots.clear();
      roots.addAll(newRoots);
      return;
    }

    if (vertexSet().size() == 0) {
      roots.clear();
      return;
    }

    /*
     * else { Collection<IndexedWord> oldRoots = new
     * ArrayList<IndexedWord>(roots); for (IndexedWord oldRoot : oldRoots) { if
     * (!containsVertex(oldRoot)) removeVertex(oldRoot); } }
     */

    // If no apparent root candidates are available, likely due to loop back
    // edges (rcmod), find the node that dominates the most nodes, and let
    // that be the new root. Note this implementation epitomizes K.I.S.S., and
    // is brain dead and non-optimal, and will require further work.
    TwoDimensionalCounter<IndexedWord, IndexedWord> nodeDists = TwoDimensionalCounter.identityHashMapCounter();
    for (IndexedWord node1 : vertexSet()) {
      for (IndexedWord node2 : vertexSet()) {
        // want directed paths only
        List<SemanticGraphEdge> path = getShortestDirectedPathEdges(node1, node2);
        if (path != null) {
          int dist = path.size();
          nodeDists.setCount(node1, node2, dist);
        }
      }
    }

    // K.I.S.S. alg: just sum up and see who's on top, values don't have much
    // meaning outside of determining dominance.
    ClassicCounter<IndexedWord> dominatedEdgeCount = ClassicCounter.identityHashMapCounter();
    for (IndexedWord outer : vertexSet()) {
      for (IndexedWord inner : vertexSet()) {
        dominatedEdgeCount.incrementCount(outer, nodeDists.getCount(outer, inner));
      }
    }

    IndexedWord winner = Counters.argmax(dominatedEdgeCount);
    // TODO: account for multiply rooted graphs later
    setRoot(winner);
  }

  public void setRoot(IndexedWord word) {
    roots.clear();
    roots.add(word);
  }

  public void setRoots(Collection<IndexedWord> words) {
    roots.clear();
    roots.addAll(words);
  }

  /**
   *
   * @return A sorted list of the vertices
   * @throws CyclicGraphException (a subtype of IllegalStateException) if this graph is not a DAG
   */
  public List<IndexedWord> topologicalSort() {
    return graph.topologicalSort();
  }

  /**
   * Does the given {@code vertex} have at least one child with the given {@code reln} and the lemma {@code childLemma}?
   */
  public boolean hasChild(IndexedWord vertex, GrammaticalRelation reln, String childLemma) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    for (SemanticGraphEdge edge : outgoingEdgeIterable(vertex)) {
      if (edge.getRelation().equals(reln)) {
        if (edge.getTarget().get(CoreAnnotations.LemmaAnnotation.class).equals(childLemma)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Does the given {@code vertex} have at least one child with the given {@code reln}?
   */
  public boolean hasChildWithReln(IndexedWord vertex, GrammaticalRelation reln) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    for (SemanticGraphEdge edge : outgoingEdgeIterable(vertex)) {
      if (edge.getRelation().equals(reln)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if vertex has an incoming relation reln
   *
   * @param vertex A node in this graph
   * @param reln The relation we want to check
   * @return true if vertex has an incoming relation reln
   */
  public boolean hasParentWithReln(IndexedWord vertex, GrammaticalRelation reln) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    for (SemanticGraphEdge edge : incomingEdgeIterable(vertex)) {
      if (edge.getRelation().equals(reln)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the first IndexedFeatureLabel bearing a certain grammatical
   * relation, or null if none.
   */
  public IndexedWord getChildWithReln(IndexedWord vertex, GrammaticalRelation reln) {
    if (vertex.equals(IndexedWord.NO_WORD))
      return null;
    if (!containsVertex(vertex))
      throw new UnknownVertexException(vertex, this);

    for (SemanticGraphEdge edge : outgoingEdgeIterable(vertex)) {
      if (edge.getRelation().equals(reln)) {
        return edge.getTarget();
      }
    }
    return null;
  }

  /**
   * Returns a set of all parents bearing a certain grammatical relation, or an
   * empty set if none.
   */
  public Set<IndexedWord> getParentsWithReln(IndexedWord vertex, GrammaticalRelation reln) {
    if (vertex.equals(IndexedWord.NO_WORD))
      return Collections.emptySet();
    if (!containsVertex(vertex))
      throw new UnknownVertexException(vertex, this);

    Set<IndexedWord> parentList = wordMapFactory.newSet();
    for (SemanticGraphEdge edge : incomingEdgeIterable(vertex)) {
      if (edge.getRelation().equals(reln)) {
        parentList.add(edge.getSource());
      }
    }
    return parentList;
  }

  /**
   * Returns a set of all parents bearing a certain grammatical relation, or an
   * empty set if none.
   */
  public Set<IndexedWord> getParentsWithReln(IndexedWord vertex, String relnName) {
    if (vertex.equals(IndexedWord.NO_WORD))
      return Collections.emptySet();
    if (!containsVertex(vertex))
      throw new UnknownVertexException(vertex, this);

    Set<IndexedWord> parentList = wordMapFactory.newSet();
    for (SemanticGraphEdge edge : incomingEdgeIterable(vertex)) {
      if (edge.getRelation().toString().equals(relnName)) {
        parentList.add(edge.getSource());
      }
    }
    return parentList;
  }

  /**
   * Returns a set of all children bearing a certain grammatical relation, or
   * an empty set if none.
   */
  public Set<IndexedWord> getChildrenWithReln(IndexedWord vertex, GrammaticalRelation reln) {
    if (vertex.equals(IndexedWord.NO_WORD))
      return Collections.emptySet();
    if (!containsVertex(vertex))
      throw new UnknownVertexException(vertex, this);

    Set<IndexedWord> childList = wordMapFactory.newSet();
    for (SemanticGraphEdge edge : outgoingEdgeIterable(vertex)) {
      if (edge.getRelation().equals(reln)) {
        childList.add(edge.getTarget());
      }
    }
    return childList;
  }

  /**
   * Returns a set of all children bearing one of a set of grammatical
   * relations, or an empty set if none.
   *
   * NOTE: this will only work for relation types that are classes. Those that
   * are collapsed are currently not handled correctly since they are identified
   * by strings.
   */
  public Set<IndexedWord> getChildrenWithRelns(IndexedWord vertex, Collection<GrammaticalRelation> relns) {
    if (vertex.equals(IndexedWord.NO_WORD))
      return Collections.emptySet();
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    Set<IndexedWord> childList = wordMapFactory.newSet();
    for (SemanticGraphEdge edge : outgoingEdgeIterable(vertex)) {
      if (relns.contains(edge.getRelation())) {
        childList.add(edge.getTarget());
      }
    }
    return childList;
  }

  /**
   * Given a governor, dependent, and the relation between them, returns the
   * SemanticGraphEdge object of that arc if it exists, otherwise returns null.
   */
  public SemanticGraphEdge getEdge(IndexedWord gov, IndexedWord dep, GrammaticalRelation reln) {
    Collection<SemanticGraphEdge> edges = getAllEdges(gov, dep);
    if (edges != null) {
      for (SemanticGraphEdge edge : edges) {
        if (!edge.getSource().equals(gov))
          continue;
        if ((edge.getRelation().equals(reln))) {
          return edge;
        }
      }
    }
    return null;
  }

  public boolean isNegatedVertex(IndexedWord vertex) {
    if (vertex == IndexedWord.NO_WORD) {
      return false;
    }
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }

    return (hasChildWithReln(vertex, EnglishGrammaticalRelations.NEGATION_MODIFIER) ||
            hasChild(vertex, GrammaticalRelation.DEPENDENT, "nor"));
  }

  private boolean isNegatedVerb(IndexedWord vertex) {
    if (!containsVertex(vertex)) {
      throw new UnknownVertexException(vertex, this);
    }
    return (vertex.tag().startsWith("VB") && isNegatedVertex(vertex));
  }

  /**
   * Check if the vertex is in a "conditional" context. Right now it's only
   * returning true if vertex has an "if" marker attached to it, i.e. the vertex
   * is in a clause headed by "if".
   */
  public boolean isInConditionalContext(IndexedWord vertex) {
    for (IndexedWord child : getChildrenWithReln(vertex, EnglishGrammaticalRelations.MARKER)) {
      if (child.word().equalsIgnoreCase("if")) {
        return true;
      }
    }
    return false;
  }

  // Obsolete; use functions in rte.feat.NegPolarityFeaturizers instead

  public boolean attachedNegatedVerb(IndexedWord vertex) {
    for (IndexedWord parent : getParents(vertex)) {
      if (isNegatedVerb(parent)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true iff this vertex stands in the "aux" relation to (any of)
   *  its parent(s).
   */
  public boolean isAuxiliaryVerb(IndexedWord vertex) {
    Set<GrammaticalRelation> relns = relns(vertex);
    if (relns.isEmpty())
      return false;

    boolean result = relns.contains(EnglishGrammaticalRelations.AUX_MODIFIER)
        || relns.contains(EnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER);
    // log.info("I say " + vertex + (result ? " is" : " is not") +
    // " an aux");
    return result;
  }

  public Set<IndexedWord> getLeafVertices() {
    Set<IndexedWord> result = wordMapFactory.newSet();
    for (IndexedWord v : vertexSet()) {
      if (outDegree(v) == 0) {
        result.add(v);
      }
    }
    return result;
  }

  /**
   * Returns the number of nodes in the graph
   */
  public int size() {
    return this.vertexSet().size();
  }


  /**
   * Returns all nodes reachable from {@code root}.
   *
   * @param root the root node of the subgraph
   * @return all nodes in subgraph
   */
  public Set<IndexedWord> getSubgraphVertices(IndexedWord root) {
    Set<IndexedWord> result = wordMapFactory.newSet();
    result.add(root);
    List<IndexedWord> queue = Generics.newLinkedList();
    queue.add(root);
    while (! queue.isEmpty()) {
      IndexedWord current = queue.remove(0);
      for (IndexedWord child : this.getChildren(current)) {
        if ( ! result.contains(child)) {
          result.add(child);
          queue.add(child);
        }
      }
    }
    return result;
  }

  /**
   * @return true if the graph contains no cycles.
   */
  public boolean isDag() {
    Set<IndexedWord> unused = wordMapFactory.newSet();
    unused.addAll(vertexSet());
    while (!unused.isEmpty()) {
      IndexedWord arbitrary = unused.iterator().next();
      boolean result = isDagHelper(arbitrary, unused, wordMapFactory.newSet());
      if (result) {
        return false;
      }
    }
    return true;
  }

  /**
   *
   * @param root root node of the subgraph.
   * @return true if the subgraph rooted at {@code root} contains no cycles.
   */

  public boolean isDag(IndexedWord root) {
    Set<IndexedWord> unused = wordMapFactory.newSet();
    unused.addAll(this.getSubgraphVertices(root));
    while (!unused.isEmpty()) {
      IndexedWord arbitrary = unused.iterator().next();
      boolean result = isDagHelper(arbitrary, unused, wordMapFactory.newSet());
      if (result) {
        return false;
      }
    }
    return true;
  }


  private boolean isDagHelper(IndexedWord current, Set<IndexedWord> unused, Set<IndexedWord> trail) {
    if (trail.contains(current)) {
      return true;
    } else if (!unused.contains(current)) {
      return false;
    }
    unused.remove(current);
    trail.add(current);
    for (IndexedWord child : getChildren(current)) {
      boolean result = isDagHelper(child, unused, trail);
      if (result) {
        return true;
      }
    }

    trail.remove(current);
    return false;
  }

  // ============================================================================
  // String display
  // ============================================================================

  /**
   * Recursive depth first traversal. Returns a structured representation of the
   * dependency graph.
   *
   * Example:
   *
   * <pre>{@code
   *  -> need-3 (root)
   *    -> We-0 (nsubj)
   *    -> do-1 (aux)
   *    -> n't-2 (neg)
   *    -> badges-6 (dobj)
   *      -> no-4 (det)
   *      -> stinking-5 (amod)
   * }</pre>
   *
   * This is a quite ugly way to print a SemanticGraph.
   * You might instead want to try {@link #toString(OutputFormat)}.
   */
  @Override
  public String toString() {
    return toString(CoreLabel.OutputFormat.VALUE_TAG);
  }

  public String toString(CoreLabel.OutputFormat wordFormat) {
    Collection<IndexedWord> rootNodes = getRoots();
    if (rootNodes.isEmpty()) {
      // Shouldn't happen, but return something!
      return toString(OutputFormat.READABLE);
    }

    StringBuilder sb = new StringBuilder();
    Set<IndexedWord> used = wordMapFactory.newSet();
    for (IndexedWord root : rootNodes) {
      sb.append("-> ").append(root.toString(wordFormat)).append(" (root)\n");
      recToString(root, wordFormat, sb, 1, used);
    }
    Set<IndexedWord> nodes = wordMapFactory.newSet();
    nodes.addAll(vertexSet());
    nodes.removeAll(used);
    while (!nodes.isEmpty()) {
      IndexedWord node = nodes.iterator().next();
      sb.append(node.toString(wordFormat)).append("\n");
      recToString(node, wordFormat, sb, 1, used);
      nodes.removeAll(used);
    }
    return sb.toString();
  }

  // helper for toString()
  private void recToString(IndexedWord curr, CoreLabel.OutputFormat wordFormat, StringBuilder sb, int offset, Set<IndexedWord> used) {
    used.add(curr);
    List<SemanticGraphEdge> edges = outgoingEdgeList(curr);
    Collections.sort(edges);
    for (SemanticGraphEdge edge : edges) {
      IndexedWord target = edge.getTarget();
      sb.append(space(2 * offset)).append("-> ").append(target.toString(wordFormat)).append(" (").append(edge.getRelation()).append(")\n");
      if (!used.contains(target)) { // recurse
        recToString(target, wordFormat, sb, offset + 1, used);
      }
    }
  }

  private static String space(int width) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < width; i++) {
      b.append(' ');
    }
    return b.toString();
  }

  public String toRecoveredSentenceString() {
    StringBuilder sb = new StringBuilder();
    boolean pastFirst = false;
    for (IndexedWord word : vertexListSorted()) {
      if (pastFirst) {
        sb.append(' ');
      }
      pastFirst = true;
      sb.append(word.word());
    }
    return sb.toString();
  }

  public String toRecoveredSentenceStringWithIndexMarking() {
    StringBuilder sb = new StringBuilder();
    boolean pastFirst = false;
    int index = 0;
    for (IndexedWord word : vertexListSorted()) {
      if (pastFirst) {
        sb.append(' ');
      }
      pastFirst = true;
      sb.append(word.word());
      sb.append("(");
      sb.append(index++);
      sb.append(")");
    }
    return sb.toString();
  }

  /**
   * Similar to {@code toRecoveredString}, but will fill in words that were
   * collapsed into relations (i.e. prep_for --> 'for'). Mostly to deal with
   * collapsed dependency trees.
   *
   * TODO: consider merging with toRecoveredString() NOTE: assumptions currently
   * are for English. NOTE: currently takes immediate successors to current word
   * and expands them. This assumption may not be valid for other conditions or
   * languages?
   */
  public String toEnUncollapsedSentenceString() {
    List<IndexedWord> uncompressedList = Generics.newLinkedList(vertexSet());
    List<Pair<String, IndexedWord>> specifics = Generics.newArrayList();

    // Collect the specific relations and the governed nodes, and then process
    // them one by one,
    // to avoid concurrent modification exceptions.
    for (IndexedWord word : vertexSet()) {
      for (SemanticGraphEdge edge : getIncomingEdgesSorted(word)) {
        GrammaticalRelation relation = edge.getRelation();
        // Extract the specific: need to account for possibility that relation
        // can
        // be a String or GrammaticalRelation (how did it happen this way?)
        String specific = relation.getSpecific();

        if (specific == null) {
          if (edge.getRelation().equals(EnglishGrammaticalRelations.AGENT)) {
            specific = "by";
          }
        }

        // Insert the specific at the leftmost token that is not governed by
        // this node.
        if (specific != null) {
          Pair<String, IndexedWord> specPair = new Pair<>(specific, word);
          specifics.add(specPair);
        }
      }
    }

    for (Pair<String, IndexedWord> tuple : specifics) {
      insertSpecificIntoList(tuple.first(), tuple.second(), uncompressedList);
    }

    return StringUtils.join(uncompressedList, " ");
  }

  /**
   * Inserts the given specific portion of an uncollapsed relation back into the
   * targetList
   *
   * @param specific Specific relation to put in.
   * @param relnTgtNode Node governed by the uncollapsed relation
   * @param tgtList Target List of words
   */
  private void insertSpecificIntoList(String specific, IndexedWord relnTgtNode, List<IndexedWord> tgtList) {
    int currIndex = tgtList.indexOf(relnTgtNode);
    Set<IndexedWord> descendants = descendants(relnTgtNode);
    IndexedWord specificNode = new IndexedWord();
    specificNode.set(CoreAnnotations.LemmaAnnotation.class, specific);
    specificNode.set(CoreAnnotations.TextAnnotation.class, specific);
    specificNode.set(CoreAnnotations.OriginalTextAnnotation.class, specific);
    while ((currIndex >= 1) && descendants.contains(tgtList.get(currIndex - 1))) {
      currIndex--;
    }
    tgtList.add(currIndex, specificNode);
  }



  public enum OutputFormat {
    LIST, XML, READABLE, RECURSIVE
  }

  /**
   * Returns a String representation of the result of this set of typed
   * dependencies in a user-specified format. Currently, four formats are
   * supported ({@link OutputFormat}):
   * <dl>
   * <dt>list</dt>
   * <dd>(Default.) Formats the dependencies as logical relations, as
   * exemplified by the following:
   *
   * <pre>
   *  nsubj(died-1, Sam-0)
   *  tmod(died-1, today-2)
   * </pre>
   *
   * </dd>
   * <dt>readable</dt>
   * <dd>Formats the dependencies as a table with columns {@code dependent}, {@code relation}, and {@code governor},
   * as exemplified by the following:
   *
   * <pre>
   *  Sam-0               nsubj               died-1
   *  today-2             tmod                died-1
   * </pre>
   *
   * </dd>
   * <dt>xml</dt>
   * <dd>Formats the dependencies as XML, as exemplified by the following:
   *
   * <pre>
   *  &lt;dependencies&gt;
   *    &lt;dep type="nsubj"&gt;
   *      &lt;governor idx="1"&gt;died&lt;/governor&gt;
   *      &lt;dependent idx="0"&gt;Sam&lt;/dependent&gt;
   *    &lt;/dep&gt;
   *    &lt;dep type="tmod"&gt;
   *      &lt;governor idx="1"&gt;died&lt;/governor&gt;
   *      &lt;dependent idx="2"&gt;today&lt;/dependent&gt;
   *    &lt;/dep&gt;
   *  &lt;/dependencies&gt;
   * </pre>
   * </dd>
   *
   * <dt>recursive</dt>
   * <dd>
   * The default output for {@link #toString()}
   * </dd>
   *
   * </dl>
   *
   * @param format A {@code String} specifying the desired format
   * @return A {@code String} representation of the typed dependencies in
   *         this {@code GrammaticalStructure}
   */
  public String toString(OutputFormat format) {
    switch(format) {
    case XML:
      return toXMLString();
    case READABLE:
      return toReadableString();
    case LIST:
      return toList();
    case RECURSIVE:
      return toString();
    default:
      throw new IllegalArgumentException("Unsupported format " + format);
    }
  }

  /**
   * Returns a String representation of this graph as a list of typed
   * dependencies, as exemplified by the following:
   *
   * <pre>
   *  nsubj(died-6, Sam-3)
   *  tmod(died-6, today-9)
   * </pre>
   *
   * @return a {@code String} representation of this set of typed dependencies
   */
  public String toList() {
    StringBuilder buf = new StringBuilder();
    for (IndexedWord root : getRoots()) {
      buf.append("root(ROOT-0, ");
      buf.append(root.toString(CoreLabel.OutputFormat.VALUE_INDEX)).append(")\n");
    }
    for (SemanticGraphEdge edge : this.edgeListSorted()) {
      buf.append(edge.getRelation()).append("(");
      buf.append(edge.getSource().toString(CoreLabel.OutputFormat.VALUE_INDEX)).append(", ");
      buf.append(edge.getTarget().toString(CoreLabel.OutputFormat.VALUE_INDEX)).append(")\n");
    }
    return buf.toString();
  }

  /**
   * Similar to toList(), but uses POS tags instead of word and index.
   */
  public String toPOSList() {
    StringBuilder buf = new StringBuilder();
    for (SemanticGraphEdge edge : this.edgeListSorted()) {
      buf.append(edge.getRelation()).append("(");
      buf.append(edge.getSource()).append(",");
      buf.append(edge.getTarget()).append(")\n");
    }
    return buf.toString();
  }

  private String toReadableString() {
    StringBuilder buf = new StringBuilder();
    buf.append(String.format("%-20s%-20s%-20s%n", "dep", "reln", "gov"));
    buf.append(String.format("%-20s%-20s%-20s%n", "---", "----", "---"));
    for (IndexedWord root : getRoots()) {
      buf.append(String.format("%-20s%-20s%-20s%n", root.toString(CoreLabel.OutputFormat.VALUE_TAG_INDEX), "root", "root"));
    }
    for (SemanticGraphEdge edge : this.edgeListSorted()) {
      buf.append(String.format("%-20s%-20s%-20s%n",
          edge.getTarget().toString(CoreLabel.OutputFormat.VALUE_TAG_INDEX),
          edge.getRelation().toString(),
          edge.getSource().toString(CoreLabel.OutputFormat.VALUE_TAG_INDEX)));
    }
    return buf.toString();
  }

  private String toXMLString() {
    StringBuilder buf = new StringBuilder("<dependencies style=\"typed\">\n");
    for (SemanticGraphEdge edge : this.edgeListSorted()) {
      String reln = edge.getRelation().toString();
      String gov = (edge.getSource()).word();
      int govIdx = (edge.getSource()).index();
      String dep = (edge.getTarget()).word();
      int depIdx = (edge.getTarget()).index();
      buf.append("  <dep type=\"").append(reln).append("\">\n");
      buf.append("    <governor idx=\"").append(govIdx);
      if (edge.getSource().hasEmptyIndex()) {
        int emptyIdx = edge.getSource().getEmptyIndex();
        if (emptyIdx != 0) {
          buf.append("\" emptyIdx=\"").append(emptyIdx);
        }
      }
      buf.append("\">").append(gov).append("</governor>\n");
      buf.append("    <dependent idx=\"").append(depIdx);
      if (edge.getTarget().hasEmptyIndex()) {
        int emptyIdx = edge.getTarget().getEmptyIndex();
        if (emptyIdx != 0) {
          buf.append("\" emptyIdx=\"").append(emptyIdx);
        }
      }
      buf.append("\">").append(dep).append("</dependent>\n");
      buf.append("  </dep>\n");
    }
    buf.append("</dependencies>\n");
    return buf.toString();
  }

  public String toCompactString() {
    return toCompactString(false);
  }

  public String toCompactString(boolean showTags) {
    StringBuilder sb = new StringBuilder();
    Set<IndexedWord> used = wordMapFactory.newSet();
    Collection<IndexedWord> roots = getRoots();
    if (roots.isEmpty()) {
      if (size() == 0) {
        return "[EMPTY_SEMANTIC_GRAPH]";
      } else {
        return "[UNROOTED_SEMANTIC_GRAPH]";
      }
      // return toString("readable");
    }
    for (IndexedWord root : roots) {
      toCompactStringHelper(root, sb, used, showTags);
    }
    return sb.toString();
  }

  private void toCompactStringHelper(IndexedWord node, StringBuilder sb, Set<IndexedWord> used, boolean showTags) {
    used.add(node);
    try {
      boolean isntLeaf = (outDegree(node) > 0);
      if (isntLeaf) {
        sb.append("[");
      }
      sb.append(node.word());
      if (showTags) {
        sb.append("/");
        sb.append(node.tag());
      }
      for (SemanticGraphEdge edge : getOutEdgesSorted(node)) {
        IndexedWord target = edge.getTarget();
        sb.append(" ").append(edge.getRelation()).append(">");
        if (!used.contains(target)) { // avoid infinite loop
          toCompactStringHelper(target, sb, used, showTags);
        } else {
          sb.append(target.word());
          if (showTags) {
            sb.append("/");
            sb.append(target.tag());
          }
        }
      }
      if (isntLeaf) {
        sb.append("]");
      }
    } catch (IllegalArgumentException e) {
      log.info("WHOA!  SemanticGraph.toCompactStringHelper() ran into problems at node " + node);
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Returns a {@code String} representation of this semantic graph,
   * formatted by the default semantic graph formatter.
   */
  public String toFormattedString() {
    return formatter.formatSemanticGraph(this);
  }

  /**
   * Returns a {@code String} representation of this semantic graph,
   * formatted by the supplied semantic graph formatter.
   */
  public String toFormattedString(SemanticGraphFormatter formatter) {
    return formatter.formatSemanticGraph(this);
  }

  /**
   * Pretty-prints this semantic graph to {@code System.out}, formatted by
   * the supplied semantic graph formatter.
   */
  public void prettyPrint(SemanticGraphFormatter formatter) {
    System.out.println(formatter.formatSemanticGraph(this));
  }

  /**
   * Pretty-prints this semantic graph to {@code System.out}, formatted by
   * the default semantic graph formatter.
   */
  public void prettyPrint() {
    System.out.println(formatter.formatSemanticGraph(this));
  }

  /**
   * Returns an unnamed dot format digraph.
   * Nodes will be labeled with the word and edges will be labeled
   * with the dependency.
   */
  public String toDotFormat() {
    return toDotFormat("");
  }

  /**
   * Returns a dot format digraph with the given name.
   * Nodes will be labeled with the word and edges will be labeled
   * with the dependency.
   */
  public String toDotFormat(String graphname) {
    return toDotFormat(graphname, CoreLabel.OutputFormat.VALUE_TAG_INDEX);
  }

  public String toDotFormat(String graphname, CoreLabel.OutputFormat indexedWordFormat) {
    StringBuilder output = new StringBuilder();
    output.append("digraph " + graphname + " {\n");
    for (IndexedWord word : graph.getAllVertices()) {
      output.append("  N_" + word.index() + " [label=\"" +
                    word.toString(indexedWordFormat) +
                    "\"];\n");
    }
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      output.append("  N_" + edge.getSource().index() +
                    " -> N_" + edge.getTarget().index() +
                    " [label=\"" + edge.getRelation() + "\"];\n");
    }
    output.append("}\n");
    return output.toString();
  }

  public SemanticGraphEdge addEdge(IndexedWord s, IndexedWord d, GrammaticalRelation reln, double weight, boolean isExtra) {
    SemanticGraphEdge newEdge = new SemanticGraphEdge(s, d, reln, weight, isExtra);
    graph.add(s, d, newEdge);
    return newEdge;
  }

  public SemanticGraphEdge addEdge(SemanticGraphEdge edge) {
    SemanticGraphEdge newEdge = new SemanticGraphEdge(edge.getGovernor(), edge.getDependent(),
        edge.getRelation(), edge.getWeight(), edge.isExtra());
    graph.add(edge.getGovernor(), edge.getDependent(), newEdge);
    return newEdge;
  }

  // =======================================================================

  /**
   * Tries to parse a String representing a SemanticGraph. Right now it's fairly
   * dumb, could be made more sophisticated.
   * <br>
   *
   * Example: {@code [ate subj>Bill dobj>[muffins compound>blueberry]]}
   * <br>
   *
   * This is the same format generated by toCompactString().
   * <br>
   * Indices are represented by a dash separated number after the word:
   * {@code [ate-1 subj>Bill-2 ...}
   * <br>
   * An EmptyIndex for fake words such as in UD datasets is represented
   * by a period separated number after the regular index
   * {@code [ate-1 dobj>Bill-1.1 ...]}
   */
  public static SemanticGraph valueOf(String s, Language language, Integer sentIndex) {
    return (new SemanticGraphParsingTask(s, language, sentIndex)).parse();
  }

  /**
   * @see SemanticGraph#valueOf(String, Language, Integer)
   */
  public static SemanticGraph valueOf(String s, Language language) {
    return (new SemanticGraphParsingTask(s, language)).parse();
  }

  /**
   * @see SemanticGraph#valueOf(String, Language, Integer)
   */
  public static SemanticGraph valueOf(String s) {
    return valueOf(s, Language.UniversalEnglish);
  }

  /**
   * @see SemanticGraph#valueOf(String, Language, Integer)
   */
  public static SemanticGraph valueOf(String s, int sentIndex) {
    return valueOf(s, Language.UniversalEnglish, sentIndex);
  }


  public SemanticGraph() {
    graph = new DirectedMultiGraph<>(outerMapFactory, innerMapFactory);
    roots = wordMapFactory.newSet();
  }

  /**
   * Returns a new SemanticGraph which is a copy of the supplied SemanticGraph.
   * Both the nodes ({@link IndexedWord}s) and the edges (SemanticGraphEdges)
   * are copied.
   */
  public SemanticGraph(SemanticGraph g) {
    graph = new DirectedMultiGraph<>(g.graph);
    roots = wordMapFactory.newSet(g.roots);
  }

  /**
   * Copies a the current graph, but also sets the mapping from the old to new
   * graph.
   */
  public SemanticGraph(SemanticGraph g,
                       Map<IndexedWord, IndexedWord> prevToNewMap) {
    graph = new DirectedMultiGraph<>(outerMapFactory, innerMapFactory);
    if (prevToNewMap == null) {
      prevToNewMap = wordMapFactory.newMap();
    }
    Set<IndexedWord> vertexes = g.vertexSet();
    for (IndexedWord vertex : vertexes) {
      IndexedWord newVertex = new IndexedWord(vertex);
      newVertex.setCopyCount(vertex.copyCount());
      addVertex(newVertex);
      prevToNewMap.put(vertex, newVertex);
    }

    roots = wordMapFactory.newSet();
    for (IndexedWord oldRoot : g.getRoots()) {
      roots.add(prevToNewMap.get(oldRoot));
    }
    for (SemanticGraphEdge edge : g.edgeIterable()) {
      IndexedWord newGov = prevToNewMap.get(edge.getGovernor());
      IndexedWord newDep = prevToNewMap.get(edge.getDependent());
      addEdge(newGov, newDep, edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
  }

  /**
   * This is the constructor used by the parser.
   */
  public SemanticGraph(Collection<TypedDependency> dependencies) {
    graph = new DirectedMultiGraph<>(outerMapFactory, innerMapFactory);
    roots = wordMapFactory.newSet();

    for (TypedDependency d : dependencies) {
      IndexedWord gov = d.gov();
      IndexedWord dep = d.dep();
      GrammaticalRelation reln = d.reln();

      if (reln != ROOT) { // the root relation only points to the root: the governor is a fake node that we don't want to add in the graph
        // It is unnecessary to call addVertex, since addEdge will
        // implicitly add vertices if needed
        //addVertex(gov);
        //addVertex(dep);
        addEdge(gov, dep, reln, Double.NEGATIVE_INFINITY, d.extra());
      } else { //it's the root and we add it
        addVertex(dep);
        roots.add(dep);
      }
    }

    // there used to be an if clause that filtered out the case of empty
    // dependencies. However, I could not understand (or replicate) the error
    // it alluded to, and it led to empty dependency graphs for very short
    // fragments,
    // which meant they were ignored by the RTE system. Changed. (pado)
    // See also SemanticGraphFactory.makeGraphFromTree().
  }

  /**
   * Returns the nodes in the shortest undirected path between two edges in the
   * graph. if source == target, returns a singleton list
   *
   * @param source
   *          node
   * @param target
   *          node
   * @return nodes along shortest undirected path from source to target, in
   *         order
   */
  public List<IndexedWord> getShortestUndirectedPathNodes(IndexedWord source, IndexedWord target) {
    return graph.getShortestPath(source, target, false);
  }

  public List<SemanticGraphEdge> getShortestUndirectedPathEdges(IndexedWord source, IndexedWord target) {
    return graph.getShortestPathEdges(source, target, false);
  }

  /**
   * Returns the shortest directed path between two edges in the graph.
   *
   * @param source node
   * @param target node
   * @return shortest directed path from source to target
   */
  public List<IndexedWord> getShortestDirectedPathNodes(IndexedWord source, IndexedWord target) {
    return graph.getShortestPath(source, target, true);
  }

  public List<SemanticGraphEdge> getShortestDirectedPathEdges(IndexedWord source, IndexedWord target) {
    return graph.getShortestPathEdges(source, target, true);
  }

  public SemanticGraph makeSoftCopy() {
    SemanticGraph newSg = new SemanticGraph();
    if ( ! this.roots.isEmpty())
      newSg.setRoot(this.getFirstRoot());
    for (SemanticGraphEdge edge : this.edgeIterable()) {
      newSg.addEdge(edge.getSource(), edge.getTarget(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
    return newSg;
  }

  // ============================================================================

  // the chunk at the end captures an integer without the [.]
  // if there is an emptyIndex attached to the node's index
  private static final Pattern WORD_AND_INDEX_PATTERN = Pattern.compile("(.*)-([0-9]+)(?:(?:[.])([0-9]+))?");

  /**
   * This nested class is a helper for valueOf(). It represents the task of
   * parsing a specific String representing a SemanticGraph.
   */
  private static class SemanticGraphParsingTask extends StringParsingTask<SemanticGraph> {

    private SemanticGraph sg;
    private TwoDimensionalMap<Integer, Integer, IndexedWord> indexesUsed = TwoDimensionalMap.hashMap();
    private final Language language;
    private final Integer sentIndex;

    public SemanticGraphParsingTask(String s) {
      this(s, Language.UniversalEnglish, null);
    }

    public SemanticGraphParsingTask(String s, Language language) {
      this(s, language, null);
    }

    public SemanticGraphParsingTask(String s, Language language, Integer sentIndex) {
      super(s);
      this.language = language;
      this.sentIndex = sentIndex;
    }

    @Override
    public SemanticGraph parse() {
      sg = new SemanticGraph();
      try {
        readWhiteSpace();
        if (!isLeftBracket(peek()))
          return null;
        readDep(null, null);
        return sg;
      } catch (ParserException e) {
        log.info("SemanticGraphParser warning: " + e.getMessage());
        return null;
      }
    }

    private void readDep(IndexedWord gov, String reln) {
      readWhiteSpace();
      if (!isLeftBracket(peek())) { // it's a leaf
        String label = readName();
        IndexedWord dep = makeVertex(label);
        sg.addVertex(dep);
        if (gov == null)
          sg.roots.add(dep);
        sg.addEdge(gov, dep, GrammaticalRelation.valueOf(this.language, reln), Double.NEGATIVE_INFINITY, false);
      } else {
        readLeftBracket();
        String label = readName();
        IndexedWord dep = makeVertex(label);
        sg.addVertex(dep);
        if (gov == null)
          sg.roots.add(dep);
        if (gov != null && reln != null) {
          sg.addEdge(gov, dep, GrammaticalRelation.valueOf(this.language, reln), Double.NEGATIVE_INFINITY, false);
        }
        readWhiteSpace();
        while (!isRightBracket(peek()) && !isEOF) {
          reln = readName();
          readRelnSeparator();
          readDep(dep, reln);
          readWhiteSpace();
        }
        readRightBracket();
      }
    }

    private IndexedWord makeVertex(String word) {
      Integer index; // initialized below
      Integer emptyIndex = 0;
      Triple<String, Integer, Integer> wordAndIndex = readWordAndIndex(word);
      if (wordAndIndex != null) {
        word = wordAndIndex.first();
        index = wordAndIndex.second();
        emptyIndex = wordAndIndex.third();
      } else {
        index = getNextFreeIndex();
      }
      if (indexesUsed.contains(index, emptyIndex)) {
        return indexesUsed.get(index, emptyIndex);
      }
      IndexedWord ifl = new IndexedWord(null, sentIndex != null ? sentIndex : 0, index);
      if (emptyIndex != 0) {
        ifl.setEmptyIndex(emptyIndex);
      }
      // log.info("SemanticGraphParsingTask>>> word = " + word);
      // log.info("SemanticGraphParsingTask>>> index = " + index);
      // log.info("SemanticGraphParsingTask>>> indexesUsed = " + indexesUsed);
      String[] wordAndTag = word.split("/");
      ifl.set(CoreAnnotations.TextAnnotation.class, wordAndTag[0]);
      ifl.set(CoreAnnotations.ValueAnnotation.class, wordAndTag[0]);
      if (wordAndTag.length > 1)
        ifl.set(CoreAnnotations.PartOfSpeechAnnotation.class, wordAndTag[1]);
      indexesUsed.put(index, emptyIndex, ifl);
      return ifl;
    }

    private static Triple<String, Integer, Integer> readWordAndIndex(String word) {
      Matcher matcher = WORD_AND_INDEX_PATTERN.matcher(word);
      if (!matcher.matches()) {
        return null;
      } else {
        word = matcher.group(1);
        Integer index = Integer.valueOf(matcher.group(2));
        Integer emptyIndex;
        if (matcher.group(3) != null) {
          emptyIndex = Integer.valueOf(matcher.group(3));
        } else {
          emptyIndex = 0;
        }
        return new Triple<>(word, index, emptyIndex);
      }
    }

    private Integer getNextFreeIndex() {
      int i = 0;
      while (indexesUsed.containsKey(i))
        i++;
      return i;
    }

    private void readLeftBracket() {
      // System.out.println("Read left.");
      readWhiteSpace();
      char ch = read();
      if (!isLeftBracket(ch))
        throw new ParserException("Expected left paren!");
    }

    private void readRightBracket() {
      // System.out.println("Read right.");
      readWhiteSpace();
      char ch = read();
      if (!isRightBracket(ch))
        throw new ParserException("Expected right paren!");
    }

    private void readRelnSeparator() {
      readWhiteSpace();
      if (isRelnSeparator(peek()))
        read();
    }

    private static boolean isLeftBracket(char ch) {
      return ch == '[';
    }

    private static boolean isRightBracket(char ch) {
      return ch == ']';
    }

    private static boolean isRelnSeparator(char ch) {
      return ch == '>';
    }

    @Override
    protected boolean isPunct(char ch) {
      return isLeftBracket(ch) || isRightBracket(ch) || isRelnSeparator(ch);
    }

  } // end SemanticGraphParsingTask

  // =======================================================================

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SemanticGraph)) {
      return false;
    }
    SemanticGraph g = (SemanticGraph) o;
    return graph.equals(g.graph) && roots.equals(g.roots);
  }

  @Override
  public int hashCode() {
    return graph.hashCode();
  }

  /**
   * Given a semantic graph, and a target relation, returns a list of all
   * relations (edges) matching.
   *
   */
  public List<SemanticGraphEdge> findAllRelns(GrammaticalRelation tgtRelation) {
    ArrayList<SemanticGraphEdge> relns = new ArrayList<>();
    for (SemanticGraphEdge edge : edgeIterable()) {
      GrammaticalRelation edgeRelation = edge.getRelation();
      if ((edgeRelation != null) && (edgeRelation.equals(tgtRelation))) {
        relns.add(edge);
      }
    }
    return relns;
  }

  /**
   * Given a semantic graph, and the short name of a target relation, returns a list of all
   * relations (edges) matching.
   *
   */
  public List<SemanticGraphEdge> findAllRelns(String tgtRelationShortname) {
    ArrayList<SemanticGraphEdge> relns = new ArrayList<>();
    for (SemanticGraphEdge edge : edgeIterable()) {
      GrammaticalRelation edgeRelation = edge.getRelation();
      if ((edgeRelation != null) && (edgeRelation.getShortName().equals(tgtRelationShortname))) {
        relns.add(edge);
      }
    }
    return relns;
  }

  /**
   * Delete all duplicate edges.
   *
   */
  public void deleteDuplicateEdges() {
    graph.deleteDuplicateEdges();
  }


  /** Returns a list of TypedDependency in the graph.
   *  This method goes through all SemanticGraphEdge and converts them
   *  to TypedDependency.
   *
   *  @return A List of TypedDependency in the graph
   */
  public Collection<TypedDependency> typedDependencies() {
    Collection<TypedDependency> dependencies = new ArrayList<>();
    IndexedWord root = null;
    for (IndexedWord node : roots) {
      if (root == null) {
        root = new IndexedWord(node.docID(), node.sentIndex(), 0);
        root.setValue("ROOT");
      }
      TypedDependency dependency = new TypedDependency(ROOT, root, node);
      dependencies.add(dependency);
    }
    for (SemanticGraphEdge e : this.edgeIterable()){
      TypedDependency dependency = new TypedDependency(e.getRelation(), e.getGovernor(), e.getDependent());
      if (e.isExtra()) {
        dependency.setExtra();
      }
      dependencies.add(dependency);
    }
    return dependencies;
  }

  /**
   * Returns the span of the subtree yield of this node. That is, the span of all the nodes under it.
   * In the case of projective graphs, the words in this span are also the yield of the constituent rooted
   * at this node.
   *
   * @param word The word acting as the root of the constituent we are finding.
   * @return A span, represented as a pair of integers. The span is zero indexed. The begin is inclusive and the end is exclusive.
   */
  public Pair<Integer, Integer> yieldSpan(IndexedWord word) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    Stack<IndexedWord> fringe = new Stack<>();
    fringe.push(word);
    while (!fringe.isEmpty()) {
      IndexedWord parent = fringe.pop();
      min = Math.min(min, parent.index() - 1);
      max = Math.max(max, parent.index());
      for (SemanticGraphEdge edge : outgoingEdgeIterable(parent)) {
        if (!edge.isExtra()) {
          fringe.push(edge.getDependent());
        }
      }
    }
    return Pair.makePair(min, max);
  }


  /**
   * Returns the yield of a node, i.e., all descendents of the node.
   *
   * @param word The word acting as the root of the constituent we are finding.
   */
  public List<IndexedWord> yield(IndexedWord word) {
    List<IndexedWord> yield = new LinkedList<>();
    Stack<IndexedWord> fringe = new Stack<>();
    fringe.push(word);
    while ( ! fringe.isEmpty()) {
      IndexedWord parent = fringe.pop();
      yield.add(parent);
      for (SemanticGraphEdge edge : outgoingEdgeIterable(parent)) {
        if (!edge.isExtra()) {
          fringe.push(edge.getDependent());
        }
      }
    }

    Collections.sort(yield);

    return yield;
  }

  /**
   * Store a comment line with this semantic graph.
   *
   * @param comment
   */
  public void addComment(String comment) {
    this.comments.add(comment);
  }

  /**
   * Return the list of comments stored with this graph.
   *
   * @return A list of comments.
   */
  public List<String> getComments() {
    return this.comments;
  }

  private static final long serialVersionUID = 1L;

}
