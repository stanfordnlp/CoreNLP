package vn.hus.nlp.tokenizer.tokens;

/**
 * @author LE Hong Phuong, phuonglh@gmail.com
 * <p>
 * A word token. It is a lexer token with  
 * an additional information - the part of speech. But in general,
 * we do not use this information.
 * 
 */
public class WordToken extends TaggedWord {
	
	private final String pos;

	/** Instantiate a new token
	 * @param rule a lexical rule
	 * @param text the text
	 * @param line line location of the text in a file
	 * @param column column position of the text in a file
	 */
	public WordToken(LexerRule rule, String text, int line, int column) {
		super(rule, text, line, column);
		pos = null;
	}

	/** Instantiate a new token
	 * @param rule a lexical rule
	 * @param text the text
	 * @param line line location of the text in a file
	 * @param column column position of the text in a file
	 * @param pos parts-of-speech of the word token
	 */
	public WordToken(LexerRule rule, String text, int line, int column, String pos) {
		super(rule, text, line, column);
		this.pos = pos;
	}
	
	/**
	 * Get the parts-of-speech of the token
	 * @return parts-of-speech of the token
	 */
	public String getPOS() {
		return pos;
	}

	/**
	 * Return a string representation of a word token
	 */
	@Override
	public String toString() {
		return super.toString();
	}
}
