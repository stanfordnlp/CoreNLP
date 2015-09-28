package edu.stanford.nlp.trees;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.function.Function;

import edu.stanford.nlp.graph.DirectedMultiGraph;
import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.AbstractCoreLabel;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WhitespaceTokenizer;
import edu.stanford.nlp.util.*;
import static edu.stanford.nlp.trees.GrammaticalRelation.DEPENDENT;
import static edu.stanford.nlp.trees.GrammaticalRelation.ROOT;


/**
 * A {@code GrammaticalStructure} stores dependency relations between
 * nodes in a tree.  A new <code>GrammaticalStructure</code> is constructed
 * from an existing parse tree with the help of {@link
 * GrammaticalRelation <code>GrammaticalRelation</code>}, which
 * defines a hierarchy of grammatical relations, along with
 * patterns for identifying them in parse trees.  The constructor for
 * <code>GrammaticalStructure</code> uses these definitions to
 * populate the new <code>GrammaticalStructure</code> with as many
 * labeled grammatical relations as it can.  Once constructed, the new
 * <code>GrammaticalStructure</code> can be printed in various
 * formats, or interrogated using the interface methods in this
 * class. Internally, this uses a representation via a {@code TreeGraphNode},
 * that is, a tree with additional labeled
 * arcs between nodes, for representing the grammatical relations in a
 * parse tree.
 * <p/>
 * <b>Caveat emptor!</b> This is a work in progress.
 * Nothing in here should be relied upon to function perfectly.
 * Feedback welcome.
 *
 * @author Bill MacCartney
 * @author Galen Andrew (refactoring English-specific stuff)
 * @author Ilya Sherman (dependencies)
 * @author Daniel Cer
 * @see EnglishGrammaticalRelations
 * @see GrammaticalRelation
 * @see EnglishGrammaticalStructure
 */
public abstract class GrammaticalStructure implements Serializable {

  private static final boolean PRINT_DEBUGGING = System.getProperty("GrammaticalStructure", null) != null;

