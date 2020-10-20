package edu.stanford.nlp.process;

import edu.stanford.nlp.ling.CoreLabel;

import java.util.*;

/**
 * Abstract class for processing a {@code List<CoreLabel>}.
 * Example use case is merging on hyphens for German NER (process) and then
 * restoring after classification (restore).
 */

public abstract class CoreLabelProcessor {

  /**
   * Alter the tokenization of list of tokens (e.g. map split on hyphen to don't split)
   **/
  public abstract List<CoreLabel> process(List<CoreLabel> tokens);

  /**
   * Undo the tokenization changes of process, maintaining any tagging
   **/
  public abstract List<CoreLabel> restore(List<CoreLabel> originalTokens, List<CoreLabel> processedTokens);

}
