package edu.stanford.nlp.trees;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;

import edu.stanford.nlp.graph.DirectedMultiGraph;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
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
  protected void correctDependencies(Collection<TypedDependency> list) {
    if (DEBUG) {
      printListSorted("At correctDependencies:", list);
    }
    correctSubjPass(list);
    if (DEBUG) {
      printListSorted("After correctSubjPass:", list);
    }
    removeExactDuplicates(list);
    if (DEBUG) {
      printListSorted("After removeExactDuplicates:", list);
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
    if (DEBUG) {
      printListSorted("At postProcessDependencies:", list);
    }
    convertRel(list);
    if (DEBUG) {
      printListSorted("After converting rel:", list);
    }
  }

  @Override
  protected void getExtras(List<TypedDependency> list) {
    addRef(list);
    if (DEBUG) {
      printListSorted("After adding ref:", list);
    }

    addExtraNSubj(list);
    if (DEBUG) {
      printListSorted("After adding extra nsubj:", list);
    }
  }
  

  /* Semgrex patterns for prepositional phrases. */
  private static SemgrexPattern PASSIVE_AGENT_PATTERN = SemgrexPattern.compile("{}=gov >nmod=reln ({}=mod >case {word:/^(?i:by)$/}=c1) >auxpass {}");
  private static SemgrexPattern PREP_MW3_PATTERN = SemgrexPattern.compile("{}=gov   [>/^(nmod|advcl|acl)$/=reln ({}=mod >case ({}=c1 >mwe {}=c2 >mwe {}=c3))]");
  private static SemgrexPattern PREP_MW2_PATTERN = SemgrexPattern.compile("{}=gov   [>/^(nmod|advcl|acl)$/=reln ({}=mod >case ({}=c1 >mwe {}=c2)) | >/^(nmod|advcl|acl)$/=reln ({}=mod >case {}=c1 >case ({}=c2 !== {}=c1))]");
  private static SemgrexPattern PREP_PATTERN = SemgrexPattern.compile("{}=gov   >/^(nmod|advcl|acl)$/=reln ({}=mod >case {}=c1)");

  
  /**
   * Adds the case marker(s) to all nmod, acl and advcl relations that are 
   * modified by one or more case markers(s).
   * 
   * @see UniversalEnglishGrammaticalStructure#addCaseMarkersToReln
   */
  private static void addCaseMarkerInformation(List<TypedDependency> list) {
        
    /* passive agent */
    SemanticGraph sg = new SemanticGraph(list);
    SemgrexMatcher matcher = PASSIVE_AGENT_PATTERN.matcher(sg);
    while (matcher.find()) {
      IndexedWord caseMarker = matcher.getNode("c1");
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");
      addPassiveAgentToReln(list, gov, mod, caseMarker);
    }    
    
    List<IndexedWord> oldCaseMarkers = Generics.newArrayList();

    
    /* 3-word prepositions */
    sg = new SemanticGraph(list);
    matcher = PREP_MW3_PATTERN.matcher(sg);
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
      addCaseMarkersToReln(list, gov, mod, caseMarkers);
      
      oldCaseMarkers = caseMarkers;
    }
    
    
    /* 2-word prepositions */
    sg = new SemanticGraph(list);
    matcher = PREP_MW2_PATTERN.matcher(sg);
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
      addCaseMarkersToReln(list, gov, mod, caseMarkers);
      
      oldCaseMarkers = caseMarkers;
    }
    
    /* Single-word prepositions */
    sg = new SemanticGraph(list);
    matcher = PREP_PATTERN.matcher(sg);
    while (matcher.find()) {
      List<IndexedWord> caseMarkers = Generics.newArrayList(1);
      caseMarkers.add(matcher.getNode("c1"));
      
      if (caseMarkers.equals(oldCaseMarkers))
        continue;
      
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");
      addCaseMarkersToReln(list, gov, mod, caseMarkers);

      oldCaseMarkers = caseMarkers;
    }
  }
  

  private static void addPassiveAgentToReln(List<TypedDependency> list,
      IndexedWord gov, IndexedWord mod, IndexedWord caseMarker) {
    for (TypedDependency td: list) {
      if ( ! td.gov().equals(gov) || ! td.dep().equals(mod)) {
        continue;
      }
      td.setReln(UniversalEnglishGrammaticalRelations.getNmod("agent"));
      break;
    }
  }
  
  
  /**
   * Appends case marker information to nmod/acl/advcl relations.
   * 
   * E.g. if there is a relation nmod(gov, dep) and case(dep, prep), then
   * the nmod relation is renamed to nmod:prep.
   * 
   * If there are multiple case markers that modify dep, then they are 
   * combined to one label if they are adjacent (for mwes such as "in front of")
   * or the relation is being copied with an additional preposition in case they 
   * are not adjacent (e.g. case(Serbia-6, to-3), cc(Serbia-6, and-4), 
   * case(Serbia-6, from-5), nmod(flies-2, Serbia-6) results in a relation
   * nmod:from(flies-2, Serbia-6) and nmod:to(flies-2, Serbia-6).
   * 
   * 
   * @param list List<TypedDependency> of current dependency relations
   * @param gov governor of the nmod/acl/advcl relation
   * @param mod modifier of the nmod/acl/advcl relation
   * @param caseMarkers List<IndexedWord> of all the case markers that depend on mod
   */
  private static void addCaseMarkersToReln(List<TypedDependency> list, IndexedWord gov, IndexedWord mod, List<IndexedWord> caseMarkers) {
    
    List<TypedDependency> newDeps = Generics.newLinkedList();
    
    for (TypedDependency td: list) {
      if ( ! td.gov().equals(gov) || ! td.dep().equals(mod)) {
        continue;
      }
      
      int lastCaseMarkerIndex = 0;
      StringBuilder sb = new StringBuilder();
      boolean firstWord = true;
      for (IndexedWord cm : caseMarkers) {
        /* check for adjacency */
        if (lastCaseMarkerIndex == 0 || cm.index() == (lastCaseMarkerIndex + 1)) {
          if ( ! firstWord) {
            sb.append("_");
          }
          sb.append(cm.word());
          firstWord = false;
        } else {
          /* Should never happen as there should be never two non-adjacent case markers.
           * If it does happen nevertheless create an additional relation.
           */
          GrammaticalRelation reln = getCaseMarkedRelation(td, sb.toString().toLowerCase());
          newDeps.add(new TypedDependency(reln, gov, mod));
          sb = new StringBuilder(cm.word());
          firstWord = true;
        }
        lastCaseMarkerIndex = cm.index();
      }
      GrammaticalRelation reln = getCaseMarkedRelation(td, sb.toString().toLowerCase());
      td.setReln(reln);
      break;
    }
    
    for (TypedDependency td : newDeps) {
      list.add(td);
    }
  }
  
  private static SemgrexPattern PREP_CONJP_PATTERN = SemgrexPattern.compile("{} >case ({}=gov >cc {}=cc >conj {}=conj)");
  
  private static void expandPrepConjunctions(List<TypedDependency> list) {
    SemanticGraph sg = new SemanticGraph(list);
    SemgrexMatcher matcher = PREP_CONJP_PATTERN.matcher(sg);
    
    IndexedWord oldGov = null;
    IndexedWord oldCcDep = null;
    List<IndexedWord> conjDeps = Generics.newLinkedList();
    
    while (matcher.find()) {
      IndexedWord ccDep = matcher.getNode("cc");
      IndexedWord conjDep = matcher.getNode("conj");
      IndexedWord gov = matcher.getNode("gov");
      if (oldGov != null &&  (! gov.equals(oldGov) || ! ccDep.equals(oldCcDep))) {
        expandPrepConjunction(list, oldGov, conjDeps, oldCcDep, sg);
        conjDeps = Generics.newLinkedList();
      }
      oldCcDep = ccDep;
      oldGov = gov;
      conjDeps.add(conjDep);
    }
    
    if (oldGov != null) {
      expandPrepConjunction(list, oldGov, conjDeps, oldCcDep, sg);
    }
    
    
  }  
  
  
  private static void expandPrepConjunction(List<TypedDependency> list, IndexedWord gov, 
      List<IndexedWord> conjDeps, IndexedWord ccDep, SemanticGraph sg) {
    
    
    IndexedWord caseGov = sg.getParent(gov);
    IndexedWord caseGovGov = sg.getParent(caseGov);
    GrammaticalRelation rel = sg.reln(caseGovGov, caseGov);
    List<IndexedWord> newConjDeps = Generics.newLinkedList();
    for (IndexedWord conjDep : conjDeps) {
      IndexedWord caseGovCopy = caseGov.makeSoftCopy();
      IndexedWord caseGovGovCopy = caseGovGov.makeSoftCopy();
      
      /* Change conj(prep-1, prep-2) to case(prep-1-gov, prep-2) */
      for (TypedDependency td : list) {
        if ( ! td.dep().equals(conjDep) || ! td.gov().equals(gov)) {
          continue;
        }
        
        td.setGov(caseGovCopy);
        td.setReln(CASE_MARKER);
        break;
      }
      
      /* Add relation to copy node. */
      list.add(new TypedDependency(rel, caseGovGovCopy, caseGovCopy));
      list.add(new TypedDependency(CONJUNCT, caseGovGov, caseGovGovCopy));
      newConjDeps.add(caseGovGovCopy);
      
      /* Attach all children except case markers of caseGov to caseGovCopy. */
      List<TypedDependency> newDeps = Generics.newLinkedList();
      for (TypedDependency td : list) {
        if ( ! td.gov().equals(caseGov) || td.reln() == CASE_MARKER) {
          continue;
        }
        newDeps.add(new TypedDependency(td.reln(), caseGovCopy, td.dep()));
      }
      
      list.addAll(newDeps);
      
    }
    
    /* Attach CC node to caseGov */ 
    for (TypedDependency td : list) {
      if ( ! td.dep().equals(ccDep) || ! td.gov().equals(gov)) {
        continue;
      }
      td.setGov(caseGovGov);
    }
    
    /* Add conjunction information for these relations already at this point.
     * It could be that we add several coordinating conjunctions while collapsing
     * and we might not know which conjunction belongs to which conjunct at a later
     * point.
     */
    addConjToReln(list, caseGovGov, newConjDeps, ccDep, sg);
  }
  
  
  private static SemgrexPattern PP_CONJP_PATTERN = SemgrexPattern.compile("{} >/^(nmod|acl|advcl)$/ (({}=gov >case {}) >cc {}=cc >conj ({}=conj >case {}))");
  
  private static void expandPPConjunctions(List<TypedDependency> list) {
    SemanticGraph sg = new SemanticGraph(list);
    SemgrexMatcher matcher = PP_CONJP_PATTERN.matcher(sg);
    
    IndexedWord oldGov = null;
    IndexedWord oldCcDep = null;
    List<IndexedWord> conjDeps = Generics.newLinkedList();
    
    while (matcher.find()) {
      IndexedWord conjDep = matcher.getNode("conj");
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord ccDep = matcher.getNode("cc");

      if (oldGov != null &&  (! gov.equals(oldGov) || ! ccDep.equals(oldCcDep))) {
        expandPPConjunction(list, oldGov, conjDeps, oldCcDep, sg);
        conjDeps = Generics.newLinkedList();
      }
      oldCcDep = ccDep;
      oldGov = gov;
      conjDeps.add(conjDep);
    }
    
    if (oldGov != null) {
      expandPPConjunction(list, oldGov, conjDeps, oldCcDep, sg);
    }
    
    
  }  
  
  
  private static void expandPPConjunction(List<TypedDependency> list, IndexedWord gov, 
      List<IndexedWord> conjDeps, IndexedWord ccDep, SemanticGraph sg) {
    
    
    IndexedWord nmodGov = sg.getParent(gov);
    GrammaticalRelation rel = sg.reln(nmodGov, gov);  
    int copyCount = 1;
    List<IndexedWord> newConjDeps = Generics.newLinkedList();
    for (IndexedWord conjDep : conjDeps) {
      IndexedWord nmodGovCopy = nmodGov.makeSoftCopy(copyCount++);
      
      /* Change conj(nmod-1, nmod-2) to nmod(nmod-1-gov, nmod-2) */
      for (TypedDependency td : list) {
        if ( ! td.dep().equals(conjDep) || ! td.gov().equals(gov)) {
          continue;
        }
        
        td.setGov(nmodGovCopy);
        td.setReln(rel);
        
        break;
      }
      
      /* Add relation to copy node. */
      list.add(new TypedDependency(CONJUNCT, nmodGov, nmodGovCopy));
      newConjDeps.add(nmodGovCopy);
    }
    
    /* Attach CC node to nmodGov */ 
    for (TypedDependency td : list) {
      if ( ! td.dep().equals(ccDep) || ! td.gov().equals(gov)) {
        continue;
      }
      td.setGov(nmodGov);
    }
    
    /* Add conjunction information for these relations already at this point.
     * It could be that we add several coordinating conjunctions while collapsing
     * and we might not know which conjunction belongs to which conjunct at a later
     * point.
     */
    addConjToReln(list, nmodGov, newConjDeps, ccDep, sg);
  }
  
  
  /**
   * 
   * Returns a GrammaticalRelation which combines the original relation and 
   * the preposition.
   * 
   */
  private static GrammaticalRelation getCaseMarkedRelation(TypedDependency td, String relationName) {
    GrammaticalRelation reln = null;
    if (td.reln() == NOMINAL_MODIFIER) {
      reln = UniversalEnglishGrammaticalRelations.getNmod(relationName);
    } else if (td.reln() == ADV_CLAUSE_MODIFIER) {
      reln = UniversalEnglishGrammaticalRelations.getAdvcl(relationName);
    } else if (td.reln() == CLAUSAL_MODIFIER) {
      reln = UniversalEnglishGrammaticalRelations.getAcl(relationName);
    }
    return reln;
  }

  
  private static SemgrexPattern CONJUNCTION_PATTERN = SemgrexPattern.compile("{}=gov >cc {}=cc >conj {}=conj");
  
  private static void addConjInformation(List<TypedDependency> list) {
    
        
    SemanticGraph sg = new SemanticGraph(list);
    SemgrexMatcher matcher = CONJUNCTION_PATTERN.matcher(sg);
    
    IndexedWord oldGov = null;
    IndexedWord oldCcDep = null;
    List<IndexedWord> conjDeps = Generics.newLinkedList();
    
    while (matcher.find()) {
      IndexedWord conjDep = matcher.getNode("conj");
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord ccDep = matcher.getNode("cc");
      if (oldGov != null &&  (! gov.equals(oldGov) || ! ccDep.equals(oldCcDep))) {
        addConjToReln(list, oldGov, conjDeps, oldCcDep, sg);
        conjDeps = Generics.newLinkedList();
      }
      oldCcDep = ccDep;
      conjDeps.add(conjDep);
      oldGov = gov;
    }
    
    if (oldGov != null) {
      addConjToReln(list, oldGov, conjDeps, oldCcDep, sg);
    }
    
  }
  
  private static void addConjToReln(List<TypedDependency> list,
      IndexedWord gov, List<IndexedWord> conjDeps, IndexedWord ccDep, SemanticGraph sg) {
    for (IndexedWord conjDep : conjDeps) {
      for (TypedDependency td : list) {
        if ( ! td.gov().equals(gov) || ! td.dep().equals(conjDep)) {
          continue;
        }
        if (td.reln() == CONJUNCT || conjDep.index() > ccDep.index()) {
          td.setReln(conjValue(ccDep, sg));
        }
        break;
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
   private static void convertRel(List<TypedDependency> list) {
    List<TypedDependency> newDeps = new ArrayList<TypedDependency>();
    
    for (TypedDependency prep : list) {
      if (prep.reln() != PREPOSITION) {
        continue;
      }

      for (TypedDependency nmod : list) {

        // todo: It would also be good to add a rule here to prefer ccomp nsubj over dobj if there is a ccomp with no subj
        // then we could get right: Which eco-friendly options do you think there will be on the new Lexus?

        if (nmod.reln() != NOMINAL_MODIFIER && nmod.reln() != RELATIVE) {
          continue;
        }
        if (!nmod.gov().equals(prep.gov()) || prep.dep().index() < nmod.dep().index()) {
          continue;
        }

        prep.setReln(CASE_MARKER);
        prep.setGov(nmod.dep());
        
        if (nmod.reln() == RELATIVE) {
          nmod.setReln(NOMINAL_MODIFIER);
        }
        
        break;
      }
    }
    
    /* Rename remaining "rel" and "prep" clauses */
    for (TypedDependency rel : list) {
      if (rel.reln() == RELATIVE) {
        rel.setReln(DIRECT_OBJECT);
      } else if (rel.reln() == PREPOSITION) {
        rel.setReln(CASE_MARKER);
      }
      
    }
      
    for (TypedDependency dep : newDeps) {
      if (!list.contains(dep)) {
        list.add(dep);
      }
    }
  }

  /**
   * Destructively modifies this {@code Collection<TypedDependency>}
   * by collapsing several types of transitive pairs of dependencies.
   * If called with a tree of dependencies and both CCprocess and
   * includeExtras set to false, then the tree structure is preserved.
   * <dl>
   * <dt>prepositional object dependencies: pobj</dt>
   * <dd>
   * <code>prep(cat, in)</code> and <code>pobj(in, hat)</code> are collapsed to
   * <code>prep_in(cat, hat)</code></dd>
   * <dt>prepositional complement dependencies: pcomp</dt>
   * <dd>
   * <code>prep(heard, of)</code> and <code>pcomp(of, attacking)</code> are
   * collapsed to <code>prepc_of(heard, attacking)</code></dd>
   * <dt>conjunct dependencies</dt>
   * <dd>
   * <code>cc(investors, and)</code> and
   * <code>conj(investors, regulators)</code> are collapsed to
   * <code>conj_and(investors,regulators)</code></dd>
   * <dt>possessive dependencies: possessive</dt>
   * <dd>
   * <code>possessive(Montezuma, 's)</code> will be erased. This is like a collapsing, but
   * due to the flatness of NPs, two dependencies are not actually composed.</dd>
   * <dt>For relative clauses, it will collapse referent</dt>
   * <dd>
   * <code>ref(man, that)</code> and <code>dobj(love, that)</code> are collapsed
   * to <code>dobj(love, man)</code></dd>
   * </dl>
   */
  @Override
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess, Extras includeExtras) {
    if (DEBUG) {
      printListSorted("collapseDependencies: CCproc: " + CCprocess + " includeExtras: " + includeExtras, list);
    }
    correctDependencies(list);
    if (DEBUG) {
      printListSorted("After correctDependencies:", list);
    }
    
    expandPrepConjunctions(list);
    if (DEBUG) {
      printListSorted("After expandPrepConjunctions:", list);
    }
    
    expandPPConjunctions(list);
    if (DEBUG) {
      printListSorted("After expandPPConjunctions:", list);
    }
    
    addCaseMarkerInformation(list);
    if (DEBUG) {
      printListSorted("After addCaseMarkerInformation:", list);
    }

    addConjInformation(list);
    if (DEBUG) {
      printListSorted("After addConjInformation:", list);
    }
    
    if (includeExtras.doRef) {
      addRef(list);
      if (DEBUG) {
        printListSorted("After adding ref:", list);
      }

      if (includeExtras.collapseRef) {
        collapseReferent(list);
        if (DEBUG) {
          printListSorted("After collapse referent:", list);
        }
      }
    }

    if (CCprocess) {
      treatCC(list);
      if (DEBUG) {
        printListSorted("After treatCC:", list);
      }
    }

    if (includeExtras.doSubj) {
      addExtraNSubj(list);
      if (DEBUG) {
        printListSorted("After adding extra nsubj:", list);
      }

      correctSubjPass(list);
      if (DEBUG) {
        printListSorted("After correctSubjPass:", list);
      }
    }

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
   * but not, if not, instead of, rather than, but rather GO TO negcc <br>
   * as well as, not to mention, but also, & GO TO and.
   *
   * @param cc The head dependency of the conjunction marker
   * @param sg The complete current semantic graph
   * @return A GrammaticalRelation made from a normalized form of that
   *         conjunction.
   */
  private static GrammaticalRelation conjValue(IndexedWord cc, SemanticGraph sg) {
    
    int pos = cc.index();
    String newConj = cc.word().toLowerCase();

    if (newConj.equals("not")) {
      IndexedWord prevWord = sg.getNodeByIndexSafe(pos - 1);
      if (prevWord != null && prevWord.word().toLowerCase().equals("but")) {
        return UniversalEnglishGrammaticalRelations.getConj("negcc");
      }
    }
    
    IndexedWord secondIWord = sg.getNodeByIndexSafe(pos + 1);
    
    if (secondIWord == null) {
      return UniversalEnglishGrammaticalRelations.getConj(cc.word());
    }
    String secondWord = secondIWord.word().toLowerCase();
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
      String thirdWord = thirdIWord != null ? thirdIWord.word().toLowerCase() : null;
      if (thirdWord != null && thirdWord.equals("mention")) {
        newConj = "and";
      }
    }
    
  
    return UniversalEnglishGrammaticalRelations.getConj(newConj);
  }


  private static void treatCC(Collection<TypedDependency> list) {
    // Construct a map from tree nodes to the set of typed
    // dependencies in which the node appears as dependent.
    Map<IndexedWord, Set<TypedDependency>> map = Generics.newHashMap();
    // Construct a map of tree nodes being governor of a subject grammatical
    // relation to that relation
    Map<IndexedWord, TypedDependency> subjectMap = Generics.newHashMap();
    // Construct a set of TreeGraphNodes with a passive auxiliary on them
    Set<IndexedWord> withPassiveAuxiliary = Generics.newHashSet();
    // Construct a map of tree nodes being governor of an object grammatical
    // relation to that relation
    // Map<TreeGraphNode, TypedDependency> objectMap = new
    // HashMap<TreeGraphNode, TypedDependency>();

    List<IndexedWord> rcmodHeads = Generics.newArrayList();
    List<IndexedWord> prepcDep = Generics.newArrayList();

    for (TypedDependency typedDep : list) {
      if (!map.containsKey(typedDep.dep())) {
        // NB: Here and in other places below, we use a TreeSet (which extends
        // SortedSet) to guarantee that results are deterministic)
        map.put(typedDep.dep(), new TreeSet<TypedDependency>());
      }
      map.get(typedDep.dep()).add(typedDep);

      if (typedDep.reln().equals(AUX_PASSIVE_MODIFIER)) {
        withPassiveAuxiliary.add(typedDep.gov());
      }

      // look for subjects
      if (typedDep.reln().getParent() == NOMINAL_SUBJECT || typedDep.reln().getParent() == SUBJECT || typedDep.reln().getParent() == CLAUSAL_SUBJECT) {
        if (!subjectMap.containsKey(typedDep.gov())) {
          subjectMap.put(typedDep.gov(), typedDep);
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
      if (typedDep.reln() == RELATIVE_CLAUSE_MODIFIER) {
        rcmodHeads.add(typedDep.gov());
      }
      // look for prepc relations: put the dependent of such a relation in the
      // list
      // to avoid wrong propagation of dobj
      if (typedDep.reln().toString().startsWith("acl:") || typedDep.reln().toString().startsWith("advcl:")) {
        prepcDep.add(typedDep.dep());
      }
    }

    // System.err.println(map);
    // if (DEBUG) System.err.println("Subject map: " + subjectMap);
    // if (DEBUG) System.err.println("Object map: " + objectMap);
    // System.err.println(rcmodHeads);

    // create a new list of typed dependencies
    Collection<TypedDependency> newTypedDeps = new ArrayList<TypedDependency>(list);

    // find typed deps of form conj(gov,dep)
    for (TypedDependency td : list) {
      if (UniversalEnglishGrammaticalRelations.getConjs().contains(td.reln())) {
        IndexedWord gov = td.gov();
        IndexedWord dep = td.dep();

        // look at the dep in the conjunct
        Set<TypedDependency> gov_relations = map.get(gov);
        // System.err.println("gov " + gov);
        if (gov_relations != null) {
          for (TypedDependency td1 : gov_relations) {
            // System.err.println("gov rel " + td1);
            IndexedWord newGov = td1.gov();
            // in the case of errors in the basic dependencies, it
            // is possible to have overlapping newGov & dep
            if (newGov.equals(dep)) {
              continue;
            }
            GrammaticalRelation newRel = td1.reln();
            if (newRel != ROOT) {
              if (rcmodHeads.contains(gov) && rcmodHeads.contains(dep)) {
                // to prevent wrong propagation in the case of long dependencies in relative clauses
                if (newRel != DIRECT_OBJECT && newRel != NOMINAL_SUBJECT) {
                  if (DEBUG) {
                    System.err.println("Adding new " + newRel + " dependency from " + newGov + " to " + dep + " (subj/obj case)");
                  }
                  newTypedDeps.add(new TypedDependency(newRel, newGov, dep));
                }
              } else {
                if (DEBUG) {
                  System.err.println("Adding new " + newRel + " dependency from " + newGov + " to " + dep);
                }
                newTypedDeps.add(new TypedDependency(newRel, newGov, dep));
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
          TypedDependency tdsubj = subjectMap.get(gov);
          // check for wrong nsubjpass: if the new verb is VB or VBZ or VBP or JJ, then
          // add nsubj (if it is tagged correctly, should do this for VBD too, but we don't)
          GrammaticalRelation relation = tdsubj.reln();
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
            System.err.println("Adding new " + relation + " dependency from " + dep + " to " + tdsubj.dep() + " (subj propagation case)");
          }
          newTypedDeps.add(new TypedDependency(relation, dep, tdsubj.dep()));
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
    list.clear();
    list.addAll(newTypedDeps);
  }

  private static boolean isDefinitelyActive(String tag) {
    // we should include VBD, but don't as it is often a tagging mistake.
    return tag.equals("VB") || tag.equals("VBZ") || tag.equals("VBP") || tag.startsWith("JJ");
  }


 
  /**
   * This method will collapse a referent relation such as follows. e.g.:
   * "The man that I love ... " ref(man, that) dobj(love, that) -> dobj(love,
   * man)
   */
  private static void collapseReferent(Collection<TypedDependency> list) {
    // find typed deps of form ref(gov, dep)
    // put them in a List for processing; remove them from the set of deps
    List<TypedDependency> refs = new ArrayList<TypedDependency>();
    for (Iterator<TypedDependency> iter = list.iterator(); iter.hasNext();) {
      TypedDependency td = iter.next();
      if (td.reln() == REFERENT) {
        refs.add(td);
        //iter.remove();
      }
    }

    // now substitute target of referent where possible
    for (TypedDependency ref : refs) {
      IndexedWord dep = ref.dep();// take the relative word
      IndexedWord ant = ref.gov();// take the antecedent
      for (TypedDependency td : list) {
        // the last condition below maybe shouldn't be necessary, but it has
        // helped stop things going haywire a couple of times (it stops the
        // creation of a unit cycle that probably leaves something else
        // disconnected) [cdm Jan 2010]
        if (td.dep().equals(dep) && td.reln() != REFERENT && !td.gov().equals(ant)) {
          if (DEBUG) {
            System.err.print("referent: changing " + td);
          }
          td.setDep(ant);
          td.setExtra();
          if (DEBUG) {
            System.err.println(" to " + td);
          }
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
  private static void addRef(Collection<TypedDependency> list) {
    List<TypedDependency> newDeps = new ArrayList<TypedDependency>();

    for (TypedDependency rcmod : list) {
      if (rcmod.reln() != RELATIVE_CLAUSE_MODIFIER) {
        // we only add ref dependencies across relative clauses
        continue;
      }

      IndexedWord head = rcmod.gov();
      IndexedWord modifier = rcmod.dep();

      TypedDependency leftChild = null;
      for (TypedDependency child : list) {
        if (child.gov().equals(modifier) &&
                EnglishPatterns.RELATIVIZING_WORD_PATTERN.matcher(child.dep().value()).matches() &&
            (leftChild == null || child.dep().index() < leftChild.dep().index())) {
          leftChild = child;
        }
      }
      
      // TODO: could be made more efficient
      TypedDependency leftGrandchild = null;
      for (TypedDependency child : list) {
        if (!child.gov().equals(modifier)) {
          continue;
        }
        for (TypedDependency grandchild : list) {
          if (grandchild.gov().equals(child.dep()) &&
              EnglishPatterns.RELATIVIZING_WORD_PATTERN.matcher(grandchild.dep().value()).matches() &&
              (leftGrandchild == null || grandchild.dep().index() < leftGrandchild.dep().index())) {
            leftGrandchild = grandchild;
          }
        }
      }

      TypedDependency newDep = null;
      if (leftGrandchild != null && (leftChild == null || leftGrandchild.dep().index() < leftChild.dep().index())) {
        newDep = new TypedDependency(REFERENT, head, leftGrandchild.dep());
      } else if (leftChild != null) {
        newDep = new TypedDependency(REFERENT, head, leftChild.dep());
      }
      if (newDep != null) {
        newDeps.add(newDep);
      }
    }

    for (TypedDependency newDep : newDeps) {
      if (!list.contains(newDep)) {
        newDep.setExtra();
        list.add(newDep);
      }
    }
  }

  /**
   * Add extra nsubj dependencies when collapsing basic dependencies.
   * <br>
   * In the general case, we look for an aux modifier under an xcomp
   * modifier, and assuming there aren't already associated nsubj
   * dependencies as daughters of the original xcomp dependency, we
   * add nsubj dependencies for each nsubj daughter of the aux.
   * <br>
   * There is also a special case for "to" words, in which case we add
   * a dependency if and only if there is no nsubj associated with the
   * xcomp and there is no other aux dependency.  This accounts for
   * sentences such as "he decided not to" with no following verb.
   */
  private static void addExtraNSubj(Collection<TypedDependency> list) {
    List<TypedDependency> newDeps = new ArrayList<TypedDependency>();

    for (TypedDependency xcomp : list) {
      if (xcomp.reln() != XCLAUSAL_COMPLEMENT) {
        // we only add extra nsubj dependencies to some xcomp dependencies
        continue;
      }

      IndexedWord modifier = xcomp.dep();
      IndexedWord head = xcomp.gov();

      boolean hasSubjectDaughter = false;
      boolean hasAux = false;
      List<IndexedWord> subjects = Generics.newArrayList();
      List<IndexedWord> objects = Generics.newArrayList();
      for (TypedDependency dep : list) {
        // already have a subject dependency
        if ((dep.reln() == NOMINAL_SUBJECT || dep.reln() == NOMINAL_PASSIVE_SUBJECT) && dep.gov().equals(modifier)) {
          hasSubjectDaughter = true;
          break;
        }

        if ((dep.reln() == AUX_MODIFIER || dep.reln() == MARKER) && dep.gov().equals(modifier)) {
          hasAux = true;
        }

        if ((dep.reln() == NOMINAL_SUBJECT || dep.reln() == NOMINAL_PASSIVE_SUBJECT) && dep.gov().equals(head)) {
          subjects.add(dep.dep());
        }

        if (dep.reln() == DIRECT_OBJECT && dep.gov().equals(head)) {
          objects.add(dep.dep());
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
          TypedDependency newDep = new TypedDependency(NOMINAL_SUBJECT, modifier, object);
          newDeps.add(newDep);
        }
      } else {
        for (IndexedWord subject : subjects) {
          TypedDependency newDep = new TypedDependency(NOMINAL_SUBJECT, modifier, subject);
          newDeps.add(newDep);
        }
      }
    }

    for (TypedDependency newDep : newDeps) {
      if (!list.contains(newDep)) {
        newDep.setExtra();
        list.add(newDep);
      }
    }
  }

  /**
   * This method corrects subjects of verbs for which we identified an auxpass,
   * but didn't identify the subject as passive.
   *
   * @param list List of typedDependencies to work on
   */
  private static void correctSubjPass(Collection<TypedDependency> list) {
    // put in a list verbs having an auxpass
    List<IndexedWord> list_auxpass = new ArrayList<IndexedWord>();
    for (TypedDependency td : list) {
      if (td.reln() == AUX_PASSIVE_MODIFIER) {
        list_auxpass.add(td.gov());
      }
    }
    for (TypedDependency td : list) {
      // correct nsubj
      if (td.reln() == NOMINAL_SUBJECT && list_auxpass.contains(td.gov())) {
        // System.err.println("%%% Changing subj to passive: " + td);
        td.setReln(NOMINAL_PASSIVE_SUBJECT);
      }
      if (td.reln() == CLAUSAL_SUBJECT && list_auxpass.contains(td.gov())) {
        // System.err.println("%%% Changing subj to passive: " + td);
        td.setReln(CLAUSAL_PASSIVE_SUBJECT);
      }

      // correct unretrieved poss: dep relation in which the dependent is a
      // PRP$ or WP$
      // cdm: Now done in basic rules.  The only cases that this still matches
      // are (1) tagging mistakes where PRP in dobj position is mistagged PRP$
      // or a couple of parsing errors where the dependency is wrong anyway, so
      // it's probably okay to keep it a dep.  So I'm disabling this.
      // String tag = td.dep().tag();
      // if (td.reln() == DEPENDENT && (tag.equals("PRP$") || tag.equals("WP$"))) {
      //  System.err.println("%%% Unrecognized basic possessive pronoun: " + td);
      //  td.setReln(POSSESSION_MODIFIER);
      // }
    }
  }

  
  
  

  
 

  // used by collapse2WP(), collapseFlatMWP(), collapse2WPbis() KEPT IN
  // ALPHABETICAL ORDER
  private static final String[][] MULTIWORD_PREPS = { { "according", "to" }, { "across", "from" }, { "ahead", "of" }, { "along", "with" }, { "alongside", "of" }, { "apart", "from" }, { "as", "for" }, { "as", "from" }, { "as", "of" }, { "as", "per" }, { "as", "to" }, { "aside", "from" }, { "away", "from" }, { "based", "on" }, { "because", "of" }, { "close", "by" }, { "close", "to" }, { "contrary", "to" }, { "compared", "to" }, { "compared", "with" }, { "due", "to" }, { "depending", "on" }, { "except", "for" }, { "exclusive", "of" }, { "far", "from" }, { "followed", "by" }, { "inside", "of" }, { "instead", "of" }, { "irrespective", "of" }, { "next", "to" }, { "near", "to" }, { "off", "of" }, { "out", "of" }, { "outside", "of" }, { "owing", "to" }, { "preliminary", "to" },
      { "preparatory", "to" }, { "previous", "to" }, { "prior", "to" }, { "pursuant", "to" }, { "regardless", "of" }, { "subsequent", "to" }, { "such", "as" }, { "thanks", "to" }, { "together", "with" } };

  // used by collapse3WP() KEPT IN ALPHABETICAL ORDER
  private static final String[][] THREEWORD_PREPS = { { "by", "means", "of" }, { "in", "accordance", "with" }, { "in", "addition", "to" }, { "in", "case", "of" }, { "in", "front", "of" }, { "in", "lieu", "of" }, { "in", "place", "of" }, { "in", "spite", "of" }, { "on", "account", "of" }, { "on", "behalf", "of" }, { "on", "top", "of" }, { "with", "regard", "to" }, { "with", "respect", "to" } };

  
  
  /**
   * Find and remove any exact duplicates from a dependency list.
   * For example, the method that "corrects" nsubj dependencies can
   * turn them into nsubjpass dependencies.  If there is some other
   * source of nsubjpass dependencies, there may now be multiple
   * copies of the nsubjpass dependency.  If the containing data type
   * is a List, they may both now be in the List.
   */
  private static void removeExactDuplicates(Collection<TypedDependency> list) {
    Set<TypedDependency> set = new TreeSet<TypedDependency>(list);
    list.clear();
    list.addAll(set);
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
