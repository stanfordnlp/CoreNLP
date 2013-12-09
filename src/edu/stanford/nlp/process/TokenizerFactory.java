package edu.stanford.nlp.process;

import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

import java.io.Reader;

/**
 * A TokenizerFactory is used to convert a java.io.Reader into a Tokenizer
 * (an extension of Iterator) over objects of type T represented by the text
 * in the java.io.Reader.  It's mainly a convenience, since you could cast
 * down anyway.
 *
 * <i>IMPORTANT NOTE:</i><br/>
 *
 * A TokenizerFactory should also provide two static methods: <br>
 * {@code public static TokenizerFactory<? extends HasWord> newTokenizerFactory(); }
 * {@code public static TokenizerFactory<Word> newWordTokenizerFactory(String options); }
 * <br/>
 * These are expected by certain JavaNLP code (e.g., LexicalizedParser),
 * which wants to produce a TokenizerFactory by reflection.
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
