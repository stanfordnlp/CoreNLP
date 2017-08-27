/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.tokenizer.segmenter;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * Nov 14, 2007, 3:21:03 PM
 * <p>
 * This is an abstract lexicon recognizer. It provides a method 
 * to determine whether a given token can be recognized or not, 
 * i.e., it is in the lexicon or not. 
 */
public abstract class AbstractLexiconRecognizer {
	/**
	 * @param token
	 * @return <tt>true</tt> if the token is accepted, <tt>false</tt> otherwise. 
	 */
	public abstract boolean accept(String token);
	
	/**
	 * Dispose the recognizer for saving space.
	 */
	public abstract void dispose();
}
