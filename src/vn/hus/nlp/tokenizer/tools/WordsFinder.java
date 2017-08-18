/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.tokenizer.tools;

import java.util.SortedSet;
import java.util.TreeSet;

import vn.hus.nlp.utils.CaseConverter;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * vn.hus.tokenizer
 * <p>
 * Jul 29, 2007, 9:56:49 AM
 * Build a lexicon for word automaton.  
 */
public final class WordsFinder {

	SortedSet<String> wordsSet;
	
	/**
	 * Delimiters specified by a regular expression. This does not contain
	 * space character.
	 */
	static final String DELIMITERS = "\\d\\.,:;\\?\\^!~\\[\\]\\(\\)\\{\\}\\$&#'\"@\\|\\+-\\/";

	
	/*
	 * 
	 */
	public WordsFinder() {
		wordsSet = new TreeSet<String>();
	}
	
	public void find(String inputFile, String outputFile) {
		String[] words = UTF8FileUtility.getLines(inputFile);
		
		for (String word : words) {
				String[] ws = word.split("[" + DELIMITERS + "]+");
				for (String w : ws) {
					if (w.trim().length() > 0 && !CaseConverter.containsUppercase(w)) 
						wordsSet.add(w.trim());
				}
		}
		// convert the syllables set to an array of syllables
		String[] lines = wordsSet.toArray(new String[wordsSet.size()]);
		
		// create a writer
		UTF8FileUtility.createWriter(outputFile);
		// save the results
		UTF8FileUtility.write(lines);
		// close the writer
		UTF8FileUtility.closeWriter();
		
	}
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Please give two arguments: <inputFile> <outputFile>");
			return;
		}
		new WordsFinder().find(args[0], args[1]);
		System.out.println("Done!");
	}

}
