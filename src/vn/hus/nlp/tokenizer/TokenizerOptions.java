/**
 * Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.tokenizer;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * 8 juil. 2009, 20:49:31
 * <p>
 * Some options of the tokenizer.
 */
public class TokenizerOptions {
	/**
	 * Use a sentence detector before tokenizing text or not.
	 */
	public static boolean USE_SENTENCE_DETECTOR = false;
	
	/**
	 * Use underscores for separating syllbles of words or not.
	 */
	public static boolean USE_UNDERSCORE = true;
	
	/**
	 * Export results as XML format or not.
	 */
	public static boolean XML_OUTPUT = false;
	
	/**
	 * Default file extension for tokenization.
	 */
	public static String TEXT_FILE_EXTENSION = ".txt";
}
