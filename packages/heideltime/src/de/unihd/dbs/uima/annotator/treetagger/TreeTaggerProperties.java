/**
 * Settings class with variables and helper methods to use with TreeTaggerWrapper
 */
package de.unihd.dbs.uima.annotator.treetagger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * 
 * @author Julian Zell
 *
 */
public class TreeTaggerProperties {
	// treetagger language name for par files
	public String languageName = null;
	
	// absolute path of the treetagger
	public String rootPath = null;

	// Files for tokenizer and part of speech tagger (standard values)
	public String tokScriptName = null;
	public String parFileName = null;
	public String abbFileName = null;

	// english, italian, and french tagger models require additional splits (see tagger readme)
	public String languageSwitch = null;

	// perl requires(?) special hint for utf-8-encoded input/output (see http://perldoc.perl.org/perlrun.html#Command-Switches -C)
	// The input text is read in HeidelTimeStandalone.java and always translated into UTF-8,
	// i.e., switch always "-CSD"
	public String utf8Switch = "-CSD";
	
	// save System-specific separators for string generation
	public String newLineSeparator = System.getProperty("line.separator");
	public String fileSeparator = System.getProperty("file.separator");
	
	// chinese tokenizer path
	public File chineseTokenizerPath = null;
	
	
	/**
	 * This method creates a process with some parameters for the tokenizer script.
	 * 
	 * Deprecated: We use TreeTaggerTokenizer in the same package nowadays which implements the utf8-tokenize.perl
	 * script from the TreeTagger package. This fixes some issues with Perl's Unicode handling.
	 * @param inputFile
	 * @return
	 * @throws IOException
	 */
	@Deprecated
	public Process getTokenizationProcess(File inputFile) throws IOException {
		// assemble a command line for the tokenization script and execute it
		ArrayList<String> command = new ArrayList<String>();
		command.add("perl");
		if(this.utf8Switch != "")
			command.add(this.utf8Switch);
		command.add(this.rootPath + this.fileSeparator + "cmd" + this.fileSeparator + this.tokScriptName);
		if(this.languageSwitch != "")
			command.add(this.languageSwitch);
		if(new File(this.rootPath + this.fileSeparator + "lib" + this.fileSeparator, this.abbFileName).exists()) {
			command.add("-a");
			command.add(this.rootPath + this.fileSeparator + "lib" + this.fileSeparator + this.abbFileName);
		}
		command.add(inputFile.getAbsolutePath());
		
		String[] commandStr = new String[command.size()];
		command.toArray(commandStr);
		Process p = Runtime.getRuntime().exec(commandStr);
		
		return p;
	}
	
	public Process getChineseTokenizationProcess() throws IOException {
		// assemble a command line for the tokenization script and execute it
		ArrayList<String> command = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(this.chineseTokenizerPath, "segment-zh.pl")))); 
		String segmenterScript = "";
		String buf = null;
		Boolean firstLine = true;
		
		// this dirty hack is to force the script to autoflush its buffers. thanks, PERL
		while((buf = br.readLine()) != null) {
			// set the lexicon files
			if(buf.startsWith("$lexicon="))
				buf = "$lexicon=\"" + new File(this.chineseTokenizerPath, "lcmc-uni2.dat").getAbsolutePath() + "\";";
			if(buf.startsWith("$lexicon2=")) 
				buf = "$lexicon2=\"" + new File(this.chineseTokenizerPath, "lcmc-bigrams2.dat").getAbsolutePath() + "\";";
			
			// add the autoflush variable
			if(firstLine) {
				segmenterScript += "$| = 1;";
				firstLine = false;
			}
			
			// we omit comments
			if(!buf.startsWith("#"))
				segmenterScript += buf;
		}
		br.close();
		
		command.add("perl");
		command.add("-X");
		command.add("-e");
		command.add(segmenterScript);
		
		String[] commandStr = new String[command.size()];
		command.toArray(commandStr);

		ProcessBuilder builder = new ProcessBuilder(commandStr);
		builder.directory(this.chineseTokenizerPath);
		
		return builder.start();
	}
	
	public Process getTreeTaggingProcess(File inputFile) throws IOException {
		// assemble a command line based on configuration and execute the POS tagging.
		ArrayList<String> command = new ArrayList<String>();
		command.add(this.rootPath + this.fileSeparator + "bin" + this.fileSeparator + "tree-tagger");
		command.add(this.rootPath + this.fileSeparator + "lib" + this.fileSeparator + this.parFileName);
		command.add(inputFile.getAbsolutePath());
		command.add("-no-unknown");
		
		String[] commandStr = new String[command.size()];
		command.toArray(commandStr);
		
		return Runtime.getRuntime().exec(commandStr);
	}
}