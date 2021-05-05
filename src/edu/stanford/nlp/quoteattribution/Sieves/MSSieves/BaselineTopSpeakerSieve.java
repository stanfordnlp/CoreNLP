package edu.stanford.nlp.quoteattribution.Sieves.MSSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.Person;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

/** Return as a guess the most frequent speaker (of the right gender) in the region of text close to the quote.
 *
 *  @author Michael Fang
 *  @author Grace Muzny
 */
public class BaselineTopSpeakerSieve extends MSSieve {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(BaselineTopSpeakerSieve.class);

  private final Map<String, Person.Gender> genderList;
  private final Set<String> familyRelations;

  public static final int BACKWARD_WINDOW = 2000;
  public static final int BACKWARD_WINDOW_BIG = 4000;
  public static final int FORWARD_WINDOW = 500;
  public static final int FORWARD_WINDOW_BIG = 2500;
  public static final double FORWARD_WEIGHT = 0.34;
  public static final double BACKWARD_WEIGHT = 1.0;

  public BaselineTopSpeakerSieve(Annotation doc, Map<String, List<Person>> characterMap,
                                 Map<Integer,String> pronounCorefMap, Set<String> animacySet,
                                 Map<String, Person.Gender> genderList, Set<String> familyRelations) {
    super(doc, characterMap, pronounCorefMap, animacySet);
    this.genderList = genderList;
    this.familyRelations = familyRelations;
  }

  @Override
  public void doMentionToSpeaker(Annotation doc) {
    topSpeakerInRange(doc);
  }

