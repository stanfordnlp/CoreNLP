/**
 * 
 */
package vn.hus.nlp.lang.model;

/**
 * @author phuonglh
 * 
 * Some constants for the plugin.
 *
 */
public interface IConstants {
	
	/**
	 * Debug the package or not 
	 */
	static final boolean DEBUG = true;
	/**
	 * The reference corpora directory that contains text files to train 
	 * the model.
	 */
	static final String CORPORA_DIRECTORY = "corpora/ref";
	/**
	 * Unigram model
	 */
	static final String UNIGRAM_MODEL = "resources/unigram.xml";
	/**
	 * Bigram model
	 */
	static final String BIGRAM_MODEL = "resources/bigram.xml";
	
	/**
	 * The conditional probabilities.
	 */
	static final String CONDITIONAL_PROBABILITIES = "resources/prob.xml";
}
