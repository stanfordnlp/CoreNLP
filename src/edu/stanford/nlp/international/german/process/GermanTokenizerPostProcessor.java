package edu.stanford.nlp.international.german.process;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.*;

import java.util.*;
import java.util.function.*;

/**
 * German tokenization is handled by PTBLexer.  This module handles special cases for German.
 * Currently the German tokenization in Stanford CoreNLP should match the UD standard, as
 * seen in the CoNLL-2018 dependency parsing data for example.
 *
 * Two special cases for now:
 *   - rebuild ordinals
 *   - rebuild numeric ranges
 *
 * For instance in the sentence "Der Vertrag läuft offiziell bis zum 31. Dezember 1992."
 * the "31." should be a token.  This module will merge "31" and "." back together.
 *
 * Also "1989" "-" "1990" should be merged back to "1989-1990"
 */

public class GermanTokenizerPostProcessor extends CoreLabelProcessor {

  /** Check that after() is not null and the empty string **/
  public Function<CoreLabel, Boolean> afterIsEmpty = tok ->
      tok.containsKey(CoreAnnotations.AfterAnnotation.class) && tok.after().equals("");

  public HashSet<String> ordinalPredictingWords = new HashSet<>(Arrays.asList(
      "Januar","Februar","März","April","Mai","Juni","Juli","August","September","Oktober","November","Dezember",
      "Jahrhundert"));

  public HashSet<String> germanAbbreviations = new HashSet<>(Arrays.asList(
      "bzw", "jap", "usw", "ca"));


  /**
   * merge the contents of two tokens
   **/
  public static void mergeTokens(CoreLabel token, CoreLabel nextToken) {
    token.setWord(token.word() + nextToken.word());
    token.setAfter(nextToken.after());
    token.setEndPosition(nextToken.endPosition());
    token.setValue(token.word()+"-"+token.sentIndex());
  }

  /**
   * Some people write umlauts as two characters instead of just one
   *<br>
   * German CoreNLP doesn't handle the two character versions correctly,
   * so here we condense it into the one character version
   */
  public static void condenseUmlauts(CoreLabel token) {
    String value = token.value();
    String updatedValue = condenseUmlauts(value);
    if (updatedValue != null) {
      token.setValue(updatedValue);
    }

    String word = token.word();
    String updatedWord = condenseUmlauts(word);
    if (updatedWord != null) {
      token.setWord(updatedWord);
    }
  }
    
  public static String condenseUmlauts(String value) {
    StringBuilder ns = null;
    for (int i = 0; i < value.length(); ++i) {
      final char cur = value.charAt(i);
      if ((int) cur == 776) {
        // this is the umlaut character
        if (ns == null) {
          ns = new StringBuilder(value.length());
          ns.append(value.substring(0, i));
        }
        final char prev = ns.length() == 0 ? ' ' : ns.charAt(ns.length() - 1);
        if (prev == 'a') {
          ns.setCharAt(ns.length() - 1, 'ä');
        } else if (prev == 'A') {
          ns.setCharAt(ns.length() - 1, 'Ä');
        } else if (prev == 'o') {
          ns.setCharAt(ns.length() - 1, 'ö');
        } else if (prev == 'O') {
          ns.setCharAt(ns.length() - 1, 'Ö');
        } else if (prev == 'u') {
          ns.setCharAt(ns.length() - 1, 'ü');
        } else if (prev == 'U') {
          ns.setCharAt(ns.length() - 1, 'Ü');
        } else {
          ns.append(cur);
        }
      } else {
        if (ns != null) {
          ns.append(cur);
        }
      }
    }
    if (ns != null) {
      return ns.toString();
    }
    return null;
  }

  @Override
  public List<CoreLabel> process(List<CoreLabel> tokens) {
    List<CoreLabel> processedTokens = new ArrayList<CoreLabel>();
    // process the first token
    if (tokens.size() > 0)
      processedTokens.add(tokens.get(0));
    // now always guaranteed one token on list of processed tokens
    for (int i = 1 ; i < tokens.size() ; i++) {
      CoreLabel currToken = tokens.get(i);
      CoreLabel lastProcessedToken = processedTokens.get(processedTokens.size()-1);
      // check last processed token has "" as after()
      boolean emptyAfter = afterIsEmpty.apply(lastProcessedToken);
      // perform ordinal merge (e.g. "21" "." "Dezember" -> "21." "Dezember")
      boolean ordinalMerge = lastProcessedToken.word().matches("[0-9]+") && currToken.word().equals(".")
          && i+1 < tokens.size() && ordinalPredictingWords.contains(tokens.get(i+1).word());
      // perform number to hyphen merge (e.g. "1989" "-" "1990" -> "1989-" "1990")
      boolean numberToHyphenMerge = lastProcessedToken.word().matches("[0-9]+") &&
          currToken.word().equals("-") && i+1 < tokens.size() && tokens.get(i+1).word().matches("[0-9]+");
      // perform hyphen to number merge (e.g. "1989-" "1990" -> "1989-1990")
      boolean hyphenToNumberMerge = lastProcessedToken.word().matches("[0-9]+-") &&
          currToken.word().matches("[0-9]+");
      // perform period to abbreviation (usw . -> usw.)
      boolean abbreviationMerge = germanAbbreviations.contains(lastProcessedToken.word())
          && currToken.word().equals(".");
      if (emptyAfter && (ordinalMerge || numberToHyphenMerge || hyphenToNumberMerge || abbreviationMerge)) {
        mergeTokens(lastProcessedToken, currToken);
      } else {
        processedTokens.add(currToken);
      }
    }

    for (CoreLabel label : processedTokens) {
      condenseUmlauts(label);
    }
    return processedTokens;
  }

  @Override
  public List<CoreLabel> restore(List<CoreLabel> originalTokens, List<CoreLabel> processedTokens) {
    return originalTokens;
  }

}
