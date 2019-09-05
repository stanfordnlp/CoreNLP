package edu.stanford.nlp.international.german.process;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.*;

import java.util.*;
import java.util.function.*;


/**
 * Class for mapping CoNLL 2018 tokenized German text to German NER tokenization
 * and vice versa.  The CoNLL 2018 German tokenization splits words such as
 * "CDU-Parlamentarier" into "CDU", "-", and "Parlamentarier".  This causes
 * a performance drop for German NER of several F1 points, so this module will
 * facilitate retokenizing CoNLL 2018 tokenized text to match our internal German
 * NER training data which does not split on hyphens.  Post classification this
 * module can restore the tokenization to the CoNLL 2018 style.
 */

public class GermanNERCoreLabelProcessor extends CoreLabelProcessor {

  /** Check that after() is not null and the empty string **/
  public Function<CoreLabel, Boolean> afterIsEmpty = tok ->
      tok.containsKey(CoreAnnotations.AfterAnnotation.class) && tok.after().equals("");

  /**
   * merge the contents of two tokens
   **/
  public static void mergeTokens(CoreLabel token, CoreLabel nextToken) {
    // NOTE: right now the merged tokens get the part-of-speech tag of the first token
    token.setWord(token.word() + nextToken.word());
    token.setAfter(nextToken.after());
    token.setEndPosition(nextToken.endPosition());
    token.setValue(token.word()+"-"+token.sentIndex());
  }

  @Override
  public List<CoreLabel> process(List<CoreLabel> tokens) {
    List<CoreLabel> processedTokens = new ArrayList<CoreLabel>();
    for (CoreLabel currToken : tokens) {
      CoreLabel processedToken = new CoreLabel(currToken);
      CoreLabel lastProcessedToken =
          processedTokens.size() > 0 ? processedTokens.get(processedTokens.size() - 1) : null;
      if (lastProcessedToken != null && afterIsEmpty.apply(lastProcessedToken) && currToken.word().equals("-")) {
        mergeTokens(lastProcessedToken, currToken);
      } else if (lastProcessedToken != null && lastProcessedToken.word().endsWith("-") &&
          afterIsEmpty.apply(lastProcessedToken)) {
        mergeTokens(lastProcessedToken, currToken);
      } else {
        processedTokens.add(processedToken);
      }
    }
    return processedTokens;
  }

  @Override
  public List<CoreLabel> restore(List<CoreLabel> originalTokens, List<CoreLabel> processedTokens) {
    List<CoreLabel> restoredTokens = new ArrayList<>();
    for (int i = 0, j = 0 ; i < processedTokens.size() ; i++) {
      // for each processed token, loop through the 1 or more original tokens
      // that correspond to the merged token
      CoreLabel processedToken = processedTokens.get(i);
      while (j < originalTokens.size()) {
        CoreLabel originalToken = originalTokens.get(j);
        if (originalToken.beginPosition() >= processedToken.endPosition())
          break;
        // copy most info from processed token (such as NER tag)
        CoreLabel restoredToken = new CoreLabel(processedToken);
        // copy text and character info from original token
        restoredToken.setWord(originalToken.word());
        restoredToken.setOriginalText(originalToken.originalText());
        restoredToken.setBeginPosition(originalToken.beginPosition());
        restoredToken.setEndPosition(originalToken.endPosition());
        restoredToken.set(CoreAnnotations.TokenBeginAnnotation.class, originalToken.get(
            CoreAnnotations.TokenBeginAnnotation.class));
        restoredToken.set(CoreAnnotations.TokenEndAnnotation.class, originalToken.get(
            CoreAnnotations.TokenEndAnnotation.class));
        restoredToken.setAfter(originalToken.after());
        restoredToken.setBefore(originalToken.before());
        restoredToken.setIndex(j+1);
        restoredToken.setValue(restoredToken.word());
        // add restored token to list
        restoredTokens.add(restoredToken);
        // move on to next original token
        j++;
      }
    }
    return restoredTokens;
  }

}
