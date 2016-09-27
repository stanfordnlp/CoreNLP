package edu.stanford.nlp.coref.statistical;

import java.io.Serializable;

import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Mention;

public class Example implements Serializable {
  private static final long serialVersionUID = 1104263558466004590L;
  public final int docId;
  public final double label;
  public final CompressedFeatureVector features;

  public final int mentionId1;
  public final int mentionId2;
  public final MentionType mentionType1;
  public final MentionType mentionType2;

  public Example(int docId, Mention m1, Mention m2, double label,
      CompressedFeatureVector features) {
    this.docId = docId;
    this.label = label;
    this.features = features;

    this.mentionId1 = m1.mentionID;
    this.mentionId2 = m2.mentionID;
    this.mentionType1 = m1.mentionType;
    this.mentionType2 = m2.mentionType;
  }

  public Example(Example pair, boolean isPositive) {
    this.docId = pair.docId;
    this.label = isPositive ? 1 : 0;
    this.features = null;

    this.mentionId1 = -1;
    this.mentionId2 = pair.mentionId2;
    this.mentionType1 = null;
    this.mentionType2 = pair.mentionType2;
  }

  public boolean isNewLink() {
    return features == null;
  }
}
