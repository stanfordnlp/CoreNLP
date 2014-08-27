package edu.stanford.nlp.pipeline;

import java.io.Reader;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;

/**
 * This class will PTB tokenize the input.  It assumes that the original
 * String is under the CoreAnnotations.TextAnnotation field
 * and it will add the output from the
 * InvertiblePTBTokenizer ({@code List<CoreLabel>}) under
 * CoreAnnotation.TokensAnnotation.
 *
 * @author Jenny Finkel
 * @author Christopher Manning
 */
public class PTBTokenizerAnnotator extends TokenizerAnnotator {

  private final TokenizerFactory<CoreLabel> factory;

  public static final String DEFAULT_OPTIONS = "invertible,ptb3Escaping=true";

  public PTBTokenizerAnnotator() {
    this(true);
  }

  public PTBTokenizerAnnotator(boolean verbose) {
   this(verbose, DEFAULT_OPTIONS);
  }

  public PTBTokenizerAnnotator(boolean verbose, String options) {
    super(verbose);
    factory = PTBTokenizer.factory(new CoreLabelTokenFactory(), options);
  }

  @Override
  public Tokenizer<CoreLabel> getTokenizer(Reader r) {
    return factory.getTokenizer(r);
  }

}
