/**
 *  @author LE Hong Phuong
 *  <p>
 *	24 mars 07
 */
package vn.hus.nlp.tokenizer.tools;

import java.util.Formatter;
import java.util.Locale;

import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE Hong Phuong
 *         <p>
 *         24 mars 07
 *         <p>
 *         vn.hus.tokenizer
 *         <p>
 *         This counter gives a statistics of lexical tokens in the Vietnamese
 *         lexicon. It counts for single and compound words of the lexicon. The
 *         purpose is to produce:
 *         <ol>
 *         <li>Number and percent of single words
 *         <li>Number and percent of two-syllable words
 *         <li>Number and percent of three-syllable words
 *         <li>Number and percent of words that have more than three syllables
 *         </ol>
 * 
 */
public final class LexiconCounter {
	/**
	 * A lexicon filename
	 */
	String lexiconFile;

	public LexiconCounter(String lexiconFile) {
		this.lexiconFile = lexiconFile;
	}

	public void count() {
		// get all lines of the lexicon
		String[] lines = UTF8FileUtility.getLines(lexiconFile);
		// count
		int[] counters = { 0, 0, 0, 0, 0};
		for (int i = 0; i < lines.length; i++) {
			String[] syllables = lines[i].split("\\s+");
			int len = syllables.length;
			if (0 < len) {
				if (len <= 4) {
					counters[syllables.length - 1]++;
				} else {
					counters[counters.length - 1]++;
				}
			}
		}
		System.out.println("# of lexicon = " + lines.length);
		for (int i = 0; i < counters.length; i++) {
			Formatter formatter = new Formatter(System.out);
			formatter.format(Locale.US, "%s %d = %d, %4.2f\n",
					"# of length ", i+1, counters[i], (float) counters[i]
							/ lines.length * 100);
		}
		// verify the total number
		int m = 0;
		for (int i = 0; i < counters.length; i++) {
			m += counters[i];
		}
		System.out.println("Total = " + m);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new LexiconCounter("dictionaries/words_v3_set.txt").count();
	}

}
