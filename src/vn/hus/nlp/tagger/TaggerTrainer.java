/**
 * Phuong LE HONG, phuonglh@gmail.com
 */
package vn.hus.nlp.tagger;

import old.edu.stanford.nlp.tagger.maxent.TaggerConfig;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 7, 2009, 2:50:20 PM
 *         <p>
 *         This class is used for training the tagger. It uses the utility
 *         <code>TestClassifier</code> of Stanford NLP Maxent Tagger.
 */
public class TaggerTrainer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TaggerConfig config;
		// create a tagger configuration
		if (args.length > 0) {
			config = new TaggerConfig(args);
		} else {
			// create an array of arguments
			String[] arguments = { "-props", IConstants.DEFAULT_TRAINING_PROPERTIES };
			config = new TaggerConfig(arguments);
		}
		// verify that the config has the mode "train"
		// and run the training
		if (config.getMode() == TaggerConfig.Mode.TRAIN) {
			try {
				// train the tagger
//				MaxentTagger.runTrainingPublic(config);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

}
