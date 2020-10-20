package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.util.Pair;
import org.ejml.simple.SimpleMatrix;


import java.util.*;
import java.util.regex.Pattern;

public class UniversalGappingEnhancer {

    private static Embedding embeddings;

    //private final static Embedding embeddings = null;

    private static double GAP_PENALTY = -10;

    private static double POS_MISMATCH_PENALTY = -2;

    //for Joakim
    //private static double EDGE_WEIGHT = 4.0;
    private static double EDGE_WEIGHT = Double.NEGATIVE_INFINITY;


    private static class ArgumentSequence {

        IndexedWord head;
        List<IndexedWord> sequence;


        private ArgumentSequence(IndexedWord gov, List<IndexedWord> seq) {
            head = gov;
            sequence = seq;
        }

        @Override
        public String toString() {
            return sequence.toString();
        }

        private SimpleMatrix getAverageEmbeddings() {
            double[][] vec = new double[embeddings.getEmbeddingSize()][1];
            SimpleMatrix totalVector = new SimpleMatrix(vec);
            for (IndexedWord w : sequence) {
                SimpleMatrix vector = embeddings.get(w.word().toLowerCase());

                if (vector != null) {
                    totalVector = totalVector.plus(vector);
                }
            }
            return totalVector.divide(this.sequence.size());
        }
    }


    private static final HashMap<String, String> coarserUPOSMap = new HashMap() {{
        put("PROPN", "NOUN");
        put("PRON", "NOUN");
        put("NUM", "NOUN");
        put("DET", "NOUN");
    }};

    private static final String coarsenUPOSTag(String uPOS) {
        if (coarserUPOSMap.containsKey(uPOS)) {
            return coarserUPOSMap.get(uPOS);
        }
        return uPOS;
    }

