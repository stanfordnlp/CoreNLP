package edu.stanford.nlp.international;

import edu.stanford.nlp.parser.lexparser.ArabicTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.FrenchTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.HebrewTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.SpanishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;

/**
 * Constants and parameters for multilingual parsing.
 *  
 * @author Spence Green
 *
 */
public class Languages {

  private Languages() {}

  public static enum Language {Arabic,Chinese,English,German,French,Hebrew,Spanish}
  
  private static String langList;
  static {
    StringBuilder sb = new StringBuilder();
    for(Language lang : Language.values()) {
      sb.append(lang.toString());
      sb.append(" ");
    }
    langList = sb.toString().trim();
  }

  public static String listOfLanguages() {
    return langList;
  }

  public static TreebankLangParserParams getLanguageParams(String lang) {
    return getLanguageParams(Language.valueOf(lang));
  }

  public static TreebankLangParserParams getLanguageParams(Language lang) {
    TreebankLangParserParams tlpp; // initialized below
    switch(lang) {
    case Arabic:
      tlpp = new ArabicTreebankParserParams();
      break;
   
    case Chinese:
      tlpp = new ChineseTreebankParserParams();
      break;

    case German:
      tlpp = new NegraPennTreebankParserParams();
      break;

    case French:
      tlpp = new FrenchTreebankParserParams();
      break;

    case Hebrew:
      tlpp = new HebrewTreebankParserParams();
      break;

    case Spanish:
      tlpp = new SpanishTreebankParserParams();
      break;

    default:
      tlpp = new EnglishTreebankParserParams();
    }
    return tlpp;
  }
}
