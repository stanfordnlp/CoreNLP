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
 *         <p>
 *         vn.hus.tokenizer
 *         <p>
 *         Jul 28, 2007, 11:54:02 AM
 * 
 * This tool finds all syllables of the word list.
 * 
 */
public final class SyllablesFinder {
	/**
	 * Delimiters specified by a regular expression
	 */
	static final String DELIMITERS = "\\s\\d\\.,:;\\?\\^!~\\[\\]\\(\\)\\{\\}\\$&#'\"@\\|\\+-\\/";
	
	/**
	 * A set of syllables;
	 */
	SortedSet<String> syllables;
	
	/**
	 * Default constructor
	 */
	public SyllablesFinder() {
		// init the syllable tree set with a comparator for Vietnamese
		syllables = new TreeSet<String>();
	}
	
	public void find(String inputFile, String outputFile) {
		// get all words of the input file
		String[] words = UTF8FileUtility.getLines(inputFile);
		// iterate through words and build the syllables set
		for (String word : words) {
			String[] syls = word.split("[" + DELIMITERS + "]+");
			for (String syl : syls) {
				if (syl.trim().length() > 0 && !CaseConverter.containsUppercase(syl)) 
					syllables.add(syl.trim());
			}
		}
		// convert the syllables set to an array of syllables
		String[] lines = syllables.toArray(new String[syllables.size()]);
		
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
		new SyllablesFinder().find(args[0], args[1]);
		System.out.println("Done");
	}

}
