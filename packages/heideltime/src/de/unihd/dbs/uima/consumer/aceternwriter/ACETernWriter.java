/*
 * ACETernWriter.java
 * 
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * ACE Tern Writer creates files according to the ACE Tern style.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */
package de.unihd.dbs.uima.consumer.aceternwriter;

import java.util.List;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;

import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.SourceDocInfo;


/**
 * 
 * @author jstroetgen
 *
 */
public class ACETernWriter extends CasConsumer_ImplBase {

	public static final String PARAM_OUTPUTDIR = "OutputDir";
	public static final String PARAM_CONVERTTIMEX3TO2 = "ConvertTimex3To2";

	private File mOutputDir;
 
	private int mDocNum;
	
	private Boolean convertTimex3To2 = true;
	
	private boolean printDetails = false;

	/**
	 * initialize
	 */
	public void initialize() throws ResourceInitializationException {
    
		mDocNum = 0;
		convertTimex3To2 = (Boolean) getConfigParameterValue(PARAM_CONVERTTIMEX3TO2);
		mOutputDir = new File((String) getConfigParameterValue(PARAM_OUTPUTDIR));
		if (!mOutputDir.exists()) {
			mOutputDir.mkdirs();
		} 
	}


	/**
	 * process
	 */
	public void processCas(CAS aCAS) throws ResourceProcessException {

		JCas jcas;
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		}

		printTimexAnnotationsInline(jcas);
	}
	
	
	
	
	public void printTimexAnnotationsInline(JCas jcas){
		// retrieve the filename of the input file from the CAS
	    FSIterator it = jcas.getAnnotationIndex(SourceDocInfo.type).iterator();
	    File outFile = null;
	    File inFile = null;
	    if (it.hasNext()) {
	      SourceDocInfo fileLoc = (SourceDocInfo) it.next();
	      try {
	        inFile = new File(new URL(fileLoc.getUri()).getPath());
	        String outFileName = inFile.getName();
	        if (fileLoc.getOffsetInSource() > 0) {
	          outFileName += ("_" + fileLoc.getOffsetInSource());
	        }
	        outFileName += ".xmi";
	        outFile = new File(mOutputDir, outFileName);
	      } catch (MalformedURLException e1) {
	        // invalid URL, use default processing below
	      }
	    }
	    if (outFile == null) {
	      outFile = new File(mOutputDir, "doc" + mDocNum++);
	    }
	    
	    // what has to be printed?
	    String toprint = "";
	    
	    // document text
	    String doctext    = jcas.getDocumentText();
	    int startposition = 0;
	    int endposition   = doctext.length();
	    boolean anyTimex  = false;
	    
		// get timex index
		FSIndex indexTimex   = jcas.getAnnotationIndex(Timex3.type);
		FSIterator iterTimex = indexTimex.iterator();
		
		
		while (iterTimex.hasNext()){
			anyTimex = true;
			Timex3 t = (Timex3) iterTimex.next();
			endposition = t.getBegin();
			if (endposition < startposition){
				if (printDetails == true){
					System.err.println("[Tern2004Writer] Overlapping expressions... ignoring: "+t.getCoveredText());
				}
			}else{
				// CHANGES DUE TO TIMEX2 not equal TIMEX3
				String timexvalue = t.getTimexValue();
				if(convertTimex3To2) {
					timexvalue = translatetimex3timex2(timexvalue);
					if (t.getTimexType().equals("SET")){
						timexvalue = translatetimex3timex2set(timexvalue); 
					}
				}
				toprint = toprint + doctext.substring(startposition, endposition); // text from begin or last timex to begin of new timex
				toprint = toprint + "<TIMEX2 val=\"" + timexvalue + "\">";         // timex opening tag
				toprint = toprint + t.getCoveredText();                            // timex text
				toprint = toprint + "</TIMEX2>";
				startposition = t.getEnd();
			}
		}
		if (anyTimex == true){
            // text from last timex to end
			toprint = toprint + doctext.substring(startposition);
		}
		if (anyTimex == false){
			// whole document text
			toprint = toprint + doctext;
		}
		
		// print toprint text
		try {
			BufferedWriter bf = new BufferedWriter(new FileWriter(outFile));
			bf.append(toprint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	public String translatetimex3timex2(String timexvalue){
		
		// change decades
		String decade = "(.*\\d\\d\\d)X";
		if (timexvalue.matches(decade)){
			for (MatchResult m : findMatches(Pattern.compile(decade), timexvalue)){
				timexvalue = m.group(1);
			}
		}
		// change century
		String century = "(.*\\d\\d)XX";
		if (timexvalue.matches(century)){
			for (MatchResult m : findMatches(Pattern.compile(century), timexvalue)){
				timexvalue = m.group(1);
			}
		}		
		
		return timexvalue;
	}
	
	public String translatetimex3timex2set(String timexvalue){
		
		// change year
		String year = "(P(\\d)+Y)";
		if (timexvalue.matches(year)){
			timexvalue = "XXXX";
		}
		// change month
		String month = "(P(\\d)+M)";
		if (timexvalue.matches(month)){
			timexvalue = "XXXX-XX";
		}
		// change day
		String day = "(P(\\d)+D)";
		if (timexvalue.matches(day)){
			timexvalue = "XXXX-XX-XX";
		}
		// change hour
		String hour = "(PT(\\d)+H)";
		if (timexvalue.matches(hour)){
			timexvalue = "XXXX-XX-XXTXX";
		}
		// change minute
		String minute = "(PT(\\d)+M)";
		if (timexvalue.matches(minute)){
			timexvalue = "XXXX-XX-XXTXX:XX";
		}
		
		return timexvalue;
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
