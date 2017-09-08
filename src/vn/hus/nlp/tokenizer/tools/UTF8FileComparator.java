/**
 *  @author LE Hong Phuong
 *  <p>
 *	23 mars 07
 */
package vn.hus.nlp.tokenizer.tools;

import java.util.ArrayList;
import java.util.List;

import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE Hong Phuong
 *         <p>
 *         23 mars 07
 *         <p>
 *         vn.hus.tokenizer
 *         <p>
 *         This is a comparator of two UTF8 text files. It is usually used to
 *         compare results of two files which contain tokenization results
 *         (using or not using an ambiguity resolver).
 */
public final class UTF8FileComparator {
	/**
	 * First file which contains not-resolved ambiguous segmentations.
	 */
	String firstFile;

	/**
	 * Second file which contains resolved ambigous segmentations.
	 */
	String secondFile;
	/**
	 * The third file that contains differences of the two files first and second.
	 */
	String thirdFile;

	public UTF8FileComparator(String firstFile, String secondFile, String thirdFile) {
		this.firstFile = firstFile;
		this.secondFile = secondFile;
		this.thirdFile = thirdFile;
	}
	/**
	 * Compare two files to get their differences.
	 *
	 */
	public String[] compare() {
		List<String> differences = new ArrayList<String>();
		// get all lines of the first file
		String[] firstLines = UTF8FileUtility.getLines(firstFile);
		// get all lines of the second file
		String[] secondLines = UTF8FileUtility.getLines(secondFile);
		// compare two arrays of strings to find out differences
		int min = (firstLines.length < secondLines.length ? firstLines.length
				: secondLines.length);
		for (int i = 0; i < min; i++) {
			if (!firstLines[i].equals(secondLines[i])) {
				differences.add("Line " + (i + 1) + ": " + firstLines[i] + " # "
						+ secondLines[i]);
			}
		}
		return differences.toArray(new String[differences.size()]); 
	}
	/**
	 * Export the differences to the third file
	 *
	 */
	public void exportResult() {
		UTF8FileUtility.createWriter(thirdFile);
		String[] differences = compare();
		UTF8FileUtility.write("# of differences = " + differences.length + "\n\n");
		UTF8FileUtility.write(differences);
		UTF8FileUtility.closeWriter();
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		UTF8FileComparator comparator = new UTF8FileComparator(
				"corpus/test/corpus0_tok_ambiguities.txt",
				"corpus/test/corpus0_tok_resolved.txt",
				"corpus/test/corpus0_tok_differences.txt");
		comparator.compare();
		comparator.exportResult();
		System.out.println("Done!");
	}
}
