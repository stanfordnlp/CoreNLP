/**
 * This is a Java port of the TreeTagger's cmd/utf8-tokenize.perl script
 * after it was altered in a way that made it unusable for invokation.
 */
package de.unihd.dbs.uima.annotator.treetagger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;

/**
 * 
 * @author Helmut Schmid, IMS, University of Stuttgart
 * 		Serge Sharoff, University of Leeds
 * 		Julian Zell, University of Heidelberg
 *
 */
public class TreeTaggerTokenizer {
	public static enum Flag {
		ENGLISH, FRENCH, ITALIAN, GALICIAN, Z;
		
		public static EnumSet<Flag> getSet(String flagName) {
			EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
			
			if(flagName == null)
				return set;
			
			if(flagName.contains("-e"))
				set.add(ENGLISH);
			if(flagName.contains("-f"))
				set.add(FRENCH);
			if(flagName.contains("-i"))
				set.add(ITALIAN);
			if(flagName.contains("-g"))
				set.add(GALICIAN);
			if(flagName.contains("-z"))
				set.add(Z);
			
			return set;
		}
	}
	EnumSet<Flag> flags = null;
	
	private File abbreviationsFile = null;
	
	private String PChar = "\\[¿¡\\{\\(\\`\"‚„†‡‹‘’“”•–—›'";
	private String FChar = "\\]\\}\\'\\`\"\\),;:\\!\\?\\%‚„…†‡‰‹‘’“”•–—›";
	private String FClitic = "";
	private String PClitic = "";
	
	private ArrayList<String> abbreviations = new ArrayList<String>();
	
