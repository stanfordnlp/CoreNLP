/**
 * (C) LE HONG Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.lang.model.test;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Created: Apr 8, 2008, 1:54:41 PM
 *         <p>
 *         Some predefined constants for testing the package.
 */
public interface TestConstants {
	/**
	 * A test corpus
	 */
	static final String CORPUS_NAME = "samples/1019.txt";
	/**
	 * The unigram text file
	 */
	static final String UNIGRAM_TEXT = "samples/1019.ugr";
	/**
	 * The unigram xml file
	 */
	static final String UNIGRAM_XML = "samples/1019.xml";
	
	/**
	 * The vocabulary with default cutoff (1)
	 */
	static final String VOCABULARY_1_TXT = "samples/1019_1.voc";
	/**
	 * The vocabulary with cutoff = 5
	 */
	static final String VOCABULARY_5_TXT = "samples/1019_5.voc";

}
