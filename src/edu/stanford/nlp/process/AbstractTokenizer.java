package edu.stanford.nlp.process;


import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An abstract tokenizer.  Tokenizers extending AbstractTokenizer need only
 * implement the <code>getNext()</code> method. This implementation does not
 * allow null tokens, since
 * null is used in the protected nextToken field to signify that no more
 * tokens are available.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 */

public abstract class AbstractTokenizer<T> implements Tokenizer<T> {

  protected T nextToken; // = null;

  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  protected abstract T getNext();

  /**
   * Returns the next token from this Tokenizer.
   *
   * @return the next token in the token stream.
   * @throws java.util.NoSuchElementException
   *          if the token stream has no more tokens.
   */
  @Override
  public T next() {
    if (nextToken == null) {
      nextToken = getNext();
    }
    T result = nextToken;
    nextToken = null;
    if (result == null) {
      throw new NoSuchElementException();
    }
    return result;
  }

  /**
   * Returns <code>true</code> if this Tokenizer has more elements.
   */
  @Override
  public boolean hasNext() {
    if (nextToken == null) {
      nextToken = getNext();
    }
    return nextToken != null;
  }

  /**
   * This is an optional operation, by default not supported.
   */
  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  /**
   * This is an optional operation, by default supported.
   *
   * @return The next token in the token stream.
   * @throws java.util.NoSuchElementException
   *          if the token stream has no more tokens.
   */
  @Override
  public T peek() {
    if (nextToken == null) {
      nextToken = getNext();
    }
    if (nextToken == null) {
      throw new NoSuchElementException();
    }
    return nextToken;
  }

  /**
   * Returns text as a List of tokens.
   *
   * @return A list of all tokens remaining in the underlying Reader
   */
  @Override
  public List<T> tokenize() {
    // System.out.println("tokenize called");
    List<T> result = new ArrayList<>();
    while (hasNext()) {
      result.add(next());
    }
    return result;
  }

}
