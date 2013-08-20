package edu.stanford.nlp.dcoref;

/**
 * TODO(gabor) This is a dummy class to fix compilation
 *
 * @author Gabor Angeli
 */
public interface MentionMatcher {
  public Boolean isCompatible(Mention mainMention, Mention antMention);
}
