package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.CoreAnnotation;
import old.edu.stanford.nlp.ling.Label;
import static old.edu.stanford.nlp.trees.GrammaticalRelation.DEPENDENT;
import static old.edu.stanford.nlp.trees.GrammaticalRelation.GOVERNOR;
import old.edu.stanford.nlp.trees.GrammaticalRelation.GrammaticalRelationAnnotation;
import old.edu.stanford.nlp.util.Filter;
import old.edu.stanford.nlp.util.Generics;

import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * A <code>GrammaticalStructure</code> is a {@link TreeGraph
 * <code>TreeGraph</code>} (that is, a tree with additional labeled
 * arcs between nodes) for representing the grammatical relations in a
 * parse tree.  A new <code>GrammaticalStructure</code> is constructed
 * from an existing parse tree with the help of {@link
 * GrammaticalRelation <code>GrammaticalRelation</code>}, which
 * defines a hierarchy of grammatical relations, along with
 * patterns for identifying them in parse trees.  The constructor for
 * <code>GrammaticalStructure</code> uses these definitions to
 * populate the new <code>GrammaticalStructure</code> with as many
 * labeled grammatical relations as it can.  Once constructed, the new
 * <code>GrammaticalStructure</code> can be printed in various
 * formats, or interrogated using the interface methods in this
 * class.
 * <p/>
 * <b>Caveat emptor!</b> This is a work in progress.
 * Nothing in here should be relied upon to function perfectly.
 * Feedback welcome.
 *
 * @author Bill MacCartney
 * @author Galen Andrew (refactoring English-specific stuff)
 * @author Ilya Sherman (dependencies)
 * @see EnglishGrammaticalRelations
 * @see GrammaticalRelation
 * @see EnglishGrammaticalStructure
 */
public abstract class GrammaticalStructure extends TreeGraph {

  protected Set<Dependency<Label, Label, Object>> dependencies = null;
  protected List<TypedDependency> typedDependencies = null;
  protected List<TypedDependency> allTypedDependencies = null;

  /**
   * Create a new GrammaticalStructure, analyzing the parse tree and
   * populate the GrammaticalStructure with as many labeled
   * grammatical relation arcs as possible.
   *
   * @param t             A Tree to analyze
   * @param relations     A set of GrammaticalRelations to consider
   * @param relationsLock Something needed to make this thread-safe
   * @param hf            A HeadFinder for analysis
   * @param puncFilter    A Filter to reject punctuation. To delete punctuation
   *                      dependencies, this filter should return false on
   *                      punctuation word strings, and true otherwise.
   *                      If punctuation dependencies should be kept, you
   *                      should pass in a Filters.&lt;String&gt;acceptFilter().
   */
  public GrammaticalStructure(Tree t, Collection<GrammaticalRelation> relations,
                              Lock relationsLock, HeadFinder hf, Filter<String> puncFilter) {
    super(t); // makes a Tree with TreeGraphNode nodes
    // add head word and tag to phrase nodes
    root.percolateHeads(hf);
    // add dependencies, using heads
    NoPunctFilter puncDepFilter = new NoPunctFilter(puncFilter);
    NoPunctTypedDependencyFilter puncTypedDepFilter = new NoPunctTypedDependencyFilter(puncFilter);
    dependencies = root.dependencies(puncDepFilter);
    for (Dependency<Label, Label, Object> p : dependencies) {
      //System.out.println("first dep found " + p);
      TreeGraphNode gov = (TreeGraphNode) p.governor();
      TreeGraphNode dep = (TreeGraphNode) p.dependent();
      dep.addArc(GrammaticalRelation.getAnnotationClass(GOVERNOR), gov);
    }
    // analyze the root (and its descendants, recursively)
    if (relationsLock != null) {
      relationsLock.lock();
    }
    try {
      analyzeNode(root, root, relations);
    }
    finally {
      if (relationsLock != null) {
        relationsLock.unlock();
      }
    }
    // add typed dependencies
    typedDependencies = getDeps(false, puncTypedDepFilter);
    allTypedDependencies = getDeps(true, puncTypedDepFilter);
  }

