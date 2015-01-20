/*
 * Tempeval2Writer.java
 * 
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * TempEval2 Writer create files according to the TempEval-2 style.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.consumer.tempeval2writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;

import de.unihd.dbs.uima.types.heideltime.Timex3;

/**
 * 
 * @author jstroetgen
 *
 */
public class Tempeval2Writer extends CasConsumer_ImplBase {
	public static final String PARAM_OUTPUTDIR = "OutputDir";

	private File mOutputDir;
	
	/**
	 * initialize
	 */
	public void initialize() throws ResourceInitializationException {
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

		printTimexAnnotations(jcas);
	}
	
	
	public void printTimexAnnotations(JCas jcas) {
		File outExtents = new File(mOutputDir, "timex-extents.tab");
		File outAttributes = new File(mOutputDir, "timex-attributes.tab");
		
		// get timex index
		FSIndex indexTimex   = jcas.getAnnotationIndex(Timex3.type);
		FSIterator iterTimex = indexTimex.iterator();
		
		while (iterTimex.hasNext()){
			Timex3 t = (Timex3) iterTimex.next();
			if (!((t.getType().toString().equals("de.unihd.dbs.uima.heidopp.types.tempeval2.GoldTimex3")))){
				
				// output extents
				String toPrintExtents    = "";
				String[] allTokList = t.getAllTokIds().split("<-->");
				for (int i=1; i < allTokList.length; i++){
					toPrintExtents    = toPrintExtents+t.getFilename()+"\t"+t.getSentId()+"\t"+allTokList[i]+
					"\ttimex3\t"+t.getTimexId()+"\t1\n";					
				}
				
				try {
					BufferedWriter bf = new BufferedWriter(new FileWriter(outExtents, true));
					bf.write(toPrintExtents);
					bf.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

				// output attributes
				String toPrintAttributes = t.getFilename()+"\t"+t.getSentId()+"\t"+t.getFirstTokId()+
											"\ttimex3\t"+t.getTimexId()+"\t1\ttype\t"+t.getTimexType()+"\n";
				toPrintAttributes += t.getFilename()+"\t"+t.getSentId()+"\t"+t.getFirstTokId()+
											"\ttimex3\t"+t.getTimexId()+"\t1\tvalue\t"+t.getTimexValue()+"\n";
	
				try {
					BufferedWriter bf = new BufferedWriter(new FileWriter(outAttributes, true));
					bf.write(toPrintAttributes);
					bf.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
	}
}
