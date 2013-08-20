package edu.stanford.nlp.dcoref;

import edu.stanford.nlp.math.NumberMatchingRegex;
import edu.stanford.nlp.util.Generics;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Information about a speaker
 *
 * @author Angel Chang
 */
public class SpeakerInfo {
  private String speakerId;
  private String speakerName;
  private String[] speakerNameStrings; // tokenized speaker name
  private Set<Mention> mentions = Generics.newHashSet();  // Mentions that corresponds to the speaker...
  private boolean speakerIdIsNumber;          // speaker id is a number (probably mention id)
  private boolean speakerIdIsAutoDetermined;  // speaker id was auto determined by system


  // TODO: keep track of speaker utterances?

  private static final Pattern DEFAULT_SPEAKER_PATTERN = Pattern.compile("PER\\d+");
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+|_+");
  public SpeakerInfo(String speakerName) {
    this.speakerId = speakerName;
    this.speakerName = speakerName;
    this.speakerNameStrings = WHITESPACE_PATTERN.split(speakerName);
    speakerIdIsNumber = NumberMatchingRegex.isDecimalInteger(speakerId);
    speakerIdIsAutoDetermined = DEFAULT_SPEAKER_PATTERN.matcher(speakerId).matches();
  }

  public String getSpeakerName() {
    return speakerName;
  }

  public String[] getSpeakerNameStrings() {
    return speakerNameStrings;
  }

  public Set<Mention> getMentions() {
    return mentions;
  }

  public boolean containsMention(Mention m) {
    return mentions.contains(m);
  }

  public void addMention(Mention m) {
    if (mentions.isEmpty() && m.mentionType == Dictionaries.MentionType.PROPER && (speakerIdIsNumber || speakerIdIsAutoDetermined)) {
      // mention name is probably better indicator of the speaker
      speakerName = m.spanToString();
      speakerNameStrings = WHITESPACE_PATTERN.split(speakerName);
    }
    mentions.add(m);
  }

  public int getCorefClusterId() {
    int corefClusterId = -1;     // Coref cluster id that corresponds to this speaker
    for (Mention m:mentions) {
      if (m.corefClusterID >= 0) {
        corefClusterId = m.corefClusterID;
        break;
      }
    }
    return corefClusterId;
  }

}
