package edu.stanford.nlp.pipeline;

import java.io.Reader;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.WhitespaceTokenizer;

/**
 * This annotator uses a WhitespaceTokenizer to split TextAnnotations
 * into TokensAnnotations.
 * <br>
 * If either the property EOL_PROPERTY or the property
 * NEWLINE_SPLITTER_PROPERTY defined in StanfordCoreNLP are present
 * and set to true, newlines are returned as tokens.  In practice,
 * either will mean the newlines get removed by the sentence splitter.
 *
 * @author John Bauer
 */
public class WhitespaceTokenizerAnnotator extends TokenizerAnnotator {

  private final TokenizerFactory<CoreLabel> factory;

  public static final String EOL_PROPERTY = "tokenize.keepeol";

  public WhitespaceTokenizerAnnotator(Properties props) {
    super(false);
    boolean eolIsSignificant =
      Boolean.valueOf(props.getProperty(EOL_PROPERTY, "false"));
    eolIsSignificant =
      (eolIsSignificant ||
       Boolean.valueOf(props.getProperty
                       (StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false")));
    factory = new WhitespaceTokenizer.WhitespaceTokenizerFactory<CoreLabel>
              (new CoreLabelTokenFactory(), eolIsSignificant);
  }

  @Override
  public Tokenizer<CoreLabel> getTokenizer(Reader r) {
    return factory.getTokenizer(r);
  }
}
