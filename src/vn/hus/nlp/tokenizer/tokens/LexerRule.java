package vn.hus.nlp.tokenizer.tokens;

import java.util.regex.Pattern;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         A lexical rule.
 */
public class LexerRule {
	/**
	 * The name of the lexical category that this rule matches
	 */
	private final String name;

	/**
	 * The regular expression used for matching
	 */
	private final String regex;
	/**
	 * A pre-compiled pattern object, kept to save processing time
	 */
	private Pattern pattern;

	/**
	 * Instantiate a new lexical rule with a name
	 * 
	 * @param name
	 *            a name
	 */
	public LexerRule(String name) {
		this.name = name;
		this.regex = "";
	}

	/**
	 * Instantiate a new lexical rule with a name and a regex
	 * 
	 * @param name
	 *            a name
	 * @param regex
	 *            a regular expression
	 */
	public LexerRule(String name, String regex) {
		this.name = name;
		this.regex = regex;
	}

	/**
	 * Get the category name
	 * 
	 * @return the name of rule
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the regex defining the rule
	 * 
	 * @return the regex
	 */
	public String getRegex() {
		return regex;
	}

	/**
	 * Return the pattern object. Create one if it hasn't been created already.
	 * 
	 * @return the pattern object
	 */
	public Pattern getPattern() {
		if (pattern == null) {
			pattern = Pattern.compile(regex);
		}
		return pattern;
	}

	/**
	 * Return a string representation of the rule
	 */
	@Override
	public String toString() {
		return "[" + name + "]";
	}
}
