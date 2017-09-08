/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.tokenizer.tools;

import java.util.Arrays;

import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE Hong Phuong
 *  <p>
 * vn.hus.tokenize
 *  <p>
 * 9 juil. 07
 *  <p>
 *  This tool sort an input Vietnamese text file alphabetically. 
 */

public final class TextFileSorter {

	
	
	/**
	 * Sort an input file and output the result to the output file.
	 * @param inputFile an input file
	 * @param outputFile an output file
	 */
	public static void sort(String inputFile, String outputFile) {
		// load all lines of the input file to an array of strings.
		String[] lines = UTF8FileUtility.getLines(inputFile);
		// sort this string using VietComparator
		Arrays.sort(lines);
		// create a writer
		UTF8FileUtility.createWriter(outputFile);
		// save the results
		UTF8FileUtility.write(lines);
		// close the writer
		UTF8FileUtility.closeWriter();
	}
}
