package edu.stanford.nlp.util; 
import edu.stanford.nlp.util.logging.Redwood;


/**
 * An instantiation of this abstract class parses a <code>String</code> and
 * returns an object of type <code>E</code>.  It's called a
 * <code>StringParsingTask</code> (rather than <code>StringParser</code>)
 * because a new instance is constructed for each <code>String</code> to be
 * parsed.  We do this to be thread-safe: methods in
 * <code>StringParsingTask</code> share state information (e.g. current
 * string index) via instance variables.
 *
 * @author Bill MacCartney
 * @param <E> The type of object produced by parsing
 */
public abstract class StringParsingTask<E>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(StringParsingTask.class);
  
  // This class represents a parser working on a specific string.  We
  // construct from a specific string in order 
  /** The string being parsed. */
  protected String s;
  /** The index of the next character to read. */
  protected int index = 0;
  /** Whether a read past the end of the string has been attempted. */
  protected boolean isEOF = false;     // true if we tried to read past end
    
  /**
   * Constructs a new <code>StringParsingTask</code> from the specified
   * <code>String</code>.  Derived class constructors should be sure to
   * call <code>super(s)</code>!
   *
   * @param s The string to parse
   */
  public StringParsingTask(String s) {
    this.s = s;
    index = 0;
  }
    
  /**
   * Parses the <code>String</code> associated with this
   * <code>StringParsingTask</code> and returns a object of type
   * <code>E</code>.
   *
   * @return The result of parsing the string
   */
  public abstract E parse();

    
  // ---------------------------------------------------------------------

  /**
   * Reads characters until {@link #isWhiteSpace(char) isWhiteSpace(ch)}or
   * {@link #isPunct(char) isPunct(ch)} or {@link #isEOF()}.  You may need
   * to override the definition of {@link #isPunct(char) isPunct(ch)} to
   * get this to work right.
   *
   * @return The (interned) characters read, which may be empty
   */
  protected String readName() {
    readWhiteSpace();
    StringBuilder sb = new StringBuilder();
    char ch = read();
    while (!isWhiteSpace(ch) && !isPunct(ch) && !isEOF) {
      sb.append(ch);
      ch = read();
    }
    unread();
    // log.info("Read text: ["+sb+"]");
    return sb.toString().intern();
  }

  /**
   * Skips whitespace, then reads a Java identifier, as defined by
   * {@link Character#isJavaIdentifierStart} and {@link Character#isJavaIdentifierPart}.
   *
   * @return The (interned) identifier, or the empty string if the next character
   *         cannot start an identifier
   */
  protected String readJavaIdentifier() {
    readWhiteSpace();
    StringBuilder sb = new StringBuilder();
    char ch = read();
    if (Character.isJavaIdentifierStart(ch) && !isEOF) {
      sb.append(ch);
      ch = read();
      while (Character.isJavaIdentifierPart(ch) && !isEOF) {
        sb.append(ch);
        ch = read();
      }
    }
    unread();
    // log.info("Read text: ["+sb+"]");
    return sb.toString().intern();
  }

  // .....................................................................

  /**
   * Skips whitespace, then reads a left parenthesis.
   *
   * @throws ParserException If the next non-whitespace character is not a left paren
   */
  protected void readLeftParen() {
    // System.out.println("Read left.");
    readWhiteSpace();
    char ch = read();
    if (!isLeftParen(ch))
      throw new ParserException("Expected left paren!");
  }

  /**
   * Skips whitespace, then reads a right parenthesis.
   *
   * @throws ParserException If the next non-whitespace character is not a right paren
   */
  protected void readRightParen() {
    // System.out.println("Read right.");
    readWhiteSpace();
    char ch = read();
    if (!isRightParen(ch)) 
      throw new ParserException("Expected right paren!");
  }

  /**
   * Skips whitespace, then reads a dot if the next character is one.
   */
  protected void readDot() {
    readWhiteSpace();
    if (isDot(peek())) read();
  }

  /**
   * Skips over whitespace, leaving the next non-whitespace character unread.
   */
  protected void readWhiteSpace() {
    char ch = read();
    while (isWhiteSpace(ch) && !isEOF()) {
      ch = read();
    }
    unread();
  }

  // .....................................................................

  /**
   * Reads the next character.  If the index is outside the string, sets the
   * EOF flag, leaves the index unchanged, and returns a space.
   *
   * @return The next character, or a space at end of input
   */
  protected char read() {
    if (index >= s.length() || index < 0) {
      isEOF = true;
      return ' ';                     // arbitrary
    }
    return s.charAt(index++);
  }
  
  /**
   * Moves the index back by one character.  This happens even if the
   * previous {@link #read()} was at end of input and did not advance.
   */
  protected void unread() {
    index--;
  }
  
  /**
   * Returns the next character without consuming it, by calling
   * {@link #read()} and then {@link #unread()}.
   *
   * @return The next character, or a space at end of input
   */
  protected char peek() {
    char ch = read();
    unread();
    return ch;
  }


  // -----------------------------------------------------------------------

  /**
   * Returns whether a read past the end of the string has been attempted.
   *
   * @return The EOF flag
   */
  protected boolean isEOF() {
    return isEOF;
  }

  /**
   * Tests for a space, tab, form feed, carriage return, or newline.
   *
   * @param ch The character to test
   * @return Whether {@code ch} is whitespace
   */
  protected boolean isWhiteSpace(char ch) {
    return (ch == ' ' || ch == '\t' || ch == '\f' || ch == '\r' || ch == '\n');
  }

  /**
   * Tests for punctuation, which by default is a left or right paren.
   *
   * @param ch The character to test
   * @return Whether {@code ch} is punctuation
   */
  protected boolean isPunct(char ch) {
    return 
      isLeftParen(ch) ||
      isRightParen(ch);
  }

  /**
   * Tests for {@code '('}.
   *
   * @param ch The character to test
   * @return Whether {@code ch} is a left paren
   */
  protected boolean isLeftParen(char ch) {
    return ch == '(';
  }

  /**
   * Tests for {@code ')'}.
   *
   * @param ch The character to test
   * @return Whether {@code ch} is a right paren
   */
  protected boolean isRightParen(char ch) {
    return ch == ')';
  }

  /**
   * Tests for {@code '.'}.
   *
   * @param ch The character to test
   * @return Whether {@code ch} is a dot
   */
  protected boolean isDot(char ch) {
    return ch == '.';
  }


  // exception class -------------------------------------------------------

  /** Thrown when the string does not have the expected form. */
  public static class ParserException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /**
     * Wraps another exception.
     *
     * @param e The cause
     */
    public ParserException(Exception e)    { super(e); }
    /**
     * Creates an exception with the given message.
     *
     * @param message The detail message
     */
    public ParserException(String message) { super(message); }
  }

}  
