package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.machinereading.domains.ace.reader.RobustTokenizer;

/**
 * Makes sure that ACE .sgm files have new lines only on end of sentence punctuation.
 * This is required because the true caser was trained with this behavior.
 */
public class NewLineOnlyOnEOS {
	private static final Pattern EOS = Pattern.compile("[\\.?!] ");
	private static final Pattern SGML_START = Pattern.compile("<[^<>/]+> ");
	private static final Pattern SGML_END = Pattern.compile(" </[^<>/]+>");
	public static final char CHANGE_EOS = ' ';
	
	public static void main(String[] args) throws Exception {
		RobustTokenizer.AbbreviationMap abbrevs = new RobustTokenizer.AbbreviationMap(true);
		StringBuffer content = simpleSplit();
		String text = content.toString();
		
		//
		// insert a newline for EOS in the middle of lines
		//
		Matcher m = EOS.matcher(text);
		int start = -2;
		while((m.find(start + 2))){
			start = m.start();
			//System.err.println("Found EOS at offset: " + start);
			String word = null;
			for(int i = start; i >= 0; i --){
				if(Character.isWhitespace(text.charAt(i))){
					word = text.substring(i + 1, start + 1);
					break;
				}
			}
			//System.err.println("\tWord: " + word);
			// found a legitimate EOS, i.e., word is not a known abbreviation
			if(word == null || ! abbrevs.contains(word.toLowerCase())){
				content.setCharAt(start + 1, '\n');
				//System.err.println("\tValid EOS!");
				// change the EOS character to white space. This helps with the CRF true caser, which otherwise things that that is an abbreviation
				if(word != null){
					content.setCharAt(start, CHANGE_EOS);
				}
			}
		}
		
		//
		// insert a newline after SGML begin tags in the middle of lines
		//
		text = content.toString();
		m = SGML_START.matcher(text);
		start = 0;
		while(m.find(start)){
			start = m.start();
			int end = m.end();
			content.setCharAt(end - 1, '\n');			
			start = end;
		}
		
		//
		// insert a newline before SGML end tags in the middle of lines
		//
		text = content.toString();
		m = SGML_END.matcher(text);
		start = 0;
		while(m.find(start)){
			start = m.start();
			int end = m.end();
			content.setCharAt(start, '\n');
			start = end;
		}
		
		System.out.print(content.toString());
	}
	
	/**
	 * Prints a new line only if EOS
	 * Note: this means we may end up with multiple sentences per line, if EOS in the middle of the line
	 * @throws Exception
	 */
	static StringBuffer simpleSplit() throws Exception {
	  BufferedReader is = new BufferedReader(new InputStreamReader(System.in));
	  List<String> lines = new ArrayList<String>();
	  String line;
	  while((line = is.readLine()) != null){
	  	lines.add(line);
	  }
	  is.close();
	  
	  StringBuffer total = new StringBuffer();
	  for(int i = 0; i < lines.size(); i ++){
	  	String crtLine = lines.get(i);
	  	String nextLine = (i < lines.size() - 1? lines.get(i + 1) : null);
	  	
	  	if(crtLine.endsWith(".") || 
	  			crtLine.endsWith("?") || 
	  			crtLine.endsWith("!")){
	  		total.append(crtLine.substring(0, crtLine.length() - 1));
				// change the EOS character to white space. This helps with the CRF true caser, which otherwise things that that is an abbreviation
	  		total.append(CHANGE_EOS);
	  		total.append("\n");
	  	} else if(nextLine == null || 
	  			nextLine.startsWith("<")){
	  		total.append(crtLine);
	  		total.append("\n");
	  	} else {
	  		total.append(crtLine);
	  		// replace newline with one space to maintain the same character offsets!
	  		total.append(" ");
	  	}
	  }
	  
	  return total;
  }
}