  /**
   * A specification for the types of extra edges to add to the dependency tree.
   * If you're in doubt, use {@link edu.stanford.nlp.trees.GrammaticalStructure.Extras#NONE}.
   */
  public static enum Extras {
    /**
     * <p> Don't include any additional edges. </p>
     * <p>
     *   Note: In older code (2014 and before) including extras was a boolean flag. This option is the equivalent of
     *   the <code>false</code> flag.
     * </p>
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
     * <p>
     *   Do the maximal amount of extra processing.
     *   Currently, this is equivalent to {@link edu.stanford.nlp.trees.GrammaticalStructure.Extras#REF_COLLAPSED_AND_SUBJ}.
     * </p>
     * <p>
     *   Note: In older code (2014 and before) including extras was a boolean flag. This option is the equivalent of
     *   the <code>true</code> flag.
     * </p>
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

  }

  protected final List<TypedDependency> typedDependencies;
  protected final List<TypedDependency> allTypedDependencies;

  protected final Predicate<String> puncFilter;
  protected final Predicate<String> tagFilter;

  /**
   * The root Tree node for this GrammaticalStructure.
   */
  protected final TreeGraphNode root;

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
   * @param relationsLock Something needed to make this thread-safe
   * @param transformer   A transformer to apply to the tree before converting
   * @param hf            A HeadFinder for analysis
   * @param puncFilter    A Filter to reject punctuation. To delete punctuation
   *                      dependencies, this filter should return false on
   *                      punctuation word strings, and true otherwise.
   *                      If punctuation dependencies should be kept, you
   *                      should pass in a Filters.&lt;String&gt;acceptFilter().
   */
  public GrammaticalStructure(Tree t, Collection<GrammaticalRelation> relations,
                              Lock relationsLock, TreeTransformer transformer,
                              HeadFinder hf, Predicate<String> puncFilter,
                              Predicate<String> tagFilter) {
    TreeGraphNode treegraph = new TreeGraphNode(t, (TreeGraphNode) null);
    // TODO: create the tree and reuse the leaf labels in one pass,
    // avoiding a wasteful copy of the labels.
    Trees.setLeafLabels(treegraph, t.yield());
    Trees.setLeafTagsIfUnset(treegraph);
    if (transformer != null) {
      Tree transformed = transformer.transformTree(treegraph);
      if (!(transformed instanceof TreeGraphNode)) {
        throw new RuntimeException("Transformer did not change TreeGraphNode into another TreeGraphNode: " + transformer);
      }
      this.root = (TreeGraphNode) transformed;
    } else {
      this.root = treegraph;
    }
    indexNodes(this.root);
    // add head word and tag to phrase nodes
    if (hf == null) {
      throw new AssertionError("Cannot use null HeadFinder");
    }
    root.percolateHeads(hf);
    if (root.value() == null) {
      root.setValue("ROOT");  // todo: cdm: it doesn't seem like this line should be here
    }
    // add dependencies, using heads
    this.puncFilter = puncFilter;
    this.tagFilter = tagFilter;
    // NoPunctFilter puncDepFilter = new NoPunctFilter(puncFilter);
    NoPunctTypedDependencyFilter puncTypedDepFilter = new NoPunctTypedDependencyFilter(puncFilter, tagFilter);

    DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> basicGraph = new DirectedMultiGraph<TreeGraphNode, GrammaticalRelation>();
    DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> completeGraph = new DirectedMultiGraph<TreeGraphNode, GrammaticalRelation>();

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
   * <code>Tree</code>.  The leaves are indexed first,
   * from left to right.  Then the internal nodes are indexed,
   * using a pre-order tree traversal.
   */
  private void indexNodes(TreeGraphNode tree) {
    indexNodes(tree, indexLeaves(tree, 1));
  }

  /**
   * Assign sequential integer indices to the leaves of the subtree
   * rooted at this <code>TreeGraphNode</code>, beginning with
   * <code>startIndex</code>, and traversing the leaves from left
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
   * rooted at this <code>TreeGraphNode</code>, beginning with
   * <code>startIndex</code>, and doing a pre-order tree traversal.
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
   * <code>TreeGraph</code> constructor.
   *
   * @param index the arbitrary integer index
   * @param node  the <code>TreeGraphNode</code> to be indexed
   */
  private void addNodeToIndexMap(int index, TreeGraphNode node) {
    indexMap.put(Integer.valueOf(index), node);
  }


  /**
   * Return the node in the this treegraph corresponding to the
   * specified integer index.
   *
   * @param index the integer index of the node you want
   * @return the <code>TreeGraphNode</code> having the specified
   *         index (or <code>null</code> if such does not exist)
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

    List<TreeGraphNode> tgWordNodes = new ArrayList<TreeGraphNode>(tokens.size());
    List<TreeGraphNode> tgPOSNodes = new ArrayList<TreeGraphNode>(tokens.size());

    CoreLabel rootLabel = new CoreLabel();
    rootLabel.setValue("ROOT");
    List<IndexedWord> nodeWords = new ArrayList<IndexedWord>(tgPOSNodes.size() + 1);
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
    List<TypedDependency> tdeps = new ArrayList<TypedDependency>(deps.size());

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
    allTypedDependencies = typedDependencies = new ArrayList<TypedDependency>(projectiveDependencies);
  }

  public GrammaticalStructure(Tree t, Collection<GrammaticalRelation> relations,
                              HeadFinder hf, Predicate<String> puncFilter, Predicate<String> tagFilter) {
    this(t, relations, null, null, hf, puncFilter, tagFilter);
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
              // System.err.println("Adding " + egr.getShortName() + " from " + t + " to " + u + " tHigh=" + tHigh + "(" + tHigh.headWordNode() + ") uHigh=" + uHigh + "(" + uHigh.headWordNode() + ")");
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
        // System.err.println("  Gov: " + gov + " Dep: " + dep + " Reln: " + reln);
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

    // Automatically generated by Eclipse
    private static final long serialVersionUID = -2872766864289207468L;
  } // end static class NoPunctTypedDependencyFilter


  /**
   * Get GrammaticalRelation between gov and dep, and null if gov  is not the
   * governor of dep
   */
  public GrammaticalRelation getGrammaticalRelation(int govIndex, int depIndex) {
    TreeGraphNode gov = getNodeByIndex(govIndex);
    TreeGraphNode dep = getNodeByIndex(depIndex);
    // TODO: this is pretty ugly
    return getGrammaticalRelation(new IndexedWord(gov.label()), new IndexedWord(dep.label()));
  }

  /**
   * Get GrammaticalRelation between gov and dep, and null if gov is not the
   * governor of dep
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
      sortedLabels = new ArrayList<GrammaticalRelation>(labels);
      Collections.sort(sortedLabels, new NameComparator<GrammaticalRelation>());
    }
    // System.err.println(" gov " + govH + " dep " + depH + " arc labels: " + sortedLabels);

    for (GrammaticalRelation reln2 : sortedLabels) {
      if (reln.isAncestor(reln2)) {
        reln = reln2;
      } else if (PRINT_DEBUGGING && ! reln2.isAncestor(reln)) {
        System.err.println("@@@\t" + reln + "\t" + reln2 + "\t" +
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
      System.err.println("### dep\t" + topCat + "\t" + topTag + "\t" + topWord +
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
    List<TypedDependency> deps;
    // This copy has to be done because of the broken way
    // TypedDependency objects can be mutated by downstream methods
    // such as collapseDependencies.  Without the copy here it is
    // possible for two consecutive calls to
    // typedDependenciesCollapsed to get different results.  For
    // example, the English dependencies rename existing objects KILL
    // to note that they should be removed.
    if (includeExtras != Extras.NONE) {
      deps = new ArrayList<TypedDependency>(allTypedDependencies.size());
      for (TypedDependency dep : allTypedDependencies) {
        deps.add(new TypedDependency(dep));
      }
    } else {
      deps = new ArrayList<TypedDependency>(typedDependencies.size());
      for (TypedDependency dep : typedDependencies) {
        deps.add(new TypedDependency(dep));
      }
    }
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
   * Destructively modify the <code>Collection&lt;TypedDependency&gt;</code> to collapse
   * language-dependent transitive dependencies.
   * <p/>
   * Default is no-op; to be over-ridden in subclasses.
   *
   * @param list A list of dependencies to process for possible collapsing
   * @param CCprocess apply CC process?
   */
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess, Extras includeExtras) {
    // do nothing as default operation
  }

  /**
   * Destructively modify the <code>Collection&lt;TypedDependency&gt;</code> to collapse
   * language-dependent transitive dependencies but keeping a tree structure.
   * <p/>
   * Default is no-op; to be over-ridden in subclasses.
   *
   * @param list A list of dependencies to process for possible collapsing
   *
   */
  protected void collapseDependenciesTree(List<TypedDependency> list) {
    // do nothing as default operation
  }


