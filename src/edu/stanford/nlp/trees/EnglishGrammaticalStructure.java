package edu.stanford.nlp.trees;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import static edu.stanford.nlp.trees.EnglishGrammaticalRelations.*;
import static edu.stanford.nlp.trees.GrammaticalRelation.*;

/**
 * A GrammaticalStructure for English. This is the class that produces Stanford Dependencies.
 *
 * For feeding Stanford parser trees into this class, the Stanford parser should be run with the
 * "-retainNPTmpSubcategories" option for best results!
 *
 * @author Bill MacCartney
 * @author Marie-Catherine de Marneffe
 * @author Christopher Manning
 * @author Daniel Cer (CoNLLX format and alternative user selected dependency
 *         printer/reader interface)
 * @author John Bauer
 */
public class EnglishGrammaticalStructure extends GrammaticalStructure  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(EnglishGrammaticalStructure.class);

  private static final long serialVersionUID = -1866362375001969402L;

  private static final boolean DEBUG = System.getProperty("EnglishGrammaticalStructure", null) != null;

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
  public EnglishGrammaticalStructure(Tree t) {
    this(t, new PennTreebankLanguagePack().punctuationWordRejectFilter());
  }

  /**
   * This gets used by GrammaticalStructureFactory (by reflection). DON'T DELETE.
   *
   * @param t Parse tree to make grammatical structure from
   * @param puncFilter Filter to remove punctuation dependencies
   */
  public EnglishGrammaticalStructure(Tree t, Predicate<String> puncFilter) {
    this(t, puncFilter, new SemanticHeadFinder(true));
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
   * Once upon a time this method had an extra parameter as to whether to operate
   * in a threadsafe manner. We decided that that was a really bad idea, and this
   * method now always acts in a threadsafe manner.
   * This method gets used by GrammaticalStructureFactory (by reflection). DON'T DELETE.
   *
   * @param t Parse tree to make grammatical structure from
   * @param puncFilter Filter for punctuation words
   * @param hf HeadFinder to use when building it
   */
  public EnglishGrammaticalStructure(Tree t, Predicate<String> puncFilter, HeadFinder hf) {
    // the tree is normalized (for index and functional tag stripping) inside CoordinationTransformer
    super(t, EnglishGrammaticalRelations.values(), EnglishGrammaticalRelations.valuesLock(), new CoordinationTransformer(hf), hf, puncFilter, Filters.acceptFilter());
  }

  /** Used for postprocessing CoNLL X dependencies */
  public EnglishGrammaticalStructure(List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
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
      return d != null && d.reln() != RELATIVE;
    }

    private static final long serialVersionUID = 1L;
  }

  private static final Predicate<TypedDependency> extraTreeDepFilter = new ExtraTreeDepFilter();


  @Override
  protected void correctDependencies(List<TypedDependency> list) {
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
    List<TypedDependency> lis = new ArrayList<>(list);
    Collections.sort(lis);
    if (title != null) {
      log.info(title);
    }
    log.info(lis);
  }

  @Override
  protected void postProcessDependencies(List<TypedDependency> list) {
    if (DEBUG) {
      printListSorted("At postProcessDependencies:", list);
    }

    SemanticGraph sg = new SemanticGraph(list);
    correctWHAttachment(sg);
    list.clear();
    list.addAll(sg.typedDependencies());
    if (DEBUG) {
      printListSorted("After correcting WH movement", list);
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

    addStrandedPobj(list);
    if (DEBUG) {
      printListSorted("After adding stranded pobj:", list);
    }
  }

  // Using this makes addStrandedPobj a lot cleaner looking, but it
  // makes the converter roughly 2% slower.  Might not be worth it.
  // Similar changes could be made to many of the other complicated
  // collapsing methods.
  // static final SemgrexPattern strandedPobjSemgrex = SemgrexPattern.compile("{}=head >rcmod ({} [ == {}=prepgov | >xcomp {}=prepgov | >conj {}=prepgov ]) : {}=prepgov >prep ({}=prepdep !>pcomp {} !> pobj {})");
  // // Deal with preposition stranding in relative clauses.
  // // For example, "the only thing I'm rooting for"
  // // This method will add pobj(for, thing) by connecting using the rcmod and prep
  // private static void addStrandedPobj(List<TypedDependency> list) {
  //   SemanticGraph graph = new SemanticGraph(list);
  //   SemgrexMatcher matcher = strandedPobjSemgrex.matcher(graph);
  //   while (matcher.find()) {
  //     IndexedWord gov = matcher.getNode("prepdep");
  //     IndexedWord dep = matcher.getNode("head");

  //     TypedDependency newDep = new TypedDependency(PREPOSITIONAL_OBJECT, gov, dep);
  //     newDep.setExtra();
  //     list.add(newDep);
  //   }
  // }


  // Deal with preposition stranding in relative clauses.
  // For example, "the only thing I'm rooting for"
  // This method will add pobj(for, thing) by connecting using the rcmod and prep
  private static void addStrandedPobj(List<TypedDependency> list) {
    List<IndexedWord> depNodes = null;
    List<TypedDependency> newDeps = null;
    for (TypedDependency rcmod : list) {
      if (rcmod.reln() != RELATIVE_CLAUSE_MODIFIER) {
        continue;
      }

      IndexedWord head = rcmod.gov();
      if (depNodes == null) {
        depNodes = Generics.newArrayList();
      } else {
        depNodes.clear();
      }
      depNodes.add(rcmod.dep());
      for (TypedDependency connected : list) {
        if (connected.gov().equals(rcmod.dep()) && (connected.reln() == XCLAUSAL_COMPLEMENT || connected.reln() == CONJUNCT)) {
          depNodes.add(connected.dep());
        }
      }

      for (IndexedWord dep : depNodes) {
        for (TypedDependency prep : list) {
          if (!prep.gov().equals(dep) || prep.reln() != PREPOSITIONAL_MODIFIER) {
            continue;
          }

          boolean found = false;
          for (TypedDependency other : list) {
            if (other.gov().equals(prep.dep()) && (other.reln() == PREPOSITIONAL_COMPLEMENT || other.reln() == PREPOSITIONAL_OBJECT)) {
              found = true;
              break;
            }
          }
          if (!found) {
            if (newDeps == null) {
              newDeps = Generics.newArrayList();
            }
            TypedDependency newDep = new TypedDependency(PREPOSITIONAL_OBJECT, prep.dep(), head);
            newDeps.add(newDep);
          }
        }
      }

    }
    if (newDeps != null) {
      list.addAll(newDeps);
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
           we have to reattach th WH-word to the verb in the embedded clause. */
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
   * the type "rel".  These occur in sentences such as "I saw the man
   * who you love".  In that case, we should produce dobj(love, who).
   * On the other hand, in the sentence "... which Mr. Bush was
   * fighting for", we should have pobj(for, which).
   */
  private static void convertRel(List<TypedDependency> list) {
    List<TypedDependency> newDeps = new ArrayList<>();
    for (TypedDependency rel : list) {
      if (rel.reln() != RELATIVE) {
        continue;
      }

      boolean foundPrep = false;
      for (TypedDependency prep : list) {

        // todo: It would also be good to add a rule here to prefer ccomp nsubj over dobj if there is a ccomp with no subj
        // then we could get right: Which eco-friendly options do you think there will be on the new Lexus?

        if (prep.reln() != PREPOSITIONAL_MODIFIER) {
          continue;
        }
        if (!prep.gov().equals(rel.gov())) {

          //Try to find a clausal complement with a preposition without an
          //object. For sentences such as "What am I good at?"
          boolean hasCompParent = false;
          for (TypedDependency prep2 : list) {
            if (prep2.reln() == XCLAUSAL_COMPLEMENT
                || prep2.reln() == ADJECTIVAL_COMPLEMENT
                || prep2.reln() == CLAUSAL_COMPLEMENT
                || prep2.reln() == ROOT) {
              if (prep.gov().equals(prep2.dep()) && prep2.gov().equals(rel.gov())) {
                hasCompParent = true;
                break;
              }
            }
          }

          if ( ! hasCompParent)
            continue;
        }

        // at this point, we have two dependencies as in the Mr. Bush
        // example.  it should be rel(fighting, which) and
        // prep(fighting, for).  We now look to see if there is a
        // corresponding pobj associated with the dependent of the
        // prep relation.  If not, we will connect the dep of the prep
        // relation and the head of the rel relation.  Otherwise, the
        // original rel relation will become a dobj.
        boolean foundPobj = false;
        for (TypedDependency pobj : list) {
          if (pobj.reln() != PREPOSITIONAL_OBJECT && pobj.reln() != PREPOSITIONAL_COMPLEMENT) {
            continue;
          }
          if (!pobj.gov().equals(prep.dep())) {
            continue;
          }
          // we did find a pobj/pcomp, so it is not necessary to
          // change this rel.
          foundPobj = true;
          break;
        }

        if (!foundPobj) {
          foundPrep = true;
          TypedDependency newDep = new TypedDependency(PREPOSITIONAL_OBJECT, prep.dep(), rel.dep());
          newDeps.add(newDep);
          rel.setReln(KILL);
          // break; // only put it in one place (or do we want to allow across-the-board effects?
        }
      }
      if (!foundPrep) {
        rel.setReln(DIRECT_OBJECT);
      }
    }

    filterKill(list);
    for (TypedDependency dep : newDeps) {
      if (!list.contains(dep)) {
        list.add(dep);
      }
    }
  }

  /**
   * Alters a list in place by removing all the KILL relations
   */
  private static void filterKill(Collection<TypedDependency> deps) {
    List<TypedDependency> filtered = Generics.newArrayList();
    for (TypedDependency dep : deps) {
      if (dep.reln() != KILL) {
        filtered.add(dep);
      }
    }
    deps.clear();
    deps.addAll(filtered);
  }


  /**
   * Destructively modifies this {@code Collection<TypedDependency>}
   * by collapsing several types of transitive pairs of dependencies.
   * If called with a tree of dependencies and both CCprocess and
   * includeExtras set to false, then the tree structure is preserved.
   * <dl>
   * <dt>prepositional object dependencies: pobj</dt>
   * <dd>
   * {@code prep(cat, in)} and {@code pobj(in, hat)} are collapsed to
   * {@code prep_in(cat, hat)}</dd>
   * <dt>prepositional complement dependencies: pcomp</dt>
   * <dd>
   * {@code prep(heard, of)} and {@code pcomp(of, attacking)} are
   * collapsed to {@code prepc_of(heard, attacking)}</dd>
   * <dt>conjunct dependencies</dt>
   * <dd>
   * {@code cc(investors, and)} and
   * {@code conj(investors, regulators)} are collapsed to
   * {@code conj_and(investors,regulators)}</dd>
   * <dt>possessive dependencies: possessive</dt>
   * <dd>
   * {@code possessive(Montezuma, 's)} will be erased. This is like a collapsing, but
   * due to the flatness of NPs, two dependencies are not actually composed.</dd>
   * <dt>For relative clauses, it will collapse referent</dt>
   * <dd>
   * {@code ref(man, that)} and {@code dobj(love, that)} are collapsed
   * to {@code dobj(love, man)}</dd>
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

    eraseMultiConj(list);
    if (DEBUG) {
      printListSorted("After collapse multi conj:", list);
    }

    collapse2WP(list);
    if (DEBUG) {
      printListSorted("After collapse2WP:", list);
    }

    collapseFlatMWP(list);
    if (DEBUG) {
      printListSorted("After collapseFlatMWP:", list);
    }

    collapse2WPbis(list);
    if (DEBUG) {
      printListSorted("After collapse2WPbis:", list);
    }

    collapse3WP(list);
    if (DEBUG) {
      printListSorted("After collapse3WP:", list);
    }

    collapsePrepAndPoss(list);
    if (DEBUG) {
      printListSorted("After PrepAndPoss:", list);
    }

    collapseConj(list);
    if (DEBUG) {
      printListSorted("After conj:", list);
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

    removeDep(list);
    if (DEBUG) {
      printListSorted("After remove dep:", list);
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
   * as well as, not to mention, but also, &amp; GO TO and.
   *
   * @param conj The head dependency of the conjunction marker
   * @return A GrammaticalRelation made from a normalized form of that
   *         conjunction.
   */
  private static GrammaticalRelation conjValue(String conj) {
    String newConj = conj.toLowerCase();
    if (newConj.equals("not") || newConj.equals("instead") || newConj.equals("rather")) {
      newConj = "negcc";
    } else if (newConj.equals("mention") || newConj.equals("to") || newConj.equals("also") || newConj.contains("well") || newConj.equals("&")) {
      newConj = "and";
    }
    return EnglishGrammaticalRelations.getConj(newConj);
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
        map.put(typedDep.dep(), new TreeSet<>());
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
      if (typedDep.reln().toString().startsWith("prepc")) {
        prepcDep.add(typedDep.dep());
      }
    }

    // log.info(map);
    // if (DEBUG) log.info("Subject map: " + subjectMap);
    // if (DEBUG) log.info("Object map: " + objectMap);
    // log.info(rcmodHeads);

    // create a new list of typed dependencies
    Collection<TypedDependency> newTypedDeps = new ArrayList<>(list);

    // find typed deps of form conj(gov,dep)
    for (TypedDependency td : list) {
      if (EnglishGrammaticalRelations.getConjs().contains(td.reln())) {
        IndexedWord gov = td.gov();
        IndexedWord dep = td.dep();

        // look at the dep in the conjunct
        Set<TypedDependency> gov_relations = map.get(gov);
        // log.info("gov " + gov);
        if (gov_relations != null) {
          for (TypedDependency td1 : gov_relations) {
            // log.info("gov rel " + td1);
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
                    log.info("Adding new " + newRel + " dependency from " + newGov + " to " + dep + " (subj/obj case)");
                  }
                  newTypedDeps.add(new TypedDependency(newRel, newGov, dep));
                }
              } else {
                if (DEBUG) {
                  log.info("Adding new " + newRel + " dependency from " + newGov + " to " + dep);
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
        // copula verbs! but
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
            log.info("Adding new " + relation + " dependency from " + dep + " to " + tdsubj.dep() + " (subj propagation case)");
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
        // log.info("Adding new " + tdobj.reln() + " dependency from "
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
   * This rewrites the "conj" relation to "conj_word" and deletes cases of the
   * "cc" relation providing this rewrite has occurred (but not if there is only
   * something like a clause-initial and). For instance, cc(elected-5, and-9)
   * conj(elected-5, re-elected-11) becomes conj_and(elected-5, re-elected-11)
   *
   * @param list List of dependencies.
   */
  private static void collapseConj(Collection<TypedDependency> list) {
    List<IndexedWord> govs = Generics.newArrayList();
    // find typed deps of form cc(gov, dep)
    for (TypedDependency td : list) {
      if (td.reln() == COORDINATION) { // i.e. "cc"
        IndexedWord gov = td.gov();
        GrammaticalRelation conj = conjValue(td.dep().value());
        if (DEBUG) {
          log.info("Set conj to " + conj + " based on " + td);
        }

        // find other deps of that gov having reln "conj"
        boolean foundOne = false;
        for (TypedDependency td1 : list) {
          if (td1.gov().equals(gov)) {
            if (td1.reln() == CONJUNCT) { // i.e., "conj"
              // change "conj" to the actual (lexical) conjunction
              if (DEBUG) {
                log.info("Changing " + td1 + " to have relation " + conj);
              }
              td1.setReln(conj);
              foundOne = true;
            } else if (td1.reln() == COORDINATION) {
              conj = conjValue(td1.dep().value());
              if (DEBUG) {
                log.info("Set conj to " + conj + " based on " + td1);
              }
            }
          }
        }

        // register to remove cc from this governor
        if (foundOne) {
          govs.add(gov);
        }
      }
    }

    // now remove typed dependencies with reln "cc" if we have successfully
    // collapsed
    list.removeIf(td2 -> td2.reln() == COORDINATION && govs.contains(td2.gov()));
  }

  /**
   * This method will collapse a referent relation such as follows. e.g.:
   * "The man that I love &hellip; " ref(man, that) dobj(love, that) -&gt;
   * dobj(love, man)
   */
  private static void collapseReferent(Collection<TypedDependency> list) {
    // find typed deps of form ref(gov, dep)
    // put them in a List for processing; remove them from the set of deps
    List<TypedDependency> refs = new ArrayList<>();
    for (Iterator<TypedDependency> iter = list.iterator(); iter.hasNext();) {
      TypedDependency td = iter.next();
      if (td.reln() == REFERENT) {
        refs.add(td);
        iter.remove();
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
            log.info("referent: changing " + td);
          }
          td.setDep(ant);
          td.setExtra();
          if (DEBUG) {
            log.info(" to " + td);
          }
        }
      }
    }
  }

  /**
   * Look for ref rules for a given word.  We look through the
   * children and grandchildren of the rcmod dependency, and if any
   * children or grandchildren depend on a that/what/which/etc word,
   * we take the leftmost that/what/which/etc word as the dependent
   * for the ref TypedDependency.
   */
  private static void addRef(Collection<TypedDependency> list) {
    List<TypedDependency> newDeps = new ArrayList<>();

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
    List<TypedDependency> newDeps = new ArrayList<>();

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

        if (dep.reln() == AUX_MODIFIER && dep.gov().equals(modifier)) {
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
    List<IndexedWord> list_auxpass = new ArrayList<>();
    for (TypedDependency td : list) {
      if (td.reln() == AUX_PASSIVE_MODIFIER) {
        list_auxpass.add(td.gov());
      }
    }
    for (TypedDependency td : list) {
      // correct nsubj
      if (td.reln() == NOMINAL_SUBJECT && list_auxpass.contains(td.gov())) {
        // log.info("%%% Changing subj to passive: " + td);
        td.setReln(NOMINAL_PASSIVE_SUBJECT);
      }
      if (td.reln() == CLAUSAL_SUBJECT && list_auxpass.contains(td.gov())) {
        // log.info("%%% Changing subj to passive: " + td);
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
      //  log.info("%%% Unrecognized basic possessive pronoun: " + td);
      //  td.setReln(POSSESSION_MODIFIER);
      // }
    }
  }

  private static boolean inConjDeps(TypedDependency td, List<Triple<TypedDependency, TypedDependency, Boolean>> conjs) {
    for (Triple<TypedDependency, TypedDependency, Boolean> trip : conjs) {
      if (td.equals(trip.first())) {
        return true;
      }
    }
    return false;
  }

  private static void collapsePrepAndPoss(Collection<TypedDependency> list) {

    // Man oh man, how gnarly is the logic of this method....

    Collection<TypedDependency> newTypedDeps = new ArrayList<>();

    // Construct a map from tree nodes to the set of typed
    // dependencies in which the node appears as governor.
    // cdm: could use CollectionValuedMap here!
    Map<IndexedWord, SortedSet<TypedDependency>> map = Generics.newHashMap();
    List<IndexedWord> vmod = Generics.newArrayList();

    for (TypedDependency typedDep : list) {
      if (!map.containsKey(typedDep.gov())) {
        map.put(typedDep.gov(), new TreeSet<>());
      }
      map.get(typedDep.gov()).add(typedDep);

      if (typedDep.reln() == VERBAL_MODIFIER) {
        // look for aux deps which indicate this was a to-be verb
        boolean foundAux = false;
        for (TypedDependency auxDep : list) {
          if (auxDep.reln() != AUX_MODIFIER) {
            continue;
          }
          if (!auxDep.gov().equals(typedDep.dep()) || !auxDep.dep().value().equalsIgnoreCase("to")) {
            continue;
          }
          foundAux = true;
          break;
        }
        if (!foundAux) {
          vmod.add(typedDep.dep());
        }
      }
    }
    // log.info("here's the vmod list: " + vmod);

    // Do preposition conjunction interaction for
    // governor p NP and p NP case ... a lot of special code cdm jan 2006

    for (TypedDependency td1 : list) {
      if (td1.reln() != PREPOSITIONAL_MODIFIER) {
        continue;
      }

      IndexedWord td1Dep = td1.dep();
      SortedSet<TypedDependency> possibles = map.get(td1Dep);
      if (possibles == null) {
        continue;
      }

      // look for the "second half"

      // unique: the head prep and whether it should be pobj
      Pair<TypedDependency, Boolean> prepDep = null;
      TypedDependency ccDep = null; // treat as unique
      // list of dep and prepOtherDep and pobj (or pcomp)
      List<Triple<TypedDependency, TypedDependency, Boolean>> conjs = new ArrayList<>();
      Set<TypedDependency> otherDtrs = new TreeSet<>();

      // first look for a conj(prep, prep) (there might be several conj relations!!!)
      boolean samePrepositionInEachConjunct = true;
      int conjIndex = -1;
      for (TypedDependency td2 : possibles) {
        if (td2.reln() == CONJUNCT) {
          IndexedWord td2Dep = td2.dep();
          String td2DepPOS = td2Dep.tag();
          if (td2DepPOS.equals("IN") || td2DepPOS.equals("TO")) {
            samePrepositionInEachConjunct = samePrepositionInEachConjunct && td2Dep.value().equals(td1Dep.value());
            Set<TypedDependency> possibles2 = map.get(td2Dep);
            boolean pobj = true;// default of collapsing preposition is prep_
            TypedDependency prepOtherDep = null;
            if (possibles2 != null) {
              for (TypedDependency td3 : possibles2) {
                IndexedWord td3Dep = td3.dep();
                String td3DepPOS = td3Dep.tag();
                // CDM Mar 2006: I put in disjunction here when I added in
                // PREPOSITIONAL_OBJECT. If it catches all cases, we should
                // be able to delete the DEPENDENT disjunct
                // maybe better to delete the DEPENDENT disjunct - it creates
                // problem with multiple prep (mcdm)
                if ((td3.reln() == PREPOSITIONAL_OBJECT || td3.reln() == PREPOSITIONAL_COMPLEMENT) && (!(td3DepPOS.equals("IN") || td3DepPOS.equals("TO"))) && prepOtherDep == null) {
                  prepOtherDep = td3;
                  if (td3.reln() == PREPOSITIONAL_COMPLEMENT) {
                    pobj = false;
                  }
                } else {
                  otherDtrs.add(td3);
                }
              }
            }
            if (conjIndex < td2Dep.index()) {
              conjIndex = td2Dep.index();
            }
            conjs.add(new Triple<>(td2, prepOtherDep, pobj));
          }
        }
      } // end td2:possibles

      if (conjs.isEmpty()) {
        continue;
      }

      // if we have a conj under a preposition dependency, we look for the other
      // parts

      String td1DepPOS = td1Dep.tag();
      for (TypedDependency td2 : possibles) {
        // we look for the cc linked to this conjDep
        // the cc dep must have an index smaller than the dep of conjDep
        if (td2.reln() == COORDINATION && td2.dep().index() < conjIndex) {
          ccDep = td2;
        } else {
          IndexedWord td2Dep = td2.dep();
          String td2DepPOS = td2Dep.tag();
          // log.info("prepDep find: td1.reln: " + td1.reln() +
          // "; td2.reln: " + td2.reln() + "; td1DepPos: " + td1DepPOS +
          // "; td2DepPos: " + td2DepPOS + "; index " + index +
          // "; td2.dep().index(): " + td2.dep().index());
          if ((td2.reln() == DEPENDENT || td2.reln() == PREPOSITIONAL_OBJECT || td2.reln() == PREPOSITIONAL_COMPLEMENT) && (td1DepPOS.equals("IN") || td1DepPOS.equals("TO") || td1DepPOS.equals("VBG")) && prepDep == null && (!(td2DepPOS.equals("RB") || td2DepPOS.equals("IN") || td2DepPOS.equals("TO")))) {
            // same index trick, in case we have multiple deps
            // I deleted this to see if it helped [cdm Jan 2010] &&
            // td2.dep().index() < index)
            prepDep = new Pair<>(td2, td2.reln() != PREPOSITIONAL_COMPLEMENT);
          } else if (!inConjDeps(td2, conjs)) {// don't want to add the conjDep
            // again!
            otherDtrs.add(td2);
          }
        }
      }

      if (prepDep == null || ccDep == null) {
        continue; // we can't deal with it in the hairy prep/conj interaction case!
      }

      if (DEBUG) {
        // ccDep must be non-null given test above
        log.info("!! Conj and prep case:");
        log.info("  td1 (prep): " + td1);
        log.info("  Kids of td1 are: " + possibles);
        log.info("  prepDep: " + prepDep);
        log.info("  ccDep: " + ccDep);
        log.info("  conjs: " + conjs);
        log.info("  samePrepositionInEachConjunct: " + samePrepositionInEachConjunct);
        log.info("  otherDtrs: " + otherDtrs);
      }

      // check if we have the same prepositions in the conjunction
      if (samePrepositionInEachConjunct) { // conjDep != null && prepOtherDep !=
        // null &&
        // OK, we have a conjunction over parallel PPs: Fred flew to Greece and
        // to Serbia.
        GrammaticalRelation reln = determinePrepRelation(map, vmod, td1, td1, prepDep.second());

        TypedDependency tdNew = new TypedDependency(reln, td1.gov(), prepDep.first().dep());
        newTypedDeps.add(tdNew);
        if (DEBUG) {
          log.info("PrepPoss Conj branch (two parallel PPs) adding: " + tdNew);
          log.info("  removing: " + td1 + "  " + prepDep + "  " + ccDep);
        }
        td1.setReln(KILL);// remember these are "used up"
        prepDep.first().setReln(KILL);
        ccDep.setReln(KILL);

        for (Triple<TypedDependency, TypedDependency, Boolean> trip : conjs) {
          TypedDependency conjDep = trip.first();
          TypedDependency prepOtherDep = trip.second();
          if (prepOtherDep == null) {
            // CDM July 2010: I think this should only ever happen if there is a
            // misparse, but it has happened in such circumstances. You have
            // something like (PP in or in (NP Serbia)), with the two
            // prepositions the same. We just clean up the mess.
            if (DEBUG) {
              log.info("  apparent misparse: same P twice with only one NP object (prepOtherDep is null)");
              log.info("  removing: " + conjDep);
            }
            ccDep.setReln(KILL);
          } else {
            TypedDependency tdNew2 = new TypedDependency(conjValue(ccDep.dep().value()), prepDep.first().dep(), prepOtherDep.dep());
            newTypedDeps.add(tdNew2);
            if (DEBUG) {
              log.info("  adding: " + tdNew2);
              log.info("  removing: " + conjDep + "  " + prepOtherDep);
            }
            prepOtherDep.setReln(KILL);
          }
          conjDep.setReln(KILL);
        }

        // promote dtrs that would be orphaned
        for (TypedDependency otd : otherDtrs) {
          if (DEBUG) {
            log.info("Changed " + otd);
          }
          otd.setGov(td1.gov());
          if (DEBUG) {
            log.info(" to " + otd);
          }
        }

        // Now we need to see if there are any TDs that will be "orphaned"
        // by this collapse. Example: if we have:
        // dep(drew, on)
        // dep(on, book)
        // dep(on, right)
        // the first two will be collapsed to on(drew, book), but then
        // the third one will be orphaned, since its governor no
        // longer appears. So, change its governor to 'drew'.
        // CDM Feb 2010: This used to not move COORDINATION OR CONJUNCT, but now
        // it does, since they're not automatically deleted
        // Some things in possibles may have already been changed, so check gov
        if (DEBUG) {
          log.info("td1: " + td1 + "; possibles: " + possibles);
        }
        for (TypedDependency td2 : possibles) {
          // if (DEBUG) {
          // log.info("[a] td2.reln " + td2.reln() + " td2.gov " +
          // td2.gov() + " td1.dep " + td1.dep());
          // }
          if (td2.reln() != KILL && td2.gov().equals(td1.dep())) { // && td2.reln()
            // != COORDINATION
            // && td2.reln()
            // != CONJUNCT
            if (DEBUG) {
              log.info("Changing " + td2 + " to have governor of " + td1 + " [a]");
            }
            td2.setGov(td1.gov());
          }
        }
        continue; // This one has been dealt with successfully
      } // end same prepositions

      // case of "Lufthansa flies to and from Serbia". Make it look like next
      // case :-)
      // that is, the prepOtherDep should be the same as prepDep !
      for (Triple<TypedDependency, TypedDependency, Boolean> trip : conjs) {
        if (trip.first() != null && trip.second() == null) {
          trip.setSecond(new TypedDependency(prepDep.first().reln(), trip.first().dep(), prepDep.first().dep()));
          trip.setThird(prepDep.second());
        }
      }

      // we have two different prepositions in the conjunction
      // in this case we need to add a node
      // "Bill jumped over the fence and through the hoop"
      // prep_over(jumped, fence)
      // conj_and(jumped, jumped)
      // prep_through(jumped, hoop)
      // Extra complication:
      // If "jumped" is already part of a conjunction, we should add the new one off that rather than chaining
      IndexedWord conjHead = td1.gov();
      for (TypedDependency td3 : list) {
        if (td3.dep().equals(td1.gov()) && td3.reln().equals(CONJUNCT)) {
          conjHead = td3.gov();
        }
      }

      GrammaticalRelation reln = determinePrepRelation(map, vmod, td1, td1, prepDep.second());
      TypedDependency tdNew = new TypedDependency(reln, td1.gov(), prepDep.first().dep());
      newTypedDeps.add(tdNew);
      if (DEBUG) {
        log.info("ConjPP (different preps) adding: " + tdNew);
        log.info("  deleting: " + td1 + "  " + prepDep.first() + "  " + ccDep);
      }
      td1.setReln(KILL);// remember these are "used up"
      prepDep.first().setReln(KILL);
      ccDep.setReln(KILL);
      // so far we added the first prep grammatical relation

      int copyNumber = 1;
      for (Triple<TypedDependency, TypedDependency, Boolean> trip : conjs) {
        TypedDependency conjDep = trip.first();
        TypedDependency prepOtherDep = trip.second();
        boolean pobj = trip.third();
        // OK, we have a conjunction over different PPs
        // we create a new node;
        // in order to make a distinction between the original node and its copy
        // we set the "copyCount" variable in the IndexedWord
        // existence of copyCount > 0 is checked at printing (toString method of
        // TypedDependency)
        IndexedWord label = td1.gov().makeSoftCopy(copyNumber);
        copyNumber++;

        // now we add the conjunction relation between conjHead (either td1.gov
        // or what it is itself conjoined with) and the copy
        // the copy has the same label as td1.gov() but is another TreeGraphNode
        // todo: Or that's the plan; there are a couple of knock on changes to fix before we can do this!
        // TypedDependency tdNew2 = new TypedDependency(conjValue(ccDep.dep().value()), conjHead, label);
        TypedDependency tdNew2 = new TypedDependency(conjValue(ccDep.dep().value()), td1.gov(), label);
        newTypedDeps.add(tdNew2);

        // now we still need to add the second prep grammatical relation
        // between the copy and the dependent of the prepOtherDep node
        TypedDependency tdNew3;

        GrammaticalRelation reln2 = determinePrepRelation(map, vmod, conjDep, td1, pobj);
        tdNew3 = new TypedDependency(reln2, label, prepOtherDep.dep());
        newTypedDeps.add(tdNew3);

        if (DEBUG) {
          log.info("  adding: " + tdNew2 + "  " + tdNew3);
          log.info("  deleting: " + conjDep + "  " + prepOtherDep);
        }
        conjDep.setReln(KILL);
        prepOtherDep.setReln(KILL);

        // promote dtrs that would be orphaned
        for (TypedDependency otd : otherDtrs) {
          // special treatment for prepositions: the original relation is
          // likely to be a "dep" and we want this to be a "prep"
          if (otd.dep().tag().equals("IN")) {
            otd.setReln(PREPOSITIONAL_MODIFIER);
          }
          otd.setGov(td1.gov());
        }
      }

      // Now we need to see if there are any TDs that will be "orphaned" off
      // the first preposition
      // by this collapse. Example: if we have:
      // dep(drew, on)
      // dep(on, book)
      // dep(on, right)
      // the first two will be collapsed to on(drew, book), but then
      // the third one will be orphaned, since its governor no
      // longer appears. So, change its governor to 'drew'.
      // CDM Feb 2010: This used to not move COORDINATION OR CONJUNCT, but now
      // it does, since they're not automatically deleted
      for (TypedDependency td2 : possibles) {
        if (td2.reln() != KILL) { // && td2.reln() != COORDINATION &&
          // td2.reln() != CONJUNCT) {
          if (DEBUG) {
            log.info("Changing " + td2 + " to have governor of " + td1 + " [b]");
          }
          td2.setGov(td1.gov());
        }
      }
      // end for different prepositions
    } // for TypedDependency td1 : list

    // below here is the single preposition/possessor basic case!!
    for (TypedDependency td1 : list) {
      if (td1.reln() == KILL) {
        continue;
      }

      IndexedWord td1Dep = td1.dep();
      String td1DepPOS = td1Dep.tag();
      // find all other typedDeps having our dep as gov
      Set<TypedDependency> possibles = map.get(td1Dep);

      if (possibles != null && (td1.reln() == PREPOSITIONAL_MODIFIER || td1.reln() == POSSESSION_MODIFIER || td1.reln() == CONJUNCT)) {

        // look for the "second half"
        boolean pobj = true;// default for prep relation is prep_
        for (TypedDependency td2 : possibles) {
          if (td2.reln() != COORDINATION && td2.reln() != CONJUNCT) {

            IndexedWord td2Dep = td2.dep();
            String td2DepPOS = td2Dep.tag();
            if ((td1.reln() == POSSESSION_MODIFIER || td1.reln() == CONJUNCT)) {
              if (td2.reln() == POSSESSIVE_MODIFIER) {
                if ( ! map.containsKey(td2Dep)) {  // if 's has no kids of its own (it shouldn't!)
                  td2.setReln(KILL);
                }
              }
            } else if ((td2.reln() == PREPOSITIONAL_OBJECT || td2.reln() == PREPOSITIONAL_COMPLEMENT) && (td1DepPOS.equals("IN") || td1DepPOS.equals("TO") || td1DepPOS.equals("VBG")) && (!(td2DepPOS.equals("RB") || td2DepPOS.equals("IN") || td2DepPOS.equals("TO"))) && !isConjWithNoPrep(td2.gov(), possibles)) {
              // we don't collapse preposition conjoined with a non-preposition
              // to avoid disconnected constituents
              // OK, we have a pair td1, td2 to collapse to td3
              if (DEBUG) {
                log.info("(Single prep/poss base case collapsing " + td1 + " and " + td2);
              }

              // check whether we are in a pcomp case:
              if (td2.reln() == PREPOSITIONAL_COMPLEMENT) {
                pobj = false;
              }

              GrammaticalRelation reln = determinePrepRelation(map, vmod, td1, td1, pobj);
              TypedDependency td3 = new TypedDependency(reln, td1.gov(), td2.dep());
              if (DEBUG) {
                log.info("PP adding: " + td3 + " deleting: " + td1 + ' ' + td2);
              }
              // add it to map to deal with recursive cases like "achieved this (PP (PP in part) with talent)"
              map.get(td3.gov()).add(td3);

              newTypedDeps.add(td3);
              td1.setReln(KILL);// remember these are "used up"
              td2.setReln(KILL);// remember these are "used up"
            }
          }
        } // for TypedDependency td2
      }

      // Now we need to see if there are any TDs that will be "orphaned"
      // by this collapse. Example: if we have:
      // dep(drew, on)
      // dep(on, book)
      // dep(on, right)
      // the first two will be collapsed to on(drew, book), but then
      // the third one will be orphaned, since its governor no
      // longer appears. So, change its governor to 'drew'.
      // CDM Feb 2010: This used to not move COORDINATION OR CONJUNCT, but now
      // it does, since they're not automatically deleted
      if (possibles != null && td1.reln() == KILL) {
        for (TypedDependency td2 : possibles) {
          if (td2.reln() != KILL) { // && td2.reln() != COORDINATION &&
            // td2.reln() != CONJUNCT) {
            if (DEBUG) {
              log.info("Changing " + td2 + " to have governor of " + td1 + " [c]");
            }
            td2.setGov(td1.gov());
          }
        }
      }

    } // for TypedDependency td1

    // now remove typed dependencies with reln "kill" and add new ones.
    for (Iterator<TypedDependency> iter = list.iterator(); iter.hasNext();) {
      TypedDependency td = iter.next();
      if (td.reln() == KILL) {
        if (DEBUG) {
          log.info("Removing dep killed in poss/prep (conj) collapse: " + td);
        }
        iter.remove();
      }
    }
    list.addAll(newTypedDeps);
  } // end collapsePrepAndPoss()


  /** Work out prep relation name. pc is the dependency whose dep() is the
   *  preposition to do a name for. topPrep may be the same or different.
   *  Among the daughters of its gov is where to look for an auxpass.
   */
  private static GrammaticalRelation determinePrepRelation(Map<IndexedWord, ? extends Set<TypedDependency>> map, List<IndexedWord> vmod, TypedDependency pc, TypedDependency topPrep, boolean pobj) {
    // handling the case of an "agent":
    // the governor of a "by" preposition must have an "auxpass" dependency
    // or be the dependent of a "vmod" relation
    // if it is the case, the "agent" variable becomes true
    boolean agent = false;
    String preposition = pc.dep().value().toLowerCase();
    if (preposition.equals("by")) {
      // look if we have an auxpass
      Set<TypedDependency> aux_pass_poss = map.get(topPrep.gov());
      if (aux_pass_poss != null) {
        for (TypedDependency td_pass : aux_pass_poss) {
          if (td_pass.reln() == AUX_PASSIVE_MODIFIER) {
            agent = true;
          }
        }
      }
      // look if we have a vmod
      if (!vmod.isEmpty() && vmod.contains(topPrep.gov())) {
        agent = true;
      }
    }

    GrammaticalRelation reln;
    if (agent) {
      reln = AGENT;
    } else {
      // for prepositions, use the preposition
      // for pobj: we collapse into "prep"; for pcomp: we collapse into "prepc"
      if (pobj) {
        reln = EnglishGrammaticalRelations.getPrep(preposition);
      } else {
        reln = EnglishGrammaticalRelations.getPrepC(preposition);
      }
    }
    return reln;
  }

  // used by collapse2WP(), collapseFlatMWP(), collapse2WPbis() KEPT IN
  // ALPHABETICAL ORDER
  private static final String[][] MULTIWORD_PREPS = { { "according", "to" }, { "across", "from" }, { "ahead", "of" }, { "along", "with" }, { "alongside", "of" }, { "apart", "from" }, { "as", "for" }, { "as", "from" }, { "as", "of" }, { "as", "per" }, { "as", "to" }, { "aside", "from" }, { "away", "from" }, { "based", "on" }, { "because", "of" }, { "close", "by" }, { "close", "to" }, { "contrary", "to" }, { "compared", "to" }, { "compared", "with" }, { "due", "to" }, { "depending", "on" }, { "except", "for" }, { "exclusive", "of" }, { "far", "from" }, { "followed", "by" }, { "inside", "of" }, { "instead", "of" }, { "irrespective", "of" }, { "next", "to" }, { "near", "to" }, { "off", "of" }, { "out", "of" }, { "outside", "of" }, { "owing", "to" }, { "preliminary", "to" },
      { "preparatory", "to" }, { "previous", "to" }, { "prior", "to" }, { "pursuant", "to" }, { "regardless", "of" }, { "subsequent", "to" }, { "such", "as" }, { "thanks", "to" }, { "together", "with" } };

  // used by collapse3WP() KEPT IN ALPHABETICAL ORDER
  private static final String[][] THREEWORD_PREPS = { { "by", "means", "of" }, { "in", "accordance", "with" }, { "in", "addition", "to" }, { "in", "case", "of" }, { "in", "front", "of" }, { "in", "lieu", "of" }, { "in", "place", "of" }, { "in", "spite", "of" }, { "on", "account", "of" }, { "on", "behalf", "of" }, { "on", "top", "of" }, { "with", "regard", "to" }, { "with", "respect", "to" } };

  /**
   * Given a list of typedDependencies, returns true if the node "node" is the
   * governor of a conj relation with a dependent which is not a preposition
   *
   * @param node
   *          A node in this GrammaticalStructure
   * @param list
   *          A list of typedDependencies
   * @return true If node is the governor of a conj relation in the list with
   *         the dep not being a preposition
   */
  private static boolean isConjWithNoPrep(IndexedWord node, Collection<TypedDependency> list) {
    for (TypedDependency td : list) {
      if (td.gov().equals(node) && td.reln() == CONJUNCT) {
        // we have a conjunct
        // check the POS of the dependent
        String tdDepPOS = td.dep().tag();
        if (!(tdDepPOS.equals("IN") || tdDepPOS.equals("TO"))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Collapse multiword preposition of the following format:
   * prep|advmod|dep|amod(gov, mwp[0]) <br/>
   * dep(mpw[0],mwp[1]) <br/>
   * pobj|pcomp(mwp[1], compl) or pobj|pcomp(mwp[0], compl) <br/>
   * -&gt; prep_mwp[0]_mwp[1](gov, compl) <br/>
   *
   * prep|advmod|dep|amod(gov, mwp[1]) <br/>
   * dep(mpw[1],mwp[0]) <br/>
   * pobj|pcomp(mwp[1], compl) or pobj|pcomp(mwp[0], compl) <br/>
   * -&gt; prep_mwp[0]_mwp[1](gov, compl)
   * <p/>
   *
   * The collapsing has to be done at once in order to know exactly which node
   * is the gov and the dep of the multiword preposition. Otherwise this can
   * lead to problems: removing a non-multiword "to" preposition for example!!!
   * This method replaces the old "collapsedMultiWordPreps"
   *
   * @param list
   *          list of typedDependencies to work on
   */
  private static void collapse2WP(Collection<TypedDependency> list) {
    Collection<TypedDependency> newTypedDeps = new ArrayList<>();

    for (String[] mwp : MULTIWORD_PREPS) {
      // first look for patterns such as:
      // X(gov, mwp[0])
      // Y(mpw[0],mwp[1])
      // Z(mwp[1], compl) or Z(mwp[0], compl)
      // -> prep_mwp[0]_mwp[1](gov, compl)
      collapseMultiWordPrep(list, newTypedDeps, mwp[0], mwp[1], mwp[0], mwp[1]);

      // now look for patterns such as:
      // X(gov, mwp[1])
      // Y(mpw[1],mwp[0])
      // Z(mwp[1], compl) or Z(mwp[0], compl)
      // -> prep_mwp[0]_mwp[1](gov, compl)
      collapseMultiWordPrep(list, newTypedDeps, mwp[0], mwp[1], mwp[1], mwp[0]);
    }
  }

  /**
   * Collapse multiword preposition of the following format:
   * prep|advmod|dep|amod(gov, mwp0) dep(mpw0,mwp1) pobj|pcomp(mwp1, compl) or
   * pobj|pcomp(mwp0, compl) -&gt; prep_mwp0_mwp1(gov, compl)
   * <p/>
   *
   * @param list List of typedDependencies to work on,
   * @param newTypedDeps List of typedDependencies that we construct
   * @param str_mwp0 First part of the multiword preposition to construct the collapsed
   *          preposition
   * @param str_mwp1 Second part of the multiword preposition to construct the
   *          collapsed preposition
   * @param w_mwp0 First part of the multiword preposition that we look for
   * @param w_mwp1 Second part of the multiword preposition that we look for
   */
  private static void collapseMultiWordPrep(Collection<TypedDependency> list, Collection<TypedDependency> newTypedDeps, String str_mwp0, String str_mwp1, String w_mwp0, String w_mwp1) {

    // first find the multiword_preposition: dep(mpw[0], mwp[1])
    // the two words should be next to another in the sentence (difference of
    // indexes = 1)
    IndexedWord mwp0 = null;
    IndexedWord mwp1 = null;
    TypedDependency dep = null;
    for (TypedDependency td : list) {
      if (td.gov().value().equalsIgnoreCase(w_mwp0) && td.dep().value().equalsIgnoreCase(w_mwp1) && Math.abs(td.gov().index() - td.dep().index()) == 1) {
        mwp0 = td.gov();
        mwp1 = td.dep();
        dep = td;
      }
    }

    if (mwp0 == null) {
      return;
    }

    // now search for prep|advmod|dep|amod(gov, mwp0)
    IndexedWord governor = null;
    TypedDependency prep = null;
    for (TypedDependency td1 : list) {
      if ((td1.reln() == PREPOSITIONAL_MODIFIER || td1.reln() == ADVERBIAL_MODIFIER || td1.reln() == ADJECTIVAL_MODIFIER || td1.reln() == DEPENDENT || td1.reln() == MULTI_WORD_EXPRESSION) && td1.dep().equals(mwp0)) {
        // we found prep|advmod|dep|amod(gov, mwp0)
        prep = td1;
        governor = prep.gov();
      }
    }

    if (prep == null) {
      return;
    }

    // search for the complement: pobj|pcomp(mwp1,X)
    // or for pobj|pcomp(mwp0,X)
    // There may be more than one in weird constructions; if there are several,
    // take the one with the LOWEST index!
    TypedDependency pobj = null;
    TypedDependency newtd = null;
    for (TypedDependency td2 : list) {
      if ((td2.reln() == PREPOSITIONAL_OBJECT || td2.reln() == PREPOSITIONAL_COMPLEMENT) && (td2.gov().equals(mwp1) || td2.gov().equals(mwp0))) {
        if (pobj == null || pobj.dep().index() > td2.dep().index()) {
          pobj = td2;
          // create the new gr relation
          GrammaticalRelation gr;
          if (td2.reln() == PREPOSITIONAL_COMPLEMENT) {
            gr = EnglishGrammaticalRelations.getPrepC(str_mwp0 + '_' + str_mwp1);
          } else {
            gr = EnglishGrammaticalRelations.getPrep(str_mwp0 + '_' + str_mwp1);
          }
          if (governor != null) {
            newtd = new TypedDependency(gr, governor, pobj.dep());
          }
        }
      }
    }

    if (pobj == null || newtd == null) {
      return;
    }

    // only if we found the three parts, set to KILL and remove
    // and add the new one
    // Necessarily from the above: prep != null, dep != null, pobj != null, newtd != null

    if (DEBUG) {
      log.info("Removing " + prep + ", " + dep + ", and " + pobj);
      log.info("  and adding " + newtd);
    }
    prep.setReln(KILL);
    dep.setReln(KILL);
    pobj.setReln(KILL);
    newTypedDeps.add(newtd);

    // now remove typed dependencies with reln "kill"
    // and promote possible orphans
    for (TypedDependency td1 : list) {
      if (td1.reln() != KILL) {
        if (td1.gov().equals(mwp0) || td1.gov().equals(mwp1)) {
          // CDM: Thought of adding this in Jan 2010, but it causes
          // conflicting relations tmod vs. pobj. Needs more thought
          // maybe restrict pobj to first NP in PP, and allow tmod for a later
          // one?
          if (td1.reln() == TEMPORAL_MODIFIER) {
            // special case when an extra NP-TMP is buried in a PP for
            // "during the same period last year"
            td1.setGov(pobj.dep());
          } else {
            td1.setGov(governor);
          }
        }
        if (!newTypedDeps.contains(td1)) {
          newTypedDeps.add(td1);
        }
      }
    }
    list.clear();
    list.addAll(newTypedDeps);
  }

  /**
   * Collapse multi-words preposition of the following format: advmod|prt(gov,
   * mwp[0]) prep(gov,mwp[1]) pobj|pcomp(mwp[1], compl) -&gt;
   * prep_mwp[0]_mwp[1](gov, compl)
   * <p/>
   *
   * @param list
   *          List of typedDependencies to work on
   */
  private static void collapse2WPbis(Collection<TypedDependency> list) {
    Collection<TypedDependency> newTypedDeps = new ArrayList<>();

    for (String[] mwp : MULTIWORD_PREPS) {
      newTypedDeps.clear();

      IndexedWord mwp0 = null;
      IndexedWord mwp1 = null;
      IndexedWord governor = null;

      TypedDependency prep = null;
      TypedDependency dep = null;
      TypedDependency pobj = null;
      TypedDependency newtd = null;

      // first find the first part of the multi_preposition: advmod|prt(gov, mwp[0])

      for (TypedDependency td : list) {
        if (td.dep().value().equalsIgnoreCase(mwp[0]) && (td.reln() == PHRASAL_VERB_PARTICLE || td.reln() == ADVERBIAL_MODIFIER || td.reln() == DEPENDENT || td.reln() == MULTI_WORD_EXPRESSION)) {
          // we found advmod(gov, mwp0) or prt(gov, mwp0)
          governor = td.gov();
          mwp0 = td.dep();
          dep = td;
        }
      }

      // now search for the second part: prep(gov, mwp1)
      // the two words in the mwp should be next to another in the sentence
      // (difference of indexes = 1)

      if (mwp0 == null || governor == null) {
        continue;
      }

      for (TypedDependency td1 : list) {
        if (td1.reln() == PREPOSITIONAL_MODIFIER && td1.dep().value().equalsIgnoreCase(mwp[1]) && Math.abs(td1.dep().index() - mwp0.index()) == 1 && td1.gov().equals(governor)) {// we
          // found
          // prep(gov,
          // mwp1)
          mwp1 = td1.dep();
          prep = td1;
        }
      }

      if (mwp1 == null) {
        continue;
      }

      // search for the complement: pobj|pcomp(mwp1,X)
      for (TypedDependency td2 : list) {
        if (td2.reln() == PREPOSITIONAL_OBJECT && td2.gov().equals(mwp1)) {
          pobj = td2;
          // create the new gr relation
          GrammaticalRelation gr = EnglishGrammaticalRelations.getPrep(mwp[0] + '_' + mwp[1]);
          newtd = new TypedDependency(gr, governor, pobj.dep());
        }
        if (td2.reln() == PREPOSITIONAL_COMPLEMENT && td2.gov().equals(mwp1)) {
          pobj = td2;
          // create the new gr relation
          GrammaticalRelation gr = EnglishGrammaticalRelations.getPrepC(mwp[0] + '_' + mwp[1]);
          newtd = new TypedDependency(gr, governor, pobj.dep());
        }
      }

      if (pobj == null) {
        return;
      }

      // only if we found the three parts, set to KILL and remove
      // and add the new one
      // now prep != null, pobj != null and newtd != null

      prep.setReln(KILL);
      dep.setReln(KILL);
      pobj.setReln(KILL);
      newTypedDeps.add(newtd);

      // now remove typed dependencies with reln "kill"
      // and promote possible orphans
      for (TypedDependency td1 : list) {
        if (td1.reln() != KILL) {
          if (td1.gov().equals(mwp0) || td1.gov().equals(mwp1)) {
            td1.setGov(governor);
          }
          if (!newTypedDeps.contains(td1)) {
            newTypedDeps.add(td1);
          }
        }
      }
      list.clear();
      list.addAll(newTypedDeps);
    }
  }

  /**
   * Collapse 3-word preposition of the following format: <br/>
   * This will be the case when the preposition is analyzed as a NP <br/>
   * prep(gov, mwp0) <br/>
   * X(mwp0,mwp1) <br/>
   * X(mwp1,mwp2) <br/>
   * pobj|pcomp(mwp2, compl) <br/>
   * -&gt; prep_mwp[0]_mwp[1]_mwp[2](gov, compl)
   * <p/>
   *
   * It also takes flat annotation into account: <br/>
   * prep(gov,mwp0) <br/>
   * X(mwp0,mwp1) <br/>
   * X(mwp0,mwp2) <br/>
   * pobj|pcomp(mwp0, compl) <br/>
   * -&gt; prep_mwp[0]_mwp[1]_mwp[2](gov, compl)
   * <p/>
   *
   *
   * @param list List of typedDependencies to work on
   */
  private static void collapse3WP(Collection<TypedDependency> list) {
    Collection<TypedDependency> newTypedDeps = new ArrayList<>();

    // first, loop over the prepositions for NP annotation
    for (String[] mwp : THREEWORD_PREPS) {
      newTypedDeps.clear();

      IndexedWord mwp0 = null;
      IndexedWord mwp1 = null;
      IndexedWord mwp2 = null;

      TypedDependency dep1 = null;
      TypedDependency dep2 = null;

      // first find the first part of the 3word preposition: dep(mpw[0], mwp[1])
      // the two words should be next to another in the sentence (difference of
      // indexes = 1)

      for (TypedDependency td : list) {
        if (td.gov().value().equalsIgnoreCase(mwp[0]) && td.dep().value().equalsIgnoreCase(mwp[1]) && Math.abs(td.gov().index() - td.dep().index()) == 1) {
          mwp0 = td.gov();
          mwp1 = td.dep();
          dep1 = td;
        }
      }

      // find the second part of the 3word preposition: dep(mpw[1], mwp[2])
      // the two words should be next to another in the sentence (difference of
      // indexes = 1)

      for (TypedDependency td : list) {
        if (td.gov().equals(mwp1) && td.dep().value().equalsIgnoreCase(mwp[2]) && Math.abs(td.gov().index() - td.dep().index()) == 1) {
          mwp2 = td.dep();
          dep2 = td;
        }
      }

      if (dep1 != null && dep2 != null) {

        // now search for prep(gov, mwp0)
        IndexedWord governor = null;
        TypedDependency prep = null;
        for (TypedDependency td1 : list) {
          if (td1.reln() == PREPOSITIONAL_MODIFIER && td1.dep().equals(mwp0)) {// we
            // found
            // prep(gov,
            // mwp0)
            prep = td1;
            governor = prep.gov();
          }
        }

        // search for the complement: pobj|pcomp(mwp2,X)

        TypedDependency pobj = null;
        TypedDependency newtd = null;
        for (TypedDependency td2 : list) {
          if (td2.reln() == PREPOSITIONAL_OBJECT && td2.gov().equals(mwp2)) {
            pobj = td2;
            // create the new gr relation
            GrammaticalRelation gr = EnglishGrammaticalRelations.getPrep(mwp[0] + '_' + mwp[1] + '_' + mwp[2]);
            if (governor != null) {
              newtd = new TypedDependency(gr, governor, pobj.dep());
            }
          }
          if (td2.reln() == PREPOSITIONAL_COMPLEMENT && td2.gov().equals(mwp2)) {
            pobj = td2;
            // create the new gr relation
            GrammaticalRelation gr = EnglishGrammaticalRelations.getPrepC(mwp[0] + '_' + mwp[1] + '_' + mwp[2]);
            if (governor != null) {
              newtd = new TypedDependency(gr, governor, pobj.dep());
            }
          }
        }

        // only if we found the governor and complement parts, set to KILL and
        // remove
        // and add the new one
        if (prep != null && pobj != null && newtd != null) {
          prep.setReln(KILL);
          dep1.setReln(KILL);
          dep2.setReln(KILL);
          pobj.setReln(KILL);
          newTypedDeps.add(newtd);

          // now remove typed dependencies with reln "kill"
          // and promote possible orphans
          for (TypedDependency td1 : list) {
            if (td1.reln() != KILL) {
              if (td1.gov().equals(mwp0) || td1.gov().equals(mwp1) || td1.gov().equals(mwp2)) {
                td1.setGov(governor);
              }
              if (!newTypedDeps.contains(td1)) {
                newTypedDeps.add(td1);
              }
            }
          }
          list.clear();
          list.addAll(newTypedDeps);
        }
      }
    }

    // second, loop again looking at flat annotation
    for (String[] mwp : THREEWORD_PREPS) {
      newTypedDeps.clear();

      IndexedWord mwp0 = null;
      IndexedWord mwp1 = null;
      IndexedWord mwp2 = null;

      TypedDependency dep1 = null;
      TypedDependency dep2 = null;

      // first find the first part of the 3word preposition: dep(mpw[0], mwp[1])
      // the two words should be next to another in the sentence (difference of
      // indexes = 1)
      for (TypedDependency td : list) {
        if (td.gov().value().equalsIgnoreCase(mwp[0]) && td.dep().value().equalsIgnoreCase(mwp[1]) && Math.abs(td.gov().index() - td.dep().index()) == 1) {
          mwp0 = td.gov();
          mwp1 = td.dep();
          dep1 = td;
        }
      }

      // find the second part of the 3word preposition: dep(mpw[0], mwp[2])
      // the two words should be one word apart in the sentence (difference of
      // indexes = 2)
      for (TypedDependency td : list) {
        if (td.gov().equals(mwp0) && td.dep().value().equalsIgnoreCase(mwp[2]) && Math.abs(td.gov().index() - td.dep().index()) == 2) {
          mwp2 = td.dep();
          dep2 = td;
        }
      }

      if (dep1 != null && dep2 != null) {

        // now search for prep(gov, mwp0)
        IndexedWord governor = null;
        TypedDependency prep = null;
        for (TypedDependency td1 : list) {
          if (td1.dep().equals(mwp0) && td1.reln() == PREPOSITIONAL_MODIFIER) {// we
            // found
            // prep(gov,
            // mwp0)
            prep = td1;
            governor = prep.gov();
          }
        }

        // search for the complement: pobj|pcomp(mwp0,X)

        TypedDependency pobj = null;
        TypedDependency newtd = null;
        for (TypedDependency td2 : list) {
          if (td2.gov().equals(mwp0) && td2.reln() == PREPOSITIONAL_OBJECT) {
            pobj = td2;
            // create the new gr relation
            GrammaticalRelation gr = EnglishGrammaticalRelations.getPrep(mwp[0] + '_' + mwp[1] + '_' + mwp[2]);
            if (governor != null) {
              newtd = new TypedDependency(gr, governor, pobj.dep());
            }
          }
          if (td2.gov().equals(mwp0) && td2.reln() == PREPOSITIONAL_COMPLEMENT) {
            pobj = td2;
            // create the new gr relation
            GrammaticalRelation gr = EnglishGrammaticalRelations.getPrepC(mwp[0] + '_' + mwp[1] + '_' + mwp[2]);
            if (governor != null) {
              newtd = new TypedDependency(gr, governor, pobj.dep());
            }
          }
        }

        // only if we found the governor and complement parts, set to KILL and
        // remove
        // and add the new one
        if (prep != null && pobj != null && newtd != null) {
          prep.setReln(KILL);
          dep1.setReln(KILL);
          dep2.setReln(KILL);
          pobj.setReln(KILL);
          newTypedDeps.add(newtd);

          // now remove typed dependencies with reln "kill"
          // and promote possible orphans
          for (TypedDependency td1 : list) {
            if (td1.reln() != KILL) {
              if (td1.gov().equals(mwp0) || td1.gov().equals(mwp1) || td1.gov().equals(mwp2)) {
                td1.setGov(governor);
              }
              if (!newTypedDeps.contains(td1)) {
                newTypedDeps.add(td1);
              }
            }
          }
          list.clear();
          list.addAll(newTypedDeps);
        }
      }
    }
  }

  /*
   *
   * While upgrading, here are some lists of common multiword prepositions which
   * we might try to cover better. (Also do corpus counts for same?)
   *
   * (Prague Dependency Treebank) as per CRIT except for RESTR but for RESTR
   * apart from RESTR away from RESTR aside from RESTR as from TSIN ahead of
   * TWHEN back of LOC, DIR3 exclusive of* RESTR instead of SUBS outside of LOC,
   * DIR3 off of DIR1 upwards of LOC, DIR3 as of TSIN because of CAUS inside of
   * LOC, DIR3 irrespective of REG out of LOC, DIR1 regardless of REG according
   * to CRIT due to CAUS next to LOC, RESTR owing to* CAUS preparatory to* TWHEN
   * prior to* TWHEN subsequent to* TWHEN as to/for REG contrary to* CPR close
   * to* LOC, EXT (except the case named in the next table) near to LOC, DIR3
   * nearer to LOC, DIR3 preliminary to* TWHEN previous to* TWHEN pursuant to*
   * CRIT thanks to CAUS along with ACMP together with ACMP devoid of* ACMP void
   * of* ACMP
   *
   * http://www.keepandshare.com/doc/view.php?u=13166
   *
   * according to ahead of as far as as well as by means of due to far from in
   * addition to in case of in front of in place of in spite of inside of
   * instead of in to (into) near to next to on account of on behalf of on top
   * of on to (onto) out of outside of owing to prior to with regards to
   *
   * www.eslmonkeys.com/book/learner/prepositions.pdf According to Ahead of
   * Along with Apart from As for As to Aside from Because of But for Contrary
   * to Except for Instead of Next to Out of Prior to Thanks to
   */

  /**
   * Collapse multi-words preposition of the following format, which comes from
   * flat annotation. This handles e.g., "because of" (PP (IN because) (IN of)
   * ...), "such as" (PP (JJ such) (IN as) ...)
   * <p/>
   * prep(gov, mwp[1]) dep(mpw[1], mwp[0]) pobj(mwp[1], compl) -&gt;
   * prep_mwp[0]_mwp[1](gov, compl)
   *
   * @param list List of typedDependencies to work on
   */
  private static void collapseFlatMWP(Collection<TypedDependency> list) {
    Collection<TypedDependency> newTypedDeps = new ArrayList<>();

    for (String[] mwp : MULTIWORD_PREPS) {
      newTypedDeps.clear();

      IndexedWord mwp1 = null;
      IndexedWord governor = null;

      TypedDependency prep = null;
      TypedDependency dep = null;
      TypedDependency pobj = null;

      // first find the multi_preposition: dep(mpw[1], mwp[0])
      for (TypedDependency td : list) {
        if (Math.abs(td.gov().index() - td.dep().index()) == 1 && td.gov().value().equalsIgnoreCase(mwp[1]) && td.dep().value().equalsIgnoreCase(mwp[0])) {
          mwp1 = td.gov();
          dep = td;
        }
      }

      if (mwp1 == null) {
        continue;
      }

      // now search for prep(gov, mwp1)
      for (TypedDependency td1 : list) {
        if (td1.dep().equals(mwp1) && td1.reln() == PREPOSITIONAL_MODIFIER) {
          // we found prep(gov, mwp1)
          prep = td1;
          governor = prep.gov();
        }
      }

      if (prep == null) {
        continue;
      }

      // search for the complement: pobj|pcomp(mwp1,X)
      for (TypedDependency td2 : list) {
        if (td2.gov().equals(mwp1) && td2.reln() == PREPOSITIONAL_OBJECT) {
          pobj = td2;
          // create the new gr relation
          GrammaticalRelation gr = EnglishGrammaticalRelations.getPrep(mwp[0] + '_' + mwp[1]);
          newTypedDeps.add(new TypedDependency(gr, governor, pobj.dep()));
        }
        if (td2.gov().equals(mwp1) && td2.reln() == PREPOSITIONAL_COMPLEMENT) {
          pobj = td2;
          // create the new gr relation
          GrammaticalRelation gr = EnglishGrammaticalRelations.getPrepC(mwp[0] + '_' + mwp[1]);
          newTypedDeps.add(new TypedDependency(gr, governor, pobj.dep()));
        }
      }

      if (pobj == null) {
        return;
      }
      // only if we found the three parts, set to KILL and remove
      // we know prep != null && dep != null && dep != null
      prep.setReln(KILL);
      dep.setReln(KILL);
      pobj.setReln(KILL);

      // now remove typed dependencies with reln "kill"
      // and promote possible orphans
      for (TypedDependency td1 : list) {
        if (td1.reln() != KILL) {
          if (td1.gov().equals(mwp1)) {
            td1.setGov(governor);
          }
          if (!newTypedDeps.contains(td1)) {
            newTypedDeps.add(td1);
          }
        }
      }
      list.clear();
      list.addAll(newTypedDeps);
    }
  }

  /**
   * This method gets rid of multiwords in conjunctions to avoid having them
   * creating disconnected constituents e.g.,
   * "bread-1 as-2 well-3 as-4 cheese-5" will be turned into conj_and(bread,
   * cheese) and then dep(well-3, as-2) and dep(well-3, as-4) cannot be attached
   * to the graph, these dependencies are erased
   *
   * @param list List of words to get rid of multiword conjunctions from
   */
  private static void eraseMultiConj(Collection<TypedDependency> list) {
    // find typed deps of form cc(gov, x)
    for (TypedDependency td1 : list) {
      if (td1.reln() == COORDINATION) {
        IndexedWord x = td1.dep();
        // find typed deps of form dep(x,y) and kill them
        for (TypedDependency td2 : list) {
          if (td2.gov().equals(x) && (td2.reln() == DEPENDENT || td2.reln() == MULTI_WORD_EXPRESSION || td2.reln() == COORDINATION ||
                  td2.reln() == ADVERBIAL_MODIFIER || td2.reln() == NEGATION_MODIFIER || td2.reln() == AUX_MODIFIER)) {
            td2.setReln(KILL);
          }
        }
      }
    }

    filterKill(list);
  }

  /**
   * Remove duplicate relations: it can happen when collapsing stranded
   * prepositions. E.g., "What does CPR stand for?" we get dep(stand, what), and
   * after collapsing we also get prep_for(stand, what).
   *
   * @param list A list of typed dependencies to check through
   */
  private static void removeDep(Collection<TypedDependency> list) {
    Set<GrammaticalRelation> prepRels = Generics.newHashSet(EnglishGrammaticalRelations.getPreps());
    prepRels.addAll(EnglishGrammaticalRelations.getPrepsC());
    for (TypedDependency td1 : list) {
      if (prepRels.contains(td1.reln())) { // if we have a prep_ relation
        IndexedWord gov = td1.gov();
        IndexedWord dep = td1.dep();

        for (TypedDependency td2 : list) {
          if (td2.reln() == DEPENDENT && td2.gov().equals(gov) && td2.dep().equals(dep)) {
            td2.setReln(KILL);
          }
        }
      }
    }

    // now remove typed dependencies with reln "kill"
    for (Iterator<TypedDependency> iter = list.iterator(); iter.hasNext();) {
      TypedDependency td = iter.next();
      if (td.reln() == KILL) {
        if (DEBUG) {
          log.info("Removing duplicate relation: " + td);
        }
        iter.remove();
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
  private static void removeExactDuplicates(Collection<TypedDependency> list) {
    Set<TypedDependency> set = new TreeSet<>(list);
    list.clear();
    list.addAll(set);
  }


  public static List<GrammaticalStructure> readCoNLLXGrammaticalStructureCollection(String fileName) throws IOException {
    return readCoNLLXGrammaticalStructureCollection(fileName, EnglishGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static EnglishGrammaticalStructure buildCoNLLXGrammaticalStructure(List<List<String>> tokenFields) {
    return (EnglishGrammaticalStructure) buildCoNLLXGrammaticalStructure(tokenFields, EnglishGrammaticalRelations.shortNameToGRel, new FromDependenciesFactory());
  }

  public static class FromDependenciesFactory
    implements GrammaticalStructureFromDependenciesFactory {
    @Override
    public EnglishGrammaticalStructure build(List<TypedDependency> tdeps, TreeGraphNode root) {
      return new EnglishGrammaticalStructure(tdeps, root);
    }
  }

  public static void main(String[] args) {
    GrammaticalStructureConversionUtils.convertTrees(args, "en-sd");
  }

} // end class EnglishGrammaticalStructure
