package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
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
            int govIdx = -1;
            GrammaticalRelation reln = null;
            HashMap<Integer, String> additionalDeps = new HashMap<>();
            for (IndexedWord parent : sg.getParents(token)) {
                SemanticGraphEdge edge = sg.getEdge(parent, token);
                if ( govIdx == -1 && ! edge.isExtra()) {
                    govIdx = parent.index();
                    reln = edge.getRelation();
                } else {
                    additionalDeps.put(parent.index(), edge.getRelation().toString());
                }
            }


            String additionalDepsString = CoNLLUUtils.toExtraDepsString(additionalDeps);
            String word = token.word();
            String featuresString = CoNLLUUtils.toFeatureString(token.get(CoreAnnotations.CoNLLUFeats.class));
            String pos = token.getString(CoreAnnotations.PartOfSpeechAnnotation.class, "_");
            String upos = token.getString(CoreAnnotations.CoarseTagAnnotation.class, "_");
            String misc = token.getString(CoreAnnotations.CoNLLUMisc.class, "_");
            String lemma = token.getString(CoreAnnotations.LemmaAnnotation.class, "_");
            String relnName = reln == null ? "_" : reln.toString();

            /* Root. */
            if (govIdx == -1 && sg.getRoots().contains(token)) {
                govIdx = 0;
                relnName = GrammaticalRelation.ROOT.toString();
            }

            if (unescapeParenthesis) {
                word = word.replaceAll(LRB_PATTERN, "(");
                word = word.replaceAll(RRB_PATTERN, ")");
                lemma = lemma.replaceAll(LRB_PATTERN, "(");
                lemma = lemma.replaceAll(RRB_PATTERN, ")");
            }

            sb.append(String.format("%d\t%s\t%s\t%s\t%s\t%s\t%d\t%s\t%s\t%s%n", token.index(), word,
                    lemma, upos, pos, featuresString, govIdx, relnName, additionalDepsString, misc));
        }
        sb.append("\n");

        return sb.toString();
    }

}
