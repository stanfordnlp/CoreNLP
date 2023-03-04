package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

import static edu.stanford.nlp.trees.GrammaticalRelation.ROOT;
import static edu.stanford.nlp.trees.ud.UniversalGrammaticalRelations.*;

public class UniversalGrammaticalStructure extends GrammaticalStructure {

    //for Joakim
    //private static final double RELCL_EDGE_WEIGHT = 3.0;
    //private static final double CONTROL_EDGE_WEIGHT = 1.0;
    //private static final double CONJPROP_EDGE_WEIGHT = 5.0;


    private static final double RELCL_EDGE_WEIGHT = Double.NEGATIVE_INFINITY;
    private static final double CONTROL_EDGE_WEIGHT =  Double.NEGATIVE_INFINITY;
    private static final double CONJPROP_EDGE_WEIGHT =  Double.NEGATIVE_INFINITY;

    public UniversalGrammaticalStructure(List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
        super(projectiveDependencies, root);
    }



    public static void addRef(SemanticGraph sg, Pattern relativizingWordPattern) {
        for (SemanticGraphEdge edge : sg.findAllRelns("acl:relcl")) {
            IndexedWord head = edge.getGovernor();
            IndexedWord modifier = edge.getDependent();

            SemanticGraphEdge leftChildEdge = null;
            for (SemanticGraphEdge childEdge : sg.outgoingEdgeIterable(modifier)) {
                 if (relativizingWordPattern.matcher(childEdge.getDependent().value()).matches() &&
                        (leftChildEdge == null || childEdge.getDependent().index() < leftChildEdge.getDependent().index())) {
                    leftChildEdge = childEdge;
                }
            }

            SemanticGraphEdge leftGrandchildEdge = null;
            for (SemanticGraphEdge childEdge : sg.outgoingEdgeIterable(modifier)) {
                if (childEdge.getRelation().getShortName().contains("comp")
                        || childEdge.getRelation().getShortName().contains("conj")
                        || childEdge.getRelation().getShortName().contains("parataxis")
                        || childEdge.getRelation().getShortName().contains("discourse")
                        || childEdge.getRelation().getShortName().contains("advcl")
                        || childEdge.getRelation().getShortName().contains("acl")
                        || childEdge.getRelation().getShortName().contains("list")
                        || childEdge.getRelation().getShortName().contains("orphan")
                        || childEdge.getRelation().getShortName().contains("vocative")
                        || childEdge.getRelation().getShortName().contains("dislocated")
                        || childEdge.getRelation().getShortName().contains("appos")) {
                    continue;
                }
                for (SemanticGraphEdge grandchildEdge : sg.outgoingEdgeIterable(childEdge.getDependent())) {
                    if (relativizingWordPattern.matcher(grandchildEdge.getDependent().value()).matches() &&
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
                sg.addEdge(head, newDep, UniversalGrammaticalRelations.REFERENT, RELCL_EDGE_WEIGHT, false);
            }
        }
    }

    public static void collapseReferent(SemanticGraph sg) {
        // find typed deps of form ref(gov, dep)
        // put them in a List for processing
        List<SemanticGraphEdge> refs = new ArrayList<>(sg.findAllRelns("ref"));

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
                if (! edge.getRelation().getShortName().equals("ref") && ! edge.getGovernor().equals(ant)) {
                    sg.removeEdge(edge);

                    GrammaticalRelation reln = edge.getRelation();
                    if (edge.getRelation().getShortName().equals("obj")) {
                        reln = RELATIVE_OBJECT;
                    } else if (edge.getRelation().getShortName().equals("nsubj")) {
                        reln = RELATIVE_NOMINAL_SUBJECT;
                    } else if (edge.getRelation().getShortName().equals("nsubj:pass")) {
                        reln = RELATIVE_NOMINAL_PASSIVE_SUBJECT;
                    }

                    sg.addEdge(edge.getGovernor(), ant, reln, RELCL_EDGE_WEIGHT, true);
                }
            }
        }
    }


