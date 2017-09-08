/**
 * 
 */
package vn.hus.nlp.tagger;

import old.edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Jun 16, 2009, 12:00:19 PM
 * <p>
 * The tagger provider which creates a maxent tagger for Vietnamese.
 *  
 */
public class VietnameseMaxentTaggerProvider {
	
	/**
	 * The maxent tagger of Stanford.
	 */
	private static MaxentTagger maxentTagger = null;
	/**
	 * Use only static method.
	 */
	private VietnameseMaxentTaggerProvider() {}

	/**
	 * Get the tagger
	 * @return the maxent tagger using the default model file.
	 */
	public static MaxentTagger getInstance() {
		return getInstance(IConstants.DEFAULT_MODEL_FILE);
	}
	
	/**
	 * Get the tagger.
	 * @param modelFile
	 * @return the maxent tagger with a given model file
	 */
	public static MaxentTagger getInstance(String modelFile) {
		if (maxentTagger == null) {
			try {
				maxentTagger = new MaxentTagger(modelFile);
			} catch (Exception e) {
				System.err.println("Error when creating the maxent tagger. Please check the model file.");
				e.printStackTrace();
			}
		}
		return maxentTagger;
	}
}
