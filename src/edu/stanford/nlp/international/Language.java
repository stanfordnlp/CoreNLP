package edu.stanford.nlp.international;

import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.util.StringUtils;

import java.util.Arrays;

/**
 * Constants and parameters for multilingual NLP (primarily, parsing).
 *
 * @author Spence Green (original Languages class for parsing)
 * @author Gabor Angeli (factor out Language enum)
 */
public enum Language {
  Any(              new EnglishTreebankParserParams()),
  Arabic(           new ArabicTreebankParserParams()),
  Chinese(          new ChineseTreebankParserParams()),
  English(          new EnglishTreebankParserParams(){{ setGenerateOriginalDependencies(true); }}),
  German(           new NegraPennTreebankParserParams()),
  French(           new FrenchTreebankParserParams()),
  Hebrew(           new HebrewTreebankParserParams()),
  Spanish(          new SpanishTreebankParserParams()),
  UniversalEnglish( new EnglishTreebankParserParams()),
  Unknown(          new EnglishTreebankParserParams());

  public static final String langList = StringUtils.join(Arrays.asList(Language.values()), " ");

  public final TreebankLangParserParams params;

  Language(TreebankLangParserParams params) {
    this.params = params;
  }

  /**
   * Returns whether these two languages can be considered compatible with each other.
   * Mostly here to handle the "Any" language value.
   */
  public boolean compatibleWith(Language other) {
    if (this == Any) { return true; }
    if (other == Any) { return true; }
    return false;
  }
}
