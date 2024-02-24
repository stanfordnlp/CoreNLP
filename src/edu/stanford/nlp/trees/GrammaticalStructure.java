package edu.stanford.nlp.trees;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;

import edu.stanford.nlp.graph.DirectedMultiGraph;
import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.AbstractCoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.ud.EnhancementOptions;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import static edu.stanford.nlp.trees.GrammaticalRelation.DEPENDENT;
import static edu.stanford.nlp.trees.GrammaticalRelation.ROOT;


/**
 * A {@code GrammaticalStructure} stores dependency relations between
 * nodes in a tree.  A new {@code GrammaticalStructure} is constructed
 * from an existing parse tree with the help of {@link
 * GrammaticalRelation {@code GrammaticalRelation}}, which
 * defines a hierarchy of grammatical relations, along with
 * patterns for identifying them in parse trees.  The constructor for
 * {@code GrammaticalStructure} uses these definitions to
 * populate the new {@code GrammaticalStructure} with as many
 * labeled grammatical relations as it can.  Once constructed, the new
 * {@code GrammaticalStructure} can be printed in various
 * formats, or interrogated using the interface methods in this
 * class. Internally, this uses a representation via a {@code TreeGraphNode},
 * that is, a tree with additional labeled
 * arcs between nodes, for representing the grammatical relations in a
 * parse tree.
 *
 * @author Bill MacCartney
 * @author Galen Andrew (refactoring English-specific stuff)
 * @author Ilya Sherman (dependencies)
 * @author Daniel Cer
 * @see EnglishGrammaticalRelations
 * @see GrammaticalRelation
 * @see EnglishGrammaticalStructure
 */
