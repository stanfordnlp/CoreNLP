package vn.hus.nlp.tokenizer.tokens;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         This class represents a single token from an input stream, matched by
 *         a LexerRule.
 */
public class TaggedWord implements Comparable<TaggedWord> {
	/**
	 * A lexer rule
	 */
	private final LexerRule rule;
	/**
	 * The text
	 */
	private final String text;

	/**
	 * The line location of the text in the file
	 */
	private int line;

	/**
	 * The column location of the text in the file
	 */
	private int column;


	/**
	 * Create a LexerToken
	 * 
	 * @param rule
	 *            a rule
	 * @param text
	 *            the text
	 * @param line
	 *            the line location of the text in a file
	 * @param column
	 *            the column location of the text in a file
	 */
	public TaggedWord(LexerRule rule, String text, int line, int column) {
		this.rule = rule;
		this.text = text;
		this.line = line;
		this.column = column;
	}

	/**
	 * Create a lexer token from a text
	 * 
	 * @param text
	 *            a text
	 */
	public TaggedWord(String text) {
		this.rule = null;
		this.text = text;
		this.line = -1;
		this.column = -1;
	}

	
	/**
	 * Create a lexer token from a lexer rule and a text.
	 * @param rule
	 * @param text
	 */
	public TaggedWord(LexerRule rule, String text) {
		this.rule = rule;
		this.text = text;
		this.line = -1;
		this.column = -1;
	}
	
	/**
	 * Return the rule that matched this token
	 * 
	 * @return the rule that match this token
	 */
	public LexerRule getRule() {
		return rule;
	}

	/**
	 * Return the text that matched by this token
	 * 
	 * @return the text matched by this token
	 */
	public String getText() {
		return text.trim();
	}

	/**
	 * Test if this rule is a phrase rule. A phrase is processed 
	 * by a lexical segmenter.
	 * 
	 * @return true/false
	 */
	public boolean isPhrase() {
		return rule.getName().equals("phrase");
	}

	/**
	 * Test if this rule is a named entity rule.
	 * 
	 * @return true/false
	 */
	public boolean isNamedEntity() {
		return rule.getName().startsWith("name");
	}
	
	/**
	 * @return true/false
	 */
	public boolean isDate() {
		return rule.getName().startsWith("date");
	}
	
	/**
	 * @return true/false
	 */
	public boolean isDateDay() {
		return rule.getName().contains("day");
	}
	
	/**
	 * @return true/false
	 */
	public boolean isDateMonth() {
		return rule.getName().contains("month");
	}

	public boolean isDateYear() {
		return rule.getName().contains("year");
	}
	
	public boolean isNumber() {
		return rule.getName().startsWith("number");
	}
	/**
	 * @return Returns the column.
	 */
	public int getColumn() {
		return column;
	}

	/**
	 * @param column
	 *            The column to set.
	 */
	public void setColumn(int column) {
		this.column = column;
	}

	/**
	 * @return Returns the line.
	 */
	public int getLine() {
		return line;
	}

	/**
	 * @param line
	 *            The line to set.
	 */
	public void setLine(int line) {
		this.line = line;
	}

	/**
	 * Return a string representation of the token
	 */
	@Override
	public String toString() {
		// return "[\"" + text + "\"" + " at (" + line + "," + column + ")]";
		// return rule.getName() + ": " + text;
		return text.trim();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return getText().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (!(obj instanceof TaggedWord)) {
			return false;
		}
		// two lexer is considered equal if their text are equal.
		// 
		return ((TaggedWord)obj).getText().equals(getText());
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(TaggedWord o) {
		return getText().compareTo(o.getText());
	}
}
