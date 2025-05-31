package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.AbstractCoreLabel;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;

import java.util.Collection;
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
    return printSemanticGraph(basicSg, enhancedSg, true, basicSg.getComments());
  }

  // TODO: put in the same place as CoNLLUReader::unescapeSpacesAfter
  public static String escapeSpaces(String after) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < after.length(); ++i) {
      char next = after.charAt(i);
      if (next == ' ') {
        result.append("\\s");
      } else if (next == '\t') {
        result.append("\\t");
      } else if (next == '\r') {
        result.append("\\r");
      } else if (next == '\n') {
        result.append("\\n");
      } else if (next == '|') {
        result.append("\\p");
      } else if (next == '\\') {
        result.append("\\\\");
      } else if (next == ' ') {
        result.append("\\u00A0");
      } else {
        result.append(next);
      }
    }
    return result.toString();
  }

  public String printSemanticGraph(SemanticGraph basicSg, SemanticGraph enhancedSg, boolean unescapeParenthesis, Collection<String> comments) {
    StringBuilder sb = new StringBuilder();

    // Print comments
    for (String comment : comments) {
      sb.append(comment).append(System.lineSeparator());
    }

    SemanticGraph tokenSg = enhancedSg != null ? enhancedSg : basicSg;

    for (IndexedWord token : tokenSg.vertexListSorted()) {
      // Check for multiword tokens
      if (token.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class)) {
        printSpan(sb, token);
      } else if (token.containsKey(CoreAnnotations.IsFirstWordOfMWTAnnotation.class) && token.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class)) {
        printMWT(sb, tokenSg, token);
      }

      // Try to find main governor and additional dependencies
      IndexedWord gov = basicSg.containsVertex(token) ? basicSg.getParent(token) : null;
      String govIdx = gov != null ? gov.toCopyOrEmptyIndex() : null;
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
          enhancedDependencies.put(parent.toCopyOrEmptyIndex(), relationString);
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
      String featuresString = CoNLLUFeatures.toFeatureString(token.get(CoreAnnotations.CoNLLUFeats.class));
      String pos = token.getString(CoreAnnotations.PartOfSpeechAnnotation.class, "_");
      String upos = token.getString(CoreAnnotations.CoarseTagAnnotation.class, "_");
      String misc = token.getString(CoreAnnotations.CoNLLUMisc.class, "_");
      String lemma = token.getString(CoreAnnotations.LemmaAnnotation.class, "_");
      String relnName = reln == null ? "_" : reln.toString();

      // don't use after() directly; it returns a default of ""
      // TODO: also print SpacesBefore on the first token
      Boolean isMWT = token.get(CoreAnnotations.IsMultiWordTokenAnnotation.class);
      if ((isMWT == null || !isMWT) && token.get(CoreAnnotations.AfterAnnotation.class) != null) {
        String after = token.after();
        if (!after.equals(" ")) {
          if (after.equals("")) {
            after = "SpaceAfter=No";
          } else {
            after = "SpacesAfter=" + escapeSpaces(after);
          }
          if (misc.equals("_")) {
            misc = after;
          } else {
            misc = misc + "|" + after;
          }
        }
      }

      // Root
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

      sb.append(String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n", token.toCopyOrEmptyIndex(), word,
                              lemma, upos, pos, featuresString, govIdx, relnName, additionalDepsString, misc));
    }
    sb.append(System.lineSeparator());

    return sb.toString();
  }

  /**
   * Outputs just one token span (MWT)
   */
  public static void printSpan(StringBuilder sb, AbstractCoreLabel token) {
    IntPair tokenSpan = token.get(CoreAnnotations.CoNLLUTokenSpanAnnotation.class);
    if (tokenSpan.getSource() == token.index()) {
      String range = String.format("%d-%d", tokenSpan.getSource(), tokenSpan.getTarget());
      sb.append(String.format("%s\t%s\t_\t_\t_\t_\t_\t_\t_\t_%n", range, token.originalText()));
    }
  }

  /**
   * Is the word part of an MWT, but not the start?
   */
  public static boolean isMWTbutNotStart(IndexedWord nextVertex) {
    if (nextVertex.containsKey(CoreAnnotations.IsFirstWordOfMWTAnnotation.class) &&
        nextVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class)) {
      return false;
    }
    if (!nextVertex.containsKey(CoreAnnotations.IsMultiWordTokenAnnotation.class) ||
        !nextVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class)) {
      return false;
    }
    return true;
  }

  public static void printMWT(StringBuilder sb, SemanticGraph graph, IndexedWord token) {
    int startIndex = token.index();
    int endIndex = startIndex;
    // advance endIndex until we reach the end of the sentence, the start of the next MWT,
    // or a word which isn't part of any MWT
    IndexedWord nextVertex;
    while ((nextVertex = graph.getNodeByIndexSafe(endIndex+1)) != null) {
      if (!isMWTbutNotStart(nextVertex)) {
        break;
      }
      ++endIndex;
    }
    if (startIndex == endIndex) {
      return;
    }
    String range = String.format("%d-%d", startIndex, endIndex);

    IndexedWord endVertex = graph.getNodeByIndexSafe(endIndex);

    String misc = "_";
    if (token.get(CoreAnnotations.MWTTokenMiscAnnotation.class) != null) {
      misc = token.get(CoreAnnotations.MWTTokenMiscAnnotation.class);
    }

    if (endVertex.get(CoreAnnotations.AfterAnnotation.class) != null) {
      String after = endVertex.after();
      if (!after.equals(" ")) {
        if (after.equals("")) {
          after = "SpaceAfter=No";
        } else {
          after = "SpacesAfter=" + escapeSpaces(after);
        }
        if (misc.equals("_")) {
          misc = after;
        } else {
          misc = misc + "|" + after;
        }
      }
    }

    sb.append(String.format("%s\t%s\t_\t_\t_\t_\t_\t_\t_\t%s%n", range, token.get(CoreAnnotations.MWTTokenTextAnnotation.class), misc));
  }

  /**
   * Outputs a partial CONLL-U file with token information (form, lemma, POS)
   * but without any dependency information.
   *
   * @param sentence
   * @return
   */

  public String printPOSAnnotations(CoreMap sentence, boolean fakeDeps) {
    StringBuilder sb = new StringBuilder();

    int index = 0;
    for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
      // Check for multiword tokens
      if (token.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class)) {
        printSpan(sb, token);
      }

      String upos = token.getString(CoreAnnotations.CoarseTagAnnotation.class, "_");
      String lemma = token.getString(CoreAnnotations.LemmaAnnotation.class, "_");
      String pos = token.getString(CoreAnnotations.PartOfSpeechAnnotation.class, "_");
      String featuresString = CoNLLUFeatures.toFeatureString(token.get(CoreAnnotations.CoNLLUFeats.class));
      String misc = token.getString(CoreAnnotations.CoNLLUMisc.class, "_");
      final String head;
      final String rel;
      final String headrel;
      if (fakeDeps) {
        // deps count from 1, with 0 as the root.
        // we will have the first word go to fake root
        head = Integer.toString(index);
        rel = (index == 0) ? "root" : "dep";
        headrel = head + ":" + rel;
      } else {
        head = "_";
        rel = "_";
        headrel = "_";
      }
      index++;
      sb.append(String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n", token.index(), token.word(),
                              lemma, upos , pos, featuresString, head, rel, headrel, misc));
    }
    sb.append(System.lineSeparator());

    return sb.toString();
  }
}
