/*
 * TimeMLResultFormatter.java
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

import java.util.HashSet;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Timex3Interval;

/**
 * Result formatter based on TimeML.
 * 
 * @see {@link org.apache.uima.examples.xmi.XmiWriterCasConsumer}
 * 
 * @author Andreas Fay, Jannik Strötgen Heidelberg University
 * @version 1.01
 */
public class TimeMLResultFormatter implements ResultFormatter {
	public String format(JCas jcas) throws Exception {
		final String documentText = jcas.getDocumentText();
		String outText = new String();
		
		// get the timex3 intervals, do some pre-selection on them
		FSIterator iterIntervals = jcas.getAnnotationIndex(Timex3Interval.type).iterator();
		TreeMap<Integer, Timex3Interval> intervals = new TreeMap<Integer, Timex3Interval>();
		while(iterIntervals.hasNext()) {
			Timex3Interval t = (Timex3Interval) iterIntervals.next();
			
			// disregard intervals that likely aren't a real interval, but just a timex-translation
			if(t.getTimexValueEB().equals(t.getTimexValueLB()) && t.getTimexValueEE().equals(t.getTimexValueLE()))
				continue;
			
			if(intervals.containsKey(t.getBegin())) {
				Timex3Interval tInt = intervals.get(t.getBegin());
				
				// always get the "larger" intervals
				if(t.getEnd() - t.getBegin() > tInt.getEnd() - tInt.getBegin()) {
					intervals.put(t.getBegin(), t);
				}
			} else {
				intervals.put(t.getBegin(), t);
			}
		}

		/* 
		 * loop through the timexes to create two treemaps:
		 * - one containing startingposition=>timex tuples for eradication of overlapping timexes
		 * - one containing endposition=>timex tuples for assembly of the XML file
		 */
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
		TreeMap<Integer, Timex3> forwardTimexes = new TreeMap<Integer, Timex3>(),
				backwardTimexes = new TreeMap<Integer, Timex3>();
		while(iterTimex.hasNext()) {
			Timex3 t = (Timex3) iterTimex.next();
			forwardTimexes.put(t.getBegin(), t);
			backwardTimexes.put(t.getEnd(), t);
		}
		
		HashSet<Timex3> timexesToSkip = new HashSet<Timex3>();
		Timex3 prevT = null;
		Timex3 thisT = null;
		// iterate over timexes to find overlaps
		for(Integer begin : forwardTimexes.navigableKeySet()) {
			thisT = (Timex3) forwardTimexes.get(begin);
			
			// check for whether this and the previous timex overlap. ex: [early (friday] morning)
			if(prevT != null && prevT.getEnd() > thisT.getBegin()) {
				
				Timex3 removedT = null; // only for debug message
				// assuming longer value string means better granularity
				if(prevT.getTimexValue().length() > thisT.getTimexValue().length()) {
					timexesToSkip.add(thisT);
					removedT = thisT;
					/* prevT stays the same. */
				} else {
					timexesToSkip.add(prevT);
					removedT = prevT;
					prevT = thisT; // this iteration's prevT was removed; setting for new iteration 
				}
				
				// ask user to let us know about possibly incomplete rules
				Logger l = Logger.getLogger("TimeMLResultFormatter");
				l.log(Level.WARNING, "Two overlapping Timexes have been discovered:" + System.getProperty("line.separator")
						+ "Timex A: " + prevT.getCoveredText() + " [\"" + prevT.getTimexValue() + "\" / " + prevT.getBegin() + ":" + prevT.getEnd() + "]" 
						+ System.getProperty("line.separator")
						+ "Timex B: " + removedT.getCoveredText() + " [\"" + removedT.getTimexValue() + "\" / " + removedT.getBegin() + ":" + removedT.getEnd() + "]" 
						+ " [removed]" + System.getProperty("line.separator")
						+ "The writer chose, for granularity: " + prevT.getCoveredText() + System.getProperty("line.separator")
						+ "This usually happens with an incomplete ruleset. Please consider adding "
						+ "a new rule that covers the entire expression.");
			} else { // no overlap found? set current timex as next iteration's previous timex
				prevT = thisT;
			}
		}

		// alternative xml creation method
		Timex3Interval interval = null;
		Timex3 timex = null;
		for(Integer docOffset = 0; docOffset <= documentText.length(); docOffset++) {
			/**
			 *  see if we have to finish off old timexes/intervals
			 */
			if(timex != null && timex.getEnd() == docOffset) {
				outText += "</TIMEX3>";
				timex = null;
			}
			if(interval != null && interval.getEnd() == docOffset) {
				outText += "</TIMEX3INTERVAL>";
				interval = null;
			}
			
			/**
			 *  grab a new interval/timex if this offset marks the beginning of one
			 */
			if(interval == null && intervals.containsKey(docOffset))
				interval = intervals.get(docOffset);
			if(timex == null && forwardTimexes.containsKey(docOffset) && !timexesToSkip.contains(forwardTimexes.get(docOffset)))
				timex = forwardTimexes.get(docOffset);
			
			/**
			 *  if an interval/timex begin here, append the opening tag. interval first, timex afterwards
			 */
			// handle interval openings first
			if(interval != null && interval.getBegin() == docOffset) {
				String intervalTag = "<TIMEX3INTERVAL";
				if (!interval.getTimexValueEB().equals(""))
					intervalTag += " earliestBegin=\"" + interval.getTimexValueEB() + "\"";
				if (!interval.getTimexValueLB().equals(""))
					intervalTag += " latestBegin=\"" + interval.getTimexValueLB() + "\"";
				if (!interval.getTimexValueEE().equals(""))
					intervalTag += " earliestEnd=\"" + interval.getTimexValueEE() + "\"";
				if (!interval.getTimexValueLE().equals(""))
					intervalTag += " latestEnd=\"" + interval.getTimexValueLE() + "\"";
				intervalTag += ">";
				outText += intervalTag;
			}
			// handle timex openings after that
			if(timex != null && timex.getBegin() == docOffset) {
				String timexTag = "<TIMEX3";
				if (!timex.getTimexId().equals(""))
					timexTag += " tid=\"" + timex.getTimexId() + "\"";
				if (!timex.getTimexType().equals(""))
					timexTag += " type=\"" + timex.getTimexType() + "\"";
				if (!timex.getTimexValue().equals(""))
					timexTag += " value=\"" + timex.getTimexValue() + "\"";
				if (!timex.getTimexQuant().equals(""))
					timexTag += " quant=\"" + timex.getTimexQuant() + "\"";
				if (!timex.getTimexFreq().equals(""))
					timexTag += " freq=\"" + timex.getTimexFreq() + "\"";
				if (!timex.getTimexMod().equals(""))
					timexTag += " mod=\"" + timex.getTimexMod() + "\"";
				timexTag += ">";
				outText += timexTag;
			}
			
			/**
			 * append the current character
			 */
			if(docOffset + 1 <= documentText.length())
				outText += documentText.substring(docOffset, docOffset + 1);
		}
		
		
		
		// Add TimeML start and end tags		
		outText = "<?xml version=\"1.0\"?>\n<!DOCTYPE TimeML SYSTEM \"TimeML.dtd\">\n<TimeML>\n" + outText + "\n</TimeML>\n";
		
		return outText;
	}

}