  public GrammaticalStructure(List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
    super(root);
    allTypedDependencies = typedDependencies = new ArrayList<TypedDependency>(projectiveDependencies);
    dependencies = new HashSet<Dependency<Label, Label, Object>>();
    for (TypedDependency tdep : projectiveDependencies) {
      dependencies.add(new NamedDependency(tdep.gov().toString(), tdep.dep().toString(), tdep.reln()));
    }
  }
  
  public GrammaticalStructure(Tree t, Collection<GrammaticalRelation> relations,
                              HeadFinder hf, Filter<String> puncFilter) {
    this(t, relations, null, hf, puncFilter);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    //    sb.append("Dependencies:");
    //    sb.append("\n" + dependencies);
    //    sb.append("Typed Dependencies:");
    //    sb.append("\n" + typedDependencies);
    //    sb.append("More Typed Dependencies:");
    //    sb.append("\n" + moreTypedDependencies());
    return sb.toString();
  }


  private static void analyzeNode(TreeGraphNode t, TreeGraphNode root, Collection<GrammaticalRelation> relations) {
    if (t.numChildren() > 0) {          // don't do leaves
      TreeGraphNode tHigh = t.highestNodeWithSameHead();
      for (GrammaticalRelation egr : relations) {
        if (egr.isApplicable(t)) {
          for (Tree u : egr.getRelatedNodes(t, root)) {
            tHigh.addArc(GrammaticalRelation.getAnnotationClass(egr), (TreeGraphNode) u);
          }
        }
      }
      // now recurse into children
      for (TreeGraphNode kid : t.children()) {
        analyzeNode(kid, root, relations);
      }
    }
  }


  /**
   * The constructor builds a list of typed dependencies using
   * information from a <code>GrammaticalStructure</code>.
   *
   * @param getExtra If true, the list of typed dependencies will contain extra ones.
   *              If false, the list of typed dependencies will respect the tree structure.
   */
  private List<TypedDependency> getDeps(boolean getExtra, Filter<TypedDependency> f) {
    List<TypedDependency> basicDep = Generics.newArrayList();

    for (Dependency<Label, Label, Object> d : dependencies()) {
      TreeGraphNode gov = (TreeGraphNode) d.governor();
      TreeGraphNode dep = (TreeGraphNode) d.dependent();
        //System.out.println("Gov: " + gov);
        //System.out.println("Dep: " + dep);
      GrammaticalRelation reln = getGrammaticalRelation(gov, dep);
        //System.out.println("Reln: " + reln);
      basicDep.add(new TypedDependency(reln, gov, dep));
    }
    if (getExtra) {
      TreeGraphNode root = root();
      getDep(root, root, basicDep, f); // adds stuff to basicDep
    }
    Collections.sort(basicDep);
    return basicDep;
  }

  private void getDep(TreeGraphNode t, TreeGraphNode root, List<TypedDependency> basicDep,
                      Filter<TypedDependency> f) {
    if (t.numChildren() > 0) {          // don't do leaves
      Map<Class<? extends CoreAnnotation>, Object> depMap = getAllDependents(t);
      for (Class<? extends CoreAnnotation> depName : depMap.keySet()) {
        for (Object depNode : (HashSet) depMap.get(depName)) {
          TreeGraphNode gov = t.headWordNode();
          TreeGraphNode dep = ((TreeGraphNode) depNode).headWordNode();
          if (gov != dep) {
            List<GrammaticalRelation> rels = getListGrammaticalRelation(t, (TreeGraphNode) depNode);
            if (!rels.isEmpty()) {
              for (GrammaticalRelation rel : rels) {
                TypedDependency newDep = new TypedDependency(rel, gov, dep);
                if (!basicDep.contains(newDep) && f.accept(newDep)) {
                  basicDep.add(newDep);
                }
              }
            }
          }
        }
      }
      // now recurse into children
      for (Tree kid : t.children()) {
        getDep((TreeGraphNode) kid, root, basicDep, f);
      }
    }
  }

  private static class NoPunctFilter implements Filter<Dependency<Label, Label, Object>> {
    private Filter<String> npf;

    NoPunctFilter(Filter<String> f) {
      this.npf = f;
    }

    public boolean accept(Dependency<Label, Label, Object> d) {
      if (d == null) {
        return false;
      }
      Label lab = d.dependent();
      if (lab == null) {
        return false;
      }
      return npf.accept(lab.value());
    }

