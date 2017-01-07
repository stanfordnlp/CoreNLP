package de.unihd.dbs.heideltime.standalone.components.impl;

import java.util.Properties;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.heideltime.standalone.components.PartOfSpeechTagger;

public class StanfordPOSTaggerWrapper implements PartOfSpeechTagger {
	// uima wrapper instance
	private de.unihd.dbs.uima.annotator.stanfordtagger.StanfordPOSTaggerWrapper stanford = 
			new de.unihd.dbs.uima.annotator.stanfordtagger.StanfordPOSTaggerWrapper();

	@Override
	public void process(JCas jcas) {
		try {
			stanford.process(jcas);
		} catch(AnalysisEngineProcessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void initialize(Properties settings) {
		StandaloneConfigContext aContext = new StandaloneConfigContext();
		
		// construct a context for the uima engine 
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.stanfordtagger.StanfordPOSTaggerWrapper.PARAM_ANNOTATE_TOKENS, 
				(Boolean) settings.get(STANFORDPOSTAGGER_ANNOTATE_TOKENS));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.stanfordtagger.StanfordPOSTaggerWrapper.PARAM_ANNOTATE_SENTENCES, 
				(Boolean) settings.get(STANFORDPOSTAGGER_ANNOTATE_SENTENCES));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.stanfordtagger.StanfordPOSTaggerWrapper.PARAM_ANNOTATE_PARTOFSPEECH, 
				(Boolean) settings.get(STANFORDPOSTAGGER_ANNOTATE_POS));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.stanfordtagger.StanfordPOSTaggerWrapper.PARAM_CONFIG_PATH, 
				((String) settings.get(STANFORDPOSTAGGER_CONFIG_PATH)).length() == 0 ? null : (String) settings.get(STANFORDPOSTAGGER_CONFIG_PATH));
		aContext.setConfigParameterValue(de.unihd.dbs.uima.annotator.stanfordtagger.StanfordPOSTaggerWrapper.PARAM_MODEL_PATH, 
				(String) settings.get(STANFORDPOSTAGGER_MODEL_PATH));
		
		stanford.initialize(aContext);
	}

}
