package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Grace Muzny
 */
public class OneNameSentenceSieve extends QMSieve {

  public OneNameSentenceSieve(Annotation doc, Map<String, List<Person>> characterMap,
                              Map<Integer,String> pronounCorefMap, Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "Deterministic oneNameSentence");
  }

  public void doQuoteToMention(Annotation doc) {
    oneNameSentence(doc);
    oneSpeakerSentence(doc);
  }

  public void oneNameSentence(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for (CoreMap quote : quotes) {
      if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
        continue;
      }
      Pair<Integer, Integer> range = QuoteAttributionUtils.getRemainderInSentence(doc, quote);
      if (range == null) {
        continue;
      }

      Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> namesAndNameIndices = scanForNames(range);
      ArrayList<String> names = namesAndNameIndices.first;
      ArrayList<Pair<Integer, Integer>> nameIndices = namesAndNameIndices.second;

      ArrayList<Integer> pronounsIndices = scanForPronouns(range);
      if (names.size() == 1) {
        String name = names.get(0).replaceAll("\\s+", " ");
        List<Person> p = characterMap.get(name);

        //guess if exactly one name
        if (p.size() == 1 && pronounsIndices.size() == 0) {
          fillInMention(quote, tokenRangeToString(nameIndices.get(0)), nameIndices.get(0).first, nameIndices.get(0).second,
                  sieveName, NAME);
        }
      }
    }
  }

}
