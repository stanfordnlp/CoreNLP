package edu.stanford.nlp.process;


import java.util.Iterator;
import java.util.List;

/**
 * Tokenizers break up text into individual Objects. These objects may be
 * Strings, Words, or other Objects.  A Tokenizer extends the Iterator
 * interface, but provides a lookahead operation <code>peek()</code>.  An
 * implementation of this interface is expected to have a constructor that
 * takes a single argument, a Reader.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 */
public interface Tokenizer<T> extends Iterator<T> {

  /**
   * Returns the next token from this Tokenizer.
   *
   * @return the next token in the token stream.
   * @throws java.util.NoSuchElementException
   *          if the token stream has no more tokens.
   */
  @Override
  public T next();

  /**
   * Returns <code>true</code> if and only if this Tokenizer has more elements.
   */
  @Override
  public boolean hasNext();

  /**
   * Removes from the underlying collection the last element returned by
   * the iterator.  This is an optional operation for Iterators - a
   * Tokenizer normally would not support it. This method can be called
   * only once per call to next.
   */
  @Override
  public void remove();

  /**
   * Returns the next token, without removing it, from the Tokenizer, so
   * that the same token will be again returned on the next call to
   * next() or peek().
   *
   * @return the next token in the token stream.
   * @throws java.util.NoSuchElementException
   *          if the token stream has no more tokens.
   */
  public T peek();

  /**
   * Returns all tokens of this Tokenizer as a List for convenience.
   *
   * @return A list of all the tokens
   */
  public List<T> tokenize();

}
