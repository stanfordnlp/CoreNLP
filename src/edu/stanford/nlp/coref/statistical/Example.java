package edu.stanford.nlp.coref.statistical;

import java.io.Serializable;

import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
/**
 * A representation of a mention-pair for training coreference models.
 * @author Kevin Clark
 */
public class Example implements Serializable {
  private static final long serialVersionUID = 1104263558466004590L;
  public final int docId;
  public final double label;
  public final CompressedFeatureVector pairwiseFeatures;

  public final int mentionId1;
  public final int mentionId2;
  public final MentionType mentionType1;
  public final MentionType mentionType2;

  public Example(int docId, Mention m1, Mention m2, double label,
      CompressedFeatureVector pairwiseFeatures) {
    this.docId = docId;
    this.label = label;
    this.pairwiseFeatures = pairwiseFeatures;

    this.mentionId1 = m1.mentionID;
    this.mentionId2 = m2.mentionID;
    this.mentionType1 = m1.mentionType;
    this.mentionType2 = m2.mentionType;
  }

  public Example(Example pair, boolean isPositive) {
    this.docId = pair.docId;
    this.label = isPositive ? 1 : 0;
    this.pairwiseFeatures = null;

    this.mentionId1 = -1;
    this.mentionId2 = pair.mentionId2;
    this.mentionType1 = null;
    this.mentionType2 = pair.mentionType2;
  }

  public boolean isNewLink() {
    return pairwiseFeatures == null;
  }
}
