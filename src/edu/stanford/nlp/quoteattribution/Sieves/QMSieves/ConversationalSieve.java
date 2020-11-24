package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.quoteattribution.Sieves.Sieve;
import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mjfang on 7/7/16.
 */
public class ConversationalSieve extends QMSieve {

  public ConversationalSieve(Annotation doc,
                             Map<String, List<Person>> characterMap,
                             Map<Integer,String> pronounCorefMap,
                             Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "conv");
  }

  //attribute conversational mentions: assign the mention to the same quote as the
  //if quote X has not been labelled, has no add'l text, and quote X-2 has been labelled, and quotes X-2, X-1, and X are consecutive in paragraph,
  //and X-1's quote does not refer to a name:
  //give quote X the same mention as X-2.
  public void doQuoteToMention(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    for(int index = 2; index < quotes.size(); index++) {
      CoreMap currQuote = quotes.get(index);
      CoreMap prevQuote = quotes.get(index - 1);
      CoreMap twoPrevQuote = quotes.get(index - 2);
      if (currQuote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
        // Chris added this in 2020: we've already found a speaker mention
        continue;
      }

      int twoPrevPara = getQuoteParagraph(twoPrevQuote);
      //default to first in quote that begins n-2
      for (int i = index-3; i >= 0; i--) {
        if (getQuoteParagraph(quotes.get(i)) == twoPrevPara) {
          twoPrevQuote = quotes.get(i);
        } else {
          break;
        }
      }
      int tokenBeginIdx = currQuote.get(CoreAnnotations.TokenBeginAnnotation.class);
      int tokenEndIdx = currQuote.get(CoreAnnotations.TokenEndAnnotation.class);
      CoreMap currQuoteBeginSentence = sentences.get(currQuote.get(CoreAnnotations.SentenceBeginAnnotation.class));
      boolean isAloneInParagraph = true;
      if(tokenBeginIdx > 0) {
        CoreLabel prevToken = tokens.get(tokenBeginIdx - 1);
        CoreMap prevSentence = sentences.get(prevToken.get(CoreAnnotations.SentenceIndexAnnotation.class));
        if(prevSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class).equals(currQuoteBeginSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class))) {
          isAloneInParagraph = false;
        }
      }
      if(tokenEndIdx < tokens.size() - 1) {
        // if the next token is *NL*, it won't be in a sentence (if newlines have been tokenized)
        // so advance to the next non *NL* toke
        CoreLabel currToken = tokens.get(tokenEndIdx + 1);
        while (currToken.isNewline() && tokenEndIdx + 1 < tokens.size() - 1) {
          tokenEndIdx++;
          currToken = tokens.get(tokenEndIdx + 1);
        }
        if (!currToken.isNewline()) {
          CoreMap nextSentence = sentences.get(currToken.get(CoreAnnotations.SentenceIndexAnnotation.class));
          if (nextSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class).equals(currQuoteBeginSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class))) {
            isAloneInParagraph = false;
          }
        }
      }
      if (twoPrevQuote.get(QuoteAttributionAnnotator.MentionAnnotation.class) == null
              || !isAloneInParagraph
              || currQuote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null
              || twoPrevQuote.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class).equals(Sieve.PRONOUN)) {
        continue;
      }
      if (getQuoteParagraph(currQuote) == getQuoteParagraph(prevQuote) + 1 && getQuoteParagraph(prevQuote) == getQuoteParagraph(twoPrevQuote) + 1) {
        fillInMention(currQuote, getMentionData(twoPrevQuote), sieveName);
      }
    }
  }

}
