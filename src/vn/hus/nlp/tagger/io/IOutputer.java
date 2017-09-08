/**
 * 
 */
package vn.hus.nlp.tagger.io;

import java.util.List;

import old.edu.stanford.nlp.ling.WordTag;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <br>
 * Jul 2, 2009, 2:14:25 PM
 * <br>
 * A general outputer for formatting tagger's results.
 */
public interface IOutputer {
	/**
	 * Formats a list of tagged words.
	 * @param list a list of word/tag pairs for outputing
	 * @return an string
	 */
	public String output(List<WordTag> list);
}
