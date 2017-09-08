/**
 * 
 */
package vn.hus.nlp.tagger;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Jun 16, 2009, 12:01:30 PM
 * <p>
 * Some predefined constants for use in the tagger.
 */
public interface IConstants {
	/**
	 * The default maxent model file of the Vietnamese tagger.
	 */
	//TODO: How to set flexible by using resources/models/vtb.tagger
	public static String DEFAULT_MODEL_FILE = "models/tagger/vtb.tagger";
	// the experiment 1 (reported in the article)
//	public static String DEFAULT_MODEL_FILE = "experiments/fold1/left5words-vtb-1.tagger";
	/**
	 * The default properties file for training the tagger.
	 */
//	public static String DEFAULT_TRAINING_PROPERTIES = "data/conf/left3words-vtb-0.tagger.props";
//	public static String DEFAULT_TRAINING_PROPERTIES = "data/conf/left3words-vtb-1.tagger.props";
//	public static String DEFAULT_TRAINING_PROPERTIES = "data/conf/left5words-vtb-1.tagger.props"; 
//	public static String DEFAULT_TRAINING_PROPERTIES = "data/conf/bidirectional5-vtb-1.tagger.props";
//	public static String DEFAULT_TRAINING_PROPERTIES = "experiments/fold1/left5words-vtb-1.tagger.props";
	
	// the experiment done with Stanford Maxent Tagger 2.0 
	public static String DEFAULT_TRAINING_PROPERTIES = "data/conf2/vnTagger.props";
	/**
	 * The delimiter between words and tags.
	 */
	public static final String DELIM = "/"; 


	/**
	 * The Vietnamese treebank file. 
	 */
	public static String TREEBANK = "data/vtb-20091030.txt"; 
	
	/**
	 * The whole tagged corpus extracted from the Vietnamese treebank.
	 */
	public static String CORPUS = "data/vtb-20091030.tagged.txt";
	
	/**
	 * The training corpus. 
	 */
//	public static String CORPUS_TRAINING = CORPUS;
	public static String CORPUS_TRAINING = "data/vtb-train.txt";
	
	/**
	 * The test corpus.
	 */
	public static String CORPUS_TEST = "data/vtb-test.txt";
}
