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
public class VocativeSieve extends QMSieve {

  public VocativeSieve(Annotation doc,
                       Map<String, List<Person>> characterMap,
                       Map<Integer,String> pronounCorefMap,
                       Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "");
  }

  public void doQuoteToMention(Annotation doc) {
    vocativeQuoteToMention(doc);
    oneSpeakerSentence(doc);
  }

  public void vocativeQuoteToMention(Annotation doc) {
    // Start of utterance
    // before period
    // between commas
    // between comman & period
    // before exclamation
    // before question
    // Dear, oh!
    List<CoreLabel> toks = doc.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    for(CoreMap quote : quotes) {
      if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
        continue;
      }

      int currQuoteIndex = quote.get(CoreAnnotations.QuotationIndexAnnotation.class);


      int currParagraph = sentences.get(quote.get(CoreAnnotations.SentenceBeginAnnotation.class)).get(CoreAnnotations.ParagraphIndexAnnotation.class);
      List<CoreMap> quotesInPrevParagraph = new ArrayList<>();
      for(int i = currQuoteIndex-1; i >= 0; i--) {
        CoreMap prevQuote = quotes.get(i);
        int prevParagraph = sentences.get(prevQuote.get(CoreAnnotations.SentenceBeginAnnotation.class)).get(CoreAnnotations.ParagraphIndexAnnotation.class);
        if(prevParagraph + 1 == currParagraph) {
          quotesInPrevParagraph.add(prevQuote);
        }
        else {
          break;
        }
      }
      if(quotesInPrevParagraph.size() == 0) {
        continue;
      }
      boolean vocativeFound = false;
      for(CoreMap prevQuote : quotesInPrevParagraph) {
        Pair<Integer, Integer> quoteRun = new Pair<>(prevQuote.get(CoreAnnotations.TokenBeginAnnotation.class), prevQuote.get(CoreAnnotations.TokenEndAnnotation.class));
        Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> nameAndIndices = scanForNames(quoteRun);

        List<Pair<String, Pair<Integer, Integer>>> vocativeIndices = new ArrayList<>();

        for (int i = 0; i < nameAndIndices.first.size(); i++) {
          String name = nameAndIndices.first.get(i);
          Pair<Integer, Integer> nameIndex = nameAndIndices.second.get(i);
          String prevToken = nameIndex.first >= 1 ? toks.get(nameIndex.first - 1).word() : "";
          String prevPrevToken = nameIndex.first >= 2 ? toks.get(nameIndex.first - 2).word() : "";
          String nextToken = nameIndex.second + 1 < toks.size() ? toks.get(nameIndex.second + 1).word() : "";
          if ((prevToken.equals(",") && nextToken.equals("!")) ||
                  (prevToken.equals(",") && nextToken.equals("?")) ||
                  (prevToken.equals(",") && nextToken.equals(".")) ||
                  (prevToken.equals(",") && nextToken.equals(",")) ||
                  (prevToken.equals(",") && nextToken.equals(";")) ||
                  (prevToken.equals("``") && nextToken.equals(",")) ||
                  (nextToken.equals("''") && prevToken.equals(",")) ||
                  prevToken.equalsIgnoreCase("dear") ||
                  (prevToken.equals("!") && prevPrevToken.equalsIgnoreCase("oh"))) {
            vocativeIndices.add(new Pair<>(name, nameIndex));
          }
        }
        if (vocativeIndices.size() > 0) {
          fillInMention(quote, vocativeIndices.get(0).first, vocativeIndices.get(0).second.first,
                  vocativeIndices.get(0).second.second, "Deterministic Vocative -- name", NAME);
          vocativeFound = true;
          break;
        }
      }
      if(vocativeFound) {
        continue;
      }
      for(CoreMap prevQuote : quotesInPrevParagraph) {
        Pair<Integer, Integer> quoteRun = new Pair<>(prevQuote.get(CoreAnnotations.TokenBeginAnnotation.class), prevQuote.get(CoreAnnotations.TokenEndAnnotation.class));
        List<Integer> animates = scanForAnimates(quoteRun);
        List<Pair<String, Integer>> animateVocatives = new ArrayList<>();
        for (int animateIndex : animates) {
          if (animateIndex < 2 || animateIndex >= toks.size() + 1)
            continue;
          String prevToken = toks.get(animateIndex - 1).word();
          String prevPrevToken = toks.get(animateIndex - 2).word();
          String nextToken = toks.get(animateIndex + 1).word();
          if ((prevToken.equals(",") && nextToken.equals("!")) ||
                  (prevToken.equals(",") && nextToken.equals("?")) ||
                  (prevToken.equals(",") && nextToken.equals(".")) ||
                  (prevToken.equals(",") && nextToken.equals(",")) ||
                  (prevToken.equals(",") && nextToken.equals(";")) ||
                  (prevToken.equals("``") && nextToken.equals(",")) ||
                  (nextToken.equals("''") && prevToken.equals(",")) ||
                  prevToken.equalsIgnoreCase("dear") ||
                  (prevToken.equals("!") && prevPrevToken.equalsIgnoreCase("oh"))) {
            animateVocatives.add(new Pair<>(toks.get(animateIndex).word(), animateIndex));
          }
        }
        if (animateVocatives.size() > 0) {
          fillInMention(quote, animateVocatives.get(0).first, animateVocatives.get(0).second,
                  animateVocatives.get(0).second, "Deterministic Vocative -- animate noun", ANIMATE_NOUN);
          break;
        }
      }
    }
  }
}