    // Automatically generated by Eclipse
    private static final long serialVersionUID = -2319891944796663180L;
  } // end static class NoPunctFilter


  private static class NoPunctTypedDependencyFilter implements Filter<TypedDependency> {
    private Filter<String> npf;

    NoPunctTypedDependencyFilter(Filter<String> f) {
      this.npf = f;
    }

    public boolean accept(TypedDependency d) {
      if (d == null) return false;

      TreeGraphNode s = d.dep();
      if (s == null) return false;

      Label l = s.label();
      if (l == null) return false;

      return npf.accept(l.value());
    }

    // Automatically generated by Eclipse
    private static final long serialVersionUID = -2872766864289207468L;
  } // end static class NoPunctTypedDependencyFilter


  /**
   * Returns the set of (governor, dependent) dependencies in this
   * <code>GrammaticalStructure</code>.
   * @return The set of (governor, dependent) dependencies in this
   * <code>GrammaticalStructure</code>.
   */
  public Set<Dependency<Label, Label, Object>> dependencies() {
    return dependencies;
  }

  /**
   * Tries to return a <code>Set</code> of leaf (terminal) nodes
   * which are the {@link GrammaticalRelation#DEPENDENT
   * <code>DEPENDENT</code>}s of the given node <code>t</code>.
   * Probably, <code>t</code> should be a leaf node as well.
   *
   * @param t a leaf node in this <code>GrammaticalStructure</code>
   * @return a <code>Set</code> of nodes which are dependents of
   *         node <code>t</code>, or else <code>null</code>
   */
  public Set<TreeGraphNode> getDependents(TreeGraphNode t) {
    Set<TreeGraphNode> deps = Generics.newTreeSet();
    Set<Tree> nodes = root.subTrees();
    for (Iterator<Tree> it = nodes.iterator(); it.hasNext();) {
      TreeGraphNode node = (TreeGraphNode) it.next();
      TreeGraphNode gov = getGovernor(node);
      if (gov != null && gov == t) {
        deps.add(node);
      }
    }
    return deps;
  }

  /**
   * Tries to return a leaf (terminal) node which is the {@link
   * GrammaticalRelation#GOVERNOR
   * <code>GOVERNOR</code>} of the given node <code>t</code>.
   * Probably, <code>t</code> should be a leaf node as well.
   *
   * @param t a leaf node in this <code>GrammaticalStructure</code>
   * @return a node which is the governor for node
   *         <code>t</code>, or else <code>null</code>
   */
  public static TreeGraphNode getGovernor(TreeGraphNode t) {
    return getNodeInRelation(t, GOVERNOR);
  }

  public static TreeGraphNode getNodeInRelation(TreeGraphNode t, GrammaticalRelation r) {
    return t.followArcToNode(GrammaticalRelation.getAnnotationClass(r));
  }

  /**
   * Get GrammaticalRelation between gov and dep, and null if gov  is not the
   * governor of dep
   */
  public GrammaticalRelation getGrammaticalRelation(int govIndex, int depIndex) {
    TreeGraphNode gov = getNodeByIndex(govIndex);
    TreeGraphNode dep = getNodeByIndex(depIndex);
    return getGrammaticalRelation(gov, dep);
  }

  /**
   * Get GrammaticalRelation between gov and dep, and null if gov is not the
   * governor of dep
   */
  public GrammaticalRelation getGrammaticalRelation(TreeGraphNode gov, TreeGraphNode dep) {
    GrammaticalRelation reln = DEPENDENT;
    TreeGraphNode govH = gov.highestNodeWithSameHead();
    TreeGraphNode depH = dep.highestNodeWithSameHead();
    /*System.out.println("gov node " + gov);
    System.out.println("govH " + govH);
    System.out.println("dep node " + dep);
    System.out.println("depH " + depH);*/

    Set sortedSet = new TreeSet(new NameComparator());
    sortedSet.addAll(govH.arcLabelsToNode(depH));
    Set<Class<? extends GrammaticalRelationAnnotation>> arcLabels = sortedSet;

    //System.out.println("arcLabels: " + arcLabels);

    for (Class<? extends GrammaticalRelationAnnotation> arcLabel : arcLabels) {
      if (arcLabel != null) {
        GrammaticalRelation reln2;
        try {
          reln2 = GrammaticalRelation.getRelation(arcLabel);
        } catch (Exception e) {
          continue;
        }
        //GrammaticalRelation reln2 = r;
        if (reln.isAncestor(reln2)) {
          reln = reln2;
        }
      }
    }
    return reln;
  }

