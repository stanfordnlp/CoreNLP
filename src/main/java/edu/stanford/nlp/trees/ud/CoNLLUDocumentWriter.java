package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;

import java.util.HashMap;

/**
 * @author Sebastian Schuster
 */
public class CoNLLUDocumentWriter {


    private static final String LRB_PATTERN = "(?i)-LRB-";
    private static final String RRB_PATTERN = "(?i)-RRB-";


    public String printSemanticGraph(SemanticGraph basicSg) {
        return printSemanticGraph(basicSg, null, true);
    }

    public String printSemanticGraph(SemanticGraph basicSg, SemanticGraph enhancedSg) {
        return printSemanticGraph(basicSg, enhancedSg, true);
    }

    public String printSemanticGraph(SemanticGraph basicSg, SemanticGraph enhancedSg, boolean unescapeParenthesis) {



        StringBuilder sb = new StringBuilder();

        /* Print comments. */
        for (String comment : basicSg.getComments()) {
            sb.append(comment).append(System.lineSeparator());
        }

        SemanticGraph tokenSg = enhancedSg != null ? enhancedSg : basicSg;

        for (IndexedWord token : tokenSg.vertexListSorted()) {
            /* Check for multiword tokens. */
            if (token.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class)) {
                IntPair tokenSpan = token.get(CoreAnnotations.CoNLLUTokenSpanAnnotation.class);
                if (tokenSpan.getSource() == token.index()) {
                    String range = String.format("%d-%d", tokenSpan.getSource(), tokenSpan.getTarget());
                    sb.append(String.format("%s\t%s\t_\t_\t_\t_\t_\t_\t_\t_%n", range, token.originalText()));
                }
            }

            /* Try to find main governor and additional dependencies. */
            IndexedWord gov = basicSg.containsVertex(token) ? basicSg.getParent(token) : null;
            String govIdx = gov != null ? gov.toCopyIndex() : null;
            GrammaticalRelation reln = gov != null ? basicSg.getEdge(gov, token).getRelation() : null;

            HashMap<String, String> enhancedDependencies = new HashMap<>();
            if (enhancedSg != null) {

                for (IndexedWord parent : enhancedSg.getParents(token)) {
                    SemanticGraphEdge edge = enhancedSg.getEdge(parent, token);
                    String relationString = edge.getRelation().toString();
                    // for Joakim
                    //if (edge.getWeight() == 1.0) {
                    //    relationString = relationString + ":ENH_CONTROL";
                    //} else if (edge.getWeight() == 3.0) {
                    //    relationString = relationString + ":ENH_RELCL";
                    //} else if (edge.getWeight() == 4.0) {
                    //    relationString = relationString + ":ENH_GAPPING";
                    //} else if (edge.getWeight() == 5.0) {
                    //    relationString = relationString + ":ENH_CONJ_PROP";
                    //}
                    enhancedDependencies.put(parent.toCopyIndex(), relationString);
                }

            } else {

                // add enhanced ones stored with token
                HashMap<String, String> secondaryDeps = token.get(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class);
                if (secondaryDeps != null) {
                    enhancedDependencies.putAll(token.get(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class));
                    //add basic dependency
                    if (gov != null) {
                        enhancedDependencies.put(govIdx, reln.toString());
                    }
                }
            }


            String additionalDepsString =  CoNLLUUtils.toExtraDepsString(enhancedDependencies);
            String word = token.word();
            String featuresString = CoNLLUUtils.toFeatureString(token.get(CoreAnnotations.CoNLLUFeats.class));
            String pos = token.getString(CoreAnnotations.PartOfSpeechAnnotation.class, "_");
            String upos = token.getString(CoreAnnotations.CoarseTagAnnotation.class, "_");
            String misc = token.getString(CoreAnnotations.CoNLLUMisc.class, "_");
            String lemma = token.getString(CoreAnnotations.LemmaAnnotation.class, "_");
            String relnName = reln == null ? "_" : reln.toString();

            /* Root. */
            if (govIdx == null && basicSg.getRoots().contains(token)) {
                govIdx = "0";
                relnName = GrammaticalRelation.ROOT.toString();
            } else if (govIdx == null) {
                govIdx = "_";
                relnName = "_";
            }

            if (enhancedSg != null && enhancedSg.getRoots().contains(token)) {
                if (enhancedDependencies.isEmpty()) {
                    additionalDepsString = "0:root";
                } else {
                    additionalDepsString = "0:root|" + additionalDepsString;
                }
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
        sb.append(System.lineSeparator());

        return sb.toString();
    }

  /**
   * Outputs a partial CONLL-U file with token information (form, lemma, POS)
   * but without any dependency information.
   *
   * @param sentence
   * @return
   */

  public String printPOSAnnotations(CoreMap sentence) {
      StringBuilder sb = new StringBuilder();

      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {

          String upos = token.getString(CoreAnnotations.CoarseTagAnnotation.class, "_");
          String lemma = token.getString(CoreAnnotations.LemmaAnnotation.class, "_");
          String pos = token.getString(CoreAnnotations.PartOfSpeechAnnotation.class, "_");
          String featuresString = CoNLLUUtils.toFeatureString(token.get(CoreAnnotations.CoNLLUFeats.class));
          String misc = token.getString(CoreAnnotations.CoNLLUMisc.class, "_");
          sb.append(String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n", token.index(), token.word(),
              lemma, upos , pos, featuresString, "_", "_", "_", misc));
      }
      sb.append(System.lineSeparator());

      return sb.toString();

    }

}
