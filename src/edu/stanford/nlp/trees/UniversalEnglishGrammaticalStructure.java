package edu.stanford.nlp.trees;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;

import edu.stanford.nlp.graph.DirectedMultiGraph;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.*;
import static edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations.*;
import static edu.stanford.nlp.trees.GrammaticalRelation.*;

/**
 * A GrammaticalStructure for Universal Dependencies English.
 * <p/>
 * For feeding Stanford parser trees into this class, the Stanford parser should be run with the
 * "-retainNPTmpSubcategories" option for best results!
 *
 * @author Bill MacCartney
 * @author Marie-Catherine de Marneffe
 * @author Christopher Manning
 * @author Daniel Cer (CoNLLX format and alternative user selected dependency
 *         printer/reader interface)
 * @author John Bauer
 * @author Sebastian Schuster
 */
public class UniversalEnglishGrammaticalStructure extends GrammaticalStructure {

  private static final long serialVersionUID = 1L;

  private static final boolean DEBUG = System.getProperty("UniversalEnglishGrammaticalStructure", null) != null;

  /**
   * Construct a new {@code EnglishGrammaticalStructure} from an existing parse
   * tree. The new {@code GrammaticalStructure} has the same tree structure
   * and label values as the given tree (but no shared storage). As part of
   * construction, the parse tree is analyzed using definitions from
   * {@link GrammaticalRelation <code>GrammaticalRelation</code>} to populate
   * the new <code>GrammaticalStructure</code> with as many labeled grammatical
   * relations as it can.
   *
   * @param t Parse tree to make grammatical structure from
   */
  public UniversalEnglishGrammaticalStructure(Tree t) {
    this(t, new PennTreebankLanguagePack().punctuationWordRejectFilter());
  }

  /**
   * This gets used by GrammaticalStructureFactory (by reflection). DON'T DELETE.
   *
   * @param t Parse tree to make grammatical structure from
   * @param tagFilter Filter to remove punctuation dependencies
   */
  public UniversalEnglishGrammaticalStructure(Tree t, Predicate<String> tagFilter) {
    this(t, tagFilter, new UniversalSemanticHeadFinder(true), true);
  }

  /**
   * This gets used by GrammaticalStructureFactory (by reflection). DON'T DELETE.
   *
   * @param t Parse tree to make grammatical structure from
   * @param tagFilter Tag filter to remove punctuation dependencies
   * @param hf HeadFinder to use when building it
   */
  public UniversalEnglishGrammaticalStructure(Tree t, Predicate<String> tagFilter, HeadFinder hf) {
    this(t, tagFilter, hf, true);
  }

  /**
   * Construct a new <code>GrammaticalStructure</code> from an existing parse
   * tree. The new <code>GrammaticalStructure</code> has the same tree structure
   * and label values as the given tree (but no shared storage). As part of
   * construction, the parse tree is analyzed using definitions from
   * {@link GrammaticalRelation <code>GrammaticalRelation</code>} to populate
   * the new <code>GrammaticalStructure</code> with as many labeled grammatical
   * relations as it can.
   *
   * @param t Parse tree to make grammatical structure from
   * @param tagFilter Filter for punctuation tags
   * @param hf HeadFinder to use when building it
   * @param threadSafe Whether or not to support simultaneous instances among multiple
   *          threads
   */
  public UniversalEnglishGrammaticalStructure(Tree t, Predicate<String> tagFilter, HeadFinder hf, boolean threadSafe) {
    
    // the tree is normalized (for index and functional tag stripping) inside CoordinationTransformer
    super(t, UniversalEnglishGrammaticalRelations.values(threadSafe), threadSafe ? UniversalEnglishGrammaticalRelations.valuesLock() : null, 
          new CoordinationTransformer(hf, true), hf, Filters.acceptFilter(), tagFilter);
  }

  /** Used for postprocessing CoNLL X dependencies */
  public UniversalEnglishGrammaticalStructure(List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
    super(projectiveDependencies, root);
  }


  /**
   * Returns a Filter which checks dependencies for usefulness as
   * extra tree-based dependencies.  By default, everything is
   * accepted.  One example of how this can be useful is in the
   * English dependencies, where the REL dependency is used as an
   * intermediate and we do not want this to be added when we make a
   * second pass over the trees for missing dependencies.
   */
  @Override
  protected Predicate<TypedDependency> extraTreeDepFilter() {
    return extraTreeDepFilter;
  }

  private static class ExtraTreeDepFilter implements Predicate<TypedDependency>, Serializable {
    @Override
    public boolean test(TypedDependency d) {
      return d != null && d.reln() != RELATIVE && d.reln() != PREPOSITION;
    }

    private static final long serialVersionUID = 1L;
  }

  private static final Predicate<TypedDependency> extraTreeDepFilter = new ExtraTreeDepFilter();

  
  @Override
  protected void getTreeDeps(List<TypedDependency> deps,
      DirectedMultiGraph<TreeGraphNode, GrammaticalRelation> completeGraph,
      Predicate<TypedDependency> puncTypedDepFilter,
      Predicate<TypedDependency> extraTreeDepFilter) {
  //Do nothing
  }

  @Override
  protected void correctDependencies(List<TypedDependency> list) {
    SemanticGraph sg = new SemanticGraph(list);
    correctDependencies(sg);
    list.clear();
    list.addAll(sg.typedDependencies());
    Collections.sort(list);
  }
  
  protected void correctDependencies(SemanticGraph sg) {
    if (DEBUG) {
      printListSorted("At correctDependencies:", sg.typedDependencies());
    }
    correctSubjPass(sg);
    if (DEBUG) {
      printListSorted("After correctSubjPass:", sg.typedDependencies());
    }
    removeExactDuplicates(sg);
    if (DEBUG) {
      printListSorted("After removeExactDuplicates:", sg.typedDependencies());
    }
  }

  private static void printListSorted(String title, Collection<TypedDependency> list) {
    List<TypedDependency> lis = new ArrayList<TypedDependency>(list);
    Collections.sort(lis);
    if (title != null) {
      System.err.println(title);
    }
    System.err.println(lis);
  }

  @Override
  protected void postProcessDependencies(List<TypedDependency> list) {
    SemanticGraph sg = new SemanticGraph(list);
    if (DEBUG) {
      printListSorted("At postProcessDependencies:", sg.typedDependencies());
    }
    correctWHAttachment(sg);
    if (DEBUG) {
      printListSorted("After corrrecting WH attachment:", sg.typedDependencies());
    }
    convertRel(sg);
    if (DEBUG) {
      printListSorted("After converting rel:", sg.typedDependencies());
    }
    list.clear();
    list.addAll(sg.typedDependencies());
  }

  @Override
  protected void getExtras(List<TypedDependency> list) {
    SemanticGraph sg = new SemanticGraph(list);
    addRef(sg);
    if (DEBUG) {
      printListSorted("After adding ref:", sg.typedDependencies());
    }

    addExtraNSubj(sg);
    if (DEBUG) {
      printListSorted("After adding extra nsubj:", sg.typedDependencies());
    }
    
    list.clear();
    list.addAll(sg.typedDependencies());
  }
  

