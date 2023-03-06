package edu.stanford.nlp.international;

import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Constants and parameters for multilingual NLP (primarily, parsing).
 *
 * @author Spence Green (original Languages class for parsing)
 * @author Gabor Angeli (factor out Language enum)
 */

public enum Language {
  Any(              new EnglishTreebankParserParams()),
  Afrikaans(        treebankForLanguage("Afrikaans")),
  AncientGreek(     treebankForLanguage("AncientGreek")),
  Arabic(           new ArabicTreebankParserParams()),
  Armenian(         treebankForLanguage("Armenian")),
  Basque(           treebankForLanguage("Basque")),
  Breton(           treebankForLanguage("Breton")),
  Bulgarian(        treebankForLanguage("Bulgarian")),
  Buryat(           treebankForLanguage("Buryat")),
  Catalan(          treebankForLanguage("Catalan")),
  Croatian(         treebankForLanguage("Croatian")),
  Chinese(          new ChineseTreebankParserParams()),
  Czech(            treebankForLanguage("Czech")),
  Danish(           treebankForLanguage("Danish")),
  Dutch(            treebankForLanguage("Dutch")),
  English(          new EnglishTreebankParserParams(){{ setGenerateOriginalDependencies(true); }}),
  Estonian(         treebankForLanguage("Estonian")),
  Faroese(          treebankForLanguage("Faroese")),
  Finnish(          treebankForLanguage("Finnish")),
  Galician(         treebankForLanguage("Galician")),
  German(           new NegraPennTreebankParserParams()),
  Gothic(           treebankForLanguage("Gothic")),
  Greek(            treebankForLanguage("Greek")),
  French(           new FrenchTreebankParserParams()),
  Hebrew(           new HebrewTreebankParserParams()),
  Hindi(            treebankForLanguage("Hindi")),
  Hungarian(        new HungarianTreebankParserParams()),
  Indonesian(       treebankForLanguage("Indonesian")),
  Italian(          new ItalianTreebankParserParams()),
  Irish(            treebankForLanguage("Irish")),
  Kazakh(           treebankForLanguage("Kazakh")),
  Korean(           treebankForLanguage("Korean")),
  Kurmanji(         treebankForLanguage("Kurmanji")),
  Latin(            treebankForLanguage("Latin")),
  Latvian(          treebankForLanguage("Latvian")),
  Naija(            treebankForLanguage("Naija")),
  NorthSami(        treebankForLanguage("NorthSami")),
  Norwegian(        treebankForLanguage("Norwegian")),
  OldChurchSlavonic( treebankForLanguage("OldChurchSlavonic")),
  OldFrench(        treebankForLanguage("OldFrench")),
  Persian(          treebankForLanguage("Persian")),
  Polish(           treebankForLanguage("Polish")),
  Portuguese(       treebankForLanguage("Portuguese")),
  Romanian(         treebankForLanguage("Romanian")),
  Russian(          treebankForLanguage("Russian")),
  Serbian(          treebankForLanguage("Serbian")),
  Slovak(           treebankForLanguage("Slovak")),
  Slovenian(        treebankForLanguage("Slovenian")),
  Spanish(          new SpanishTreebankParserParams()),
  Swedish(          treebankForLanguage("Swedish")),
  Japanese(         treebankForLanguage("Japanese")),
  Thai(             treebankForLanguage("Thai")),
  Turkish(          treebankForLanguage("Turkish")),
  Ukrainian(        treebankForLanguage("Ukrainian")),
  UniversalChinese( new ChineseTreebankParserParams()),
  UniversalEnglish( new EnglishTreebankParserParams()),
  Unknown(          new EnglishTreebankParserParams()),
  UpperSorbian(     treebankForLanguage("UpperSorbian")),
  Urdu(             treebankForLanguage("Urdu")),
  Uyghur(           treebankForLanguage("Uyghur")),
  Vietnamese(       treebankForLanguage("Vietnamese"));

  public static final String langList = StringUtils.join(Arrays.asList(Language.values()), " ");

  public static final Map<String, Language> lowerLangNames = new HashMap() {{
    for (Language lang : Language.values()) {
      String lowerLang = lang.name().toLowerCase();
      if (containsKey(lowerLang)) {
        throw new AssertionError("Duplicate Language names: " + lang.name() + " and " + lowerLangNames.get(lowerLang).name());
      }
      put(lowerLang, lang);
    }
  }};

  /** return a case insensitive search with no exceptions (unknown language becomes null) */
  public static Language valueOfSafe(String language) {
    return lowerLangNames.get(language.toLowerCase());
  }

  public final TreebankLangParserParams params;

  Language(TreebankLangParserParams params) {
    this.params = params;
  }

  public static TreebankLangParserParams treebankForLanguage(String languageName) {
    try {
      Class clazz = Class.forName("edu.stanford.nlp.parser.lexparser."+languageName+"TreebankParserParams");
      return (TreebankLangParserParams) clazz.getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException | NoClassDefFoundError | InstantiationException |
        IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
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
