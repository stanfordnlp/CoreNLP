package edu.stanford.nlp.international.german.process;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.*;

import java.util.*;
import java.util.function.*;

/**
 * German tokenization is handled by PTBLexer.  This module handles ordinals.
 * For instance in the sentence "Der Vertrag läuft offiziell bis zum 31. Dezember 1992."
 * the "31." should be a token.  This module will merge "31" and "." back together.
 */

public class GermanTokenizerPostProcessor extends CoreLabelProcessor {

  /** Check that after() is not null and the empty string **/
  public Function<CoreLabel, Boolean> afterIsEmpty = tok ->
      tok.containsKey(CoreAnnotations.AfterAnnotation.class) && tok.after().equals("");

  public HashSet<String> ordinalPredictingWords = new HashSet<>(Arrays.asList(
      "Januar","Februar","März","April","Mai","Juni","Juli","August","September","Oktober","November","Dezember",
      "Jahrhundert"));

  /**
   * merge the contents of two tokens
   **/
  public static void mergeTokens(CoreLabel token, CoreLabel nextToken) {
    token.setWord(token.word() + nextToken.word());
    token.setAfter(nextToken.after());
    token.setEndPosition(nextToken.endPosition());
    token.setValue(token.word()+"-"+token.sentIndex());
  }

  @Override
  public List<CoreLabel> process(List<CoreLabel> tokens) {
    List<CoreLabel> processedTokens = new ArrayList<CoreLabel>();
    for (int i = 1 ; i < tokens.size() ; i++) {
      CoreLabel currToken = tokens.get(i);
      CoreLabel processedToken = new CoreLabel(currToken);
      CoreLabel lastProcessedToken =
          processedTokens.size() > 0 ? processedTokens.get(processedTokens.size() - 1) : null;
      boolean nextTokenPredictsOrdinal =
          i+1 < tokens.size() ? ordinalPredictingWords.contains(tokens.get(i+1).word()) : false ;
      if (lastProcessedToken != null && afterIsEmpty.apply(lastProcessedToken) && currToken.word().equals(".") &&
          nextTokenPredictsOrdinal) {
        mergeTokens(lastProcessedToken, currToken);
      } else {
        processedTokens.add(processedToken);
      }
    }
    return processedTokens;
  }

  @Override
  public List<CoreLabel> restore(List<CoreLabel> originalTokens, List<CoreLabel> processedTokens) {
    return originalTokens;
  }

}