  /* Semgrex patterns for prepositional phrases. */
  private static SemgrexPattern PASSIVE_AGENT_PATTERN = SemgrexPattern.compile("{}=gov >nmod=reln ({}=mod >case {word:/^(?i:by)$/}=c1) >auxpass {}");
  private static SemgrexPattern PREP_MW3_PATTERN = SemgrexPattern.compile("{}=gov   [>/^(nmod|advcl|acl)$/=reln ({}=mod >case ({}=c1 >mwe {}=c2 >mwe ({}=c3 !== {}=c2) ))]");
  private static SemgrexPattern PREP_MW2_PATTERN = SemgrexPattern.compile("{}=gov >/^(nmod|advcl|acl)$/=reln ({}=mod >case ({}=c1 >mwe {}=c2))");
  private static SemgrexPattern PREP_PATTERN = SemgrexPattern.compile("{}=gov   >/^(nmod|advcl|acl)$/=reln ({}=mod >case {}=c1)");

  
  /**
   * Adds the case marker(s) to all nmod, acl and advcl relations that are 
   * modified by one or more case markers(s).
   * 
   * @see UniversalEnglishGrammaticalStructure#addCaseMarkersToReln
   */
  private static void addCaseMarkerInformation(SemanticGraph sg) {
    
    /* Semgrexes require a graph with a root. */
    if (sg.getRoots().isEmpty())
      return;
    
    /* passive agent */
    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = PASSIVE_AGENT_PATTERN.matcher(sgCopy);
    while (matcher.find()) {
      IndexedWord caseMarker = matcher.getNode("c1");
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");
      addPassiveAgentToReln(sg, gov, mod, caseMarker);
    }    
    
    List<IndexedWord> oldCaseMarkers = Generics.newArrayList();

    
    /* 3-word prepositions */
    sgCopy = sg.makeSoftCopy();
    matcher = PREP_MW3_PATTERN.matcher(sgCopy);
    while (matcher.find()) {
      List<IndexedWord> caseMarkers = Generics.newArrayList(3);
      caseMarkers.add(matcher.getNode("c1"));
      caseMarkers.add(matcher.getNode("c2"));
      caseMarkers.add(matcher.getNode("c3"));
      
      Collections.sort(caseMarkers);

      /* We only want to match every case marker once. */
      if (caseMarkers.equals(oldCaseMarkers))
        continue;

      
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");
      
      addCaseMarkersToReln(sg, gov, mod, caseMarkers);
      
      oldCaseMarkers = caseMarkers;
    }
    
    
    /* 2-word prepositions */
    sgCopy = sg.makeSoftCopy();
    matcher = PREP_MW2_PATTERN.matcher(sgCopy);
    while (matcher.find()) {
      List<IndexedWord> caseMarkers = Generics.newArrayList(2);
      caseMarkers.add(matcher.getNode("c1"));
      caseMarkers.add(matcher.getNode("c2"));
      Collections.sort(caseMarkers);

      /* We only want to match every case marker once. */
      if (caseMarkers.equals(oldCaseMarkers))
        continue;
      
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");
      addCaseMarkersToReln(sg, gov, mod, caseMarkers);
      
      oldCaseMarkers = caseMarkers;
    }
    
    /* Single-word prepositions */
    sgCopy = sg.makeSoftCopy();
    matcher = PREP_PATTERN.matcher(sgCopy);
    while (matcher.find()) {
      List<IndexedWord> caseMarkers = Generics.newArrayList(1);
      caseMarkers.add(matcher.getNode("c1"));
      
      if (caseMarkers.equals(oldCaseMarkers))
        continue;
      
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");
      addCaseMarkersToReln(sg, gov, mod, caseMarkers);

      oldCaseMarkers = caseMarkers;
    }
    
  }
  

  private static void addPassiveAgentToReln(SemanticGraph sg,
      IndexedWord gov, IndexedWord mod, IndexedWord caseMarker) {
   
    SemanticGraphEdge edge = sg.getEdge(gov, mod);
    GrammaticalRelation reln = UniversalEnglishGrammaticalRelations.getNmod("agent");
    edge.setRelation(reln);
  }
  
  
  /**
   * Appends case marker information to nmod/acl/advcl relations.
   * <p/>
   * E.g. if there is a relation <code>nmod(gov, dep)</code> and <code>case(dep, prep)</code>, then
   * the <code>nmod</nmod> relation is renamed to <code>nmod:prep</code>.
   * 
   * 
   * @param sg semantic graph
   * @param gov governor of the nmod/acl/advcl relation
   * @param mod modifier of the nmod/acl/advcl relation
   * @param caseMarkers List<IndexedWord> of all the case markers that depend on mod
   */
  private static void addCaseMarkersToReln(SemanticGraph sg, IndexedWord gov, IndexedWord mod, List<IndexedWord> caseMarkers) {
    
    SemanticGraphEdge edge = sg.getEdge(gov, mod);
    int lastCaseMarkerIndex = 0;
    StringBuilder sb = new StringBuilder();
    boolean firstWord = true;
    for (IndexedWord cm : caseMarkers) {
      /* check for adjacency */
      if (lastCaseMarkerIndex == 0 || cm.index() == (lastCaseMarkerIndex + 1)) {
        if ( ! firstWord) {
          sb.append("_");
        }
        sb.append(cm.value());
        firstWord = false;
      } else {
        /* Should never happen as there should be never two non-adjacent case markers.
         * If it does happen nevertheless create an additional relation.
         */
        GrammaticalRelation reln = getCaseMarkedRelation(edge.getRelation(), sb.toString().toLowerCase());
        sg.addEdge(gov, mod, reln, Double.NEGATIVE_INFINITY, true);
        sb = new StringBuilder(cm.value());
        firstWord = true;
      }
      lastCaseMarkerIndex = cm.index();
    }
    GrammaticalRelation reln = getCaseMarkedRelation(edge.getRelation(), sb.toString().toLowerCase());
    edge.setRelation(reln);
  }
  
  private static SemgrexPattern PREP_CONJP_PATTERN = SemgrexPattern.compile("{} >case ({}=gov >cc {}=cc >conj {}=conj)");
  
  /**
   * Expands prepositions with conjunctions such as in the sentence
   * "Bill flies to and from Serbia." by copying the verb resulting 
   * in the following relations:
   * <p/>
   * <code>conj:and(flies, flies')</code><br/>
   * <code>case(Serbia, to)</code><br/>
   * <code>cc(to, and)</code><br/>
   * <code>conj(to, from)</code><br/>
   * <code>nmod(flies, Serbia)</code><br/>
   * <code>nmod(flies', Serbia)</code><br/>
   * <p/>
   * The label of the conjunct relation includes the conjunction type
   * because if the verb has multiple cc relations then it can be impossible
   * to infer which coordination marker belongs to which conjuncts.
   * 
   * @param list mutable list of dependencies
   */
  private static void expandPrepConjunctions(SemanticGraph sg) {
    
    /* Semgrexes require a graph with a root. */
    if (sg.getRoots().isEmpty())
      return;

    
    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = PREP_CONJP_PATTERN.matcher(sgCopy);
    
    IndexedWord oldGov = null;
    IndexedWord oldCcDep = null;
    List<IndexedWord> conjDeps = Generics.newLinkedList();
    
    while (matcher.find()) {
      IndexedWord ccDep = matcher.getNode("cc");
      IndexedWord conjDep = matcher.getNode("conj");
      IndexedWord gov = matcher.getNode("gov");
      if (oldGov != null &&  (! gov.equals(oldGov) || ! ccDep.equals(oldCcDep))) {
        expandPrepConjunction(sg, oldGov, conjDeps, oldCcDep);
        conjDeps = Generics.newLinkedList();
      }
      oldCcDep = ccDep;
      oldGov = gov;
      conjDeps.add(conjDep);
    }
    
    if (oldGov != null) {
      expandPrepConjunction(sg, oldGov, conjDeps, oldCcDep);
    }
    
  }  
  