  /**
   * Destructively modify the <code>TypedDependencyGraph</code> to correct
   * language-dependent dependencies. (e.g., nsubjpass in a relative clause)
   * <p/>
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

    Collection<TypedDependency> roots = new ArrayList<TypedDependency>();

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


  public static final String DEFAULT_PARSER_FILE = "/u/nlp/data/lexparser/englishPCFG.ser.gz";

  /**
   * Print typed dependencies in either the Stanford dependency representation
   * or in the conllx format.
   *
   * @param deps
   *          Typed dependencies to print
   * @param tree
   *          Tree corresponding to typed dependencies (only necessary if conllx
   *          == true)
   * @param conllx
   *          If true use conllx format, otherwise use Stanford representation
   * @param extraSep
   *          If true, in the Stanford representation, the extra dependencies
   *          (which do not preserve the tree structure) are printed after the
   *          basic dependencies
   */
  public static void printDependencies(GrammaticalStructure gs, Collection<TypedDependency> deps, Tree tree, boolean conllx, boolean extraSep) {
    System.out.println(dependenciesToString(gs, deps, tree, conllx, extraSep));
  }

  
  /**
   * Calls dependenciesToCoNLLXString with the basic dependencies 
   * from a grammatical structure.
   * 
   * (see {@link #dependenciesToCoNLLXString(Collection, CoreMap)})
   */
  public static String dependenciesToCoNLLXString(GrammaticalStructure gs, CoreMap sentence) {
    return dependenciesToCoNLLXString(gs.typedDependencies(), sentence);
  }
  
  
  /**
   *
   * Returns a dependency tree in CoNNL-X format.
   * It requires a CoreMap for the sentence with a TokensAnnotation.
   * Each token has to contain a word and a POS tag.
   *
   * @param deps The list of TypedDependency relations.
   * @param sentence The corresponding CoreMap for the sentence.
   * @return Dependency tree in CoNLL-X format.
   */
  public static String dependenciesToCoNLLXString(Collection<TypedDependency> deps, CoreMap sentence) {
    StringBuilder bf = new StringBuilder();

    HashMap<Integer, TypedDependency> indexedDeps = new HashMap<Integer, TypedDependency>(deps.size());
    for (TypedDependency dep : deps) {
      indexedDeps.put(dep.dep().index(), dep);
    }

    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tokens == null) {
      throw new RuntimeException("dependenciesToCoNLLXString: CoreMap does not have required TokensAnnotation.");
    }
    int idx = 1;

    for (CoreLabel token : tokens) {
      String word = token.value();
      String pos = token.tag();
      String cPos = (token.get(CoreAnnotations.CoarseTagAnnotation.class) != null) ?
          token.get(CoreAnnotations.CoarseTagAnnotation.class) : pos;
      String lemma = token.lemma() != null ? token.lemma() : "_";
      Integer gov = indexedDeps.containsKey(idx) ? indexedDeps.get(idx).gov().index() : 0;
      String reln = indexedDeps.containsKey(idx) ? indexedDeps.get(idx).reln().toString() : "erased";
      String out = String.format("%d\t%s\t%s\t%s\t%s\t_\t%d\t%s\t_\t_\n", idx, word, lemma, cPos, pos, gov, reln);
      bf.append(out);
      idx++;
    }
    return bf.toString();
  }

  public static String dependenciesToString(GrammaticalStructure gs, Collection<TypedDependency> deps, Tree tree, boolean conllx, boolean extraSep) {
    StringBuilder bf = new StringBuilder();

    Map<Integer, Integer> indexToPos = Generics.newHashMap();
    indexToPos.put(0,0); // to deal with the special node "ROOT"
    List<Tree> gsLeaves = gs.root.getLeaves();
    for (int i = 0; i < gsLeaves.size(); i++) {
      TreeGraphNode leaf = (TreeGraphNode) gsLeaves.get(i);
      indexToPos.put(leaf.label.index(), i + 1);
    }

    if (conllx) {

      List<Tree> leaves = tree.getLeaves();
      Tree uposTree = UniversalPOSMapper.mapTree(tree);
      List<Label> uposLabels = uposTree.preTerminalYield();

      int index = 0;
      CoreMap sentence = new CoreLabel();
      List<CoreLabel> tokens = new ArrayList<CoreLabel>(leaves.size());
      for (Tree leaf : leaves) {
        index++;
        if (!indexToPos.containsKey(index)) {
          continue;
        }
        CoreLabel token = new CoreLabel();
        token.setIndex(index);
        token.setValue(leaf.value());
        token.setWord(leaf.value());
        token.setTag(leaf.parent(tree).value());
        token.set(CoreAnnotations.CoarseTagAnnotation.class, uposLabels.get(index - 1).value());
        tokens.add(token);
      }
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
      bf.append(dependenciesToCoNLLXString(deps, sentence));
    } else {
      if (extraSep) {
        List<TypedDependency> extraDeps = new ArrayList<TypedDependency>();
        for (TypedDependency dep : deps) {
          if (dep.extra()) {
            extraDeps.add(dep);
          } else {
            bf.append(toStringIndex(dep, indexToPos));
            bf.append("\n");
          }
        }
        // now we print the separator for extra dependencies, and print these if
        // there are some
        if (!extraDeps.isEmpty()) {
          bf.append("======\n");
          for (TypedDependency dep : extraDeps) {
            bf.append(toStringIndex(dep, indexToPos));
            bf.append("\n");
          }
        }
      } else {
        for (TypedDependency dep : deps) {
          bf.append(toStringIndex(dep, indexToPos));
          bf.append("\n");
        }
      }
    }

    return bf.toString();
  }

  private static String toStringIndex(TypedDependency td, Map<Integer, Integer> indexToPos) {
    IndexedWord gov = td.gov();
    IndexedWord dep = td.dep();
    return td.reln() + "(" + gov.value() + "-" + indexToPos.get(gov.index()) + gov.toPrimes() + ", " + dep.value() + "-" + indexToPos.get(dep.index()) + dep.toPrimes() + ")";
  }


  // Note that these field constants are 0-based whereas much documentation is 1-based

  public static final int CoNLLX_WordField = 1;
  public static final int CoNLLX_POSField = 3;
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
    LineNumberReader reader = new LineNumberReader(IOUtils.readerFromString(fileName));
    List<GrammaticalStructure> gsList = new LinkedList<GrammaticalStructure>();

    List<List<String>> tokenFields = new ArrayList<List<String>>();

