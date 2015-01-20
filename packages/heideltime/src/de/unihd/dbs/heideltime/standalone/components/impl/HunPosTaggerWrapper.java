package de.unihd.dbs.heideltime.standalone.components.impl;

import java.util.Properties;

import org.apache.uima.jcas.JCas;

import de.unihd.dbs.heideltime.standalone.Config;
import de.unihd.dbs.heideltime.standalone.components.PartOfSpeechTagger;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;

public class HunPosTaggerWrapper implements PartOfSpeechTagger {
	
	private hr.fer.zemris.takelab.uima.annotator.hunpos.HunPosTaggerWrapper hptw = 
			new hr.fer.zemris.takelab.uima.annotator.hunpos.HunPosTaggerWrapper();

	@Override
	public void initialize(Properties settings) {
		Language language = (Language) settings.get(HUNPOS_LANGUAGE);
		String hunpos_path = Config.get(Config.HUNPOS_PATH);
		String model_path = Config.get(Config.HUNPOS_MODEL_PATH);
		Boolean annotatePOS = (Boolean) settings.get(HUNPOS_ANNOTATE_POS);
		Boolean annotateTokens = (Boolean) settings.get(HUNPOS_ANNOTATE_TOKENS);
		Boolean annotateSentences = (Boolean) settings.get(HUNPOS_ANNOTATE_SENTENCES);
		
		hptw.initialize(language, hunpos_path, model_path, annotateTokens, annotateSentences, annotatePOS);
	}

	@Override
	public void process(JCas jcas) {
		try {
			hptw.process(jcas);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
