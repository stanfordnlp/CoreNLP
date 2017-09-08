/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 * vn.hus.tokenizer
 * 2007
 */
package vn.hus.nlp.lang.model.unigram;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import vn.hus.nlp.lang.model.IConstants;
import vn.hus.nlp.lexicon.LexiconMarshaller;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE Hong Phuong
 *         <p>
 *         8 mars 07
 *         </p>
 *         A counter for tokens in corpus, used to produce frequencies of
 *         Vietnamese tokens.
 */
public class Unigram {

	/**
	 * A map that stores strings and their corresponding frequencies.
	 */
	private static Map<String, Integer> UNIGRAM;

	/**
	 * The unigram model
	 */
	private static Unigram MODEL;
	/**
	 * Private constructor
	 */
	private Unigram() {
		init();
	}
	
	/**
	 * Initialize the map of unigrams
	 */
	private void init() {
		UNIGRAM = new HashMap<String, Integer>();
	}

	/**
	 * Get the unique instance of a unigram model.
	 * @return an empty unigram model
	 */
	public static Unigram getInstance() {
		if (MODEL == null) {
			MODEL = new Unigram();
		}
		return MODEL;
	}
	
	/**
	 * Test if a file is a directory 
	 * @param filename a filename
	 * @return true or false
	 */
	private static boolean isDirectory(String filename) {
		File file = new File(filename);
		return file.isDirectory();
	}
	
	/**
	 * Load all flat text files from a directory.
	 * @param directoryName name of a directory that contains corpora.
	 */
	public static void loadCorpora(String directoryName) {
		// get the corpora directory
		File corporaDir = new File(IConstants.CORPORA_DIRECTORY);
		// list its files
		File[] corpora = corporaDir.listFiles();
		// load all of the files
		// don't take into account subdirectories
		for (int i = 0; i < corpora.length; i++) {
			String corpus = corpora[i].getPath();
			if (!isDirectory(corpus)) {
				try {
					loadCorpus(corpus);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		System.err.println("Total " + corpora.length + " files loaded.");
	}


	/**
	 * Load a corpus and update the frequencies.
	 * 
	 * @param corpus
	 *            a corpus
	 * @throws IOException
	 */
	public static void loadCorpus(String corpus) throws IOException {
		String[] lines = UTF8FileUtility.getLines(corpus);
		for (int i = 0; i < lines.length; i++) {
			String token = lines[i];
			if (!UNIGRAM.containsKey(token)) {
				UNIGRAM.put(token, new Integer(1));
			} else {
				int v = ((Integer) UNIGRAM.get(token)).intValue();
				UNIGRAM.put(token, new Integer(v + 1));
			}
		}
	}


	/**
	 * Get the frequencies map.
	 * 
	 * @return the frequencies map.
	 */
	public static Map<String, Integer> getFrequencies() {
		return UNIGRAM;
	}

	/**
	 * Output the unigram to a plain text file in the form of two columns.
	 * 
	 * @param filename a flat text filename
	 * @see {@link #marshal(String)}
	 */
	public static void print(String filename) {
		// create a file writer
		UTF8FileUtility.createWriter(filename);
		Iterator<String> keys = UNIGRAM.keySet().iterator();
		// create a string buffer for storing the text
		StringBuffer sBuffer = new StringBuffer(1024);
		int numTokens = 0;
		int freq = 0;
		while (keys.hasNext()) {
			String token = keys.next();
			freq = ((Integer) UNIGRAM.get(token)).intValue();
			numTokens += freq;
			sBuffer.append(token + '\t' + freq + "\n");
		}
		// write the string buffer to the file
		UTF8FileUtility.write(sBuffer.toString());
		// close the writer
		UTF8FileUtility.closeWriter();
		System.err.println("# of   tokens = " + numTokens);
		System.err.println("# of unigrams = " + UNIGRAM.size());
	}
	
	/**
	 * Marshal the map to an XML file using the lexicon format.
	 * @param filename the XML file containing the unigram model.
	 */
	public static void marshal(String filename) {
		new LexiconMarshaller().marshal(UNIGRAM, filename);
	}

}
