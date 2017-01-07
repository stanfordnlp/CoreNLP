/*
 * Eventi2014Reader.java
 * 
 * Copyright (c) 2014, Database Research Group, Institute of Computer Science, Heidelberg University. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * The Eventi2014 Reader reads Eventi corpora.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.reader.eventi2014reader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.FileUtils;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.annotator.heideltime.utilities.Toolbox;
import de.unihd.dbs.uima.types.heideltime.Dct;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Token;

/**
 * CollectionReader for TempEval Data 
 */
public class Eventi2014Reader extends CollectionReader_ImplBase {
	private Class<?> component = this.getClass();
	
	// uima descriptor parameter name
	private String PARAM_INPUTDIR = "InputDirectory";
	
	private Integer numberOfDocuments = 0;
	
	// For improving the formatting of the documentText 
	// -> to not have a space between all the tokens
	// HashSet containing tokens in front of which no white space is added
	private HashSet<String> hsNoSpaceBefore = new HashSet<String>();
	private HashSet<String> hsNoSpaceBehind = new HashSet<String>();
	
	private Queue<File> files = new LinkedList<File>();
	
	public void initialize() throws ResourceInitializationException {
		String dirPath = (String) getConfigParameterValue(PARAM_INPUTDIR);
		dirPath = dirPath.trim();
		
		hsNoSpaceBefore.add(".");
		hsNoSpaceBefore.add(",");
		hsNoSpaceBefore.add(":");
		hsNoSpaceBefore.add(";");
		hsNoSpaceBefore.add("?");
		hsNoSpaceBefore.add("!");
		hsNoSpaceBefore.add(")");
		
		hsNoSpaceBehind.add("(");
		
		populateFileList(dirPath);
	}

	public void getNext(CAS aCAS) throws IOException, CollectionException {
		JCas jcas;
		
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new CollectionException(e);
		}

		fillJCas(jcas);
		