	public TreeTaggerTokenizer(String abbreviationsFile, EnumSet<Flag> flags) throws RuntimeException {
		this.flags = flags;
		
		if(abbreviationsFile != null) {
			this.abbreviationsFile = new File(abbreviationsFile);
			
			if(!this.abbreviationsFile.exists() || !this.abbreviationsFile.canRead()) {
				Logger.printError(this.getClass(), "Couldn't read abbreviations file " + abbreviationsFile +
						" (exist:" + this.abbreviationsFile.exists() + ",read:" + this.abbreviationsFile.canRead() + ")");
				throw new RuntimeException();
			}
			
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(this.abbreviationsFile));
				String line = null;
				
				while((line = br.readLine()) != null) {
					line = line.replaceAll("^[ \t\r\n]+", "");
					line = line.replaceAll("[ \t\r\n]+$", "");
					
					if(!line.matches("^(#.*|\\s$)")) {
						abbreviations.add(line);
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				if(br != null) {
					try {
						br.close();
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		if(flags.contains(Flag.ENGLISH)) {
			FClitic = "'(s|re|ve|d|m|em|ll)|n't";
		}
		if(flags.contains(Flag.ITALIAN)) {
			PClitic = "[dD][ae]ll'|[nN]ell'|[Aa]ll'|[lLDd]'|[Ss]ull'|[Qq]uest'|[Uu]n'|[Ss]enz'|[Tt]utt'";
		}
		if(flags.contains(Flag.FRENCH)) {
			PClitic = "[dcjlmnstDCJLNMST]'|[Qq]u'|[Jj]usqu'|[Ll]orsqu'";
			FClitic = "-t-elles?|-t-ils?|-t-on|-ce|-elles?|-ils?|-je|-la|-les?|-leur|-lui|-mmes?|-m'|-moi|-nous|-on|-toi|-tu|-t'|-vous|-en|-y|-ci|-l";
		}
		if(flags.contains(Flag.GALICIAN)) {
			FClitic = "-la|-las|-lo|-los|-nos";
		}
	}
	
	public List<String> tokenize(String in) {
		StringBuilder outBuf = new StringBuilder();
		
		for(String text : in.split("\n")) {
			// replace newlines and tab characters with blanks
			text = text.replaceAll("[\r\n\t]", " ");
			// replace blanks within SGML tags
			text = text.replaceAll("(<[^<> ]*) ([^<>]*>)", "$1\377$2");
			// replace whitespace with a special character
			text = text.replaceAll("[\\u2000-\\u200A ]", "\376");
			// restore SGML tags
			text = text.replaceAll("\377", " ");
			text = text.replaceAll("\376", "\377");
			// prepare SGML-Tags for tokenization
			text = text.replaceAll("(<[^<>]*>)", "\377$1\377");
			text = text.replaceAll("^\377", "");
			text = text.replaceAll("\377$", "");
			text = text.replaceAll("\377\377\377*", "\377");
			
			String[] texts = text.split("\377");
			
			for(String line : texts) {
				if(line.matches("^<.*>$")) {
					// SGML tag
					outBuf.append(line + "\n");
				} else {
					// add a blank at the beginning and the end of each segment
					line = " " + line + " ";
					
					// insert missing blanks after punctuation
					line = line.replaceAll("\\.\\.\\.", " ... ");
					line = line.replaceAll("([;\\!\\?])([^ ])", "$1 $2");
					line = line.replaceAll("([.,:])([^ 0-9.])", "$1 $2");
					
					String[] lines = line.split(" ");
					
					for(String token : lines) {
						// remove some whitespaces that \s doesn't catch
						if(token.equals(""))
							continue;
						
						String suffix = "";
						
						// separate punctuation and parentheses from words
						Boolean finished = false;
						Matcher m;
						do {
							finished = true;
							
							// cut off preceding punctuation
							m = Pattern.compile("^([" + PChar + "])(.)").matcher(token);
							if(m.find()) {
								token = token.replaceAll("^([" + PChar + "])(.)", "$2");
								outBuf.append(m.group(1) + "\n");
								finished = false;
							}
							
							// cut off trailing punctuation
							m = Pattern.compile("(.)([" + FChar + "])$").matcher(token);
							if(m.find()) {
								token = token.replaceAll("(.)([" + FChar + "])$", "$1");
								suffix = m.group(2) + "\n" + suffix;
								finished = false;
							}
							
							// cut off trailing periods if punctuation precedes
							m = Pattern.compile("([" + FChar + "])\\.$").matcher(token);
							if(m.find()) {
								token = token.replaceAll("([" + FChar + "])\\.$", "");
								suffix = ".\n" + suffix;
								
								if(token.equals("")) {
									token = m.group(1);
								} else {
									suffix = m.group(1) + "\n" + suffix;
								}
								
								finished = false;
							}
						} while(!finished);
						
						// handle explicitly listed tokens
						if(abbreviations.contains(token)) {
							outBuf.append(token + "\n" + suffix);
							continue;
						}
						
						// abbreviations of the form A. or U.S.A.
						if(token.matches("^([A-Za-z-]\\.)+$")) {
							outBuf.append(token + "\n" + suffix);
							continue;
						}
						
						// disambiguate periods
						m = Pattern.compile("^(..*)\\.$").matcher(token);
						if(m.matches() && !line.equals("...") 
								&& !(flags.contains(Flag.GALICIAN) && token.matches("^[0-9]+\\.$"))) {
							token = m.group(1);
							suffix = ".\n" + suffix;
							if(abbreviations.contains(token)) {
								outBuf.append(token + "\n" + suffix);
								continue;
							}
						}
						
						// cut off clitics
						while(true) {
							m = Pattern.compile("^(--)(.)").matcher(token);
							
							if(!m.find()) {
								break;
							}
							
							token = token.replaceAll("^(--)(.)", "$2");
							outBuf.append(m.group(1) + "\n");
						}
						if(!PClitic.equals("")) {
							while(true) {
								m = Pattern.compile("^(" + PClitic + ")(.)").matcher(token);
								
								if(!m.find()) {
									break;
								}
								
								token = token.replaceAll("^(" + PClitic + ")(.)", "$2");
								outBuf.append(m.group(1) + "\n");
							}
						}
	
						while(true) {
							m = Pattern.compile("^(--)(.)").matcher(token);
							
							if(!m.find()) {
								break;
							}
							
							token = token.replaceAll("^(--)(.)", "$1");
							suffix = m.group(2) + "\n" + suffix;
						}
						if(!FClitic.equals("")) {
							while(true) {
								m = Pattern.compile("(.)(" + FClitic + ")$").matcher(token);
								
								if(!m.find()) {
									break;
								}
								
								token = token.replaceAll("(.)(" + FClitic + ")$", "$1");
								suffix = m.group(2) + "\n" + suffix;
							}
						}
						outBuf.append(token + "\n" + suffix);
					}
				}
			}
		}
		
		LinkedList<String> outList = new LinkedList<String>();
		
		for(String s : outBuf.toString().split("\n")) {
			outList.add(s);
		}
		
		return outList;
	}
}
