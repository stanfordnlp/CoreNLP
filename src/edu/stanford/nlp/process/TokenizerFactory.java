package edu.stanford.nlp.process;

import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

import java.io.Reader;

/**
 * A TokenizerFactory is a factory that can build a Tokenizer (an extension of Iterator)
 * from a java.io.Reader.
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

  Tokenizer<T> getTokenizer(Reader r);

  Tokenizer<T> getTokenizer(Reader r, String extraOptions);

  void setOptions(String options);

}
