package vn.hus.nlp.tokenizer;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vn.hus.nlp.fsm.IConstants;
import vn.hus.nlp.lexicon.LexiconUnmarshaller;
import vn.hus.nlp.lexicon.jaxb.Corpus;
import vn.hus.nlp.lexicon.jaxb.W;
import vn.hus.nlp.tokenizer.io.Outputer;
import vn.hus.nlp.tokenizer.segmenter.Segmenter;
import vn.hus.nlp.tokenizer.tokens.LexerRule;
import vn.hus.nlp.tokenizer.tokens.TaggedWord;
import vn.hus.nlp.tokenizer.tokens.WordToken;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE Hong Phuong, phuonglh@gmail.com
 * <p>
 * The Vietnamese tokenizer.
 */

public class Tokenizer  {

	/**
	 * List of rules for this lexer
	 */
	private LexerRule rules[] = new LexerRule[0];

	/**
	 * The current input stream
	 */
	private InputStream inputStream;

	/**
	 * Current reader, keep track of our position within the input file
	 */
	private LineNumberReader lineReader;

	/**
	 * Current line
	 */
	private String line;

	/**
	 * Current column
	 */
	private int column;

	/**
	 * A list of tokens containing the result of tokenization
	 */
	private List<TaggedWord> result = null;

	/**
	 * A lexical segmenter
	 */
	private final Segmenter segmenter;
	/**
	 * A lexer token outputer
	 */
	private Outputer outputer = null;
	/**
	 * A list of tokenizer listeners
	 */
	private final List<ITokenizerListener> tokenizerListener = new ArrayList<ITokenizerListener>();
	/**
	 * Are ambiguities resolved? True by default.
	 */
	private boolean isAmbiguitiesResolved = true;
	
	private Logger logger;
	
	private final ResultMerger resultMerger;

	private final ResultSplitter resultSplitter;
	
	
	/**
	 * Creates a tokenizer from a lexers filename and a segmenter.
	 * @param lexersFilename the file that contains lexer rules 
	 * @param segmenter a lexical segmenter<ol></ol>
	 */
	public Tokenizer(String lexersFilename, Segmenter segmenter) {
		// load the lexer rules
		loadLexerRules(lexersFilename);
		this.segmenter = segmenter;
		result = new ArrayList<TaggedWord>();
		// use a plain (default) outputer
		createOutputer();
		// create result merger
		resultMerger = new ResultMerger();
		// create a result splitter
		resultSplitter = new ResultSplitter();
		// create logger
		createLogger();
		// add a simple tokenizer listener for reporting 
		// tokenization progress
		addTokenizerListener(new SimpleProgressReporter());
	}
	
	/**
	 * Creates a tokenizer from a properties object and a segmenter. This is 
	 * the prefered way to create a tokenizer. 
	 * @param properties
	 * @param segmenter
	 */
	public Tokenizer(Properties properties, Segmenter segmenter) {
		// load the lexer rules
		loadLexerRules(properties.getProperty("lexers"));
		this.segmenter = segmenter;
		result = new ArrayList<TaggedWord>();
		// use a plain (default) outputer
		createOutputer();
		// create result merger
		resultMerger = new ResultMerger();
		// create a result splitter
		resultSplitter = new ResultSplitter(properties);
		// create logger
		createLogger();
		// add a simple tokenizer listener for reporting 
		// tokenization progress
		addTokenizerListener(new SimpleProgressReporter());
	}
	
	private void createOutputer() {
		if (outputer == null) {
			outputer = new Outputer();
		}
	}

	/**
	 * @return an outputer
	 */
	public Outputer getOutputer() {
		return outputer;
	}
	
