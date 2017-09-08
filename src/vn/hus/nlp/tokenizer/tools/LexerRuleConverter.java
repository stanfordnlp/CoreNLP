/**
 * (C) LE HONG Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.tokenizer.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vn.hus.nlp.lexicon.LexiconMarshaller;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Jan 11, 2008, 10:11:46 PM
 *         <p>
 *         Convert the text file that contains lexer rules to xml file for OS
 *         independent. The format in use is the lexicon format, each w element
 *         contains a rule.
 */
public class LexerRuleConverter {
	/**
	 * Regex for parsing the specification file
	 */
	static private final String lxRuleString = "^\\s*(\\S+)\\s+(\\S+)\\s*$";

	private final Map<String, String> lexerMap = new TreeMap<String, String>();

	/**
	 * Load lexer specification file. This text file contains lexical rules to
	 * tokenize a text
	 * 
	 * @param lexersText
	 *            the lexer text filename
	 * @return a map
	 */
	private Map<String, String> load(String lexersText) {
		try {
			// Read the specification text file line by line
			FileInputStream fis = new FileInputStream(lexersText);
			InputStreamReader isr = new InputStreamReader(fis);
			LineNumberReader lnr = new LineNumberReader(isr);

			// Pattern for parsing each line of specification file
			Pattern lxRule = Pattern.compile(lxRuleString);
			while (true) {
				String line = lnr.readLine();
				// read until file is exhausted
				if (line == null)
					break;
				Matcher matcher = lxRule.matcher(line);
				if (matcher.matches()) {
					// add rules to the list of rules
					String name = matcher.group(1);
					String regex = matcher.group(2);
					lexerMap.put(regex,name);
				} else {
					System.err.println("Syntax error in " + lexersText
							+ " at line " + lnr.getLineNumber());
					System.exit(1);
				}
			}
			// close the file
			fis.close();
		} catch (IOException ioe) {
			System.err.println("IOException!");
		}
		return lexerMap;
	}

	/**
	 * Convert the lexers text to lexer xml.
	 * 
	 * @param lexersXML
	 */
	private void convert(String lexersXML) {
		new LexiconMarshaller().marshal(lexerMap, lexersXML);
	}

	public static void main(String[] args) {
		LexerRuleConverter lexerRuleConverter = new LexerRuleConverter();
		lexerRuleConverter.load("resources/lexers/lexers.txt");
		lexerRuleConverter.convert("resources/lexers/lexers.xml");
		System.out.println("Done!");
	}
}