		// give an indicator that a file has been processed
		System.err.print(".");

		
		/*TODO:DEBUGGING
		FSIterator fsi = jcas.getAnnotationIndex(Token.type).iterator();
		while(fsi.hasNext())
			System.err.println("token: " + ((Token)fsi.next()).getTokenId());
		*/
	}

	private void fillJCas(JCas jcas) throws IOException, CollectionException {
		// grab a file to process
		File f = files.poll();
		
	    String text = "";   
	    String xml = FileUtils.file2String(f);
	    
	    String[] lines = xml.split("\n");
	    
	    String fullDctTag = "";
	    String dct = "";
	    String filename = "";
	    String lastTok = "";
	    int sentBegin = 0;
	    int sentEnd  = -1;
	    
	    for (String line : lines) {
	    	
	    	// get document name
			if (line.startsWith("<Document doc_name=")){
				Pattern paConstraint = Pattern.compile("<Document doc_name=\"(.*?)\">");
				for (MatchResult mr : Toolbox.findMatches(paConstraint,line)) {
					filename = mr.group(1);
				}
			}
			
			// handle the tokens
			if (line.startsWith("<token")){
				
				// get token text, token ID, token number, sentence number
				Pattern paConstraint = Pattern.compile("<token t_id=\"(.*?)\" sentence=\"(.*?)\" number=\"(.*?)\">(.*?)</token>");
				for (MatchResult mr : Toolbox.findMatches(paConstraint,line)) {

					String token   = mr.group(4); 
//					System.err.println("INPUT: -->" + token + "<--");
					int tokID   = Integer.parseInt(mr.group(1));
					int sentNum = Integer.parseInt(mr.group(2));
					int tokNum  = Integer.parseInt(mr.group(3));
					
					// prepare token annotation
					int tokBegin;
					int tokEnd;
					
					// first token in sentence
					if (text.equals("")){
						tokBegin = 0;
						tokEnd   = token.length();
						text  = token;
						lastTok = token;
					}
					else{
						// tokens without space before the tokens
						if (hsNoSpaceBefore.contains(token)){
							tokBegin = text.length();
							tokEnd   = tokBegin + token.length();
							text  = text + token;
							lastTok = token;
						}
//						// empty tokens
//						else if (token.equals("")){
//							tokBegin = text.length();
//							tokEnd   = tokBegin + token.length();
//							text  = text + token;
//							lastTok = token;
//						}
						else{
							// tokens without space behind the tokens
							if (!(hsNoSpaceBehind.contains(lastTok))){
								tokBegin = text.length()+ 1;
								text  = text + " " + token;
							}
							// all other tokens
							else{
								tokBegin = text.length();
								text = text + token;
							}
							tokEnd   = tokBegin + token.length();
							lastTok = token;
						}
					}
					// check for new sentences
					if (tokNum == 0){
						if (sentEnd >= 0){
							// add sentence annotation, once a new sentence starts
							addSentenceAnnotation(jcas, sentBegin, sentEnd, filename);
						}
						sentBegin = tokBegin;
					}
					// add the token annotation
					addTokenAnnotation(jcas, tokBegin, tokEnd, tokID, filename, sentNum, tokNum);
					sentEnd = tokEnd;
				}
			}
			
			// get the document creation time
			if (line.startsWith("<TIMEX3")){
				Pattern paConstraint = Pattern.compile("(<TIMEX3 .*? TAG_DESCRIPTOR=\"D[CP]T\" .*? value=\"(.*?)\".*?/>)");
				for (MatchResult mr : Toolbox.findMatches(paConstraint,line)) {
					fullDctTag = mr.group(1); 
					dct = mr.group(2);
					System.err.println("DCT: " + dct);
				}
			}
	    }
	    // add the very last sentence annotation
	    addSentenceAnnotation(jcas, sentBegin, sentEnd, filename);
	    jcas.setDocumentText(text);
	    
	    // add DCT to jcas
	    if (!(dct.equals(""))){
		    Dct dctAnnotation  = new Dct(jcas);
		    dctAnnotation.setBegin(0);
		    dctAnnotation.setEnd(text.length());
		    dctAnnotation.setFilename(filename + "---" + fullDctTag);
		    dctAnnotation.setValue(dct);
		    dctAnnotation.addToIndexes();
	    }
		
	}
	
	public void addSentenceAnnotation(JCas jcas, int begin, int end, String filename){
		Sentence sentAnnotation = new Sentence(jcas);
		sentAnnotation.setBegin(begin);
		sentAnnotation.setEnd(end);
		sentAnnotation.setFilename(filename);
		sentAnnotation.addToIndexes();
	}
	
	public void addTokenAnnotation(JCas jcas, int begin, int end, int tokID, String filename, int sentNum, int tokNum){
		Token tokenAnnotation = new Token(jcas);
		tokenAnnotation.setBegin(begin);
		tokenAnnotation.setEnd(end);
		tokenAnnotation.setTokenId(tokID);
		tokenAnnotation.setFilename(filename + "---" + sentNum + "---" + tokNum);
		tokenAnnotation.addToIndexes();
	}

	public boolean hasNext() throws IOException, CollectionException {
	    return files.size() > 0;
	}
	
	public Progress[] getProgress() {
		return new Progress[] { new ProgressImpl(numberOfDocuments-files.size(), numberOfDocuments , Progress.ENTITIES) };
	}
	
	public void close() throws IOException {
		files.clear();
	}

	private void populateFileList(String dirPath) throws ResourceInitializationException {
		ArrayList<File> myFiles = new ArrayList<File>();
		File dir = new File(dirPath);
		
		// check if the given directory path is valid
		if(!dir.exists() || !dir.isDirectory())
			throw new ResourceInitializationException();
		else
			myFiles.addAll(Arrays.asList(dir.listFiles()));
		
		// check for existence and readability; add handle to the list
		for(File f : myFiles) {
			if(!f.exists() || !f.isFile() || !f.canRead()) {
				Logger.printDetail(component, "File \""+f.getAbsolutePath()+"\" was ignored because it either didn't exist, wasn't a file or wasn't readable.");
			} else {
				files.add(f);
			}
		}
		
		numberOfDocuments = files.size();
	}
	
	
	
}