  /**
   * Get a list of GrammaticalRelation between gov and dep. Useful for getting extra dependencies, in which
   * two nodes can be linked by multiple arcs.
   */
  public static List<GrammaticalRelation> getListGrammaticalRelation(TreeGraphNode gov, TreeGraphNode dep) {
    List<GrammaticalRelation> list = new ArrayList<GrammaticalRelation>();
    TreeGraphNode govH = gov.highestNodeWithSameHead();
    TreeGraphNode depH = dep.highestNodeWithSameHead();

    /*System.out.println("Extra gov node " + gov);
    System.out.println("govH " + govH);
    System.out.println("dep node " + dep);
    System.out.println("depH " + depH);*/

    Set<Class<? extends GrammaticalRelationAnnotation>> arcLabels = govH.arcLabelsToNode(depH);
    //System.out.println("arcLabels: " + arcLabels);
    if (dep != depH) {
      Set<Class<? extends GrammaticalRelationAnnotation>> arcLabels2 = govH.arcLabelsToNode(dep);
      //System.out.println("arcLabels2: " + arcLabels2);
      arcLabels.addAll(arcLabels2);
    }
    //System.out.println("arcLabels: " + arcLabels);

    for (Class<? extends GrammaticalRelationAnnotation> arcLabel : arcLabels) {
      if (arcLabel != null) {
        GrammaticalRelation reln2 = GrammaticalRelation.getRelation(arcLabel);
        if (!list.isEmpty()) {
          for (int i = 0; i < list.size(); i++) {
            GrammaticalRelation gr = list.get(i);
            //if the element in the list is an ancestor of the current relation, replace it
            if (gr.isAncestor(reln2)) {
              int index = list.indexOf(gr);
              list.set(index, reln2);
            }
            //if the relation is not an ancestor of an element in the list, we add the relation
            else if (!reln2.isAncestor(gr)) {
              list.add(reln2);
            }

          }
        } else {
          list.add(reln2);
        }
      }
    }
    //System.out.println("in list " + list);
    return list;
  }

  /**
   * Returns the typed dependencies of this grammatical structure which do not break the tree structure of dependencies
   */
  public Collection<TypedDependency> typedDependencies() {
    return typedDependencies(false);
  }


  /**
   * Returns all the typed dependencies of this grammatical structure.
   */
  public Collection<TypedDependency> allTypedDependencies() {
    return typedDependencies(true);
  }


  /**
   * Returns the typed dependencies of this grammatical structure.
   * <p/>
   * If the boolean argument is true, the list of typed dependencies
   * returned may include "extras", and does not follow a tree structure.
   */
  public List<TypedDependency> typedDependencies(boolean includeExtras) {
    List<TypedDependency> dependencies = includeExtras ? allTypedDependencies
                                                       : typedDependencies;
    correctDependencies(dependencies);
    return dependencies;
  }

  /**
   * Get the typed dependencies after collapsing them.
   * No guarantee that the dependencies are in a tree structure.
   *
   * @return collapsed dependencies
   */
  public Collection<TypedDependency> typedDependenciesCollapsed() {
    return typedDependenciesCollapsed(false);
  }

  /**
   * Get the typed dependencies after collapsing them, but keep a tree structure:
   * <p/> no relative clause processing
   * <p/> no xsubj relations
   * <p/> no propagation of conjuncts
   *
   * @return collapsed dependencies keeping a tree structure
   */
  public Collection<TypedDependency> typedDependenciesCollapsedTree() {
    List<TypedDependency> tdl = typedDependencies(false);
    collapseDependenciesTree(tdl);
    return tdl;
  }

  /**
   * Get the typed dependencies after collapsing them.
   * <p/>
   * If the boolean argument is true, the list of typed dependencies
   * returned may include "extras".
   *
   * @return collapsed dependencies
   */
  public List<TypedDependency> typedDependenciesCollapsed(boolean includeExtras) {
    List<TypedDependency> tdl = typedDependencies(includeExtras);
    collapseDependencies(tdl, false);
    return tdl;
  }