  /*
   * Used by expandPrepConjunctions.
   */
  
  private static void expandPrepConjunction(SemanticGraph sg, IndexedWord gov, 
      List<IndexedWord> conjDeps, IndexedWord ccDep)  {
    
    IndexedWord caseGov = sg.getParent(gov);
    
    if (caseGov == null) 
      return;
    
    IndexedWord caseGovGov = sg.getParent(caseGov);
    
    if (caseGovGov == null)
      return;
    
    IndexedWord conjGov = caseGovGov.getOriginal() != null ? caseGovGov.getOriginal() : caseGovGov;
    GrammaticalRelation rel = sg.reln(caseGovGov, caseGov);
    List<IndexedWord> newConjDeps = Generics.newLinkedList();
    for (IndexedWord conjDep : conjDeps) {
      //IndexedWord caseGovCopy = caseGov.makeSoftCopy();
      IndexedWord caseGovGovCopy = caseGovGov.makeSoftCopy();
      
      /* Change conj(prep-1, prep-2) to case(prep-1-gov-copy, prep-2) */
      //SemanticGraphEdge edge = sg.getEdge(gov, conjDep);
      //sg.removeEdge(edge);
      //sg.addEdge(caseGovCopy, conjDep, CASE_MARKER, Double.NEGATIVE_INFINITY, false);
      
      /* Add relation to copy node. */
      //sg.addEdge(caseGovGovCopy, caseGovCopy, rel, Double.NEGATIVE_INFINITY, false);

      sg.addEdge(conjGov, caseGovGovCopy, CONJUNCT, Double.NEGATIVE_INFINITY, false);
      newConjDeps.add(caseGovGovCopy);
      
      sg.addEdge(caseGovGovCopy, caseGov, rel, Double.NEGATIVE_INFINITY, true);

      List<IndexedWord> caseMarkers = Generics.newArrayList();
      caseMarkers.add(conjDep);
      
      addCaseMarkersToReln(sg, caseGovGovCopy, caseGov, caseMarkers);      
      /* Attach all children except case markers of caseGov to caseGovCopy. */
      //for (SemanticGraphEdge e : sg.outgoingEdgeList(caseGov)) {
      //  if (e.getRelation() != CASE_MARKER && ! e.getDependent().equals(ccDep)) {
      //    sg.addEdge(caseGovCopy, e.getDependent(), e.getRelation(), Double.NEGATIVE_INFINITY, false);
      //  }
     // }
    }
    
    /* Attach CC node to caseGov */ 
    //SemanticGraphEdge edge = sg.getEdge(gov, ccDep);
    //sg.removeEdge(edge);
    //sg.addEdge(conjGov, ccDep, COORDINATION, Double.NEGATIVE_INFINITY, false);
    
    /* Add conjunction information for these relations already at this point.
     * It could be that we add several coordinating conjunctions while collapsing
     * and we might not know which conjunction belongs to which conjunct at a later
     * point.
     */
    addConjToReln(sg, conjGov, newConjDeps, ccDep);
  }
  
  
  private static SemgrexPattern PP_CONJP_PATTERN = SemgrexPattern.compile("{} >/^(nmod|acl|advcl)$/ (({}=gov >case {}) >cc {}=cc >conj ({}=conj >case {}))");
  
  
  /**
   * Expands PPs with conjunctions such as in the sentence
   * "Bill flies to France and from Serbia." by copying the verb 
   * that governs the prepositinal phrase resulting in the following
   * relations:
   * <p/>
   * <code>conj:and(flies, flies')</code><br/>
   * <code>case(France, to)</code><br/>
   * <code>cc(flies, and)</code><br/>
   * <code>case(Serbia, from)</code><br/>
   * <code>nmod(flies, France)</code><br/>
   * <code>nmod(flies', Serbia)</code><br/>
   * <p/>
   * The label of the conjunct relation includes the conjunction type
   * because if the verb has multiple cc relations then it can be impossible
   * to infer which coordination marker belongs to which conjuncts.
   * 
   * @param sg SemanticGraph to operate on.
   */
  private static void expandPPConjunctions(SemanticGraph sg) {

    /* Semgrexes require a graph with a root. */
    if (sg.getRoots().isEmpty())
      return;

    
    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = PP_CONJP_PATTERN.matcher(sgCopy);
    
    IndexedWord oldGov = null;
    IndexedWord oldCcDep = null;
    List<IndexedWord> conjDeps = Generics.newLinkedList();
    
    while (matcher.find()) {
      IndexedWord conjDep = matcher.getNode("conj");
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord ccDep = matcher.getNode("cc");

      if (oldGov != null &&  (! gov.equals(oldGov) || ! ccDep.equals(oldCcDep))) {
        expandPPConjunction(sg, oldGov, conjDeps, oldCcDep);
        conjDeps = Generics.newLinkedList();
      }
      oldCcDep = ccDep;
      oldGov = gov;
      conjDeps.add(conjDep);
    }
    
    if (oldGov != null) {
      expandPPConjunction(sg, oldGov, conjDeps, oldCcDep);
    }
    
  }  
  
