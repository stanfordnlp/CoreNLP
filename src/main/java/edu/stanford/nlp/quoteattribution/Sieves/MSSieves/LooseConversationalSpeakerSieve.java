package edu.stanford.nlp.quoteattribution.Sieves.MSSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.quoteattribution.Person;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mjfang on 7/8/16.
 */
public class LooseConversationalSpeakerSieve extends MSSieve {

  public LooseConversationalSpeakerSieve(Annotation doc,
                                         Map<String, List<Person>> characterMap,
                                         Map<Integer,String> pronounCorefMap,
                                         Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet);
  }

  public void doMentionToSpeaker(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    List<List<Pair<Integer, Integer>>> skipChains = new ArrayList<>();
    List<Pair<Integer, Integer>> currChain = new ArrayList<>(); //Pairs are (pred_idx, paragraph_idx)

    for(int quote_idx = 0; quote_idx < quotes.size(); quote_idx++) {
      CoreMap quote = quotes.get(quote_idx);
      if(quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) != null) {
        int para_idx = getQuoteParagraph(quote);
        if (currChain.size() != 0) {
          if (currChain.get(currChain.size() - 1).second != para_idx - 2) {
            skipChains.add(currChain);
            currChain = new ArrayList<>();
          }
        }
        currChain.add(new Pair<>(quote_idx, para_idx));
      }
    }
    if(currChain.size() != 0) {
      skipChains.add(currChain);
    }
    for(List<Pair<Integer, Integer>> skipChain : skipChains) {
      Pair<Integer, Integer> firstPair = skipChain.get(0);
      int firstParagraph = firstPair.second;
      //look for conversational chain candidate
      for(int prev_idx = firstPair.first - 1; prev_idx >= 0; prev_idx--) {
        CoreMap quote = quotes.get(prev_idx + 1);
        CoreMap prevQuote = quotes.get(prev_idx);
        if(getQuoteParagraph(prevQuote) == firstParagraph - 2) {
          quote.set(QuoteAttributionAnnotator.SpeakerAnnotation.class, prevQuote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class));
          quote.set(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class, "Loose Conversational Speaker");
        }
      }
    }
  }

}
