package hr.fer.zemris.takelab.splitter;

import java.util.ArrayList;
import java.util.List;

/**
 * A very simple implementation of a token splitter. Splitting is modeled after the splitting done by the CSTLemma lemmatiser.
 * @author Luka Skukan
 *
 */
public class TokenSplitter {

	/**
	 * Whitespace characters (vertical tab not included)
	 */
	private static String spaces = " \t\n\r";
	
	/**
	 * Punctuation characters (according to CSTLemma)
	 */
	private static String punctuation = ".,;?!:()";
	
	/**
	 * Takes a string and returns a List of all tokens contained within it.
	 * Any non-whitespace string of characters delimited by punctuation or whitespace is considered a token.
	 * Likewise, every instance of a punctuation character is also a token.
	 * @param sentence A string from which tokens are extracted
	 * @return List of tokens in given string
	 */
	public static List<String> getTokens(String sentence) {
		//Token container
		List<String> tokens = new ArrayList<String>();
		//Token building buffer
		StringBuilder buff = new StringBuilder();
		
		for(char c : sentence.toCharArray()) {
			//Spaces delimit tokens (if non-empty token)
			if(buff.length() > 0 && spaces.indexOf(c) != -1) {
				tokens.add(buff.toString());
				buff.setLength(0);
			//Punctuation both delimits non-empty tokens and IS a token
			} else if(punctuation.indexOf(c) != -1) {
				if(buff.length() > 0) {
					tokens.add(buff.toString());
					buff.setLength(0);
				}
				
				tokens.add(String.valueOf(c));
				
			//Non-whitespace is added to currently built token
			} else if(spaces.indexOf(c) == -1){
				buff.append(c);
			}
		}
		
		//If we've a token left in the buffer, wrap it up
		if(buff.length() > 0) {
			tokens.add(buff.toString());
		}
		
		return tokens;
	}
}
