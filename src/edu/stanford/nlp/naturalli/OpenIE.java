package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.util.Execution;

/**
 * A simple OpenIE system based on valid Natural Logic deletions of a sentence.
 *
 * @author Gabor Angeli
 */
public class OpenIE {




  public static void main(String[] args) {
    OpenIE extractor = new OpenIE();
    Execution.fillOptions(extractor, args);
  }
}
