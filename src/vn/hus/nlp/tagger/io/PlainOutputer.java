/**
 * 
 */
package vn.hus.nlp.tagger.io;

import java.util.List;

import old.edu.stanford.nlp.ling.WordTag;
import vn.hus.nlp.tagger.IConstants;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <br>
 * Jul 2, 2009, 2:16:54 PM
 * <br>
 * A plain outputer.
 */
public class PlainOutputer implements IOutputer {

	/* (non-Javadoc)
	 * @see vn.hus.nlp.tagger.io.IOutputer#output(java.util.List)
	 */
	public String output(List<WordTag> list) {
		StringBuffer result = new StringBuffer(1024);
		for (WordTag wordTag : list) {
			result.append(wordTag.word());
			result.append(IConstants.DELIM);
			result.append(wordTag.tag());
			result.append(" ");
		}
		result.append("\n");
		return result.toString();
	}

}
