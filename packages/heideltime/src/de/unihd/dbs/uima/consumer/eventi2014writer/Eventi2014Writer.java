/*
 * Eventi2014Writer.java
 * 
 * Copyright (c) 2014, Database Research Group, Institute of Computer Science, Heidelberg University. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * The Eventi2014 Writer writes Eventi-style output.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.consumer.eventi2014writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.types.heideltime.Dct;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Timex3Interval;
import de.unihd.dbs.uima.types.heideltime.Token;

public class Eventi2014Writer extends CasConsumer_ImplBase {
	private Class<?> component = this.getClass();

	private static final String PARAM_OUTPUTDIR = "OutputDir";
	
	// counter for outputting documents. gets increased in case there is no DCT/filename info 
	private static volatile Integer outCount = 0;

	private File mOutputDir;

	public void initialize() throws ResourceInitializationException {
		mOutputDir = new File((String) getConfigParameterValue(PARAM_OUTPUTDIR));
		
		if (!mOutputDir.exists()) {
			if(!mOutputDir.mkdirs()) {
				Logger.printError(component, "Couldn't create non-existant folder "+mOutputDir.getAbsolutePath());
				throw new ResourceInitializationException();
			}
		}
		
		if(!mOutputDir.canWrite()) {
			Logger.printError(component, "Folder "+mOutputDir.getAbsolutePath()+" is not writable.");
			throw new ResourceInitializationException();
		}
	}
	
	public void processCas(CAS aCAS) throws ResourceProcessException {
		JCas jcas;
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		}
		
		// prepare everything for document
		String fullDocument = "";
		
		// get the DCT
		Dct dct = null;
		String filename = null;
		String dctTag = "";
		try {
			dct = (Dct) jcas.getAnnotationIndex(Dct.type).iterator().next();
			String[] parts = dct.getFilename().split("---");
			filename = parts[0];
			dctTag = parts[1];
		} catch(Exception e) {
			e.printStackTrace();
			filename = "doc_" + Eventi2014Writer.getOutCount();
		}
		
		// create the document according to the formatting requirements of EVENTI 2014

		// first line: <Document doc_name="FILENAME">
		String firstLine = "<Document doc_name=\"FILENAME\">\n";
		fullDocument = firstLine.replaceAll("FILENAME", filename);
		
		// get the tokens and add them to fullDocument
		FSIterator itToken = jcas.getAnnotationIndex(Token.type).iterator();
		int oldTokNum = 0;
		int oldTokID  = 0;
		while (itToken.hasNext()){
			Token t = (Token) itToken.next();
			
			String[] parts = t.getFilename().split("---");
			String sentNum = parts[1];
			String tokNum  = parts[2];
			
			while (oldTokID < (t.getTokenId() - 1)){
				
				oldTokNum++;
				oldTokID++;
				String tokenLine = "<token t_id=\"TOKENID\" sentence=\"SENTENCEID\" number=\"TOKENNUMBER\">TOKENSTRING</token>\n";
				tokenLine = tokenLine.replace("TOKENID", oldTokID+"");
				tokenLine = tokenLine.replace("SENTENCEID", sentNum);
				tokenLine = tokenLine.replace("TOKENNUMBER", oldTokNum+"");
				tokenLine = tokenLine.replace("TOKENSTRING", "");
				fullDocument = fullDocument + tokenLine;
			}
			
			String tokenLine = "<token t_id=\"TOKENID\" sentence=\"SENTENCEID\" number=\"TOKENNUMBER\">TOKENSTRING</token>\n";
			tokenLine = tokenLine.replace("TOKENID", t.getTokenId()+"");
			tokenLine = tokenLine.replace("SENTENCEID", sentNum);
			tokenLine = tokenLine.replace("TOKENNUMBER", tokNum);
			tokenLine = tokenLine.replace("TOKENSTRING", t.getCoveredText());
			oldTokNum = Integer.parseInt(tokNum);
			oldTokID  = t.getTokenId();
			
//			System.err.println("TOKEN FOUND....-->" + t.getCoveredText() + "<--");
			
			fullDocument = fullDocument + tokenLine;
		}
		
		// add opening markable tag
		fullDocument = fullDocument + "\n\n<Markables>\n";
		
		// collection for timexes which have an emptyValue attribute
		HashMap<Timex3, Integer> emptyValueTimexes = new HashMap<Timex3, Integer>();
		// association for HeidelTime-internal Timex3 IDs -> markable_ids
		HashMap<String, String> idTranslation = new HashMap<String, String>();
		
		// get the timex3s and add them to fullDocument
		int markableCounter = 1;
		FSIterator itTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
		while (itTimex.hasNext()){
			Timex3 t = (Timex3) itTimex.next();
			if(t instanceof Timex3Interval) continue;
			
			if(t.getEmptyValue() != null && !t.getEmptyValue().equals(""))
				emptyValueTimexes.put(t, markableCounter);
			
			// full tag - probably not required
//			String open  = "<TIMEX3 m_id=\"MARKABLEID\" temporalFunction=\"FALSE\" functionInDocument=\"\" endPoint=\"\" anchorTimeID=\"\" mod=\"\" beginPoint=\"\" quant=\"\" freq=\"\" value=\"1985\" type=\"DATE\" comment=\"\"  >";
			String open  = "<TIMEX3 m_id=\"MARKABLEID\" mod=\"MODSTRING\" "+
								"quant=\"QUANTSTRING\" freq=\"FREQSTRING\" "+
								"value=\"VALUESTRING\" type=\"TYPESTRING\"  >\n";
			String tokenInfoSingle = "<token_anchor t_id=\"TOKENID\"/>\n";
			String close = "</TIMEX3>\n";
			
			// set the attributes of the TIMEX3 annotations
			open = open.replace("MARKABLEID", markableCounter+"");
			open = open.replace("MODSTRING", t.getTimexMod());
			open = open.replace("QUANTSTRING", t.getTimexQuant());
			open = open.replace("FREQSTRING", t.getTimexFreq());
			open = open.replace("VALUESTRING", t.getTimexValue());
			open = open.replace("TYPESTRING", t.getTimexType());
			fullDocument = fullDocument + open;
			
			// get the ids of the tokens which are involved

			FSIterator tokenIt = jcas.getAnnotationIndex(Token.type).iterator();
			while (tokenIt.hasNext())
			{
				Token tok = (Token) tokenIt.next();
				if ((tok.getBegin() >= t.getBegin()) && (tok.getEnd() <= t.getEnd())){
					int tokID = tok.getTokenId();
					String line = tokenInfoSingle;
					line = line.replace("TOKENID", tokID+"");
					fullDocument = fullDocument + line;
				}
			}
			
			fullDocument = fullDocument + close;
			
			idTranslation.put(t.getTimexId(), markableCounter+"");
			
			markableCounter++;
		}
		
		// add document creation time tag
		Pattern p = Pattern.compile("m_id=\"([^\"]*)\"");
		Matcher m = p.matcher(dctTag);
		if(m.find())
			dctTag = dctTag.substring(0, m.start(1)) + (markableCounter++) + dctTag.substring(m.end(1), dctTag.length());
		fullDocument = fullDocument + dctTag + "\n";
		
		// add empty tags
		for(Entry<Timex3, Integer> entry : emptyValueTimexes.entrySet()) {
			String open  = "<TIMEX3 m_id=\""+(markableCounter++)+"\" TAG_DESCRIPTOR=\"Empty_Mark\" anchorTimeID=\""+entry.getValue()+"\" value=\""+entry.getKey().getEmptyValue()+"\" type=\"DATE\" />\n";

			fullDocument = fullDocument + open;
		}
		
		// add empty tags from timex3intervals
		FSIterator tx3intIt = jcas.getAnnotationIndex(Timex3Interval.type).iterator();
		while(tx3intIt.hasNext()) {
			Timex3Interval tx3i = (Timex3Interval) tx3intIt.next();
			if(tx3i.getEmptyValue() != null && !tx3i.getEmptyValue().equals("")) {
				String beginMarkable = idTranslation.get(tx3i.getBeginTimex());
				String endMarkable = idTranslation.get(tx3i.getEndTimex());
				fullDocument += "<TIMEX3 m_id=\""+(markableCounter++)+"\" TAG_DESCRIPTOR=\"Empty_Mark\" beginPoint=\""+beginMarkable+"\" endPoint=\""+endMarkable+"\" anchorTimeID=\""+beginMarkable+"\" value=\""+tx3i.getEmptyValue()+"\" type=\"DURATION\" />\n";
			}
		}
		
		// add closing tag for markables
		fullDocument += "</Markables>\n<Relations>\n</Relations>\n</Document>";
		
		writeDocument(fullDocument, filename);
	}


	/**
	 * writes a populated DOM xml(timeml) document to a given directory/file 
	 * @param xmlDoc xml dom object
	 * @param filename name of the file that gets appended to the set output path
	 */
	private void writeDocument(String fullDocument, String filename) {
		// create output file handle
		File outFile = new File(mOutputDir, filename+".xml"); 
		
		BufferedWriter bw = null;
		try {
			// create a buffered writer for the output file
			bw = new BufferedWriter(new FileWriter(outFile));
			bw.append(fullDocument);
			
		} catch (IOException e) { // something went wrong with the bufferedwriter
			e.printStackTrace();
			Logger.printError(component, "File "+outFile.getAbsolutePath()+" could not be written.");
		} finally { // clean up for the bufferedwriter
			try {
				bw.close();
			} catch(IOException e) {
				e.printStackTrace();
				Logger.printError(component, "File "+outFile.getAbsolutePath()+" could not be closed.");
			}
		}
	}

	public static synchronized Integer getOutCount() {
		return outCount++;
	}
}
