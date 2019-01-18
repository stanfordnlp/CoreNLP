package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Generics;

import java.util.*;
import java.util.regex.Pattern;

import static edu.stanford.nlp.trees.GrammaticalRelation.ROOT;
import static edu.stanford.nlp.trees.ud.UniversalGrammaticalRelations.CONTROLLING_NOMINAL_SUBJECT;

public class UniversalGrammaticalStructure extends GrammaticalStructure {

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
                sg.addEdge(head, newDep, UniversalGrammaticalRelations.REFERENT, Double.NEGATIVE_INFINITY, false);
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
                    sg.addEdge(edge.getGovernor(), ant, edge.getRelation(), Double.NEGATIVE_INFINITY, true);
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
        // Map<TreeGraphNode, TypedDependency> objectMap = new
        // HashMap<TreeGraphNode, TypedDependency>();

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
            if (edge.getRelation().getShortName().equals("expl")) {
                    explMap.putIfAbsent(edge.getGovernor(), edge);
            }

            // look for rcmod relations
            if (edge.getRelation().getShortName().equals("acl:relcl")) {
                rcmodHeads.add(edge.getGovernor());
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
                                && ! newRel.getShortName().equals("case")
                                && ! newRel.getShortName().equals("advcl")
                                && ! newRel.getShortName().equals("acl")
                                && ! newRel.getShortName().equals("parataxis")
                                && ! newRel.getShortName().equals("list")
                                && ! newRel.getShortName().equals("orphan")
                                && ! newRel.getShortName().equals("conj")) {
                            if (rcmodHeads.contains(gov) && rcmodHeads.contains(dep)) {
                                // to prevent wrong propagation in the case of long dependencies in relative clauses
                                if (! newRel.getShortName().equals("obj") && newRel.getShortName().equals("nsubj")) {
                                    sg.addEdge(newGov, dep, newRel, Double.NEGATIVE_INFINITY, true);
                                }
                            } else {
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
                String tag = dep.getString(CoreAnnotations.CoarseTagAnnotation.class);
                if (subjectMap.containsKey(gov)
                        && (tag.equals("VERB") || tag.equals("ADJ"))
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
                    sg.addEdge(dep, tdsubj.getDependent(), relation, Double.NEGATIVE_INFINITY, true);
                }
            }
        }

    }

}
