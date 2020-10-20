package edu.stanford.nlp.process;

import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

import java.io.Reader;

/**
 * A TokenizerFactory is a factory that can build a Tokenizer (an extension of Iterator)
 * from a java.io.Reader.
 *
 * <i>IMPORTANT NOTE:</i>
 *
 * A TokenizerFactory should also provide two static methods:
 *
 * {@code public static TokenizerFactory<? extends HasWord> newTokenizerFactory(); }
 * {@code public static TokenizerFactory<Word> newWordTokenizerFactory(String options); }
 *
 * These are expected by certain JavaNLP code (e.g., LexicalizedParser),
 * which wants to produce a TokenizerFactory by reflection.
 *
 * @author Christopher Manning
 *
 * @param <T> The type of the tokens returned by the Tokenizer
 */
public interface TokenizerFactory<T> extends IteratorFromReaderFactory<T> {

  /** Get a tokenizer for this reader.
   *
   *  @param r A Reader (which is assumed to already by buffered, if appropriate)
   *  @return A Tokenizer
   */
  Tokenizer<T> getTokenizer(Reader r);

  /** Get a tokenizer for this reader.
   *
   *  @param r A Reader (which is assumed to already by buffered, if appropriate)
   *  @param extraOptions Options for how this tokenizer should behave
   *  @return A Tokenizer
   */
  Tokenizer<T> getTokenizer(Reader r, String extraOptions);

  /** Sets default options for how tokenizers built from this factory should behave.
   *
   *  @param options Options for how this tokenizer should behave
   */
  void setOptions(String options);

}
