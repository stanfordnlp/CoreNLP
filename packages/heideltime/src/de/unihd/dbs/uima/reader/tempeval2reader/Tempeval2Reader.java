/*
 * TempEval2Reader.java
 * 
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * The TempEval2 Reader reads TempEval-2 corpora.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.reader.tempeval2reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import de.unihd.dbs.uima.types.heideltime.Dct;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Token;

/**
 * CollectionReader for TempEval Data 
 */
public class Tempeval2Reader extends CollectionReader_ImplBase {
	/**
	 * Logger for this class
	 */
	private static Logger logger = null;
	
	/**
	 * ComponentId
	 */
	private static final String compontent_id = "de.unihd.dbs.uima.reader.tempeval2reader";
	
	/**
	 * Parameter for files in the input directory
	 */
	public static final String FILE_BASE_SEGMENTATION  = "base-segmentation.tab";
	public static final String FILE_DCT                = "dct.tab";
	
	/**
	 * Needed information to create cas objects for all "documents"
	 */
	public Integer numberOfDocuments = 0;
	
	/**
	 * HashMap for all tokens of a document
	 */
	public HashMap<String, Token> hmToken = new HashMap<String, Token>();
	
	/**
	 * HashMap for all document creation times
	 */
	public HashMap<String, Dct> hmDct = new HashMap<String, Dct>();
	
	
  /**
   * Name of configuration parameter that must be set to the path of a directory
   * containing input files.
   */
	  public static final String PARAM_INPUTDIR  = "InputDirectory";
	  public static final String PARAM_CHARSET  = "Charset";
	  public static final String PARAM_USE_SPACES = "UseSpacesAsSeparators";

  /**
   * List containing all filenames of "documents"
   */
  private List<String> filenames = new ArrayList<String>();

  
  /**
   * Current file number
   */
  private int currentIndex;

  /**
   * Parentheses are given as "-LRB-" ... reset to "(" ...
   */
  
  Boolean resettingParentheses = true;
  
  /**
   * Check the TempEval counting beginning if "0" or "1"
   */
	int newTokSentNumber = 0;
	
  /**
   * Charset to use for reading in files
   */
  Charset charset = null;
	
  /**
   * Charset to use for reading in files
   */
  Boolean USE_SPACES = true;

  /**
   * 
   */
  public void initialize() throws ResourceInitializationException {
	  String charsetText = (String) getConfigParameterValue(PARAM_CHARSET);
	  if(charsetText == null || charsetText.equals(""))
		  charsetText = "UTF-8";
	  try {
		  charset = Charset.forName(charsetText);
	  } catch(Exception e) {
		  System.err.println("["+compontent_id+"] Charset " + charsetText + " was not available to be used.");
		  throw new ResourceInitializationException();
	  }

	  Boolean useSpaces = (Boolean) getConfigParameterValue(PARAM_USE_SPACES);
	  if(useSpaces != false) {
		  USE_SPACES = true;
	  } else {
		  USE_SPACES = false;
	  }
	   
	  // save doc names to list
	  List<File> inputFiles = getFilesFromInputDirectory();
	  
	  // get total document number and put all doc names into list "filenames"
	  numberOfDocuments = getNumberOfDocuments(inputFiles);
	  System.err.println("["+compontent_id+"] number of documents: "+numberOfDocuments);
  }
  
  
  public void getNext(CAS cas) throws IOException, CollectionException {
	  
	  // create jcas  
	  JCas jcas;
	  try {
		  jcas = cas.getJCas();
	  } catch (CASException e) {
		  throw new CollectionException(e);
	  }
	  
	  // clear HashMaps for new document
	  hmToken.clear();
	  hmDct.clear();
	  
	  // get current doc name 
	  String docname = filenames.get(currentIndex++);
	  
	  // save doc names to list
	  List<File> inputFiles = getFilesFromInputDirectory();
	  
	  // set documentText, sentences, tokens from file
	  setTextSentencesTokens(docname, inputFiles, jcas);
	  
	  // set document creation time (dct)
	  setDocumentCreationTime(docname, inputFiles, jcas);
  }

  /**
   * @see org.apache.uima.collection.CollectionReader#hasNext()
   */
  public boolean hasNext() throws IOException, CollectionException {
    return currentIndex < numberOfDocuments;
  }

  /**
   * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#getProgress()
   */
  public Progress[] getProgress() {
    return new Progress[] { new ProgressImpl(currentIndex, numberOfDocuments, Progress.ENTITIES) };
  }

  /**
   * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#close()
   */
  public void close() throws IOException {
  }

  
  