	/**
	 * @param outputer The outputer to set.
	 */
	public void setOutputer(Outputer outputer) {
		this.outputer = outputer;
	}
	
	
	private void createLogger() {
		if (logger == null) {
			logger = Logger.getLogger(Segmenter.class.getName());
			// use a console handler to trace the log
//			logger.addHandler(new ConsoleHandler());
			try {
				logger.addHandler(new FileHandler("tokenizer.log"));
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			logger.setLevel(Level.FINEST);
		}
	}

	/**
	 * Load lexer specification file. This text file contains lexical rules to
	 * tokenize a text
	 * 
	 * @param lexersFilename
	 *            specification file
	 */
	private void loadLexerRules(String lexersFilename) {
		LexiconUnmarshaller unmarshaller = new LexiconUnmarshaller();
		Corpus corpus = unmarshaller.unmarshal(lexersFilename);
		ArrayList<LexerRule> ruleList = new ArrayList<LexerRule>();
		List<W> lexers = corpus.getBody().getW();
		for (W w : lexers) {
			LexerRule lr = new LexerRule(w.getMsd(), w.getContent());
//			System.out.println(w.getMsd() + ": " + w.getContent());
			ruleList.add(lr);
		}
		// convert the list of rules to an array and save it
		rules = ruleList.toArray(rules);
	}

	/**
	 * Tokenize a reader. If ambiguities are not resolved, this method
	 * selects the first segmentation for a phrase if there are more than one
	 * segmentations. Otherwise, it selects automatically the most
	 * probable segmentation returned by the ambiguity resolver. 
	 */
	public void tokenize(Reader reader) throws IOException {
		// Firstly, the result list is emptied
		result.clear();
		lineReader = new LineNumberReader(reader);
		// position within the file
		line = null;
		column = 1;
		// do tokenization
		while (true) {
			// get the next token
			TaggedWord taggedWord = getNextToken();
			// stop if there is no more token
			if (taggedWord == null) {
				break;
			}
//			// DEBUG 
//			System.out.println("taggedWord = " + taggedWord);
			// if this token is a phrase, we need to use a segmenter
			// object to segment it.
			if (taggedWord.isPhrase()) {
//				System.out.println("taggedWord phrase = " + taggedWord);
				String phrase = taggedWord.getText().trim();
				if (!isSimplePhrase(phrase)) {
					String[] tokens = null;
					// segment the phrase
					List<String[]> segmentations = segmenter.segment(phrase);
					if (segmentations.size() == 0) {
						logger.log(Level.WARNING, "The segmenter cannot segment the phrase \"" + phrase + "\"");
					}
					// resolved the result if there is such option
					// and the there are many segmentations.
					if (isAmbiguitiesResolved() && segmentations.size() > 1) {
						tokens = segmenter.resolveAmbiguity(segmentations);
					} else {
						// get the first segmentation
						Iterator<String[]> it = segmentations.iterator();
						if (it.hasNext()) {
							tokens = it.next();
						} 
					}
					if (tokens == null) {
						logger.log(Level.WARNING, "Problem: " + phrase);
					}
					
					// build tokens of the segmentation
					for (int j = 0; j < tokens.length; j++) {
						WordToken token = new WordToken(
								new LexerRule("word"), tokens[j], lineReader.getLineNumber(), column);
						result.add(token);
						column += tokens[j].length();
					}
				} else { // phrase is simple
					if (phrase.length() > 0)
						result.add(taggedWord);
				}
			} else { // lexerToken is not a phrase
				// check to see if it is a named entity
				if (taggedWord.isNamedEntity()) {
					// try to split the lexer into two lexers
					TaggedWord[] tokens = resultSplitter.split(taggedWord);
					if (tokens != null) {
						for (TaggedWord token : tokens) {
							result.add(token);
						}
					} else {
						result.add(taggedWord);
					}
				} else {
					// we simply add it into the list
					if (taggedWord.getText().trim().length() > 0) {
						result.add(taggedWord);
					}
				}
			}
			// ok, the token has been processed,
			// it is now reported to all registered listeners
			fireProcess(taggedWord);
		}
		// close the line reader
		if (lineReader != null)
			lineReader.close();
		// merge the result
		result = resultMerger.mergeList(result);
	}


	/**
	 * Tokenize a file.
	 * 
	 * @param filename
	 *            a filename
	 */
	public void tokenize(String filename) {
		// create a UTF8 reader
		UTF8FileUtility.createReader(filename);
		try {
			tokenize(UTF8FileUtility.reader);
		} catch (IOException e) {
			e.printStackTrace();
		}
		UTF8FileUtility.closeReader();
	}
	
	/**
	 * A phrase is simple if it contains only one syllable.
	 * @param phrase
	 * @return true/false
	 */
	private boolean isSimplePhrase(String phrase) {
		phrase = phrase.trim();
		if (phrase.indexOf(IConstants.BLANK_CHARACTER) >= 0)
			return false;
		else return true;
	}

	/**
	 * Return the next token from the input. This old version is deprecated.
	 * 
	 * @return next token from the input
	 * @throws IOException
	 * @see {@link #getNextToken()}
	 */
	@SuppressWarnings("unused")
	@Deprecated
	private TaggedWord getNextTokenOld() throws IOException {
		// scan the file line by line and quit when no more lines are left
		if (line == null || line.length() == 0) {
			line = lineReader.readLine();
			if (line == null) {
				if (inputStream != null)
					inputStream.close();
				lineReader = null;
				return null;
			}
			// an empty line:
			if (line.length() == 0) {
				return new TaggedWord("\n");
			}
			column = 1;
		}
		// match the next token
		TaggedWord token = null;
		// the end of the next token, within the line
		int tokenEnd = -1;
		// the length of the rule that matches the most characters from the
		// input
		int longestMatchLen = -1;
		String text = "";
		// find the rule that matches the longest substring of the input
		for (int i = 0; i < rules.length; i++) {
			LexerRule rule = rules[i];
			// get the precompiled pattern for this rule
			Pattern pattern = rule.getPattern();
			// create a matcher to perform match operations on the string 
			// by interpreting the pattern
			Matcher matcher = pattern.matcher(line);
			// if there is a match, calculate its length
			// and compare it with the longest match len
			// Here, we attempts to match the input string, starting at the beginning, against the pattern.
			// The method lookingAt() always starts at the beginning of the region; 
			// unlike that method, it does not require that the entire region be matched. 
			// This method returns true if, and only if, a prefix of the input sequence matches 
			// this matcher's pattern
			// Problem: if the string is "abc xyz@gmail.com", the next word will be "abc xyz", 
			// which is a wrong segmentation! Need a less greedy method to overcome this shortcomming.
			if (matcher.lookingAt()) {
				int matchLen = matcher.end();
				if (matchLen > longestMatchLen) {
					longestMatchLen = matchLen;
					text = matcher.group(0);
					System.err.println(rule.getName() + ": " + text);
					int lineNumber = lineReader.getLineNumber();
					token = new TaggedWord(rule, text, lineNumber, column);
					tokenEnd = matchLen;
				}
			}
		}
		// if we didn't match anything, we exit...
		if (token == null) {
			logger.log(Level.WARNING, "Error! line = " + lineReader.getLineNumber()
					+ ", col = " + column);
			System.out.println(line);
			System.exit(1);
			return null;
		} else {
			// we match something, skip past the token, get ready
			// for the next match, and return the token
			column += tokenEnd;
			line = line.substring(tokenEnd);
			return token;
		}
	}

	/**
	 * Return the next token from the input. Version 2, less greedy
	 * method than version 1.
	 * 
	 * @return next token from the input
	 * @throws IOException
	 */
	private TaggedWord getNextToken() throws IOException {
		// scan the file line by line and quit when no more lines are left
		if (line == null || line.length() == 0) {
			line = lineReader.readLine();
			if (line == null) {
				if (inputStream != null)
					inputStream.close();
				lineReader = null;
				return null;
			}
			// an empty line corresponds to an empty tagged word
			if (line.trim().length() == 0) {
				System.err.println("Create an empty line tagged word...");
				//return new TaggedWord(new LexerRule("return", "(\\^\\$)"), "\n");
				return new TaggedWord(new LexerRule("return"), "\n");
			}
			column = 1;
		}
		// match the next token
		TaggedWord token = null;
		// the end of the next token, within the line
		int tokenEnd = -1;
		// the length of the rule that matches the most characters from the
		// input
		int longestMatchLen = -1;
		int lineNumber = lineReader.getLineNumber();
		LexerRule selectedRule = null;   
		// find the rule that matches the longest substring of the input
		for (int i = 0; i < rules.length; i++) {
			LexerRule rule = rules[i];
			// get the precompiled pattern for this rule
			Pattern pattern = rule.getPattern();
			// create a matcher to perform match operations on the string 
			// by interpreting the pattern
			Matcher matcher = pattern.matcher(line);
			// find the longest match from the start 
			if (matcher.lookingAt()) {
				int matchLen = matcher.end();
				if (matchLen > longestMatchLen) {
					longestMatchLen = matchLen;
					tokenEnd = matchLen;
					selectedRule = rule;
				}
			}
		}
		// 
		// check if this relates to an email address (to fix an error with email) 
		// yes, I know that this "manual" method must be improved by a more general way.
		// But at least, it can fix an error with email addresses at the moment. :-)
		int endIndex = tokenEnd;
		if (tokenEnd < line.length()) {
			if (line.charAt(tokenEnd) == '@') {
				while (endIndex > 0 && line.charAt(endIndex) != ' ') {
					endIndex--;
				}
			}
		}
		// the following statement fixes the error reported by hiepnm, for the case like "(School@net)"
		if (endIndex == 0) {
			endIndex = tokenEnd;
		}
		
		if (selectedRule == null) {
			selectedRule = new LexerRule("word");
		}
		String text = line.substring(0, endIndex);
		token = new TaggedWord(selectedRule, text, lineNumber, column);
		// we match something, skip past the token, get ready
		// for the next match, and return the token
		column += endIndex;
		line = line.substring(endIndex).trim();
//		System.out.println(line);
		return token;
	}
	
	/**
	 * Export the result of tokenization to a text file, the output
	 * format is determined by an outputer
	 * 
	 * @param filename a file to export the result to
	 * @param outputer an outputer
	 * @see vn.hus.nlp.tokenizer.io.IOutputFormatter
	 */
	public void exportResult(String filename, Outputer outputer) {
		System.out.print("Exporting result of tokenization...");
		try {
			FileOutputStream fos = new FileOutputStream(filename);
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			BufferedWriter bw = new BufferedWriter(osw);
			bw.write(outputer.output(result));
			bw.flush();
			bw.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("OK.");
	}
	/**
	 * Export the result of tokenization to a text file using the plain output
	 * format.
	 * @param filename
	 */
	public void exportResult(String filename) {
		System.out.print("Exporting result of tokenization...");
		UTF8FileUtility.createWriter(filename);
		for (Iterator<TaggedWord> iter = result.iterator(); iter.hasNext();) {
			TaggedWord token = iter.next();
			UTF8FileUtility.write(token.toString() + "\n");
		}
		UTF8FileUtility.closeWriter();
		System.out.println("OK");
	}
	
	/**
	 * @return Returns the a result of tokenization.
	 */
	public List<TaggedWord> getResult() {
		return result;
	}

	/**
	 * @param result
	 *            The result to set.
	 */
	public void setResult(List<TaggedWord> result) {
		this.result = result;
	}


	
	/**
	 * Adds a listener
	 * @param listener a listener to add
	 */
	public void addTokenizerListener(ITokenizerListener listener) {
		tokenizerListener.add(listener);
	}
	/**
	 * Removes a tokenier listener
	 * @param listener a listener to remove
	 */
	public void removeTokenizerListener(ITokenizerListener listener) {
		tokenizerListener.remove(listener);
	}
	/**
	 * Get all the tokenizer listener
	 * @return a list of listeners
	 */
	public List<ITokenizerListener> getTokenizerListener() {
		return tokenizerListener;
	}
	
	/**
	 * Reports process of the tokenization to all listener
	 * @param token the processed token
	 */
	private void fireProcess(TaggedWord token) {
		for (ITokenizerListener listener : tokenizerListener) {
			listener.processToken(token);
		}
	}
	
	/**
	 * Dispose the tokenizer
	 *
	 */
	public void dispose() {
		// dispose the segmenter
		segmenter.dispose();
		// clear all lexer tokens
		result.clear();
		// remove all tokenizer listeners
		tokenizerListener.clear();
	}
	
	/**
	 * Return <code>true</code> if the ambiguities are resolved by
	 * a resolver. The default value is <code>false</code>. 
	 * @return
	 */
	public boolean isAmbiguitiesResolved() {
		return isAmbiguitiesResolved;
	}
	/**
	 * Is the ambiguity resolved?
	 * @param b <code>true/false</code>
	 */
	public void setAmbiguitiesResolved(boolean b) {
		isAmbiguitiesResolved = b;
	}
	
	/**
	 * Return the lexical segmenter
	 * @return
	 */
	public Segmenter getSegmenter() {
		return segmenter;
	}

	/**
	 * @author Le Hong Phuong, phuonglh@gmail.com
	 * <p>
	 * 8 juil. 2009, 22:57:19
	 * <p>
	 * A simple listener for reporting tokenization progress.
	 */
	private class SimpleProgressReporter implements ITokenizerListener {

		@Override
		public void processToken(TaggedWord token) {
			// report some simple progress
			if (result.size() % 1000 == 0) {
				System.out.print(".");
			}
		}
		
	}
}
