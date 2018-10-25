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
  Russian(          treebankForLanguage("Russian")),
  Spanish(          new SpanishTreebankParserParams()),
  UniversalChinese( new ChineseTreebankParserParams()),
  UniversalEnglish( new EnglishTreebankParserParams()),
  Unknown(          new EnglishTreebankParserParams());

  public static final String langList = StringUtils.join(Arrays.asList(Language.values()), " ");

  public final TreebankLangParserParams params;

  Language(TreebankLangParserParams params) {
    this.params = params;
  }

  public static TreebankLangParserParams treebankForLanguage(String languageName) {
    try {
      Class clazz = Class.forName(languageName+"TreebankParserParams");
      return (TreebankLangParserParams) clazz.newInstance();
    } catch (ClassNotFoundException | NoClassDefFoundError | java.lang.InstantiationException |
        java.lang.IllegalAccessException ex) {
      return null;
    }
  }

  /**
   * Returns whether these two languages can be considered compatible with each other.
   * Mostly here to handle the "Any" language value.
   */
  public boolean compatibleWith(Language other) {
    return this == other || this == Any || other == Any;
  }
}
