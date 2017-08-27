/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 * vn.hus.tokenizer
 * 2007
 */
package vn.hus.nlp.lang.model.bigram;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import vn.hus.nlp.lang.model.IConstants;
import vn.hus.nlp.lexicon.LexiconMarshaller;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE Hong Phuong
 * <p>
 * 13 mars 07
 * </p>
 * A counter for two sequential tokens in corpus (bigram model), 
 * used to produce frequencies of bigrams of Vietnamese tokens.
 * 
 */
public class Bigram {
	
	/**
	 * A map of couples. We use a map to speed up the search of a couple.
	 * 
	 */
	private Map<Couple, Couple> bigram;
	
	public Bigram() {
		init();
		loadCorpora();
	}
	
	public Bigram(boolean isCoded) {
		init();
		// load corpora, do statistics
		loadCorpora();
	}
	
	/**
	 * Load all corpora.
	 *
	 */
	private void loadCorpora() {
		// get the corpora directory
		File corporaDir = new File(IConstants.CORPORA_DIRECTORY);
		// list its files
		File[] corpora = corporaDir.listFiles();
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
		System.out.println("Total " + corpora.length + " files loaded.");
	}

	private boolean isDirectory(String filename) {
		File file = new File(filename);
		return file.isDirectory();
	}
	
	/**
	 * Load a corpus and update the bigram set
	 * @param corpus
	 * @throws IOException 
	 */
	private void loadCorpus(String corpus) throws IOException {
		
		String[] lines = UTF8FileUtility.getLines(corpus);
		String first = "";
		for (int i = 0; i < lines.length; i++) {
			String second = lines[i];
			Couple couple = new Couple(first,second);
			if (!bigram.keySet().contains(couple)) {
				bigram.put(couple, couple);
			} else {
				// search for the couple
				Couple c = bigram.get(couple);
				c.incFreq();
			}
			// update the first token
			first = second;
		}
	}

	private void init() {
		bigram = new HashMap<Couple, Couple>();
	}

	/**
	 * Get the bigram set.
	 * @return
	 */
	public Map<Couple, Couple> getBigram() {
		return bigram;
	}
	
	/**
	 * Output bigram to a text file.
	 * @param filename
	 * @see {@link #marshalResults(String)}.
	 */
	public void print(String filename) {
		try {
			Writer writer = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8");
			BufferedWriter bufWriter = new BufferedWriter(writer);
			Iterator<Couple> couples = bigram.keySet().iterator();
			int numCouples = 0;
			while (couples.hasNext()) {
				Couple couple = couples.next();
				int freq = couple.getFreq();
				bufWriter.write(couple + "\n");
				numCouples += freq;
			}
			bufWriter.flush();
			writer.close();
			System.out.println("# of couples = " + bigram.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Marshal the map to an xml file using the lexicon format.
	 * @param filename
	 */
	public void marshal(String filename) {
		// prepare a map for marshalling
		Map<String, Integer> map = new HashMap<String, Integer>();
		for (Iterator<Couple> it = bigram.keySet().iterator(); it.hasNext(); ) {
			Couple c = it.next();
			String key = c.getFirst() + "," + c.getSecond();
			int value = c.getFreq();
			map.put(key, value);
		}
		new LexiconMarshaller().marshal(map, filename);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Bigram counter = new Bigram(false);
		counter.marshal(IConstants.BIGRAM_MODEL);
		System.out.println("Done!");
	}

}
