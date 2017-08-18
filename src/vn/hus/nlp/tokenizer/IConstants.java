/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 * vn.hus.toolkit
 * 2006
 */
package vn.hus.nlp.tokenizer;

/**
 * @author LE Hong Phuong
 * <p>
 * 31 déc. 06
 * </p>
 * Some predefined contants for vnTokenizer tool.
 * 
 */
public interface IConstants {
	/**
	 * Vietnamese word set
	 */
	public static final String WORD_SET = "data/dictionaries/words_v4.txt";
	
	/**
	 * The Vietnamese lexicon
	 */
	public static final String LEXICON = "data/dictionaries/words_v4.xml";
	
	/**
	 * The Vietnamese DFA lexicon
	 */
	public static final String LEXICON_DFA = "models/tokenization/automata/lexicon_dfa_minimal.xml";
	
	/**
	 * The named entity prefix.
	 */
	public static final String NAMED_ENTITY_PREFIX = "models/tokenization/prefix/namedEntityPrefix.xml";
}
