package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
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
public class TrigramSieve extends QMSieve {

  public TrigramSieve(Annotation doc, Map<String, List<Person>> characterMap,
                      Map<Integer,String> pronounCorefMap, Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "");
  }

  public void doQuoteToMention(Annotation doc) {
    trigramPatterns(doc);
    oneSpeakerSentence(doc);
  }

  @SuppressWarnings("UnnecessaryContinue")
  public void trigramPatterns(Annotation doc) {
    List<CoreLabel> docTokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> docQuotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for (CoreMap quote : docQuotes) {
      if(quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null)
        continue;
      int quoteBeginTokenIndex = quote.get(CoreAnnotations.TokenBeginAnnotation.class);
      int quoteEndTokenIndex = quote.get(CoreAnnotations.TokenEndAnnotation.class);
      // int quoteEndSentenceIndex = quote.get(CoreAnnotations.SentenceEndAnnotation.class);
      Pair<Boolean, Pair<Integer, Integer>> pair = QuoteAttributionUtils.getTokenRangePrecedingQuote(doc, quote);
      Pair<Integer, Integer> precedingTokenRange;
      if (pair == null) {
        precedingTokenRange = null;
      } else {
        precedingTokenRange = pair.second();
      }
      //get tokens before and after
      if (precedingTokenRange != null) {
        Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> namesAndNameIndices = scanForNames(precedingTokenRange);
        ArrayList<String> names = namesAndNameIndices.first;
        ArrayList<Pair<Integer, Integer>> nameIndices = namesAndNameIndices.second;

        if (names.size() > 0) {
          int offset = 0;
          if (beforeQuotePunctuation.contains(docTokens.get(quoteBeginTokenIndex - 1).word())) {
            offset = 1;
          }
          Pair<Integer, Integer> lastNameIndex = nameIndices.get(nameIndices.size() - 1);
          CoreLabel prevToken = docTokens.get(quoteBeginTokenIndex - 1 - offset);


          //CVQ
          if (prevToken.tag() != null && prevToken.tag().startsWith("V")  // verb!
                  && lastNameIndex.second.equals(quoteBeginTokenIndex - 2 - offset)) {
            fillInMention(quote, names.get(names.size() - 1), lastNameIndex.first, lastNameIndex.second, "trigram CVQ", NAME);
            continue;
          }
          //VCQ
          if (lastNameIndex.second.equals(quoteBeginTokenIndex - 1 - offset)) {
            CoreLabel secondPrevToken = lastNameIndex.first >= 1 ? docTokens.get(lastNameIndex.first - 1) : null;
            if (secondPrevToken != null && secondPrevToken.tag().startsWith("V")) {
              fillInMention(quote, names.get(names.size() - 1), lastNameIndex.first, lastNameIndex.second, "trigram VCQ", NAME);
              continue;
            }
          }
        }

        ArrayList<Integer> pronounsIndices = scanForPronouns(precedingTokenRange);
        if (pronounsIndices.size() > 0) {
          int offset = 0;
          if (beforeQuotePunctuation.contains(docTokens.get(quoteBeginTokenIndex - 1).word())) {
            offset = 1;
          }

          CoreLabel prevToken = docTokens.get(quoteBeginTokenIndex - 1 - offset);
          int lastPronounIndex = pronounsIndices.get(pronounsIndices.size() - 1);
          //PVQ
          if (prevToken.tag().startsWith("V") /* verb! */ && lastPronounIndex == quoteBeginTokenIndex - 2 - offset) {
            fillInMention(quote, tokenRangeToString(lastPronounIndex), lastPronounIndex, lastPronounIndex, "trigram PVQ", PRONOUN);
            continue;
          }
          //VPQ
          if (lastPronounIndex == quoteBeginTokenIndex - 1 - offset &&
              docTokens.get(quoteBeginTokenIndex - 2 - offset).tag().startsWith("V")) {
            fillInMention(quote, tokenRangeToString(lastPronounIndex), lastPronounIndex, lastPronounIndex, "trigram VPQ", PRONOUN);
            continue;
          }
        }
      }

      Pair<Boolean, Pair<Integer, Integer>> followingPair = QuoteAttributionUtils.getTokenRangeFollowingQuote(doc, quote);
      Pair<Integer, Integer> followingTokenRange = (followingPair == null) ? null : followingPair.second();
      if (followingTokenRange != null) {
        Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> namesAndNameIndices = scanForNames(followingTokenRange);
        ArrayList<String> names = namesAndNameIndices.first;
        ArrayList<Pair<Integer, Integer>> nameIndices = namesAndNameIndices.second;

        if (names.size() > 0 &&
            docTokens.size() > quoteEndTokenIndex + 1) {
          Pair<Integer, Integer> firstNameIndex = nameIndices.get(0);
          CoreLabel nextToken = docTokens.get(quoteEndTokenIndex + 1);
          //QVC
          if (nextToken.tag().startsWith("V") && // verb!
              firstNameIndex.first.equals(quoteEndTokenIndex + 2)) {
            fillInMention(quote, names.get(0), firstNameIndex.first, firstNameIndex.second, "trigram QVC", NAME);
            continue;
          }
          //QCV
          if (firstNameIndex.first.equals(quoteEndTokenIndex + 1) &&
              docTokens.size() > firstNameIndex.second + 1) {
            CoreLabel secondNextToken = docTokens.get(firstNameIndex.second + 1);
            if(secondNextToken.tag().startsWith("V")) {
              fillInMention(quote, names.get(0), firstNameIndex.first, firstNameIndex.second, "trigram QCV", NAME);
              continue;
            }
          }
        }

        ArrayList<Integer> pronounsIndices = scanForPronouns(followingTokenRange);
        if (pronounsIndices.size() > 0 &&
            docTokens.size() > quoteEndTokenIndex + 1) {
          CoreLabel nextToken = docTokens.get(quoteEndTokenIndex + 1);
          int firstPronounIndex = pronounsIndices.get(0);
          //QVP
          if (nextToken.tag().startsWith("V")  /* verb! */ && firstPronounIndex == quoteEndTokenIndex + 2) {
            fillInMention(quote, tokenRangeToString(pronounsIndices.get(0)), firstPronounIndex, firstPronounIndex, "trigram QVP", PRONOUN);
            continue;
          }
          //QPV
          if (firstPronounIndex == quoteEndTokenIndex + 1 &&
              docTokens.size() > quoteEndTokenIndex + 2 &&
              docTokens.get(quoteEndTokenIndex + 2).tag().startsWith("V")) {
            fillInMention(quote, tokenRangeToString(pronounsIndices.get(pronounsIndices.size() - 1)), firstPronounIndex,
                          firstPronounIndex, "trigram QPV", PRONOUN);
            continue;
          }
        }
      }
    }
  }
}
