package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.quoteattribution.Person;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.Sieves.Sieve;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;

/**
 * @author Michael Fang
 */
public abstract class QMSieve extends Sieve {

  protected static Set<String> beforeQuotePunctuation =
      new HashSet<String>(Arrays.asList(new String[]{",", ":"}));

  protected static final SemgrexPattern subjVerbPattern = SemgrexPattern.compile("{pos:/VB.*/}=VERB >nsubj {}=SUBJ");
  protected static Set<String> commonSpeechWords = new HashSet<String>(
      Arrays.asList(new String[]{"say", "cry", "reply", "add", "think",
          "observe", "call", "answer"}));

  protected String sieveName;

  public QMSieve(Annotation doc,
                 Map<String, List<Person>> characterMap,
                 Map<Integer, String> pronounCorefMap,
                 Set<String> animacySet,
                 String sieveName) {
    super(doc, characterMap, pronounCorefMap, animacySet);
    this.sieveName = sieveName;
  }

//  public abstract void doQuoteToMention(List<XMLPredictions> predsList);
  public abstract void doQuoteToMention(Annotation doc);

  public static void fillInMention(CoreMap quote, String text, int begin, int end, String sieveName, String mentionType) {
    quote.set(QuoteAttributionAnnotator.MentionAnnotation.class, text);
    quote.set(QuoteAttributionAnnotator.MentionBeginAnnotation.class, begin);
    quote.set(QuoteAttributionAnnotator.MentionEndAnnotation.class, end);
    quote.set(QuoteAttributionAnnotator.MentionSieveAnnotation.class, sieveName);
    quote.set(QuoteAttributionAnnotator.MentionTypeAnnotation.class, mentionType);
  }
  protected static void fillInMention(CoreMap quote, MentionData md, String sieveName) {
    fillInMention(quote, md.text, md.begin, md.end, sieveName, md.type);
  }
  protected MentionData getMentionData(CoreMap quote) {
    String text = quote.get(QuoteAttributionAnnotator.MentionAnnotation.class);
    int begin = quote.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class);
    int end = quote.get(QuoteAttributionAnnotator.MentionEndAnnotation.class);
    String type = quote.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class);
    return new MentionData(begin, end, text, type);
  }
}
