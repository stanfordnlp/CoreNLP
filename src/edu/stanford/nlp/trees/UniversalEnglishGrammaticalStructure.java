package edu.stanford.nlp.trees;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;

import edu.stanford.nlp.graph.DirectedMultiGraph;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.ud.EnhancementOptions;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

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
public class UniversalEnglishGrammaticalStructure extends GrammaticalStructure  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(UniversalEnglishGrammaticalStructure.class);

  private static final long serialVersionUID = 1L;

  private static final boolean DEBUG = System.getProperty("UniversalEnglishGrammaticalStructure", null) != null;

  private static final boolean USE_NAME = System.getProperty("UDUseNameRelation") != null;

  /*
   * Options for "Enhanced" representation:
   *
   * - Process multi-word prepositions: No
   * - Add prepositions to relation labels: Yes
   * - Add prepositions only to nmod relations: No
   * - Add coordinating conjunctions to relation labels: Yes
   * - Propagate dependents: Yes
   * - Add "referent" relations: Yes
   * - Add copy nodes for conjoined Ps and PPs: No
   * - Turn quantificational modifiers into flat MWEs: No
   * - Add relations between controlling subject and controlled verbs: Yes
   *
   */
  public static final EnhancementOptions ENHANCED_OPTIONS = new EnhancementOptions(false, true, false, true, true, true,
      false, false, true);

  /*
   * Options for "Enhanced++" representation:
   *
   * - Process multi-word prepositions: Yes
   * - Add prepositions to relation labels: Yes
   * - Add prepositions only to nmod relations: No
   * - Add coordinating conjunctions to relation labels: Yes
   * - Propagate dependents: Yes
   * - Add "referent" relations: Yes
   * - Add copy nodes for conjoined Ps and PPs: Yes
   * - Turn quantificational modifiers into flat MWEs: Yes
   * - Add relations between controlling subject and controlled verbs: Yes
   *
   */
  public static final EnhancementOptions ENHANCED_PLUS_PLUS_OPTIONS = new EnhancementOptions(true, true, false, true, true, true,
      true, true, true);

  /*
   * Options for "Collapsed" representation.
   * This represenation is similar to the "collapsed" SD representation
   * without any "Extra" relations.
   *
   * - Process multi-word prepositions: Yes
   * - Add prepositions to relation labels: Yes
   * - Add prepositions only to nmod relations: Yes
   * - Add coordinating conjunctions to relation labels: Yes
   * - Propagate dependents: No
   * - Add "referent" relations: No
   * - Add copy nodes for conjoined Ps and PPs: Yes
   * - Turn quantificational modifiers into flat MWEs: No
   * - Add relations between controlling subject and controlled verbs: No
   *
   */
  @Deprecated
  public static final EnhancementOptions COLLAPSED_OPTIONS = new EnhancementOptions(true, true, true, true, false, false,
      true, false, false);


  /**
   * Construct a new {@code EnglishGrammaticalStructure} from an existing parse
   * tree. The new {@code GrammaticalStructure} has the same tree structure
   * and label values as the given tree (but no shared storage). As part of
   * construction, the parse tree is analyzed using definitions from
   * {@link GrammaticalRelation {@code GrammaticalRelation}} to populate
   * the new {@code GrammaticalStructure} with as many labeled grammatical
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
    this(t, tagFilter, new UniversalSemanticHeadFinder(true));
  }

  /**
   * Construct a new {@code GrammaticalStructure} from an existing parse
   * tree. The new {@code GrammaticalStructure} has the same tree structure
   * and label values as the given tree (but no shared storage). As part of
   * construction, the parse tree is analyzed using definitions from
   * {@link GrammaticalRelation {@code GrammaticalRelation}} to populate
   * the new {@code GrammaticalStructure} with as many labeled grammatical
   * relations as it can.
   *
   * This gets used by GrammaticalStructureFactory (by reflection). DON'T DELETE.
   *
   * @param t Parse tree to make grammatical structure from
   * @param tagFilter Filter for punctuation tags
   * @param hf HeadFinder to use when building it
   */
  public UniversalEnglishGrammaticalStructure(Tree t, Predicate<String> tagFilter, HeadFinder hf) {

    // the tree is normalized (for index and functional tag stripping) inside CoordinationTransformer
    super(t, UniversalEnglishGrammaticalRelations.values(), UniversalEnglishGrammaticalRelations.valuesLock(),
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

  protected static void correctDependencies(SemanticGraph sg) {
    if (DEBUG) {
      printListSorted("At correctDependencies:", sg.typedDependencies());
    }
    correctSubjPass(sg);
    if (DEBUG) {
      printListSorted("After correctSubjPass:", sg.typedDependencies());
    }
    processNames(sg);
    if (DEBUG) {
      printListSorted("After processNames:", sg.typedDependencies());
    }
    removeExactDuplicates(sg);
    if (DEBUG) {
      printListSorted("After removeExactDuplicates:", sg.typedDependencies());
    }
  }

  private static void printListSorted(String title, Collection<TypedDependency> list) {
    List<TypedDependency> lis = new ArrayList<>(list);
    Collections.sort(lis);
    if (title != null) {
      log.info(title);
    }
    log.info(lis);
  }

  @Override
  protected void postProcessDependencies(List<TypedDependency> list) {
    SemanticGraph sg = new SemanticGraph(list);
    postProcessDependencies(sg);
    list.clear();
    list.addAll(sg.typedDependencies());
  }

  protected static void postProcessDependencies(SemanticGraph sg) {
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
  private static SemgrexPattern[] PREP_MW3_PATTERNS = {
      SemgrexPattern.compile("{}=gov   [>/^nmod$/=reln ({}=mod >case ({}=c1 >mwe {}=c2 >mwe ({}=c3 !== {}=c2) ))]"),
      SemgrexPattern.compile("{}=gov   [>/^(advcl|acl)$/=reln ({}=mod >/^(mark|case)$/ ({}=c1 >mwe {}=c2 >mwe ({}=c3 !== {}=c2) ))]")

  };
  private static SemgrexPattern[] PREP_MW2_PATTERNS = {
      SemgrexPattern.compile("{}=gov >/^nmod$/=reln ({}=mod >case ({}=c1 >mwe {}=c2))"),
    SemgrexPattern.compile("{}=gov >/^(advcl|acl)$/=reln ({}=mod >/^(mark|case)$/ ({}=c1 >mwe {}=c2))")

  };
  private static SemgrexPattern[] PREP_PATTERNS = {
      SemgrexPattern.compile("{}=gov   >/^nmod$/=reln ({}=mod >case {}=c1)"),
      SemgrexPattern.compile("{}=gov   >/^(advcl|acl)$/=reln ({}=mod >/^(mark|case)$/ {}=c1)")
  };

  /**
   * Adds the case marker(s) to all nmod, acl and advcl relations that are
   * modified by one or more case markers(s).
   *
   * @param enhanceOnlyNmods If this is set to true, then prepositons will only be appended to nmod
   *                         relations (and not to acl or advcl) relations.
   *
   * @see UniversalEnglishGrammaticalStructure#addCaseMarkersToReln
   */
  private static void addCaseMarkerInformation(SemanticGraph sg, boolean enhanceOnlyNmods) {

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
    for (SemgrexPattern p: PREP_MW3_PATTERNS) {
      sgCopy = sg.makeSoftCopy();
      matcher = p.matcher(sgCopy);
      while (matcher.find()) {
        if (enhanceOnlyNmods && ! matcher.getRelnString("reln").equals("nmod")) {
          continue;
        }

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
    }


    /* 2-word prepositions */
    for (SemgrexPattern p: PREP_MW2_PATTERNS) {
      sgCopy = sg.makeSoftCopy();
      matcher = p.matcher(sgCopy);
      while (matcher.find()) {
        if (enhanceOnlyNmods && ! matcher.getRelnString("reln").equals("nmod")) {
          continue;
        }

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
    }

    /* Single-word prepositions */
    for (SemgrexPattern p: PREP_PATTERNS) {
      sgCopy = sg.makeSoftCopy();
      matcher = p.matcher(sgCopy);
      while (matcher.find()) {
        if (enhanceOnlyNmods && ! matcher.getRelnString("reln").equals("nmod")) {
          continue;
        }

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
   * E.g. if there is a relation {@code nmod(gov, dep)} and {@code case(dep, prep)}, then
   * the {@code nmod} relation is renamed to {@code nmod:prep}.
   *
   *
   * @param sg semantic graph
   * @param gov governor of the nmod/acl/advcl relation
   * @param mod modifier of the nmod/acl/advcl relation
   * @param caseMarkers {@code List<IndexedWord>} of all the case markers that depend on mod
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
          sb.append('_');
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

  private static final SemgrexPattern PREP_CONJP_PATTERN = SemgrexPattern.compile("{} >case ({}=gov >cc {}=cc >conj {}=conj)");

  /**
   * Expands prepositions with conjunctions such as in the sentence
   * "Bill flies to and from Serbia." by copying the verb resulting
   * in the following relations:
   * <p/>
   * {@code conj:and(flies, flies')}<br/>
   * {@code case(Serbia, to)}<br/>
   * {@code cc(to, and)}<br/>
   * {@code conj(to, from)}<br/>
   * {@code nmod(flies, Serbia)}<br/>
   * {@code nmod(flies', Serbia)}<br/>
   * <p/>
   * The label of the conjunct relation includes the conjunction type
   * because if the verb has multiple cc relations then it can be impossible
   * to infer which coordination marker belongs to which conjuncts.
   *
   * @param sg A SemanticGraph for a sentence
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
   * {@code conj:and(flies, flies')}<br/>
   * {@code case(France, to)}<br/>
   * {@code cc(flies, and)}<br/>
   * {@code case(Serbia, from)}<br/>
   * {@code nmod(flies, France)}<br/>
   * {@code nmod(flies', Serbia)}<br/>
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


  private static final SemgrexPattern CONJUNCTION_PATTERN = SemgrexPattern.compile("{}=gov >cc {}=cc >conj {}=conj");


  /**
   * Adds the type of conjunction to all conjunct relations.
   * <p/>
   * {@code cc(Marie, and)}, {@code conj(Marie, Chris)} and {@code conj(Marie, John)}
   * become {@code cc(Marie, and)}, {@code conj:and(Marie, Chris)} and {@code conj:and(Marie, John)}.
   * <p/>
   * In case multiple coordination marker depend on the same governor
   * the one that precedes the conjunct is appended to the conjunction relation or the
   * first one if no preceding marker exists.
   * <p/>
   * Some multi-word coordination markers are collapsed to {@code conj:and} or {@code conj:negcc}.
   * See {@link #conjValue(IndexedWord, SemanticGraph)}.
   *
   * @param sg A SemanticGraph from a sentence
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
  private static final SemgrexPattern XCOMP_PATTERN = SemgrexPattern.compile("{}=root >xcomp {}=embedded >/^(dep|dobj)$/ {}=wh ?>/([di]obj)/ {}=obj");

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
          String lemma = Morphology.lemmaStatic(root.value(), root.tag());
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

  @Override
  protected void addEnhancements(List<TypedDependency> list, EnhancementOptions options) {

    SemanticGraph sg = new SemanticGraph(list);

    if (DEBUG) {
      printListSorted("addEnhancements: before correctDependencies()", sg.typedDependencies());
    }

    correctDependencies(sg);

    if (DEBUG) {
      printListSorted("addEnhancements: after correctDependencies()", sg.typedDependencies());
    }

    /* Turn multi-word prepositions into flat mwe. */
    if (options.processMultiWordPrepositions) {
      processMultiwordPreps(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after processMultiwordPreps()", sg.typedDependencies());
      }
    }
    /* Turn quantificational modifiers into flat mwe. */
    if (options.demoteQuantMod) {
      demoteQuantificationalModifiers(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after demoteQuantificationalModifiers()", sg.typedDependencies());
      }
    }
    /* Add copy nodes for conjoined Ps and PPs. */
    if (options.addCopyNodes) {
      expandPPConjunctions(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after expandPPConjunctions()", sg.typedDependencies());
      }
      expandPrepConjunctions(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after expandPrepConjunctions()", sg.typedDependencies());
      }
    }
    /* Add propositions to relation names. */
    if (options.enhancePrepositionalModifiers) {
      addCaseMarkerInformation(sg, options.enhanceOnlyNmods);
      if (DEBUG) {
        printListSorted("addEnhancements: after addCaseMarkerInformation()", sg.typedDependencies());
      }
    }
    /* Add coordinating conjunctions to relation names. */
    if (options.enhanceConjuncts) {
      addConjInformation(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after addConjInformation()", sg.typedDependencies());
      }
    }
    /* Add "referent" relations. */
    if (options.addReferent) {
      addRef(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after addRef()", sg.typedDependencies());
      }
      collapseReferent(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after collapseReferent()", sg.typedDependencies());
      }
    }
    /* Propagate dependents. */
    if (options.propagateDependents) {
      treatCC(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after treatCC()", sg.typedDependencies());
      }
    }
    /* Add relations between controlling subjects and controlled verbs. */
    if (options.addXSubj) {
      addExtraNSubj(sg);
      if (DEBUG) {
        printListSorted("addEnhancements: after addExtraNSubj()", sg.typedDependencies());
      }
    }

    correctSubjPass(sg);
    list.clear();
    list.addAll(sg.typedDependencies());

    Collections.sort(list);
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
   * If there exist the relations {@code case(hat, in)} and {@code nmod(in, hat)} then
   * the {@code nmod} relation is enhanced to {@code nmod:in(cat, hat)}.
   * The {@code case(hat, in)} relation is preserved.</dd>
   * <dt>clausal modifier of noun/adverbial clause modifier with case markers: acs/advcl</dt>
   * <dd>
   * If there exist the relations {@code case(attacking, of)} and {@code advcl(heard, attacking)} then
   * the {@code nmod} relation is enhanced to {@code nmod:of(heard, attacking)}.
   * The {@code case(attacking, of)} relation is preserved.</dd>
   * <dt>conjunct dependencies</dt>
   * <dd>
   * If there exist the relations
   * {@code cc(investors, and)} and
   * {@code conj(investors, regulators)}, then the {@code conj} relation is
   * enhanced to
   * {@code conj:and(investors, regulators)}</dd>
   * <dt>For relative clauses, it will collapse referent</dt>
   * <dd>
   * {@code ref(man, that)} and {@code dobj(love, that)} are collapsed
   * to {@code dobj(love, man)}</dd>
   * </dl>
   */
  @Override
  protected void collapseDependencies(List<TypedDependency> list, boolean CCprocess, Extras includeExtras) {
    EnhancementOptions options = new EnhancementOptions(COLLAPSED_OPTIONS);
    if (includeExtras.doRef) {
      options.addReferent = true;
    }

    if (includeExtras.doSubj) {
      options.addXSubj = true;
    }

    if (CCprocess) {
      options.propagateDependents = true;
    }
    addEnhancements(list, options);
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
        map.put(edge.getDependent(), new TreeSet<>());
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

    // log.info(map);
    // if (DEBUG) log.info("Subject map: " + subjectMap);
    // if (DEBUG) log.info("Object map: " + objectMap);
    // log.info(rcmodHeads);

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
        // log.info("gov " + gov);
        if (gov_relations != null) {
          for (SemanticGraphEdge edge1 : gov_relations) {
            // log.info("gov rel " + td1);
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
                    log.info("Adding new " + newRel + " dependency from " + newGov + " to " + dep + " (subj/obj case)");
                  }
                  sg.addEdge(newGov, dep, newRel, Double.NEGATIVE_INFINITY, true);
                }
              } else {
                if (DEBUG) {
                  log.info("Adding new " + newRel + " dependency from " + newGov + " to " + dep);
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
            log.info("Adding new " + relation + " dependency from " + dep + " to " + tdsubj.getDependent() + " (subj propagation case)");
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
        // log.info("Adding new " + tdobj.reln() + " dependency from "
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
    List<SemanticGraphEdge> refs = new ArrayList<>(sg.findAllRelns(REFERENT));

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
        sg.addEdge(head, newDep, REFERENT, Double.NEGATIVE_INFINITY, false);
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
      if ( ! objects.isEmpty()) {
        for (IndexedWord object : objects) {
          if ( ! sg.containsEdge(modifier, object))
            sg.addEdge(modifier, object, CONTROLLING_NOMINAL_SUBJECT, Double.NEGATIVE_INFINITY, true);
        }
      } else {
        for (IndexedWord subject : subjects) {
          if ( ! sg.containsEdge(modifier, subject))
            sg.addEdge(modifier, subject, CONTROLLING_NOMINAL_SUBJECT, Double.NEGATIVE_INFINITY, true);
        }
      }
    }
  }

  private static SemgrexPattern CORRECT_SUBJPASS_PATTERN = SemgrexPattern.compile("{}=gov >auxpass {} >/^(nsubj|csubj).*$/ {}=subj");

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
      } else if (edge.getRelation() == CONTROLLING_NOMINAL_SUBJECT) {
        reln = CONTROLLING_NOMINAL_PASSIVE_SUBJECT;
      } else if (edge.getRelation() == CONTROLLING_CLAUSAL_SUBJECT) {
        reln = CONTROLLING_CLAUSAL_PASSIVE_SUBJECT;
      }

      if (reln != null) {
        sg.removeEdge(edge);
        sg.addEdge(gov, subj, reln, Double.NEGATIVE_INFINITY, false);
      }
    }
  }

  /* These multi-word prepositions typically have a
   *   case/advmod(gov, w1)
   *   case(gov, w2)
   * structure in the basic represenation.
   *
   * Kept in alphabetical order.
   */
  private static final String[] TWO_WORD_PREPS_REGULAR = {"across_from", "along_with", "alongside_of", "apart_from", "as_for", "as_from", "as_of", "as_per", "as_to", "aside_from", "based_on", "close_by", "close_to", "contrary_to", "compared_to", "compared_with", " depending_on", "except_for", "exclusive_of", "far_from", "followed_by", "inside_of", "irrespective_of", "next_to", "near_to", "off_of", "out_of", "outside_of", "owing_to", "preliminary_to", "preparatory_to", "previous_to", " prior_to", "pursuant_to", "regardless_of", "subsequent_to", "thanks_to", "together_with"};

  /* These multi-word prepositions can have a
   *   advmod(gov1, w1)
   *   nmod(w1, gov2)
   *   case(gov2, w2)
   * structure in the basic represenation.
   *
   * Kept in alphabetical order.
   */
  private static final String[] TWO_WORD_PREPS_COMPLEX = {"apart_from", "as_from", "aside_from", "away_from", "close_by", "close_to", "contrary_to", "far_from", "next_to", "near_to", "out_of", "outside_of", "pursuant_to", "regardless_of", "together_with"};

  /*
   * Multi-word prepositions with the structure
   *   case(w2, w1)
   *   nmod(gov, w2)
   *   case(gov2, w3)
   *   nmod(w2, gov2)
   * in the basic representations.
   */
  private static final String[] THREE_WORD_PREPS = { "by_means_of", "in_accordance_with", "in_addition_to", "in_case_of", "in_front_of", "in_lieu_of", "in_place_of", "in_spite_of", "on_account_of", "on_behalf_of", "on_top_of", "with_regard_to", "with_respect_to" };


  private static final SemgrexPattern TWO_WORD_PREPS_REGULAR_PATTERN = SemgrexPattern.compile("{}=gov >/(case|advmod)/ ({}=w1 !> {}) >case ({}=w2 !== {}=w1 !> {})");
  private static final SemgrexPattern TWO_WORD_PREPS_COMPLEX_PATTERN = SemgrexPattern.compile("({}=w1 >nmod ({}=gov2 >case ({}=w2 !> {}))) [ == {$} | < {}=gov ]");
  private static final SemgrexPattern THREE_WORD_PREPS_PATTERN = SemgrexPattern.compile("({}=w2 >/(nmod|acl|advcl)/ ({}=gov2 >/(case|mark)/ ({}=w3 !> {}))) >case ({}=w1 !> {}) [ < {}=gov | == {$} ]");


  /**
   * Process multi-word prepositions.
   */
  private static void processMultiwordPreps(SemanticGraph sg) {
    /* Semgrexes require a graph with a root. */
    if (sg.getRoots().isEmpty())
      return;

    HashMap<String, HashSet<Integer>> bigrams = new HashMap<>();
    HashMap<String, HashSet<Integer>> trigrams = new HashMap<>();


    List<IndexedWord> vertexList = sg.vertexListSorted();
    int numWords = vertexList.size();

    for (int i = 1; i < numWords; i++) {
      String bigram = vertexList.get(i-1).value().toLowerCase() + '_' + vertexList.get(i).value().toLowerCase();

      bigrams.putIfAbsent(bigram, new HashSet<>());

      bigrams.get(bigram).add(vertexList.get(i-1).index());

      if (i > 1) {
        String trigram = vertexList.get(i-2).value().toLowerCase() + '_' + bigram;
        trigrams.putIfAbsent(trigram, new HashSet<>());
        trigrams.get(trigram).add(vertexList.get(i-2).index());
      }
    }

    /* Simple two-word prepositions. */
    processSimple2WP(sg, bigrams);

    /* More complex two-word prepositions in which the first
     * preposition is the head of the prepositional phrase. */
    processComplex2WP(sg, bigrams);

    /* Process three-word prepositions. */
    process3WP(sg, trigrams);
  }


  /**
   * Processes all the two-word prepositions in TWO_WORD_PREPS_REGULAR.
   */
  private static void processSimple2WP(SemanticGraph sg, HashMap<String, HashSet<Integer>> bigrams) {
    for (String bigram : TWO_WORD_PREPS_REGULAR) {
      if (bigrams.get(bigram) == null) {
        continue;
      }

      for (Integer i : bigrams.get(bigram)) {
        IndexedWord w1 = sg.getNodeByIndexSafe(i);
        IndexedWord w2 = sg.getNodeByIndexSafe(i + 1);

        if (w1 == null || w2 == null) {
          continue;
        }

        SemgrexMatcher matcher = TWO_WORD_PREPS_REGULAR_PATTERN.matcher(sg);
        IndexedWord gov = null;
        while (matcher.find()) {
          if (w1.equals(matcher.getNode("w1")) && w2.equals(matcher.getNode("w2"))) {
            gov = matcher.getNode("gov");
            break;
          }
        }

        if (gov == null) {
          continue;
        }

        createMultiWordExpression(sg, gov, CASE_MARKER, w1, w2);
      }
    }
  }


  /**
   * Processes all the two-word prepositions in TWO_WORD_PREPS_COMPLEX.
   */
  private static void processComplex2WP(SemanticGraph sg, HashMap<String, HashSet<Integer>> bigrams) {
    for (String bigram : TWO_WORD_PREPS_COMPLEX) {
      if (bigrams.get(bigram) == null) {
        continue;
      }

      for (Integer i : bigrams.get(bigram)) {
        IndexedWord w1 = sg.getNodeByIndexSafe(i);
        IndexedWord w2 = sg.getNodeByIndexSafe(i + 1);

        if (w1 == null || w2 == null) {
          continue;
        }

        SemgrexMatcher matcher = TWO_WORD_PREPS_COMPLEX_PATTERN.matcher(sg);
        IndexedWord gov = null;
        IndexedWord gov2 = null;
        while (matcher.find()) {
          if (w1.equals(matcher.getNode("w1")) && w2.equals(matcher.getNode("w2"))) {
            gov = matcher.getNode("gov");
            gov2 = matcher.getNode("gov2");
            break;
          }
        }

        if (gov2 == null) {
          continue;
        }

        /* Attach the head of the prepositional phrase to
         * the head of w1. */
        if (sg.getRoots().contains(w1)) {
          SemanticGraphEdge edge = sg.getEdge(w1, gov2);
          if (edge == null) {
            continue;
          }

          sg.removeEdge(edge);
          sg.getRoots().remove(w1);
          sg.addRoot(gov2);
        } else {
          SemanticGraphEdge edge = sg.getEdge(w1, gov2);
          if (edge == null) {
            continue;
          }
          sg.removeEdge(edge);

          gov = gov == null ? sg.getParent(w1) : gov;
          if (gov == null) {
            continue;
          }

          /* Determine the relation to use. If it is a relation that can
           * join two clauses and w1 is the head of a copular construction, then
           * use the relation of w1 and its parent. Otherwise use the relation of edge. */
          GrammaticalRelation reln = edge.getRelation();
          if (sg.hasChildWithReln(w1, COPULA)) {
            GrammaticalRelation reln2 = sg.getEdge(gov, w1).getRelation();
            if (clauseRelations.contains(reln2)) {
              reln = reln2;
            }
          }
         sg.addEdge(gov, gov2, reln, Double.NEGATIVE_INFINITY, false);
        }

        /* Make children of w1 dependents of gov2. */
        for (SemanticGraphEdge edge2 : sg.getOutEdgesSorted(w1)) {
          sg.removeEdge(edge2);
          sg.addEdge(gov2, edge2.getDependent(), edge2.getRelation(), edge2.getWeight(), edge2.isExtra());
        }

        createMultiWordExpression(sg, gov2, CASE_MARKER, w1, w2);
      }
    }
  }


  /**
   * Processes all the three-word prepositions in THREE_WORD_PREPS.
   */
  private static void process3WP(SemanticGraph sg, HashMap<String, HashSet<Integer>> trigrams) {

    for (String trigram : THREE_WORD_PREPS) {
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

        SemgrexMatcher matcher = THREE_WORD_PREPS_PATTERN.matcher(sg);
        IndexedWord gov = null;
        IndexedWord gov2 = null;
        while (matcher.find()) {
          if (w1.equals(matcher.getNode("w1")) && w2.equals(matcher.getNode("w2")) && w3.equals(matcher.getNode("w3"))) {
            gov = matcher.getNode("gov");
            gov2 = matcher.getNode("gov2");
            break;
          }
        }

        if (gov2 == null) {
          continue;
        }

        GrammaticalRelation markerReln = CASE_MARKER;

        if (sg.getRoots().contains(w2)) {
          SemanticGraphEdge edge = sg.getEdge(w2, gov2);
          if (edge == null) {
            continue;
          }

          sg.removeEdge(edge);
          sg.getRoots().remove(w2);
          sg.addRoot(gov2);
        } else {
          SemanticGraphEdge edge = sg.getEdge(w2, gov2);
          if (edge == null) {
            continue;
          }
          sg.removeEdge(edge);

          gov = gov == null ? sg.getParent(w2) : gov;
          if (gov == null) {
            continue;
          }

          GrammaticalRelation reln = sg.getEdge(gov, w2).getRelation();
          if (reln == NOMINAL_MODIFIER
              && (edge.getRelation() == CLAUSAL_MODIFIER ||
                  edge.getRelation() == ADV_CLAUSE_MODIFIER)) {
            reln = edge.getRelation();
            markerReln = MARKER;
          }
          sg.addEdge(gov, gov2, reln, Double.NEGATIVE_INFINITY, false);
        }

        /* Make children of w2 dependents of gov2. */
        for (SemanticGraphEdge edge2 : sg.getOutEdgesSorted(w2)) {
          sg.removeEdge(edge2);
          sg.addEdge(gov2, edge2.getDependent(), edge2.getRelation(), edge2.getWeight(), edge2.isExtra());
        }

        createMultiWordExpression(sg, gov2, markerReln, w1, w2, w3);
      }
    }
  }

  private static void createMultiWordExpression(SemanticGraph sg, IndexedWord gov, GrammaticalRelation reln, IndexedWord... words) {
    if (sg.getRoots().isEmpty() || gov == null || words.length < 1) {
      return;
    }

    boolean first = true;
    IndexedWord mweHead = null;
    for (IndexedWord word : words) {
      IndexedWord wordGov = sg.getParent(word);
      if (wordGov != null) {
        SemanticGraphEdge edge = sg.getEdge(wordGov, word);
        if (edge != null) {
          sg.removeEdge(edge);
        }
      }

      if (first) {
        sg.addEdge(gov, word, reln, Double.NEGATIVE_INFINITY, false);
        mweHead = word;
        first = false;
      } else {
        sg.addEdge(mweHead, word, MULTI_WORD_EXPRESSION, Double.NEGATIVE_INFINITY, false);
      }
    }
  }


  /** A lot of, an assortment of, ... */
  private static final SemgrexPattern QUANT_MOD_3W_PATTERN = SemgrexPattern.compile("{word:/(?i:lot|assortment|number|couple|bunch|handful|litany|sheaf|slew|dozen|series|variety|multitude|wad|clutch|wave|mountain|array|spate|string|ton|range|plethora|heap|sort|form|kind|type|version|bit|pair|triple|total)/}=w2 >det {word:/(?i:an?)/}=w1 !>amod {} >nmod ({tag:/(NN.*|PRP.*)/}=gov >case {word:/(?i:of)/}=w3) . {}=w3");

  private static final SemgrexPattern[] QUANT_MOD_2W_PATTERNS = {
      /* Lots of, dozens of, heaps of ... */
      SemgrexPattern.compile("{word:/(?i:lots|many|several|plenty|tons|dozens|multitudes|mountains|loads|pairs|tens|hundreds|thousands|millions|billions|trillions|[0-9]+s)/}=w1 >nmod ({tag:/(NN.*|PRP.*)/}=gov >case {word:/(?i:of)/}=w2) . {}=w2"),

      /* Some of the ..., all of them, ... */
      SemgrexPattern.compile("{word:/(?i:some|all|both|neither|everyone|nobody|one|two|three|four|five|six|seven|eight|nine|ten|hundred|thousand|million|billion|trillion|[0-9]+)/}=w1 [>nmod ({tag:/(NN.*)/}=gov >case ({word:/(?i:of)/}=w2 $+ {}=det) >det {}=det) |  >nmod ({tag:/(PRP.*)/}=gov >case {word:/(?i:of)/}=w2)] . {}=w2")
  };


  private static void demoteQuantificationalModifiers(SemanticGraph sg) {
    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = QUANT_MOD_3W_PATTERN.matcher(sgCopy);

    while (matcher.findNextMatchingNode()) {
      IndexedWord w1 = matcher.getNode("w1");
      IndexedWord w2 = matcher.getNode("w2");
      IndexedWord w3 = matcher.getNode("w3");
      IndexedWord gov = matcher.getNode("gov");

      demoteQmodParentHelper(sg, gov, w2);

      List<IndexedWord> otherDeps = Generics.newLinkedList();

      otherDeps.add(w1);
      otherDeps.add(w2);
      otherDeps.add(w3);

      demoteQmodMWEHelper(sg, otherDeps, gov, w2);
    }

    for (SemgrexPattern p : QUANT_MOD_2W_PATTERNS) {
      sgCopy = sg.makeSoftCopy();
      matcher = p.matcher(sgCopy);
      while (matcher.findNextMatchingNode()) {
        IndexedWord w1 = matcher.getNode("w1");
        IndexedWord w2 = matcher.getNode("w2");
        IndexedWord gov = matcher.getNode("gov");

        demoteQmodParentHelper(sg, gov, w1);

        List<IndexedWord> otherDeps = Generics.newLinkedList();
        otherDeps.add(w1);
        otherDeps.add(w2);

        demoteQmodMWEHelper(sg, otherDeps, gov, w1);
      }
    }


  }

  private static void demoteQmodMWEHelper(SemanticGraph sg, List<IndexedWord> otherDeps, IndexedWord gov, IndexedWord oldHead) {
    createMultiWordExpression(sg, gov, QMOD, otherDeps.toArray(new IndexedWord[otherDeps.size()]));
  }


  private static void demoteQmodParentHelper(SemanticGraph sg, IndexedWord gov, IndexedWord oldHead) {
    if (!sg.getRoots().contains(oldHead)) {
      IndexedWord parent = sg.getParent(oldHead);
      if (parent == null) {
        return;
      }
      SemanticGraphEdge edge = sg.getEdge(parent, oldHead);
      sg.addEdge(parent, gov, edge.getRelation(), edge.getWeight(), edge.isExtra());
      sg.removeEdge(edge);
    } else {
      sg.getRoots().remove(oldHead);
      sg.addRoot(gov);
    }

    //temporary relation to keep the graph connected
    sg.addEdge(gov, oldHead, DEPENDENT, Double.NEGATIVE_INFINITY, false);
    sg.removeEdge(sg.getEdge(oldHead, gov));
  }


  private static final SemgrexPattern[] NAME_PATTERNS = {
    SemgrexPattern.compile("{ner:PERSON}=w1 >compound {}=w2"),
    SemgrexPattern.compile("{ner:LOCATION}=w1 >compound {}=w2")
  };
  private static final Predicate<String> PUNCT_TAG_FILTER = new PennTreebankLanguagePack().punctuationWordRejectFilter();


  /**
   *
   * Looks for NPs that should have the {@code name} relation and
   * a) changes the structure such that the leftmost token becomes the head
   * b) changes the relation from {@code compound} to {@code name}.
   *
   * Requires NER tags.
   *
   * @param sg A semantic graph.
   */
  private static void processNames(SemanticGraph sg) {

    if ( ! USE_NAME) {
      return;
    }

    // check whether NER tags are available
    IndexedWord rootToken = sg.getFirstRoot();
    if (rootToken == null || !rootToken.containsKey(CoreAnnotations.NamedEntityTagAnnotation.class)) {
      return;
    }

    SemanticGraph sgCopy = sg.makeSoftCopy();
    for (SemgrexPattern pattern : NAME_PATTERNS) {
      SemgrexMatcher matcher = pattern.matcher(sgCopy);
      List<IndexedWord> nameParts = new ArrayList<>();
      IndexedWord head = null;
      while (matcher.find()) {
        IndexedWord w1 = matcher.getNode("w1");
        IndexedWord w2 = matcher.getNode("w2");
        if (head != w1) {
          if (head != null) {
            processNamesHelper(sg, head, nameParts);
            nameParts = new ArrayList<>();
          }
          head = w1;
        }
        if (w2.ner().equals(w1.ner())) {
          nameParts.add(w2);
        }
      }
      if (head != null) {
        processNamesHelper(sg, head, nameParts);
        sgCopy = sg.makeSoftCopy();
      }
    }
  }


  private static void processNamesHelper(SemanticGraph sg, IndexedWord oldHead, List<IndexedWord> nameParts) {

    if (nameParts.size() < 1) {
      // if the named entity only spans one token, change compound relations
      // to nmod relations to get the right structure for NPs with additional modifiers
      // such as "Mrs. Clinton".
      Set<IndexedWord> children = new HashSet<>(sg.getChildren(oldHead));
      for (IndexedWord child : children) {
        SemanticGraphEdge oldEdge = sg.getEdge(oldHead, child);
        if (oldEdge.getRelation() == UniversalEnglishGrammaticalRelations.COMPOUND_MODIFIER) {
          sg.addEdge(oldHead, child, UniversalEnglishGrammaticalRelations.NOMINAL_MODIFIER,
              oldEdge.getWeight(), oldEdge.isExtra());
          sg.removeEdge(oldEdge);
        }
      }
      return;
    }

    // sort nameParts
    Collections.sort(nameParts);

    // check whether {nameParts[0], ..., nameParts[n], oldHead} are a contiguous NP
    for (int i = nameParts.get(0).index(), end = oldHead.index(); i < end; i++) {
      IndexedWord node = sg.getNodeByIndexSafe(i);
      if (node == null) {
        return;
      }
      if ( ! nameParts.contains(node) && PUNCT_TAG_FILTER.test(node.tag())) {
        // not in nameParts and not a punctuation mark => not a contiguous NP
        return;
      }
    }


    IndexedWord gov = sg.getParent(oldHead);
    if (gov == null && ! sg.getRoots().contains(oldHead)) {
      return;
    }
    IndexedWord newHead = nameParts.get(0);
    Set<IndexedWord> children = new HashSet<>(sg.getChildren(oldHead));

    //change structure and relations
    for (IndexedWord child : children) {
      if (child == newHead) {
        // make the leftmost word the new head
        if (gov == null) {
          sg.getRoots().add(newHead);
          sg.getRoots().remove(oldHead);
        } else {
          SemanticGraphEdge oldEdge = sg.getEdge(gov, oldHead);
          sg.addEdge(gov, newHead, oldEdge.getRelation(), oldEdge.getWeight(), oldEdge.isExtra());
          sg.removeEdge(oldEdge);
        }
        // swap direction of relation between old head and new head and change it to name relation.
        SemanticGraphEdge oldEdge = sg.getEdge(oldHead, newHead);
        sg.addEdge(newHead, oldHead, UniversalEnglishGrammaticalRelations.NAME_MODIFIER, oldEdge.getWeight(), oldEdge.isExtra());
        sg.removeEdge(oldEdge);
      } else  if (nameParts.contains(child)) {
        // remove relation between the old head and part of the name
        // and introduce new relation between new head and part of the name
        SemanticGraphEdge oldEdge = sg.getEdge(oldHead, child);
        sg.addEdge(newHead, child, UniversalEnglishGrammaticalRelations.NAME_MODIFIER, oldEdge.getWeight(), oldEdge.isExtra());
        sg.removeEdge(oldEdge);
      } else {
        // attach word to new head
        SemanticGraphEdge oldEdge = sg.getEdge(oldHead, child);
        //if not the entire compound is part of a named entity, attach the other tokens via an nmod relation
        GrammaticalRelation reln = oldEdge.getRelation() == UniversalEnglishGrammaticalRelations.COMPOUND_MODIFIER ?
            UniversalEnglishGrammaticalRelations.NOMINAL_MODIFIER : oldEdge.getRelation();
        sg.addEdge(newHead, child, reln, oldEdge.getWeight(), oldEdge.isExtra());
        sg.removeEdge(oldEdge);
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
