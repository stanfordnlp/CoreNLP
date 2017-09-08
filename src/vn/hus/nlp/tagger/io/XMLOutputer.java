/**
 * 
 */
package vn.hus.nlp.tagger.io;

import java.util.List;

import old.edu.stanford.nlp.ling.WordTag;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <br>
 * Jul 2, 2009, 2:18:53 PM
 * <br>
 * Simple XML outputer.
 */
public class XMLOutputer implements IOutputer {

	/* (non-Javadoc)
	 * @see vn.hus.nlp.tagger.io.IOutputer#output(java.util.List)
	 */
	public String output(List<WordTag> list) {
		StringBuffer result = new StringBuffer(1024);
		result.append("\t<s>\n");
		for (WordTag wordTag : list) {
			result.append("\t\t<w pos=\"" + wordTag.tag() + "\">" + wordTag.word() + "</w>\n");
		}
		result.append("\t</s>\n");
		return result.toString();
	}

}
