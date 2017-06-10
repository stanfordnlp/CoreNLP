package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * @author Sebastian Schuster
 */
public class CoNLLUDocumentWriter {


    private static final String LRB_PATTERN = "(?i)-LRB-";
    private static final String RRB_PATTERN = "(?i)-RRB-";


    public String printSemanticGraph(SemanticGraph sg) {
        return printSemanticGraph(sg, true);
    }

    public String printSemanticGraph(SemanticGraph sg, boolean unescapeParenthesis) {


        boolean isTree = SemanticGraphUtils.isTree(sg);

        StringBuilder sb = new StringBuilder();

        /* Print comments. */
        for (String comment : sg.getComments()) {
            sb.append(comment).append("\n");
        }

        for (IndexedWord token : sg.vertexListSorted()) {
            /* Check for multiword tokens. */
            if (token.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class)) {
                IntPair tokenSpan = token.get(CoreAnnotations.CoNLLUTokenSpanAnnotation.class);
                if (tokenSpan.getSource() == token.index()) {
                    String range = String.format("%d-%d", tokenSpan.getSource(), tokenSpan.getTarget());
                    sb.append(String.format("%s\t%s\t_\t_\t_\t_\t_\t_\t_\t_%n", range, token.originalText()));
                }
            }

            /* Try to find main governor and additional dependencies. */
            String govIdx = null;
            GrammaticalRelation reln = null;
            HashMap<String, String> enhancedDependencies = new HashMap<>();
            for (IndexedWord parent : sg.getParents(token)) {
                SemanticGraphEdge edge = sg.getEdge(parent, token);
                if ( govIdx == null && ! edge.isExtra()) {
                    govIdx = parent.toCopyIndex();
                    reln = edge.getRelation();
                }
                enhancedDependencies.put(parent.toCopyIndex(), edge.getRelation().toString());
            }



            String additionalDepsString = isTree ? "_" : CoNLLUUtils.toExtraDepsString(enhancedDependencies);
            String word = token.word();
            String featuresString = CoNLLUUtils.toFeatureString(token.get(CoreAnnotations.CoNLLUFeats.class));
            String pos = token.getString(CoreAnnotations.PartOfSpeechAnnotation.class, "_");
            String upos = token.getString(CoreAnnotations.CoarseTagAnnotation.class, "_");
            String misc = token.getString(CoreAnnotations.CoNLLUMisc.class, "_");
            String lemma = token.getString(CoreAnnotations.LemmaAnnotation.class, "_");
            String relnName = reln == null ? "_" : reln.toString();

            /* Root. */
            if (govIdx == null && sg.getRoots().contains(token)) {
                govIdx = "0";
                relnName = GrammaticalRelation.ROOT.toString();
                additionalDepsString = isTree ? "_" : "0:" + relnName;
            }

            if (unescapeParenthesis) {
                word = word.replaceAll(LRB_PATTERN, "(");
                word = word.replaceAll(RRB_PATTERN, ")");
                lemma = lemma.replaceAll(LRB_PATTERN, "(");
                lemma = lemma.replaceAll(RRB_PATTERN, ")");
            }

            sb.append(String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n", token.toCopyIndex(), word,
                    lemma, upos, pos, featuresString, govIdx, relnName, additionalDepsString, misc));
        }
        sb.append("\n");

        return sb.toString();
    }

}
