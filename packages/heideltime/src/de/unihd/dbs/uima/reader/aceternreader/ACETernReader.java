/*
 * ACETernReader.java
 * 
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * ACE Tern Reader reads temporal annotated corpora that are in the ACE Tern style.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.reader.aceternreader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.FileUtils;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import de.unihd.dbs.uima.types.heideltime.Dct;
import de.unihd.dbs.uima.types.heideltime.SourceDocInfo;



/**
 * CollectionReader for ACE Tern Data 
 */
public class ACETernReader extends CollectionReader_ImplBase {
	
	private static Logger logger = null;
	
	private static final String compontent_id = "de.unihd.dbs.uima.reader.aceternreader";
	
	/**
	 * Needed information to create cas objects for all "documents"
	 */
	public Integer numberOfDocuments = 0;
  
	/**
	 * Parameter information
	 */
	public static final String PARAM_INPUTDIR = "InputDirectory";
	public static final String PARAM_DCT      = "AnnotateCreationTime";
	public Boolean annotateDCT = false; 

	/**
	 * List containing all filenames of "documents"
	 */
	private ArrayList<File> mFiles;
    
	/**
	 * Current file number
	 */
	private int currentIndex;

  

	
	/**
	 * @see org.apache.uima.collection.CollectionReader_ImplBase#initialize()
	 */
	public void initialize() throws ResourceInitializationException {
		
		logger = getUimaContext().getLogger();
		logger.log(Level.INFO, "initialize() - Initializing ACETern-Reader...");
		
		annotateDCT = (Boolean) getConfigParameterValue(PARAM_DCT);
		
		File directory = new File(((String) getConfigParameterValue(PARAM_INPUTDIR)).trim());
		currentIndex = 0;
		
		// if input directory does not exist or is not a directory, throw exception
		if (!directory.exists() || !directory.isDirectory()) {
			throw new ResourceInitializationException(ResourceConfigurationException.DIRECTORY_NOT_FOUND,
					new Object[] { PARAM_INPUTDIR, this.getMetaData().getName(), directory.getPath() });
		}

		// get list of files (without subdirectories) in the specified directory
		mFiles = new ArrayList<File>();
		File[] files = directory.listFiles();
		for (int i = 0; i < files.length; i++) {
			if (!files[i].isDirectory()) {
				mFiles.add(files[i]);
			}
		}
	}

	
	/**
	 * @see org.apache.uima.collection.CollectionReader#hasNext()
	 */
	public boolean hasNext() {
		return currentIndex < mFiles.size();
	}

	/**
	 * @see org.apache.uima.collection.CollectionReader#getNext(org.apache.uima.cas.CAS)
	 */
	public void getNext(CAS aCAS) throws IOException, CollectionException {
		System.err.print(".");
		JCas jcas;
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new CollectionException(e);
		}

		// open input stream to file
	    File file = (File) mFiles.get(currentIndex++);
		logger.log(Level.INFO, "getNext(CAS) - Reading file " + file.getName());
	    

	    String text = "";   
	    String xml = FileUtils.file2String(file);
	    text = xml;

	    // put document into CAS
	    text = text.replaceAll("(?s)<QUOTE PREVIOUSPOST=.*?/>", "");
	    jcas.setDocumentText(text);


	    // Keep Source document information
	    SourceDocInfo srcDocInfo = new SourceDocInfo(jcas);
	    URL url = file.getAbsoluteFile().toURI().toURL();
		srcDocInfo.setUri(url.toString());
	    srcDocInfo.addToIndexes();
	    

