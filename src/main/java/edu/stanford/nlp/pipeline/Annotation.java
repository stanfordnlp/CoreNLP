//
// Annotation -- annotation protocol used by StanfordCoreNLP
// Copyright (c) 2009-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

/**
 * An annotation representing a span of text in a document.
 *
 * Basically just an implementation of CoreMap that knows about text.
 * You're meant to use the annotation keys in CoreAnnotations for common
 * cases, but can define bespoke ones for unusual annotations.
 *
 * @author Jenny Finkel
 * @author Anna Rafferty
 * @author bethard
 */
public class Annotation extends ArrayCoreMap {

  /**
   * SerialUID
   */
  private static final long serialVersionUID = 1L;


  /** Copy constructor.
   *  @param map The new Annotation copies this one.
   */
  public Annotation(Annotation map) {
    super(map);
  }

  /** Copies the map, but not a deep copy.
   *  @return The copy
   */
  public Annotation copy() {
    return new Annotation(this);
  }

  /**
   * The text becomes the CoreAnnotations.TextAnnotation of the newly
   * created Annotation.
   */
  public Annotation(String text) {
    this.set(CoreAnnotations.TextAnnotation.class, text);
  }

  /** The basic toString() method of an Annotation simply
   *  prints out the text over which any annotations have
   *  been made (TextAnnotation). To print all the
   *  Annotation keys, use {@code toShorterString();}.
   *
   *  @return The text underlying this Annotation
   */
  @Override
  public String toString() {
    return this.get(CoreAnnotations.TextAnnotation.class);
  }

  /** Make a new Annotation from a List of tokenized sentences. */
  public Annotation(List<CoreMap> sentences) {
    super();
    this.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    List<CoreLabel> tokens = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    for (CoreMap sentence : sentences) {
      List<CoreLabel> sentenceTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      tokens.addAll(sentenceTokens);
      if (sentence.containsKey(CoreAnnotations.TextAnnotation.class)) {
        text.append(sentence.get(CoreAnnotations.TextAnnotation.class));
      } else {
        // If there is no text in the sentence, fake it as best as we can
        if (text.length() > 0) {
          text.append('\n');
        }
        text.append(SentenceUtils.listToString(sentenceTokens));
      }
    }
    this.set(CoreAnnotations.TokensAnnotation.class, tokens);
    this.set(CoreAnnotations.TextAnnotation.class, text.toString());
  }

  // ==================
  // Old Deprecated API. This shouldn't be used. It's currently only used in old RTE code.
  // ==================

  @Deprecated
  public Annotation() {
    super(12);
  }

}
