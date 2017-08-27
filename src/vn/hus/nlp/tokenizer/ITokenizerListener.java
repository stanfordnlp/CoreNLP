/**
 *  @author LE Hong Phuong
 *  <p>
 *	22/01/2007
 */
package vn.hus.nlp.tokenizer;

import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author LE Hong Phuong
 * <p>
 * 22/01/2007
 * <p>
 */
public interface ITokenizerListener {
	/**
	 * Process a token
	 * @param token
	 */
	public void processToken(TaggedWord token);
}