  public void setDocumentCreationTime(String docname, List<File> inputFiles, JCas jcas) throws IOException{
	  String documentCreationTime = "";
	  
	  String directory = (String) getConfigParameterValue(PARAM_INPUTDIR);
	  String filename = directory+"/"+FILE_DCT;
	  for (File file : inputFiles) {
		if (file.getAbsolutePath().equals(filename)){
			try {
				String line;
				BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
				while ((line = bf.readLine()) != null){
					String[] parts = line.split("(\t)+");
					String fileId  = parts[0];
					if (fileId.equals(docname)){
						documentCreationTime = parts[1];
						Dct dct = new Dct(jcas);
						String text = jcas.getDocumentText();
						dct.setBegin(0);
						dct.setEnd(text.length());
						dct.setFilename(fileId);
						dct.setValue(documentCreationTime);
						dct.setTimexId("t0");
						dct.addToIndexes();
						
						// add dct to HashMap
						hmDct.put("t0", dct);
					}
				}
				bf.close();
			}
			catch (IOException e){
				throw new IOException(e);
			}
			
		}
	  }
  }
  
  public void setTextSentencesTokens(String docname, List<File> inputFiles, JCas jcas) throws IOException{
	  String text        = "";
	  String sentString  = "";
	  Integer positionCounter = 0;
	  Integer sentId = -1;
	  Integer lastSentId = -1;
	  
	  String directory = (String) getConfigParameterValue(PARAM_INPUTDIR);
	  String filename = directory+"/"+FILE_BASE_SEGMENTATION;
	  for (File file : inputFiles) {
		if (file.getAbsolutePath().equals(filename)){
			try {
				String line;
				BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
				Boolean lastSentProcessed  = false;
				Boolean firstSentProcessed = false;
				String fileId = "";
				Boolean veryFirstLine = true;
				while ((line = bf.readLine()) != null){
					
					// Check if TempEval counting starts with "0" or "1"
					if (veryFirstLine){
						String[] parts = line.split("\t");
						newTokSentNumber = Integer.parseInt(parts[1]);
					}
					veryFirstLine = false;
					
					String[] parts = line.split("\t");
					fileId  = parts[0];
					sentId = Integer.parseInt(parts[1]);
					Integer tokId  = Integer.parseInt(parts[2]);

					// Check for "empty tokens" (Italian corpus)
					String tokenString = "";
					if (!(parts.length < 4)){
						tokenString = parts[3];	
					}
					
					if (resettingParentheses == true){
						tokenString = resetParentheses(tokenString);
					}
					
					if (fileId.equals(docname)){
						
						// First Sentence, first Token
						if ((sentId == newTokSentNumber) && (tokId == newTokSentNumber)){
							firstSentProcessed = true;
							text = tokenString;
							sentString  = tokenString;
							positionCounter = addTokenAnnotation(tokenString, fileId, sentId, tokId, positionCounter, jcas);
						}
						
						// new Sentence, first Token
						else if ((tokId == newTokSentNumber) || (lastSentId != sentId)){
							positionCounter = addSentenceAnnotation(sentString, fileId, sentId-1, positionCounter, jcas);
							if(!USE_SPACES) // in chinese, there are no spaces 
								text = text + tokenString;
							else
								text = text + " " + tokenString;
							sentString  = tokenString;
							positionCounter = addTokenAnnotation(tokenString, fileId, sentId, tokId, positionCounter, jcas);
						}
						
						// within any sentence
						else{
							if(!USE_SPACES) { // in chinese, there are no spaces 
								text = text + tokenString;
								sentString = sentString + tokenString;
							} else {
								text = text + " " + tokenString;
								sentString = sentString + " " + tokenString;
							}
							positionCounter = addTokenAnnotation(tokenString, fileId, sentId, tokId, positionCounter, jcas);
						}
					}
					else{
						if ((firstSentProcessed) && (!(lastSentProcessed))){
							positionCounter = addSentenceAnnotation(sentString, docname, lastSentId, positionCounter, jcas);
							lastSentProcessed = true;
						}
					}
					lastSentId = sentId;
				}
				if (fileId.equals(docname)){
					positionCounter = addSentenceAnnotation(sentString, docname, lastSentId, positionCounter, jcas);
				}
				
				bf.close();
			}catch (IOException e){
				throw new IOException(e);
			}
		}
	  }
	  jcas.setDocumentText(text);	  
  }
  
