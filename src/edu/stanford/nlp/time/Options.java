package edu.stanford.nlp.time;

import java.util.*;

import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Various options for using time expression extractor
 *
 * @author Angel Chang
 */
public class Options {

  public enum RelativeHeuristicLevel { NONE, BASIC, MORE }

  // Whether to mark time ranges like from 1991 to 1992 as one timex
  // or leave it separate
  public boolean markTimeRanges = false;
  // Whether include non timex3 temporal expressions
  boolean restrictToTimex3 = false;
  // Heuristics for determining relative time
  // level 1 = no heuristics (default)
  // level 2 = basic heuristics taking into past tense
  // level 3 = more heuristics with since/until
  RelativeHeuristicLevel teRelHeurLevel = RelativeHeuristicLevel.NONE;
  // Include nested time expressions
  boolean includeNested = false;
  // Create range for all temporals and include range attribute in timex annotation
  boolean includeRange = false;
  // Look for document date in the document text (if not provided)
  boolean searchForDocDate = false;
  // language for SUTime
  public String language = "english";
  public static final HashMap<String,String> languageToRulesFiles = new HashMap<>();
  // TODO: Add default country for holidays and default time format
  // would want a per document default as well
  String grammarFilename = null;
  Env.Binder[] binders = null;


  static final String DEFAULT_GRAMMAR_FILES = "edu/stanford/nlp/models/sutime/defs.sutime.txt,edu/stanford/nlp/models/sutime/english.sutime.txt,edu/stanford/nlp/models/sutime/english.holidays.sutime.txt";
  static final String DEFAULT_BRITISH_GRAMMAR_FILES =
      "edu/stanford/nlp/models/sutime/defs.sutime.txt,edu/stanford/nlp/models/sutime/british.sutime.txt,edu/stanford/nlp/models/sutime/english.sutime.txt,edu/stanford/nlp/models/sutime/english.holidays.sutime.txt";
  static final String DEFAULT_SPANISH_GRAMMAR_FILES =
      "edu/stanford/nlp/models/sutime/defs.sutime.txt,edu/stanford/nlp/models/sutime/spanish.sutime.txt";
  static final String[] DEFAULT_BINDERS = { "edu.stanford.nlp.time.JollyDayHolidays" };
  //static final String[] DEFAULT_BINDERS = { };

  static {
    languageToRulesFiles.put("english", DEFAULT_GRAMMAR_FILES);
    languageToRulesFiles.put("en", DEFAULT_GRAMMAR_FILES);
    languageToRulesFiles.put("british", DEFAULT_BRITISH_GRAMMAR_FILES);
    languageToRulesFiles.put("spanish", DEFAULT_SPANISH_GRAMMAR_FILES);
    languageToRulesFiles.put("es", DEFAULT_SPANISH_GRAMMAR_FILES);
  }

  boolean verbose = false;


  public Options() { }

  public Options(String name, Properties props) {
    includeRange = PropertiesUtils.getBool(props, name + ".includeRange",
        includeRange);
    markTimeRanges = PropertiesUtils.getBool(props, name + ".markTimeRanges",
        markTimeRanges);
    includeNested = PropertiesUtils.getBool(props, name + ".includeNested",
        includeNested);
    restrictToTimex3 = PropertiesUtils.getBool(props, name + ".restrictToTimex3",
        restrictToTimex3);
    teRelHeurLevel = RelativeHeuristicLevel.valueOf(
        props.getProperty(name + ".teRelHeurLevel",
            teRelHeurLevel.toString()));
    verbose = PropertiesUtils.getBool(props, name + ".verbose", verbose);

    // set default rules by SUTime language
    language = props.getProperty(name + ".language", language);
    if (!languageToRulesFiles.keySet().contains(language))
      language = "english";
    grammarFilename = languageToRulesFiles.get(language);

    // override if rules are set by properties
    grammarFilename = props.getProperty(name + ".rules", grammarFilename);


    searchForDocDate = PropertiesUtils.getBool(props, name + ".searchForDocDate", searchForDocDate);

    String binderProperty = props.getProperty(name + ".binders");
    int nBinders;
    String[] binderClasses;
    if (binderProperty == null) {
      nBinders = DEFAULT_BINDERS.length;
      binderClasses = DEFAULT_BINDERS;
    } else {
      nBinders = PropertiesUtils.getInt(props, name + ".binders", 0);
      binderClasses = new String[nBinders];
      for (int i = 0; i < nBinders; ++i) {
        String binderPrefix = name + ".binder." + (i + 1);
        binderClasses[i] = props.getProperty(binderPrefix);
      }
    }
    if (nBinders > 0 && System.getProperty("STS") == null) {
      binders = new Env.Binder[nBinders];
      for (int i = 0; i < nBinders; i++) {
        int bi = i+1;
        String binderPrefix = name + ".binder." + bi;
        try {
          Class<Env.Binder> binderClass = (Class<Env.Binder>) Class.forName(binderClasses[i]);
          binderPrefix = binderPrefix + '.';
          binders[i] = binderClass.getDeclaredConstructor().newInstance();
          binders[i].init(binderPrefix, props);
        } catch (Exception ex) {
          throw new RuntimeException("Error initializing binder " + bi, ex);
        }
      }
    }
  }
}
