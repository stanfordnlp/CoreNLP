package edu.stanford.nlp.coref;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.data.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Useful utilities for coreference resolution.
 * @author Kevin Clark
 */
public class CorefUtils {
  public static List<Mention> getSortedMentions(Document document) {
    List<Mention> mentions = new ArrayList<>(document.predictedMentionsByID.values());
    Collections.sort(mentions, (m1, m2) -> m1.appearEarlierThan(m2) ? -1 : 1);
    return mentions;
  }

  public static List<Pair<Integer, Integer>> getMentionPairs(Document document) {
     List<Pair<Integer, Integer>> pairs = new ArrayList<>();
     List<Mention> mentions = getSortedMentions(document);
     for (int i = 0; i < mentions.size(); i++) {
       for (int j = 0; j < i; j++) {
         pairs.add(new Pair<>(mentions.get(j).mentionID, mentions.get(i).mentionID));
       }
     }
     return pairs;
   }

   public static Map<Pair<Integer, Integer>, Boolean> getUnlabeledMentionPairs(Document document) {
     return CorefUtils.getMentionPairs(document).stream()
         .collect(Collectors.toMap(p -> p, p -> false));
   }

   public static Map<Pair<Integer, Integer>, Boolean> getLabeledMentionPairs(Document document) {
     Map<Pair<Integer, Integer>, Boolean> mentionPairs = getUnlabeledMentionPairs(document);
     for (CorefCluster c : document.goldCorefClusters.values()) {
       List<Mention> clusterMentions = new ArrayList<>(c.getCorefMentions());
       for (Mention clusterMention : clusterMentions) {
         for (Mention clusterMention2 : clusterMentions) {
           Pair<Integer, Integer> mentionPair = new Pair<>(
               clusterMention.mentionID, clusterMention2.mentionID);
           if (mentionPairs.containsKey(mentionPair)) {
             mentionPairs.put(mentionPair, true);
           }
         }
       }
     }
     return mentionPairs;
   }

  public static void mergeCoreferenceClusters(Pair<Integer, Integer> mentionPair,
      Document document) {
    Mention m1 = document.predictedMentionsByID.get(mentionPair.first);
    Mention m2 = document.predictedMentionsByID.get(mentionPair.second);
    if (m1.corefClusterID == m2.corefClusterID) {
      return;
    }

    int removeId = m1.corefClusterID;
    CorefCluster c1 = document.corefClusters.get(m1.corefClusterID);
    CorefCluster c2 = document.corefClusters.get(m2.corefClusterID);
    CorefCluster.mergeClusters(c2, c1);
    document.corefClusters.remove(removeId);
  }

  public static void removeSingletonClusters(Document document) {
    for (CorefCluster c : new ArrayList<>(document.corefClusters.values())) {
      if (c.getCorefMentions().size() == 1) {
        document.corefClusters.remove(c.clusterID);
      }
    }
  }

  public static void mergePronounsBasedOnSpeaker(Document document, List<Mention> mentions) {
    if (document.numberOfSpeakers() == 2) {
      // Let's hack something together for 'I' and 'you'
      Map<String, Set<Mention>> groupedMentions = new HashMap<>();
      Set<String> speakers = document.speakerInfoMap.keySet();
      for (String s : speakers) {
        groupedMentions.put(s, new HashSet<>());
      }
      for (Mention m : mentions) {
        String speaker = m.headWord.get(CoreAnnotations.SpeakerAnnotation.class);
        Dictionaries.Person p = m.person;
        if (Dictionaries.Person.I == p) {
          groupedMentions.get(speaker).add(m);
        } else if (Dictionaries.Person.YOU == p) {
          String otherSpeaker = null;
          for (String s : speakers) {
            if (!s.equals(speaker)) {
              otherSpeaker = s;
              break;
            }
          }
          if (otherSpeaker != null) {
            groupedMentions.get(otherSpeaker).add(m);
          }
        }
      }
      for (Set<Mention> group : groupedMentions.values()) {
        if (group.size() > 1) {
          List<Mention> ms = CollectionUtils.toList(group);
          for (Mention m : ms) {
            CorefUtils.mergeCoreferenceClusters(Pair.makePair(m.mentionID, ms.get(0).mentionID), document);
          }
        }
      }
    }
  }

  public static void checkForInterrupt() {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
  }

  public static Map<Integer, List<Integer>> heuristicFilter(List<Mention> sortedMentions,
      int maxMentionDistance, int maxMentionDistanceWithStringMatch) {
    Map<String, List<Mention>> wordToMentions = new HashMap<>();
    for (int i = 0; i < sortedMentions.size(); i++) {
      Mention m = sortedMentions.get(i);
      for (String word : getContentWords(m)) {
        wordToMentions.putIfAbsent(word, new ArrayList<>());
        wordToMentions.get(word).add(m);
      }
    }

    Map<Integer, List<Integer>> mentionToCandidateAntecedents = new HashMap<>();
    for (int i = 0; i < sortedMentions.size(); i++) {
      Mention m = sortedMentions.get(i);
      List<Integer> candidateAntecedents = new ArrayList<>();
      for (int j = Math.max(0, i - maxMentionDistance); j < i; j++) {
        candidateAntecedents.add(sortedMentions.get(j).mentionID);
      }
      for (String word : getContentWords(m)) {
        List<Mention> withStringMatch = wordToMentions.get(word);
        if (withStringMatch != null) {
          for (Mention match : withStringMatch) {
            if (match.mentionNum < m.mentionNum
                && match.mentionNum >= m.mentionNum - maxMentionDistanceWithStringMatch) {
              if (!candidateAntecedents.contains(match.mentionID)) {
                candidateAntecedents.add(match.mentionID);
              }
            }
          }
        }
      }
      if (!candidateAntecedents.isEmpty()) {
        mentionToCandidateAntecedents.put(m.mentionID, candidateAntecedents);
      }
    }
    return mentionToCandidateAntecedents;
  }