    private static final Pair<Double, List<Integer>> align(List<ArgumentSequence> fullArguments, List<ArgumentSequence> gappedArguments) {
        int n = fullArguments.size();
        int m = gappedArguments.size();

        double scores[][] = new double[n + 1][m + 1];
        int backtracing[][][] = new int[n + 1][m + 1][2];

        for (int i = 0; i < n + 1; i++) {
            scores[i][0] = i * GAP_PENALTY;
            backtracing[i][0][0] = i - 1;
            backtracing[i][0][1] = 0;
        }

        for (int j = 0; j < m + 1; j++) {
            scores[0][j] = j * GAP_PENALTY;
            backtracing[0][j][0] = 0;
            backtracing[0][j][1] = j - 1;
        }

        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < m + 1; j++) {

                double distance = 0.0;
                if (embeddings != null) {
                    SimpleMatrix fullEmbedding = fullArguments.get(i - 1).getAverageEmbeddings();
                    SimpleMatrix gappedEmbedding = gappedArguments.get(j - 1).getAverageEmbeddings();

                    distance = fullEmbedding.minus(gappedEmbedding).normF();
                }
                String fullCoarseTag = coarsenUPOSTag(fullArguments.get(i - 1).head.get(CoreAnnotations.CoarseTagAnnotation.class));
                String gappedCoarseTag = coarsenUPOSTag(gappedArguments.get(j - 1).head.get(CoreAnnotations.CoarseTagAnnotation.class));


                double posScore = fullCoarseTag.equals(gappedCoarseTag) ? 0.0 : POS_MISMATCH_PENALTY;


                //System.err.println("" + fullArguments.get(i-1) + " |||| " + gappedArguments.get(j-1) + " |||| " + distance);

                double match = scores[i - 1][j - 1] - distance + posScore;
                double del = scores[i - 1][j] + GAP_PENALTY;
                double ins = scores[i][j - 1] + GAP_PENALTY;
                backtracing[i][j][0] = match >= del && match >= ins ? i - 1 :
                        del > match && del >= ins ? i - 1 : i;
                backtracing[i][j][1] = match >= del && match >= ins ? j - 1 :
                        del > match && del >= ins ? j : j - 1;

                scores[i][j] = Math.max(match, Math.max(del, ins));

            }
        }

        int i = n;
        int j = m;

        //System.err.println("FINAL SCORE: " + scores[i][j]);

        LinkedList<Integer> alignmentA = new LinkedList<>();
        LinkedList<Integer> alignmentB = new LinkedList<>();
        while (i > 0 || j > 0) {


            int new_i = backtracing[i][j][0];
            int new_j = backtracing[i][j][1];

            if (new_i == i - 1 && new_j == j - 1) {
                alignmentA.add(new_i);
                alignmentB.add(new_j);

            } else if (new_i == i - 1 && new_j == j) {
                alignmentA.add(new_i);
                alignmentB.add(-1);
            } else {
                alignmentA.add(-1);
                alignmentB.add(new_j);
            }
            i = new_i;
            j = new_j;
        }

        Collections.reverse(alignmentA);
        Collections.reverse(alignmentB);

        //System.err.println(alignmentA);
        //System.err.println(alignmentB);


        double alignmentScore = scores[n][m];

        List<Integer> alignment = new ArrayList<>(m);
        for (int k = 0; k < alignmentB.size(); k++) {
            if (alignmentB.get(k) > -1) {
                alignment.add(alignmentA.get(k));
            }
        }


        Pair<Double, List<Integer>> result = new Pair(alignmentScore, alignment);
        return result;


    }


    private static final SemgrexPattern ORPHAN_PATTERN = SemgrexPattern.compile("{}=orphangov < {}=conjgov >orphan {}");

    private static final Pair<IndexedWord, IndexedWord> getConjGovOrphanGovPair(SemanticGraph sg) {
        SemgrexMatcher matcher = ORPHAN_PATTERN.matcher(sg);
        int conjGovPosition = Integer.MAX_VALUE;
        IndexedWord firstConjGov = null;
        IndexedWord firstOrphanGov = null;
        while (matcher.find()) {
            IndexedWord conjGov = matcher.getNode("conjgov");
            IndexedWord orphanGov = matcher.getNode("orphangov");

            // Let's go from left to right in filling gaps.
            // Sometimes a gap is needed to fill another gap to its right
            if (firstOrphanGov == null || firstOrphanGov.index() > orphanGov.index()) {
                firstConjGov = conjGov;
                firstOrphanGov = orphanGov;
            }

        }

        if (firstOrphanGov != null) {
            return new Pair<>(firstConjGov, firstOrphanGov);
        }

        return null;
    }

    //Argument relations:
    //obj, iobj, nsubj, nsubj:pass, csubj, csubj:pass, xcomp, ccomp, nmod, nmod:tmod, nmod:npadvmod, obl*, advcl, acl, compound:prt
    private static final Pattern ARGUMENT_PATTERN = Pattern.compile("^(i?obj|(n|c)subj.*|(x|c)comp|nmod(:tmod|:npadvmod)?|obl.*|advcl|acl|compound:prt)$");

    private static final boolean isArgument(SemanticGraph sg, SemanticGraphEdge edge) {
        boolean matches = ARGUMENT_PATTERN.matcher(edge.getRelation().toString()).matches();
        if (matches) {
            for (SemanticGraphEdge edge2 : sg.outgoingEdgeIterable(edge.getDependent())) {
                if (edge2.getRelation().equals(UniversalEnglishGrammaticalRelations.ORPHAN)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    //Clausal argument relations:
    //csubj, csubj:pass, xcomp, ccomp, acl, advcl
    private static final Pattern CLAUSAL_ARGUMENT_PATTERN = Pattern.compile("^(csubj.*|(x|c)comp|advcl|acl)$");

    private static final boolean isClausalArgument(SemanticGraph sg, SemanticGraphEdge edge) {
        boolean matches = CLAUSAL_ARGUMENT_PATTERN.matcher(edge.getRelation().toString()).matches();
        if (matches) {
            for (SemanticGraphEdge edge2 : sg.outgoingEdgeIterable(edge.getDependent())) {
                if (edge2.getRelation().equals(UniversalEnglishGrammaticalRelations.ORPHAN)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static final void getArgumentSubsequences(SemanticGraph sg, IndexedWord currentHead, List<ArgumentSequence> currentSequences) {

        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(currentHead)) {
            if (isArgument(sg, edge)) {
                ArgumentSequence seq = new ArgumentSequence(edge.getDependent(), sg.yield(edge.getDependent()));
                currentSequences.add(seq);
                if (isClausalArgument(sg, edge)) { //TODO: maybe use isArgument here?
                    getArgumentSubsequences(sg, edge.getDependent(), currentSequences);
                }
            }
        }
    }

    private static final List<List<ArgumentSequence>> getFullConjunctArgumentsHelper(SemanticGraph sg, IndexedWord conjGov, IndexedWord orphanGov) {
        List<List<ArgumentSequence>> arguments = new LinkedList<>();
        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(conjGov)) {
            if (isArgument(sg, edge) && edge.getDependent().pseudoPosition() < orphanGov.pseudoPosition()) {
                List<ArgumentSequence> argumentVariants = new LinkedList<>();
                ArgumentSequence seq = new ArgumentSequence(edge.getDependent(), sg.yield(edge.getDependent()));
                argumentVariants.add(seq);
                getArgumentSubsequences(sg, edge.getDependent(), argumentVariants);
                arguments.add(argumentVariants);
            }
        }
        arguments.sort((arg1, arg2) -> arg1.get(0).head.index() - arg2.get(0).head.index());
        return arguments;
    }

    private static final void buildAllArgumentSequences(int argIndex, List<ArgumentSequence> prefix,
                                                        List<List<ArgumentSequence>> argumentVariants,
                                                        List<List<ArgumentSequence>> currentSequences) {
        int nArguments = argumentVariants.size();
        for (ArgumentSequence seq : argumentVariants.get(argIndex)) {
            List<ArgumentSequence> newPrefix = new ArrayList<>(nArguments);
            newPrefix.addAll(prefix);
            newPrefix.add(seq);
            if (nArguments == argIndex + 1) {
                currentSequences.add(newPrefix);
            } else {
                buildAllArgumentSequences(argIndex + 1, newPrefix, argumentVariants, currentSequences);
            }
        }
    }


    private static final List<List<ArgumentSequence>> getFullConjunctArguments(SemanticGraph sg, IndexedWord conjGov, IndexedWord orphanGov) {
        List<List<ArgumentSequence>> argumentVariants = getFullConjunctArgumentsHelper(sg, conjGov, orphanGov);

        //System.err.println(argumentVariants.size());


        int totalArguments = argumentVariants.size() > 0 ? 1 : 0;
        for (List<ArgumentSequence> args : argumentVariants) {
            totalArguments *= args.size();
        }
        List<List<ArgumentSequence>> argumentSequences = new ArrayList<>(totalArguments);
        if (totalArguments > 0) {
            buildAllArgumentSequences(0, new LinkedList<>(), argumentVariants, argumentSequences);
        }
        return argumentSequences;
    }


    private static final Pattern MODIFIER_PATTERN = Pattern.compile("^(amod|advmod|nmod|obl|acl|mark|case|compound|flat)$");


    private static final boolean isModifier(SemanticGraphEdge edge) {
        return MODIFIER_PATTERN.matcher(edge.getRelation().toString()).matches();
    }

    private static final ArgumentSequence getOrphanGovSequence(SemanticGraph sg, IndexedWord orphanGov) {
        List<IndexedWord> seq = new LinkedList<>();
        seq.add(orphanGov);
        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(orphanGov)) {
            if (isModifier(edge)) {
                seq.addAll(sg.yield(edge.getDependent()));
            }
        }

        Collections.sort(seq);
        return new ArgumentSequence(orphanGov, seq);
    }

    private static final List<ArgumentSequence> getGappedConjunctArguments(SemanticGraph sg, IndexedWord orphanGov) {
        List<ArgumentSequence> arguments = new LinkedList<>();
        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(orphanGov)) {
            if (edge.getRelation().equals(UniversalEnglishGrammaticalRelations.ORPHAN)) {
                ArgumentSequence seq = new ArgumentSequence(edge.getDependent(), sg.yield(edge.getDependent()));
                arguments.add(seq);
            }
        }
        arguments.add(getOrphanGovSequence(sg, orphanGov));

        arguments.sort((arg1, arg2) -> arg1.head.compareTo(arg2.head));

        return arguments;
    }

    private static final Pattern CORE_ARGUMENTS_PATTERN = Pattern.compile("^((n|c)subj.*|(x|c)comp|i?obj|expl|compound:prt)$");

    private static final SemgrexPattern CONJ_PATTERN = SemgrexPattern.compile("{}=predicate > ({}=arg1 >conj {}=conjdep) > {}=arg2");

    private static final void doEnhancement(SemanticGraph sg, IndexedWord conjGov, IndexedWord orphanGov,
                                            List<ArgumentSequence> fullConjunctArguments,
                                            List<ArgumentSequence> orphanConjunctArguments,
                                            List<Integer> alignment) {
        HashMap<IndexedWord, IndexedWord> copiedNodes = new HashMap<>();
        IndexedWord conjGovCopy = conjGov.makeSoftCopy();
        conjGovCopy.setPseudoPosition(conjGovCopy.pseudoPosition() + conjGovCopy.copyCount() / 10.0);
        SemanticGraphEdge edge = sg.getEdge(conjGov, orphanGov);
        sg.removeEdge(edge);
        sg.addEdge(conjGov, conjGovCopy, edge.getRelation(), EDGE_WEIGHT, false);
        copiedNodes.put(conjGov, conjGovCopy);
        for (int i = 0; i < orphanConjunctArguments.size(); i++) {
            IndexedWord dep = orphanConjunctArguments.get(i).head;
            if (sg.hasParentWithReln(dep, UniversalEnglishGrammaticalRelations.ORPHAN)) {
                SemanticGraphEdge oldEdge = sg.getEdge(orphanGov, dep);
                sg.removeEdge(oldEdge);
            }
            int alignmentIdx = alignment.get(i);
            if (alignmentIdx < 0) {
                sg.addEdge(conjGovCopy, dep, GrammaticalRelation.DEPENDENT, EDGE_WEIGHT, false);
            } else {
                IndexedWord parallelArgument = fullConjunctArguments.get(alignmentIdx).head;
                List<SemanticGraphEdge> parallelPath = sg.getShortestDirectedPathEdges(conjGov, parallelArgument);
                for (int j = 0; j < parallelPath.size(); j++) {
                    SemanticGraphEdge parallelEdge = parallelPath.get(j);
                    boolean newCopyNode = false;


                    IndexedWord sourceNode = copiedNodes.get(parallelEdge.getGovernor());
                    if (sourceNode == null) {
                        IndexedWord copyNode = parallelEdge.getGovernor().makeSoftCopy();
                        copyNode.setPseudoPosition(copyNode.pseudoPosition() + copyNode.copyCount() / 10.0);
                        copiedNodes.put(parallelEdge.getGovernor(), copyNode);
                        newCopyNode = true;
                        sourceNode = copyNode;
                    }

                    IndexedWord targetNode = j < parallelPath.size() - 1 ? copiedNodes.get(parallelEdge.getDependent()) : dep;

                    if (targetNode == null) {
                        IndexedWord copyNode = parallelEdge.getDependent().makeSoftCopy();
                        copyNode.setPseudoPosition(copyNode.pseudoPosition() + copyNode.copyCount() / 10.0);
                        copiedNodes.put(parallelEdge.getDependent(), copyNode);
                        newCopyNode = true;
                        targetNode = copyNode;
                    }

                    if (targetNode == dep || newCopyNode) {
                        sg.addEdge(sourceNode, targetNode, parallelEdge.getRelation(), EDGE_WEIGHT, false);
                    }
                }
            }
        }

        // Add additional arguments.

        for (IndexedWord copiedNode : copiedNodes.keySet()) {
            for (SemanticGraphEdge originalEdge : sg.outgoingEdgeIterable(copiedNode)) {
                if (CORE_ARGUMENTS_PATTERN.matcher(originalEdge.getRelation().toString()).matches()) {
                    IndexedWord copyNode = copiedNodes.get(copiedNode);
                    if (!sg.hasChildWithReln(copyNode, originalEdge.getRelation())) {
                        sg.addEdge(copyNode, originalEdge.getDependent(), originalEdge.getRelation(), EDGE_WEIGHT, false);
                    }
                }
            }
        }


        // Correct conj dependents if they are clausal
        SemanticGraph sgCopy = sg.makeSoftCopy();

        for (IndexedWord copyNode : copiedNodes.values()) {

            SemgrexMatcher matcher = CONJ_PATTERN.matcher(sgCopy, copyNode);
            while (matcher.find()) {
                IndexedWord pred = matcher.getNode("predicate");
                IndexedWord arg1 = matcher.getNode("arg1");
                IndexedWord conjDep = matcher.getNode("conjdep");
                IndexedWord arg2 = matcher.getNode("arg2");

                if (pred != copyNode || arg1 != orphanGov) {
                    continue;
                }

                if (arg2.pseudoPosition() > arg1.pseudoPosition() && conjDep.pseudoPosition() > arg2.pseudoPosition()) {
                    SemanticGraphEdge conjEdge = sg.getEdge(arg1, conjDep);
                    sg.removeEdge(conjEdge);
                    sg.addEdge(pred, conjDep, UniversalEnglishGrammaticalRelations.CONJUNCT, EDGE_WEIGHT, false);
                }
            }
        }

        //reattach cc
        List<SemanticGraphEdge> orphanOutgoingEdges = sg.outgoingEdgeList(orphanGov);
        for (SemanticGraphEdge edge1 : orphanOutgoingEdges) {
            if (edge1.getRelation().getShortName().equals("cc")) {
                sg.removeEdge(edge1);
                sg.addEdge(conjGovCopy, edge1.getDependent(), edge1.getRelation(), EDGE_WEIGHT, false);
            }
        }

    }

    public static final void addEnhancements(SemanticGraph sg, Embedding embeddingMatrix) {

        embeddings = embeddingMatrix;

        Pair<IndexedWord, IndexedWord> conjGovOrphanGov = null;
        int iterations = 0;
        while ((conjGovOrphanGov = getConjGovOrphanGovPair(sg)) != null && ++iterations < 10) {
            //System.err.println(sg.toString(SemanticGraph.OutputFormat.READABLE));

            IndexedWord conjGov = conjGovOrphanGov.first();
            IndexedWord orphanGov = conjGovOrphanGov.second();
            List<List<ArgumentSequence>> fullConjunctArgumentSequences = getFullConjunctArguments(sg, conjGov, orphanGov);
            List<ArgumentSequence> gappedConjunctArguments = getGappedConjunctArguments(sg, orphanGov);


            List<Integer> bestAlignment = null;
            List<ArgumentSequence> bestArgumentSequence = null;

            Double bestScore = Double.NEGATIVE_INFINITY;
            for (List<ArgumentSequence> fullConjunctArguments : fullConjunctArgumentSequences) {
                Pair<Double, List<Integer>> res = align(fullConjunctArguments, gappedConjunctArguments);
                double score = res.first;
                List<Integer> alignment = res.second();
                if (score > bestScore) {
                    bestScore = score;
                    bestAlignment = alignment;
                    bestArgumentSequence = fullConjunctArguments;
                }
            }


            if (bestArgumentSequence != null) {
                doEnhancement(sg, conjGov, orphanGov, bestArgumentSequence, gappedConjunctArguments, bestAlignment);
            }
        }

        if (iterations == 10) {
            System.err.println("Problem with graph:");
            System.err.println(sg.toString(SemanticGraph.OutputFormat.READABLE));
        }

    }
}
