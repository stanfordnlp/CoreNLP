package edu.stanford.nlp.tagger.common;

/**
 * This module includes constants that are the same for all taggers,
 * as opposed to being part of their configurations.
 */
public class TaggerConstants {
  public static final String EOS_TAG = ".$$.";
  public static final String EOS_WORD = ".$.";
  
  /** constants only */
  private TaggerConstants() {}
}
