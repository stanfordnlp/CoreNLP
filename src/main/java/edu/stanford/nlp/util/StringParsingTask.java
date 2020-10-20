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
 */
public abstract class StringParsingTask<E>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(StringParsingTask.class);
  
  // This class represents a parser working on a specific string.  We
  // construct from a specific string in order 
  protected String s;
  protected int index = 0;
  protected boolean isEOF = false;     // true if we tried to read past end
    
  /**
   * Constructs a new <code>StringParsingTask</code> from the specified
   * <code>String</code>.  Derived class constructors should be sure to
   * call <code>super(s)</code>!
   */
  public StringParsingTask(String s) {
    this.s = s;
    index = 0;
  }
    
  /**
   * Parses the <code>String</code> associated with this
   * <code>StringParsingTask</code> and returns a object of type
   * <code>E</code>.
   */
  public abstract E parse();

    
  // ---------------------------------------------------------------------

  /**
   * Reads characters until {@link #isWhiteSpace(char) isWhiteSpace(ch)}or
   * {@link #isPunct(char) isPunct(ch)} or {@link #isEOF()}.  You may need
   * to override the definition of {@link #isPunct(char) isPunct(ch)} to
   * get this to work right.
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

  protected void readLeftParen() {
    // System.out.println("Read left.");
    readWhiteSpace();
    char ch = read();
    if (!isLeftParen(ch))
      throw new ParserException("Expected left paren!");
  }

  protected void readRightParen() {
    // System.out.println("Read right.");
    readWhiteSpace();
    char ch = read();
    if (!isRightParen(ch)) 
      throw new ParserException("Expected right paren!");
  }

  protected void readDot() {
    readWhiteSpace();
    if (isDot(peek())) read();
  }

  protected void readWhiteSpace() {
    char ch = read();
    while (isWhiteSpace(ch) && !isEOF()) {
      ch = read();
    }
    unread();
  }

  // .....................................................................

  protected char read() {
    if (index >= s.length() || index < 0) {
      isEOF = true;
      return ' ';                     // arbitrary
    }
    return s.charAt(index++);
  }
  
  protected void unread() {
    index--;
  }
  
  protected char peek() {
    char ch = read();
    unread();
    return ch;
  }


  // -----------------------------------------------------------------------

  protected boolean isEOF() {
    return isEOF;
  }

  protected boolean isWhiteSpace(char ch) {
    return (ch == ' ' || ch == '\t' || ch == '\f' || ch == '\r' || ch == '\n');
  }

  protected boolean isPunct(char ch) {
    return 
      isLeftParen(ch) ||
      isRightParen(ch);
  }

  protected boolean isLeftParen(char ch) {
    return ch == '(';
  }

  protected boolean isRightParen(char ch) {
    return ch == ')';
  }

  protected boolean isDot(char ch) {
    return ch == '.';
  }


  // exception class -------------------------------------------------------

  public static class ParserException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ParserException(Exception e)    { super(e); }
    public ParserException(String message) { super(message); }
  }

}  
