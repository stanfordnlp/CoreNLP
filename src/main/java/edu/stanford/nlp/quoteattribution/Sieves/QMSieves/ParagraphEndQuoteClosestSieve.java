package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.quoteattribution.Sieves.Sieve;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Grace Muzny
 */
public class ParagraphEndQuoteClosestSieve extends QMSieve {

  public ParagraphEndQuoteClosestSieve(Annotation doc, Map<String, List<Person>> characterMap,
                                       Map<Integer,String> pronounCorefMap, Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "Deterministic endQuoteClosestBefore");
  }

  public void doQuoteToMention(Annotation doc) {
    paragraphEndQuoteClosestBefore(doc);
    oneSpeakerSentence(doc);
  }

  //select nearest mention to the left if: the quote is ending a paragraph.
  public void paragraphEndQuoteClosestBefore(Annotation doc) {
    List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for(CoreMap quote : quotes) {
      if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
        continue;
      }
      Pair<Integer, Integer> range = QuoteAttributionUtils.getRemainderInSentence(doc, quote);
      if (range == null) {
        continue;
      }
      //search for mentions in the first run
      Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> namesAndNameIndices = scanForNames(range);
      ArrayList<String> names = namesAndNameIndices.first;
      int quoteBeginTokenIndex = quote.get(CoreAnnotations.TokenBeginAnnotation.class);
      boolean isBefore = range.second.equals(quoteBeginTokenIndex - 1); //check if the range is preceding the quote or after it.
      int quoteParagraph = QuoteAttributionUtils.getQuoteParagraphIndex(doc, quote);
      int quoteIndex = quote.get(CoreAnnotations.QuotationIndexAnnotation.class);

      boolean isOnlyQuoteInParagraph = true;
      if(quoteIndex > 0) {
        CoreMap prevQuote = quotes.get(quoteIndex - 1);
        int prevQuoteParagraph = QuoteAttributionUtils.getQuoteParagraphIndex(doc, prevQuote);
        if(prevQuoteParagraph == quoteParagraph) {
          isOnlyQuoteInParagraph = false;
        }
      }
      if(quoteIndex < quotes.size() - 1) {
        CoreMap nextQuote = quotes.get(quoteIndex + 1);
        int nextQuoteParagraph = QuoteAttributionUtils.getQuoteParagraphIndex(doc, nextQuote);
        if(nextQuoteParagraph == quoteParagraph) {
          isOnlyQuoteInParagraph = false;
        }
      }

      if(isBefore && tokens.get(range.second).word().equals(",") && isOnlyQuoteInParagraph) {
        Sieve.MentionData closestMention = findClosestMentionInSpanBackward(range);
        if(closestMention != null && !closestMention.type.equals("animate noun")) {
          fillInMention(quote, closestMention, sieveName);
        }
      }
    }
  }
}
