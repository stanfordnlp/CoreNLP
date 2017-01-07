/*
 * XMIResultFormatter.java
 *
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU General Public License.
 *
 * authors: Andreas Fay, Jannik Strötgen
 * email:  fay@stud.uni-heidelberg.de, stroetgen@uni-hd.de
 *
 * HeidelTime is a multilingual, cross-domain temporal tagger.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */ 

package de.unihd.dbs.heideltime.standalone.components.impl;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.XMLSerializer;

import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;

/**
 * Result formatter based on XMI.
 * 
 * @see {@link org.apache.uima.examples.xmi.XmiWriterCasConsumer}
 * 
 * @author Andreas Fay, University of Heidelberg
 * @version 1.0
 */
public class XMIResultFormatter implements ResultFormatter {

	@Override
	public String format(JCas jcas) throws Exception {
		ByteArrayOutputStream outStream = null;

		try {
			// Write XMI
			outStream = new ByteArrayOutputStream();
			XmiCasSerializer ser = new XmiCasSerializer(jcas.getTypeSystem());
			XMLSerializer xmlSer = new XMLSerializer(outStream, false);
			ser.serialize(jcas.getCas(), xmlSer.getContentHandler());

			// Convert output stream to string
//			String newOut = outStream.toString("UTF-8");
			String newOut = outStream.toString();	
			
			
//			System.err.println("NEWOUT:"+newOut);
//			
//			if (newOut.matches("^<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>.*$")){
//				newOut = newOut.replaceFirst("<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>",
//								"<\\?xml version=\"1.0\" encoding=\""+Charset.defaultCharset().name()+"\"\\?>");	
//			}
			
//			if (newOut.matches("^.*?sofaString=\"(.*?)\".*$")){
//				for (MatchResult r : findMatches(Pattern.compile("^(.*?sofaString=\")(.*?)(\".*)$"), newOut)){
//					String stringBegin = r.group(1);
//					String sofaString  = r.group(2);
//					System.err.println("SOFASTRING:"+sofaString);
//					String stringEnd   = r.group(3);
//					// The sofaString is encoded as UTF-8.
//					// However, at this point it has to be translated back into the defaultCharset.
//					byte[] defaultDocText  = new String(sofaString.getBytes(), "UTF-8").getBytes(Charset.defaultCharset().name());
//					String docText = new String(defaultDocText);
//					System.err.println("DOCTEXT:"+docText);
//					newOut = stringBegin + docText + stringEnd;
////					newOut = newOut.replaceFirst("sofaString=\".*?\"", "sofaString=\"" + docText + "\"");
//				}
//			}
//			System.err.println("NEWOUT:"+newOut);
			return newOut;
		} finally {
			if (outStream != null) {
				outStream.close();
			}
		}

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