  /*
   * Used by expandPPConjunction.
   */
  private static void expandPPConjunction(SemanticGraph sg, IndexedWord gov, 
      List<IndexedWord> conjDeps, IndexedWord ccDep) {
    
    IndexedWord nmodGov = sg.getParent(gov);
    
    if (nmodGov == null)
      return;
    
    IndexedWord conjGov = nmodGov.getOriginal() != null ? nmodGov.getOriginal() : nmodGov;
    GrammaticalRelation rel = sg.reln(nmodGov, gov);  
    List<IndexedWord> newConjDeps = Generics.newLinkedList();
    for (IndexedWord conjDep : conjDeps) {
      IndexedWord nmodGovCopy = nmodGov.makeSoftCopy();
      
      /* Change conj(nmod-1, nmod-2) to nmod(nmod-1-gov, nmod-2) */
      SemanticGraphEdge edge = sg.getEdge(gov, conjDep);
      if (edge != null) {
        sg.removeEdge(edge);
        sg.addEdge(nmodGovCopy, conjDep, rel, Double.NEGATIVE_INFINITY, false);
      }
      
      /* Add relation to copy node. */
      sg.addEdge(conjGov, nmodGovCopy, CONJUNCT, Double.NEGATIVE_INFINITY, false);
      newConjDeps.add(nmodGovCopy);
    }
    
    /* Attach CC node to conjGov */ 
    SemanticGraphEdge edge = sg.getEdge(gov, ccDep);
    if (edge != null) {
      sg.removeEdge(edge);
      sg.addEdge(conjGov, ccDep, COORDINATION, Double.NEGATIVE_INFINITY, false);
    }
    
    /* Add conjunction information for these relations already at this point.
     * It could be that we add several coordinating conjunctions while collapsing
     * and we might not know which conjunction belongs to which conjunct at a later
     * point.
     */
    addConjToReln(sg, conjGov, newConjDeps, ccDep);
  }
  
  
  /**
   * 
   * Returns a GrammaticalRelation which combines the original relation and 
   * the preposition.
   * 
   */
  private static GrammaticalRelation getCaseMarkedRelation(GrammaticalRelation reln, String relationName) {
    GrammaticalRelation newReln = reln;

    if (reln.getSpecific() != null) {
      reln = reln.getParent();
    }
    if (reln == NOMINAL_MODIFIER) {
      newReln = UniversalEnglishGrammaticalRelations.getNmod(relationName);
    } else if (reln == ADV_CLAUSE_MODIFIER) {
      newReln = UniversalEnglishGrammaticalRelations.getAdvcl(relationName);
    } else if (reln == CLAUSAL_MODIFIER) {
      newReln = UniversalEnglishGrammaticalRelations.getAcl(relationName);
    }
    return newReln;
  }

  
  private static SemgrexPattern CONJUNCTION_PATTERN = SemgrexPattern.compile("{}=gov >cc {}=cc >conj {}=conj");
  
  
  /**
   * Adds the type of conjunction to all conjunct relations. 
   * <p/>
   * <code>cc(Marie, and)</code>, <code>conj(Marie, Chris)</code> and <code>conj(Marie, John)</code>
   * become <code>cc(Marie, and)</code>, <code>conj(Marie, Chris)</code> and <code>conj(Marie, John)</code>.
   * <p/>
   * In case multiple coordination marker depend on the same governor
   * the one that precedes the conjunct is appended to the conjunction relation or the
   * first one if no preceding marker exists.
   * <p/>
   * Some multi-word coordination markers are collapsed to <code>conj:and</code> or <code>conj:negcc</code>. 
   * See {@link #conjValue(IndexedWord, SemanticGraph)}.
   * 
   * @param list mutable list of dependency relations
   */
  private static void addConjInformation(SemanticGraph sg) {
    
    /* Semgrexes require a graph with a root. */
    if (sg.getRoots().isEmpty())
      return;
    
    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = CONJUNCTION_PATTERN.matcher(sgCopy);
    
    IndexedWord oldGov = null;
    IndexedWord oldCcDep = null;
    List<IndexedWord> conjDeps = Generics.newLinkedList();
    
    while (matcher.find()) {
      IndexedWord conjDep = matcher.getNode("conj");
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord ccDep = matcher.getNode("cc");
      if (oldGov != null &&  (! gov.equals(oldGov) || ! ccDep.equals(oldCcDep))) {
        addConjToReln(sg, oldGov, conjDeps, oldCcDep);
        conjDeps = Generics.newLinkedList();
      }
      oldCcDep = ccDep;
      conjDeps.add(conjDep);
      oldGov = gov;
    }
    
    if (oldGov != null) {
      addConjToReln(sg, oldGov, conjDeps, oldCcDep);
    }
    
  }
  
  /*
   * Used by addConjInformation.
   */
  private static void addConjToReln(SemanticGraph sg,
      IndexedWord gov, List<IndexedWord> conjDeps, IndexedWord ccDep) {

    for (IndexedWord conjDep : conjDeps) {
      SemanticGraphEdge edge = sg.getEdge(gov, conjDep);
      if (edge.getRelation() == CONJUNCT || conjDep.index() > ccDep.index()) {
        edge.setRelation(conjValue(ccDep, sg));
      }
    }
  }

  /* Used by correctWHAttachment */
  private static SemgrexPattern XCOMP_PATTERN = SemgrexPattern.compile("{}=root >xcomp {}=embedded >/^(dep|dobj)$/ {}=wh ?>/([di]obj)/ {}=obj");
  
  private static Morphology morphology = new Morphology();
  
  /**
   * Tries to correct complicated cases of WH-movement in
   * sentences such as "What does Mary seem to have?" in
   * which "What" should attach to "have" instead of the
   * control verb. 
   * 
   * @param sg The Semantic graph to operate on.
   */
  private static void correctWHAttachment(SemanticGraph sg) {
    
    /* Semgrexes require a graph with a root. */
    if (sg.getRoots().isEmpty())
      return;
    
    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = XCOMP_PATTERN.matcher(sgCopy);
    while (matcher.findNextMatchingNode()) {
      IndexedWord root = matcher.getNode("root");
      IndexedWord embeddedVerb = matcher.getNode("embedded");
      IndexedWord wh = matcher.getNode("wh");
      IndexedWord dobj = matcher.getNode("obj");
      
      /* Check if the object is a WH-word. */
      if (wh.tag().startsWith("W")) {
        boolean reattach = false;
        /* If the control verb already has an object, then
           we have to reattach the WH-word to the verb in the embedded clause. */
        if (dobj != null) {
          reattach = true;
        } else {
          /* If the control verb can't have an object, we also have to reattach. */
          String lemma = morphology.lemma(root.value(), root.tag());
          if (lemma.matches(EnglishPatterns.NP_V_S_INF_VERBS_REGEX)) {
            reattach = true;
          }
        }
        
        if (reattach) {
          SemanticGraphEdge edge = sg.getEdge(root, wh);
          if (edge != null) {
            sg.removeEdge(edge);
            sg.addEdge(embeddedVerb, wh, DIRECT_OBJECT, Double.NEGATIVE_INFINITY, false);
          }
        }
      }
    }
  }
  
  
  /**
   * What we do in this method is look for temporary dependencies of
   * the type "rel" and "prep".  These occur in sentences such as "I saw the man
   * who you love".  In that case, we should produce dobj(love, who).
   * On the other hand, in the sentence "... which Mr. Bush was
   * fighting for", we should have case(which, for).
   */
   private static void convertRel(SemanticGraph sg) {
    
    for (SemanticGraphEdge prep : sg.findAllRelns(PREPOSITION)) {
      
      boolean changedPrep = false;
      
      for (SemanticGraphEdge nmod : sg.outgoingEdgeIterable(prep.getGovernor())) {
        
        // todo: It would also be good to add a rule here to prefer ccomp nsubj over dobj if there is a ccomp with no subj
        // then we could get right: Which eco-friendly options do you think there will be on the new Lexus?
        if (nmod.getRelation() != NOMINAL_MODIFIER && nmod.getRelation() != RELATIVE) {
          continue;
        }
        
        if (prep.getDependent().index() < nmod.getDependent().index()) {
          continue;
        }
        
        sg.removeEdge(prep);
        sg.addEdge(nmod.getDependent(), prep.getDependent(), CASE_MARKER, Double.NEGATIVE_INFINITY, false);
        
        changedPrep = true;
        
        if (nmod.getRelation() == RELATIVE) {
          nmod.setRelation(NOMINAL_MODIFIER);
        }
        
        break;
      }
      
      if ( ! changedPrep) {
        prep.setRelation(NOMINAL_MODIFIER);
      }
    }
  
    /* Rename remaining "rel" relations. */
    for (SemanticGraphEdge edge : sg.findAllRelns(RELATIVE)) {
      edge.setRelation(DIRECT_OBJECT);
    }
  }