public abstract class GrammaticalStructure implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(GrammaticalStructure.class);

  private static final boolean PRINT_DEBUGGING = System.getProperty("GrammaticalStructure", null) != null;

  /**
   * A specification for the types of extra edges to add to the dependency tree.
   * If you're in doubt, use {@link edu.stanford.nlp.trees.GrammaticalStructure.Extras#NONE}.
   */
  public enum Extras {
    /**
     * Don't include any additional edges.
     *
     *   Note: In older code (2014 and before) including extras was a boolean flag. This option is the equivalent of
     *   the {@code false} flag.
     */
    NONE(false, false, false),
    /**
     * Include only the extra reference edges, and save them as reference edges without collapsing.
     */
    REF_ONLY_UNCOLLAPSED(true, false, false),
    /**
     * Include only the extra reference edges, but collapsing these edges to clone the edge type of the referent.
     * So, for example, <i>My dog who eats sausage</i> may have a "ref" edge from <i>who</i> to <i>dog</i>
     * that would be deleted and replaced with an "nsubj" edge from <i>eats</i> to <i>dog</i>.
     */
    REF_ONLY_COLLAPSED(true, false, true),
    /**
     * Add extra subjects only, not adding any of the other extra edge types.
     */
    SUBJ_ONLY(false, true, false),
    /**
     * @see edu.stanford.nlp.trees.GrammaticalStructure.Extras#SUBJ_ONLY
     * @see edu.stanford.nlp.trees.GrammaticalStructure.Extras#REF_ONLY_UNCOLLAPSED
     */
    REF_UNCOLLAPSED_AND_SUBJ(true, true, false),
    /**
     * @see edu.stanford.nlp.trees.GrammaticalStructure.Extras#SUBJ_ONLY
     * @see edu.stanford.nlp.trees.GrammaticalStructure.Extras#REF_ONLY_COLLAPSED
     */
    REF_COLLAPSED_AND_SUBJ(true, true, true),
    /**
     *   Do the maximal amount of extra processing.
     *   Currently, this is equivalent to {@link edu.stanford.nlp.trees.GrammaticalStructure.Extras#REF_COLLAPSED_AND_SUBJ}.
     *
     *   Note: In older code (2014 and before) including extras was a boolean flag. This option is the equivalent of
     *   the {@code true} flag.
     */
    MAXIMAL(true, true, true);

    /** Add "ref" edges */
    public final boolean doRef;
    /** Add extra subject edges */
    public final boolean doSubj;
    /** collapse the "ref" edges */
    public final boolean collapseRef;

    /** Constructor. Nothing exciting here. */
    Extras(boolean doRef, boolean doSubj, boolean collapseRef) {
      this.doRef = doRef;
      this.doSubj = doSubj;
      this.collapseRef = collapseRef;
    }

  } // end enum Extras


  protected final List<TypedDependency> typedDependencies;
  protected final List<TypedDependency> allTypedDependencies;

  protected final Predicate<String> puncFilter;
  protected final Predicate<String> tagFilter;

  /**
   * The root Tree node for this GrammaticalStructure.
   */
  private final TreeGraphNode root;

  /**
   * A map from arbitrary integer indices to nodes.
   */
  private final Map<Integer, TreeGraphNode> indexMap = Generics.newHashMap();

  /**
   * Create a new GrammaticalStructure, analyzing the parse tree and
   * populate the GrammaticalStructure with as many labeled
   * grammatical relation arcs as possible.
   *
   * @param t             A Tree to analyze
   * @param relations     A set of GrammaticalRelations to consider
   * @param relationsLock Something needed to make this thread-safe when iterating over relations
   * @param transformer   A tree transformer to apply to the tree before converting (this argument
   *                      may be null if no transformer is required)
   * @param hf            A HeadFinder for analysis
   * @param puncFilter    A Filter to reject punctuation. To delete punctuation
   *                      dependencies, this filter should return false on
   *                      punctuation word strings, and true otherwise.
   *                      If punctuation dependencies should be kept, you
   *                      should pass in a {@code Filters.<String>acceptFilter()}.
   * @param tagFilter     Appears to be unused (filters out tags??)
   */
  public GrammaticalStructure(Tree t, Collection<GrammaticalRelation> relations,
                              Lock relationsLock, TreeTransformer transformer,
                              HeadFinder hf, Predicate<String> puncFilter,
                              Predicate<String> tagFilter) {
    TreeGraphNode treeGraph = new TreeGraphNode(t, (TreeGraphNode) null);
    // TODO: create the tree and reuse the leaf labels in one pass,
    // avoiding a wasteful copy of the labels.
    Trees.setLeafLabels(treeGraph, t.yield());
    Trees.setLeafTagsIfUnset(treeGraph);
    //System.out.println(treeGraph.toPrettyString(2));
    if (transformer != null) {
      Tree transformed = transformer.transformTree(treeGraph);
      if (!(transformed instanceof TreeGraphNode)) {
        throw new RuntimeException("Transformer did not change TreeGraphNode into another TreeGraphNode: " + transformer);
      }
      this.root = (TreeGraphNode) transformed;
    } else {
      this.root = treeGraph;
    }
    //System.out.println(this.root.toPrettyString(2));
    indexNodes(this.root);
    // add head word and tag to phrase nodes
    if (hf == null) {
      throw new AssertionError("Cannot use null HeadFinder");
    }
    try {
      root.percolateHeads(hf);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Cannot process tree:\n" + t, e);
    }
    if (root.value() == null) {
      root.setValue("ROOT");  // todo: cdm: it doesn't seem like this line should be here
    }
    // add dependencies, using heads
    this.puncFilter = puncFilter;
    this.tagFilter = tagFilter;
    // NoPunctFilter puncDepFilter = new NoPunctFilter(puncFilter);
    NoPunctTypedDependencyFilter puncTypedDepFilter = new NoPunctTypedDependencyFilter(puncFilter, tagFilter);

    DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> basicGraph = new DirectedMultiGraph<>();
    DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> completeGraph = new DirectedMultiGraph<>();

    // analyze the root (and its descendants, recursively)
    if (relationsLock != null) {
      relationsLock.lock();
    }
    try {
      analyzeNode(root, root, relations, hf, puncFilter, tagFilter, basicGraph, completeGraph);
    }
    finally {
      if (relationsLock != null) {
        relationsLock.unlock();
      }
    }

    attachStrandedNodes(root, root, false, puncFilter, tagFilter, basicGraph);

    // add typed dependencies
    typedDependencies = getDeps(puncTypedDepFilter, basicGraph);
    allTypedDependencies = Generics.newArrayList(typedDependencies);
    getExtraDeps(allTypedDependencies, puncTypedDepFilter, completeGraph);
  }


  /**
   * Assign sequential integer indices (starting with 1) to all
   * nodes of the subtree rooted at this
   * {@code Tree}.  The leaves are indexed first,
   * from left to right.  Then the internal nodes are indexed,
   * using a pre-order tree traversal.
   */
  private void indexNodes(TreeGraphNode tree) {
    indexNodes(tree, indexLeaves(tree, 1));
  }

  /**
   * Assign sequential integer indices to the leaves of the subtree
   * rooted at this {@code TreeGraphNode}, beginning with
   * {@code startIndex}, and traversing the leaves from left
   * to right. If node is already indexed, then it uses the existing index.
   *
   * @param startIndex index for this node
   * @return the next index still unassigned
   */
  private int indexLeaves(TreeGraphNode tree, int startIndex) {
    if (tree.isLeaf()) {
      int oldIndex = tree.index();
      if (oldIndex >= 0) {
        startIndex = oldIndex;
      } else {
        tree.setIndex(startIndex);
      }
      addNodeToIndexMap(startIndex, tree);
      startIndex++;
    } else {
      for (TreeGraphNode child : tree.children) {
        startIndex = indexLeaves(child, startIndex);
      }
    }
    return startIndex;
  }

  /**
   * Assign sequential integer indices to all nodes of the subtree
   * rooted at this {@code TreeGraphNode}, beginning with
   * {@code startIndex}, and doing a pre-order tree traversal.
   * Any node which already has an index will not be re-indexed
   * &mdash; this is so that we can index the leaves first, and
   * then index the rest.
   *
   * @param startIndex index for this node
   * @return the next index still unassigned
   */
  private int indexNodes(TreeGraphNode tree, int startIndex) {
    if (tree.index() < 0) {		// if this node has no index
      addNodeToIndexMap(startIndex, tree);
      tree.setIndex(startIndex++);
    }
    if (!tree.isLeaf()) {
      for (TreeGraphNode child : tree.children) {
        startIndex = indexNodes(child, startIndex);
      }
    }
    return startIndex;
  }

  /**
   * Store a mapping from an arbitrary integer index to a node in
   * this treegraph.  Normally a client shouldn't need to use this,
   * as the nodes are automatically indexed by the
   * {@code TreeGraph} constructor.
   *
   * @param index the arbitrary integer index
   * @param node  the {@code TreeGraphNode} to be indexed
   */
  private void addNodeToIndexMap(int index, TreeGraphNode node) {
    indexMap.put(Integer.valueOf(index), node);
  }


  /**
   * Return the node in the this treegraph corresponding to the
   * specified integer index.
   *
   * @param index the integer index of the node you want
   * @return the {@code TreeGraphNode} having the specified
   *         index (or {@code null} if such does not exist)
   */
  private TreeGraphNode getNodeByIndex(int index) {
    return indexMap.get(Integer.valueOf(index));
  }

  /**
   * Return the root Tree of this GrammaticalStructure.
   *
   * @return the root Tree of this GrammaticalStructure
   */
  public TreeGraphNode root() {
    return root;
  }

  private static void throwDepFormatException(String dep) {
     throw new RuntimeException(String.format("Dependencies should be for the format 'type(arg-idx, arg-idx)'. Could not parse '%s'", dep));
  }

  /**
   * Create a grammatical structure from its string representation.
   *
   * Like buildCoNLLXGrammaticalStructure,
   * this method fakes up the parts of the tree structure that are not
   * used by the grammatical relation transformation operations.
   *
   * <i>Note:</i> Added by daniel cer
   *
   * @param tokens
   * @param posTags
   * @param deps
   */
  public static GrammaticalStructure fromStringReps(List<String> tokens, List<String> posTags, List<String> deps) {
    if (tokens.size() != posTags.size()) {
      throw new RuntimeException(String.format(
              "tokens.size(): %d != pos.size(): %d%n", tokens.size(), posTags
                      .size()));
    }

    List<TreeGraphNode> tgWordNodes = new ArrayList<>(tokens.size());
    List<TreeGraphNode> tgPOSNodes = new ArrayList<>(tokens.size());

    CoreLabel rootLabel = new CoreLabel();
    rootLabel.setValue("ROOT");
    List<IndexedWord> nodeWords = new ArrayList<>(tgPOSNodes.size() + 1);
    nodeWords.add(new IndexedWord(rootLabel));

    UniversalSemanticHeadFinder headFinder = new UniversalSemanticHeadFinder();

    Iterator<String> posIter = posTags.iterator();
    for (String wordString : tokens) {
      String posString = posIter.next();
      CoreLabel wordLabel = new CoreLabel();
      wordLabel.setWord(wordString);
      wordLabel.setValue(wordString);
      wordLabel.setTag(posString);
      TreeGraphNode word = new TreeGraphNode(wordLabel);
      CoreLabel tagLabel = new CoreLabel();
      tagLabel.setValue(posString);
      tagLabel.setWord(posString);
      TreeGraphNode pos = new TreeGraphNode(tagLabel);
      tgWordNodes.add(word);
      tgPOSNodes.add(pos);
      TreeGraphNode[] childArr = {word};
      pos.setChildren(childArr);
      word.setParent(pos);
      pos.percolateHeads(headFinder);
      nodeWords.add(new IndexedWord(wordLabel));
    }

    TreeGraphNode root = new TreeGraphNode(rootLabel);

    root.setChildren(tgPOSNodes.toArray(new TreeGraphNode[tgPOSNodes.size()]));

    root.setIndex(0);

    // Build list of TypedDependencies
    List<TypedDependency> tdeps = new ArrayList<>(deps.size());

    for (String depString : deps) {
      int firstBracket = depString.indexOf('(');
      if (firstBracket == -1) throwDepFormatException(depString);


      String type = depString.substring(0, firstBracket);

      if (depString.charAt(depString.length() - 1) != ')') throwDepFormatException(depString);

      String args = depString.substring(firstBracket + 1, depString.length() - 1);

      int argSep = args.indexOf(", ");
      if (argSep == -1) throwDepFormatException(depString);

      String parentArg = args.substring(0, argSep);
      String childArg  = args.substring(argSep + 2);
      int parentDash = parentArg.lastIndexOf('-');
      if (parentDash == -1) throwDepFormatException(depString);
      int childDash = childArg.lastIndexOf('-');
      if (childDash == -1) throwDepFormatException(depString);
      //System.err.printf("parentArg: %s%n", parentArg);
      int parentIdx = Integer.parseInt(parentArg.substring(parentDash+1).replace("'", ""));

      int childIdx = Integer.parseInt(childArg.substring(childDash+1).replace("'", ""));

      GrammaticalRelation grel = new GrammaticalRelation(Language.Any, type, null, DEPENDENT);

      TypedDependency tdep = new TypedDependency(grel, nodeWords.get(parentIdx), nodeWords.get(childIdx));
      tdeps.add(tdep);
    }

    // TODO add some elegant way to construct language
    // appropriate GrammaticalStructures (e.g., English, Chinese, etc.)
    return new GrammaticalStructure(tdeps, root) {
      private static final long serialVersionUID = 1L;
    };
  }

  public GrammaticalStructure(List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
    this.root = root;
    indexNodes(this.root);
    this.puncFilter = Filters.acceptFilter();
    this.tagFilter = Filters.acceptFilter();
    allTypedDependencies = typedDependencies = new ArrayList<>(projectiveDependencies);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(root.toPrettyString(0).substring(1));
    sb.append("Typed Dependencies:\n");
    sb.append(typedDependencies);
    return sb.toString();
  }

  private static void attachStrandedNodes(TreeGraphNode t, TreeGraphNode root, boolean attach, Predicate<String> puncFilter, Predicate<String> tagFilter, DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> basicGraph) {
    if (t.isLeaf()) {
      return;
    }
    if (attach && puncFilter.test(t.headWordNode().label().value()) &&
        tagFilter.test(t.headWordNode().label().tag())) {
      // make faster by first looking for links from parent
      // it is necessary to look for paths using all directions
      // because sometimes there are edges created from lower nodes to
      // nodes higher up
      TreeGraphNode parent = t.parent().highestNodeWithSameHead();
      if (!basicGraph.isEdge(parent, t) && basicGraph.getShortestPath(root, t, false) == null) {
        basicGraph.add(parent, t, GrammaticalRelation.DEPENDENT);
      }
    }
    for (TreeGraphNode kid : t.children()) {
      attachStrandedNodes(kid, root, (kid.headWordNode() != t.headWordNode()), puncFilter, tagFilter, basicGraph);
    }
  }

  // cdm dec 2009: I changed this to automatically fail on preterminal nodes, since they shouldn't match for GR parent patterns.  Should speed it up.
  private static void analyzeNode(TreeGraphNode t, TreeGraphNode root, Collection<GrammaticalRelation> relations, HeadFinder hf, Predicate<String> puncFilter, Predicate<String> tagFilter, DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> basicGraph, DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> completeGraph) {
    if (t.isPhrasal()) {    // don't do leaves or preterminals!
      TreeGraphNode tHigh = t.highestNodeWithSameHead();
      for (GrammaticalRelation egr : relations) {
        if (egr.isApplicable(t)) {
          for (TreeGraphNode u : egr.getRelatedNodes(t, root, hf)) {
            TreeGraphNode uHigh = u.highestNodeWithSameHead();
            if (uHigh == tHigh) {
              continue;
            }
            if (!puncFilter.test(uHigh.headWordNode().label().value()) ||
                ! tagFilter.test(uHigh.headWordNode().label().tag())) {
              continue;
            }
            completeGraph.add(tHigh, uHigh, egr);
            // If there are two patterns that add dependencies, X --> Z and Y --> Z, and X dominates Y, then the dependency Y --> Z is not added to the basic graph to prevent unwanted duplication.
            // Similarly, if there is already a path from X --> Y, and an expression would trigger Y --> X somehow, we ignore that
            Set<TreeGraphNode> parents = basicGraph.getParents(uHigh);
            if ((parents == null || parents.size() == 0 || parents.contains(tHigh)) &&
                basicGraph.getShortestPath(uHigh, tHigh, true) == null) {
              // log.info("Adding " + egr.getShortName() + " from " + t + " to " + u + " tHigh=" + tHigh + "(" + tHigh.headWordNode() + ") uHigh=" + uHigh + "(" + uHigh.headWordNode() + ")");
              basicGraph.add(tHigh, uHigh, egr);
            }
          }
        }
      }
      // now recurse into children
      for (TreeGraphNode kid : t.children()) {
        analyzeNode(kid, root, relations, hf, puncFilter, tagFilter, basicGraph, completeGraph);
      }
    }
  }

  private void getExtraDeps(List<TypedDependency> deps, Predicate<TypedDependency> puncTypedDepFilter, DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> completeGraph) {
    getExtras(deps);
    // adds stuff to basicDep based on the tregex patterns over the tree
    this.getTreeDeps(deps, completeGraph, puncTypedDepFilter, extraTreeDepFilter());
    Collections.sort(deps);
  }

  /**
   * Helps the constructor build a list of typed dependencies using
   * information from a {@code GrammaticalStructure}.
   */
  private List<TypedDependency> getDeps(Predicate<TypedDependency> puncTypedDepFilter, DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> basicGraph) {
    List<TypedDependency> basicDep = Generics.newArrayList();

    for (TreeGraphNode gov : basicGraph.getAllVertices()) {
      for (TreeGraphNode dep : basicGraph.getChildren(gov)) {
        GrammaticalRelation reln = getGrammaticalRelationCommonAncestor(gov.headWordNode().label(), gov.label(), dep.headWordNode().label(), dep.label(), basicGraph.getEdges(gov, dep));
        // log.info("  Gov: " + gov + " Dep: " + dep + " Reln: " + reln);
        basicDep.add(new TypedDependency(reln, new IndexedWord(gov.headWordNode().label()), new IndexedWord(dep.headWordNode().label())));
      }
    }

    // add the root
    TreeGraphNode dependencyRoot = new TreeGraphNode(new Word("ROOT"));
    dependencyRoot.setIndex(0);
    TreeGraphNode rootDep = root().headWordNode();
    if (rootDep == null) {
      List<Tree> leaves = Trees.leaves(root());
      if (leaves.size() > 0) {
        Tree leaf = leaves.get(0);
        if (!(leaf instanceof TreeGraphNode)) {
          throw new AssertionError("Leaves should be TreeGraphNodes");
        }
        rootDep = (TreeGraphNode) leaf;
        if (rootDep.headWordNode() != null) {
          rootDep = rootDep.headWordNode();
        }
      }
    }

    if (rootDep != null) {
      TypedDependency rootTypedDep = new TypedDependency(ROOT, new IndexedWord(dependencyRoot.label()), new IndexedWord(rootDep.label()));
      if (puncTypedDepFilter.test(rootTypedDep)) {
        basicDep.add(rootTypedDep);
      } else { // Root is a punctuation character

        /* Heuristic to find a root for the graph.
         * Make the first child of the current root the
         * new root and attach all other children to
         * the new root.
         */

        IndexedWord root = rootTypedDep.dep();
        IndexedWord newRoot = null;
        Collections.sort(basicDep);
        for (TypedDependency td : basicDep) {
          if (td.gov().equals(root)) {
            if (newRoot != null) {
              td.setGov(newRoot);
            } else {
              td.setGov(td.gov());
              td.setReln(ROOT);
              newRoot = td.dep();
            }
          }
        }
      }
    }

    postProcessDependencies(basicDep);

    Collections.sort(basicDep);

    return basicDep;
  }

  /**
   * Returns a Filter which checks dependencies for usefulness as
   * extra tree-based dependencies.  By default, everything is
   * accepted.  One example of how this can be useful is in the
   * English dependencies, where the REL dependency is used as an
   * intermediate and we do not want this to be added when we make a
   * second pass over the trees for missing dependencies.
   */
  protected Predicate<TypedDependency> extraTreeDepFilter() {
    return Filters.acceptFilter();
  }

  /**
   * Post process the dependencies in whatever way this language
   * requires.  For example, English might replace "rel" dependencies
   * with either dobj or pobj depending on the surrounding
   * dependencies.
   */
  protected void postProcessDependencies(List<TypedDependency> basicDep) {
    // no post processing by default
  }

  /**
   * Get extra dependencies that do not depend on the tree structure,
   * but rather only depend on the existing dependency structure.
   * For example, the English xsubj dependency can be extracted that way.
   */
  protected void getExtras(List<TypedDependency> basicDep) {
    // no extra dependencies by default
  }


  /** Look through the tree t and adds to the List basicDep
   *  additional dependencies which aren't
   *  in the List but which satisfy the filter puncTypedDepFilter.
   *
   * @param deps The list of dependencies which may be augmented
   * @param completeGraph a graph of all the tree dependencies found earlier
   * @param puncTypedDepFilter The filter that may skip punctuation dependencies
   * @param extraTreeDepFilter Additional dependencies are added only if they pass this filter
   */
  protected void getTreeDeps(List<TypedDependency> deps,
                                  DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> completeGraph,
                                  Predicate<TypedDependency> puncTypedDepFilter,
                                  Predicate<TypedDependency> extraTreeDepFilter) {
    for (TreeGraphNode gov : completeGraph.getAllVertices()) {
      for (TreeGraphNode dep : completeGraph.getChildren(gov)) {
        for (GrammaticalRelation rel : removeGrammaticalRelationAncestors(completeGraph.getEdges(gov, dep))) {
          TypedDependency newDep = new TypedDependency(rel, new IndexedWord(gov.headWordNode().label()), new IndexedWord(dep.headWordNode().label()));
          if (!deps.contains(newDep) && puncTypedDepFilter.test(newDep) && extraTreeDepFilter.test(newDep)) {
            newDep.setExtra();
            deps.add(newDep);
          }
        }
      }
    }
  }

  private static class NoPunctFilter implements Predicate<Dependency<Label, Label, Object>>, Serializable {
    private Predicate<String> npf;

    NoPunctFilter(Predicate<String> f) {
      this.npf = f;
    }

    @Override
    public boolean test(Dependency<Label, Label, Object> d) {
      if (d == null) {
        return false;
      }
      Label lab = d.dependent();
      if (lab == null) {
        return false;
      }
      return npf.test(lab.value());
    }

    // Automatically generated by Eclipse
    private static final long serialVersionUID = -2319891944796663180L;
  } // end static class NoPunctFilter


  private static class NoPunctTypedDependencyFilter implements Predicate<TypedDependency>, Serializable {
    private Predicate<String> npf;
    private Predicate<String> tf;

    NoPunctTypedDependencyFilter(Predicate<String> f, Predicate<String> tf) {
      this.npf = f;
      this.tf = tf;
    }

    @Override
    public boolean test(TypedDependency d) {
      if (d == null) return false;

      IndexedWord l = d.dep();
      if (l == null) return false;

      return npf.test(l.value()) && tf.test(l.tag());
    }

    private static final long serialVersionUID = -2872766864289207468L;

  } // end static class NoPunctTypedDependencyFilter


  /**
   * Get GrammaticalRelation between gov and dep, and null if gov  is not the
   * governor of dep.
   */
  public GrammaticalRelation getGrammaticalRelation(int govIndex, int depIndex) {
    TreeGraphNode gov = getNodeByIndex(govIndex);
    TreeGraphNode dep = getNodeByIndex(depIndex);
    // TODO: this is pretty ugly
    return getGrammaticalRelation(new IndexedWord(gov.label()), new IndexedWord(dep.label()));
  }

  /**
   * Get GrammaticalRelation between gov and dep, and null if gov is not the
   * governor of dep.
   */
  public GrammaticalRelation getGrammaticalRelation(IndexedWord gov, IndexedWord dep) {
    List<GrammaticalRelation> labels = Generics.newArrayList();
    for (TypedDependency dependency : typedDependencies(Extras.MAXIMAL)) {
      if (dependency.gov().equals(gov) && dependency.dep().equals(dep)) {
        labels.add(dependency.reln());
      }
    }

    return getGrammaticalRelationCommonAncestor(gov, gov, dep, dep, labels);
  }

  /**
   * Returns the GrammaticalRelation which is the highest common
   * ancestor of the list of relations passed in.  The Labels are
   * passed in only for debugging reasons.  gov &amp; dep are the
   * labels with the text, govH and depH can be higher labels in the
   * tree which represent the category
   */
  private static GrammaticalRelation getGrammaticalRelationCommonAncestor(AbstractCoreLabel gov, AbstractCoreLabel govH, AbstractCoreLabel dep, AbstractCoreLabel depH, List<GrammaticalRelation> labels) {
    GrammaticalRelation reln = GrammaticalRelation.DEPENDENT;

    List<GrammaticalRelation> sortedLabels;
    if (labels.size() <= 1) {
      sortedLabels = labels;
    } else {
      sortedLabels = new ArrayList<>(labels);
      Collections.sort(sortedLabels, new NameComparator<>());
    }
    // log.info(" gov " + govH + " dep " + depH + " arc labels: " + sortedLabels);

    for (GrammaticalRelation reln2 : sortedLabels) {
      if (reln.isAncestor(reln2)) {
        reln = reln2;
      } else if (PRINT_DEBUGGING && ! reln2.isAncestor(reln)) {
        log.info("@@@\t" + reln + "\t" + reln2 + "\t" +
                           govH.get(CoreAnnotations.ValueAnnotation.class) + "\t" + depH.get(CoreAnnotations.ValueAnnotation.class));
      }
    }
    if (PRINT_DEBUGGING && reln.equals(GrammaticalRelation.DEPENDENT)) {
      String topCat = govH.get(CoreAnnotations.ValueAnnotation.class);
      String topTag = gov.tag();
      String topWord = gov.value();
      String botCat = depH.get(CoreAnnotations.ValueAnnotation.class);
      String botTag = dep.tag();
      String botWord = dep.value();
      log.info("### dep\t" + topCat + "\t" + topTag + "\t" + topWord +
                         "\t" + botCat + "\t" + botTag + "\t" + botWord + "\t");
    }
    return reln;
  }

  private static List<GrammaticalRelation> removeGrammaticalRelationAncestors(List<GrammaticalRelation> original) {
    List<GrammaticalRelation> filtered = Generics.newArrayList();
    for (GrammaticalRelation reln : original) {
      boolean descendantFound = false;
      for (int index = 0; index < filtered.size(); ++index) {
        GrammaticalRelation gr = filtered.get(index);
        //if the element in the list is an ancestor of the current
        //relation, remove it (we will replace it later)
        if (gr.isAncestor(reln)) {
          filtered.remove(index);
          --index;
        } else if (reln.isAncestor(gr)) {
          //if the relation is not an ancestor of an element in the
          //list, we add the relation
          descendantFound = true;
        }
      }
      if (!descendantFound) {
        filtered.add(reln);
      }
    }
    return filtered;
  }


  /**
   * Returns the typed dependencies of this grammatical structure.  These
   * are the basic word-level typed dependencies, where each word is dependent
   * on one other thing, either a word or the starting ROOT, and the
   * dependencies have a tree structure.  This corresponds to the
   * command-line option "basicDependencies".
   *
   * @return The typed dependencies of this grammatical structure
   */
  public Collection<TypedDependency> typedDependencies() {
    return typedDependencies(Extras.NONE);
  }


  /**
   * Returns all the typed dependencies of this grammatical structure.
   * These are like the basic (uncollapsed) dependencies, but may include
   * extra arcs for control relationships, etc. This corresponds to the
   * "nonCollapsed" option.
   */
  public Collection<TypedDependency> allTypedDependencies() {
    return typedDependencies(Extras.MAXIMAL);
  }


  /**
   * Returns the typed dependencies of this grammatical structure. These
   * are non-collapsed dependencies (basic or nonCollapsed).
   *
   * @param includeExtras If true, the list of typed dependencies
   * returned may include "extras", and does not follow a tree structure.
   * @return The typed dependencies of this grammatical structure
   */
  public List<TypedDependency> typedDependencies(Extras includeExtras) {
    // This copy has to be done because of the broken way
    // TypedDependency objects can be mutated by downstream methods
    // such as collapseDependencies.  Without the copy here it is
    // possible for two consecutive calls to
    // typedDependenciesCollapsed to get different results.  For
    // example, the English dependencies rename existing objects KILL
    // to note that they should be removed.
    List<TypedDependency> source;
    if (includeExtras != Extras.NONE) {
      source = allTypedDependencies;
    } else {
      source = typedDependencies;
    }
    List<TypedDependency> deps = new ArrayList<>(source);
    //TODO (sebschu): prevent correctDependencies from getting called multiple times
    correctDependencies(deps);
    return deps;
  }

  /**
   * @see edu.stanford.nlp.trees.GrammaticalStructure#typedDependencies(edu.stanford.nlp.trees.GrammaticalStructure.Extras)
   */
  @Deprecated
  public List<TypedDependency> typedDependencies(boolean includeExtras) {
    return typedDependencies(includeExtras ? Extras.MAXIMAL : Extras.NONE);
  }

  /**
   * Get the typed dependencies after collapsing them.
   * Collapsing dependencies refers to turning certain function words
   * such as prepositions and conjunctions into arcs, so they disappear from
   * the set of nodes.
   * There is no guarantee that the dependencies are a tree. While the
   * dependencies are normally tree-like, the collapsing may introduce
   * not only re-entrancies but even small cycles.
   *
   * @return A set of collapsed dependencies
   */
  public Collection<TypedDependency> typedDependenciesCollapsed() {
    return typedDependenciesCollapsed(Extras.NONE);
  }

  // todo [cdm 2012]: The semantics of this method is the opposite of the others.
  // The other no argument methods correspond to includeExtras being
  // true, but for this one it is false.  This should probably be made uniform.
  /**
   * Get the typed dependencies after mostly collapsing them, but keep a tree
   * structure.  In order to do this, the code does:
   * <ol>
   * <li> no relative clause processing
   * <li> no xsubj relations
   * <li> no propagation of conjuncts
   * </ol>
   * This corresponds to the "tree" option.
   *
   * @return collapsed dependencies keeping a tree structure
   */
  public Collection<TypedDependency> typedDependenciesCollapsedTree() {
    List<TypedDependency> tdl = typedDependencies(Extras.NONE);
    collapseDependenciesTree(tdl);
    return tdl;
  }

  /**
   * Get the typed dependencies after collapsing them.
   * The "collapsed" option corresponds to calling this method with argument
   * {@code true}.
   *
   * @param includeExtras If true, the list of typed dependencies
   * returned may include "extras", like controlling subjects
   * @return collapsed dependencies
   */
  public List<TypedDependency> typedDependenciesCollapsed(Extras includeExtras) {
    List<TypedDependency> tdl = typedDependencies(includeExtras);
    collapseDependencies(tdl, false, includeExtras);
    return tdl;
  }

  /**
   * @see edu.stanford.nlp.trees.GrammaticalStructure#typedDependenciesCollapsed(edu.stanford.nlp.trees.GrammaticalStructure.Extras)
   */
  @Deprecated
  public List<TypedDependency> typedDependenciesCollapsed(boolean includeExtras) {
    return typedDependenciesCollapsed(includeExtras ? Extras.MAXIMAL : Extras.NONE);
  }


  /**
   * Get the typed dependencies after collapsing them and processing eventual
   * CC complements.  The effect of this part is to distributed conjoined
   * arguments across relations or conjoined predicates across their arguments.
   * This is generally useful, and we generally recommend using the output of
   * this method with the second argument being {@code true}.
   * The "CCPropagated" option corresponds to calling this method with an
   * argument of {@code true}.
   *
   * @param includeExtras If true, the list of typed dependencies
   * returned may include "extras", such as controlled subject links.
   * @return collapsed dependencies with CC processed
   */
  public List<TypedDependency> typedDependenciesCCprocessed(Extras includeExtras) {
    List<TypedDependency> tdl = typedDependencies(includeExtras);
    collapseDependencies(tdl, true, includeExtras);
    return tdl;
  }

  /**
   * @see edu.stanford.nlp.trees.GrammaticalStructure#typedDependenciesCCprocessed(edu.stanford.nlp.trees.GrammaticalStructure.Extras)
   */
  @Deprecated
  public List<TypedDependency> typedDependenciesCCprocessed(boolean includeExtras) {
    return typedDependenciesCCprocessed(includeExtras ? Extras.MAXIMAL : Extras.NONE);
  }


  public List<TypedDependency> typedDependenciesEnhanced() {
    List<TypedDependency> tdl = typedDependencies(Extras.MAXIMAL);
    addEnhancements(tdl, UniversalEnglishGrammaticalStructure.ENHANCED_OPTIONS);
    return tdl;
  }

  public List<TypedDependency> typedDependenciesEnhancedPlusPlus() {
    List<TypedDependency> tdl = typedDependencies(Extras.MAXIMAL);
    addEnhancements(tdl, UniversalEnglishGrammaticalStructure.ENHANCED_PLUS_PLUS_OPTIONS);
    return tdl;
  }


  /**
   * Get a list of the typed dependencies, including extras like control
   * dependencies, collapsing them and distributing relations across
   * coordination.  This method is generally recommended for best
   * representing the semantic and syntactic relations of a sentence. In
   * general it returns a directed graph (i.e., the output may not be a tree
   * and it may contain (small) cycles).
   * The "CCPropagated" option corresponds to calling this method.
   *
   * @return collapsed dependencies with CC processed
   */
  public List<TypedDependency> typedDependenciesCCprocessed() {
    return typedDependenciesCCprocessed(Extras.MAXIMAL);
  }


  /**
   * Destructively modify the {@code Collection&lt;TypedDependency&gt;} to collapse
   * language-dependent transitive dependencies.
   * <br>
   * Default is no-op; to be over-ridden in subclasses.
   *
   * @param list A list of dependencies to process for possible collapsing
   * @param CCprocess apply CC process?
   */
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess, Extras includeExtras) {
    // do nothing as default operation
  }


  /**
   *
   * Destructively applies different enhancements to the dependency graph.
   * <br>
   * Default is no-op; to be over-ridden in subclasses.
   *
   * @param list A list of dependencies
   * @param options Options that determine which enhancements are applied to the dependency graph.
   */
  protected void addEnhancements(List<TypedDependency> list, EnhancementOptions options) {
    // do nothing as default operation
  }

  /**
   * Destructively modify the {@code Collection&lt;TypedDependency&gt;} to collapse
   * language-dependent transitive dependencies but keeping a tree structure.
   * <br>
   * Default is no-op; to be over-ridden in subclasses.
   *
   * @param list A list of dependencies to process for possible collapsing
   *
   */
  protected void collapseDependenciesTree(List<TypedDependency> list) {
    // do nothing as default operation
  }


  /**
   * Destructively modify the {@code TypedDependencyGraph} to correct
   * language-dependent dependencies. (e.g., nsubjpass in a relative clause)
   * <br>
   * Default is no-op; to be over-ridden in subclasses.
   *
   */
  protected void correctDependencies(List<TypedDependency> list) {
    // do nothing as default operation
  }


  /**
   * Checks if all the typeDependencies are connected
   * @param list a list of typedDependencies
   * @return true if the list represents a connected graph, false otherwise
   */
  public static boolean isConnected(Collection<TypedDependency> list) {
    return getRoots(list).size() <= 1; // there should be no more than one root to have a connected graph
                                         // there might be no root in the way we look when you have a relative clause
                                         // ex.: Apple is a society that sells computers
                                         // (the root "society" will also be the nsubj of "sells")
  }

  /**
   * Return a list of TypedDependencies which are not dependent on any node from the list.
   *
   * @param list The list of TypedDependencies to check
   * @return A list of TypedDependencies which are not dependent on any node from the list
   */
  public static Collection<TypedDependency> getRoots(Collection<TypedDependency> list) {

    Collection<TypedDependency> roots = new ArrayList<>();

    // need to see if more than one governor is not listed somewhere as a dependent
    // first take all the deps
    Collection<IndexedWord> deps = Generics.newHashSet();
    for (TypedDependency typedDep : list) {
      deps.add(typedDep.dep());
    }

    // go through the list and add typedDependency for which the gov is not a dep
    Collection<IndexedWord> govs = Generics.newHashSet();
    for (TypedDependency typedDep : list) {
      IndexedWord gov = typedDep.gov();
      if (!deps.contains(gov) && !govs.contains(gov)) {
        roots.add(typedDep);
      }
      govs.add(gov);
    }
    return roots;
  }

  private static final long serialVersionUID = 2286294455343892678L;

  private static class NameComparator<X> implements Comparator<X> {
    @Override
    public int compare(X o1, X o2) {
      String n1 = o1.toString();
      String n2 = o2.toString();
      return n1.compareTo(n2);
    }
  }

  // Note that these field constants are 0-based whereas much documentation is 1-based

  public static final int CoNLLX_WordField = 1;
  public static final int CoNLLX_POSField = 4;
  public static final int CoNLLX_GovField = 6;
  public static final int CoNLLX_RelnField = 7;

  public static final int CoNLLX_FieldCount = 10;

  /**
   * Read in a file containing a CoNLL-X dependency treebank and return a
   * corresponding list of GrammaticalStructures.
   *
   * @throws IOException
   */
  public static List<GrammaticalStructure> readCoNLLXGrammaticalStructureCollection(String fileName, Map<String, GrammaticalRelation> shortNameToGRel, GrammaticalStructureFromDependenciesFactory factory) throws IOException {
    try (BufferedReader r = IOUtils.readerFromString(fileName)) {
      LineNumberReader reader = new LineNumberReader(r);
      List<GrammaticalStructure> gsList = new LinkedList<>();

      List<List<String>> tokenFields = new ArrayList<>();

      for (String inline = reader.readLine(); inline != null;
           inline = reader.readLine()) {
        if (!inline.isEmpty()) {
          // read in a single sentence token by token
          List<String> fields = Arrays.asList(inline.split("\t"));
          if (fields.size() != CoNLLX_FieldCount) {
            throw new RuntimeException(String.format("Error (line %d): 10 fields expected but %d are present", reader.getLineNumber(), fields.size()));
          }
          tokenFields.add(fields);
        } else {
          if (tokenFields.isEmpty())
            continue; // skip excess empty lines

          gsList.add(buildCoNLLXGrammaticalStructure(tokenFields, shortNameToGRel, factory));
          tokenFields = new ArrayList<>();
        }
      }

      return gsList;
    }
  }

  public static GrammaticalStructure buildCoNLLXGrammaticalStructure(List<List<String>> tokenFields,
                                Map<String, GrammaticalRelation> shortNameToGRel,
                                GrammaticalStructureFromDependenciesFactory factory) {
    List<IndexedWord> tgWords = new ArrayList<>(tokenFields.size());
    List<TreeGraphNode> tgPOSNodes = new ArrayList<>(tokenFields.size());

    SemanticHeadFinder headFinder = new SemanticHeadFinder();

    // Construct TreeGraphNodes for words and POS tags
    for (List<String> fields : tokenFields) {
      CoreLabel word = new CoreLabel();
      word.setValue(fields.get(CoNLLX_WordField));
      word.setWord(fields.get(CoNLLX_WordField));
      word.setTag(fields.get(CoNLLX_POSField));
      word.setIndex(tgWords.size() + 1);
      CoreLabel pos = new CoreLabel();
      pos.setTag(fields.get(CoNLLX_POSField));
      pos.setValue(fields.get(CoNLLX_POSField));
      TreeGraphNode wordNode = new TreeGraphNode(word);
      TreeGraphNode posNode =new TreeGraphNode(pos);
      tgWords.add(new IndexedWord(word));
      tgPOSNodes.add(posNode);
      TreeGraphNode[] childArr = { wordNode };
      posNode.setChildren(childArr);
      wordNode.setParent(posNode);
      posNode.percolateHeads(headFinder);
    }

    // We fake up the parts of the tree structure that are not
    // actually used by the grammatical relation transformation
    // operations.
    //
    // That is, the constructed TreeGraphs consist of a flat tree,
    // without any phrase bracketing, but that does preserve the
    // parent child relationship between words and their POS tags.
    //
    // e.g. (ROOT (PRP I) (VBD hit) (DT the) (NN ball) (. .))

    TreeGraphNode root =
      new TreeGraphNode(new Word("ROOT-" + (tgPOSNodes.size() + 1)));
    root.setChildren(tgPOSNodes.toArray(new TreeGraphNode[tgPOSNodes.size()]));

    // Build list of TypedDependencies
    List<TypedDependency> tdeps = new ArrayList<>(tgWords.size());

    // Create a node outside the tree useful for root dependencies;
    // we want to keep those if they were stored in the conll file

    CoreLabel rootLabel = new CoreLabel();
    rootLabel.setValue("ROOT");
    rootLabel.setWord("ROOT");
    rootLabel.setIndex(0);
    IndexedWord dependencyRoot = new IndexedWord(rootLabel);
    for (int i = 0; i < tgWords.size(); i++) {
      String parentIdStr = tokenFields.get(i).get(CoNLLX_GovField);
      if (StringUtils.isNullOrEmpty(parentIdStr)) {
        continue;
      }
      String grelString = tokenFields.get(i).get(CoNLLX_RelnField);
      if (grelString.equals("null") || grelString.equals("erased"))
        continue;
      GrammaticalRelation grel = shortNameToGRel.get(grelString.toLowerCase());
      TypedDependency tdep;
      if (grel == null) {
        if (grelString.toLowerCase().equals("root")) {
          tdep = new TypedDependency(ROOT, dependencyRoot, tgWords.get(i));
        } else {
          throw new RuntimeException("Unknown grammatical relation '" +
                                     grelString + "' fields: " +
                                     tokenFields.get(i) + "\nNode: " +
                                     tgWords.get(i) + '\n' +
                                     "Known Grammatical relations: ["+shortNameToGRel.keySet() + ']');
        }
      } else {
        int parentId = Integer.parseInt(parentIdStr) - 1;
        if (parentId >= tgWords.size()) {
          System.err.printf("Warning: Invalid Parent Id %d Sentence Length: %d%n", parentId+1, tgWords.size());
          System.err.printf("         Assigning to root (0)%n");
          parentId = -1;
        }
        tdep = new TypedDependency(grel, (parentId == -1 ? dependencyRoot : tgWords.get(parentId)),
                                   tgWords.get(i));
      }
      tdeps.add(tdep);
    }
    return factory.build(tdeps, root);
  }



  public static void main(String[] args) {
    /* Language-specific default properties. The default
     * options produce English Universal dependencies.
     * This should be overwritten in every subclass.
     *
     */
    GrammaticalStructureConversionUtils.convertTrees(args, "en");
  }


}
