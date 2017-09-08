/**
 * Phuong LE HONG, phuonglh@gmail.com
 */
package vn.hus.nlp.tagger.util;

import java.util.Arrays;
import java.util.List;

import vn.hus.nlp.tagger.IConstants;
import vn.hus.nlp.utils.RandomSelector;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Oct 8, 2009, 3:11:38 PM
 * <p>
 * Create test and training corpus from the whole corpus.
 */
public class CorpusSplitter {

	
	public CorpusSplitter(String corpus, int n) {
		List<String> sentences = Arrays.asList(UTF8FileUtility.getLines(corpus));
		split(sentences, n);
	}
	
	private void split(List<String> sentences, int n) {
		RandomSelector<String> randomSelector = new RandomSelector<String>(sentences, n);
		
		// write the test corpus
		//
		List<String> testCorpus = randomSelector.getSelectedElements();
		UTF8FileUtility.createWriter(IConstants.CORPUS_TEST);
		UTF8FileUtility.write(testCorpus.toArray(new String[testCorpus.size()]));
		UTF8FileUtility.closeWriter();
		
		// write the training corpus
		//
		List<String> trainingCorpus = randomSelector.getUnselectedElements();
		UTF8FileUtility.createWriter(IConstants.CORPUS_TRAINING);
		UTF8FileUtility.write(trainingCorpus.toArray(new String[trainingCorpus.size()]));
		UTF8FileUtility.closeWriter();
		
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// The treebank has 10,165 sentences. It is reasonable for 
		// creating a test corpus of size 500 sentences (5%), and the rest for training.
		int size = 10165;
		int n = 500;
		System.out.println((float)n/size);
		new CorpusSplitter(IConstants.CORPUS, n);
		System.out.println("Done.");
	}

}