    public static void addExtraNSubj(SemanticGraph sg) {

        for (SemanticGraphEdge xcomp : sg.findAllRelns("xcomp")) {
            IndexedWord modifier = xcomp.getDependent();
            IndexedWord head = xcomp.getGovernor();

            boolean hasSubjectDaughter = false;

            //TODO: unclear whether we can do something like this on a universal level
            //boolean hasAux = false;
            List<IndexedWord> subjects = Generics.newArrayList();
            List<IndexedWord> objects = Generics.newArrayList();
            for (SemanticGraphEdge dep : sg.edgeIterable()) {
                // already have a subject dependency
                if ((dep.getRelation().getShortName().startsWith("nsubj")) && dep.getGovernor().equals(modifier)) {
                    hasSubjectDaughter = true;
                    break;
                }

               // if ((dep.getRelation().getShortName().equals("aux")
               //         || dep.getRelation().getShortName().equals("marker"))
               //         && dep.getGovernor().equals(modifier)) {
               //     hasAux = true;
               // }

                if ((dep.getRelation().getShortName().startsWith("nsubj")) && dep.getGovernor().equals(head)) {
                    subjects.add(dep.getDependent());
                }

                if (dep.getRelation().getShortName().equals("obj") && dep.getGovernor().equals(head)) {
                    objects.add(dep.getDependent());
                }
            }

            // if we already have an nsubj dependency, no need to add an extra nsubj
            if (hasSubjectDaughter) {
                continue;
            }

            if (modifier.get(CoreAnnotations.CoarseTagAnnotation.class).equalsIgnoreCase("PART")) {
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
                        sg.addEdge(modifier, object, CONTROLLING_NOMINAL_SUBJECT, CONTROL_EDGE_WEIGHT, true);
                }
            } else {
                for (IndexedWord subject : subjects) {
                    if ( ! sg.containsEdge(modifier, subject))
                        sg.addEdge(modifier, subject, CONTROLLING_NOMINAL_SUBJECT, CONTROL_EDGE_WEIGHT, true);
                }
            }
        }
    }


    public static void propagateConjuncts(SemanticGraph sg) {

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

        //all other core-arguments
        Map<GrammaticalRelation, Map<IndexedWord, SemanticGraphEdge>> coreArguments = Generics.newHashMap();
        coreArguments.put(XCLAUSAL_COMPLEMENT, Generics.newHashMap());
        coreArguments.put(CLAUSAL_COMPLEMENT, Generics.newHashMap());
        coreArguments.put(DIRECT_OBJECT, Generics.newHashMap());
        coreArguments.put(INDIRECT_OBJECT, Generics.newHashMap());




        Map<IndexedWord, SemanticGraphEdge> explMap = Generics.newHashMap();


        List<IndexedWord> rcmodHeads = Generics.newArrayList();

        for (SemanticGraphEdge edge : sg.edgeIterable()) {
            if (!map.containsKey(edge.getDependent())) {
                // NB: Here and in other places below, we use a TreeSet (which extends
                // SortedSet) to guarantee that results are deterministic)
                map.put(edge.getDependent(), new TreeSet<>());
            }
            map.get(edge.getDependent()).add(edge);

            if (edge.getRelation().getShortName().equals("aux:pass")) {
                withPassiveAuxiliary.add(edge.getGovernor());
            }

            // look for subjects
            if (edge.getRelation().getShortName().contains("subj")) {
                if (!subjectMap.containsKey(edge.getGovernor())) {
                    subjectMap.put(edge.getGovernor(), edge);
                }
            }

            // look for expletives
            else if (edge.getRelation().getShortName().equals("expl")) {
                    explMap.putIfAbsent(edge.getGovernor(), edge);
            }

            // look for acl:relcl relations
            else if (edge.getRelation().getShortName().equals("acl:relcl")) {
                rcmodHeads.add(edge.getGovernor());
            }

            // look for xcomp relations
            else if (edge.getRelation().getShortName().equals("xcomp")) {
                coreArguments.get(XCLAUSAL_COMPLEMENT).put(edge.getGovernor(), edge);

            }

            // look for ccomp relations
            else if (edge.getRelation().getShortName().equals("ccomp")) {
                coreArguments.get(CLAUSAL_COMPLEMENT).put(edge.getGovernor(), edge);
            }

            // look for obj relations
            else if (edge.getRelation().getShortName().equals("obj")) {
                coreArguments.get(DIRECT_OBJECT).put(edge.getGovernor(), edge);

            }

            //look for iobj
            else if (edge.getRelation().getShortName().equals("iobj")) {
                coreArguments.get(INDIRECT_OBJECT).put(edge.getGovernor(), edge);
            }

        }

        // create a new list of typed dependencies
        //Collection<TypedDependency> newTypedDeps = new ArrayList<TypedDependency>(list);

        SemanticGraph sgCopy = sg.makeSoftCopy();

        // find typed deps of form conj(gov,dep)
        for (SemanticGraphEdge edge: sgCopy.edgeIterable()) {
            if (edge.getRelation().getShortName().equals("conj")) {
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
                        if (newRel != ROOT
                                //&& ! newRel.getShortName().equals("case")
                                //&& ! newRel.getShortName().equals("advcl")
                                //&& ! newRel.getShortName().equals("acl")
                                && ! newRel.getShortName().equals("dislocated")
                                && ! newRel.getShortName().equals("vocative")
                                && ! newRel.getShortName().equals("discourse")
                                && ! newRel.getShortName().equals("parataxis")
                                && ! newRel.getShortName().equals("list")
                                && ! newRel.getShortName().equals("orphan")
                                && ! newRel.getShortName().equals("conj")) {
                            if (rcmodHeads.contains(gov) && rcmodHeads.contains(dep)) {
                                // to prevent wrong propagation in the case of long dependencies in relative clauses
                                if (! newRel.getShortName().equals("obj") && newRel.getShortName().equals("nsubj")) {
                                    sg.addEdge(newGov, dep, newRel, CONJPROP_EDGE_WEIGHT, true);
                                }
                            } else {
                                sg.addEdge(newGov, dep, newRel, CONJPROP_EDGE_WEIGHT, true);
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
                // SS 2017: I relaxed this assumption for UD; otherwise we'll miss many copular
                // constructions
                if (subjectMap.containsKey(gov)
                        && ! subjectMap.containsKey(dep)
                        && ! explMap.containsKey(dep)) {
                    SemanticGraphEdge tdsubj = subjectMap.get(gov);
                    GrammaticalRelation relation = tdsubj.getRelation();
                    if (relation.getShortName().equals("nsubj")) {
                        if (withPassiveAuxiliary.contains(dep)) {
                            relation = UniversalGrammaticalRelations.NOMINAL_PASSIVE_SUBJECT;
                        }
                    } else if (relation.getShortName().equals("csubj")) {
                        if (withPassiveAuxiliary.contains(dep)) {
                            relation = UniversalGrammaticalRelations.CLAUSAL_PASSIVE_SUBJECT;
                        }
                    }
                    sg.addEdge(dep, tdsubj.getDependent(), relation, CONJPROP_EDGE_WEIGHT, true);
                }

                for (GrammaticalRelation reln : coreArguments.keySet()) {
                    Map<IndexedWord, SemanticGraphEdge> arguments = coreArguments.get(reln);
                    if (arguments.containsKey(gov) && ! arguments.containsKey(dep)) {
                        SemanticGraphEdge argEdge = arguments.get(gov);
                        //Heuristic: If object depends on head of coordinated phrase, but appears after conjunct,
                        //propagate the conjunct
                        if (dep.index() < argEdge.getDependent().index()) {
                            sg.addEdge(dep, argEdge.getDependent(), argEdge.getRelation(), CONJPROP_EDGE_WEIGHT, true);
                        }
                    }
                }
            }
        }

    }


    //add prepositions
    private static SemgrexPattern[] PREP_PATTERNS = {
            SemgrexPattern.compile("{}=gov   >/^(nmod|obl)$/=reln ({}=mod >case {}=c1)"),
            SemgrexPattern.compile("{}=gov   >/^(advcl|acl)$/=reln ({}=mod >/^(mark|case)$/ {}=c1)")
    };

    public static final void addCaseMarkerInformation(SemanticGraph sg) {
        for (SemgrexPattern p: PREP_PATTERNS) {
            SemanticGraph sgCopy = sg.makeSoftCopy();
            SemgrexMatcher matcher = p.matcher(sgCopy);
            IndexedWord oldCaseMarker = null;
            while (matcher.find()) {


                IndexedWord caseMarker = matcher.getNode("c1");

                if (oldCaseMarker != null && caseMarker.equals(oldCaseMarker)) {
                    continue;
                }

                IndexedWord gov = matcher.getNode("gov");
                IndexedWord mod = matcher.getNode("mod");
                addCaseMarkersToReln(sg, gov, mod, caseMarker);

                oldCaseMarker = caseMarker;
            }
        }
    }


    /**
     * Post-process graph and copy over case markers in case the PP-complement
     * is conjoined.
     * @param sg
     */

    public static void addCaseMarkerForConjunctions(SemanticGraph sg) {
        SemanticGraph sgCopy = sg.makeSoftCopy();
        for (SemanticGraphEdge edge : sgCopy.edgeIterable()) {
            String relnName = edge.getRelation().toString();
            if (relnName.equals("nmod") || relnName.equals("obl") || relnName.equals("acl") || relnName.equals("advcl")) {
                Set<IndexedWord> conjParents =  sg.getParentsWithReln(edge.getDependent(), "conj");
                for (IndexedWord conjParent : conjParents) {
                    List<SemanticGraphEdge> conjParentIncomingEdges = sg.getIncomingEdgesSorted(conjParent);
                    boolean changed = false;
                    for (SemanticGraphEdge edge1: conjParentIncomingEdges) {

                        if (edge1.getRelation().toString().startsWith(relnName) && edge1.getRelation().getSpecific() != null) {
                            changed = true;
                            sg.updateEdge(edge, edge1.getRelation());
                            break;
                        }
                    }
                    if (changed) {
                        break;
                    }
                }
            }
        }
    }

    private static void addCaseMarkersToReln(SemanticGraph sg, IndexedWord gov, IndexedWord mod, IndexedWord caseMarker) {
        SemanticGraphEdge edge = sg.getEdge(gov, mod);

        List<IndexedWord> caseMarkers = new ArrayList<>();
        caseMarkers.add(caseMarker);
        sg.getChildrenWithReln(caseMarker, FIXED).stream().forEach(iw -> caseMarkers.add(iw));

        Collections.sort(caseMarkers);

        String relnName = StringUtils.join(caseMarkers.stream().map(iw->iw.lemma()), "_");

        if (relnName.matches("[^a-zA-Z_]")) {
            return;
        }

        //for Joakim
        //GrammaticalRelation reln = getCaseMarkedRelation(edge.getRelation(), relnName.toLowerCase() + ":ENH_CASE");
        GrammaticalRelation reln = getCaseMarkedRelation(edge.getRelation(), relnName.toLowerCase());

        sg.updateEdge(edge, reln);
    }

    /**
     *
     * Returns a GrammaticalRelation which combines the original relation and
     * the preposition.
     *
     */
    private static GrammaticalRelation getCaseMarkedRelation(GrammaticalRelation reln, String relationName) {
        GrammaticalRelation newReln = reln;

        if (reln.getShortName().equals("nmod")) {
            newReln = UniversalGrammaticalRelations.getNmod(relationName);
        } else if (reln.getShortName().equals("obl")) {
            newReln = UniversalGrammaticalRelations.getObl(relationName);
        } else if (reln.getShortName().equals("advcl")) {
            newReln = UniversalGrammaticalRelations.getAdvcl(relationName);
        } else if (reln.getShortName().equals("acl")) {
            newReln = UniversalGrammaticalRelations.getAcl(relationName);
        }
        return newReln;
    }





    //TODO: do something about apples or oranges, and bananas?

    //add conjunctions to relations names
    private static final SemgrexPattern CONJUNCTION_PATTERN = SemgrexPattern.compile("{}=gov  >conj ({} >cc {}=cc) >conj {}=conj " );


    /**
     * Adds the type of conjunction to all conjunct relations.
     * <br>
     * {@code cc(Marie, and)}, {@code conj(Marie, Chris)} and {@code conj(Marie, John)}
     * become {@code cc(Marie, and)}, {@code conj:and(Marie, Chris)} and {@code conj:and(Marie, John)}.
     * <br>
     * In case multiple coordination marker depend on the same governor
     * the one that precedes the conjunct is appended to the conjunction relation or the
     * first one if no preceding marker exists.
     * <br>
     *
     * @param sg A SemanticGraph from a sentence
     */
    public static void addConjInformation(SemanticGraph sg) {

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
            if (edge.getRelation().toString().equals("conj") || conjDep.index() > ccDep.index()) {
                //for Joakim
                //edge.setRelation(UniversalGrammaticalRelations.getConj(conjValue(ccDep, sg) + ":ENH_CONJ"));

                String relnName = conjValue(ccDep, sg);

                if (relnName.matches("[^a-zA-Z_]")) {
                    continue;
                }
                sg.updateEdge(edge, UniversalGrammaticalRelations.getConj(relnName));
            }
        }
    }


    private static String conjValue(IndexedWord cc, SemanticGraph sg) {
        List<IndexedWord> yield = sg.yield(cc);

        if (yield.size() < 2) {
            return cc.lemma();
        }

        List<String> ccParts = new LinkedList<>();

        yield.stream().forEach(iw -> ccParts.add(iw.lemma()));

        return StringUtils.join(ccParts, "_").toLowerCase();


    }

}
