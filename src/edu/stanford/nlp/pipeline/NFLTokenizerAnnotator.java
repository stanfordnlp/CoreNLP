package edu.stanford.nlp.pipeline;

import java.util.List;

import edu.stanford.nlp.ie.machinereading.domains.nfl.NFLReader.NFLTokenizer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

/**
 * Implements a series of tokenization post-processing rules, necessary for the NFL domain
 * This will be (mostly) unnecessary when the LDC-compliant tokenizer will be supported
 * @author Mihai
 */
public class NFLTokenizerAnnotator implements Annotator {
  
  private NFLTokenizer tokenizer;
  
  public NFLTokenizerAnnotator() {
    tokenizer = new NFLTokenizer();
  }

  public void annotate(Annotation annotation) {
    if (annotation.has(CoreAnnotations.TokensAnnotation.class)) {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      tokens = tokenizer.postprocess(tokens);
      annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);
    } else {
      throw new RuntimeException("unable to find tokens in annotation: " + annotation);
    }
  }

}