  /**
   * Get the typed dependencies after collapsing them and processing eventual CC complements.
   * <p/>
   * If the boolean argument is true, the list of typed dependencies
   * returned may include "extras".
   *
   * @return collapsed dependencies with CC processed
   */
  public List<TypedDependency> typedDependenciesCCprocessed(boolean includeExtras) {
    List<TypedDependency> tdl = typedDependencies(includeExtras);
    collapseDependencies(tdl, true);
    return tdl;
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
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess) {
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
  protected void correctDependencies(Collection<TypedDependency> list) {
    // do nothing as default operation
  }


  /**
   * Returns the dependency path as a list of String, from node to root, it is assumed that
   * that root is an ancestor of node
   *
   * @return a list of dependency labels
   */
  public List<String> getDependencyPath(int nodeIndex, int rootIndex) {
    TreeGraphNode node = getNodeByIndex(nodeIndex);
    TreeGraphNode root = getNodeByIndex(rootIndex);
    return getDependencyPath(node, root);
  }

  /**
   * Returns the dependency path as a list of String, from node to root, it is assumed that
   * that root is an ancestor of node
   *
   * @param node Note to return path from
   * @param root The root of the tree, an ancestor of node
   * @return A list of dependency labels
   */
  public List<String> getDependencyPath(TreeGraphNode node, TreeGraphNode root) {
    List<String> path = new ArrayList<String>();
    while (!node.equals(root)) {
      TreeGraphNode gov = getGovernor(node);
      System.out.println("Governor for \"" + node.value() + "\": \"" + gov.value() + "\"");
      List<GrammaticalRelation> relations = getListGrammaticalRelation(gov, node);
      StringBuilder sb = new StringBuilder();
      for (GrammaticalRelation relation : relations) {
        //if (!arcLabel.equals(GOVERNOR))
        sb.append((sb.length() == 0 ? "" : "+")).append(relation.toString());
      }
      path.add(sb.toString());
      node = gov;
    }
    return path;
  }

  /**
   * returns all the dependencies of a certain node.
   *
   * @return map of dependencies
   */
  private Map<Class<? extends CoreAnnotation>, Object> getAllDependents(TreeGraphNode node) {
    Map<Class<? extends CoreAnnotation>, Object> newMap = Generics.newHashMap();

    for (Class<?> o : node.label.keySet()) {
      try {
        o.asSubclass(GrammaticalRelationAnnotation.class);
        newMap.put((Class<? extends CoreAnnotation>) o, node.label.get((Class<? extends CoreAnnotation>) o));//javac doesn't compile properly if generics are fully specified (but eclipse does...)
      } catch(Exception e) { }
    }
    return newMap;
  }



  /**
   * Checks if all the typeDependencies are connected
   * @param list a list of typedDependencies
   * @return true if the list represents a connected graph, false otherwise
   */
  public boolean isConnected(Collection<TypedDependency> list) {
    return (getRoots(list).size() <= 1); // there should be no more than one root to have a connected graph
                                         // there might be no root in the way we look when you have a relative clause
                                         // ex.: Apple is a society that sells computers
                                         // (the root "society" will also be the nsujb of "sells")
  }

  /**
   * Return a list of TypedDependencies which are not dependent on any node from the list
   * @return A list of TypedDependencies which are not dependent on any node from the list
   */
  public Collection<TypedDependency> getRoots(Collection<TypedDependency> list) {

    Collection<TypedDependency> roots = new ArrayList<TypedDependency>();

    // need to see if more than one governor is not listed somewhere as a dependent
    // first take all the deps
    Collection<TreeGraphNode> deps = new HashSet<TreeGraphNode>();
    for (TypedDependency typedDep : list) {
      deps.add(typedDep.dep());
    }

    // go through the list and add typedDependency for which the gov is not a dep
    Collection<TreeGraphNode> govs = new HashSet<TreeGraphNode>();
    for (TypedDependency typedDep : list) {
      TreeGraphNode gov = typedDep.gov();
      if (!deps.contains(gov) && !govs.contains(gov)) {
        roots.add(typedDep);
      }
      govs.add(gov);
    }
    return roots;
  }

  private static final long serialVersionUID = 2286294455343892678L;

  private static class NameComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      String n1 = o1.toString();
      String n2 = o2.toString();
      return n1.compareTo(n2);
    }
  }

}