  public MentionData makeMentionData(CoreMap q) {
    if(q.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
      return new MentionData(q.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class),
              q.get(QuoteAttributionAnnotator.MentionEndAnnotation.class),
              q.get(QuoteAttributionAnnotator.MentionAnnotation.class),
              q.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class));
    }
    return new MentionData(-1, -1, null, null);
  }

  public void topSpeakerInRange(Annotation doc) {
    List<CoreLabel> toks = doc.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for (int quote_idx = 0, qsize = quotes.size(); quote_idx < qsize; quote_idx++) {
      CoreMap quote = quotes.get(quote_idx);
      // log.info("BaselineTopSpeakerSieve processing quote: " + quote.toShorterString());
      if (quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) == null) {
        Pair<Integer, Integer> quoteRun = new Pair<>(quote.get(CoreAnnotations.TokenBeginAnnotation.class),
                quote.get(CoreAnnotations.TokenEndAnnotation.class));
        int backSpanStart = Math.max(0, quoteRun.first - BACKWARD_WINDOW);
        List<MentionData> closestMentionsBackward = findClosestMentionsInSpanBackward(
                new Pair<>(backSpanStart, quoteRun.first - 1));
        // log.info("  Found backward mentions in [" + backSpanStart + "," + (quoteRun.first - 1) + "]: " +
        //         closestMentionsBackward);
        int forwardSpanEnd = Math.min(quoteRun.second + FORWARD_WINDOW, toks.size() - 1);
        List<MentionData> closestMentions = findClosestMentionsInSpanForward(new Pair<>(quoteRun.second + 1, forwardSpanEnd));
        // log.info("  Found forward mentions in [" + (quoteRun.second + 1) + "," + forwardSpanEnd + "]: " +
        //         closestMentions);
        closestMentions.addAll(closestMentionsBackward);

        Person.Gender gender = getGender(makeMentionData(quote));

        List<String> topSpeakers = Counters.toSortedList(getTopSpeakers(closestMentions, closestMentionsBackward, gender,
                quote, false));
        // if none found, try again with bigger window
        if (topSpeakers.isEmpty()) {
          if (backSpanStart > 0) {
            backSpanStart = Math.max(0, quoteRun.first - BACKWARD_WINDOW_BIG);
            closestMentionsBackward = findClosestMentionsInSpanBackward(new Pair<>(backSpanStart, quoteRun.first - 1));
            // log.info("  Found big backward mentions in [" + backSpanStart + "," + (quoteRun.first - 1) + "]: " +
            //         closestMentionsBackward);
          }
          if (forwardSpanEnd < toks.size() - 1) {
            forwardSpanEnd = Math.min(quoteRun.second + FORWARD_WINDOW_BIG, toks.size() - 1);
            closestMentions = findClosestMentionsInSpanForward(new Pair<>(quoteRun.second + 1, forwardSpanEnd));
            // log.info("  Found big forward mentions in [" + (quoteRun.second + 1) + "," + forwardSpanEnd + "]: " +
            //         closestMentions);
          }
          topSpeakers = Counters.toSortedList(getTopSpeakers(closestMentions, closestMentionsBackward, gender,
                  quote, true));
        }
        if (topSpeakers.isEmpty()) {
          log.warn("  Empty top speakers list for: " + quote.toShorterString() +
                  " [no candidate top speakers found – just ignore!");
          continue;
        }
        topSpeakers = removeQuoteNames(topSpeakers, quote);
        String topSpeaker = topSpeakers.get(0);

        Pair<String, String> nextPrediction = getConversationalNextPrediction(quotes, quote_idx, gender);
        boolean set = updatePredictions(quote, nextPrediction);
        if (set) {
          continue;
        }

        Pair<String, String> prevPrediction = getConversationalPreviousPrediction(quotes, quote_idx, gender);
        set = updatePredictions(quote, prevPrediction);
        if (set) {
          continue;
        }

        Pair<String, String> famPrediction = getFamilyAnimateVocative(quotes, quote_idx, gender, topSpeakers);
        set = updatePredictions(quote, famPrediction);
        if (set) {
          continue;
        }

        updatePredictions(quote, new Pair<>(topSpeaker, ""));
      }
    }
  }

  public List<String> removeQuoteNames(List<String> topSpeakers, CoreMap quote) {
    // if the top speakers name is in the quote, move to the next option and remove it
    String topSpeaker = topSpeakers.get(0);
    Set<Person> namesInParagraphQuotes = getNamesInParagraph(quote);
    if(namesInParagraphQuotes.contains(characterMap.get(topSpeaker).get(0)) && topSpeakers.size() > 1) {
      topSpeakers.remove(0);
    }
    return topSpeakers;
  }

  public Person.Gender getGender(MentionData mention) {
    Person.Gender gender = Person.Gender.UNK;
    if (mention.type != null && mention.type.equals("pronoun")) {
      if (mention.text.equalsIgnoreCase("he")) {
        gender = Person.Gender.MALE;
      } else if (mention.text.equalsIgnoreCase("she")){
        gender = Person.Gender.FEMALE;
      }
    } else if (mention.type != null && mention.type.equals("animate noun")) {
      String mentionText = mention.text.toLowerCase();
      if (genderList.get(mentionText) != null) {
        gender = genderList.get(mentionText);
      }
    } else if(mention.type != null && mention.type.equals("name")) {
      String mentionText = mention.text.replaceAll("\\s+", " ");
      gender = characterMap.get(mentionText).get(0).gender;
    }
    return gender;
  }

  public Counter<String> getTopSpeakers(List<MentionData> closestMentionsForward, List<MentionData> closestMentionsBackward,
                                        Person.Gender gender, CoreMap quote, boolean overrideGender) {
    // put all mentions in closestMentions, and then use closestMentionsBackwards to differentiate forward/backward
    List<MentionData> closestMentions = new ArrayList<>(closestMentionsForward.size() + closestMentionsBackward.size());
    closestMentions.addAll(closestMentionsForward);
    closestMentions.addAll(closestMentionsBackward);
    Counter<String> topSpeakerInRange = new ClassicCounter<>();
    Counter<String> topSpeakerInRangeIgnoreGender = new ClassicCounter<>();

    Set<MentionData> backwardsMentions = new HashSet<>(closestMentionsBackward);

    for (MentionData mention : closestMentions) {
      double weight = backwardsMentions.contains(mention) ? BACKWARD_WEIGHT : FORWARD_WEIGHT;
      if (mention.type.equals(NAME)) {
        if (!characterMap.containsKey(mention.text)) {
          continue;
        }
        Person p = characterMap.get(mention.text).get(0);
        if ((gender == Person.Gender.MALE && p.gender == Person.Gender.MALE) ||
                (gender == Person.Gender.FEMALE && p.gender == Person.Gender.FEMALE) ||
                (gender == Person.Gender.UNK)) {
          topSpeakerInRange.incrementCount(p.name, weight);
        }
        topSpeakerInRangeIgnoreGender.incrementCount(p.name, weight);
        // if (closestMentions.size() == 128 && closestMentionsBackward.size() == 94)
        //   System.out.println(p.name + " " + weight + " name");
      } else if (mention.type.equals(PRONOUN)) {
        int charBeginKey = doc.get(CoreAnnotations.TokensAnnotation.class).get(mention.begin).beginPosition();
        Person p = doCoreference(charBeginKey, quote);
        if (p != null) {
          if ((gender == Person.Gender.MALE && p.gender == Person.Gender.MALE) ||
                  (gender == Person.Gender.FEMALE && p.gender == Person.Gender.FEMALE) ||
                  (gender == Person.Gender.UNK)) {
            topSpeakerInRange.incrementCount(p.name, weight);
          }
          topSpeakerInRangeIgnoreGender.incrementCount(p.name, weight);
          // if (closestMentions.size() == 128 && closestMentionsBackward.size() == 94)
          //   System.out.println(p.name + " " + weight + " pronoun");
        }
      }
    }
    if (topSpeakerInRange.size() > 0) {
      return topSpeakerInRange;
    } else if (gender != Person.Gender.UNK && ! overrideGender) {
      return topSpeakerInRange;
    }
    return topSpeakerInRangeIgnoreGender;
  }

  public boolean updatePredictions(CoreMap quote, Pair<String, String> speakerAndMethod) {
    if (speakerAndMethod.first != null && speakerAndMethod.second != null) {
      quote.set(QuoteAttributionAnnotator.SpeakerAnnotation.class, characterMap.get(speakerAndMethod.first).get(0).name);
      quote.set(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class, "Baseline Top" + speakerAndMethod.second);
      return true;
    }
    return false;
  }


  public Pair<String, String> getFamilyAnimateVocative(List<CoreMap> quotes, int quote_index, Person.Gender gender,
                                                       List<String> topSpeakers) {
    MentionData mention = makeMentionData(quotes.get(quote_index));
    if (mention.text != null) {
      if (mention.type.equals("animate noun") && familyRelations.contains(mention.text.toLowerCase())
              && gender != Person.Gender.UNK) {
        int quoteContainingMention = getQuoteContainingRange(quotes, new Pair<>(mention.begin, mention.end));

        if (quoteContainingMention >= 0) {
          String relatedName = quotes.get(quoteContainingMention).get(QuoteAttributionAnnotator.SpeakerAnnotation.class);
          if (relatedName != null) {
            for (String speaker : topSpeakers) {
              String[] speakerNames = speaker.split("_");
              if (relatedName.endsWith(speakerNames[speakerNames.length - 1])) {
                return new Pair<>(speaker, "family animate");
              }
            }
          }
        }
      }
    }
    return new Pair<>(null, null);
  }

  public Pair<String, String> getConversationalPreviousPrediction(List<CoreMap> quotes, int quoteIndex,
                                                                  Person.Gender gender) {
    String topSpeaker = null;
    String modifier = null;
    // if the n - 2 paragraph quotes are labelled with a speaker and
    // that speakers gender does not disagree, label with that speaker
    List<Integer> quotesInPrevPrev = new ArrayList<>();
    CoreMap quote = quotes.get(quoteIndex);
    int quoteParagraph = getQuoteParagraph(quote);
    for(int j = quoteIndex - 1; j >= 0; j--) {
      if(getQuoteParagraph(quotes.get(j)) == quoteParagraph - 2) {
        quotesInPrevPrev.add(j);
      }
    }

    for (int prevPrev : quotesInPrevPrev) {
      CoreMap prevprevQuote = quotes.get(prevPrev);
      String speakerName = prevprevQuote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class);

      if (speakerName != null && (gender == Person.Gender.UNK) || getGender(makeMentionData(prevprevQuote)) == gender) {
        topSpeaker = speakerName;
        modifier = " conversation - prev";
      }
    }
    return new Pair<>(topSpeaker, modifier);
  }

  public Pair<String, String> getConversationalNextPrediction(List<CoreMap> quotes, int quoteIndex,
                                                              Person.Gender gender) {
    String topSpeaker = null;
    String modifier = null;
    // if the n - 2 paragraph quotes are labelled with a speaker and
    // that speakers gender does not disagree, label with that speaker
    List<Integer> quotesInNextNext = new ArrayList<>();
    CoreMap quote = quotes.get(quoteIndex);
    int quoteParagraph = getQuoteParagraph(quote);
    for (int j = quoteIndex + 1; j < quotes.size(); j++) {
      if (getQuoteParagraph(quotes.get(j)) == quoteParagraph + 2) {
        quotesInNextNext.add(j);
      }
    }

    for (int nextNext : quotesInNextNext) {
      CoreMap nextNextQuote = quotes.get(nextNext);
      String speakerName = nextNextQuote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class);
      MentionData md = makeMentionData(quotes.get(nextNext));
      if (speakerName != null && (gender == Person.Gender.UNK) || getGender(md) == gender) {
        topSpeaker = speakerName;
        modifier = " conversation - next";
      }
    }
    return new Pair<>(topSpeaker, modifier);
  }

  public static int getQuoteContainingRange(List<CoreMap> quotes, Pair<Integer, Integer> range) {
    for (int i = 0, qSize = quotes.size(); i < qSize; i++) {
      if (quotes.get(i).get(CoreAnnotations.TokenBeginAnnotation.class) <= range.first &&
              quotes.get(i).get(CoreAnnotations.TokenEndAnnotation.class) >= range.second) {
        return i;
      }
    }
    return -1;
  }

}
