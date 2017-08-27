/**
 * Phuong LE HONG, phuonglh@gmail.com
 */
package vn.hus.nlp.tokenizer;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import vn.hus.nlp.lexicon.LexiconUnmarshaller;
import vn.hus.nlp.lexicon.jaxb.Corpus;
import vn.hus.nlp.lexicon.jaxb.W;
import vn.hus.nlp.tokenizer.tokens.LexerRule;
import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Dec 24, 2009, 3:21:00 PM
 * <p>
 * This utility is used to split out a tagged token into two tokens. For example the 
 * named entity "Ông Nông Đức Mạnh" is split into two tokens "Ông" and "Nông Đức Mạnh".
 */
public class ResultSplitter {

	/**
	 * Set of predefined prefixes.
	 */
	private Set<String> prefix;
	
	/**
	 * Default constructor.
	 */
	public ResultSplitter() {
		this(IConstants.NAMED_ENTITY_PREFIX);
	}
	
	/**
	 * Creates a result splitter from a named entity prefix filename.
	 * @param namedEntityPrefixFilename
	 */
	public ResultSplitter(String namedEntityPrefixFilename) {
		// load the prefix lexicon
		// 
		LexiconUnmarshaller  lexiconUnmarshaller = new LexiconUnmarshaller();
		Corpus lexicon = lexiconUnmarshaller.unmarshal(namedEntityPrefixFilename);
		List<W> ws = lexicon.getBody().getW();
		prefix = new HashSet<String>();
		// add all prefixes to the set after converting them to lowercase
		for (W w : ws) {
			prefix.add(w.getContent().toLowerCase());
		}
	}
	
	/**
	 * Creates a result splitter from a properties filename.
	 * @param properties a properties file.
	 */
	public ResultSplitter(Properties properties) {
		this(properties.getProperty("namedEntityPrefix"));
	}
	
	private boolean isPrefix(String syllable) {
		return prefix.contains(syllable.toLowerCase());
	}
	
	/**
	 * Splits a named entity token into two tokens.
	 * @param token
	 * @return two tagged tokens
	 */
	public TaggedWord[] split(TaggedWord token) {
		// split the token basing on spaces or underscore 
		String[] syllables = token.getText().split("\\s+");
		if (syllables.length > 1) {
			// extract the first syllable of token
			if (isPrefix(syllables[0])) {
//				System.err.println("Split " + token.getText());
				int position = syllables[0].length() + 1;
				// it is sure that postion > 0
				String suffix = token.getText().substring(position);
				TaggedWord[] result = new TaggedWord[2];
				result[0] = new TaggedWord(new LexerRule("word"), syllables[0]);
				result[1] = new TaggedWord(new LexerRule("name"), suffix.trim());
				return result;
			}
		}
		return null;
	}
}
