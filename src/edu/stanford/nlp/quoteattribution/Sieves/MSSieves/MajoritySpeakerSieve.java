package edu.stanford.nlp.quoteattribution.Sieves.MSSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.quoteattribution.Person;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mjfang on 7/10/16.
 */
public class MajoritySpeakerSieve extends MSSieve {

  private final Counter<String> topSpeakerList;

  public Counter<String> getTopSpeakerList() {
    Counter<String> characters = new ClassicCounter<>();

    ArrayList<String> names = scanForNames(new Pair<>(0, doc.get(CoreAnnotations.TokensAnnotation.class).size() - 1)).first;

    for(String name : names) {
      name = name.replaceAll("\\s+", " ");
      characters.incrementCount(characterMap.get(name).get(0).name);
    }
    return characters;
  }

  public MajoritySpeakerSieve(Annotation doc,
                              Map<String, List<Person>> characterMap,
                              Map<Integer,String> pronounCorefMap,
                              Set<String> animacySet ) {
    super(doc, characterMap, pronounCorefMap, animacySet);
    this.topSpeakerList = getTopSpeakerList();
  }

  public void doMentionToSpeaker(Annotation doc) {
    for (CoreMap quote : doc.get(CoreAnnotations.QuotationsAnnotation.class)) {
      if (quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) == null) {
        quote.set(QuoteAttributionAnnotator.SpeakerAnnotation.class, characterMap.get(Counters.toSortedList(topSpeakerList).get(0)).get(0).name);
        quote.set(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class, "majority speaker baseline");
      }
    }
  }

}
