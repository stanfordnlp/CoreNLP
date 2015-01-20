/*
 * TreeTaggerWrapper.java
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

import java.util.Properties;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.heideltime.standalone.Config;
import de.unihd.dbs.heideltime.standalone.components.PartOfSpeechTagger;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;

/**
 * Proxy class for UIMA-TreeTaggerWrapper
 * 
 * @author Andreas Fay, Jannik Strötgen, Julian Zell, Heidelberg University
 * @version 1.01
 */
public class TreeTaggerWrapper implements PartOfSpeechTagger {
	// uima wrapper instance
	private de.unihd.dbs.uima.annotator.treetagger.TreeTaggerWrapper ttw = 
			new de.unihd.dbs.uima.annotator.treetagger.TreeTaggerWrapper();
	
	public void initialize(Language language, Boolean annotateTokens, 
			Boolean annotateSentences, Boolean annotatePartOfSpeech, Boolean improveGermanSentences) {
	}
	
	public void process(JCas jcas) {
		try {
			ttw.process(jcas);
		} catch(AnalysisEngineProcessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void initialize(Properties settings) {
		Language language = (Language) settings.get(TREETAGGER_LANGUAGE);
		String treeTaggerHome = Config.get(Config.TREETAGGERHOME);
		Boolean annotateTokens = (Boolean) settings.get(TREETAGGER_ANNOTATE_TOKENS);
		Boolean annotateSentences = (Boolean) settings.get(TREETAGGER_ANNOTATE_SENTENCES);
		Boolean annotatePartOfSpeech = (Boolean) settings.get(TREETAGGER_ANNOTATE_POS);
		Boolean improveGermanSentences = (Boolean) settings.get(TREETAGGER_IMPROVE_GERMAN_SENTENCES);
		String cnTokenizerPath = (String) settings.get(TREETAGGER_CHINESE_TOKENIZER_PATH);
		ttw.initialize(language, treeTaggerHome, annotateTokens, 
				annotateSentences, annotatePartOfSpeech, improveGermanSentences, cnTokenizerPath);
	}
}