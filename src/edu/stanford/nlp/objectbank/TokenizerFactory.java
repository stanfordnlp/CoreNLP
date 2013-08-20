package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.process.Tokenizer;

import java.io.Reader;

/**
 * A TokenizerFactory is used to convert a java.io.Reader
 * into a Tokenizer (or an Iterator) over the Objects represented by the text
 * in the java.io.Reader.  It's mainly a convenience, since you could cast
 * down anyway.
 *
 * <i>Note:</i> A TokenizerFactory should also provide a static method: <br><code>
 * public static TokenizerFactory&lt;? extends HasWord&gt; newTokenizerFactory();
 * </code><br>
 * This is expected by certain JavaNLP code which wants to produce a
 * TokenizerFactory by reflection.
 * 
 * @author Christopher Manning
 *
 * @param <T> The type of the tokens returned by the Tokenizer
 */
public interface TokenizerFactory<T> extends IteratorFromReaderFactory<T> {

  public Tokenizer<T> getTokenizer(Reader r);
  
  public Tokenizer<T> getTokenizer(Reader r, String extraOptions);
  
  public void setOptions(String options);
}
