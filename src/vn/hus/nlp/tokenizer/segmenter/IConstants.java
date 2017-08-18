/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.tokenizer.segmenter;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * Nov 12, 2007, 8:48:03 PM
 * <p>
 * Some constants for the package.
 */
public interface IConstants {
	/**
	 * The lexicon dfa.
	 */
	static String LEXICON_DFA = "models/tokenization/automata/dfaLexicon.xml";
	
	/**
	 * The external lexicon
	 */
	static String EXTERNAL_LEXICON = "models/tokenization/automata/externalLexicon.xml";
	/**
	 * The file contains normalization rules for Vietnamese accents.
	 */
	static String NORMALIZATION_RULES = "models/tokenization/normalization/rules.txt";
}