	    // Get document creation time if necessary
		if (annotateDCT){
			/*
			 * if DCT shall be set, set it now
			 */

			setDCT(xml, jcas, url.toString());
		}
	}

	@SuppressWarnings("unused")
	public void setDCT(String xml, JCas jcas, String filename){
		
		// SET DOCUMENT CREATION TIME!!!!
		// possible tags for DCT:
		// DATETIME (all WikiWar documents) with the following format 2009-12-20T17:00:00
		// DATE_TIME (Tern 2004) with the following format "10/17/2000 18:46:13.59" "10/17/2000 18:41:01.17" "11/04/2000 9:14:43.41" "2000-10-01 20:56:35"
		// DATE (Tern 2004) with the following format "07/15/2000" "1996-02-13" "1997-03-09 10:50:59" 
		// WITHOUT DATE ARE THE ACE TERN 2004 training files: chtb_171.eng.sgm, 172, 174, 179, 183, 
		// DATETIME (ACE 2005 training) with the following formats additionally: 20041221-20:24:00, 20030422

		String datetimetag = null;
		// possible date formats
		String dateformat1 = "(.*?)(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)(T| )(\\d\\d):(\\d\\d):(\\d\\d)(.*?)"; // 2009-12-20T17:00:00 or 2000-10-01 20:56:35
		String dateformat2 = "(.*?)(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)(T| )(\\d):(\\d\\d):(\\d\\d)(.*?)"; // 2009-12-20T7:00:00 or 2000-10-01 9:56:35
		String dateformat3 = "(.*?)(\\d\\d)/(\\d\\d)/(\\d\\d\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d\\d)(.*?)"; // 10/17/2000 18:46:13.59
		String dateformat4 = "(.*?)(\\d\\d)/(\\d\\d)/(\\d\\d\\d\\d) (\\d):(\\d\\d):(\\d\\d)\\.(\\d\\d)(.*?)"; // 10/17/2000 1:46:13.59
		String dateformat5 = "(.*?)(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)(.*?)"; // 1996-02-13
		String dateformat6 = "(.*?)(\\d\\d)/(\\d\\d)/(\\d\\d\\d\\d)(.*?)"; // 07/15/2000
		String dateformat7 = "(.*?)(January|February|March|April|May|June|July|August|September|October|November|December) ([\\d]?[\\d]),? (\\d\\d\\d\\d)(.*?)";
		String dateformat8 = "(.*?)(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)-(\\d\\d):(\\d\\d):(\\d\\d)(.*?)"; // 20041221-20:24:00
		String dateformat9 = "(.*?)(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)(.*?)"; // 20030422
		for (MatchResult m : findMatches(Pattern.compile("(<DATETIME>|<DATE_TIME>|<DATE>|<STORY_REF_TIME>)(("+dateformat1+
																						")|("+dateformat2+
																						")|("+dateformat3+
																						")|("+dateformat4+
																						")|("+dateformat5+
																						")|("+dateformat6+
																						")|("+dateformat7+
																						")|("+dateformat8+
																						")|("+dateformat9+")(</DATETIME>|</DATE_TIME>|</DATE>|</STORY_REF_TIME>))"), xml)){
			datetimetag = m.group(2);
		}
		
		
		String time_value = null;
		String date_value = null;
		if (!(datetimetag == null)){
			if (datetimetag.matches(dateformat1)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat1), datetimetag)){
					date_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4);
					time_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4)+"T"+m.group(6)+":"+m.group(7)+":"+m.group(8);
				}
			}
			else if (datetimetag.matches(dateformat2)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat2), datetimetag)){
					date_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4);
					time_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4)+"T0"+m.group(6)+":"+m.group(7)+":"+m.group(8);
				}
			}
			else if (datetimetag.matches(dateformat3)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat3), datetimetag)){
					date_value = m.group(4)+"-"+m.group(2)+"-"+m.group(3);
					time_value = m.group(4)+"-"+m.group(2)+"-"+m.group(3)+"T"+m.group(5)+":"+m.group(6)+":"+m.group(7)+"."+m.group(8);
				}
			}
			else if (datetimetag.matches(dateformat4)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat4), datetimetag)){
					date_value = m.group(4)+"-"+m.group(2)+"-"+m.group(3);
					time_value = m.group(4)+"-"+m.group(2)+"-"+m.group(3)+"T0"+m.group(5)+":"+m.group(6)+":"+m.group(7)+"."+m.group(8);
				}
			}
			else if (datetimetag.matches(dateformat5)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat5), datetimetag)){
					date_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4);
				}
			}
			else if (datetimetag.matches(dateformat6)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat6), datetimetag)){
					date_value = m.group(4)+"-"+m.group(2)+"-"+m.group(3);
				}
			}
			else if (datetimetag.matches(dateformat7)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat7), datetimetag)){
					String year  = m.group(4);
					String month = normMonth(m.group(2));
					String day   = normDay(m.group(3));
					date_value = year+"-"+month+"-"+day;
				}
			}
			else if (datetimetag.matches(dateformat8)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat8), datetimetag)){
					date_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4);
					time_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4)+"T"+m.group(5)+":"+m.group(6)+":"+m.group(7);
				}
			}
			else if (datetimetag.matches(dateformat9)){
				for (MatchResult m : findMatches(Pattern.compile(dateformat9), datetimetag)){
					date_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4);
				}
			}
			else{
				System.err.println();
				System.err.println("["+compontent_id+"] cannot set dct with datetimetag: "+datetimetag);
			}
			if (!(date_value == null)){
				Dct dct = new Dct(jcas);
				dct.setBegin(0);
				dct.setEnd(1);
				dct.setFilename(filename);
				dct.setTimexId("dct");
				if (!(time_value == null)){
					dct.setValue(time_value);
//					System.err.println("["+compontent_id+"] set dct to: "+time_value);
				}else if (!(date_value == null)){
					dct.setValue(date_value);
//					System.err.println("["+compontent_id+"] set dct to: "+date_value);
				}
				else{
					System.err.println();
					System.err.println("["+compontent_id+"] something wrong with setting DCT of : "+datetimetag);
				}
				dct.addToIndexes();
			}
		}
		else{
			if (date_value == null){
//				System.err.println("Checking for further formats of DCT...");
				String refYear  = "";
				String refMonth = "";
				String refDay   = "";
				for (MatchResult m1 : findMatches(Pattern.compile("DATE:[\\s]+("+dateformat7+")"),xml)){
					String referenceDate = m1.group(1);
					if (referenceDate.matches(dateformat7)){
						for (MatchResult mr : findMatches(Pattern.compile(dateformat7), referenceDate)){
							refYear  = mr.group(4);
							refMonth = normMonth(mr.group(2));
							refDay   = normDay(mr.group(3));
						}
					}
				}
				for (MatchResult m : findMatches(Pattern.compile("<STORY_REF_TIME>"
						+"(Jan\\.|Feb\\.|Mar\\.|Apr\\.|May\\.|Jun\\.|Jul\\.|Aug\\.|Sep\\.|Oct\\.|Nov\\.|Dec\\.|"
						+ "JAN\\.|FEB\\.|MAR\\.|APR\\.|MAY\\.|JUN\\.|JUL\\.|AUG\\.|SEP\\.|OCT\\.|NOV\\.|DEC\\.)[\\s]+([\\d]?[\\d])"
						+"</STORY_REF_TIME>"), xml)){
					String exactMonth = m.group(1);
					String exactDay   = m.group(2);
					date_value = refYear+"-"+normMonth(exactMonth)+"-"+normDay(exactDay);
				}
			}
			if (date_value == null){
				for (MatchResult m : findMatches(Pattern.compile("<STORY_REF_TIME>"
						+".*?(\\d\\d\\d\\d)(\\d\\d)(\\d\\d).*?"
						+"</STORY_REF_TIME>"), xml)){
					String exactYear  = m.group(1);
					String exactMonth = m.group(2);
					String exactDay   = m.group(3);
					date_value = exactYear+"-"+exactMonth+"-"+exactDay;
				}
			}
			if (date_value == null){
				String refYear  = "";
				String refMonth = "";
				String refDay   = "";
				for (MatchResult m : findMatches(Pattern.compile("<DOCNO>.*?(\\d\\d\\d\\d)(\\d\\d)(\\d\\d).*?</DOCNO>"),xml)){
					refYear  = m.group(1);
					refMonth = normMonth(m.group(2));
					refDay   = normDay(m.group(3));
				}
				if (!(refYear.matches(""))){
					for (MatchResult m : findMatches(Pattern.compile("<STORY_REF_TIME>.*?"
							+"(January|February|March|April|May|June|July|August|September|October|November|December) ([\\d]?[\\d]).*?"+
							"</STORY_REF_TIME>"), xml)){
						String exactMonth = normMonth(m.group(1));
						String exactDay   = normDay(m.group(2));
						date_value = refYear+"-"+exactMonth+"-"+exactDay;
					}
				}
			}
			if (date_value == null){
				String refYear  = "";
				String refMonth = "";
				String refDay   = "";				
					for (MatchResult m : findMatches(Pattern.compile("Publish Date:[\\s]+(\\d\\d)/(\\d\\d)/(\\d\\d)"),xml)){
						refYear  = "19"+m.group(3);
						refMonth = normMonth(m.group(1));
						refDay   = normDay(m.group(2));
					}
					if (!(refYear.matches(""))){
						for (MatchResult m : findMatches(Pattern.compile("<STORY_REF_TIME>.*?"
								+"(Jan\\.|Feb\\.|Mar\\.|Apr\\.|May\\.|Jun\\.|Jul\\.|Aug\\.|Sep\\.|Oct\\.|Nov\\.|Dec\\.|"
								+ "JAN\\.|FEB\\.|MAR\\.|APR\\.|MAY\\.|JUN\\.|JUL\\.|AUG\\.|SEP\\.|OCT\\.|NOV\\.|DEC\\.)[\\s]+([\\d]?[\\d]).*?"+
								"</STORY_REF_TIME>"), xml)){
							String exactMonth = normMonth(m.group(1));
							String exactDay   = normDay(m.group(2));
							date_value = refYear+"-"+exactMonth+"-"+exactDay;
						}
					}
					
			}
			// Document Creation Time style of EVALITA I-CAB corpus (Italian corpus)
			// example: <DOC ID="adige20040907_id405581" DATE="20040907">
			if (date_value == null){
				try {
					for (MatchResult m : findMatches(Pattern.compile("(<DOC ID=\".*?\" DATE=\")("+dateformat9+")(\">)"), xml)){
						datetimetag = m.group(2);
					}
					if (datetimetag.matches(dateformat9)){
						for (MatchResult m : findMatches(Pattern.compile(dateformat9), datetimetag)){
							date_value = m.group(2)+"-"+m.group(3)+"-"+m.group(4);
						}
					} else {
						System.err.println();
						System.err.println("["+compontent_id+"] cannot set dct with datetimetag: "+datetimetag);
					}
				} catch(NullPointerException e) { } // nothing to see here, carry on
			}
			if (date_value == null){
				System.err.println();
				System.err.println("["+compontent_id+"] Cannot set Document Creation Time - no datetimetag found in "+filename+"!");
			}
			else{
				Dct dct = new Dct(jcas);
				dct.setBegin(0);
				dct.setEnd(1);
				dct.setFilename(filename);
				dct.setTimexId("dct");
				dct.setValue(date_value);
				dct.addToIndexes();
			}
		}
	}
	
	public String normDay(String day){
		if (!(day.matches("\\d\\d"))){
			if (day.equals("1")){
				day = "01";
			}
			else if (day.equals("2")){
				day = "02";
			}
			else if (day.equals("3")){
				day = "03";
			}
			else if (day.equals("4")){
				day = "04";
			}
			else if (day.equals("5")){
				day = "05";
			}
			else if (day.equals("6")){
				day = "06";
			}
			else if (day.equals("7")){
				day = "07";
			}
			else if (day.equals("8")){
				day = "08";
			}
			else if (day.equals("9")){
				day = "09";
			}
		}
		
		return day;
	}
	
	public String normMonth(String month){
		if (month.toLowerCase().startsWith("jan")){
			month = "01";
		}
		else if (month.toLowerCase().startsWith("feb")){
			month = "02";
		}
		else if (month.toLowerCase().startsWith("mar")){
			month = "03";
		}
		else if (month.toLowerCase().startsWith("apr")){
			month = "04";
		}
		else if (month.toLowerCase().startsWith("may")){
			month = "05";
		}
		else if (month.toLowerCase().startsWith("jun")){
			month = "06";
		}
		else if (month.toLowerCase().startsWith("jul")){
			month = "07";
		}
		else if (month.toLowerCase().startsWith("aug")){
			month = "08";
		}
		else if (month.toLowerCase().startsWith("sep")){
			month = "09";
		}
		else if (month.toLowerCase().startsWith("oct")){
			month = "10";
		}
		else if (month.toLowerCase().startsWith("nov")){
			month = "11";
		}
		else if (month.toLowerCase().startsWith("dec")){
			month = "12";
		}
		return month;
	}
	
	/**
	 * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#close()
	 */
	public void close() throws IOException {
	}

	/**
	 * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#getProgress()
	 */
	public Progress[] getProgress() {
		return new Progress[] { new ProgressImpl(currentIndex, mFiles.size(), Progress.ENTITIES) };
	}

	/**
	 * Gets the total number of documents that will be returned by this collection reader. This is not
	 * part of the general collection reader interface.
	 * 
	 * @return the number of documents in the collection
	 */
	public int getNumberOfDocuments() {
		return mFiles.size();
	}
  
	/**
	 * Find all the matches of a pattern in a charSequence and return the
	 * results as list.
	 * 
	 * @param pattern
	 * @param s
	 * @return
	 */
	public static Iterable<MatchResult> findMatches(Pattern pattern,
			CharSequence s) {
		List<MatchResult> results = new ArrayList<MatchResult>();

		for (Matcher m = pattern.matcher(s); m.find();)
			results.add(m.toMatchResult());

		return results;
	}
}