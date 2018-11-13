package edu.stanford.nlp.coref;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * Class for printing out coreference output.
 * @author Heeyoung Lee
 * @author Kevin Clark
 */
public class CorefPrinter {
  public static String printConllOutput(Document document, boolean gold) {
    return printConllOutput(document, gold, false);
  }

  public static String printConllOutput(Document document, boolean gold, boolean filterSingletons)
  {
    return printConllOutput(document, gold, filterSingletons, document.corefClusters);
  }

  public static String printConllOutput(Document document, boolean gold, boolean filterSingletons,
                                        Map<Integer, CorefCluster> corefClusters) {
    List<List<Mention>> orderedMentions = gold ? document.goldMentions : document.predictedMentions;
    if (filterSingletons) {
      orderedMentions = orderedMentions.stream().map(
          ml -> ml.stream().filter(m -> corefClusters.get(m.corefClusterID) != null &&
            corefClusters.get(m.corefClusterID).size() > 1)
            .collect(Collectors.toList()))
          .collect(Collectors.toList());
    }
    return CorefPrinter.printConllOutput(document, orderedMentions, gold);
  }

  public static String printConllOutput(Document document,
      List<List<Mention>> orderedMentions, boolean gold) {
    Annotation anno = document.annotation;
    List<List<String[]>> conllDocSentences = document.getSentenceWordLists();
    String docID = anno.get(CoreAnnotations.DocIDAnnotation.class);
    StringBuilder sb = new StringBuilder();
    sb.append("#begin document ").append(docID).append("\n");
    List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
    for(int sentNum = 0 ; sentNum < sentences.size() ; sentNum++){
      List<CoreLabel> sentence = sentences.get(sentNum).get(CoreAnnotations.TokensAnnotation.class);
      List<String[]> conllSentence = (conllDocSentences != null)? conllDocSentences.get(sentNum) : null;
      Map<Integer,Set<Mention>> mentionBeginOnly = Generics.newHashMap();
      Map<Integer,Set<Mention>> mentionEndOnly = Generics.newHashMap();
      Map<Integer,Set<Mention>> mentionBeginEnd = Generics.newHashMap();

      for(int i=0 ; i<sentence.size(); i++){
        mentionBeginOnly.put(i, new LinkedHashSet<>());
        mentionEndOnly.put(i, new LinkedHashSet<>());
        mentionBeginEnd.put(i, new LinkedHashSet<>());
      }

      for(Mention m : orderedMentions.get(sentNum)) {
        if(m.startIndex==m.endIndex-1) {
          mentionBeginEnd.get(m.startIndex).add(m);
        } else {
          mentionBeginOnly.get(m.startIndex).add(m);
          mentionEndOnly.get(m.endIndex-1).add(m);
        }
      }

      for(int i=0 ; i<sentence.size(); i++){
        StringBuilder sb2 = new StringBuilder();
        for(Mention m : mentionBeginOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append("(").append(corefClusterId);
        }
        for(Mention m : mentionBeginEnd.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append("(").append(corefClusterId).append(")");
        }
        for(Mention m : mentionEndOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append(corefClusterId).append(")");
        }
        if(sb2.length() == 0) sb2.append("-");

        if (conllSentence != null) {
          String[] columns = conllSentence.get(i);
          for (int j = 0; j < columns.length - 1; j++) {
            String column = columns[j];
            sb.append(column).append("\t");
          }
        }
        sb.append(sb2).append("\n");
      }
      sb.append("\n");
    }

    sb.append("#end document").append("\n");

    return sb.toString();
  }
}