  public String resetParentheses(String tokenString){
	  if (tokenString.equals("-LRB-")){
		  tokenString = tokenString.replace("-LRB-", "(");
	  }
	  else if (tokenString.equals("-RRB-")){
		  tokenString = tokenString.replace("-RRB-", ")");
	  }
	  else if (tokenString.equals("-LSB-")){
		  tokenString = tokenString.replace("-LSB-","[");
	  }
	  else if (tokenString.equals("-RSB-")){
		  tokenString = tokenString.replace("-RSB-","]");
	  }
	  else if (tokenString.equals("-LCB-")){
		  tokenString = tokenString.replace("-LCB-","{");
	  }
	  else if (tokenString.equals("-RCB-")){
		  tokenString = tokenString.replace("-RCB-","}");
	  }
	  // ITALIAN TEMPEVAL CORPUS PROBLEMS
	  else if (tokenString.endsWith("a'")){
		  tokenString = tokenString.replaceFirst("a'", "à");
	  }
	  else if (tokenString.endsWith("i'")){
		  tokenString = tokenString.replaceFirst("i'", "ì");
	  }
	  else if (tokenString.endsWith("e'")){
		  tokenString = tokenString.replaceFirst("e'", "è");
	  }
	  else if (tokenString.endsWith("u'")){
		  tokenString = tokenString.replaceFirst("u'", "ù");
	  }
	  else if (tokenString.endsWith("o'")){
		  tokenString = tokenString.replaceFirst("o'", "ò");
	  }
	  return tokenString;
  }
  
  public Integer addSentenceAnnotation(String sentenceString, String fileId, Integer sentId, Integer positionCounter, JCas jcas){
	  Sentence sentence = new Sentence(jcas);
	  Integer begin = positionCounter - sentenceString.length();
	  sentence.setFilename(fileId);
	  sentence.setSentenceId(sentId);
	  sentence.setBegin(begin);
	  sentence.setEnd(positionCounter);
	  sentence.addToIndexes();
	  return positionCounter;
  }
  
  
  
  /**
   * Add token annotation to jcas
   * @param tokenString
   * @param fileId
   * @param tokId
   * @param positionCounter
   * @param jcas
   * @return
   */
  public Integer addTokenAnnotation(String tokenString, String fileId, Integer sentId, Integer tokId, Integer positionCounter, JCas jcas){
		Token token = new Token(jcas);
		if (!((sentId == newTokSentNumber) && (tokId == newTokSentNumber))){
			if(USE_SPACES) // in chinese, there are no spaces, so the +1 correction is unnecessary
				positionCounter = positionCounter + 1;
		}
		token.setBegin(positionCounter);
		positionCounter = positionCounter + tokenString.length();
		token.setEnd(positionCounter);
		token.setTokenId(tokId);
		token.setSentId(sentId);
		token.setFilename(fileId);
		token.addToIndexes();
		
		String id = fileId+"_"+sentId+"_"+tokId;
		hmToken.put(id, token);
		
		return positionCounter;
  }
  
  /**
   * count the number of different "documents" and save doc names in filenames
   * @param inputFiles
   * @return
   * @throws ResourceInitializationException
   */
  private Integer getNumberOfDocuments(List<File> inputFiles) throws ResourceInitializationException{
	  String directory = (String) getConfigParameterValue(PARAM_INPUTDIR);
	  String filename = directory+"/"+FILE_BASE_SEGMENTATION;
	  for (File file : inputFiles) {
		if (file.getAbsolutePath().equals(filename)){
			try {
				String line;
				BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
				while ((line = bf.readLine()) != null){
					String docName = (line.split("\t"))[0];
					if (!(filenames.contains(docName))){
						filenames.add(docName);
					}
				}
				bf.close();
			} catch (IOException e) {
				throw new ResourceInitializationException(e);
			}
		}
	  }	  
	  int  docCounter = filenames.size();
	  return docCounter;
  }
  
  
  private List<File> getFilesFromInputDirectory() {
	  // get directory and save 
	  File directory = new File(((String) getConfigParameterValue(PARAM_INPUTDIR)).trim());
	  List<File> documentFiles = new ArrayList<File>();
	  
	  
	  // if input directory does not exist or is not a directory, throw exception
	  if (!directory.exists() || !directory.isDirectory()) {
		  logger.log(Level.WARNING, "getFilesFromInputDirectory() " + directory
				  + " does not exist. Client has to set configuration parameter '" + PARAM_INPUTDIR + "'.");
		  return null;
	  }

	  // get list of files (not subdirectories) in the specified directory
	  File[] dirFiles = directory.listFiles();
	  for (int i = 0; i < dirFiles.length; i++) {
		  if (!dirFiles[i].isDirectory()) {
			  documentFiles.add(dirFiles[i]);
		  }
	  }
	  return documentFiles;
  }
  
}

