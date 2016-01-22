package edu.stanford.nlp.pipeline;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.util.Timing;

/**
 * This is an abstract base class for any annotator class that uses a
 * Tokenizer to split TextAnnotation into TokensAnnotation.
 * <br>
 * The only method the subclass needs to define is a method
 * that produces a Tokenizer of CoreLabels, which is then
 * used to split the TextAnnotation of the given Annotation
 * into CoreLabels.
 * <br>
 * In order to maintain thread safety, getTokenizer should return
 * a thread-safe tokenizer.  In the case of tokenizers built from
 * .flex files, that will mean new tokenizers for each call.
 *
 * @author Jenny Finkel
 * @author John Bauer
 */
abstract public class TokenizerAnnotator implements Annotator {

  private final boolean VERBOSE;

  public TokenizerAnnotator(boolean verbose) {
    VERBOSE = verbose;
  }

  /**
   * Abstract: returns a tokenizer
   */
  abstract Tokenizer<CoreLabel> getTokenizer(Reader r);

  /**
   * Does the actual work of splitting TextAnnotation into CoreLabels,
   * which are then attached to the TokensAnnotation.
   */
  public void annotate(Annotation annotation) {
    Timing timer = null;

    if (VERBOSE) {
      timer = new Timing();
      timer.start();
      System.err.print("PTB tokenizing ... ");
    }

    if (annotation.has(CoreAnnotations.TextAnnotation.class)) {
      String text = annotation.get(CoreAnnotations.TextAnnotation.class);
      Reader r = new StringReader(text);  // don't wrap in BufferedReader.  It gives you nothing for in memory String unless you need the readLine() method!
      List<CoreLabel> tokens = getTokenizer(r).tokenize();
      // cdm 2010-05-15: This is now unnecessary, as it is done in CoreLabelTokenFactory
      // for (CoreLabel token: tokens) {
      //   token.set(CoreAnnotations.TextAnnotation.class, token.get(CoreAnnotations.TextAnnotation.class));
      // }
      annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);
      if (VERBOSE) {
        timer.stop("done.");
        System.err.println("output: "+annotation.get(CoreAnnotations.TokensAnnotation.class)+"\n");
      }
    } else {
      throw new RuntimeException("unable to find text in annotation: " + annotation);
    }
  }


  @Override
  public Set<Requirement> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }
}
