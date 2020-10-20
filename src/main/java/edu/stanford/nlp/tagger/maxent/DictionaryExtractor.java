/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.tagger.maxent;

/**
 * This class is the same as a regular Extractor, but keeps a pointer
 * to the tagger's dictionary as well.
 *
 * Obviously that means this kind of extractor is not reusable across
 * multiple taggers (see comments Extractor.java), so no extractor of
 * this type should be declared static.
 */
public class DictionaryExtractor extends Extractor {

  private static final long serialVersionUID = 692763177746328195L;

  /**
   * A pointer to the creating / owning tagger's dictionary.
   */
  protected transient Dictionary dict;

  /**
   * Any subclass of this extractor that overrides setGlobalHolder
   * should call this class's setGlobalHolder as well...
   */
  @Override
  protected void setGlobalHolder(MaxentTagger tagger) {
    super.setGlobalHolder(tagger);
    this.dict = tagger.dict;
  }
}
