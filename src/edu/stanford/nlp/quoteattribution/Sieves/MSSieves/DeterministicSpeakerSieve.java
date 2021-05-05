package edu.stanford.nlp.quoteattribution.Sieves.MSSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.quoteattribution.Person;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mjfang on 7/8/16.
 */
public class DeterministicSpeakerSieve extends MSSieve {

  // private Map<String, Person.Gender> genderList;

  public DeterministicSpeakerSieve(Annotation doc, Map<String, List<Person>> characterMap,
                                   Map<Integer,String> pronounCorefMap, Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet);
  }

  public void doMentionToSpeaker(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for (CoreMap quote : quotes) {
      String mention = quote.get(QuoteAttributionAnnotator.MentionAnnotation.class);
      if (mention == null) {
        continue;
      }
      // replaceAll is to avoid newlines etc ruining our search
      mention = mention.replaceAll("\\s+", " ");
      int mentionBegin = quote.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class);
      int mentionEnd = quote.get(QuoteAttributionAnnotator.MentionEndAnnotation.class);
      List<CoreLabel> mentionTokens = new ArrayList<>();
      for (int i = mentionBegin; i <= mentionEnd; i++) {
        mentionTokens.add(doc.get(CoreAnnotations.TokensAnnotation.class).get(i));
      }
      String mentionType = quote.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class);
      if (mentionType.equals("name")) {
        quote.set(QuoteAttributionAnnotator.SpeakerAnnotation.class, characterMap.get(mention).get(0).name);
        quote.set(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class, "automatic name");
      } else if (mentionType.equals("pronoun")) {
        Person speaker = doCoreference(mentionTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class), quote);
        if (speaker != null) {
          quote.set(QuoteAttributionAnnotator.SpeakerAnnotation.class, speaker.name);
          quote.set(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class, "coref");
        }
      }
    }
  }

}
