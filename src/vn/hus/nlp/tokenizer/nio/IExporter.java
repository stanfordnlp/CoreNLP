package vn.hus.nlp.tokenizer.nio;

import java.util.List;

import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Jul 13, 2009, 1:47:35 PM
 *         <p>
 *         The exporter which is used to export result of tokenization.
 */
public interface IExporter {
	/**
	 * Creates a string representation of an array of lists of tokens.
	 * @param list
	 * @return a string
	 */
	public String export(List<List<TaggedWord>> list);
}
