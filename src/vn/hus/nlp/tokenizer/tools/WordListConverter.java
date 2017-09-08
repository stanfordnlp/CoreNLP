/**
 * 
 */
package vn.hus.nlp.tokenizer.tools;

import java.util.Map;
import java.util.TreeMap;

import vn.hus.nlp.lexicon.LexiconMarshaller;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <br>
 * Jul 15, 2009, 4:51:29 PM
 * <br>
 * This utility is used to convert a list of words in simple format to XML (lexicon) format.
 */
public class WordListConverter {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String fileInp = "data/dictionaries/words_v4.txt";
		String fileOut = "data/dictionaries/words_v4.xml";
		
		String[] words = UTF8FileUtility.getLines(fileInp);
		Map<String, String> wordMap = new TreeMap<String, String>();
		for (String word : words) {
			word = word.trim();
			if (word.length() > 0)
				wordMap.put(word, "");
		}
		LexiconMarshaller marshaller = new LexiconMarshaller();
		marshaller.marshal(wordMap, fileOut);
		System.out.println("Done");
	}

}
