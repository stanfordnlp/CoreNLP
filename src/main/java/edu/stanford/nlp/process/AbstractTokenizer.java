package edu.stanford.nlp.process;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

// import edu.stanford.nlp.util.logging.Redwood;


/**
 * An abstract tokenizer. Tokenizers extending AbstractTokenizer need only
 * implement the {@code getNext()} method. This implementation does not
 * allow null tokens, since
 * null is used in the protected nextToken field to signify that no more
 * tokens are available.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 */

public abstract class AbstractTokenizer<T> implements Tokenizer<T>  {

  // /** A logger for this class */
  // private static final Redwood.RedwoodChannels log = Redwood.channels(AbstractTokenizer.class);

  /** For tokenizing carriage returns.
   *  We return this token as a representation of newlines when a tokenizer has the option
   *  {@code tokenizeNLs = true}. It is assumed that no tokenizer allows *NL* as a token.
   *  This is certainly true for PTBTokenizer-derived tokenizers, where the asterisks would
   *  become separate tokens.
   */
  public static final String NEWLINE_TOKEN = "*NL*";


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
   * Returns {@code true} if this Tokenizer has more elements.
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

  // Assume that the text we are being asked to tokenize is usually more than 10 tokens; save 5 reallocations
  private static final int DEFAULT_TOKENIZE_LIST_SIZE = 64;

  /**
   * Returns text as a List of tokens.
   *
   * @return A list of all tokens remaining in the underlying Reader
   */
  @Override
  public List<T> tokenize() {
    ArrayList<T> result = new ArrayList<>(DEFAULT_TOKENIZE_LIST_SIZE);
    while (hasNext()) {
      result.add(next());
    }
    // log.info("tokenize() produced " + result);
    // if it was tiny, reallocate small
    if (result.size() <= DEFAULT_TOKENIZE_LIST_SIZE / 4) {
      result.trimToSize();
    }
    return result;
  }

}
