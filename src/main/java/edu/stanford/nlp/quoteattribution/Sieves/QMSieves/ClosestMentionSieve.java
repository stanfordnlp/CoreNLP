package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mjfang on 7/7/16.
 */
public class ClosestMentionSieve extends QMSieve {

  public ClosestMentionSieve(Annotation doc,
                             Map<String, List<Person>> characterMap,
                             Map<Integer,String> pronounCorefMap,
                             Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "closestBaseline");
  }

  public MentionData getClosestMention(CoreMap quote) {
    MentionData closestBackward = findClosestMentionInSpanBackward(new Pair<>(0, quote.get(CoreAnnotations.TokenBeginAnnotation.class) - 1));
    MentionData closestForward = findClosestMentionInSpanForward(new Pair<>(quote.get(CoreAnnotations.TokenEndAnnotation.class), doc.get(CoreAnnotations.TokensAnnotation.class).size() - 1));
    int backDistance = quote.get(CoreAnnotations.TokenBeginAnnotation.class) - closestBackward.end;
    int forwardDistance = closestForward.begin - quote.get(CoreAnnotations.TokenEndAnnotation.class) + 1;
    if (backDistance < forwardDistance) {
      return closestBackward;
    } else {
      return closestForward;
    }
  }

  public void doQuoteToMention(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for (CoreMap quote : quotes) {
      // cdm 2020: Test used to be != but surely it shold be ==; I changed it
      if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) == null) {
        MentionData md = getClosestMention(quote);
        fillInMention(quote, md, sieveName);
      }
    }
  }

}

