/**
 * (C) LE HONG Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.lang.model.test;

import java.io.IOException;

import vn.hus.nlp.lang.model.unigram.Unigram;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Created: Apr 8, 2008, 1:27:16 PM
 *         <p>
 *         Test the unigram model.
 * 
 */
public final class UnigramTest {

	public static void main(String[] args) {
		// load the sample corpus to a unigram model and print the model to a
		// plain text file
		// and an XML file.
		Unigram.getInstance();
		try {
			Unigram.loadCorpus(TestConstants.CORPUS_NAME);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Unigram.print(TestConstants.UNIGRAM_TEXT);
		Unigram.marshal(TestConstants.UNIGRAM_XML);
	}
}
