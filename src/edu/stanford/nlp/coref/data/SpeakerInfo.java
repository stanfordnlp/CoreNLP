package edu.stanford.nlp.coref.data;

import edu.stanford.nlp.math.NumberMatchingRegex;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Information about a speaker
 *
 * @author Angel Chang
 */
public class SpeakerInfo implements Serializable {
  private static final long serialVersionUID = 7776098967746458031L;

  private final String speakerId;
  private String speakerName;
  private String[] speakerNameStrings; // tokenized speaker name
  private String speakerDesc;
  private final Set<Mention> mentions = new LinkedHashSet<>();  // Mentions that corresponds to the speaker...
//  private Mention originalMention;            // the mention used when creating this SpeakerInfo
  private final boolean speakerIdIsNumber;          // speaker id is a number (probably mention id)
  private final boolean speakerIdIsAutoDetermined;  // speaker id was auto determined by system
//  private Mention mainMention;

  // TODO: keep track of speaker utterances?

  private static final Pattern DEFAULT_SPEAKER_PATTERN = Pattern.compile("PER\\d+");
  public static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+|_+");
  public SpeakerInfo(String speakerName) {
    this.speakerId = speakerName;
    int commaPos = speakerName.indexOf(',');
    if (commaPos > 0) {
      // drop everything after the ,
      this.speakerName = speakerName.substring(0, commaPos);
      if (commaPos < speakerName.length()) {
        speakerDesc = speakerName.substring(commaPos+1);
        speakerDesc = speakerDesc.trim();
        if (speakerDesc.isEmpty()) speakerDesc = null;
      }
    } else {
      this.speakerName = speakerName;
    }
    this.speakerNameStrings = WHITESPACE_PATTERN.split(this.speakerName);
    speakerIdIsNumber = NumberMatchingRegex.isDecimalInteger(speakerId);
    speakerIdIsAutoDetermined = DEFAULT_SPEAKER_PATTERN.matcher(speakerId).matches();
  }

  public boolean hasRealSpeakerName() {
    return mentions.size() > 0 || !(speakerIdIsAutoDetermined || speakerIdIsNumber);
  }

  public String getSpeakerName() {
    return speakerName;
  }

  public String getSpeakerDesc() {
    return speakerDesc;
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
    if (mentions.isEmpty() && m.mentionType == Dictionaries.MentionType.PROPER) {
      // check if mention name is probably better indicator of the speaker
      String mentionName = m.spanToString();
      if (speakerIdIsNumber || speakerIdIsAutoDetermined) {
        String nerName = m.nerName();
        speakerName = (nerName != null)? nerName: mentionName;
        speakerNameStrings = WHITESPACE_PATTERN.split(speakerName);
      }
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

  public String toString() {
    return speakerId;
  }

}