    for (String inline = reader.readLine(); inline != null;
         inline = reader.readLine()) {
      if ( ! inline.isEmpty()) {
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
        tokenFields = new ArrayList<List<String>>();
      }
    }

    return gsList;
  }

  public static GrammaticalStructure
  buildCoNLLXGrammaticalStructure(List<List<String>> tokenFields,
                                Map<String, GrammaticalRelation> shortNameToGRel,
                                GrammaticalStructureFromDependenciesFactory factory) {
    List<IndexedWord> tgWords = new ArrayList<IndexedWord>(tokenFields.size());
    List<TreeGraphNode> tgPOSNodes = new ArrayList<TreeGraphNode>(tokenFields.size());

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
    List<TypedDependency> tdeps = new ArrayList<TypedDependency>(tgWords.size());

    // Create a node outside the tree useful for root dependencies;
    // we want to keep those if they were stored in the conll file

    CoreLabel rootLabel = new CoreLabel();
    rootLabel.setValue("ROOT");
    rootLabel.setWord("ROOT");
    rootLabel.setIndex(0);
    IndexedWord dependencyRoot = new IndexedWord(rootLabel);
    for (int i = 0; i < tgWords.size(); i++) {
      String parentIdStr = tokenFields.get(i).get(CoNLLX_GovField);
      if (parentIdStr == null || parentIdStr.equals(""))
        continue;
      int parentId = Integer.parseInt(parentIdStr) - 1;
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
                                     tgWords.get(i) + "\n" +
                                     "Known Grammatical relations: ["+shortNameToGRel.keySet()+"]" );
        }
      } else {
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


  private static String[] parseClassConstructArgs(String namePlusArgs) {
    String[] args = StringUtils.EMPTY_STRING_ARRAY;
    String name = namePlusArgs;
    if (namePlusArgs.matches(".*\\([^)]*\\)$")) {
      String argStr = namePlusArgs.replaceFirst("^.*\\(([^)]*)\\)$", "$1");
      args = argStr.split(",");
      name = namePlusArgs.replaceFirst("\\([^)]*\\)$", "");
    }
    String[] tokens = new String[1 + args.length];
    tokens[0] = name;
    System.arraycopy(args, 0, tokens, 1, args.length);
    return tokens;
  }


  private static DependencyReader loadAlternateDependencyReader(String altDepReaderName) {
    Class<? extends DependencyReader> altDepReaderClass = null;
    String[] toks = parseClassConstructArgs(altDepReaderName);
    altDepReaderName = toks[0];
    String[] depReaderArgs = new String[toks.length - 1];
    System.arraycopy(toks, 1, depReaderArgs, 0, toks.length - 1);

    try {
      Class<?> cl = Class.forName(altDepReaderName);
      altDepReaderClass = cl.asSubclass(DependencyReader.class);
    } catch (ClassNotFoundException e) {
      // have a second go below
    }
    if (altDepReaderClass == null) {
      try {
        Class<?> cl = Class.forName("edu.stanford.nlp.trees." + altDepReaderName);
        altDepReaderClass = cl.asSubclass(DependencyReader.class);
      } catch (ClassNotFoundException e) {
          //
      }
    }
    if (altDepReaderClass == null) {
      System.err.println("Can't load dependency reader " + altDepReaderName + " or edu.stanford.nlp.trees." + altDepReaderName);
      return null;
    }

    DependencyReader altDepReader; // initialized below
    if (depReaderArgs.length == 0) {
      try {
        altDepReader = altDepReaderClass.newInstance();
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        System.err.println("No argument constructor to " + altDepReaderName + " is not public");
        return null;
      }
    } else {
      try {
        altDepReader = altDepReaderClass.getConstructor(String[].class).newInstance((Object) depReaderArgs);
      } catch (IllegalArgumentException | SecurityException | InvocationTargetException e) {
        throw new RuntimeException(e);
      } catch (InstantiationException e) {
        e.printStackTrace();
        return null;
      } catch (IllegalAccessException e) {
        System.err.println(depReaderArgs.length + " argument constructor to " + altDepReaderName + " is not public.");
        return null;
      } catch (NoSuchMethodException e) {
        System.err.println("String arguments constructor to " + altDepReaderName + " does not exist.");
        return null;
      }
    }
    return altDepReader;
  }


  private static DependencyPrinter loadAlternateDependencyPrinter(String altDepPrinterName) {
    Class<? extends DependencyPrinter> altDepPrinterClass = null;
    String[] toks = parseClassConstructArgs(altDepPrinterName);
    altDepPrinterName = toks[0];
    String[] depPrintArgs = new String[toks.length - 1];
    System.arraycopy(toks, 1, depPrintArgs, 0, toks.length - 1);

    try {
      Class<?> cl = Class.forName(altDepPrinterName);
      altDepPrinterClass = cl.asSubclass(DependencyPrinter.class);
    } catch (ClassNotFoundException e) {
      //
    }
    if (altDepPrinterClass == null) {
      try {
        Class<?> cl = Class.forName("edu.stanford.nlp.trees." + altDepPrinterName);
        altDepPrinterClass = cl.asSubclass(DependencyPrinter.class);
      } catch (ClassNotFoundException e) {
        //
      }
    }
    if (altDepPrinterClass == null) {
      System.err.printf("Unable to load alternative printer %s or %s. Is your classpath set correctly?\n", altDepPrinterName, "edu.stanford.nlp.trees." + altDepPrinterName);
      return null;
    }
    try {
      DependencyPrinter depPrinter;
      if (depPrintArgs.length == 0) {
        depPrinter = altDepPrinterClass.newInstance();
      } else {
        depPrinter = altDepPrinterClass.getConstructor(String[].class).newInstance((Object) depPrintArgs);
      }
      return depPrinter;
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
      return null;
    } catch (SecurityException e) {
      e.printStackTrace();
      return null;
    } catch (InstantiationException e) {
      e.printStackTrace();
      return null;
    } catch (IllegalAccessException e) {
      e.printStackTrace();
      return null;
    } catch (InvocationTargetException e) {
      e.printStackTrace();
      return null;
    } catch (NoSuchMethodException e) {
      if (depPrintArgs.length == 0) {
        System.err.printf("Can't find no-argument constructor %s().%n", altDepPrinterName);
      } else {
        System.err.printf("Can't find constructor %s(%s).%n", altDepPrinterName, Arrays.toString(depPrintArgs));
      }
      return null;
    }
  }

  private static Function<List<? extends HasWord>, Tree> loadParser(String parserFile, String parserOptions, boolean makeCopulaHead) {
    if (parserFile == null || "".equals(parserFile)) {
      parserFile = DEFAULT_PARSER_FILE;
      if (parserOptions == null) {
        parserOptions = "-retainTmpSubcategories";
      }
    }
    if (parserOptions == null) {
      parserOptions = "";
    }
    if (makeCopulaHead) {
      parserOptions = "-makeCopulaHead " + parserOptions;
    }
    parserOptions = parserOptions.trim();
    // Load parser by reflection, so that this class doesn't require parser
    // for runtime use
    // LexicalizedParser lp = LexicalizedParser.loadModel(parserFile);
    // For example, the tregex package uses TreePrint, which uses
    // GrammaticalStructure, which would then import the
    // LexicalizedParser.  The tagger can read trees, which means it
    // would depend on tregex and therefore depend on the parser.
    Function<List<? extends HasWord>, Tree> lp;
    try {
      Class<?>[] classes = new Class<?>[] { String.class, String[].class };
      Method method = Class.forName("edu.stanford.nlp.parser.lexparser.LexicalizedParser").getMethod("loadModel", classes);
      String[] opts = {};
      if (parserOptions.length() > 0) {
        opts = parserOptions.split(" +");
      }
      lp = (Function<List<? extends HasWord>,Tree>) method.invoke(null, parserFile, opts);
    } catch (Exception cnfe) {
      throw new RuntimeException(cnfe);
    }
    return lp;
  }

  /**
   * Allow a collection of trees, that is a Treebank, appear to be a collection
   * of GrammaticalStructures.
   *
   * @author danielcer
   *
   */
  private static class TreeBankGrammaticalStructureWrapper implements Iterable<GrammaticalStructure> {

    private final Iterable<Tree> trees;
    private final boolean keepPunct;
    private final TreebankLangParserParams params;

    private final Map<GrammaticalStructure, Tree> origTrees = new WeakHashMap<GrammaticalStructure, Tree>();

    public TreeBankGrammaticalStructureWrapper(Iterable<Tree> wrappedTrees, boolean keepPunct, TreebankLangParserParams params) {
      trees = wrappedTrees;
      this.keepPunct = keepPunct;
      this.params = params;
    }

    @Override
    public Iterator<GrammaticalStructure> iterator() {
      return new GsIterator();
    }

    public Tree getOriginalTree(GrammaticalStructure gs) {
      return origTrees.get(gs);
    }


    private class GsIterator implements Iterator<GrammaticalStructure> {

      private final Iterator<Tree> tbIterator = trees.iterator();
      private final Predicate<String> puncFilter;
      private final HeadFinder hf;
      private GrammaticalStructure next;

      public GsIterator() {
        if (keepPunct) {
          puncFilter = Filters.acceptFilter();
        } else if (params.generateOriginalDependencies()) {
          puncFilter = params.treebankLanguagePack().punctuationWordRejectFilter();
        } else {
          puncFilter = params.treebankLanguagePack().punctuationTagRejectFilter();
        }
        hf = params.typedDependencyHeadFinder();
        primeGs();
      }

      private void primeGs() {
        GrammaticalStructure gs = null;
        while (gs == null && tbIterator.hasNext()) {
          Tree t = tbIterator.next();
          // System.err.println("GsIterator: Next tree is");
          // System.err.println(t);
          if (t == null) {
            continue;
          }
          try {
            gs = params.getGrammaticalStructure(t, puncFilter, hf);
            origTrees.put(gs, t);
            next = gs;
            // System.err.println("GsIterator: Next tree is");
            // System.err.println(t);
            return;
          } catch (NullPointerException npe) {
            System.err.println("Bung tree caused below dump. Continuing....");
            System.err.println(t);
            npe.printStackTrace();
          }
        }
        next = null;
      }

      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public GrammaticalStructure next() {
        GrammaticalStructure ret = next;
        if (ret == null) {
          throw new NoSuchElementException();
        }
        primeGs();
        return ret;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

    }
  } // end static class TreebankGrammaticalStructureWrapper


  /**
   * Given sentences or trees, output the typed dependencies.
   * <p>

   * By default, the method outputs the collapsed typed dependencies with
   * processing of conjuncts. The input can be given as plain text (one sentence
   * by line) using the option -sentFile, or as trees using the option
   * -treeFile. For -sentFile, the input has to be strictly one sentence per
   * line. You can specify where to find a parser with -parserFile
   * serializedParserPath. See LexicalizedParser for more flexible processing of
   * text files (including with Stanford Dependencies output). The above options
   * assume a file as input. You can also feed trees (only) via stdin by using
   * the option -filter.  If one does not specify a -parserFile, one
   * can specify which language pack to use with -tLPP, This option
   * specifies a class which determines which GrammaticalStructure to
   * use, which HeadFinder to use, etc.  It will default to
   * edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams,
   * but any TreebankLangParserParams can be specified.
   * <p>
   * If no method of producing trees is given other than to use the
   * LexicalizedParser, but no parser is specified, a default parser
   * is used, the English parser.  You can specify options to load
   * with the parser using the -parserOpts flag.  If the default
   * parser is used, and no options are provided, the option
   * -retainTmpSubcategories is used.
   * <p>
   * The following options can be used to specify the types of dependencies
   * wanted: </p>
   * <ul>
   * <li> -collapsed collapsed dependencies
   * <li> -basic non-collapsed dependencies that preserve a tree structure
   * <li> -nonCollapsed non-collapsed dependencies that do not preserve a tree
   * structure (the basic dependencies plus the extra ones)
   * <li> -CCprocessed
   * collapsed dependencies and conjunctions processed (dependencies are added
   * for each conjunct) -- this is the default if no options are passed
   * <li> -collapsedTree collapsed dependencies retaining a tree structure
   * <li> -makeCopulaHead Contrary to the approach argued for in the SD papers,
   *  nevertheless make the verb 'to be' the head, not the predicate noun, adjective,
   *  etc. (However, when the verb 'to be' is used as an auxiliary verb, the main
   *  verb is still treated as the head.)
   * <li> -originalDependencies generate the dependencies using the original converter
   * instead of the Universal Dependencies converter.
   * </ul>
   * <p>
   * The {@code -conllx} option will output the dependencies in the CoNLL format,
   * instead of in the standard Stanford format (relation(governor,dependent))
   * and will retain punctuation by default.
   * When used in the "collapsed" format, words such as prepositions, conjunctions
   * which get collapsed into the grammatical relations and are not part of the
   * sentence per se anymore will be annotated with "erased" as grammatical relation
   * and attached to the fake "ROOT" node with index 0.
   * <p/><p>
   * There is also an option to retain dependencies involving punctuation:
   * {@code -keepPunct}
   * </p><p>
   * The {@code -extraSep} option used with -nonCollapsed will print the basic
   * dependencies first, then a separator ======, and then the extra
   * dependencies that do not preserve the tree structure. The -test option is
   * used for debugging: it prints the grammatical structure, as well as the
   * basic, collapsed and CCprocessed dependencies. It also checks the
   * connectivity of the collapsed dependencies. If the collapsed dependencies
   * list doesn't constitute a connected graph, it prints the possible offending
   * nodes (one of them is the real root of the graph).
   * </p><p>
   * Using the -conllxFile, you can pass a file containing Stanford dependencies
   * in the CoNLL format (e.g., the basic dependencies), and obtain another
   * representation using one of the representation options.
   * </p><p>
   * Usage: <br>
   * <code>java edu.stanford.nlp.trees.GrammaticalStructure [-treeFile FILE | -sentFile FILE | -conllxFile FILE | -filter] <br>
   * [-collapsed -basic -CCprocessed -test -generateOriginalDependencies]</code>
   *
   * @param args Command-line arguments, as above
   */
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {

    // System.out.print("GrammaticalRelations under DEPENDENT:");
    // System.out.println(DEPENDENT.toPrettyString());

    /* Use a tree normalizer that removes all empty nodes. 
       This prevents wrong indexing of the nodes in the dependency relations.*/
    MemoryTreebank tb = new MemoryTreebank(new NPTmpRetainingTreeNormalizer(0, false, 1, false));
    Iterable<Tree> trees = tb;

    Iterable<GrammaticalStructure> gsBank = null;
    Properties props = StringUtils.argsToProperties(args);

    String encoding = props.getProperty("encoding", "utf-8");
    try {
      System.setOut(new PrintStream(System.out, true, encoding));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    String treeFileName = props.getProperty("treeFile");
    String sentFileName = props.getProperty("sentFile");
    String conllXFileName = props.getProperty("conllxFile");
    String altDepPrinterName = props.getProperty("altprinter");
    String altDepReaderName = props.getProperty("altreader");
    String altDepReaderFilename = props.getProperty("altreaderfile");

    String filter = props.getProperty("filter");

    boolean makeCopulaHead = props.getProperty("makeCopulaHead") != null;
    boolean generateOriginalDependencies = props.getProperty("originalDependencies") != null;

    // TODO: if a parser is specified, load this from the parser
    // instead of ever loading it from this way
    String tLPP = props.getProperty("tLPP", "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams");
    TreebankLangParserParams params = ReflectionLoading.loadByReflection(tLPP);
    if (generateOriginalDependencies) {
      params.setGenerateOriginalDependencies(true);
    }
    if (makeCopulaHead) {
      // TODO: generalize and allow for more options
      String[] options = { "-makeCopulaHead" };
      params.setOptionFlag(options, 0);
    }

    if (sentFileName == null && (altDepReaderName == null || altDepReaderFilename == null) && treeFileName == null && conllXFileName == null && filter == null) {
      try {
        System.err.println("Usage: java GrammaticalStructure [options]* [-sentFile|-treeFile|-conllxFile file] [-testGraph]");
        System.err.println("  options: -basic, -collapsed, -CCprocessed [the default], -collapsedTree, -parseTree, -test, -parserFile file, -conllx, -keepPunct, -altprinter -altreader -altreaderfile -originalDependencies");
        TreeReader tr = new PennTreeReader(new StringReader("((S (NP (NNP Sam)) (VP (VBD died) (NP-TMP (NN today)))))"));
        tb.add(tr.readTree());
      } catch (Exception e) {
        System.err.println("Horrible error: " + e);
        e.printStackTrace();
      }
    } else if (altDepReaderName != null && altDepReaderFilename != null) {
      DependencyReader altDepReader = loadAlternateDependencyReader(altDepReaderName);
      try {
        gsBank = altDepReader.readDependencies(altDepReaderFilename);
      } catch (IOException e) {
        System.err.println("Error reading " + altDepReaderFilename);
        return;
      }
    } else if (treeFileName != null) {
      tb.loadPath(treeFileName);
    } else if (filter != null) {
      tb.load(IOUtils.readerFromStdin());
    } else if (conllXFileName != null) {
      try {
        gsBank = params.readGrammaticalStructureFromFile(conllXFileName);
      } catch (RuntimeIOException e) {
        System.err.println("Error reading " + conllXFileName);
        return;
      }
    } else {
      String parserFile = props.getProperty("parserFile");
      String parserOpts = props.getProperty("parserOpts");
      boolean tokenized = props.getProperty("tokenized") != null;
      Function<List<? extends HasWord>, Tree> lp = loadParser(parserFile, parserOpts, makeCopulaHead);
      trees = new LazyLoadTreesByParsing(sentFileName, encoding, tokenized, lp);

      // Instead of getting this directly from the LP, use reflection
      // so that a package which uses GrammaticalStructure doesn't
      // necessarily have to use LexicalizedParser
      try {
        Method method = lp.getClass().getMethod("getTLPParams");
        params = (TreebankLangParserParams) method.invoke(lp);
      } catch (Exception cnfe) {
        throw new RuntimeException(cnfe);
      }
    }

    // treats the output according to the options passed
    boolean basic = props.getProperty("basic") != null;
    boolean collapsed = props.getProperty("collapsed") != null;
    boolean CCprocessed = props.getProperty("CCprocessed") != null;
    boolean collapsedTree = props.getProperty("collapsedTree") != null;
    boolean nonCollapsed = props.getProperty("nonCollapsed") != null;
    boolean extraSep = props.getProperty("extraSep") != null;
    boolean parseTree = props.getProperty("parseTree") != null;
    boolean test = props.getProperty("test") != null;
    boolean keepPunct = props.getProperty("keepPunct") != null;
    boolean conllx = props.getProperty("conllx") != null;
    // todo: Support checkConnected on more options (including basic)
    boolean checkConnected = props.getProperty("checkConnected") != null;
    boolean portray = props.getProperty("portray") != null;

    // enforce keepPunct if conllx is turned on
    if(conllx) {
      keepPunct = true;
    }

    // If requested load alternative printer
    DependencyPrinter altDepPrinter = null;
    if (altDepPrinterName != null) {
      altDepPrinter = loadAlternateDependencyPrinter(altDepPrinterName);
    }

    // System.err.println("First tree in tb is");
    // System.err.println(((MemoryTreebank) tb).get(0));

    Method m = null;
    if (test) {
      // see if we can use SemanticGraph(Factory) to check for being a DAG
      // Do this by reflection to avoid this becoming a dependency when we distribute the parser
      try {
        Class sgf = Class.forName("edu.stanford.nlp.semgraph.SemanticGraphFactory");
        m = sgf.getDeclaredMethod("makeFromTree", GrammaticalStructure.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, Predicate.class, String.class, int.class);
      } catch (Exception e) {
        System.err.println("Test cannot check for cycles in tree format (classes not available)");
      }
    }

    if (gsBank == null) {
      gsBank = new TreeBankGrammaticalStructureWrapper(trees, keepPunct, params);
    }

    for (GrammaticalStructure gs : gsBank) {

      Tree tree;
      if (gsBank instanceof TreeBankGrammaticalStructureWrapper) {
        // System.err.println("Using TreeBankGrammaticalStructureWrapper branch");
        tree = ((TreeBankGrammaticalStructureWrapper) gsBank).getOriginalTree(gs);
        // System.err.println("Tree is: ");
        // System.err.println(t);
      } else {
        // System.err.println("Using gs.root() branch");
        tree = gs.root(); // recover tree
        // System.err.println("Tree from gs is");
        // System.err.println(t);
      }

      if (test) { // print the grammatical structure, the basic, collapsed and CCprocessed

        System.out.println("============= parse tree =======================");
        tree.pennPrint();
        System.out.println();

        System.out.println("------------- GrammaticalStructure -------------");
        System.out.println(gs);

        boolean allConnected = true;
        boolean connected;
        Collection<TypedDependency> bungRoots = null;
        System.out.println("------------- basic dependencies ---------------");
        List<TypedDependency> gsb = gs.typedDependencies(Extras.NONE);
        System.out.println(StringUtils.join(gsb, "\n"));
        connected = GrammaticalStructure.isConnected(gsb);
        if ( ! connected && bungRoots == null) {
          bungRoots = GrammaticalStructure.getRoots(gsb);
        }
        allConnected = connected && allConnected;

        System.out.println("------------- non-collapsed dependencies (basic + extra) ---------------");
        List<TypedDependency> gse = gs.typedDependencies(Extras.MAXIMAL);
        System.out.println(StringUtils.join(gse, "\n"));
        connected = GrammaticalStructure.isConnected(gse);
        if ( ! connected && bungRoots == null) {
          bungRoots = GrammaticalStructure.getRoots(gse);
        }
        allConnected = connected && allConnected;

        System.out.println("------------- collapsed dependencies -----------");
        System.out.println(StringUtils.join(gs.typedDependenciesCollapsed(Extras.MAXIMAL), "\n"));

        System.out.println("------------- collapsed dependencies tree -----------");
        System.out.println(StringUtils.join(gs.typedDependenciesCollapsedTree(), "\n"));

        System.out.println("------------- CCprocessed dependencies --------");
        List<TypedDependency> gscc = gs.typedDependenciesCollapsed(Extras.MAXIMAL);
        System.out.println(StringUtils.join(gscc, "\n"));

        System.out.println("-----------------------------------------------");
        // connectivity tests
        connected = GrammaticalStructure.isConnected(gscc);
        if ( ! connected && bungRoots == null) {
          bungRoots = GrammaticalStructure.getRoots(gscc);
        }
        allConnected = connected && allConnected;
        if (allConnected) {
          System.out.println("dependencies form connected graphs.");
        } else {
          System.out.println("dependency graph NOT connected! possible offending nodes: " + bungRoots);
        }

        // test for collapsed dependencies being a tree:
        // make sure at least it doesn't contain cycles (i.e., is a DAG)
        // Do this by reflection so parser doesn't need SemanticGraph and its
        // libraries
        if (m != null) {
          try {
            // the first arg is null because it's a static method....
            Object semGraph = m.invoke(null, gs, false, true, false, false, false, false, null, null, 0);
            Class sg = Class.forName("edu.stanford.nlp.semgraph.SemanticGraph");
            Method mDag = sg.getDeclaredMethod("isDag");
            boolean isDag = (Boolean) mDag.invoke(semGraph);

            System.out.println("tree dependencies form a DAG: " + isDag);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }// end of "test" output

      else {
        if (parseTree) {
          System.out.println("============= parse tree =======================");
          tree.pennPrint();
          System.out.println();
        }

        if (basic) {
          if (collapsed || CCprocessed || collapsedTree || nonCollapsed) {
            System.out.println("------------- basic dependencies ---------------");
          }
          if (altDepPrinter == null) {
            printDependencies(gs, gs.typedDependencies(Extras.NONE), tree, conllx, false);
          } else {
            System.out.println(altDepPrinter.dependenciesToString(gs, gs.typedDependencies(Extras.NONE), tree));
          }
        }

        if (nonCollapsed) {
          if (basic || CCprocessed || collapsed || collapsedTree) {
            System.out.println("----------- non-collapsed dependencies (basic + extra) -----------");
          }
          printDependencies(gs, gs.allTypedDependencies(), tree, conllx, extraSep);
        }

        if (collapsed) {
          if (basic || CCprocessed || collapsedTree || nonCollapsed) {
            System.out.println("----------- collapsed dependencies -----------");
          }
          printDependencies(gs, gs.typedDependenciesCollapsed(Extras.MAXIMAL), tree, conllx, false);
        }

        if (CCprocessed) {
          if (basic || collapsed || collapsedTree || nonCollapsed) {
            System.out.println("---------- CCprocessed dependencies ----------");
          }
          List<TypedDependency> deps = gs.typedDependenciesCCprocessed(Extras.MAXIMAL);
          if (checkConnected) {
            if (!GrammaticalStructure.isConnected(deps)) {
              System.err.println("Graph is not connected for:");
              System.err.println(tree);
              System.err.println("possible offending nodes: " + GrammaticalStructure.getRoots(deps));
            }
          }
          printDependencies(gs, deps, tree, conllx, false);
        }

        if (collapsedTree) {
          if (basic || CCprocessed || collapsed || nonCollapsed) {
            System.out.println("----------- collapsed dependencies tree -----------");
          }
          printDependencies(gs, gs.typedDependenciesCollapsedTree(), tree, conllx, false);
        }

        // default use: CCprocessed (to parallel what happens within the parser)
        if (!basic && !collapsed && !CCprocessed && !collapsedTree && !nonCollapsed) {
          // System.out.println("----------- CCprocessed dependencies -----------");
          printDependencies(gs, gs.typedDependenciesCCprocessed(Extras.MAXIMAL), tree, conllx, false);
        }
      }

      if (portray) {
        try {
          // put up a window showing it
          Class sgu = Class.forName("edu.stanford.nlp.semgraph.SemanticGraphUtils");
          Method mRender = sgu.getDeclaredMethod("render", GrammaticalStructure.class, String.class);
          // the first arg is null because it's a static method....
          mRender.invoke(null, gs, "Collapsed, CC processed deps");
        } catch (Exception e) {
          throw new RuntimeException("Couldn't use swing to portray semantic graph", e);
        }
      }

    } // end for
  } // end main

  // todo [cdm 2013]: Take this out and make it a trees class: TreeIterableByParsing
  static class LazyLoadTreesByParsing implements Iterable<Tree> {
    final Reader reader;
    final String filename;
    final boolean tokenized;
    final String encoding;
    final Function<List<? extends HasWord>, Tree> lp;

    public LazyLoadTreesByParsing(String filename, String encoding, boolean tokenized, Function<List<? extends HasWord>, Tree> lp) {
      this.filename = filename;
      this.encoding = encoding;
      this.reader = null;
      this.tokenized = tokenized;
      this.lp = lp;
    }
    public LazyLoadTreesByParsing(Reader reader, boolean tokenized, Function<List<? extends HasWord>, Tree> lp) {
      this.filename = null;
      this.encoding = null;
      this.reader = reader;
      this.tokenized = tokenized;
      this.lp = lp;
    }

    @Override
    public Iterator<Tree> iterator() {
      final BufferedReader iReader;
      if (reader != null) {
        iReader = new BufferedReader(reader);
      } else {
        try {
          iReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      return new Iterator<Tree>() {

        String line = null;

        @Override
        public boolean hasNext() {
          if (line != null) {
            return true;
          } else {
            try {
              line = iReader.readLine();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            if (line == null) {
              try {
                if (reader == null) iReader.close();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              return false;
            }
            return true;
          }
        }

        @Override
        public Tree next() {
          if (line == null) {
            throw new NoSuchElementException();
          }
          Reader lineReader = new StringReader(line);
          line = null;
          List<Word> words;
          if (tokenized) {
            words = WhitespaceTokenizer.newWordWhitespaceTokenizer(lineReader).tokenize();
          } else {
            words = PTBTokenizer.newPTBTokenizer(lineReader).tokenize();
          }
          if (!words.isEmpty()) {
            // the parser throws an exception if told to parse an empty sentence.
            Tree parseTree = lp.apply(words);
            return parseTree;
          } else {
            return new SimpleTree();
          }
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }

      };
    }

  } // end static class LazyLoadTreesByParsing

}
