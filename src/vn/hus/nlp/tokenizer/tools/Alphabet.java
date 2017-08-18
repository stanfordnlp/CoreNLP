
package vn.hus.nlp.tokenizer.tools;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * This tool provides lists of Vietnamese lowercase and uppercase alphabet.
 * 
 */
public class Alphabet {

	/**
	 * Default constructor
	 *
	 */
	public Alphabet() {
		
	}
	
	/**
	 * Get Vietnamese lowercase alphabet
	 * @return a list of vietnamese lowercase characters
	 */
	public List<Character> getLowerAlphabet() {
		List<Character> l  = new ArrayList<Character>();
		for (int i = 0; i < Unicode_char.length; i++) {
			l.add(new Character(Unicode_char[i]));
		}
		//Collections.sort(l);
		return l;
	}
	/**
	 * Get Vietnamese uppercase alphabet
	 * @return a list of vietnamese uppercase characters
	 */
	public List<Character> getUpperAlphabet() {
		List<Character> l  = new ArrayList<Character>();
		for (int i = 0; i < Unicode_cap.length; i++) {
			l.add(new Character(Unicode_cap[i]));
		}
		//Collections.sort(l);
		return l;
	}
	
	/**
	 * Print a list to an output file
	 * @param list a list to print
	 * @param filename filename to create
	 * @throws IOException
	 */
	public void print(List<Character> list, String filename) throws IOException {
		FileOutputStream fos = new FileOutputStream(filename);
		OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
		BufferedWriter br = new BufferedWriter(osw);
		Iterator<Character> it = list.iterator();
		while (it.hasNext()) {
			Character c = it.next();
			br.write(c.toString() + "\t" + Character.getNumericValue(c.charValue()) + "\n"); 
		}
		br.close();
	}
	
	public static void main(String args[]) throws IOException {
		Alphabet alphabet = new Alphabet();
		alphabet.print(alphabet.getLowerAlphabet(), "resources//lowerChars.txt");
		alphabet.print(alphabet.getUpperAlphabet(), "resources//uppersChars.txt");
	}
	
	/**
	 * An array of lowercase characters
	 */
    private final char[] Unicode_char = {
    		'\u1EF9', '\u1EF7', '\u1EF5', '\u1EF3', '\u1EF1', '\u1EEF', '\u1EED', '\u00FD', '\u00E0',
            '\u1EEB', '\u1EE9', '\u1EE7', '\u1EE5', '\u1EE3', '\u1EE1', '\u1EDF', '\u1EDD', '\u1EDB',
            '\u1ED9', '\u1ED7', '\u1ED5', '\u1ED3', '\u1ED1', '\u1ECF', '\u1ECD', '\u1ECB', '\u1EC9',
            '\u1EC7', '\u1EC5', '\u1EC3', '\u1EC1', '\u1EBF', '\u1EBD', '\u1EBB', '\u1EB9', '\u1EB7',
            '\u1EB5', '\u1EB3', '\u00F4', '\u1EAF', '\u1EAD', '\u1EAB', '\u1EA9', '\u1EA7', '\u1EA5',
            '\u1EA3', '\u1EA1', '\u01B0', '\u01A1', '\u0169', '\u0129', '\u0111', '\u00E3', '\u00E2',
            '\u0103', '\u00FA', '\u00F9', '\u00F5', '\u1EB1', '\u00F3', '\u00F2', '\u00ED', '\u00E1',
            '\u00EC', '\u00EA', '\u00E9', '\u00E8'   
            };

    /**
     * An array of uppercase characters
     */
    private final char[] Unicode_cap = {
    		'\u00C0', '\u1EA2', '\u00C3', '\u00C1', '\u1EA0', '\u00C8','\u1EBA', '\u1EBC', '\u00C9', 
			'\u1EB8', '\u00CC', '\u1EC8', '\u0128', '\u00CD', '\u1ECA','\u00D2', '\u1ECE', '\u00D5', 
			'\u00D3', '\u1ECC', '\u00D9', '\u1EE6', '\u0168', '\u00DA', '\u00D4', '\u0110', '\u00CA', 
            '\u1EE4', '\u1EF2', '\u1EF6', '\u1EF8', '\u00DD', '\u1EF4', '\u1EB0', '\u1EB2', '\u1EB4',
            '\u1EAE', '\u1EB6', '\u1EA6', '\u1EA8', '\u1EAA', '\u1EA4', '\u1EAC', '\u1EC0', '\u1EC2',
            '\u1EC4', '\u1EBE', '\u1EC6', '\u1ED2', '\u1ED4', '\u1ED6', '\u1ED0', '\u1ED8', '\u1EDC',
            '\u1EDE', '\u1EE0', '\u1EDA', '\u1EE2', '\u1EEA', '\u1EEC', '\u1EEE', '\u1EE8', '\u1EF0',
			'\u00C2', '\u0102', '\u01AF', '\u01A0'
			};
    
}
