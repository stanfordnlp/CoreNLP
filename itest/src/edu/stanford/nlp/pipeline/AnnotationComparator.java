package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.CoreMap;

import java.util.List;

/**
 * Tools for comparing annotations.  Generally used for debugging processes that should
 * generate identical Annotations.
 */

public class AnnotationComparator {

    public static void compareTokensLists(CoreMap originalDoc, CoreMap newDoc) {
        List<CoreLabel> originalTokens = originalDoc.get(CoreAnnotations.TokensAnnotation.class);
        List<CoreLabel> newTokens = newDoc.get(CoreAnnotations.TokensAnnotation.class);
        if (originalTokens.size() != newTokens.size()) {
            System.err.println("---");
            System.err.println("token size mismatch!");
            System.err.println("original token size: "+originalTokens.size());
            System.err.println("new token size; "+newTokens.size());
        } else {
            for (int i = 0 ; i < originalTokens.size() ; i++) {
                if (!originalTokens.get(i).equals(newTokens.get(i))) {
                    System.err.println("---");
                    System.err.println("token mismatch detected!");
                    System.err.println("token number: "+i);
                    System.err.println(originalTokens.get(i));
                }
            }
        }
    }

    public static void compareEntityMentionsLists(Annotation originalDoc, Annotation newDoc) {
        List<CoreMap> originalMentions = originalDoc.get(CoreAnnotations.MentionsAnnotation.class);
        List<CoreMap> newMentions = newDoc.get(CoreAnnotations.MentionsAnnotation.class);
        if (originalMentions.size() != newMentions.size()) {
            System.err.println("---");
            System.err.println("entity mentions size mismatch!");
            System.err.println("original entity mention size: "+originalMentions.size());
            System.err.println("new entity mention size: "+newMentions.size());
        } else {
            for (int i = 0 ; i < originalMentions.size() ; i++) {
                if (!originalMentions.get(i).equals(newMentions.get(i))) {
                    System.err.println("---");
                    System.err.println("entity mention mismatch detected!");
                    System.err.println("entity mention number: "+i);
                    System.err.println(originalMentions.get(i));
                }
            }
        }
    }

    public static void findCoreMapDifference(CoreMap originalCoreMap, CoreMap readCoreMap) {
        if (!originalCoreMap.get(CoreAnnotations.TokensAnnotation.class).equals(
                readCoreMap.get(CoreAnnotations.TokensAnnotation.class)))
            System.err.println("tokens annotation difference detected!");
        if (!originalCoreMap.get(CoreAnnotations.TextAnnotation.class).equals(
                readCoreMap.get(CoreAnnotations.TextAnnotation.class)))
            System.err.println("text annotation difference detected!");
        if (!originalCoreMap.get(CorefCoreAnnotations.CorefChainAnnotation.class).equals(
                readCoreMap.get(CorefCoreAnnotations.CorefChainAnnotation.class)))
            System.err.println("coref chain annotation difference detected!");
        if (!originalCoreMap.get(CorefCoreAnnotations.CorefMentionsAnnotation.class).equals(
                readCoreMap.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)))
            System.err.println("coref mentions difference detected!");
        if (!originalCoreMap.get(CoreAnnotations.EntityMentionToCorefMentionMappingAnnotation.class).equals(
                readCoreMap.get(CoreAnnotations.EntityMentionToCorefMentionMappingAnnotation.class)))
            System.err.println("entity mention to coref mapping difference detected!");
        if (!originalCoreMap.get(CoreAnnotations.CorefMentionToEntityMentionMappingAnnotation.class).equals(
                readCoreMap.get(CoreAnnotations.CorefMentionToEntityMentionMappingAnnotation.class)))
            System.err.println("coref mention to entity mapping difference detected!");
        if (!originalCoreMap.get(CoreAnnotations.MentionsAnnotation.class).equals(
                readCoreMap.get(CoreAnnotations.MentionsAnnotation.class)))
            System.err.println("mentions annotation difference detected!");
        if (!originalCoreMap.get(CoreAnnotations.SentencesAnnotation.class).equals(
                readCoreMap.get(CoreAnnotations.SentencesAnnotation.class)))
            System.err.println("sentences annotation difference detected!");
    }

}
