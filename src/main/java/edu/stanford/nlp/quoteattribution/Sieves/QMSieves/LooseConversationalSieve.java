package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

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
public class LooseConversationalSieve extends QMSieve {

  public LooseConversationalSieve(Annotation doc, Map<String, List<Person>> characterMap,
                                  Map<Integer,String> pronounCorefMap, Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "loose");
  }

  public void doQuoteToMention(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    List<List<Pair<Integer, Integer>>> skipChains = new ArrayList<>();
    List<Pair<Integer, Integer>> currChain = new ArrayList<>(); //Pairs are (quote_idx, paragraph_idx)
    //same as conversational, but make it less restrictive.
    //look for patterns: are they consecutive in paragraph? group those that are in
    for (int quote_idx = 0; quote_idx < quotes.size(); quote_idx++) {
      CoreMap quote = quotes.get(quote_idx);
      if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) == null) {
        int para_idx = getQuoteParagraph(quote);

        if (currChain.size() != 0 && currChain.get(currChain.size() - 1).second != para_idx - 2) {
          skipChains.add(currChain);
          currChain = new ArrayList<>();
        }
        currChain.add(new Pair<>(quote_idx, para_idx));
      }
    }
    if (currChain.size() != 0) {
      skipChains.add(currChain);
    }

    for (List<Pair<Integer, Integer>> skipChain : skipChains) {
      Pair<Integer, Integer> firstQuoteAndParagraphIdx = skipChain.get(0);
      int firstParagraph = firstQuoteAndParagraphIdx.second;
      // boolean chainAttributed = false;
      for (int prevQuoteIdx = firstQuoteAndParagraphIdx.first - 1; prevQuoteIdx >= 0; prevQuoteIdx--) {
        CoreMap prevQuote = quotes.get(prevQuoteIdx);
        if (getQuoteParagraph(prevQuote) == firstParagraph - 2 && prevQuote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
          for (Pair<Integer, Integer> quoteAndParagraphIdx : skipChain) {
            CoreMap quote = quotes.get(quoteAndParagraphIdx.first);
            fillInMention(quote, getMentionData(prevQuote), sieveName);
          }
        }
      }
    }
  }

}