  private static List<String> getContentWords(Mention m) {
    List<String> words = new ArrayList<>();
    for (int i = m.startIndex; i < m.endIndex; i++) {
      CoreLabel cl = m.sentenceWords.get(i);
      String POS = cl.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if (POS.equals("NN") || POS.equals("NNS") || POS.equals("NNP") || POS.equals("NNPS")) {
        words.add(cl.word().toLowerCase());
      }
    }
    return words;
  }

  public static void printHumanReadableCoref(Document document) {
    for (CorefCluster c : document.corefClusters.values()) {
      for (Mention corefMention : c.corefMentions) {
        Redwood.log(document.docInfo.get("DOC_ID") + "\t" + c.clusterID + "\t" +
            corefMention.originalSpan.get(0).beginPosition() + "\t" + corefMention.toString()+"\n");
      }
    }
  }

  static Set<String> abstractPronouns = CollectionUtils.asSet("that", "this", "it", "here", "there", "these", "those", "its");
    public static Set<Triple<Integer, Integer, Integer>> getMatchingSpans(Annotation annotation) {
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        Set<Triple<Integer, Integer, Integer>> set = new HashSet<>();
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            int iToken = 0;
            for (CoreLabel token : tokens) {
                if (abstractPronouns.contains(token.word().toLowerCase()) && "customer".equals(token.get(CoreAnnotations.SpeakerAnnotation.class))) {
                    set.add(Triple.makeTriple(sentence.get(CoreAnnotations.SentenceIndexAnnotation.class), iToken, iToken+1));
                }
                iToken++;
            }
        }
        return set;
    }


  public static Set<Triple<Integer, Integer, Integer>> getMatchingMentionsSpans(
          Annotation annotation, Collection<CorefChain> chains,
          Predicate<Pair<CorefChain.CorefMention, List<CoreLabel>>> matcher, boolean includeAllMentionsInChain) {
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      Set<Triple<Integer, Integer, Integer>> set = new HashSet<>();
      for (CorefChain chain : chains) {
          List<CorefChain.CorefMention> mentions = chain.getMentionsInTextualOrder();
          boolean chainMatched = false;
          for (CorefChain.CorefMention mention : mentions) {
              List<CoreLabel> tokens = sentences.get(mention.sentNum-1).get(CoreAnnotations.TokensAnnotation.class).subList(mention.startIndex-1, mention.endIndex-1);
              if (matcher.test(Pair.makePair(mention, tokens))) {
                  chainMatched = true;
                  if (includeAllMentionsInChain) {
                      break;
                  } else {
                      set.add(Triple.makeTriple(mention.sentNum - 1, mention.startIndex - 1, mention.endIndex - 1));
                  }
              }
          }
          if (chainMatched && includeAllMentionsInChain) {
              for (CorefChain.CorefMention mention : mentions) {
                  set.add(Triple.makeTriple(mention.sentNum - 1, mention.startIndex - 1, mention.endIndex - 1));
              }
          }
      }
      return set;
  }
  public static Predicate<Pair<CorefChain.CorefMention, List<CoreLabel>>> filterCustomerAbstractPronouns = pair -> {
      CoreLabel token = pair.second.get(0);
      CorefChain.CorefMention mention = pair.first;
      return abstractPronouns.contains(mention.mentionSpan.toLowerCase()) && "customer".equals(token.get(CoreAnnotations.SpeakerAnnotation.class));
  };

    public static boolean filterCorefChainWithMentionSpans(CorefChain chain, Set<Triple<Integer, Integer,Integer>> spans) {
        List<CorefChain.CorefMention> mentions = chain.getMentionsInTextualOrder();
        return mentions.stream().anyMatch(mention -> {
            return spans.contains(Triple.makeTriple(mention.sentNum-1, mention.startIndex-1, mention.endIndex-1));
        });
    }

    public static boolean filterClustersWithMentionSpans(CorefCluster cluster, Set<Triple<Integer, Integer,Integer>> spans) {
        Set<Mention> mentions = cluster.getCorefMentions();
        return mentions.stream().anyMatch(mention -> {
            return spans.contains(Triple.makeTriple(mention.sentNum, mention.startIndex, mention.endIndex));
        });
    }

    public static List<List<Mention>> filterXmlTagsFromMentions(List<List<Mention>> mentions) {
        List<List<Mention>> filtered = mentions.stream().map(
                smentions -> smentions.stream().filter( x -> {
                    String text = x.spanToString();
                    boolean isTag = (text.startsWith("<") && text.endsWith(">") &&
                            text.indexOf('<', 1) < 0 &&
                            text.lastIndexOf('>', text.length()-2) < 0);
                    return !isTag;
                }).collect(Collectors.toList())
        ).collect(Collectors.toList());
        return filtered;
    }
}