  /**
   * Destructively modifies this {@code Collection<TypedDependency>}
   * by collapsing several types of transitive pairs of dependencies or
   * by adding additional information from the dependents to the relation
   * of the governor.
   * If called with a tree of dependencies and both CCprocess and
   * includeExtras set to false, then the tree structure is preserved.
   * <p/>
   * 
   * <dl>
   * <dt>nominal modifier dependencies: nmod</dt>
   * <dd>
   * If there exist the relations <code>case(hat, in)</code> and <code>nmod(in, hat)</code> then
   * the <code>nmod</code> relation is enhanced to <code>nmod:in(cat, hat)</code>. 
   * The <code>case(hat, in)</code> relation is preserved.</dd>
   * <dt>clausal modifier of noun/adverbial clause modifier with case markers: acs/advcl</dt>
   * <dd>
   * If there exist the relations <code>case(attacking, of)</code> and <code>advcl(heard, attacking)</code> then
   * the <code>nmod</code> relation is enhanced to <code>nmod:of(heard, attacking)</code>. 
   * The <code>case(attacking, of)</code> relation is preserved.</dd>
   * <dt>conjunct dependencies</dt>
   * <dd>
   * If there exist the relations 
   * <code>cc(investors, and)</code> and
   * <code>conj(investors, regulators)</code>, then the <code>conj</code> relation is
   * enhanced to 
   * <code>conj:and(investors, regulators)</code></dd>
   * <dt>For relative clauses, it will collapse referent</dt>
   * <dd>
   * <code>ref(man, that)</code> and <code>dobj(love, that)</code> are collapsed
   * to <code>dobj(love, man)</code></dd>
   * </dl>
   */
  @Override
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess, Extras includeExtras) {
    SemanticGraph sg = new SemanticGraph(list);
    
    if (DEBUG) {
      printListSorted("collapseDependencies: CCproc: " + CCprocess + " includeExtras: " + includeExtras, sg.typedDependencies());
    }
    
    
    correctDependencies(sg);
    if (DEBUG) {
      printListSorted("After correctDependencies:", sg.typedDependencies());
    }

    processMultiwordPreps(sg);
    if (DEBUG) {
      printListSorted("After processMultiwordPreps:", sg.typedDependencies());
    }

    
    expandPPConjunctions(sg);
    if (DEBUG) {
      printListSorted("After expandPPConjunctions:", sg.typedDependencies());
    }

    expandPrepConjunctions(sg);
    if (DEBUG) {
      printListSorted("After expandPrepConjunctions:", sg.typedDependencies());
    }
        
    addCaseMarkerInformation(sg);
    if (DEBUG) {
      printListSorted("After addCaseMarkerInformation:", sg.typedDependencies());
    }

    addConjInformation(sg);
    if (DEBUG) {
      printListSorted("After addConjInformation:", sg.typedDependencies());
    }
    
    if (includeExtras.doRef) {
      addRef(sg);
      if (DEBUG) {
        printListSorted("After adding ref:", sg.typedDependencies());
      }

      if (includeExtras.collapseRef) {
        collapseReferent(sg);
        if (DEBUG) {
          printListSorted("After collapse referent:",  sg.typedDependencies());
        }
      }
    }

    if (CCprocess) {
      treatCC(sg);
      if (DEBUG) {
        printListSorted("After treatCC:", sg.typedDependencies());
      }
    }

    if (includeExtras.doSubj) {
      addExtraNSubj(sg);
      
      if (DEBUG) {
        printListSorted("After adding extra nsubj:", sg.typedDependencies());
      }
      correctSubjPass(sg);

      if (DEBUG) {
        printListSorted("After correctSubjPass:", sg.typedDependencies());
      }
    }

    list.clear();
    list.addAll(sg.typedDependencies());
    
    Collections.sort(list);
    if (DEBUG) {
      printListSorted("After all collapse:", list);
    }
  }

  @Override
  protected void collapseDependenciesTree(List<TypedDependency> list) {
    collapseDependencies(list, false, Extras.NONE);
  }

  /**
   * Does some hard coding to deal with relation in CONJP. For now we deal with:
   * but not, if not, instead of, rather than, but rather GO TO negcc <br/>
   * as well as, not to mention, but also, & GO TO and.
   *
   * @param cc The head dependency of the conjunction marker
   * @param sg The complete current semantic graph
   * @return A GrammaticalRelation made from a normalized form of that
   *         conjunction.
   */
  private static GrammaticalRelation conjValue(IndexedWord cc, SemanticGraph sg) {
    
    int pos = cc.index();
    String newConj = cc.value().toLowerCase();

    if (newConj.equals("not")) {
      IndexedWord prevWord = sg.getNodeByIndexSafe(pos - 1);
      if (prevWord != null && prevWord.value().toLowerCase().equals("but")) {
        return UniversalEnglishGrammaticalRelations.getConj("negcc");
      }
    }
    
    IndexedWord secondIWord = sg.getNodeByIndexSafe(pos + 1);
    
    if (secondIWord == null) {
      return UniversalEnglishGrammaticalRelations.getConj(cc.value());
    }
    String secondWord = secondIWord.value().toLowerCase();
    if (newConj.equals("but")) {
      if (secondWord.equals("rather")) {
        newConj = "negcc";
      } else if (secondWord.equals("also")) {
        newConj = "and";
      }
    } else if (newConj.equals("if") && secondWord.equals("not")) {
      newConj = "negcc";
    } else if (newConj.equals("instead") && secondWord.equals("of")) {
      newConj = "negcc";
    } else if (newConj.equals("rather") && secondWord.equals("than")) {
      newConj = "negcc";
    } else if (newConj.equals("as") && secondWord.equals("well")) {
      newConj = "and";
    } else if (newConj.equals("not") && secondWord.equals("to")) {
      IndexedWord thirdIWord = sg.getNodeByIndexSafe(pos + 2);
      String thirdWord = thirdIWord != null ? thirdIWord.value().toLowerCase() : null;
      if (thirdWord != null && thirdWord.equals("mention")) {
        newConj = "and";
      }
    }  
    return UniversalEnglishGrammaticalRelations.getConj(newConj);
  }


  private static void treatCC(SemanticGraph sg) {
    
    // Construct a map from tree nodes to the set of typed
    // dependencies in which the node appears as dependent.
    Map<IndexedWord, Set<SemanticGraphEdge>> map = Generics.newHashMap();
    // Construct a map of tree nodes being governor of a subject grammatical
    // relation to that relation
    Map<IndexedWord, SemanticGraphEdge> subjectMap = Generics.newHashMap();
    // Construct a set of TreeGraphNodes with a passive auxiliary on them
    Set<IndexedWord> withPassiveAuxiliary = Generics.newHashSet();
    // Construct a map of tree nodes being governor of an object grammatical
    // relation to that relation
    // Map<TreeGraphNode, TypedDependency> objectMap = new
    // HashMap<TreeGraphNode, TypedDependency>();

    List<IndexedWord> rcmodHeads = Generics.newArrayList();
    List<IndexedWord> prepcDep = Generics.newArrayList();

    
    for (SemanticGraphEdge edge : sg.edgeIterable()) {
      if (!map.containsKey(edge.getDependent())) {
        // NB: Here and in other places below, we use a TreeSet (which extends
        // SortedSet) to guarantee that results are deterministic)
        map.put(edge.getDependent(), new TreeSet<SemanticGraphEdge>());
      }
      map.get(edge.getDependent()).add(edge);

      if (edge.getRelation().equals(AUX_PASSIVE_MODIFIER)) {
        withPassiveAuxiliary.add(edge.getGovernor());
      }

      // look for subjects
      if (edge.getRelation().getParent() == NOMINAL_SUBJECT 
          || edge.getRelation().getParent() == SUBJECT 
          || edge.getRelation().getParent() == CLAUSAL_SUBJECT) {
        if (!subjectMap.containsKey(edge.getGovernor())) {
          subjectMap.put(edge.getGovernor(), edge);
        }
      }

      // look for objects
      // this map was only required by the code commented out below, so comment
      // it out too
      // if (typedDep.reln() == DIRECT_OBJECT) {
      // if (!objectMap.containsKey(typedDep.gov())) {
      // objectMap.put(typedDep.gov(), typedDep);
      // }
      // }

      // look for rcmod relations
      if (edge.getRelation() == RELATIVE_CLAUSE_MODIFIER) {
        rcmodHeads.add(edge.getGovernor());
      }
      // look for prepc relations: put the dependent of such a relation in the
      // list
      // to avoid wrong propagation of dobj
      if (edge.getRelation().toString().startsWith("acl:") || edge.getRelation().toString().startsWith("advcl:")) {
        prepcDep.add(edge.getDependent());
      }
    }

    // System.err.println(map);
    // if (DEBUG) System.err.println("Subject map: " + subjectMap);
    // if (DEBUG) System.err.println("Object map: " + objectMap);
    // System.err.println(rcmodHeads);

    // create a new list of typed dependencies
    //Collection<TypedDependency> newTypedDeps = new ArrayList<TypedDependency>(list);

    SemanticGraph sgCopy = sg.makeSoftCopy();
    
    // find typed deps of form conj(gov,dep)
    for (SemanticGraphEdge edge: sgCopy.edgeIterable()) {
      if (UniversalEnglishGrammaticalRelations.getConjs().contains(edge.getRelation())) {
        IndexedWord gov = edge.getGovernor();
        IndexedWord dep = edge.getDependent();

        // look at the dep in the conjunct
        Set<SemanticGraphEdge> gov_relations = map.get(gov);
        // System.err.println("gov " + gov);
        if (gov_relations != null) {
          for (SemanticGraphEdge edge1 : gov_relations) {
            // System.err.println("gov rel " + td1);
            IndexedWord newGov = edge1.getGovernor();
            // in the case of errors in the basic dependencies, it
            // is possible to have overlapping newGov & dep
            if (newGov.equals(dep)) {
              continue;
            }
            
            GrammaticalRelation newRel = edge1.getRelation();
            //TODO: Do we want to copy case markers here?
            if (newRel != ROOT && newRel != CASE_MARKER) {
              if (rcmodHeads.contains(gov) && rcmodHeads.contains(dep)) {
                // to prevent wrong propagation in the case of long dependencies in relative clauses
                if (newRel != DIRECT_OBJECT && newRel != NOMINAL_SUBJECT) {
                  if (DEBUG) {
                    System.err.println("Adding new " + newRel + " dependency from " + newGov + " to " + dep + " (subj/obj case)");
                  }
                  sg.addEdge(newGov, dep, newRel, Double.NEGATIVE_INFINITY, true);
                }
              } else {
                if (DEBUG) {
                  System.err.println("Adding new " + newRel + " dependency from " + newGov + " to " + dep);
                }
                sg.addEdge(newGov, dep, newRel, Double.NEGATIVE_INFINITY, true);
              }
            }
          }
        }

        // propagate subjects
        // look at the gov in the conjunct: if it is has a subject relation,
        // the dep is a verb and the dep doesn't have a subject relation
        // then we want to add a subject relation for the dep.
        // (By testing for the dep to be a verb, we are going to miss subject of
        // copular verbs! but
        // is it safe to relax this assumption?? i.e., just test for the subject
        // part)
        // CDM 2008: I also added in JJ, since participial verbs are often
        // tagged JJ
        String tag = dep.tag();
        if (subjectMap.containsKey(gov) && (tag.startsWith("VB") || tag.startsWith("JJ")) && ! subjectMap.containsKey(dep)) {
          SemanticGraphEdge tdsubj = subjectMap.get(gov);
          // check for wrong nsubjpass: if the new verb is VB or VBZ or VBP or JJ, then
          // add nsubj (if it is tagged correctly, should do this for VBD too, but we don't)
          GrammaticalRelation relation = tdsubj.getRelation();
          if (relation == NOMINAL_PASSIVE_SUBJECT) {
            if (isDefinitelyActive(tag)) {
              relation = NOMINAL_SUBJECT;
            }
          } else if (relation == CLAUSAL_PASSIVE_SUBJECT) {
            if (isDefinitelyActive(tag)) {
              relation = CLAUSAL_SUBJECT;
            }
          } else if (relation == NOMINAL_SUBJECT) {
            if (withPassiveAuxiliary.contains(dep)) {
              relation = NOMINAL_PASSIVE_SUBJECT;
            }
          } else if (relation == CLAUSAL_SUBJECT) {
            if (withPassiveAuxiliary.contains(dep)) {
              relation = CLAUSAL_PASSIVE_SUBJECT;
            }
          }
          if (DEBUG) {
            System.err.println("Adding new " + relation + " dependency from " + dep + " to " + tdsubj.getDependent() + " (subj propagation case)");
          }
          sg.addEdge(dep, tdsubj.getDependent(), relation, Double.NEGATIVE_INFINITY, true);
        }

        // propagate objects
        // cdm july 2010: This bit of code would copy a dobj from the first
        // clause to a later conjoined clause if it didn't
        // contain its own dobj or prepc. But this is too aggressive and wrong
        // if the later clause is intransitive
        // (including passivized cases) and so I think we have to not have this
        // done always, and see no good "sometimes" heuristic.
        // IF WE WERE TO REINSTATE, SHOULD ALSO NOT ADD OBJ IF THERE IS A ccomp
        // (SBAR).
        // if (objectMap.containsKey(gov) &&
        // dep.tag().startsWith("VB") && ! objectMap.containsKey(dep)
        // && ! prepcDep.contains(gov)) {
        // TypedDependency tdobj = objectMap.get(gov);
        // if (DEBUG) {
        // System.err.println("Adding new " + tdobj.reln() + " dependency from "
        // + dep + " to " + tdobj.dep() + " (obj propagation case)");
        // }
        // newTypedDeps.add(new TypedDependency(tdobj.reln(), dep,
        // tdobj.dep()));
        // }
      }
    }
  }

  private static boolean isDefinitelyActive(String tag) {
    // we should include VBD, but don't as it is often a tagging mistake.
    return tag.equals("VB") || tag.equals("VBZ") || tag.equals("VBP") || tag.startsWith("JJ");
  }


  /**
   * This method will collapse a referent relation such as follows. e.g.:
   * "The man that I love ... " ref(man, that) dobj(love, that) -> ref(man, that) dobj(love,
   * man)
   */
  private static void collapseReferent(SemanticGraph sg) {
    // find typed deps of form ref(gov, dep)
    // put them in a List for processing
    List<SemanticGraphEdge> refs = new ArrayList<SemanticGraphEdge>(sg.findAllRelns(REFERENT));
   
    SemanticGraph sgCopy = sg.makeSoftCopy();
    
    // now substitute target of referent where possible
    for (SemanticGraphEdge ref : refs) {
      IndexedWord dep = ref.getDependent();// take the relative word
      IndexedWord ant = ref.getGovernor();// take the antecedent

      for (Iterator<SemanticGraphEdge> iter = sgCopy.incomingEdgeIterator(dep); iter.hasNext(); ) {
        SemanticGraphEdge edge = iter.next();

        // the last condition below maybe shouldn't be necessary, but it has
        // helped stop things going haywire a couple of times (it stops the
        // creation of a unit cycle that probably leaves something else
        // disconnected) [cdm Jan 2010]
        if (edge.getRelation() != REFERENT && ! edge.getGovernor().equals(ant)) {
          sg.removeEdge(edge);
          sg.addEdge(edge.getGovernor(), ant, edge.getRelation(), Double.NEGATIVE_INFINITY, true);
        }
      }
    }
  }
  
  /**
   * Look for ref rules for a given word.  We look through the
   * children and grandchildren of the acl:relcl dependency, and if any
   * children or grandchildren depend on a that/what/which/etc word,
   * we take the leftmost that/what/which/etc word as the dependent
   * for the ref TypedDependency.
   */
  private static void addRef(SemanticGraph sg) {
    for (SemanticGraphEdge edge : sg.findAllRelns(RELATIVE_CLAUSE_MODIFIER)) {
      IndexedWord head = edge.getGovernor();
      IndexedWord modifier = edge.getDependent();

      SemanticGraphEdge leftChildEdge = null;
      for (SemanticGraphEdge childEdge : sg.outgoingEdgeIterable(modifier)) {
        if (EnglishPatterns.RELATIVIZING_WORD_PATTERN.matcher(childEdge.getDependent().value()).matches() &&
            (leftChildEdge == null || childEdge.getDependent().index() < leftChildEdge.getDependent().index())) {
          leftChildEdge = childEdge;
        }
      }
      
      SemanticGraphEdge leftGrandchildEdge = null;
      for (SemanticGraphEdge childEdge : sg.outgoingEdgeIterable(modifier)) {
        for (SemanticGraphEdge grandchildEdge : sg.outgoingEdgeIterable(childEdge.getDependent())) {
          if (EnglishPatterns.RELATIVIZING_WORD_PATTERN.matcher(grandchildEdge.getDependent().value()).matches() &&
              (leftGrandchildEdge == null || grandchildEdge.getDependent().index() < leftGrandchildEdge.getDependent().index())) {
            leftGrandchildEdge = grandchildEdge;
          }
        }
      }
      
      IndexedWord newDep = null;
      if (leftGrandchildEdge != null
          && (leftChildEdge == null || leftGrandchildEdge.getDependent().index() < leftChildEdge.getDependent().index())) {
        newDep = leftGrandchildEdge.getDependent();  
      } else if (leftChildEdge != null) {
        newDep = leftChildEdge.getDependent();
      }
      if (newDep != null && ! sg.containsEdge(head, newDep)) {
        sg.addEdge(head, newDep, REFERENT, Double.NEGATIVE_INFINITY, true);
      }
    }
  }

  /**
   * Add extra nsubj dependencies when collapsing basic dependencies.
   * <br/>
   * In the general case, we look for an aux modifier under an xcomp
   * modifier, and assuming there aren't already associated nsubj
   * dependencies as daughters of the original xcomp dependency, we
   * add nsubj dependencies for each nsubj daughter of the aux.
   * <br/>
   * There is also a special case for "to" words, in which case we add
   * a dependency if and only if there is no nsubj associated with the
   * xcomp and there is no other aux dependency.  This accounts for
   * sentences such as "he decided not to" with no following verb.
   */
  private static void addExtraNSubj(SemanticGraph sg) {
    
    for (SemanticGraphEdge xcomp : sg.findAllRelns(XCLAUSAL_COMPLEMENT)) {
      IndexedWord modifier = xcomp.getDependent();
      IndexedWord head = xcomp.getGovernor();

      boolean hasSubjectDaughter = false;
      boolean hasAux = false;
      List<IndexedWord> subjects = Generics.newArrayList();
      List<IndexedWord> objects = Generics.newArrayList();
      for (SemanticGraphEdge dep : sg.edgeIterable()) {
        // already have a subject dependency
        if ((dep.getRelation() == NOMINAL_SUBJECT || dep.getRelation() == NOMINAL_PASSIVE_SUBJECT) && dep.getGovernor().equals(modifier)) {
          hasSubjectDaughter = true;
          break;
        }

        if ((dep.getRelation() == AUX_MODIFIER || dep.getRelation() == MARKER) && dep.getGovernor().equals(modifier)) {
          hasAux = true;
        }

        if ((dep.getRelation() == NOMINAL_SUBJECT || dep.getRelation() == NOMINAL_PASSIVE_SUBJECT) && dep.getGovernor().equals(head)) {
          subjects.add(dep.getDependent());
        }

        if (dep.getRelation() == DIRECT_OBJECT && dep.getGovernor().equals(head)) {
          objects.add(dep.getDependent());
        }
      }

      // if we already have an nsubj dependency, no need to add an extra nsubj
      if (hasSubjectDaughter) {
        continue;
      }

      if ((modifier.value().equalsIgnoreCase("to") && hasAux) ||
          (!modifier.value().equalsIgnoreCase("to") && !hasAux)) {
        continue;
      }

      // In general, we find that the objects of the verb are better
      // for extra nsubj than the original nsubj of the verb.  For example,
      // "Many investors wrote asking the SEC to require ..."
      // There is no nsubj of asking, but the dobj, SEC, is the extra nsubj of require.
      // Similarly, "The law tells them when to do so"
      // Instead of nsubj(do, law) we want nsubj(do, them)
      if (objects.size() > 0) {
        for (IndexedWord object : objects) {
          if ( ! sg.containsEdge(modifier, object))
            sg.addEdge(modifier, object, NOMINAL_SUBJECT, Double.NEGATIVE_INFINITY, true);
        }
      } else {
        for (IndexedWord subject : subjects) {
          if ( ! sg.containsEdge(modifier, subject))
            sg.addEdge(modifier, subject, NOMINAL_SUBJECT, Double.NEGATIVE_INFINITY, true);
        }
      }
    }
  }

  private static SemgrexPattern CORRECT_SUBJPASS_PATTERN = SemgrexPattern.compile("{}=gov >auxpass {} >/^(nsubj|csubj)$/ {}=subj");
  
  /**
   * This method corrects subjects of verbs for which we identified an auxpass,
   * but didn't identify the subject as passive.
   *
   * @param sg SemanticGraph to work on
   */
  private static void correctSubjPass(SemanticGraph sg) {
    
    /* If the graph doesn't have a root (most likely because
     * a parsing error, we can't match Semgrexes, so do
     * nothing. */
    if (sg.getRoots().isEmpty())
      return;
    
    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = CORRECT_SUBJPASS_PATTERN.matcher(sgCopy);

    while (matcher.find()) {
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord subj = matcher.getNode("subj");
      SemanticGraphEdge edge = sg.getEdge(gov, subj);
      
      GrammaticalRelation reln = null;
      if (edge.getRelation() == NOMINAL_SUBJECT) {
        reln = NOMINAL_PASSIVE_SUBJECT;
      } else if (edge.getRelation() == CLAUSAL_SUBJECT) {
        reln = CLAUSAL_PASSIVE_SUBJECT;
      }
      
      if (reln != null) {
        sg.removeEdge(edge);
        sg.addEdge(gov, subj, reln, Double.NEGATIVE_INFINITY, false);
      }
    }
  }

  
  
  

  
 

  // used by collapse2WP(), collapseFlatMWP(), collapse2WPbis() KEPT IN
  // ALPHABETICAL ORDER
  private static final String[] MULTIWORD_PREPS = {  "according_to", "across_from", "ahead_of", "along_with", "alongside_of", "apart_from", "as_for", "as_from", "as_of", "as_per", "as_to", "aside_from", "away_from", "based_on", "because_of", "close_by", "close_to", "contrary_to", "compared_to", "compared_with", "due_to", "depending_on", "except_for", "exclusive_of", "far_from", "followed_by", "inside_of", "instead_of", "irrespective_of", "next_to", "near_to", "off_of", "out_of", "outside_of", "owing_to", "preliminary_to",
       "preparatory_to", "previous_to", "prior_to", "pursuant_to", "regardless_of", "subsequent_to", "such_as", "thanks_to", "together_with"};

  // used by collapse3WP() KEPT IN ALPHABETICAL ORDER
  private static final String[] THREEWORD_PREPS = { "by_means_of", "in_accordance_with", "in_addition_to", "in_case_of", "in_front_of", "in_lieu_of", "in_place_of", "in_spite_of", "on_account_of", "on_behalf_of", "on_top_of", "with_regard_to", "with_respect_to" };

  

  /**
   * 
   * @param sg
   */
  private static void processMultiwordPreps(SemanticGraph sg) {
    
    /* Semgrexes require a graph with a root. */
    if (sg.getRoots().isEmpty())
      return;
    
    HashMap<String, HashSet<Integer>> bigrams = new HashMap<String, HashSet<Integer>>();
    HashMap<String, HashSet<Integer>> trigrams = new HashMap<String, HashSet<Integer>>();

    
    List<IndexedWord> vertexList = sg.vertexListSorted();
    int numWords = vertexList.size();
    
    for (int i = 1; i < numWords; i++) {
      String bigram = vertexList.get(i-1).value().toLowerCase() + "_" + vertexList.get(i).value().toLowerCase();
      
      if (bigrams.get(bigram) == null) {
        bigrams.put(bigram, new HashSet<Integer>());
      }
      
      bigrams.get(bigram).add(vertexList.get(i-1).index());
      
      if (i > 1) {
        String trigram = vertexList.get(i-2).value().toLowerCase() + "_" + bigram;
        
        if (trigrams.get(trigram) == null) {
          trigrams.put(trigram, new HashSet<Integer>());
        }
        
        trigrams.get(trigram).add(vertexList.get(i-2).index());
      }
    }
        
    for (String bigram : MULTIWORD_PREPS) {
      if (bigrams.get(bigram) == null) {
        continue;
      }
      
      for (Integer i : bigrams.get(bigram)) {
        IndexedWord w1 = sg.getNodeByIndexSafe(i);
        IndexedWord w2 = sg.getNodeByIndexSafe(i + 1);
        
        if (w1 == null || w2 == null) {
          continue;
        }
        
        IndexedWord gov1 = sg.getParent(w1);
        IndexedWord gov2 = sg.getParent(w2);
        
        if (gov1 == null || gov2 == null) {
          continue;
        }
        
        SemanticGraphEdge edge1 = sg.getEdge(gov1, w1);
        SemanticGraphEdge edge2 = sg.getEdge(gov2, w2);
        
        GrammaticalRelation reln1 = edge1.getRelation();
        GrammaticalRelation reln2 = edge2.getRelation();
        
        if (reln1 != CASE_MARKER && reln2 != CASE_MARKER) {
          continue;
        }
        
        IndexedWord caseGov = reln1 == CASE_MARKER ? gov1 : gov2;
        IndexedWord caseGovGov = sg.getParent(caseGov);
        
        
        /* Prevent cycles. */
        if (caseGovGov != null && (caseGovGov.equals(w1) || caseGovGov.equals(w2))) {
          continue;
        }
        
        sg.removeEdge(edge1);
        sg.removeEdge(edge2);
        
        sg.addEdge(caseGov, w1, CASE_MARKER, Double.NEGATIVE_INFINITY, false);
        sg.addEdge(w1, w2, MULTI_WORD_EXPRESSION, Double.NEGATIVE_INFINITY, false);
      }
    }
      
    for (String trigram : THREEWORD_PREPS) {
      if (trigrams.get(trigram) == null) {
        continue;
      }
      
      for (Integer i : trigrams.get(trigram)) {
        IndexedWord w1 = sg.getNodeByIndexSafe(i);
        IndexedWord w2 = sg.getNodeByIndexSafe(i + 1);
        IndexedWord w3 = sg.getNodeByIndexSafe(i + 2);

        if (w1 == null || w2 == null || w3 == null) {
          continue;
        }
        
        
        IndexedWord gov1 = sg.getParent(w1);
        IndexedWord gov2 = sg.getParent(w2);
        IndexedWord gov3 = sg.getParent(w3);

        if (gov1 == null || gov2 == null || gov3 == null) {
          continue;
        }

        
        SemanticGraphEdge edge1 = sg.getEdge(gov1, w1);
        SemanticGraphEdge edge2 = sg.getEdge(gov2, w2);
        SemanticGraphEdge edge3 = sg.getEdge(gov3, w3);

        
        GrammaticalRelation reln1 = edge1.getRelation();
        GrammaticalRelation reln3 = edge3.getRelation();
        
        if (reln1 != CASE_MARKER && reln3 != CASE_MARKER) {
          continue;
        }
        
        IndexedWord caseGov = reln3 == CASE_MARKER ? gov3 : gov1;
        IndexedWord caseGovGov = sg.getParent(caseGov);
        
        /* Prevent cycles. */
        if (caseGovGov != null && (caseGovGov.equals(w1) || caseGovGov.equals(w2) || caseGovGov.equals(w3))) {
          continue;
        }
        
        sg.removeEdge(edge1);
        sg.removeEdge(edge2);
        sg.removeEdge(edge3);
        
        sg.addEdge(caseGov, w1, CASE_MARKER, Double.NEGATIVE_INFINITY, false);
        sg.addEdge(w1, w2, MULTI_WORD_EXPRESSION, Double.NEGATIVE_INFINITY, false);
        sg.addEdge(w1, w3, MULTI_WORD_EXPRESSION, Double.NEGATIVE_INFINITY, false);
      }
    }
    
   
    
    
    
  }
  
  
  /**
   * Find and remove any exact duplicates from a dependency list.
   * For example, the method that "corrects" nsubj dependencies can
   * turn them into nsubjpass dependencies.  If there is some other
   * source of nsubjpass dependencies, there may now be multiple
   * copies of the nsubjpass dependency.  If the containing data type
   * is a List, they may both now be in the List.
   */
  private static void removeExactDuplicates(SemanticGraph sg) {
    sg.deleteDuplicateEdges();
  }


  public static List<GrammaticalStructure> readCoNLLXGrammaticalStructureCollection(String fileName) throws IOException {
    return readCoNLLXGrammaticalStructureCollection(fileName, UniversalEnglishGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static UniversalEnglishGrammaticalStructure buildCoNLLXGrammaticalStructure(List<List<String>> tokenFields) {
    return (UniversalEnglishGrammaticalStructure) buildCoNLLXGrammaticalStructure(tokenFields, UniversalEnglishGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static class FromDependenciesFactory
    implements GrammaticalStructureFromDependenciesFactory {
    @Override
    public UniversalEnglishGrammaticalStructure build(List<TypedDependency> tdeps, TreeGraphNode root) {
      return new UniversalEnglishGrammaticalStructure(tdeps, root);
    }
  }

} // end class UniversalEnglishGrammaticalStructure
