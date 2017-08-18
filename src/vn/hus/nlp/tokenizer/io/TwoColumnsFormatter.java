/**
 * @author LE Hong Phuong
 * 8 sept. 2005
 */
package vn.hus.nlp.tokenizer.io;

import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author LE Hong Phuong
 *         <p>
 *         This class is an implementaton of interface
 *         <code>IOuputFormatter</code>. Its purpose is to provide a plain
 *         segmentation output in the form of two columns : token text and its
 *         lexical type.
 * 
 */
public class TwoColumnsFormatter implements IOutputFormatter {

	/* (non-Javadoc)
	 * @see vn.hus.tokenizer.io.IOutputFormatter#outputLexeme(vn.hus.tokenizer.tokens.LexerToken)
	 */
	public String outputLexeme(TaggedWord token) {
		StringBuffer stBuf = new StringBuffer();
		// output the text
		stBuf.append(token.getText());
		// a tab character
		stBuf.append("\t");
		// its type
		stBuf.append(token.getRule().getName());
		// end of line
		stBuf.append("\n");
		return stBuf.toString();
	}

}
