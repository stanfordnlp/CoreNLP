/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 * vn.hus.tokenizer
 * 2007
 */
package vn.hus.nlp.tokenizer.io;

import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author LE Hong Phuong
 * <p>
 * 4 févr. 07
 * </p>
 * An outputer listener.
 */
public interface IOutputListener {
	/**
	 * Notifies a token to be outputed.
	 * @param token a lexer token.
	 */
	public void outputToken(TaggedWord token);
}
